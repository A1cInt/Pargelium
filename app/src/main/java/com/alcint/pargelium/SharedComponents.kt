@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alcint.pargelium

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alcint.pargelium.liquid.InnerShadow
import com.alcint.pargelium.liquid.innerShadow
import com.alcint.pargelium.liquid.lens
import com.alcint.pargelium.liquid.vibrancy
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import kotlin.math.abs

private val CapsuleHighlight = Highlight(
    width = 1.25.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.26f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.45f, -0.1f),
            color = Color.White,
            intensity = 1.15f
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.85f, -0.4f),
            color = Color.White,
            intensity = 0.40f
        ),
        dualPeak = true
    )
)

private val CapsuleShapeProvider: () -> Shape = { CircleShape }
private val CapsuleHighlightProvider: () -> Highlight = { CapsuleHighlight }

private val MiniPlayerBackdropEffects: BackdropEffectScope.() -> Unit = {
    vibrancy()
    blur(12.dp.toPx(), 12.dp.toPx())
    lens(
        refractionHeight = 14.dp.toPx(),
        refractionAmount = 10.dp.toPx(),
        depthEffect = false,
        chromaticAberration = 0f
    )
}

private val MiniPlayerDarkInnerShadow = InnerShadow(
    radius = 8.dp,
    offset = DpOffset(0.dp, 2.dp),
    color = Color.White.copy(alpha = 0.14f)
)

private val MiniPlayerLightInnerShadow = InnerShadow(
    radius = 8.dp,
    offset = DpOffset(0.dp, 2.dp),
    color = Color.White.copy(alpha = 0.42f)
)

private val DarkShadowProvider: () -> InnerShadow = { MiniPlayerDarkInnerShadow }
private val LightShadowProvider: () -> InnerShadow = { MiniPlayerLightInnerShadow }

@Composable
fun FloatingActionRow(
    onRefresh: () -> Unit,
    onShuffleAll: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.padding(end = 8.dp)) {
            SmallActionBtn(Icons.Default.Shuffle, onClick = onShuffleAll)
        }

        Box(modifier = Modifier.padding(end = 16.dp)) {
            SmallActionBtn(Icons.Default.MoreVert) { showMenu = true }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = RoundedCornerShape(16.dp)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.action_refresh_library)) },
                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                    onClick = {
                        showMenu = false
                        onRefresh()
                    }
                )
            }
        }
    }
}

@Composable
fun SmallActionBtn(icon: ImageVector, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.size(40.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MiniPlayer(
    track: AudioTrack,
    isPlaying: Boolean,
    progress: Float,
    albumArt: Uri?,
    backdrop: Backdrop,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isInDark = isSystemInDarkTheme()
    val accentColor = MaterialTheme.colorScheme.primary
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    val latestProgress by rememberUpdatedState(progress)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val swipeOffsetX = remember { Animatable(0f) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "miniPlayerScale"
    )

    val capsuleShape = CircleShape

    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 320f),
        label = "coverScale"
    )

    val coverCorner by animateDpAsState(
        targetValue = if (isPlaying) 16.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f),
        label = "coverCorner"
    )

    val actualArt: Uri? = remember(track.id, albumArt) {
        val saavnTrack = SaavnApi.trackCache[track.id]
        if (saavnTrack != null && saavnTrack.coverUrl.isNotEmpty()) {
            Uri.parse(saavnTrack.coverUrl)
        } else {
            albumArt
        }
    }

    val imageRequest = remember(context, actualArt) {
        actualArt?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(144)
                .crossfade(false)
                .build()
        }
    }

    val rimBrush = remember(isInDark, accentColor) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isInDark) 0.36f else 0.62f),
                accentColor.copy(alpha = if (isInDark) 0.20f else 0.25f),
                Color.White.copy(alpha = if (isInDark) 0.08f else 0.18f)
            )
        )
    }

    val glassSurfaceColor = remember(isInDark, surfaceContainerColor) {
        surfaceContainerColor.copy(alpha = if (isInDark) 0.58f else 0.72f)
    }

    val shadowProvider = if (isInDark) DarkShadowProvider else LightShadowProvider

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(68.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                translationX = swipeOffsetX.value
            }
            .shadow(
                elevation = 14.dp,
                shape = capsuleShape,
                spotColor = accentColor.copy(alpha = 0.35f),
                ambientColor = Color.Black.copy(alpha = 0.32f)
            )
            .clip(capsuleShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = CapsuleShapeProvider,
                effects = MiniPlayerBackdropEffects,
                highlight = CapsuleHighlightProvider,
                onDrawSurface = { drawRect(color = glassSurfaceColor) }
            )
            .innerShadow(capsuleShape, shadowProvider)
            .border(width = 1.dp, brush = rimBrush, shape = capsuleShape)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        val next = (swipeOffsetX.value + delta * 0.55f).coerceIn(-140f, 140f)
                        swipeOffsetX.snapTo(next)
                    }
                },
                onDragStopped = {
                    val finalX = swipeOffsetX.value
                    if (abs(finalX) > 72f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSkipNext()
                    }
                    scope.launch {
                        swipeOffsetX.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f)
                        )
                    }
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 12.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = coverScale
                        scaleY = coverScale
                    }
                    .shadow(
                        elevation = if (isPlaying) 8.dp else 2.dp,
                        shape = RoundedCornerShape(coverCorner),
                        spotColor = accentColor.copy(alpha = 0.55f)
                    )
                    .clip(RoundedCornerShape(coverCorner))
                    .border(
                        width = 0.8.dp,
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(coverCorner)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (imageRequest != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = onSurfaceVariantColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = onSurfaceColor,
                    modifier = Modifier.basicMarquee(iterations = 2)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }
                    Text(
                        text = track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = onSurfaceVariantColor
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniPlayerCrystalPlayButton(
                    isPlaying = isPlaying,
                    accentColor = accentColor,
                    isInDark = isInDark,
                    onClick = onPlayPause
                )

                MiniPlayerCrystalIconButton(
                    icon = Icons.Default.SkipNext,
                    tint = onSurfaceColor,
                    isInDark = isInDark,
                    onClick = onSkipNext
                )
            }
        }

        MiniPlayerGlowProgressBar(
            progressProvider = { latestProgress },
            accentColor = accentColor,
            isInDark = isInDark,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 26.dp, vertical = 3.dp)
                .fillMaxWidth()
                .height(3.dp)
        )
    }
}

