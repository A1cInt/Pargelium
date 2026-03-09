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

    private fun getPrefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = getPrefs(context).getBoolean(PREF_MIX_ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(PREF_MIX_ENABLED, enabled).apply()

    fun getOverlapSeconds(context: Context): Float = getPrefs(context).getFloat(PREF_MIX_OVERLAP, 8f)
    fun setOverlapSeconds(context: Context, seconds: Float) = getPrefs(context).edit().putFloat(PREF_MIX_OVERLAP, seconds).apply()
}

class ContinuousMixManager(
    private val context: Context,
    private val mainPlayer: Player,
    private val mediaSourceFactory: MediaSource.Factory
) {
    private var secondaryPlayer: ExoPlayer? = null
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
                if (isMixing && reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    finishMix()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && isMixing) {
                    finishMix()
                }
            }
        })
    }

    fun start() {
        cachedEnabled = ContinuousMixPrefs.isEnabled(context)
        cachedOverlapMs = (ContinuousMixPrefs.getOverlapSeconds(context) * 1000).toLong()

        monitorJob = mixScope.launch {
            while (isActive) {
                if (!isMixing) {
                    cachedEnabled = ContinuousMixPrefs.isEnabled(context)
                    if (cachedEnabled) {
                        cachedOverlapMs = (ContinuousMixPrefs.getOverlapSeconds(context) * 1000).toLong()
                    }
                }
                checkMixLogic()
                delay(1000)
            }
        }
    }

    fun stop() {
        mixScope.coroutineContext.cancelChildren()
        finishMix()
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

        if (remaining in 1..cachedOverlapMs) {
            startCrossfade(nextIndex, cachedOverlapMs)
        }
    }

    private fun startCrossfade(nextIndex: Int, overlapMs: Long) {
        isMixing = true
        val nextItem = mainPlayer.getMediaItemAt(nextIndex)

        secondaryPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaItem(nextItem)
                volume = 0f
                prepare()

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY && isMixing && mixJob == null) {
                            mixJob = mixScope.launch {
                                executeSmoothCrossfade(overlapMs, nextIndex)
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        finishMix()
                    }
                })
                playWhenReady = true
            }
    }

    private suspend fun executeSmoothCrossfade(overlapMs: Long, nextIndex: Int) {
        while (mixScope.isActive && isMixing) {
            val duration = mainPlayer.duration
            val pos = mainPlayer.currentPosition
            if (duration == C.TIME_UNSET || pos == C.TIME_UNSET) {
                delay(50)
                continue
            }

            val remaining = duration - pos

            if (remaining <= 150) {
                break
            }

            val progress = 1f - (remaining.toFloat() / overlapMs.toFloat()).coerceIn(0f, 1f)

            val newVol = progress.toDouble().pow(1.5).toFloat()
            val oldVol = (1f - progress).toDouble().pow(1.5).toFloat()

            secondaryPlayer?.volume = newVol
            mainPlayer.volume = oldVol

            delay(30)
        }

        if (!isMixing) return

        isAutoSkipping = true

        val targetPos = secondaryPlayer?.currentPosition ?: 0L

        mainPlayer.volume = 0f // Глушим старый
        mainPlayer.seekTo(nextIndex, targetPos)

        var timeout = 0
        while (mainPlayer.playbackState != Player.STATE_READY && timeout < 50) {
            delay(10)
            timeout++
        }

        mainPlayer.volume = 1f
        finishMix()
    }

    private fun finishMix() {
        isMixing = false
        mixJob?.cancel()
        mixJob = null
        mainPlayer.volume = 1f
        secondaryPlayer?.stop()
        secondaryPlayer?.release()
        secondaryPlayer = null
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