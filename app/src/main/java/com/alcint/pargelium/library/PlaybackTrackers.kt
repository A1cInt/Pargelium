package com.alcint.pargelium.library

import androidx.compose.runtime.*
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
    var hasCountedPlay by remember(currentTrack) { mutableStateOf(false) }

    LaunchedEffect(isPlaying, currentTrack) {
        var lastSaveTime = System.currentTimeMillis()
        while (isPlaying) {
            playerController?.let {
                val pos = it.currentPosition
                if (!hasCountedPlay && pos > 40000 && currentTrack != null) {
                    PrefsManager.recordPlay(currentTrack.id)
                    hasCountedPlay = true
                }
                val now = System.currentTimeMillis()
                if (now - lastSaveTime > 3000) {
                    PrefsManager.saveLastPosition(pos)
                    lastSaveTime = now
                }
            }
            delay(1000)
        }
    }
}

@Composable
fun PlayerPositionProvider(
    playerController: MediaController?,
    isPlaying: Boolean,
    content: @Composable (currentPositionProvider: () -> Long) -> Unit
) {
    var pos by remember { mutableLongStateOf(playerController?.currentPosition ?: 0L) }

    LaunchedEffect(isPlaying, playerController) {
        while (isPlaying) {
            playerController?.let { pos = it.currentPosition }
            delay(500)
        }
    }

    content { pos }
}