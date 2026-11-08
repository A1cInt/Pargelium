@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alcint.pargelium.material

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcint.pargelium.library.NavItem

@Composable
fun MaterialBottomNavStyle(
    pagerState: PagerState,
    navItems: List<NavItem>,
    onTabSelected: (Int) -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(68.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, item ->
                val isSelected = pagerState.currentPage == index
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val itemWeight by animateFloatAsState(
                    targetValue = if (isSelected) 1.35f else 1f,
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    label = "m3eNavWeight"
                )

                val pillCorner by animateDpAsState(
                    targetValue = when {
                        isPressed -> 12.dp
                        isSelected -> 20.dp
                        else -> 28.dp
                    },
                    animationSpec = motionScheme.fastSpatialSpec(),
                    label = "m3eNavCorner"
                )

                val itemScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.92f else 1f,
                    animationSpec = motionScheme.fastSpatialSpec(),
                    label = "m3eNavScale"
                )

                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    label = "m3eNavBg"
                )

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    label = "m3eNavTint"
                )

                Box(
                    modifier = Modifier
                        .weight(itemWeight)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = itemScale
                            scaleY = itemScale
                        }
                        .clip(RoundedCornerShape(pillCorner))
                        .background(containerColor)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            if (pagerState.currentPage != index) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTabSelected(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = item.iconRes),
                            contentDescription = stringResource(id = item.labelRes),
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(id = item.labelRes),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}