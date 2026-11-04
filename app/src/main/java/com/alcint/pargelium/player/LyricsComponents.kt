@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.alcint.pargelium.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcint.pargelium.LyricLine
import com.alcint.pargelium.R
import com.alcint.pargelium.SyllableWord
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun InstrumentalCountdown(
    timeUntilMs: () -> Long,
    isPlaying: Boolean = true
) {
    val secondsLeft by remember {
        derivedStateOf { (timeUntilMs() / 1000L).toInt().coerceAtLeast(0) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = if (secondsLeft > 3) 4 else secondsLeft,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(220)) },
            label = "countdown"
        ) { state ->
            if (state > 3) {
                val alpha = if (isPlaying) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val a by infiniteTransition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 0.88f,
                        animationSpec = infiniteRepeatable(
                            tween(750, easing = FastOutSlowInEasing),
                            RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )
                    a
                } else 0.45f

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .graphicsLayer { this.alpha = alpha }
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            } else if (state > 0) {
                Text(
                    text = state.toString(),
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black),
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun KaraokeLine(
    line: LyricLine,
    isActive: Boolean,
    currentPositionProvider: () -> Long,
    isPlaying: Boolean,
    nextLineTimeMs: Long?,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.35f)
) {
    val wordsToRender = remember(line, nextLineTimeMs) {
        if (line.words.isNotEmpty()) {
            line.words
        } else {
            val wordsOnly = line.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
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

    val fullNormalizedText = remember(wordsToRender, line.text) {
        if (wordsToRender.isEmpty()) {
            line.text
        } else {
            buildString {
                wordsToRender.forEachIndexed { index, word ->
                    append(word.text)
                    if (index < wordsToRender.lastIndex && !word.text.endsWith(" ")) {
                        append(" ")
                    }
                }
            }
        }
    }

    val baseTextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 36.sp,
        textAlign = TextAlign.Center
    )

    val activeShadow = remember(activeColor) {
        Shadow(color = activeColor.copy(alpha = 0.3f), blurRadius = 10f)
    }
    val completedSpanStyle = remember(activeColor, activeShadow) {
        SpanStyle(color = activeColor, shadow = activeShadow)
    }
    val upcomingSpanStyle = remember(inactiveColor) {
        SpanStyle(color = inactiveColor)
    }

    if (isActive && wordsToRender.isNotEmpty()) {
        val lastWordEndMs = remember(wordsToRender) {
            wordsToRender.maxOfOrNull { it.endMs } ?: (line.timeMs + 4000L)
        }

        var quantizedPosition by remember(line.timeMs) { mutableLongStateOf(currentPositionProvider()) }
        val latestExternalPos by rememberUpdatedState(currentPositionProvider())

        LaunchedEffect(latestExternalPos) {
            if (abs(quantizedPosition - latestExternalPos) > 450L) {
                quantizedPosition = latestExternalPos
            }
        }

        // Оптимизированный цикл: обновляет UI с шагом ~24 мс вместо 120 Гц, снижая нагрузку на GC в 3 раза
        LaunchedEffect(isActive, isPlaying, lastWordEndMs) {
            if (isActive && isPlaying) {
                var internalPos = quantizedPosition
                var lastFrameTime = withFrameNanos { it }
                while (isActive && isPlaying) {
                    val frameTime = withFrameNanos { it }
                    val deltaMs = (frameTime - lastFrameTime) / 1_000_000L
                    lastFrameTime = frameTime
                    if (internalPos <= lastWordEndMs + 200L) {
                        internalPos += deltaMs
                        if (abs(internalPos - quantizedPosition) >= 24L) {
                            quantizedPosition = internalPos
                        }
                    } else {
                        delay(120L)
                        lastFrameTime = withFrameNanos { it }
                    }
                }
            }
        }

        val annotatedText = remember(quantizedPosition, wordsToRender, completedSpanStyle, upcomingSpanStyle) {
            buildAnnotatedString {
                for (index in wordsToRender.indices) {
                    val word = wordsToRender[index]
                    val wordText = if (index < wordsToRender.lastIndex && !word.text.endsWith(" ")) {
                        word.text + " "
                    } else {
                        word.text
                    }

                    val duration = (word.endMs - word.startMs).coerceAtLeast(1L)
                    val progress = when {
                        quantizedPosition >= word.endMs -> 1f
                        quantizedPosition <= word.startMs -> 0f
                        else -> ((quantizedPosition - word.startMs).toFloat() / duration).coerceIn(0f, 1f)
                    }

                    val spanStyle = when {
                        progress >= 1f -> completedSpanStyle
                        progress <= 0f -> upcomingSpanStyle
                        else -> {
                            val p1 = (progress - 0.08f).coerceIn(0f, 1f)
                            val p2 = (progress + 0.08f).coerceIn(0f, 1f)
                            SpanStyle(
                                brush = Brush.horizontalGradient(
                                    0.0f to activeColor,
                                    p1 to activeColor,
                                    p2 to inactiveColor,
                                    1.0f to inactiveColor
                                ),
                                shadow = activeShadow
                            )
                        }
                    }

                    withStyle(spanStyle) {
                        append(wordText)
                    }
                }
            }
        }

        Text(
            text = annotatedText,
            style = baseTextStyle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
    } else {
        Text(
            text = fullNormalizedText,
            style = baseTextStyle,
            color = if (isActive) activeColor else inactiveColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun TranslationLine(
    translation: String,
    lineTimeMs: Long,
    nextLineTimeMs: Long?,
    isActive: Boolean,
    isPast: Boolean,
    currentPositionProvider: () -> Long
) {
    val activeColor = remember { Color.White.copy(alpha = 0.85f) }
    val inactiveColor = remember { Color.White.copy(alpha = 0.32f) }
    val baseStyle = MaterialTheme.typography.titleMedium.copy(
        fontStyle = FontStyle.Italic,
        textAlign = TextAlign.Center
    )

    if (isActive) {
        val pos = currentPositionProvider()
        val lineDuration = (nextLineTimeMs?.let { it - lineTimeMs } ?: 4000L).coerceAtLeast(1L)
        val transProgress = ((pos - lineTimeMs).toFloat() / lineDuration).coerceIn(0f, 1f)
        val p1 = (transProgress - 0.05f).coerceIn(0f, 1f)
        val p2 = (transProgress + 0.05f).coerceIn(0f, 1f)

        val transBrush = Brush.horizontalGradient(
            0.0f to activeColor,
            p1 to activeColor,
            p2 to inactiveColor,
            1.0f to inactiveColor
        )

        Text(
            text = translation,
            style = baseStyle.merge(TextStyle(brush = transBrush)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        )
    } else {
        Text(
            text = translation,
            style = baseStyle,
            color = if (isPast) activeColor.copy(alpha = 0.45f) else inactiveColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
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
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val currentPositionState = rememberUpdatedState(currentPosition)

    val activeIndex by remember(lyrics) {
        derivedStateOf {
            val pos = currentPositionState.value
            lyrics.indexOfLast { it.timeMs <= pos }
        }
    }

    LaunchedEffect(activeIndex, lyrics.size) {
        if (activeIndex >= 0 && lyrics.isNotEmpty() && !listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == activeIndex }

            if (itemInfo != null) {
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val targetY = viewportHeight / 2
                val itemCenter = itemInfo.offset + (itemInfo.size / 2)
                val scrollDistance = itemCenter - targetY

                if (abs(scrollDistance) > 8) {
                    listState.animateScrollBy(
                        value = scrollDistance.toFloat(),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            } else {
                val safeTarget = (activeIndex - 1).coerceIn(0, lyrics.lastIndex)
                listState.animateScrollToItem(safeTarget, scrollOffset = -180)
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
                text = stringResource(R.string.lyrics_not_found),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.55f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isSearching) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
            } else {
                OutlinedButton(
                    onClick = onSearchOnline,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_search_online))
                }
            }
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = lyrics,
                key = { index, line -> "${index}_${line.timeMs}" },
                contentType = { _, line -> if (line.translation.isNullOrBlank()) 0 else 1 }
            ) { index, line ->
                val isActive = index == activeIndex
                val isPast = index < activeIndex

                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.05f else 0.94f,
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    label = "scale"
                )
                val alpha by animateFloatAsState(
                    targetValue = if (isActive) 1f else if (isPast) 0.30f else 0.52f,
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    label = "alpha"
                )

                val nextLineTimeMs = lyrics.getOrNull(index + 1)?.timeMs
                val prevLineEnd = remember(index, lyrics) {
                    if (index > 0) {
                        val pLine = lyrics[index - 1]
                        pLine.words.lastOrNull()?.endMs ?: (pLine.timeMs + 3000L)
                    } else 0L
                }

                val isBigGap = remember(line.timeMs, prevLineEnd, index) {
                    (line.timeMs - prevLineEnd) > 4000L || (index == 0 && line.timeMs > 4000L)
                }

                val isNextToBecomeActive = (index == activeIndex + 1) || (index == 0 && activeIndex == -1)

                val showCountdown by remember(isNextToBecomeActive, isBigGap, line.timeMs) {
                    derivedStateOf {
                        isNextToBecomeActive && isBigGap && (line.timeMs - currentPositionState.value) > 0L
                    }
                }

                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onLineClick(line.timeMs)
                            },
                            onLongClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val textToCopy = if (!line.translation.isNullOrBlank()) {
                                    "${line.text}\n${line.translation}"
                                } else line.text
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Lyrics", textToCopy))
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, line.text, Toast.LENGTH_SHORT).show()
                            }
                        )
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (line.text.isNotBlank()) {
                        AnimatedVisibility(
                            visible = showCountdown,
                            enter = expandVertically(tween(280)) + fadeIn(tween(280)),
                            exit = fadeOut(tween(220)) + shrinkVertically(tween(280))
                        ) {
                            InstrumentalCountdown(
                                timeUntilMs = { line.timeMs - currentPositionState.value },
                                isPlaying = isPlaying
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                        ) {
                            KaraokeLine(
                                line = line,
                                isActive = isActive,
                                currentPositionProvider = { currentPositionState.value },
                                isPlaying = isPlaying,
                                nextLineTimeMs = nextLineTimeMs
                            )

                            if (!line.translation.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TranslationLine(
                                    translation = line.translation,
                                    lineTimeMs = line.timeMs,
                                    nextLineTimeMs = nextLineTimeMs,
                                    isActive = isActive,
                                    isPast = isPast,
                                    currentPositionProvider = { currentPositionState.value }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}