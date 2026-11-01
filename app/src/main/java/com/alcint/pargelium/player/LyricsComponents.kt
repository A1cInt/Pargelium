@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.alcint.pargelium.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcint.pargelium.LyricLine
import com.alcint.pargelium.R
import com.alcint.pargelium.SyllableWord

@Composable
fun InstrumentalCountdown(timeUntilMs: Long) {
    val secondsLeft = (timeUntilMs / 1000L).toInt()

    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = if (secondsLeft > 3) 4 else secondsLeft,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "countdown"
        ) { state ->
            if (state > 3) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "alpha"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White.copy(alpha = alpha)))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White.copy(alpha = alpha)))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White.copy(alpha = alpha)))
                }
            } else if (state > 0) {
                Text(
                    text = state.toString(),
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black),
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KaraokeLine(
    line: LyricLine,
    isActive: Boolean,
    currentPosition: Long,
    isPlaying: Boolean,
    nextLineTimeMs: Long?,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.35f)
) {
    val wordsToRender = remember(line, nextLineTimeMs) {
        if (line.words.isNotEmpty()) {
            line.words
        } else {
            val wordsOnly = line.text.split(" ").filter { it.isNotBlank() }
            val maxDuration = nextLineTimeMs?.let { it - line.timeMs } ?: 4000L
            val estimatedLineDurationMs = maxDuration.coerceIn(500L, 7000L)
            val step = estimatedLineDurationMs / wordsOnly.size.coerceAtLeast(1)

            wordsOnly.mapIndexed { index, w ->
                SyllableWord(
                    text = w,
                    startMs = line.timeMs + (index * step),
                    endMs = line.timeMs + ((index + 1) * step)
                )
            }
        }
    }

    if (isActive && wordsToRender.isNotEmpty()) {
        var smoothPosition by remember { mutableLongStateOf(currentPosition) }

        LaunchedEffect(currentPosition) {
            if (kotlin.math.abs(smoothPosition - currentPosition) > 800) {
                smoothPosition = currentPosition
            }
        }

        LaunchedEffect(isActive, isPlaying) {
            if (isActive && isPlaying) {
                var lastFrameTime = withFrameNanos { it }
                while (isActive) {
                    val frameTime = withFrameNanos { it }
                    val deltaMs = (frameTime - lastFrameTime) / 1_000_000
                    lastFrameTime = frameTime
                    smoothPosition += deltaMs
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            wordsToRender.forEachIndexed { index, word ->
                val progress = when {
                    smoothPosition >= word.endMs -> 1f
                    smoothPosition <= word.startMs -> 0f
                    else -> ((smoothPosition - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                }

                val p1 = (progress - 0.05f).coerceIn(0f, 1f)
                val p2 = (progress + 0.05f).coerceIn(0f, 1f)

                val brush = Brush.horizontalGradient(
                    0.0f to activeColor,
                    p1 to activeColor,
                    p2 to inactiveColor,
                    1.0f to inactiveColor
                )

                Text(
                    text = word.text + if (index < wordsToRender.lastIndex) " " else "",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 36.sp,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = activeColor.copy(alpha = 0.25f),
                            blurRadius = 8f
                        )
                    ).merge(androidx.compose.ui.text.TextStyle(brush = brush))
                )
            }
        }
    } else {
        Text(
            text = line.text,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                lineHeight = 36.sp
            ),
            color = if (isActive) activeColor else inactiveColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    currentPosition: Long,
    isPlaying: Boolean,
    onLineClick: (Long) -> Unit,
    onSearchOnline: () -> Unit,
    isSearching: Boolean,
    contentPadding: PaddingValues
) {
    val listState = rememberLazyListState()

    val activeIndex by remember(lyrics, currentPosition) {
        derivedStateOf { lyrics.indexOfLast { it.timeMs <= currentPosition } }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyrics.isNotEmpty()) {
            val layoutInfo = listState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == activeIndex }

            if (itemInfo != null) {
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val targetY = viewportHeight / 2
                val itemCenter = itemInfo.offset + (itemInfo.size / 2)
                val scrollDistance = itemCenter - targetY

                if (kotlin.math.abs(scrollDistance) > 5) {
                    listState.animateScrollBy(
                        value = scrollDistance.toFloat(),
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 50f)
                    )
                }
            } else {
                val safeTarget = (activeIndex - 1).coerceIn(0, lyrics.lastIndex)
                listState.animateScrollToItem(safeTarget, scrollOffset = -200)
            }
        }
    }

    if (lyrics.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.lyrics_not_found),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isSearching) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
            } else {
                OutlinedButton(
                    onClick = onSearchOnline,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_search_online))
                }
            }
        }
    } else {
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(
                items = lyrics,
                key = { _, line -> "${line.timeMs}_${line.text.take(10)}" }
            ) { index, line ->
                val isActive = index == activeIndex
                val isPast = index < activeIndex

                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.1f else 0.9f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 100f),
                    label = "scale"
                )
                val alpha by animateFloatAsState(
                    targetValue = if (isActive) 1f else if (isPast) 0.3f else 0.5f,
                    animationSpec = tween(500),
                    label = "alpha"
                )
                val blurRadius by animateDpAsState(
                    targetValue = if (isActive) 0.dp else 1.5.dp,
                    animationSpec = tween(500),
                    label = "blur"
                )

                val nextLineTimeMs = lyrics.getOrNull(index + 1)?.timeMs
                val prevLineEnd = if (index > 0) {
                    val pLine = lyrics[index - 1]
                    pLine.words.lastOrNull()?.endMs ?: (pLine.timeMs + 3000L)
                } else {
                    0L
                }

                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .blur(blurRadius)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onLineClick(line.timeMs) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (line.text.isNotBlank()) {
                            val timeUntilStart = line.timeMs - currentPosition
                            val gapDuration = line.timeMs - prevLineEnd
                            val isBigGap = gapDuration > 4000L || (index == 0 && line.timeMs > 4000L)

                            val isNextOrActive = (index == activeIndex + 1) || (index == 0 && activeIndex == -1)
                            val showCountdown = isNextOrActive && isBigGap && timeUntilStart > 0L

                            AnimatedVisibility(
                                visible = showCountdown,
                                enter = expandVertically(tween(400)) + fadeIn(tween(400)),
                                exit = fadeOut(tween(400)) + shrinkVertically(tween(400, delayMillis = 400))
                            ) {
                                InstrumentalCountdown(timeUntilMs = timeUntilStart)
                            }

                            KaraokeLine(
                                line = line,
                                isActive = isActive,
                                currentPosition = currentPosition,
                                isPlaying = isPlaying,
                                nextLineTimeMs = nextLineTimeMs
                            )

                            if (!line.translation.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))

                                val lineDuration = nextLineTimeMs?.let { it - line.timeMs } ?: 4000L
                                val transProgress = if (isActive) {
                                    ((currentPosition - line.timeMs).toFloat() / lineDuration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                } else {
                                    if (isPast) 1f else 0f
                                }

                                val p1 = (transProgress - 0.05f).coerceIn(0f, 1f)
                                val p2 = (transProgress + 0.05f).coerceIn(0f, 1f)

                                val activeColor = Color.White.copy(alpha = 0.8f)
                                val inactiveColor = Color.White.copy(alpha = 0.3f)

                                val transBrush = Brush.horizontalGradient(
                                    0.0f to activeColor,
                                    p1 to activeColor,
                                    p2 to inactiveColor,
                                    1.0f to inactiveColor
                                )

                                Text(
                                    text = line.translation,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontStyle = FontStyle.Italic
                                    ).merge(
                                        androidx.compose.ui.text.TextStyle(
                                            brush = if (isActive) transBrush else Brush.horizontalGradient(listOf(inactiveColor, inactiveColor))
                                        )
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}