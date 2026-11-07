package com.alcint.pargelium

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.alcint.pargelium.liquid.LiquidGlassSwitch
import com.alcint.pargelium.settings.IconContainer
import com.alcint.pargelium.settings.SettingsGroup
import kotlinx.coroutines.*
import kotlin.math.cos
import kotlin.math.sin

object ContinuousMixPrefs {
    private const val PREFS_NAME = "pargelium_mix_prefs"
    private const val PREF_MIX_ENABLED = "continuous_mix_enabled"
    private const val PREF_MIX_OVERLAP = "continuous_mix_overlap"

    @Volatile
    var cachedEnabled: Boolean = false
        private set

    @Volatile
    var cachedOverlapSeconds: Float = 8f
        private set

    private var initialized = false
    var onConfigChanged: (() -> Unit)? = null

    private fun getPrefs(context: Context): SharedPreferences {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!initialized) {
            cachedEnabled = prefs.getBoolean(PREF_MIX_ENABLED, false)
            cachedOverlapSeconds = prefs.getFloat(PREF_MIX_OVERLAP, 8f)
            initialized = true
        }
        return prefs
    }

    fun isEnabled(context: Context): Boolean {
        getPrefs(context)
        return cachedEnabled
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        cachedEnabled = enabled
        getPrefs(context).edit().putBoolean(PREF_MIX_ENABLED, enabled).apply()
        onConfigChanged?.invoke()
    }

    fun getOverlapSeconds(context: Context): Float {
        getPrefs(context)
        return cachedOverlapSeconds
    }

    fun setOverlapSeconds(context: Context, seconds: Float) {
        cachedOverlapSeconds = seconds
        getPrefs(context).edit().putFloat(PREF_MIX_OVERLAP, seconds).apply()
        onConfigChanged?.invoke()
    }
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

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (isAutoSkipping) {
                isAutoSkipping = false
                return
            }
            if (isMixing && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK || reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)) {
                abortMix()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncMonitorState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && isMixing) {
                fadeOutPlayer?.pause()
            } else if (playWhenReady && isMixing) {
                fadeOutPlayer?.play()
            }
            syncMonitorState()
        }
    }

    init {
        ContinuousMixPrefs.isEnabled(appContext)
        mainPlayer.addListener(playerListener)
        ContinuousMixPrefs.onConfigChanged = {
            syncMonitorState()
        }
    }

    fun start() {
        syncMonitorState()
    }

    /**
     * Запускает корутину мониторинга ТОЛЬКО когда кроссфейд включен и плеер реально воспроизводит трек.
     * На паузе или при выключенном миксе корутина полностью остановлена (0% нагрузки на батарею).
     */
    private fun syncMonitorState() {
        val shouldMonitor = ContinuousMixPrefs.cachedEnabled && mainPlayer.isPlaying
        if (!shouldMonitor) {
            monitorJob?.cancel()
            monitorJob = null
            return
        }
        if (monitorJob?.isActive == true) return

        monitorJob = mixScope.launch {
            while (isActive && ContinuousMixPrefs.cachedEnabled && mainPlayer.isPlaying) {
                val sleepMs = if (!isMixing) {
                    checkMixLogicAndGetNextDelay()
                } else {
                    500L
                }
                delay(sleepMs)
            }
        }
    }

    fun stop() {
        ContinuousMixPrefs.onConfigChanged = null
        mainPlayer.removeListener(playerListener)
        monitorJob?.cancel()
        monitorJob = null
        mixScope.coroutineContext.cancelChildren()
        abortMix()
    }

    private fun checkMixLogicAndGetNextDelay(): Long {
        if (!ContinuousMixPrefs.cachedEnabled || isMixing) return 1500L
        if (mainPlayer.playbackState != Player.STATE_READY || !mainPlayer.playWhenReady) return 1500L

        val nextIndex = mainPlayer.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return 2000L

        val duration = mainPlayer.duration
        val currentPos = mainPlayer.currentPosition
        if (duration == C.TIME_UNSET || currentPos == C.TIME_UNSET || duration <= 15_000L) return 2000L

        val overlapMs = (ContinuousMixPrefs.cachedOverlapSeconds * 1000f).toLong()
        val remaining = duration - currentPos

        return when {
            remaining in 1..(overlapMs + 400L) -> {
                startCrossfade(overlapMs)
                500L
            }
            remaining > overlapMs + 6000L -> {
                // Вдали от конца трека просыпаемся редко
                2000L
            }
            else -> {
                // В последние 6 секунд перед сведением проверяем каждые 250 мс
                250L
            }
        }
    }

    private fun startCrossfade(overlapMs: Long) {
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
                delay(25)
            }
        }

        while (mixScope.isActive && isMixing) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= overlapMs) break

            val progress = (elapsed.toFloat() / overlapMs.toFloat()).coerceIn(0f, 1f)
            val newVol = sin(progress * Math.PI / 2).toFloat()
            val oldVol = cos(progress * Math.PI / 2).toFloat()

            mainPlayer.volume = newVol
            fader.volume = oldVol

            delay(40)
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

    SettingsGroup {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconContainer(
                    icon = Icons.Rounded.GraphicEq,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.continuous_mix_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.continuous_mix_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LiquidGlassSwitch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        ContinuousMixPrefs.setEnabled(context, it)
                    }
                )
            }

            AnimatedVisibility(visible = isEnabled) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.continuous_mix_overlap_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = stringResource(id = R.string.continuous_mix_seconds_fmt, overlapSeconds.toInt()),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
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