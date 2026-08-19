package com.alcint.pargelium

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.appwidget.updateAll
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
import coil.imageLoader
import coil.request.ImageRequest
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList
import kotlin.math.abs

@UnstableApi
class PlaybackService : MediaLibraryService() {

    companion object {
        var activeAudioProcessor: CustomAudioProcessor? = null
            private set
        private val SAAVN_HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        )
    }

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var fossApiManager: FossWearApiManager? = null
    private var continuousMixManager: ContinuousMixManager? = null
    private var wearUpdateJob: Job? = null

    private var lastWearTitle = ""
    private var lastWearIsPlaying = false
    private var lastWearPos = -1L

    override fun onCreate() {
        super.onCreate()
        PrefsManager.init(applicationContext)

        val customProcessor = CustomAudioProcessor()
        activeAudioProcessor = customProcessor

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(50000, 50000, 2500, 5000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(applicationContext) {
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

        val baseDataSourceFactory = DefaultDataSource.Factory(applicationContext, httpDataSourceFactory)

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(baseDataSourceFactory) { dataSpec ->
            val uri = dataSpec.uri

            if (uri.scheme == "saavn") {
                val trackId = uri.schemeSpecificPart.toLongOrNull() ?: return@Factory dataSpec
                try {
                    val streamUri = runBlocking(Dispatchers.IO) { SaavnApi.getStreamUri(trackId) }

                    return@Factory dataSpec.buildUpon()
                        .setUri(streamUri)
                        .setHttpRequestHeaders(SAAVN_HEADERS)
                        .build()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            dataSpec
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(applicationContext)
            .setDataSourceFactory(resolvingDataSourceFactory)

        val player = ExoPlayer.Builder(applicationContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()

        continuousMixManager = ContinuousMixManager(applicationContext, player, mediaSourceFactory)
        continuousMixManager?.start()

        val playerHandler = Handler(player.applicationLooper)
        fossApiManager = FossWearApiManager(applicationContext, serviceScope) { command ->
            playerHandler.post { handlePlayerCommand(player, command) }
        }
        fossApiManager?.syncTheme(PrefsManager.getThemeMode())

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { updateWearState(player) }
            override fun onPlaybackStateChanged(playbackState: Int) { updateWearState(player) }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { updateWearState(player, true) }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                updateWearState(player, true)
            }
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

    private fun handlePlayerCommand(player: ExoPlayer, command: String) {
        when (command) {
            "PLAY" -> player.play()
            "PAUSE" -> player.pause()
            "NEXT" -> if (player.hasNextMediaItem()) player.seekToNext()
            "PREV" -> if (player.hasPreviousMediaItem()) player.seekToPrevious() else player.seekTo(0)
        }
    }

    private fun updateWearState(player: Player, force: Boolean = false) {
        val currentItem = player.currentMediaItem
        val title = currentItem?.mediaMetadata?.title?.toString() ?: getString(R.string.app_name)
        val artist = currentItem?.mediaMetadata?.artist?.toString() ?: getString(R.string.unknown_artist)
        val isPlaying = player.isPlaying
        val pos = player.currentPosition
        val dur = if (player.duration != C.TIME_UNSET && player.duration > 0) player.duration else 1L

        if (!force && title == lastWearTitle && isPlaying == lastWearIsPlaying && abs(pos - lastWearPos) < 1000) return

        lastWearTitle = title
        lastWearIsPlaying = isPlaying
        lastWearPos = pos

        wearUpdateJob?.cancel()
        wearUpdateJob = serviceScope.launch(Dispatchers.IO) {
            val coverFile = File(applicationContext.cacheDir, "widget_cover.png")
            try {
                val artworkData = currentItem?.mediaMetadata?.artworkData
                if (artworkData != null) {
                    val bitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
                    FileOutputStream(coverFile).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                } else {
                    val trackId = currentItem?.mediaId?.toLongOrNull() ?: 0L
                    val saavnTrack = SaavnApi.trackCache[trackId]
                    val imageUrl: Any? = saavnTrack?.coverUrl?.takeIf { it.isNotEmpty() }
                        ?: currentItem?.mediaMetadata?.artworkUri

                    if (imageUrl != null) {
                        val request = ImageRequest.Builder(applicationContext)
                            .data(imageUrl)
                            .size(300)
                            .allowHardware(false)
                            .build()

                        val result = applicationContext.imageLoader.execute(request)
                        val bitmap = result.drawable?.toBitmap()

                        if (bitmap != null) {
                            FileOutputStream(coverFile).use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                            }
                        } else {
                            coverFile.delete()
                        }
                    } else {
                        coverFile.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                coverFile.delete()
            }

            PrefsManager.saveWidgetState(title, artist, isPlaying)

            try {
                PargeliumWidget().updateAll(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val isWearEnabled = PrefsManager.getFossWearEnabled()
            if (isWearEnabled) {
                delay(250)
                fossApiManager?.updatePlaybackState(title, artist, isPlaying, pos, dur, Color.DKGRAY)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        continuousMixManager?.stop()
        continuousMixManager = null

        wearUpdateJob?.cancel()
        serviceScope.cancel()
        fossApiManager?.release()
        fossApiManager = null

        activeAudioProcessor?.release()
        activeAudioProcessor = null

        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
}