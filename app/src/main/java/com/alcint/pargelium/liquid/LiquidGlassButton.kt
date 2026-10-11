package com.alcint.pargelium.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
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
        color = Color.White.copy(alpha = 0.25f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.4f, -0.1f),
            color = Color.White,
            intensity = 1.2f
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.4f),
            color = Color.White,
            intensity = 0.5f
        ),
        dualPeak = true
    )
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val highlight = remember(coroutineScope) {
        InteractiveHighlight(animationScope = coroutineScope)
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "btnScale"
    )

    // Тончайшая вуаль, чтобы стекло не темнело грязно
    val surfaceColor = if (isActive) {
        activeColor.copy(alpha = 0.22f)
    } else {
        if (isInDark) Color(0x221E1E22) else Color(0x35FFFFFF)
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
            // НАСТОЯЩИЙ БЭКДРОП С ПРЕЛОМЛЕНИЕМ ИСПЫТАННЫЙ В BOTTOM BAR
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    blur(8.dp.toPx(), 8.dp.toPx())
                    lens(
                        refractionHeight = 16.dp.toPx(),
                        refractionAmount = 14.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = 0.03f
                    )
                },
                highlight = { ButtonSpecularHighlight },
                onDrawSurface = { drawRect(surfaceColor) }
            )
            .innerShadow(shape) {
                InnerShadow.Default.copy(
                    radius = 8.dp,
                    color = (if (isActive) activeColor else Color.White).copy(alpha = if (isActive) 0.35f else 0.20f)
                )
            }
            .then(highlight.modifier)
            .then(highlight.gestureModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val highlight = remember(coroutineScope) {
        InteractiveHighlight(animationScope = coroutineScope)
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "playScale"
    )

    val shape = RoundedCornerShape(26.dp)

    // Кнопка Play теперь прозрачная линза с ярким акцентным отливом, а не кусок мела
    val playSurface = if (isInDark) Color(0x40FFFFFF) else Color(0x65FFFFFF)

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 20.dp,
                shape = shape,
                spotColor = accentColor.copy(alpha = 0.55f),
                ambientColor = Color.Black.copy(alpha = 0.45f)
            )
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    blur(6.dp.toPx(), 6.dp.toPx())
                    lens(
                        refractionHeight = 24.dp.toPx(),
                        refractionAmount = 20.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = 0.05f
                    )
                },
                highlight = {
                    ButtonSpecularHighlight.copy(
                        width = 1.5.dp
                    )
                },
                onDrawSurface = { drawRect(playSurface) }
            )
            .innerShadow(shape) {
                InnerShadow.Default.copy(
                    radius = 12.dp,
                    color = Color.White.copy(alpha = 0.60f)
                )
            }
            .then(highlight.modifier)
            .then(highlight.gestureModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(38.dp),
            tint = Color.White
        )
    }
}