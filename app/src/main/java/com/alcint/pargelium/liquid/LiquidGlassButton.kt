package com.alcint.pargelium.liquid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource

private val ButtonSpecularHighlight = Highlight(
    width = 1.2.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.28f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.4f, -0.1f),
            color = Color.White,
            intensity = 1.25f
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.4f),
            color = Color.White,
            intensity = 0.55f
        ),
        dualPeak = true
    )
)

private val PlayButtonSpecularHighlight = ButtonSpecularHighlight.copy(width = 1.6.dp)

private val DefaultIconInnerShadow = InnerShadow(
    radius = 8.dp,
    offset = DpOffset(0.dp, 3.dp),
    color = Color.White.copy(alpha = 0.22f)
)

private val PlayButtonInnerShadow = InnerShadow(
    radius = 12.dp,
    offset = DpOffset(0.dp, 4.dp),
    color = Color.White.copy(alpha = 0.55f)
)

@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    shape: Shape = CircleShape,
    activeColor: Color = Color.Transparent,
    isActive: Boolean = false,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val isInDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val highlight = remember(coroutineScope) {
        InteractiveHighlight(animationScope = coroutineScope)
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 450f),
        label = "btnScale"
    )

    val surfaceColor = remember(isActive, activeColor, isInDark, isPressed) {
        when {
            isPressed -> (if (activeColor != Color.Transparent) activeColor else Color.White).copy(alpha = 0.26f)
            isActive -> activeColor.copy(alpha = 0.22f)
            isInDark -> Color(0x241E1E24)
            else -> Color(0x36FFFFFF)
        }
    }

    val cachedInnerShadow = remember(isActive, activeColor) {
        if (isActive && activeColor != Color.Transparent) {
            InnerShadow(
                radius = 8.dp,
                offset = DpOffset(0.dp, 3.dp),
                color = activeColor.copy(alpha = 0.38f)
            )
        } else {
            DefaultIconInnerShadow
        }
    }

    val rimBrush = remember(isActive, activeColor, isPressed) {
        val topTint = if (isActive && activeColor != Color.Transparent) activeColor else Color.White
        Brush.verticalGradient(
            colors = listOf(
                topTint.copy(alpha = if (isPressed) 0.58f else 0.36f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isActive) 12.dp else 6.dp,
                shape = shape,
                spotColor = if (isActive) activeColor.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f)
            )
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    blur(7.dp.toPx(), 7.dp.toPx())
                    lens(
                        refractionHeight = 15.dp.toPx(),
                        refractionAmount = 13.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = 0.025f
                    )
                },
                highlight = { ButtonSpecularHighlight },
                onDrawSurface = { drawRect(surfaceColor) }
            )
            .innerShadow(shape) { cachedInnerShadow }
            .border(width = 0.8.dp, brush = rimBrush, shape = shape)
            .then(highlight.modifier)
            .then(highlight.gestureModifier)
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
        content()
    }
}

@Composable
fun LiquidGlassPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp
) {
    val coroutineScope = rememberCoroutineScope()
    val isInDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val highlight = remember(coroutineScope) {
        InteractiveHighlight(animationScope = coroutineScope)
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.60f, stiffness = 420f),
        label = "playScale"
    )

    // Плавный морфинг линзы: круг на паузе -> сквиркл при воспроизведении -> компактный сквиркл при нажатии
    val cornerRadius by animateDpAsState(
        targetValue = when {
            isPressed -> 20.dp
            isPlaying -> 26.dp
            else -> size / 2
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "playCornerMorph"
    )

    val shape = RoundedCornerShape(cornerRadius)

    val playSurfaceBrush = remember(accentColor, isInDark, isPlaying) {
        val baseWhite = if (isInDark) Color(0x42FFFFFF) else Color(0x64FFFFFF)
        val accentTint = accentColor.copy(alpha = if (isPlaying) 0.34f else 0.20f)
        Brush.linearGradient(
            colors = listOf(baseWhite, accentTint)
        )
    }

    val outerRimBrush = remember(accentColor, isPlaying) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.72f),
                accentColor.copy(alpha = if (isPlaying) 0.55f else 0.30f),
                Color.White.copy(alpha = 0.18f)
            )
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isPlaying) 22.dp else 16.dp,
                shape = shape,
                spotColor = accentColor.copy(alpha = 0.65f),
                ambientColor = Color.Black.copy(alpha = 0.45f)
            )
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    blur(6.dp.toPx(), 6.dp.toPx())
                    lens(
                        refractionHeight = 22.dp.toPx(),
                        refractionAmount = 18.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = 0.04f
                    )
                },
                highlight = { PlayButtonSpecularHighlight },
                onDrawSurface = { drawRect(brush = playSurfaceBrush) }
            )
            .innerShadow(shape) { PlayButtonInnerShadow }
            .border(width = 1.2.dp, brush = outerRimBrush, shape = shape)
            .then(highlight.modifier)
            .then(highlight.gestureModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.65f, stiffness = 500f), initialScale = 0.55f) + fadeIn())
                    .togetherWith(scaleOut(spring(stiffness = 500f), targetScale = 0.55f) + fadeOut())
            },
            label = "liquidPlayPauseIcon"
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = Color.White
            )
        }
    }
}