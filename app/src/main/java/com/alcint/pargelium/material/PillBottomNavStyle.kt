@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alcint.pargelium.material

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcint.pargelium.library.NavItem
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PillBottomNavStyle(
    pagerState: PagerState,
    navItems: List<NavItem>,
    onTabSelected: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val tabCount = navItems.size.coerceAtLeast(1)
    val itemWidthPx = if (totalWidthPx > 0f) totalWidthPx / tabCount else 0f

    val targetPageFraction by remember(pagerState, tabCount) {
        derivedStateOf {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, (tabCount - 1).toFloat())
        }
    }

    val animatedFraction by animateFloatAsState(
        targetValue = targetPageFraction,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "pill_slider_spring"
    )

    // Островок эластично растягивается по горизонтали во время перелета между вкладками
    val fractionalPart = abs(animatedFraction - animatedFraction.toInt())
    val midFlightStretch = 1f + (0.24f * (0.5f - abs(fractionalPart - 0.5f)) * 2f)

    val indicatorCorner by animateDpAsState(
        targetValue = if (fractionalPart > 0.08f && fractionalPart < 0.92f) 18.dp else 32.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "pillIndicatorCorner"
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            )
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .height(64.dp)
            .fillMaxWidth()
            .onSizeChanged { totalWidthPx = it.width.toFloat() }
            .pointerInput(tabCount, itemWidthPx) {
                if (itemWidthPx <= 0f) return@pointerInput
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val dragTargetIndex = (change.position.x / itemWidthPx).toInt().coerceIn(0, tabCount - 1)
                        if (dragTargetIndex != pagerState.currentPage) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTabSelected(dragTargetIndex)
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        if (itemWidthPx > 0f) {
            val itemWidthDp = with(density) { itemWidthPx.toDp() }

            Box(
                modifier = Modifier
                    .width(itemWidthDp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = animatedFraction * itemWidthPx
                        scaleX = midFlightStretch
                        scaleY = 1f - ((midFlightStretch - 1f) * 0.25f)
                    }
                    .padding(vertical = 6.dp, horizontal = 6.dp)
                    .clip(RoundedCornerShape(indicatorCorner))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, item ->
                val isSelected = pagerState.currentPage == index
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val scale by animateFloatAsState(
                    targetValue = when {
                        isPressed -> 0.88f
                        isSelected -> 1.08f
                        else -> 1.0f
                    },
                    animationSpec = motionScheme.fastSpatialSpec(),
                    label = "pill_item_scale"
                )

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    label = "pill_item_color"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            if (pagerState.currentPage != index) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            scope.launch { onTabSelected(index) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = item.iconRes),
                            contentDescription = stringResource(id = item.labelRes),
                            modifier = Modifier.size(22.dp),
                            tint = contentColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(id = item.labelRes),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}