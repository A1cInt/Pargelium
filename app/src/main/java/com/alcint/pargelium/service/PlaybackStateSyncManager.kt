package com.alcint.pargelium.service

import android.content.Context
import android.graphics.Color
import android.os.Handler
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.appwidget.updateAll
import androidx.media3.common.C
import androidx.media3.common.Player
import coil.imageLoader
import coil.request.ImageRequest
import com.alcint.pargelium.FossWearApiManager
import com.alcint.pargelium.PargeliumWidget
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.R
import com.alcint.pargelium.SaavnApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class PlaybackStateSyncManager(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val playerHandler: Handler,
    private val player: Player
) {
    private var fossApiManager: FossWearApiManager? = null
    private var wearUpdateJob: Job? = null

    private var lastWearTitle = ""
    private var lastWearIsPlaying = false
    private var lastWearPos = -1L

    init {
        fossApiManager = FossWearApiManager(context, serviceScope) { command ->
            playerHandler.post { handlePlayerCommand(command) }
        }
        fossApiManager?.syncTheme(PrefsManager.getThemeMode())
    }

    private fun handlePlayerCommand(command: String) {
        when (command) {
            "PLAY" -> player.play()
            "PAUSE" -> player.pause()
            "NEXT" -> if (player.hasNextMediaItem()) player.seekToNext()
            "PREV" -> if (player.hasPreviousMediaItem()) player.seekToPrevious() else player.seekTo(0)
        }
    }

    fun updateWearState(force: Boolean = false) {
        val currentItem = player.currentMediaItem
        val title = currentItem?.mediaMetadata?.title?.toString() ?: context.getString(R.string.app_name)
        val artist = currentItem?.mediaMetadata?.artist?.toString() ?: context.getString(R.string.unknown_artist)
        val isPlaying = player.isPlaying
        val pos = player.currentPosition
        val dur = if (player.duration != C.TIME_UNSET && player.duration > 0) player.duration else 1L

        if (!force && title == lastWearTitle && isPlaying == lastWearIsPlaying && abs(pos - lastWearPos) < 1000) return

        lastWearTitle = title
        lastWearIsPlaying = isPlaying
        lastWearPos = pos

        wearUpdateJob?.cancel()
        wearUpdateJob = serviceScope.launch(Dispatchers.IO) {
            val coverFile = File(context.cacheDir, "widget_cover.png")
            try {
                val trackId = currentItem?.mediaId?.toLongOrNull() ?: 0L
                val saavnTrack = SaavnApi.trackCache[trackId]
                val imageUrl: Any? = saavnTrack?.coverUrl?.takeIf { it.isNotEmpty() }
                    ?: currentItem?.mediaMetadata?.artworkUri

                if (imageUrl != null) {
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(300)
                        .allowHardware(false)
                        .build()

                    val result = context.imageLoader.execute(request)
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
            } catch (e: Exception) {
                e.printStackTrace()
                coverFile.delete()
            }

            PrefsManager.saveWidgetState(title, artist, isPlaying)

            try {
                PargeliumWidget().updateAll(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (PrefsManager.getFossWearEnabled()) {
                delay(250)
                fossApiManager?.updatePlaybackState(title, artist, isPlaying, pos, dur, Color.DKGRAY)
            }
        }
    }

    fun release() {
        wearUpdateJob?.cancel()
        fossApiManager?.release()
        fossApiManager = null
    }
}