@Composable
private fun MiniPlayerCrystalPlayButton(
    isPlaying: Boolean,
    accentColor: Color,
    isInDark: Boolean,
    onClick: () -> Unit,
    size: Dp = 44.dp
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 480f),
        label = "miniPlayScale"
    )

    val cornerRadius by animateDpAsState(
        targetValue = when {
            isPressed -> 12.dp
            isPlaying -> 15.dp
            else -> size / 2
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "miniPlayCorner"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isPlaying) {
            accentColor.copy(alpha = if (isInDark) 0.88f else 0.92f)
        } else {
            if (isInDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "miniPlayBg"
    )

    val iconTint = if (isPlaying) {
        if (accentColor.luminance() > 0.55f) Color(0xFF101014) else Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val rimBrush = remember(isPlaying, accentColor) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.55f),
                accentColor.copy(alpha = 0.25f)
            )
        )
    }

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isPlaying) 8.dp else 0.dp,
                shape = shape,
                spotColor = accentColor.copy(alpha = 0.65f)
            )
            .clip(shape)
            .background(bgColor)
            .border(width = 0.9.dp, brush = rimBrush, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.65f, stiffness = 500f), initialScale = 0.5f) + fadeIn())
                    .togetherWith(scaleOut(spring(stiffness = 500f), targetScale = 0.5f) + fadeOut())
            },
            label = "miniPlayPauseMorph"
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = iconTint
            )
        }
    }
}

@Composable
private fun MiniPlayerCrystalIconButton(
    icon: ImageVector,
    tint: Color,
    isInDark: Boolean,
    onClick: () -> Unit,
    size: Dp = 40.dp
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 480f),
        label = "miniNextScale"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 12.dp else size / 2,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "miniNextCorner"
    )

    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val bg = if (isInDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
    val rimColor = Color.White.copy(alpha = if (isInDark) 0.26f else 0.45f)

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bg)
            .border(width = 0.8.dp, color = rimColor, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = tint
        )
    }
}

@Composable
private fun MiniPlayerGlowProgressBar(
    progressProvider: () -> Float,
    accentColor: Color,
    isInDark: Boolean,
    modifier: Modifier = Modifier
) {
    val trackColor = Color.White.copy(alpha = if (isInDark) 0.14f else 0.26f)

    Spacer(
        modifier = modifier.drawWithCache {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            val progressBrush = Brush.horizontalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.65f),
                    accentColor,
                    Color.White.copy(alpha = 0.9f)
                )
            )
            onDrawBehind {
                drawRoundRect(
                    color = trackColor,
                    cornerRadius = radius
                )
                val p = progressProvider().coerceIn(0f, 1f)
                if (p > 0.005f) {
                    val activeW = (size.width * p).coerceAtLeast(size.height)
                    drawRoundRect(
                        brush = progressBrush,
                        size = Size(activeW, size.height),
                        cornerRadius = radius
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = 0.55f),
                        radius = size.height * 1.3f,
                        center = Offset(activeW, size.height * 0.5f)
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingPillIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    ContainedLoadingIndicator(
        modifier = modifier.size(56.dp),
        indicatorColor = color,
        containerColor = trackColor
    )
}