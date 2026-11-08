package com.alcint.pargelium.liquid

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

@SuppressLint("NewApi")
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {

    private val pressProgressAnimationSpec =
        spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec =
        spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val offset: Offset get() = positionAnimation.value - startPosition

    @Language("AGSL")
    private val shader: RuntimeShader? =
        if (!isRuntimeShaderSupported()) null else RuntimeShader(
            """
    uniform float2 size;
    layout(color) uniform half4 color;
    uniform float radius;
    uniform float2 position;
    
    half4 main(float2 coord) {
        float dist = distance(coord, position);
        float intensity = smoothstep(radius, radius * 0.5, dist);
        return color * intensity;
    }"""
        )

    // Кэшируем ShaderBrush один раз вместо аллокации на каждом кадре
    private val cachedShaderBrush: ShaderBrush? = shader?.let { ShaderBrush(it) }

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0.002f) {
                drawRect(
                    Color.White.copy(0.06f * progress),
                    blendMode = BlendMode.Plus
                )
                val currentPos = position(size, positionAnimation.value)
                val spotX = currentPos.x.fastCoerceIn(0f, size.width)
                val spotY = currentPos.y.fastCoerceIn(0f, size.height)
                val spotColor = Color.White.copy(0.12f * progress)
                val radius = (size.minDimension * 1.2f).coerceAtLeast(1f)
                if (shader != null && cachedShaderBrush != null) {
                    shader.setFloatUniform("size", size.width, size.height)
                    shader.setColorUniform("color", spotColor.toArgb())
                    shader.setFloatUniform("radius", radius)
                    shader.setFloatUniform("position", spotX, spotY)
                    drawRect(
                        brush = cachedShaderBrush,
                        blendMode = BlendMode.Plus
                    )
                } else {
                    drawRect(
                        Brush.radialGradient(
                            0f to spotColor,
                            0.5f to spotColor,
                            1f to Color.Transparent,
                            center = Offset(spotX, spotY),
                            radius = radius,
                        ),
                        blendMode = BlendMode.Plus
                    )
                }
            }

            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                }
            ) { change, dragAmount ->
                if (dragAmount != Offset.Zero) {
                    animationScope.launch { positionAnimation.snapTo(change.position) }
                }
            }
        }
}