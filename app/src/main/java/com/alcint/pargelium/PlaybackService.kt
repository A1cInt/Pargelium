package com.alcint.pargelium

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.ArrayList

var activeAudioProcessor: CustomAudioProcessor? = null

@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var fossApiManager: FossWearApiManager? = null

    private var continuousMixManager: ContinuousMixManager? = null

    override fun onCreate() {
        super.onCreate()
        PrefsManager.init(this)

        val customProcessor = CustomAudioProcessor()
        activeAudioProcessor = customProcessor

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(50000, 50000, 2500, 5000)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>
            ) {
                val myAudioSink = DefaultAudioSink.Builder()
                    .setAudioProcessors(arrayOf(customProcessor))
                    .setEnableFloatOutput(false)
                    .build()

                val renderer = MediaCodecAudioRenderer(
                    context, mediaCodecSelector, enableDecoderFallback,
                    eventHandler, eventListener, myAudioSink
                )
                out.add(renderer)
            }
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        val baseDataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(baseDataSourceFactory, object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val uri = dataSpec.uri

                if (uri.scheme == "saavn") {
                    val trackIdStr = uri.schemeSpecificPart
                    try {
                        val trackId = trackIdStr.toLongOrNull() ?: return dataSpec
                        val streamUri = runBlocking(Dispatchers.IO) { SaavnApi.getStreamUri(trackId) }

                        val headers = mutableMapOf<String, String>()
                        headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

                        return dataSpec.buildUpon()
                            .setUri(streamUri)
                            .setHttpRequestHeaders(headers)
                            .build()

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return dataSpec
            }
        })

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(resolvingDataSourceFactory)

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Тот самый нищий Continuous Mix
        continuousMixManager = ContinuousMixManager(this, player, mediaSourceFactory)
        continuousMixManager?.start()

        fossApiManager = FossWearApiManager(this, serviceScope) { command ->
            serviceScope.launch {
                when (command) {
                    "PLAY" -> player.play()
                    "PAUSE" -> player.pause()
                    "NEXT" -> if (player.hasNextMediaItem()) player.seekToNext()
                    "PREV" -> if (player.hasPreviousMediaItem()) player.seekToPrevious() else player.seekTo(0)
                }
            }
        }
        fossApiManager?.syncTheme(PrefsManager.getThemeMode())

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { updateWearState(player) }
            override fun onPlaybackStateChanged(playbackState: Int) { updateWearState(player) }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { updateWearState(player) }
        })

        val callback = object : MediaLibrarySession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_SET_REPEAT_MODE)
                    .add(Player.COMMAND_SET_SHUFFLE_MODE)
                    .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                    .build()

                return MediaSession.ConnectionResult.accept(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                    playerCommands
                )
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>
            ): ListenableFuture<List<MediaItem>> {
                val updatedMediaItems = mediaItems.map { item ->
                    item.buildUpon()
                        .setUri(item.requestMetadata.mediaUri ?: item.localConfiguration?.uri)
                        .build()
                }
                return Futures.immediateFuture(updatedMediaItems)
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback).build()
    }

    private fun updateWearState(player: Player) {
        val currentItem = player.currentMediaItem
        val title = currentItem?.mediaMetadata?.title?.toString() ?: "Pargelium"
        val artist = currentItem?.mediaMetadata?.artist?.toString() ?: "Выберите трек"
        val isPlaying = player.isPlaying
        val pos = player.currentPosition
        val dur = if (player.duration > 0) player.duration else 1L

        serviceScope.launch(Dispatchers.IO) {
            fossApiManager?.updatePlaybackState(title, artist, isPlaying, pos, dur, Color.DKGRAY)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        continuousMixManager?.stop()
        continuousMixManager = null

        serviceScope.cancel()
        fossApiManager?.release()
        activeAudioProcessor = null
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
}