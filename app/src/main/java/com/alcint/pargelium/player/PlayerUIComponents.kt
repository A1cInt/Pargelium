@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.alcint.pargelium.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.LyricLine
import com.alcint.pargelium.R
import com.alcint.pargelium.SaavnApi
import com.alcint.pargelium.liquid.LiquidGlassIconButton
import com.alcint.pargelium.liquid.LiquidGlassPlayPauseButton
import ir.mahozad.multiplatform.wavyslider.material3.WavySlider
import top.yukonga.miuix.kmp.blur.LayerBackdrop

@Composable
fun PlayerTopBar(
    onCollapse: () -> Unit,
    hasCanvas: Boolean,
    onPickCanvas: () -> Unit,
    onResetCanvas: () -> Unit,
    onBookmarksClick: () -> Unit,
    lyrics: List<LyricLine>,
    isTranslating: Boolean,
    onTranslateClick: () -> Unit,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    iconTint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Default.KeyboardArrowDown, stringResource(id = R.string.action_collapse), modifier = Modifier.size(32.dp), tint = Color.White)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            var showCanvasMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showCanvasMenu = true }) {
                    Icon(Icons.Default.Movie, stringResource(id = R.string.background), tint = Color.White)
                }
                DropdownMenu(expanded = showCanvasMenu, onDismissRequest = { showCanvasMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.choose_canvas)) },
                        onClick = { showCanvasMenu = false; onPickCanvas() }
                    )
                    if (hasCanvas) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.reset_canvas)) },
                            onClick = { showCanvasMenu = false; onResetCanvas() }
                        )
                    }
                }
            }

            IconButton(onClick = onBookmarksClick) {
                Icon(Icons.Default.Flag, stringResource(id = R.string.bookmarks), tint = Color.White)
            }

            val hasTranslation = lyrics.any { !it.translation.isNullOrBlank() }
            IconButton(onClick = onTranslateClick) {
                if (isTranslating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Language, stringResource(id = R.string.translation), tint = if (hasTranslation) iconTint else Color.White)
                }
            }

            IconButton(
                onClick = onToggleLyrics,
                colors = IconButtonDefaults.iconButtonColors(containerColor = if (showLyrics) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            ) {
                Icon(Icons.Default.FormatQuote, stringResource(id = R.string.action_lyrics), tint = Color.White)
            }
        }
    }
}

@Composable
fun TrackTitleArtist(
    track: AudioTrack,
    onArtistClick: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.basicMarquee()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = track.artist,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(0.9f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onArtistClick(track.artist) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .basicMarquee()
        )
    }
}

@Composable
fun PlayerProgressSlider(
    track: AudioTrack,
    isRadio: Boolean,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    iconTint: Color,
    onSeek: (Long) -> Unit
) {
    if (isRadio) {
        val saavnTrack = SaavnApi.trackCache[track.id]
        LinearWavyProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = if (saavnTrack != null) iconTint else Color.Red,
            trackColor = Color.White.copy(alpha = 0.2f)
        )
        if (saavnTrack != null) {
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = iconTint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("JioSaavn • MP4A 320kbps", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = iconTint)
            }
        } else {
            Text(stringResource(R.string.status_live_broadcast), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
        }
    } else {
        var sliderPosition by remember { mutableFloatStateOf(0f) }
        var isDragging by remember { mutableStateOf(false) }
        LaunchedEffect(currentPosition) { if (!isDragging) sliderPosition = currentPosition.toFloat() }

        val animatedWaveHeight by animateDpAsState(
            targetValue = if (isPlaying && !isDragging) 16.dp else 0.dp,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            label = "waveHeight"
        )

        WavySlider(
            value = sliderPosition,
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            onValueChange = { isDragging = true; sliderPosition = it },
            onValueChangeFinished = { onSeek(sliderPosition.toLong()); isDragging = false },
            waveHeight = animatedWaveHeight,
            waveLength = 45.dp,
            waveThickness = 12.dp,
            trackThickness = 12.dp,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = iconTint,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(if (isDragging) sliderPosition.toLong() else currentPosition), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f))

            val saavnTrack = SaavnApi.trackCache[track.id]
            if (saavnTrack != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, tint = iconTint, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("JioSaavn", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = iconTint)
                }
            }

            Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f))
        }
    }
}

@Composable
fun PlayerControlsRow(
    repeatMode: Int,
    isPlaying: Boolean,
    iconTint: Color,
    showQueue: Boolean,
    playerBackdrop: LayerBackdrop,
    onToggleRepeat: () -> Unit,
    onSkipPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleQueue: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
        val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF

        LiquidGlassIconButton(
            onClick = onToggleRepeat,
            backdrop = playerBackdrop,
            size = 48.dp,
            activeColor = iconTint,
            isActive = isRepeatActive
        ) {
            Icon(
                imageVector = repeatIcon,
                contentDescription = stringResource(id = R.string.action_repeat),
                tint = if (isRepeatActive) iconTint else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidGlassIconButton(onClick = onSkipPrevious, backdrop = playerBackdrop, size = 52.dp) {
                Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White)
            }

            LiquidGlassPlayPauseButton(
                isPlaying = isPlaying,
                onClick = onPlayPause,
                accentColor = iconTint,
                backdrop = playerBackdrop,
                size = 76.dp
            )

            LiquidGlassIconButton(onClick = onSkipNext, backdrop = playerBackdrop, size = 52.dp) {
                Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White)
            }
        }

        LiquidGlassIconButton(
            onClick = onToggleQueue,
            backdrop = playerBackdrop,
            size = 48.dp,
            activeColor = iconTint,
            isActive = showQueue
        ) {
            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun PlayerBottomInfo(
    audioOutput: AudioOutputState,
    iconTint: Color,
    showTrackInfoBar: Boolean,
    metadata: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.height(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Icon(painterResource(audioOutput.iconResId), null, tint = iconTint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(audioOutput.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
            }
        }

        if (showTrackInfoBar) {
            Spacer(Modifier.width(8.dp))
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.1f), modifier = Modifier.height(24.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(metadata.ifEmpty { stringResource(R.string.meta_unknown) }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
    }
}