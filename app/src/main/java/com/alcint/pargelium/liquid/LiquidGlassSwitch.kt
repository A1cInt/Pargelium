package com.alcint.pargelium.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val CachedTrackInnerShadow = InnerShadow(
    radius = 5.dp,
    offset = DpOffset(0.dp, 2.dp),
    color = Color.Black.copy(alpha = 0.24f)
)

@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isInDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
    val shape = CircleShape
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val primaryColor = MaterialTheme.colorScheme.primary

    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "switchProgress"
    )

    // Физика жидкой капли: при движении (progress ~ 0.5) или нажатии капля растягивается по горизонтали
    val motionStretch = 1f + (0.22f * (1f - abs(progress * 2f - 1f)))
    val pressStretch by animateFloatAsState(
        targetValue = if (isPressed) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 500f),
        label = "pressStretch"
    )
    val pressSquishY by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f),
        label = "pressSquishY"
    )

    val activeTrackColor = primaryColor.copy(alpha = if (isInDark) 0.58f else 0.74f)
    val inactiveTrackColor = if (isInDark) Color(0x30FFFFFF) else Color(0x22000000)

    val trackColor by animateColorAsState(
        targetValue = if (checked) activeTrackColor else inactiveTrackColor,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "trackColor"
    )

    val rimBrush = remember(isInDark, checked, primaryColor) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (checked) 0.52f else if (isInDark) 0.28f else 0.45f),
                (if (checked) primaryColor else Color.White).copy(alpha = if (isInDark) 0.12f else 0.18f)
            )
        )
    }

    val thumbLensBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.White,
                Color(0xFFF2F4F8),
                Color(0xFFE4E8F0)
            )
        )
    }

    val thumbRimBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.White,
                Color.White.copy(alpha = 0.35f)
            )
        )
    }

    Box(
        modifier = modifier
            .width(56.dp)
            .height(32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .shadow(
                elevation = if (checked) 6.dp else 3.dp,
                shape = shape,
                spotColor = if (checked) primaryColor.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.25f)
            )
            .clip(shape)
            .background(trackColor)
            .innerShadow(shape) { CachedTrackInnerShadow }
            .border(width = 1.dp, brush = rimBrush, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val travelDistance = 24.dp

        // Внутренний световой ореол под каплей
        if (progress > 0.02f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = (size.height * 0.5f) + progress * (size.width - size.height)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.35f * progress),
                            Color.Transparent
                        ),
                        center = Offset(centerX, size.height * 0.5f),
                        radius = size.height * 0.75f
                    ),
                    center = Offset(centerX, size.height * 0.5f),
                    radius = size.height * 0.75f
                )
            }
        }

        // 3D капля-бегунок с бликом линзы и светящимся ядром
        Box(
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    translationX = progress * travelDistance.toPx()
                    scaleX = motionStretch * pressStretch
                    scaleY = pressSquishY
                }
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    spotColor = if (checked) primaryColor.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.35f)
                )
                .clip(CircleShape)
                .background(thumbLensBrush)
                .border(
                    width = 1.dp,
                    brush = thumbRimBrush,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Верхний зеркальный блик капли + акцентная микро-линза в центре
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawOval(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            Color.White.copy(alpha = 0.0f)
                        ),
                        startY = 0f,
                        endY = size.height * 0.52f
                    ),
                    topLeft = Offset(size.width * 0.18f, size.height * 0.08f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.38f)
                )

                if (progress > 0.05f) {
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.85f * progress),
                        radius = (size.minDimension * 0.16f) * progress,
                        center = center
                    )
                }
            }
        }
    }
}