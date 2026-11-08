@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
package com.alcint.pargelium.material

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ExpressivePlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    iconSize: Dp = 32.dp
) {
    val motionScheme = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerRadius by animateDpAsState(
        targetValue = when {
            isPressed -> size * 0.20f
            isPlaying -> size * 0.30f
            else -> size / 2
        },
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "expressivePlayCorner"
    )

    val scaleX by animateFloatAsState(
        targetValue = if (isPressed) 1.07f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "expressivePlayScaleX"
    )
    val scaleY by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "expressivePlayScaleY"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isPlaying) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "expressivePlayContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isPlaying) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "expressivePlayContentColor"
    )

    FilledIconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = scaleY
            },
        shape = RoundedCornerShape(cornerRadius),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (scaleIn(motionScheme.fastSpatialSpec(), initialScale = 0.45f) +
                        fadeIn(motionScheme.fastEffectsSpec()))
                    .togetherWith(
                        scaleOut(motionScheme.fastSpatialSpec(), targetScale = 0.45f) +
                                fadeOut(motionScheme.fastEffectsSpec())
                    )
            },
            label = "play_pause_morph"
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun ExpressiveIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    isActive: Boolean = false
) {
    val motionScheme = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerRadius by animateDpAsState(
        targetValue = when {
            isPressed -> size * 0.22f
            isActive -> size * 0.30f
            else -> size / 2
        },
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "expressiveIconCorner"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "expressiveIconScale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "expressiveIconBg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "expressiveIconTint"
    )

    FilledTonalIconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(cornerRadius),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize)
        )
    }
}