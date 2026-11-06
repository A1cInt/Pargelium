package com.alcint.pargelium.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FastScrollThumb(
    itemCount: Int,
    state: LazyGridState,
    getLetter: (Int) -> String
) {
    if (itemCount <= 4) return

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thumbHeightPx = remember(density) { with(density) { 44.dp.toPx() } }

    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    var lastHapticLetter by remember { mutableStateOf("") }
    var lastScrolledIndex by remember { mutableIntStateOf(-1) }

    val isScrolling by remember { derivedStateOf { state.isScrollInProgress } }
    val showThumb = isScrolling || isDragging

    val thumbAlpha by animateFloatAsState(
        targetValue = if (showThumb) 1f else 0f,
        animationSpec = tween(durationMillis = 250, delayMillis = if (showThumb) 0 else 900),
        label = "thumbAlpha"
    )

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 14.dp else 5.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "thumbWidth"
    )

    val currentLetter by remember(itemCount) {
        derivedStateOf {
            val idx = if (isDragging && trackHeightPx > 1f) {
                val fraction = (dragOffsetY / trackHeightPx).coerceIn(0f, 1f)
                (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
            } else {
                state.firstVisibleItemIndex.coerceIn(0, itemCount - 1)
            }
            getLetter(idx)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .onSizeChanged { trackHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(itemCount) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isDragging = true
                    dragOffsetY = down.position.y
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    fun scrollToY(y: Float) {
                        val fraction = (y / trackHeightPx).coerceIn(0f, 1f)
                        val targetIndex = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                        if (targetIndex != lastScrolledIndex) {
                            lastScrolledIndex = targetIndex
                            val letter = getLetter(targetIndex)
                            if (letter != lastHapticLetter && letter.isNotEmpty()) {
                                lastHapticLetter = letter
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            scope.launch { state.scrollToItem(targetIndex) }
                        }
                    }

                    scrollToY(dragOffsetY)

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            change.consume()
                            dragOffsetY = change.position.y
                            scrollToY(dragOffsetY)
                        }
                    } while (event.changes.any { it.pressed })

                    isDragging = false
                    lastScrolledIndex = -1
                }
            }
    ) {
        AnimatedVisibility(
            visible = isDragging && currentLetter.isNotEmpty(),
            enter = fadeIn(tween(150)) + scaleIn(spring(dampingRatio = 0.65f, stiffness = 400f)),
            exit = fadeOut(tween(150)) + scaleOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val safeY = (dragOffsetY - thumbHeightPx * 1.4f)
                        .coerceIn(0f, (trackHeightPx - thumbHeightPx * 2f).coerceAtLeast(0f))
                    IntOffset(-76, safeY.roundToInt())
                }
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 28.dp, bottomEnd = 6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp,
                modifier = Modifier.size(58.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = currentLetter,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        if (thumbAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .offset {
                        val maxThumbY = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val targetY = if (isDragging) {
                            dragOffsetY.coerceIn(0f, maxThumbY)
                        } else {
                            val currentFraction = state.firstVisibleItemIndex.toFloat() / (itemCount - 1).coerceAtLeast(1)
                            (currentFraction * maxThumbY).coerceIn(0f, maxThumbY)
                        }
                        IntOffset(0, targetY.roundToInt())
                    }
                    .width(thumbWidth)
                    .height(44.dp)
                    .graphicsLayer { alpha = thumbAlpha }
                    .clip(CircleShape)
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
            )
        }
    }
}
