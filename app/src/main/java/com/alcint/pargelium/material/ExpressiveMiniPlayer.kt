@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
package com.alcint.pargelium.material

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alcint.pargelium.AudioTrack
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ExpressiveMiniPlayer(
    track: AudioTrack,
    isPlaying: Boolean,
    progress: Float,
    albumArt: Uri?,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val swipeOffsetX = remember { Animatable(0f) }

    val toolbarCorner by animateDpAsState(
        targetValue = if (isPressed) 20.dp else 28.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "toolbarCorner"
    )

    val toolbarScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "toolbarScale"
    )

    val coverCorner by animateDpAsState(
        targetValue = if (isPlaying) 16.dp else 26.dp,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "coverCorner"
    )

    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.92f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "coverScale"
    )

    val imageRequest = remember(context, albumArt) {
        albumArt?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(144)
                .crossfade(true)
                .build()
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBgColor = MaterialTheme.colorScheme.surfaceContainerHighest

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .graphicsLayer {
                scaleX = toolbarScale
                scaleY = toolbarScale
                translationX = swipeOffsetX.value
            }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch {
                        swipeOffsetX.snapTo((swipeOffsetX.value + delta * 0.55f).coerceIn(-140f, 140f))
                    }
                },
                onDragStopped = {
                    if (abs(swipeOffsetX.value) > 72f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSkipNext()
                    }
                    scope.launch {
                        swipeOffsetX.animateTo(0f, motionScheme.defaultSpatialSpec())
                    }
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(toolbarCorner),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .graphicsLayer {
                            scaleX = coverScale
                            scaleY = coverScale
                        }
                        .clip(RoundedCornerShape(coverCorner))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageRequest != null) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                ExpressivePlayButton(
                    isPlaying = isPlaying,
                    onClick = onPlayPause,
                    size = 48.dp,
                    iconSize = 24.dp
                )

                Spacer(modifier = Modifier.width(6.dp))

                ExpressiveIconButton(
                    icon = Icons.Default.SkipNext,
                    onClick = onSkipNext,
                    size = 42.dp,
                    iconSize = 22.dp
                )
            }

            Spacer(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .drawWithCache {
                        val corner = CornerRadius(size.height / 2f, size.height / 2f)
                        onDrawBehind {
                            drawRoundRect(color = trackBgColor, cornerRadius = corner)
                            val p = progress.coerceIn(0f, 1f)
                            if (p > 0.005f) {
                                drawRoundRect(
                                    color = primaryColor,
                                    size = Size((size.width * p).coerceAtLeast(size.height), size.height),
                                    cornerRadius = corner
                                )
                            }
                        }
                    }
            )
        }
    }
}