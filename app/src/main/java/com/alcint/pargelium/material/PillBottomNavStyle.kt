@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alcint.pargelium.material

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcint.pargelium.library.NavItem
import kotlinx.coroutines.launch

@Composable
fun PillBottomNavStyle(
    pagerState: PagerState,
    navItems: List<NavItem>,
    onTabSelected: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val tabCount = navItems.size.coerceAtLeast(1)
    val itemWidthPx = if (totalWidthPx > 0f) totalWidthPx / tabCount else 0f

    // Анимация позиции островка с выразительной пружиной Material 3 Expressive
    val animatedFraction by animateFloatAsState(
        targetValue = pagerState.currentPage.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pill_slider_spring"
    )

    // Внешняя парящая капсула
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .shadow(elevation = 16.dp, shape = CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .height(64.dp)
            .fillMaxWidth()
            .onGloballyPositioned { totalWidthPx = it.size.width.toFloat() }
            // Обработка слайдинга/свайпа по островку
            .pointerInput(tabCount, itemWidthPx) {
                if (itemWidthPx <= 0f) return@pointerInput
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val dragTargetIndex = (change.position.x / itemWidthPx).toInt().coerceIn(0, tabCount - 1)
                        if (dragTargetIndex != pagerState.currentPage) {
                            onTabSelected(dragTargetIndex)
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Скользящий интерактивный островок-индикатор (Pill indicator)
        if (itemWidthPx > 0f) {
            val itemWidthDp = with(density) { itemWidthPx.toDp() }
            val sliderOffsetDp = with(density) { (animatedFraction * itemWidthPx).toDp() }

            Box(
                modifier = Modifier
                    .offset(x = sliderOffsetDp)
                    .width(itemWidthDp)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp, horizontal = 6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }

        // Ряд кнопок
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, item ->
                val isSelected = pagerState.currentPage == index
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.1f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                    label = "item_scale"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
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
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(id = item.labelRes),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}