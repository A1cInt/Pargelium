package com.alcint.pargelium.liquid

import android.annotation.SuppressLint
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun Modifier.bgEffectDraw(
    painter: BgEffectPainter,
    preset: BgEffectConfig.Config,
    deviceType: DeviceType,
    isDarkTheme: Boolean,
    surface: Color,
    effectBackground: Boolean,
    isFullSize: Boolean,
    playing: Boolean,
    colorStage: () -> Float,
    alpha: () -> Float,
): Modifier = this then BgEffectElement(
    painter = painter,
    preset = preset,
    deviceType = deviceType,
    isDarkTheme = isDarkTheme,
    surface = surface,
    effectBackground = effectBackground,
    isFullSize = isFullSize,
    playing = playing,
    colorStage = colorStage,
    alpha = alpha,
)

@SuppressLint("ModifierNodeInspectableProperties")
private data class BgEffectElement(
    val painter: BgEffectPainter,
    val preset: BgEffectConfig.Config,
    val deviceType: DeviceType,
    val isDarkTheme: Boolean,
    val surface: Color,
    val effectBackground: Boolean,
    val isFullSize: Boolean,
    val playing: Boolean,
    val colorStage: () -> Float,
    val alpha: () -> Float,
) : ModifierNodeElement<BgEffectNode>() {

    override fun create(): BgEffectNode = BgEffectNode(
        painter = painter,
        preset = preset,
        deviceType = deviceType,
        isDarkTheme = isDarkTheme,
        surface = surface,
        effectBackground = effectBackground,
        isFullSize = isFullSize,
        playing = playing,
        colorStage = colorStage,
        alpha = alpha,
    )

    override fun update(node: BgEffectNode) {
        node.update(
            painter = painter,
            preset = preset,
            deviceType = deviceType,
            isDarkTheme = isDarkTheme,
            surface = surface,
            effectBackground = effectBackground,
            isFullSize = isFullSize,
            playing = playing,
            colorStage = colorStage,
            alpha = alpha,
        )
    }
}

private class BgEffectNode(
    private var painter: BgEffectPainter,
    private var preset: BgEffectConfig.Config,
    private var deviceType: DeviceType,
    private var isDarkTheme: Boolean,
    private var surface: Color,
    private var effectBackground: Boolean,
    private var isFullSize: Boolean,
    private var playing: Boolean,
    private var colorStage: () -> Float,
    private var alpha: () -> Float,
) : Modifier.Node(),
    DrawModifierNode {

    private var animationJob: Job? = null
    private var animTime: Float = 0f
    private var startOffset: Float = 0f

    override fun onAttach() {
        if (playing && effectBackground) startAnimation()
    }

    override fun onDetach() {
        animationJob?.cancel()
        animationJob = null
    }

    fun update(
        painter: BgEffectPainter,
        preset: BgEffectConfig.Config,
        deviceType: DeviceType,
        isDarkTheme: Boolean,
        surface: Color,
        effectBackground: Boolean,
        isFullSize: Boolean,
        playing: Boolean,
        colorStage: () -> Float,
        alpha: () -> Float,
    ) {
        val visualPropsChanged = this.preset !== preset ||
                this.isDarkTheme != isDarkTheme ||
                this.surface != surface ||
                this.isFullSize != isFullSize ||
                this.effectBackground != effectBackground

        this.painter = painter
        this.preset = preset
        this.deviceType = deviceType
        this.isDarkTheme = isDarkTheme
        this.surface = surface
        this.isFullSize = isFullSize
        this.colorStage = colorStage
        this.alpha = alpha

        val shouldAnimate = playing && effectBackground
        val wasAnimating = this.playing && this.effectBackground
        this.playing = playing
        this.effectBackground = effectBackground

        if (shouldAnimate != wasAnimating) {
            if (shouldAnimate) {
                startAnimation()
            } else {
                animationJob?.cancel()
                animationJob = null
            }
        }
        if (visualPropsChanged) {
            invalidateDraw()
        }
    }

    private fun startAnimation() {
        animationJob?.cancel()
        startOffset = animTime
        animationJob = coroutineScope.launch {
            val originMs = SystemClock.elapsedRealtime()
            while (isActive) {
                if (alpha() <= 0.005f) {
                    delay(250L)
                    continue
                }
                // Настоящий сон корутины на 50 мс (20 FPS) вместо 120 Гц цикла withFrameNanos
                delay(50L)
                val elapsedSec = (SystemClock.elapsedRealtime() - originMs) / 1000f
                animTime = startOffset + elapsedSec
                invalidateDraw()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun ContentDrawScope.draw() {
        drawRect(surface)
        if (effectBackground) {
            val alphaValue = alpha()
            if (alphaValue > 0.005f) {
                val drawHeight = if (isFullSize) size.height * 0.8f else size.height * 0.5f
                val period = preset.colorInterpPeriod.coerceAtLeast(1f)
                val computedColorStage = colorStage() + (animTime / period)

                painter.updateResolution(size.width, size.height)
                painter.updateBoundIfNeeded(drawHeight, size.height, size.width)
                painter.updatePresetIfNeeded(deviceType, isDarkTheme)
                painter.updateColors(preset, computedColorStage)
                painter.updateAnimTime(animTime)
                painter.updatePointsAnim(animTime, preset)

                drawRect(painter.brush, alpha = alphaValue)
            }
        }
        drawContent()
    }
}