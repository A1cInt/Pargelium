package com.alcint.pargelium

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

fun Modifier.tiltOnTouch(
    maxAngle: Float = 8f,
    minScale: Float = 0.95f
) = composed {
    val coroutineScope = rememberCoroutineScope()

    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }

    this
        .graphicsLayer {
            rotationX = tiltX.value
            rotationY = tiltY.value
            scaleX = scale.value
            scaleY = scale.value
            cameraDistance = 8f * density
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)

                fun calculateTargets(position: Offset): Pair<Float, Float> {
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()

                    val x = position.x.coerceIn(0f, width)
                    val y = position.y.coerceIn(0f, height)

                    val nx = (x - width / 2f) / (width / 2f)
                    val ny = (y - height / 2f) / (height / 2f)

                    return Pair(-ny * maxAngle, nx * maxAngle)
                }

                val (initialTx, initialTy) = calculateTargets(down.position)

                coroutineScope.launch { tiltX.animateTo(initialTx, spring(dampingRatio = 0.6f, stiffness = 400f)) }
                coroutineScope.launch { tiltY.animateTo(initialTy, spring(dampingRatio = 0.6f, stiffness = 400f)) }
                coroutineScope.launch { scale.animateTo(minScale, spring(dampingRatio = 0.6f, stiffness = 400f)) }

                var isTracking = true
                while (isTracking) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id }

                    if (change == null || !change.pressed) {
                        isTracking = false
                    } else {
                        val (tx, ty) = calculateTargets(change.position)
                        coroutineScope.launch {
                            tiltX.snapTo(tx)
                            tiltY.snapTo(ty)
                        }
                    }
                }

                coroutineScope.launch { tiltX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
                coroutineScope.launch { tiltY.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
                coroutineScope.launch { scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
            }
        }
}