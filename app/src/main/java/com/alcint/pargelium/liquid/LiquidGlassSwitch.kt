package com.alcint.pargelium.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isInDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
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

    Spacer(
        modifier = modifier
            .width(56.dp)
            .height(32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .shadow(
                elevation = if (checked) 5.dp else 2.dp,
                shape = CircleShape,
                spotColor = if (checked) primaryColor.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.25f)
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .drawWithCache {
                val trackCorner = CornerRadius(size.height / 2f, size.height / 2f)
                val strokeW = 1.dp.toPx()
                val padPx = 3.dp.toPx()
                val thumbDiameter = size.height - padPx * 2f
                val thumbRadius = thumbDiameter / 2f
                val travelPx = (size.width - size.height).coerceAtLeast(0f)

                val innerDepthBrush = Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.24f),
                    0.35f to Color.Black.copy(alpha = 0.05f),
                    0.75f to Color.Transparent,
                    1.0f to Color.White.copy(alpha = 0.08f)
                )

                val rimBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (checked) 0.52f else if (isInDark) 0.28f else 0.45f),
                        (if (checked) primaryColor else Color.White).copy(alpha = if (isInDark) 0.12f else 0.18f)
                    )
                )

                val thumbLensBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White,
                        Color(0xFFF2F4F8),
                        Color(0xFFE4E8F0)
                    )
                )

                val thumbSpecularBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color.White.copy(alpha = 0.0f)
                    ),
                    startY = 0f,
                    endY = thumbDiameter * 0.52f
                )

                onDrawBehind {
                    // 1. Фон стеклянного желоба + мягкая внутренняя тень глубины (без тяжелого BlurEffect)
                    drawRoundRect(
                        color = trackColor,
                        cornerRadius = trackCorner
                    )
                    drawRoundRect(
                        brush = innerDepthBrush,
                        cornerRadius = trackCorner
                    )
                    drawRoundRect(
                        brush = rimBrush,
                        cornerRadius = trackCorner,
                        style = Stroke(width = strokeW)
                    )

                    val centerX = padPx + thumbRadius + progress * travelPx
                    val centerY = size.height * 0.5f

                    // 2. Световой ореол под каплей
                    if (progress > 0.02f) {
                        val glowRadius = size.height * 0.72f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.34f * progress),
                                    Color.Transparent
                                ),
                                center = Offset(centerX, centerY),
                                radius = glowRadius
                            ),
                            center = Offset(centerX, centerY),
                            radius = glowRadius
                        )
                    }

                    // 3. 3D капля-бегунок с упругой деформацией, тенью, зеркальным бликом и неоновым ядром
                    val sx = motionStretch * pressStretch
                    val sy = pressSquishY

                    withTransform({
                        translate(left = centerX, top = centerY)
                        scale(scaleX = sx, scaleY = sy, pivot = Offset.Zero)
                    }) {
                        // Мягкая контактная тень под каплей
                        drawCircle(
                            color = if (checked) primaryColor.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.22f),
                            radius = thumbRadius + 1.5.dp.toPx(),
                            center = Offset(0f, 1.5.dp.toPx())
                        )

                        // Тело капли
                        drawCircle(
                            brush = thumbLensBrush,
                            radius = thumbRadius,
                            center = Offset.Zero
                        )

                        // Верхний зеркальный блик линзы
                        drawOval(
                            brush = thumbSpecularBrush,
                            topLeft = Offset(-thumbRadius * 0.64f, -thumbRadius * 0.84f),
                            size = Size(thumbDiameter * 0.64f, thumbDiameter * 0.38f)
                        )

                        // Кромка капли
                        drawCircle(
                            color = Color.White.copy(alpha = 0.85f),
                            radius = thumbRadius,
                            center = Offset.Zero,
                            style = Stroke(width = strokeW)
                        )

                        // Акцентная микро-линза в центре
                        if (progress > 0.05f) {
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.85f * progress),
                                radius = (thumbDiameter * 0.16f) * progress,
                                center = Offset.Zero
                            )
                        }
                    }
                }
            }
    )
}