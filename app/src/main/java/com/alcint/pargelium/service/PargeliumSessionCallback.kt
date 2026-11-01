package com.alcint.pargelium.service

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PargeliumSessionCallback : MediaLibrarySession.Callback {
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
            val cleanMetadata = item.mediaMetadata.buildUpon()
                .setArtworkData(null, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build()

            item.buildUpon()
                .setUri(item.requestMetadata.mediaUri ?: item.localConfiguration?.uri)
                .setMediaMetadata(cleanMetadata)
                .build()
        }
        return Futures.immediateFuture(updatedMediaItems)
    }
}