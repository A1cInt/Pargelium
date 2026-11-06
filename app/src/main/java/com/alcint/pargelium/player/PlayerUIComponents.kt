@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.alcint.pargelium.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    iconTint: Color,
    isVinylMode: Boolean = false,
    onToggleVinylMode: () -> Unit = {},
    isOnlineCanvasEnabled: Boolean = true,
    onToggleOnlineCanvas: (Boolean) -> Unit = {},
    canvasSourceMode: CanvasSourceMode = CanvasSourceMode.BOTH_SPOTIFY_PRIORITY,
    onCanvasSourceModeChange: (CanvasSourceMode) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCollapse,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.White.copy(alpha = 0.08f)
            )
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(id = R.string.action_collapse),
                modifier = Modifier.size(28.dp),
                tint = Color.White
            )
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleVinylMode()
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isVinylMode) iconTint.copy(alpha = 0.25f) else Color.Transparent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = stringResource(id = R.string.player_vinyl_mode_cd),
                        tint = if (isVinylMode) iconTint else Color.White
                    )
                }

                var showCanvasMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showCanvasMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = stringResource(id = R.string.background),
                            tint = when {
                                hasCanvas -> iconTint
                                !isOnlineCanvasEnabled -> Color.White.copy(alpha = 0.45f)
                                else -> Color.White
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = showCanvasMenu,
                        onDismissRequest = { showCanvasMenu = false },
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(id = R.string.choose_canvas),
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            onClick = {
                                showCanvasMenu = false
                                onPickCanvas()
                            }
                        )
                        if (hasCanvas) {
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.reset_canvas)) },
                                onClick = {
                                    showCanvasMenu = false
                                    onResetCanvas()
                                }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(
                                        id = if (isOnlineCanvasEnabled) R.string.online_canvas_enabled_label
                                        else R.string.online_canvas_disabled_label
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            trailingIcon = {
                                Switch(
                                    checked = isOnlineCanvasEnabled,
                                    onCheckedChange = { onToggleOnlineCanvas(it) }
                                )
                            },
                            onClick = { onToggleOnlineCanvas(!isOnlineCanvasEnabled) }
                        )

                        if (isOnlineCanvasEnabled) {
                            CanvasSourceMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    trailingIcon = {
                                        if (canvasSourceMode == mode) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = iconTint
                                            )
                                        }
                                    },
                                    onClick = {
                                        onCanvasSourceModeChange(mode)
                                        showCanvasMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onBookmarksClick) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = stringResource(id = R.string.bookmarks),
                        tint = Color.White
                    )
                }

                val hasTranslation = lyrics.any { !it.translation.isNullOrBlank() }
                IconButton(onClick = onTranslateClick) {
                    if (isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = stringResource(id = R.string.translation),
                            tint = if (hasTranslation) iconTint else Color.White
                        )
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleLyrics()
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (showLyrics) iconTint.copy(alpha = 0.28f) else Color.Transparent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = stringResource(id = R.string.action_lyrics),
                        tint = if (showLyrics) iconTint else Color.White
                    )
                }
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
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.basicMarquee()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.artist,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.88f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onArtistClick(track.artist) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .basicMarquee()
            )
            if (track.album.isNotBlank() && !track.album.equals("<unknown>", ignoreCase = true)) {
                Text(
                    text = "• ${track.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp)
                )
            }
        }
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
    val haptic = LocalHapticFeedback.current

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
                Text(
                    text = stringResource(id = R.string.saavn_stream_quality_badge),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = iconTint
                )
            }
        } else {
            Text(
                text = stringResource(R.string.status_live_broadcast),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Red,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    } else {
        var sliderPosition by remember { mutableFloatStateOf(0f) }
        var isDragging by remember { mutableStateOf(false) }
        var lastHapticBucket by remember { mutableIntStateOf(-1) }
        var showRemainingTime by remember { mutableStateOf(false) }

        LaunchedEffect(currentPosition) {
            if (!isDragging) sliderPosition = currentPosition.toFloat()
        }

        val animatedWaveHeight by animateDpAsState(
            targetValue = if (isPlaying && !isDragging) 15.dp else 0.dp,
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            label = "waveHeight"
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn(tween(150)) + scaleIn(tween(150)),
                exit = fadeOut(tween(150)) + scaleOut(tween(150))
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = iconTint,
                    contentColor = Color.Black,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "${formatDuration(sliderPosition.toLong())} / ${formatDuration(duration)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }

            WavySlider(
                value = sliderPosition,
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                onValueChange = { newVal ->
                    if (!isDragging) {
                        isDragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    sliderPosition = newVal
                    val bucket = (newVal / 15_000f).toInt()
                    if (bucket != lastHapticBucket) {
                        lastHapticBucket = bucket
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                onValueChangeFinished = {
                    onSeek(sliderPosition.toLong())
                    isDragging = false
                    lastHapticBucket = -1
                },
                waveHeight = animatedWaveHeight,
                waveLength = 45.dp,
                waveThickness = 11.dp,
                trackThickness = 11.dp,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = iconTint,
                    inactiveTrackColor = Color.White.copy(alpha = 0.26f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(if (isDragging) sliderPosition.toLong() else currentPosition),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.78f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSeek((currentPosition - 10_000L).coerceAtLeast(0L))
                        },
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = stringResource(id = R.string.seek_10s_short),
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = stringResource(id = R.string.seek_10s_short),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    val saavnTrack = SaavnApi.trackCache[track.id]
                    if (saavnTrack != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null, tint = iconTint, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = stringResource(id = R.string.saavn_service_name),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = iconTint
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSeek((currentPosition + 10_000L).coerceAtMost(duration))
                        },
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.seek_10s_short),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = stringResource(id = R.string.seek_10s_short),
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                val rightTimeText = if (showRemainingTime) {
                    val rem = (duration - (if (isDragging) sliderPosition.toLong() else currentPosition)).coerceAtLeast(0L)
                    "-${formatDuration(rem)}"
                } else {
                    formatDuration(duration)
                }

                Text(
                    text = rightTimeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showRemainingTime = !showRemainingTime }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
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
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showSignalChainDialog by remember { mutableStateOf(false) }

    if (showSignalChainDialog) {
        AlertDialog(
            onDismissRequest = { showSignalChainDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = iconTint)
            },
            title = {
                Text(
                    text = stringResource(id = R.string.signal_path_dialog_title),
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SignalPathNode(
                        step = stringResource(id = R.string.signal_path_step_source),
                        detail = metadata.ifEmpty { stringResource(R.string.meta_unknown) },
                        accentColor = iconTint
                    )
                    SignalPathNode(
                        step = stringResource(id = R.string.signal_path_step_engine),
                        detail = stringResource(id = R.string.signal_path_engine_desc),
                        accentColor = iconTint
                    )
                    SignalPathNode(
                        step = stringResource(id = R.string.signal_path_step_output),
                        detail = "${audioOutput.name} (${audioOutput.typeLabel})",
                        accentColor = iconTint
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSignalChainDialog = false
                    openSystemAudioOutputSwitcher(context)
                }) {
                    Icon(Icons.Rounded.SpeakerGroup, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(id = R.string.action_switch_audio_output))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignalChainDialog = false }) {
                    Text(stringResource(id = R.string.album_action_close))
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                openSystemAudioOutputSwitcher(context)
            },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.12f),
            modifier = Modifier
                .height(28.dp)
                .border(0.5.dp, Color.White.copy(alpha = 0.16f), CircleShape)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Icon(
                    painter = painterResource(audioOutput.iconResId),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = audioOutput.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showTrackInfoBar) {
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showSignalChainDialog = true
                },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .height(28.dp)
                    .border(0.5.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = metadata.ifEmpty { stringResource(R.string.meta_unknown) },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalPathNode(
    step: String,
    detail: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
