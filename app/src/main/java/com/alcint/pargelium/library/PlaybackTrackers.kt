package com.alcint.pargelium.library

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.PrefsManager
import kotlinx.coroutines.delay

@Composable
fun HeadlessPlaybackTracker(
    playerController: MediaController?,
    currentTrack: AudioTrack?,
    isPlaying: Boolean
) {
    var hasCountedPlay by remember(currentTrack?.id) { mutableStateOf(false) }

    LaunchedEffect(isPlaying, currentTrack?.id, playerController) {
        val player = playerController ?: return@LaunchedEffect
        val track = currentTrack ?: return@LaunchedEffect

        var ticksSinceSave = 0
        while (isPlaying) {
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration
            val thresholdMs = if (dur in 1..60_000L) (dur / 2L).coerceAtLeast(10_000L) else 30_000L

            if (!hasCountedPlay && pos >= thresholdMs) {
                PrefsManager.recordPlay(track.id)
                hasCountedPlay = true
            }

            ticksSinceSave++
            if (ticksSinceSave >= 5) {
                PrefsManager.saveLastPosition(pos)
                ticksSinceSave = 0
            }

            delay(if (hasCountedPlay) 6000L else 2500L)
        }
    }
}

@Composable
fun PlayerPositionProvider(
    playerController: MediaController?,
    isPlaying: Boolean,
    pollIntervalMs: Long = 800L,
    content: @Composable (currentPositionProvider: () -> Long) -> Unit
) {
    var pos by remember { mutableLongStateOf(playerController?.currentPosition ?: 0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isUiVisible by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isUiVisible = event.targetState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(playerController) {
        val player = playerController ?: return@DisposableEffect onDispose {}
        pos = player.currentPosition.coerceAtLeast(0L)

        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                pos = newPosition.positionMs.coerceAtLeast(0L)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                pos = player.currentPosition.coerceAtLeast(0L)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                pos = player.currentPosition.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(isPlaying, isUiVisible, playerController, pollIntervalMs) {
        val player = playerController ?: return@LaunchedEffect
        pos = player.currentPosition.coerceAtLeast(0L)
        while (isPlaying && isUiVisible) {
            pos = player.currentPosition.coerceAtLeast(0L)
            delay(pollIntervalMs)
        }
    }

    content { pos }
}