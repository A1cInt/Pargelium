package com.alcint.pargelium.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isInDark = isSystemInDarkTheme()
    val shape = CircleShape
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f),
        label = "switchProgress"
    )

    val thumbStretch by animateFloatAsState(
        targetValue = if (isPressed) 1.25f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 450f),
        label = "thumbStretch"
    )

    val activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isInDark) 0.65f else 0.80f)
    val inactiveTrackColor = if (isInDark) Color(0x33FFFFFF) else Color(0x1F000000)

    val trackColor by animateColorAsState(
        targetValue = if (checked) activeTrackColor else inactiveTrackColor,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "trackColor"
    )

    val rimBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isInDark) 0.28f else 0.45f),
            Color.White.copy(alpha = if (isInDark) 0.05f else 0.10f)
        )
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .shadow(
                elevation = 4.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.25f)
            )
            .clip(shape)
            .background(trackColor)
            .innerShadow(shape) {
                InnerShadow.Default.copy(
                    radius = 6.dp,
                    color = Color.Black.copy(alpha = 0.20f)
                )
            }
            .border(width = 1.dp, brush = rimBrush, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch
            ) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val travelDistance = 20.dp

        Box(
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    translationX = progress * travelDistance.toPx()
                    scaleX = thumbStretch
                }
                .shadow(
                    elevation = 3.dp,
                    shape = CircleShape,
                    spotColor = Color.Black.copy(alpha = 0.35f)
                )
                .clip(CircleShape)
                .background(Color.White)
                .border(
                    width = 0.5.dp,
                    color = Color.Black.copy(alpha = 0.08f),
                    shape = CircleShape
                )
        )
    }
}