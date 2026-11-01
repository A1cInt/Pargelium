package com.alcint.pargelium.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alcint.pargelium.globalAudioLoudness
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AuroraBackground(seedColor: Color, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val baseBackground = MaterialTheme.colorScheme.background
    val defaultColor = MaterialTheme.colorScheme.primary
    val effectiveSeed = if (seedColor == Color.Unspecified || seedColor == Color.Transparent) defaultColor else seedColor

    val targetColor = if (effectiveSeed.luminance() < 0.1f) defaultColor else effectiveSeed
    val animatedColor by animateColorAsState(targetValue = targetColor, animationSpec = tween(1500), label = "color")

    val infiniteTransition = rememberInfiniteTransition(label = "aurora")

    val animationDuration = if (isPlaying) 15000 else 45000
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(animationDuration, easing = LinearEasing), RepeatMode.Restart),
        label = "blob1"
    )
    val slowScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "slowScale"
    )

    val color1 = animatedColor.copy(alpha = 0.6f)
    val color2 = animatedColor.copy(alpha = 0.4f)
    val bgGradientColors = remember(baseBackground) { listOf(Color.Transparent, baseBackground.copy(alpha = 0.5f), baseBackground) }

    val smoothedLoudnessState = remember { mutableFloatStateOf(0f) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppResumed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isAppResumed = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isPlaying, isAppResumed) {
        if (!isPlaying || !isAppResumed) {
            var currentLoudness = smoothedLoudnessState.floatValue
            while (currentLoudness > 0.01f && isActive) {
                currentLoudness *= 0.9f
                smoothedLoudnessState.floatValue = currentLoudness
                delay(32)
            }
            smoothedLoudnessState.floatValue = 0f
            return@LaunchedEffect
        }

        var fast = 0f
        var slow = 0f
        var visualLoudness = 0f

        while (isActive) {
            withFrameNanos {
                val target = globalAudioLoudness
                val safeTarget = if (target.isNaN() || target.isInfinite()) 0f else target

                fast = fast * 0.85f + safeTarget * 0.15f
                slow = slow * 0.98f + safeTarget * 0.02f
                val peak = (fast - slow).coerceAtLeast(0f)

                if (peak > visualLoudness) {
                    visualLoudness = visualLoudness * 0.5f + peak * 0.5f
                } else {
                    visualLoudness *= 0.94f
                }
                smoothedLoudnessState.floatValue = visualLoudness
            }
            delay(16)
        }
    }

    Canvas(modifier = modifier.fillMaxSize().background(baseBackground)) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas

        val loud = smoothedLoudnessState.floatValue
        val currentBeat = 1.0f + (loud * 4.0f).coerceIn(0f, 0.6f)
        val curOffset = offset1
        val curScale = slowScale

        val x1 = width * 0.5f + cos(curOffset) * width * 0.2f
        val y1 = height * 0.4f + sin(curOffset) * height * 0.1f
        val r1 = ((width * 0.8f * curScale) * currentBeat).coerceAtLeast(1f)

        drawCircle(
            brush = Brush.radialGradient(listOf(color1, Color.Transparent), center = Offset(x1, y1), radius = r1),
            center = Offset(x1, y1),
            radius = r1
        )

        val x2 = width * 0.5f + cos(curOffset + 3.14f) * width * 0.3f
        val y2 = height * 0.6f + sin(curOffset + 3.14f) * height * 0.2f
        val r2 = ((width * 0.9f * (2f - curScale)) * currentBeat).coerceAtLeast(1f)

        drawCircle(
            brush = Brush.radialGradient(listOf(color2, Color.Transparent), center = Offset(x2, y2), radius = r2),
            center = Offset(x2, y2),
            radius = r2
        )

        drawRect(brush = Brush.verticalGradient(bgGradientColors))
    }
}