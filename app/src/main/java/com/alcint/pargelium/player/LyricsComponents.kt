@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.alcint.pargelium.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcint.pargelium.LyricLine
import com.alcint.pargelium.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InstrumentalCountdown(
    timeUntilMs: () -> Long,
    isPlaying: Boolean = true
) {
    val secondsLeft by remember {
        derivedStateOf { (timeUntilMs() / 1000L).toInt().coerceAtLeast(0) }
    }

    if (secondsLeft <= 0) return

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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .graphicsLayer { this.alpha = if (isPlaying) 0.65f else 0.35f }
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
    val hasWordSync = line.words.isNotEmpty()
    val wordsToRender = remember(line) { line.words }

    val fullNormalizedText = remember(wordsToRender, line.text) {
        if (wordsToRender.isEmpty()) {
            line.text
        } else {
            buildString {
                wordsToRender.forEach { word -> append(word.text) }
            }.trim()
        }
    }

    val baseTextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 36.sp,
        textAlign = TextAlign.Center
    )

    // Убрана размытая Shadow внутри динамического градиента: она сбрасывала кэш глифов шрифта в GPU на каждом тике
    val completedSpanStyle = remember(activeColor) { SpanStyle(color = activeColor) }
    val upcomingSpanStyle = remember(inactiveColor) { SpanStyle(color = inactiveColor) }

    if (isActive && hasWordSync) {
        val lastWordEndMs = remember(wordsToRender) {
            wordsToRender.maxOfOrNull { it.endMs } ?: (line.timeMs + 4000L)
        }

        var quantizedPosition by remember(line.timeMs) { mutableLongStateOf(currentPositionProvider()) }
        val latestExternalPos by rememberUpdatedState(currentPositionProvider())

        LaunchedEffect(latestExternalPos) {
            if (abs(quantizedPosition - latestExternalPos) > 350L) {
                quantizedPosition = latestExternalPos
            }
        }

        // Квантованный шаг 65 мс (~15 FPS) снижает нагрузку на текстовый движок в 2.5 раза без потери плавности
        LaunchedEffect(isActive, isPlaying, lastWordEndMs) {
            if (isActive && isPlaying) {
                var lastTickMs = SystemClock.elapsedRealtime()
                while (isActive && isPlaying) {
                    delay(65L)
                    val now = SystemClock.elapsedRealtime()
                    val delta = (now - lastTickMs).coerceIn(0L, 120L)
                    lastTickMs = now
                    if (quantizedPosition <= lastWordEndMs + 200L) {
                        quantizedPosition += delta
                    } else {
                        delay(200L)
                        lastTickMs = SystemClock.elapsedRealtime()
                    }
                }
            }
        }

        val annotatedText = remember(quantizedPosition, wordsToRender, completedSpanStyle, upcomingSpanStyle) {
            buildAnnotatedString {
                for (index in wordsToRender.indices) {
                    val word = wordsToRender[index]
                    val wordText = word.text

                    val duration = (word.endMs - word.startMs).coerceAtLeast(1L)
                    val rawProgress = when {
                        quantizedPosition >= word.endMs -> 1f
                        quantizedPosition <= word.startMs -> 0f
                        else -> ((quantizedPosition - word.startMs).toFloat() / duration).coerceIn(0f, 1f)
                    }
                    val progress = (rawProgress * 12f).roundToInt() / 12f

                    val spanStyle = when {
                        progress >= 0.98f -> completedSpanStyle
                        progress <= 0.02f -> upcomingSpanStyle
                        else -> {
                            val p1 = (progress - 0.08f).coerceIn(0f, 1f)
                            val p2 = (progress + 0.08f).coerceIn(0f, 1f)
                            SpanStyle(
                                brush = Brush.horizontalGradient(
                                    0.0f to activeColor,
                                    p1 to activeColor,
                                    p2 to inactiveColor,
                                    1.0f to inactiveColor
                                )
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
    isActive: Boolean,
    isPast: Boolean
) {
    val activeColor = remember { Color.White.copy(alpha = 0.86f) }
    val pastColor = remember { Color.White.copy(alpha = 0.38f) }
    val inactiveColor = remember { Color.White.copy(alpha = 0.32f) }

    Text(
        text = translation,
        style = MaterialTheme.typography.titleMedium.copy(
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center
        ),
        color = when {
            isActive -> activeColor
            isPast -> pastColor
            else -> inactiveColor
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    )
}

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    currentPosition: Long,
    isPlaying: Boolean,
    onLineClick: (Long) -> Unit,
    onSearchOnline: () -> Unit,
    isSearching: Boolean,
    contentPadding: PaddingValues,
    creditUrl: String? = null
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
                            visible = isNextToBecomeActive && isBigGap,
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
                                    isActive = isActive,
                                    isPast = isPast
                                )
                            }
                        }
                    }
                }
            }

            item(key = "lrc_red_credit_footer") {
                val targetLink = creditUrl ?: "https://lrc.red"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.10f),
                        modifier = Modifier
                            .border(0.5.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(50))
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetLink)))
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.lyrics_credit_lrcred),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}