package com.alcint.pargelium.liquid

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize

@Immutable
data class InnerShadow(
    val radius: Dp = 24.dp,
    val offset: DpOffset = DpOffset(0.dp, radius),
    val color: Color = Color.Black.copy(alpha = 0.15f),
    val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode,
) {
    companion object {
        @Stable
        val Default: InnerShadow = InnerShadow()
    }
}

fun Modifier.innerShadow(
    shape: Shape,
    shadow: () -> InnerShadow?,
): Modifier = this then InnerShadowElement(shape, shadow)

private class InnerShadowElement(
    val shape: Shape,
    val shadow: () -> InnerShadow?,
) : ModifierNodeElement<InnerShadowNode>() {

    override fun create(): InnerShadowNode = InnerShadowNode(shape, shadow)

    override fun update(node: InnerShadowNode) {
        if (node.shape != shape || node.shadow !== shadow) {
            node.shape = shape
            node.shadow = shadow
            node.markDirty()
            node.invalidateDraw()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "innerShadow"
        properties["shape"] = shape
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InnerShadowElement) return false
        if (shape != other.shape) return false
        if (shadow != other.shadow) return false
        return true
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + shadow.hashCode()
        return result
    }
}

private class InnerShadowNode(
    var shape: Shape,
    var shadow: () -> InnerShadow?,
) : Modifier.Node(),
    DrawModifierNode {

    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()
    private val clipPath = Path()
    private var prevRadius = Float.NaN

    private var cachedSize: Size = Size.Unspecified
    private var cachedLayoutDirection: LayoutDirection? = null
    private var cachedShape: Shape? = null
    private var cachedShadow: InnerShadow? = null
    private var isLayerDirty: Boolean = true

    fun markDirty() {
        isLayerDirty = true
    }

    override fun ContentDrawScope.draw() {
        drawContent()

        val currentShadow = shadow() ?: return
        if (currentShadow.alpha <= 0.005f || currentShadow.color.alpha <= 0.005f) return
        if (size.width <= 0f || size.height <= 0f) return

        val layer = shadowLayer ?: return
        val radius = currentShadow.radius.toPx()
        val offsetX = currentShadow.offset.x.toPx()
        val offsetY = currentShadow.offset.y.toPx()

        val needsRebuild = isLayerDirty ||
                cachedSize != size ||
                cachedLayoutDirection != layoutDirection ||
                cachedShape != shape ||
                cachedShadow != currentShadow

        if (needsRebuild) {
            cachedSize = size
            cachedLayoutDirection = layoutDirection
            cachedShape = shape
            cachedShadow = currentShadow
            isLayerDirty = false

            val outline = shape.createOutline(size, layoutDirection, this)
            clipPath.reset()
            when (outline) {
                is Outline.Rectangle -> clipPath.addRect(outline.rect)
                is Outline.Rounded -> clipPath.addRoundRect(outline.roundRect)
                is Outline.Generic -> clipPath.addPath(outline.path)
            }

            paint.color = currentShadow.color
            layer.alpha = currentShadow.alpha
            layer.blendMode = currentShadow.blendMode
            if (prevRadius != radius) {
                layer.renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
                prevRadius = radius
            }

            layer.record(size = size.toIntSize()) {
                drawContext.canvas.let { canvas ->
                    canvas.save()
                    canvas.clipPath(clipPath)
                    canvas.drawOutline(outline, paint)
                    canvas.translate(offsetX, offsetY)
                    canvas.drawOutline(outline, ShadowMaskPaint)
                    canvas.translate(-offsetX, -offsetY)
                    canvas.restore()
                }
            }
        }

        drawContext.canvas.let { canvas ->
            canvas.save()
            canvas.clipPath(clipPath)
            drawLayer(layer)
            canvas.restore()
        }
    }

    override fun onAttach() {
        isLayerDirty = true
        shadowLayer = requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    override fun onDetach() {
        shadowLayer?.let { layer ->
            requireGraphicsContext().releaseGraphicsLayer(layer)
            shadowLayer = null
        }
        prevRadius = Float.NaN
        isLayerDirty = true
    }
}

private val ShadowMaskPaint: Paint = Paint().apply {
    blendMode = BlendMode.Clear
}