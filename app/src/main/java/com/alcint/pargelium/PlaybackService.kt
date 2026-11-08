package com.alcint.pargelium

import android.content.Intent
import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.alcint.pargelium.service.PargeliumSessionCallback
import com.alcint.pargelium.service.PlaybackStateSyncManager
import com.alcint.pargelium.service.PlayerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@UnstableApi
class PlaybackService : MediaLibraryService() {

    companion object {
        var activeAudioProcessor: CustomAudioProcessor? = null
            private set
    }

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var stateSyncManager: PlaybackStateSyncManager? = null
    private var continuousMixManager: ContinuousMixManager? = null
    private var playerListener: Player.Listener? = null

    override fun onCreate() {
        super.onCreate()
        PrefsManager.init(applicationContext)

        val customProcessor = CustomAudioProcessor()
        activeAudioProcessor = customProcessor

        val components = PlayerFactory.create(applicationContext, customProcessor)
        val player = components.player

        continuousMixManager = ContinuousMixManager(applicationContext, player, components.mediaSourceFactory)
        continuousMixManager?.start()

        val playerHandler = Handler(player.applicationLooper)
        stateSyncManager = PlaybackStateSyncManager(applicationContext, serviceScope, playerHandler, player)

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { stateSyncManager?.updateWearState() }
            override fun onPlaybackStateChanged(playbackState: Int) { stateSyncManager?.updateWearState() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { stateSyncManager?.updateWearState(force = true) }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                stateSyncManager?.updateWearState(force = true)
            }
        }
        playerListener = listener
        player.addListener(listener)

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, PargeliumSessionCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        continuousMixManager?.stop()
        continuousMixManager = null

        mediaLibrarySession?.player?.let { player ->
            playerListener?.let { player.removeListener(it) }
        }
        playerListener = null

        stateSyncManager?.release()
        stateSyncManager = null
        serviceScope.cancel()

        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null

        activeAudioProcessor?.release()
        activeAudioProcessor = null

        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
}