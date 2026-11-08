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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val motionScheme = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "expressiveSwitchProgress"
    )

    val trackCorner by animateDpAsState(
        targetValue = if (isPressed) 12.dp else 16.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "expressiveTrackCorner"
    )

    val thumbCorner by animateDpAsState(
        targetValue = when {
            isPressed -> 8.dp
            checked -> 10.dp
            else -> 12.dp
        },
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "expressiveThumbCorner"
    )

    val thumbBaseSize by animateDpAsState(
        targetValue = if (checked || isPressed) 24.dp else 18.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "expressiveThumbSize"
    )

    val midFlightStretch = 1f + 0.20f * (1f - abs(progress * 2f - 1f))
    val pressStretch by animateFloatAsState(
        targetValue = if (isPressed) 1.16f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "expressiveThumbStretch"
    )

    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "expressiveTrackColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "expressiveBorderColor"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "expressiveThumbColor"
    )

    val iconTint by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "expressiveIconTint"
    )

    val trackShape = RoundedCornerShape(trackCorner)

    Box(
        modifier = modifier
            .width(54.dp)
            .height(32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(trackShape)
            .background(trackColor)
            .border(width = if (checked) 0.dp else 1.5.dp, color = borderColor, shape = trackShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val maxTravel = 22.dp

        Box(
            modifier = Modifier
                .size(thumbBaseSize)
                .graphicsLayer {
                    translationX = progress * maxTravel.toPx()
                    scaleX = midFlightStretch * pressStretch
                }
                .clip(RoundedCornerShape(thumbCorner))
                .background(thumbColor),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = checked,
                transitionSpec = {
                    (scaleIn(motionScheme.fastSpatialSpec(), initialScale = 0.4f) +
                            fadeIn(motionScheme.fastEffectsSpec()))
                        .togetherWith(
                            scaleOut(motionScheme.fastSpatialSpec(), targetScale = 0.4f) +
                                    fadeOut(motionScheme.fastEffectsSpec())
                        )
                },
                label = "switchIconMorph"
            ) { isChecked ->
                Icon(
                    imageVector = if (isChecked) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(if (isChecked) 15.dp else 12.dp)
                )
            }
        }
    }
}