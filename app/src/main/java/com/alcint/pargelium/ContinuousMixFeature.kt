package com.alcint.pargelium

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.*
import kotlin.math.pow

object ContinuousMixPrefs {
    private const val PREFS_NAME = "pargelium_mix_prefs"
    private const val PREF_MIX_ENABLED = "continuous_mix_enabled"
    private const val PREF_MIX_OVERLAP = "continuous_mix_overlap"

    private fun getPrefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = getPrefs(context).getBoolean(PREF_MIX_ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(PREF_MIX_ENABLED, enabled).apply()

    fun getOverlapSeconds(context: Context): Float = getPrefs(context).getFloat(PREF_MIX_OVERLAP, 8f)
    fun setOverlapSeconds(context: Context, seconds: Float) = getPrefs(context).edit().putFloat(PREF_MIX_OVERLAP, seconds).apply()
}

class ContinuousMixManager(
    context: Context,
    private val mainPlayer: Player,
    private val mediaSourceFactory: MediaSource.Factory
) {
    private val appContext = context.applicationContext

    private var fadeOutPlayer: ExoPlayer? = null

    private var isMixing = false
    private var isAutoSkipping = false

    private val mixScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mixJob: Job? = null
    private var monitorJob: Job? = null

    private var cachedEnabled = false
    private var cachedOverlapMs = 8000L

    init {
        mainPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isAutoSkipping) {
                    isAutoSkipping = false
                    return
                }
                if (isMixing && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK || reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)) {
                    abortMix()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && isMixing) {
                    fadeOutPlayer?.pause()
                } else if (playWhenReady && isMixing) {
                    fadeOutPlayer?.play()
                }
            }
        })
    }

    fun start() {
        updatePrefs()

        monitorJob = mixScope.launch {
            while (isActive) {
                if (!isMixing) {
                    updatePrefs()
                }
                checkMixLogic()

                delay(200)
            }
        }
    }

    private fun updatePrefs() {
        cachedEnabled = ContinuousMixPrefs.isEnabled(appContext)
        if (cachedEnabled) {
            cachedOverlapMs = (ContinuousMixPrefs.getOverlapSeconds(appContext) * 1000).toLong()
        }
    }

    fun stop() {
        mixScope.coroutineContext.cancelChildren()
        abortMix()
    }

    private fun checkMixLogic() {
        if (!cachedEnabled || isMixing) return
        if (mainPlayer.playbackState != Player.STATE_READY || !mainPlayer.playWhenReady) return

        val nextIndex = mainPlayer.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return

        val duration = mainPlayer.duration
        val currentPos = mainPlayer.currentPosition
        if (duration == C.TIME_UNSET || currentPos == C.TIME_UNSET) return

        val remaining = duration - currentPos

        if (remaining > 0 && remaining <= cachedOverlapMs + 500) {
            startCrossfade(nextIndex, cachedOverlapMs)
        }
    }

    private fun startCrossfade(nextIndex: Int, overlapMs: Long) {
        isMixing = true
        isAutoSkipping = true

        val currentItem = mainPlayer.currentMediaItem ?: return
        val currentPos = mainPlayer.currentPosition

        fadeOutPlayer = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaItem(currentItem)
                volume = mainPlayer.volume
                prepare()
                seekTo(currentPos)

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY && isMixing && mixJob == null) {
                            playWhenReady = true

                            mainPlayer.volume = 0f
                            mainPlayer.seekToNextMediaItem()
                            mainPlayer.playWhenReady = true

                            mixJob = mixScope.launch {
                                executeSmoothCrossfade(overlapMs)
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        abortMix()
                    }
                })
            }
    }

    private suspend fun executeSmoothCrossfade(overlapMs: Long) {
        val fader = fadeOutPlayer ?: return
        val startTime = System.currentTimeMillis()

        withTimeoutOrNull(2000) {
            while (mainPlayer.playbackState != Player.STATE_READY) {
                delay(20)
            }
        }

        while (mixScope.isActive && isMixing) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= overlapMs) {
                break
            }

            val progress = (elapsed.toFloat() / overlapMs.toFloat()).coerceIn(0f, 1f)

            val newVol = kotlin.math.sin(progress * Math.PI / 2).toFloat()
            val oldVol = kotlin.math.cos(progress * Math.PI / 2).toFloat()

            mainPlayer.volume = newVol
            fader.volume = oldVol

            delay(30)
        }

        finishMix()
    }

    private fun finishMix() {
        if (!isMixing) return
        isMixing = false
        mixJob?.cancel()
        mixJob = null

        mainPlayer.volume = 1f

        fadeOutPlayer?.stop()
        fadeOutPlayer?.release()
        fadeOutPlayer = null
    }

    private fun abortMix() {
        isMixing = false
        isAutoSkipping = false
        mixJob?.cancel()
        mixJob = null

        mainPlayer.volume = 1f

        fadeOutPlayer?.stop()
        fadeOutPlayer?.release()
        fadeOutPlayer = null
    }
}

@Composable
fun ContinuousMixSettingsCard(context: Context) {
    var isEnabled by remember { mutableStateOf(ContinuousMixPrefs.isEnabled(context)) }
    var overlapSeconds by remember { mutableFloatStateOf(ContinuousMixPrefs.getOverlapSeconds(context)) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Continuous Mix", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Умное бесшовное наложение треков (Crossfade)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        ContinuousMixPrefs.setEnabled(context, it)
                    }
                )
            }

            AnimatedVisibility(visible = isEnabled) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Text("Длина наложения: ${overlapSeconds.toInt()} сек.", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = overlapSeconds,
                        onValueChange = { overlapSeconds = it },
                        onValueChangeFinished = { ContinuousMixPrefs.setOverlapSeconds(context, overlapSeconds) },
                        valueRange = 2f..15f
                    )
                }
            }
        }
    }
}