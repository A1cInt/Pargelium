package com.alcint.pargelium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node. invalidateDraw
import androidx.compose.ui.node.invalidateLayer
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Высокопроизводительный 3D-tilt модификатор на базе Modifier.Node (0 аллокаций при рекомпозиции)
 * с динамическим световым бликом (Specular Glare), реагирующим на положение пальца.
 */
fun Modifier.tiltOnTouch(
    maxAngle: Float = 8f,
    minScale: Float = 0.95f,
    enableGlare: Boolean = true
): Modifier = this then TiltTouchElement(
    maxAngle = maxAngle,
    minScale = minScale,
    enableGlare = enableGlare
)

private data class TiltTouchElement(
    val maxAngle: Float,
    val minScale: Float,
    val enableGlare: Boolean
) : ModifierNodeElement<TiltTouchNode>() {

    override fun create(): TiltTouchNode = TiltTouchNode(maxAngle, minScale, enableGlare)

    override fun update(node: TiltTouchNode) {
        node.maxAngle = maxAngle
        node.minScale = minScale
        node.enableGlare = enableGlare
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "tiltOnTouch"
        properties["maxAngle"] = maxAngle
        properties["minScale"] = minScale
        properties["enableGlare"] = enableGlare
    }
}

private class TiltTouchNode(
    var maxAngle: Float,
    var minScale: Float,
    var enableGlare: Boolean
) : Modifier.Node(), PointerInputModifierNode, LayoutModifierNode, DrawModifierNode {

    private var currentTiltX = 0f
    private var currentTiltY = 0f
    private var currentScale = 1f
    private var glareCenter = Offset.Zero
    private var glareIntensity = 0f

    private val returnAnim = Animatable(0f)
    private var releaseJob: Job? = null
    private var nodeSize = IntSize.Zero

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        nodeSize = IntSize(placeable.width, placeable.height)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                rotationX = currentTiltX
                rotationY = currentTiltY
                scaleX = currentScale
                scaleY = currentScale
                cameraDistance = 10f * density
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (enableGlare && glareIntensity > 0.01f && size.width > 0f && size.height > 0f) {
            val radius = hypot(size.width, size.height) * 0.65f
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f * glareIntensity),
                        Color.White.copy(alpha = 0.06f * glareIntensity),
                        Color.Transparent
                    ),
                    center = glareCenter,
                    radius = radius.coerceAtLeast(1f)
                )
            )
        }
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) {
        if (pass != PointerEventPass.Main) return
        val width = bounds.width.toFloat()
        val height = bounds.height.toFloat()
        if (width <= 1f || height <= 1f) return

        val firstChange = pointerEvent.changes.firstOrNull()
        if (firstChange == null) {
            animateToRest()
            return
        }

        when (pointerEvent.type) {
            PointerEventType.Press, PointerEventType.Move -> {
                if (firstChange.pressed) {
                    releaseJob?.cancel()
                    val pos = firstChange.position
                    val x = pos.x.coerceIn(0f, width)
                    val y = pos.y.coerceIn(0f, height)

                    val nx = ((x - width * 0.5f) / (width * 0.5f)).coerceIn(-1f, 1f)
                    val ny = ((y - height * 0.5f) / (height * 0.5f)).coerceIn(-1f, 1f)

                    val targetTx = -ny * maxAngle
                    val targetTy = nx * maxAngle

                    // Плавная низкочастотная фильтрация без спама корутинами
                    currentTiltX += (targetTx - currentTiltX) * 0.45f
                    currentTiltY += (targetTy - currentTiltY) * 0.45f
                    currentScale += (minScale - currentScale) * 0.35f
                    glareCenter = Offset(x, y)
                    glareIntensity += (1f - glareIntensity) * 0.35f

                    invalidateLayer()
                    if (enableGlare) invalidateDraw()
                } else {
                    animateToRest()
                }
            }
            PointerEventType.Release, PointerEventType.Exit -> {
                animateToRest()
            }
        }
    }

    override fun onCancelPointerInput() {
        animateToRest()
    }

    private fun animateToRest() {
        if (!isAttached) return
        if (releaseJob?.isActive == true) return

        val startTx = currentTiltX
        val startTy = currentTiltY
        val startScale = currentScale
        val startGlare = glareIntensity

        releaseJob = coroutineScope.launch {
            returnAnim.snapTo(0f)
            returnAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) {
                val p = value
                currentTiltX = startTx * (1f - p)
                currentTiltY = startTy * (1f - p)
                currentScale = startScale + (1f - startScale) * p
                glareIntensity = startGlare * (1f - p).coerceIn(0f, 1f)
                invalidateLayer()
                if (enableGlare) invalidateDraw()
            }
        }
    }
}