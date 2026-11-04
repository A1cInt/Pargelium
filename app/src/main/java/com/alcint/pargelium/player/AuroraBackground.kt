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
import androidx.compose.ui.graphics.toArgb
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
fun AuroraBackground(
    seedColor: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val baseBackground = MaterialTheme.colorScheme.background
    val defaultColor = MaterialTheme.colorScheme.primary
    val effectiveSeed = if (seedColor == Color.Unspecified || seedColor == Color.Transparent) defaultColor else seedColor

    val targetColor = remember(effectiveSeed, defaultColor) {
        if (effectiveSeed.luminance() < 0.08f) defaultColor else effectiveSeed
    }

    val animatedColorState = animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(1400, easing = LinearEasing),
        label = "auroraColor"
    )

    val bgGradientBrush = remember(baseBackground) {
        Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = 0.18f),
                baseBackground.copy(alpha = 0.48f),
                baseBackground
            )
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

            val rotationSpeed = if (isPlaying) TWO_PI / 14f else TWO_PI / 45f
            animState.offset = (animState.offset + rotationSpeed * dt) % TWO_PI

            animState.scalePhase = (animState.scalePhase + (TWO_PI / 16f) * dt) % TWO_PI
            animState.slowScale = 1.0f - 0.12f * cos(animState.scalePhase)

            if (isPlaying) {
                val target = globalAudioLoudness
                val safeTarget = if (target.isNaN() || target.isInfinite()) 0f else target

                val stepFactor = (dt * 30f).coerceIn(0.5f, 3f)
                animState.fast += (safeTarget - animState.fast) * (0.16f * stepFactor).coerceAtMost(1f)
                animState.slow += (safeTarget - animState.slow) * (0.02f * stepFactor).coerceAtMost(1f)
                val peak = (animState.fast - animState.slow).coerceAtLeast(0f)

                if (peak > animState.loudness) {
                    animState.loudness = animState.loudness * 0.45f + peak * 0.55f
                } else {
                    animState.loudness *= 0.93f
                }
            } else {
                animState.loudness *= 0.86f
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
        val currentBeat = 1.0f + (loud * 4.2f).coerceIn(0f, 0.65f)
        val curOffset = animState.offset
        val curScale = animState.slowScale

        val cosOffset = cos(curOffset)
        val sinOffset = sin(curOffset)

        // Первое основное сияние
        val x1 = width * 0.48f + cosOffset * width * 0.22f
        val y1 = height * 0.34f + sinOffset * height * 0.12f
        val r1 = ((width * 0.82f * curScale) * currentBeat).coerceAtLeast(1f)

        withTransform({
            translate(left = x1, top = y1)
            scale(scaleX = r1, scaleY = r1, pivot = Offset.Zero)
        }) {
            drawCircle(brush = brushCache.brush1, radius = 1f, center = Offset.Zero)
        }

        // Второе противофазное сияние
        val x2 = width * 0.52f - cosOffset * width * 0.28f
        val y2 = height * 0.62f - sinOffset * height * 0.18f
        val r2 = ((width * 0.88f * (2f - curScale)) * currentBeat).coerceAtLeast(1f)

        withTransform({
            translate(left = x2, top = y2)
            scale(scaleX = r2, scaleY = r2, pivot = Offset.Zero)
        }) {
            drawCircle(brush = brushCache.brush2, radius = 1f, center = Offset.Zero)
        }

        // Третье комплементарное пятно для глубины
        val x3 = width * 0.5f + sinOffset * width * 0.24f
        val y3 = height * 0.22f - cosOffset * height * 0.1f
        val r3 = ((width * 0.65f * curScale) * (0.95f + loud * 2f)).coerceAtLeast(1f)

        withTransform({
            translate(left = x3, top = y3)
            scale(scaleX = r3, scaleY = r3, pivot = Offset.Zero)
        }) {
            drawCircle(brush = brushCache.brush3, radius = 1f, center = Offset.Zero)
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

    var brush1: Brush = Brush.radialGradient(listOf(Color.Transparent, Color.Transparent), Offset.Zero, 1f)
        private set
    var brush2: Brush = brush1
        private set
    var brush3: Brush = brush1
        private set

    fun updateIfNeeded(color: Color) {
        if (color != lastColor) {
            lastColor = color
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color.toArgb(), hsv)
            hsv[0] = (hsv[0] + 38f) % 360f
            val shiftedColor = Color(android.graphics.Color.HSVToColor(hsv))

            brush1 = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.62f), Color.Transparent),
                center = Offset.Zero,
                radius = 1f
            )
            brush2 = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.42f), Color.Transparent),
                center = Offset.Zero,
                radius = 1f
            )
            brush3 = Brush.radialGradient(
                colors = listOf(shiftedColor.copy(alpha = 0.34f), Color.Transparent),
                center = Offset.Zero,
                radius = 1f
            )
        }
    }
}