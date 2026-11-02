package com.alcint.pargelium.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alcint.pargelium.globalAudioLoudness
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (2 * PI).toFloat()

@Composable
fun AuroraBackground(seedColor: Color, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val baseBackground = MaterialTheme.colorScheme.background
    val defaultColor = MaterialTheme.colorScheme.primary
    val effectiveSeed = if (seedColor == Color.Unspecified || seedColor == Color.Transparent) defaultColor else seedColor

    val targetColor = remember(effectiveSeed, defaultColor) {
        if (effectiveSeed.luminance() < 0.1f) defaultColor else effectiveSeed
    }

    val animatedColorState = animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(1500, easing = LinearEasing),
        label = "color"
    )

    val bgGradientBrush = remember(baseBackground) {
        Brush.verticalGradient(
            listOf(Color.Transparent, baseBackground.copy(alpha = 0.5f), baseBackground)
        )
    }

    var frameTick by remember { mutableIntStateOf(0) }
    val animState = remember { AuroraAnimState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isAppResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isPlaying, isAppResumed) {
        if (!isAppResumed) return@LaunchedEffect

        var lastNanos = withFrameNanos { it }
        var skipFrame = false

        while (isActive && (isPlaying || animState.loudness > 0.002f)) {
            val frameNanos = withFrameNanos { it }
            skipFrame = !skipFrame
            if (skipFrame) continue

            val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.25f)
            lastNanos = frameNanos

            val rotationSpeed = if (isPlaying) TWO_PI / 15f else TWO_PI / 45f
            animState.offset = (animState.offset + rotationSpeed * dt) % TWO_PI

            animState.scalePhase = (animState.scalePhase + (TWO_PI / 16f) * dt) % TWO_PI
            animState.slowScale = 1.0f - 0.1f * cos(animState.scalePhase)

            if (isPlaying) {
                val target = globalAudioLoudness
                val safeTarget = if (target.isNaN() || target.isInfinite()) 0f else target

                val stepFactor = (dt * 30f).coerceIn(0.5f, 3f)
                animState.fast += (safeTarget - animState.fast) * (0.15f * stepFactor).coerceAtMost(1f)
                animState.slow += (safeTarget - animState.slow) * (0.02f * stepFactor).coerceAtMost(1f)
                val peak = (animState.fast - animState.slow).coerceAtLeast(0f)

                if (peak > animState.loudness) {
                    animState.loudness = animState.loudness * 0.5f + peak * 0.5f
                } else {
                    animState.loudness *= 0.94f
                }
            } else {
                animState.loudness *= 0.88f
                if (animState.loudness <= 0.002f) {
                    animState.loudness = 0f
                    animState.fast = 0f
                    animState.slow = 0f
                }
            }

            frameTick++
        }
    }

    val brushCache = remember { AuroraBrushCache() }

    Canvas(modifier = modifier.fillMaxSize().background(baseBackground)) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        @Suppress("UNUSED_VARIABLE")
        val tick = frameTick
        val currentColor = animatedColorState.value
        brushCache.updateIfNeeded(currentColor)

        val loud = animState.loudness
        val currentBeat = 1.0f + (loud * 4.0f).coerceIn(0f, 0.6f)
        val curOffset = animState.offset
        val curScale = animState.slowScale

        val cosOffset = cos(curOffset)
        val sinOffset = sin(curOffset)

        val x1 = width * 0.5f + cosOffset * width * 0.2f
        val y1 = height * 0.4f + sinOffset * height * 0.1f
        val r1 = ((width * 0.8f * curScale) * currentBeat).coerceAtLeast(1f)

        withTransform({
            translate(left = x1, top = y1)
            scale(scaleX = r1, scaleY = r1, pivot = Offset.Zero)
        }) {
            drawCircle(
                brush = brushCache.brush1,
                radius = 1f,
                center = Offset.Zero
            )
        }

        val x2 = width * 0.5f - cosOffset * width * 0.3f
        val y2 = height * 0.6f - sinOffset * height * 0.2f
        val r2 = ((width * 0.9f * (2f - curScale)) * currentBeat).coerceAtLeast(1f)

        withTransform({
            translate(left = x2, top = y2)
            scale(scaleX = r2, scaleY = r2, pivot = Offset.Zero)
        }) {
            drawCircle(
                brush = brushCache.brush2,
                radius = 1f,
                center = Offset.Zero
            )
        }

        drawRect(brush = bgGradientBrush)
    }
}

@Stable
private class AuroraAnimState {
    var offset: Float = 0f
    var scalePhase: Float = 0f
    var slowScale: Float = 0.9f
    var loudness: Float = 0f
    var fast: Float = 0f
    var slow: Float = 0f
}

private class AuroraBrushCache {
    private var lastColor: Color = Color.Unspecified

    var brush1: Brush = Brush.radialGradient(
        colors = listOf(Color.Transparent, Color.Transparent),
        center = Offset.Zero,
        radius = 1f
    )
        private set

    var brush2: Brush = brush1
        private set

    fun updateIfNeeded(color: Color) {
        if (color != lastColor) {
            lastColor = color
            brush1 = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.6f), Color.Transparent),
                center = Offset.Zero,
                radius = 1f
            )
            brush2 = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.4f), Color.Transparent),
                center = Offset.Zero,
                radius = 1f
            )
        }
    }
}