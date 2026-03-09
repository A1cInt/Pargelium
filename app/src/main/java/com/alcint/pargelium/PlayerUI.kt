@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.media3.common.util.UnstableApi::class
)
package com.alcint.pargelium

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch
import ir.mahozad.multiplatform.wavyslider.material3.WavySlider
import androidx.compose.ui.res.painterResource

@Composable
fun AuroraBackground(seedColor: Color, modifier: Modifier = Modifier) {
    val baseBackground = MaterialTheme.colorScheme.background
    val defaultColor = MaterialTheme.colorScheme.primary
    val effectiveSeed = if (seedColor == Color.Unspecified || seedColor == Color.Transparent) defaultColor else seedColor

    val targetColor = if (effectiveSeed.luminance() < 0.1f) defaultColor else effectiveSeed
    val animatedColor by animateColorAsState(targetValue = targetColor, animationSpec = tween(1500), label = "color")

    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val offset1 by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 6.28f, animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Restart), label = "blob1")
    val slowScale by infiniteTransition.animateFloat(initialValue = 0.9f, targetValue = 1.1f, animationSpec = infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "slowScale")

    val color1 = animatedColor.copy(alpha = 0.6f)
    val color2 = animatedColor.copy(alpha = 0.4f)
    val bgGradientColors = remember(baseBackground) { listOf(Color.Transparent, baseBackground.copy(alpha = 0.5f), baseBackground) }

    val smoothedLoudnessState = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var fast = 0f
        var slow = 0f
        var visualLoudness = 0f
        while (true) {
            withFrameNanos {
                val target = globalAudioLoudness
                val safeTarget = if (target.isNaN() || target.isInfinite()) 0f else target

                fast = fast * 0.85f + safeTarget * 0.15f
                slow = slow * 0.98f + safeTarget * 0.02f
                val peak = (fast - slow).coerceAtLeast(0f)

                if (peak > visualLoudness) {
                    visualLoudness = visualLoudness * 0.5f + peak * 0.5f
                } else {
                    visualLoudness *= 0.94f
                }

                smoothedLoudnessState.floatValue = visualLoudness
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize().background(baseBackground)) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas

        val loud = smoothedLoudnessState.floatValue
        val currentBeat = 1.0f + (loud * 4.0f).coerceIn(0f, 0.6f)
        val curOffset = offset1
        val curScale = slowScale

        val x1 = width * 0.5f + cos(curOffset) * width * 0.2f
        val y1 = height * 0.4f + sin(curOffset) * height * 0.1f
        val r1 = ((width * 0.8f * curScale) * currentBeat).coerceAtLeast(1f)

        drawCircle(
            brush = Brush.radialGradient(listOf(color1, Color.Transparent), center = Offset(x1, y1), radius = r1),
            center = Offset(x1, y1),
            radius = r1
        )

        val x2 = width * 0.5f + cos(curOffset + 3.14f) * width * 0.3f
        val y2 = height * 0.6f + sin(curOffset + 3.14f) * height * 0.2f
        val r2 = ((width * 0.9f * (2f - curScale)) * currentBeat).coerceAtLeast(1f)

        drawCircle(
            brush = Brush.radialGradient(listOf(color2, Color.Transparent), center = Offset(x2, y2), radius = r2),
            center = Offset(x2, y2),
            radius = r2
        )

        drawRect(brush = Brush.verticalGradient(bgGradientColors))
    }
}

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    currentPosition: Long,
    onLineClick: (Long) -> Unit,
    contentPadding: PaddingValues
) {
    val listState = rememberLazyListState()

    val activeIndex by remember(lyrics, currentPosition) {
        derivedStateOf { lyrics.indexOfLast { it.timeMs <= currentPosition } }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && !listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == activeIndex }

            if (itemInfo != null) {
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val targetY = (viewportHeight * 0.35f).toInt()

                val itemCenter = itemInfo.offset + (itemInfo.size / 2)

                val scrollDistance = itemCenter - targetY

                if (kotlin.math.abs(scrollDistance) > 10) {
                    listState.animateScrollBy(
                        value = scrollDistance.toFloat(),
                        animationSpec = tween(600, easing = LinearOutSlowInEasing)
                    )
                }
            } else {
                listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
            }
        }
    }

    if (lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.lyrics_not_found), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.5f))
        }
    } else {
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = index == activeIndex
                val scale by animateFloatAsState(targetValue = if (isActive) 1.05f else 0.95f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow), label = "scale")
                val alpha by animateFloatAsState(targetValue = if (isActive) 1f else 0.4f, animationSpec = tween(400, easing = LinearEasing), label = "alpha")

                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onLineClick(line.timeMs) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium, lineHeight = 32.sp),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                        )
                        if (!line.translation.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = line.translation,
                                style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                                color = Color.White.copy(alpha = if (isActive) 0.7f else 0.3f),
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoBackground(videoUri: Uri) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
        }
    }

    LaunchedEffect(videoUri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { exoPlayer.play() }
            else if (event == Lifecycle.Event.ON_PAUSE) { exoPlayer.pause() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
        },
        modifier = Modifier.fillMaxSize().background(Color.Black)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    track: AudioTrack,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val trackKey = "${track.artist}_${track.title}".hashCode().toString()
    var bookmarks by remember { mutableStateOf(PrefsManager.getTrackBookmarks(trackKey)) }

    var showAddDialog by remember { mutableStateOf(false) }
    var bookmarkToEdit by remember { mutableStateOf<TrackBookmark?>(null) }

    var bookmarkNameInput by remember { mutableStateOf("") }
    var bookmarkTimeInput by remember { mutableStateOf("") }

    val newBookmarkStr = stringResource(id = R.string.new_bookmark)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp).fillMaxWidth()) {
            Text(
                stringResource(id = R.string.track_bookmarks),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            Button(
                onClick = {
                    bookmarkToEdit = null
                    bookmarkNameInput = newBookmarkStr
                    bookmarkTimeInput = formatDuration(currentPosition)
                    showAddDialog = true
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(id = R.string.create_bookmark))
            }

            LazyColumn(modifier = Modifier.fillMaxHeight(0.5f).padding(top = 8.dp)) {
                if (bookmarks.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(id = R.string.no_bookmarks_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(bookmarks) { b ->
                    ListItem(
                        headlineContent = { Text(b.name) },
                        supportingContent = { Text(formatDuration(b.timeMs)) },
                        modifier = Modifier.clickable {
                            onSeek(b.timeMs)
                            onDismiss()
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    bookmarkToEdit = b
                                    bookmarkNameInput = b.name
                                    bookmarkTimeInput = formatDuration(b.timeMs)
                                    showAddDialog = true
                                }) {
                                    Icon(Icons.Default.Edit, stringResource(id = R.string.action_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    val updated = bookmarks.filter { it.id != b.id }
                                    bookmarks = updated
                                    PrefsManager.saveTrackBookmarks(trackKey, updated)
                                }) {
                                    Icon(Icons.Default.Delete, stringResource(id = R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (bookmarkToEdit == null) stringResource(id = R.string.create_bookmark) else stringResource(id = R.string.edit_bookmark)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = bookmarkNameInput,
                        onValueChange = { bookmarkNameInput = it },
                        label = { Text(stringResource(id = R.string.bookmark_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = bookmarkTimeInput,
                        onValueChange = { bookmarkTimeInput = it },
                        label = { Text(stringResource(id = R.string.bookmark_time_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    var parsedTimeMs = parseDurationToMs(bookmarkTimeInput)
                    if (parsedTimeMs < 0) {
                        parsedTimeMs = currentPosition
                    }

                    val updated = if (bookmarkToEdit == null) {
                        bookmarks + TrackBookmark(name = bookmarkNameInput, timeMs = parsedTimeMs)
                    } else {
                        bookmarks.map { if (it.id == bookmarkToEdit!!.id) it.copy(name = bookmarkNameInput, timeMs = parsedTimeMs) else it }
                    }
                    bookmarks = updated.sortedBy { it.timeMs }
                    PrefsManager.saveTrackBookmarks(trackKey, bookmarks)
                    showAddDialog = false
                }) { Text(stringResource(id = R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(id = R.string.action_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FullPlayerScreen(
    track: AudioTrack,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    metadata: String,
    seedColor: Color,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    playlist: List<AudioTrack> = emptyList(),
    currentQueueIndex: Int = -1,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleRepeat: () -> Unit = {},
    onQueueItemClick: (Int) -> Unit = {},
    onRemoveFromQueue: (Int) -> Unit = {},
    onCollapse: () -> Unit,
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    onArtistClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isRadio = duration <= 0
    var showLyrics by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }

    val trackKey = remember(track) { "${track.artist}_${track.title}".hashCode().toString() }
    var canvasUri by remember { mutableStateOf<Uri?>(null) }
    var isVideo by remember { mutableStateOf(false) }

    val iconTint = if (seedColor == Color.Unspecified || seedColor == Color.Transparent) MaterialTheme.colorScheme.primary else seedColor

    val initialPage = currentQueueIndex.coerceAtLeast(0)
    val pageCount = if (playlist.isEmpty()) 1 else playlist.size
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })

    var dynamicSeedColor by remember(seedColor) { mutableStateOf(seedColor) }

    val canvasPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            PrefsManager.saveCustomCanvas(trackKey, uri.toString())
            canvasUri = uri
            isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true || uri.toString().contains("video", ignoreCase = true)
        }
    }

    LaunchedEffect(currentQueueIndex) {
        if (currentQueueIndex != -1 && currentQueueIndex != pagerState.currentPage) {
            if ((currentQueueIndex - pagerState.currentPage).absoluteValue > 2) pagerState.scrollToPage(currentQueueIndex)
            else pagerState.animateScrollToPage(currentQueueIndex)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (currentQueueIndex != -1 && pagerState.settledPage != currentQueueIndex && playlist.isNotEmpty()) {
            onQueueItemClick(pagerState.settledPage)
        }
    }

    LaunchedEffect(track) {
        lyrics = emptyList()
        val loadedLyrics = LyricsManager.getLyrics(context, track)
        lyrics = loadedLyrics

        val customCanvasStr = PrefsManager.getCustomCanvas(trackKey)
        if (customCanvasStr != null) {
            val uri = Uri.parse(customCanvasStr)
            canvasUri = uri
            isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true || customCanvasStr.contains("video", ignoreCase = true)
        } else {
            val foundCanvas = withContext(Dispatchers.IO) { AudioRepository.findCanvasForTrack(context, track.uri) }
            canvasUri = foundCanvas
            isVideo = foundCanvas?.let { context.contentResolver.getType(it)?.startsWith("video") == true || it.toString().contains("video", ignoreCase = true) } ?: false
        }
    }

    LaunchedEffect(track, canvasUri) {
        val saavnTrack = SaavnApi.trackCache[track.id]
        val imageUrl: Any? = canvasUri
            ?: saavnTrack?.coverUrl?.takeIf { it.isNotEmpty() }
            ?: (track.albumId.takeIf { it != 0L }?.let { ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), it) })

        if (imageUrl != null && !isVideo) {
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(100)
                        .allowHardware(false)
                        .build()
                    val result = context.imageLoader.execute(request)
                    val drawable = result.drawable as? android.graphics.drawable.BitmapDrawable
                    drawable?.bitmap?.let { bitmap ->
                        var r = 0L; var g = 0L; var b = 0L
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

                        val step = 10
                        var count = 0
                        for (i in pixels.indices step step) {
                            val color = pixels[i]
                            r += android.graphics.Color.red(color)
                            g += android.graphics.Color.green(color)
                            b += android.graphics.Color.blue(color)
                            count++
                        }

                        if (count > 0) {
                            val avgColor = Color(android.graphics.Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt()))
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(avgColor.toArgb(), hsv)
                            hsv[1] = (hsv[1] * 1.5f).coerceIn(0.4f, 1f)
                            hsv[2] = (hsv[2] * 1.5f).coerceIn(0.5f, 1f)
                            dynamicSeedColor = Color(android.graphics.Color.HSVToColor(hsv))
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        } else {
            dynamicSeedColor = seedColor
        }
    }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(ImageDecoderDecoder.Factory()) }
            .build()
    }

    if (showBookmarks) {
        BookmarksSheet(track = track, currentPosition = currentPosition, onSeek = onSeek, onDismiss = { showBookmarks = false })
    }

    if (showQueue) {
        val listState = rememberLazyListState()
        LaunchedEffect(showQueue) {
            if (currentQueueIndex >= 0 && currentQueueIndex < playlist.size) {
                listState.scrollToItem((currentQueueIndex - 2).coerceAtLeast(0))
            }
        }
        ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = MaterialTheme.colorScheme.surface, scrimColor = Color.Black.copy(0.5f)) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(stringResource(id = R.string.playback_queue), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(0.6f)) {
                    itemsIndexed(playlist) { index, item ->
                        val isCurrent = item.id == track.id
                        ListItem(
                            headlineContent = { Text(item.title, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, color = if (isCurrent) iconTint else MaterialTheme.colorScheme.onSurface) },
                            supportingContent = { Text(item.artist) },
                            leadingContent = { if (isCurrent) Icon(Icons.Rounded.GraphicEq, null, tint = iconTint) else Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailingContent = { IconButton(onClick = { onRemoveFromQueue(index) }) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                            modifier = Modifier.clickable { onQueueItemClick(index); showQueue = false }
                        )
                    }
                }
            }
        }
    }

    val blurRadius by animateDpAsState(targetValue = if (showLyrics) 25.dp else 0.dp, animationSpec = tween(600, easing = LinearOutSlowInEasing), label = "mainBlur")

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        val bgModifier = if (blurRadius > 0.dp) {
            Modifier.fillMaxSize().blur(blurRadius)
        } else {
            Modifier.fillMaxSize()
        }

        Crossfade(
            targetState = canvasUri,
            animationSpec = tween(1000),
            label = "bg_clear",
            modifier = Modifier.fillMaxSize()
        ) { uri ->
            if (uri != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isVideo) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            VideoBackground(videoUri = uri)
                            if (showLyrics) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
                            }
                        }
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(uri).build(),
                            imageLoader = imageLoader,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = bgModifier
                        )
                    }
                }
            } else {
                AuroraBackground(seedColor = dynamicSeedColor, modifier = bgModifier)
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0.0f to Color.Transparent, 0.6f to Color.Black.copy(alpha = 0.4f), 1.0f to Color.Black.copy(alpha = 0.8f))))

        val parallaxTransitionSpec: AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = {
            if (targetState) {
                (slideInVertically(tween(500, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(500)))
                    .togetherWith(slideOutVertically(tween(500, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(500)))
            } else {
                (slideInVertically(tween(500, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(500)))
                    .togetherWith(slideOutVertically(tween(500, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(500)))
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            val isLandscape = maxWidth > maxHeight || maxWidth > 600.dp

            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 16.dp), contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = showLyrics,
                            transitionSpec = parallaxTransitionSpec,
                            label = "TabletLyricsSwitch"
                        ) { isLyricsVisible ->
                            if (isLyricsVisible) {
                                LyricsView(lyrics = lyrics, currentPosition = currentPosition, onLineClick = { onSeek(it) }, contentPadding = PaddingValues(vertical = 32.dp))
                            } else {
                                if (canvasUri == null) {
                                    Box(modifier = Modifier.aspectRatio(1f).fillMaxHeight(0.8f), contentAlignment = Alignment.Center) {
                                        val saavnTrack = SaavnApi.trackCache[track.id]
                                        val artModel: Any? = when {
                                            saavnTrack != null && saavnTrack.coverUrl.isNotEmpty() -> saavnTrack.coverUrl
                                            track.albumId != 0L -> ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), track.albumId)
                                            else -> null
                                        }

                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            elevation = CardDefaults.cardElevation(12.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            if (artModel != null) {
                                                AsyncImage(model = artModel, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            } else {
                                                Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) { Icon(if (isRadio) Icons.Default.Radio else Icons.Rounded.GraphicEq, null, tint = Color.White.copy(0.2f), modifier = Modifier.size(80.dp)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        IconButton(onClick = onCollapse, modifier = Modifier.align(Alignment.TopStart)) { Icon(Icons.Default.KeyboardArrowDown, stringResource(id = R.string.action_collapse), tint = Color.White) }
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.weight(1f))
                        Text(track.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, modifier = Modifier.basicMarquee())

                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(0.9f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onArtistClick(track.artist) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .basicMarquee()
                        )
                        Spacer(Modifier.height(32.dp))

                        if (!isRadio) {
                            var sliderPosition by remember { mutableFloatStateOf(0f) }
                            var isDragging by remember { mutableStateOf(false) }
                            LaunchedEffect(currentPosition) { if (!isDragging) sliderPosition = currentPosition.toFloat() }

                            val animatedWaveHeight by animateDpAsState(
                                targetValue = if (isPlaying && !isDragging) 16.dp else 0.dp,
                                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                                label = "waveHeight"
                            )

                            WavySlider(
                                value = sliderPosition,
                                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                onValueChange = { isDragging = true; sliderPosition = it },
                                onValueChangeFinished = { onSeek(sliderPosition.toLong()); isDragging = false },
                                waveHeight = animatedWaveHeight,
                                waveLength = 45.dp,
                                waveThickness = 12.dp,
                                trackThickness = 12.dp,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = iconTint,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(formatDuration(if (isDragging) sliderPosition.toLong() else currentPosition), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f))

                                val saavnTrack = SaavnApi.trackCache[track.id]
                                if (saavnTrack != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayArrow, null, tint = iconTint, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("JioSaavn", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = iconTint)
                                    }
                                }

                                Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f))
                            }
                        } else {
                            val saavnTrack = SaavnApi.trackCache[track.id]
                            LinearWavyProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (saavnTrack != null) iconTint else Color.Red,
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                            if (saavnTrack != null) {
                                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, null, tint = iconTint, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("JioSaavn • MP4A 320kbps", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = iconTint)
                                }
                            } else {
                                Text(stringResource(R.string.status_live_broadcast), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Red, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                            val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                            val repeatTint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.5f) else iconTint
                            IconButton(onClick = onToggleRepeat) { Icon(imageVector = repeatIcon, contentDescription = stringResource(id = R.string.action_repeat), tint = repeatTint) }

                            IconButton(onClick = onSkipPrevious, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(36.dp), tint = Color.White) }
                            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(36.dp)) }
                            IconButton(onClick = onSkipNext, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(36.dp), tint = Color.White) }

                            IconButton(onClick = { showBookmarks = true }) { Icon(Icons.Default.Flag, null, tint = Color.White.copy(0.8f)) }
                        }

                        Spacer(Modifier.weight(1f))
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.1f), modifier = Modifier.height(24.dp)) { Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) { Text(metadata.ifEmpty { stringResource(R.string.meta_unknown) }, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.9f)) } }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.padding(top = 12.dp).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f)))

                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCollapse) { Icon(Icons.Default.KeyboardArrowDown, stringResource(id = R.string.action_collapse), modifier = Modifier.size(32.dp), tint = Color.White) }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var showCanvasMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showCanvasMenu = true }) {
                                    Icon(Icons.Default.Movie, stringResource(id = R.string.background), tint = Color.White)
                                }
                                DropdownMenu(expanded = showCanvasMenu, onDismissRequest = { showCanvasMenu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(id = R.string.choose_canvas)) }, onClick = {
                                        showCanvasMenu = false
                                        canvasPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                    })
                                    if (canvasUri != null) {
                                        DropdownMenuItem(text = { Text(stringResource(id = R.string.reset_canvas)) }, onClick = {
                                            showCanvasMenu = false
                                            PrefsManager.saveCustomCanvas(trackKey, null)
                                            scope.launch {
                                                val foundCanvas = withContext(Dispatchers.IO) { AudioRepository.findCanvasForTrack(context, track.uri) }
                                                canvasUri = foundCanvas
                                                isVideo = foundCanvas?.let { context.contentResolver.getType(it)?.startsWith("video") == true || it.toString().contains("video", ignoreCase = true) } ?: false
                                            }
                                        })
                                    }
                                }
                            }

                            IconButton(onClick = { showBookmarks = true }) { Icon(Icons.Default.Flag, stringResource(id = R.string.bookmarks), tint = Color.White) }

                            val hasTranslation = lyrics.any { !it.translation.isNullOrBlank() }

                            IconButton(onClick = {
                                if (lyrics.isNotEmpty() && !isTranslating) {
                                    showLyrics = true
                                    if (!hasTranslation) {
                                        isTranslating = true
                                        scope.launch {
                                            lyrics = LyricsManager.translateLyrics(context, track, lyrics)
                                            isTranslating = false
                                        }
                                    }
                                }
                            }) {
                                if (isTranslating) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Language, stringResource(id = R.string.translation), tint = if (hasTranslation) iconTint else Color.White)
                                }
                            }

                            IconButton(onClick = { showLyrics = !showLyrics }, colors = IconButtonDefaults.iconButtonColors(containerColor = if (showLyrics) Color.White.copy(alpha = 0.2f) else Color.Transparent)) { Icon(Icons.Default.FormatQuote, stringResource(id = R.string.action_lyrics), tint = Color.White) }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = showLyrics,
                            transitionSpec = parallaxTransitionSpec,
                            label = "LyricsSwitch"
                        ) { isLyricsVisible ->
                            if (isLyricsVisible) {
                                LyricsView(lyrics = lyrics, currentPosition = currentPosition, onLineClick = { onSeek(it) }, contentPadding = PaddingValues(top = 150.dp, bottom = 250.dp))
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (canvasUri != null) Spacer(modifier = Modifier.weight(1f))
                                    else {
                                        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 48.dp), pageSpacing = 16.dp, verticalAlignment = Alignment.CenterVertically) { page ->
                                            val itemTrack = if (playlist.isNotEmpty()) playlist.getOrNull(page) else track
                                            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                            val scale = 1f - (pageOffset.absoluteValue * 0.15f).coerceIn(0f, 0.3f)
                                            val alpha = 1f - (pageOffset.absoluteValue * 0.5f).coerceIn(0f, 0.5f)

                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                            ) {
                                                val saavnTrack = itemTrack?.let { SaavnApi.trackCache[it.id] }
                                                val artModel: Any? = when {
                                                    saavnTrack != null && saavnTrack.coverUrl.isNotEmpty() -> saavnTrack.coverUrl
                                                    itemTrack != null && itemTrack.albumId != 0L -> ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), itemTrack.albumId)
                                                    else -> null
                                                }

                                                Card(
                                                    shape = RoundedCornerShape(32.dp),
                                                    elevation = CardDefaults.cardElevation(12.dp),
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer {
                                                            scaleX = scale
                                                            scaleY = scale
                                                            this.alpha = alpha
                                                        }
                                                ) {
                                                    if (artModel != null) {
                                                        AsyncImage(model = artModel, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                    } else {
                                                        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) { Icon(if (isRadio) Icons.Default.Radio else Icons.Rounded.GraphicEq, null, tint = Color.White.copy(0.2f), modifier = Modifier.size(120.dp)) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        AnimatedContent(targetState = track, transitionSpec = { (fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.9f, animationSpec = tween(400))).togetherWith(fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 0.9f, animationSpec = tween(400))) }, label = "TrackInfo") { targetTrack ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(text = targetTrack.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, modifier = Modifier.basicMarquee())
                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = targetTrack.artist,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { onArtistClick(targetTrack.artist) }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        .basicMarquee()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                            val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                            val repeatTint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.5f) else iconTint
                            IconButton(onClick = onToggleRepeat) { Icon(imageVector = repeatIcon, contentDescription = stringResource(id = R.string.action_repeat), tint = repeatTint) }

                            IconButton(onClick = onSkipPrevious, modifier = Modifier.size(64.dp)) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(42.dp), tint = Color.White) }
                            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(80.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(40.dp)) }
                            IconButton(onClick = onSkipNext, modifier = Modifier.size(64.dp)) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(42.dp), tint = Color.White) }
                            IconButton(onClick = { showQueue = true }) { Icon(Icons.Default.QueueMusic, null, tint = Color.White) }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                            if (isRadio) {
                                val saavnTrack = SaavnApi.trackCache[track.id]
                                LinearWavyProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (saavnTrack != null) iconTint else Color.Red,
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )
                                if (saavnTrack != null) {
                                    Row(modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayArrow, null, tint = iconTint, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("JioSaavn • MP4A 320kbps", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = iconTint)
                                    }
                                } else {
                                    Text(stringResource(R.string.status_live_broadcast), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Red, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                                }
                            } else {
                                var sliderPosition by remember { mutableFloatStateOf(0f) }
                                var isDragging by remember { mutableStateOf(false) }
                                LaunchedEffect(currentPosition) { if (!isDragging) sliderPosition = currentPosition.toFloat() }

                                val animatedWaveHeight by animateDpAsState(
                                    targetValue = if (isPlaying && !isDragging) 16.dp else 0.dp,
                                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                                    label = "waveHeight"
                                )

                                WavySlider(
                                    value = sliderPosition,
                                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                    onValueChange = { isDragging = true; sliderPosition = it },
                                    onValueChangeFinished = { onSeek(sliderPosition.toLong()); isDragging = false },
                                    waveHeight = animatedWaveHeight,
                                    waveLength = 45.dp,
                                    waveThickness = 12.dp,
                                    trackThickness = 12.dp,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = iconTint,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatDuration(if (isDragging) sliderPosition.toLong() else currentPosition), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f))

                                    val saavnTrack = SaavnApi.trackCache[track.id]
                                    if (saavnTrack != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PlayArrow, null, tint = iconTint, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("JioSaavn", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = iconTint)
                                        }
                                    }

                                    Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.1f), modifier = Modifier.height(24.dp).align(Alignment.CenterHorizontally)) { Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) { Text(metadata.ifEmpty { stringResource(R.string.meta_unknown) }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f)) } }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    NavigationBar(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ) {
                        val navColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = iconTint,
                            selectedTextColor = iconTint,
                            indicatorColor = iconTint.copy(alpha = 0.2f),
                            unselectedIconColor = Color.White.copy(alpha = 0.6f),
                            unselectedTextColor = Color.White.copy(alpha = 0.6f)
                        )
                        NavigationBarItem(
                            selected = currentTab == 0,
                            onClick = { onTabSelected(0) },
                            icon = { Icon(painterResource(id = R.drawable.ic_action_key), null) },
                            label = { Text(stringResource(R.string.nav_library)) },
                            colors = navColors
                        )
                        NavigationBarItem(
                            selected = currentTab == 1,
                            onClick = { onTabSelected(1) },
                            icon = { Icon(painterResource(id = R.drawable.ic_satellite_alt), null) },
                            label = { Text(stringResource(R.string.nav_radio)) },
                            colors = navColors
                        )
                        NavigationBarItem(
                            selected = currentTab == 2,
                            onClick = { onTabSelected(2) },
                            icon = { Icon(painterResource(id = R.drawable.ic_package_2), null) },
                            label = { Text(stringResource(id = R.string.nav_playlists)) },
                            colors = navColors
                        )
                        NavigationBarItem(
                            selected = currentTab == 3,
                            onClick = { onTabSelected(3) },
                            icon = { Icon(painterResource(id = R.drawable.ic_instant_mix), null) },
                            label = { Text(stringResource(R.string.nav_eq)) },
                            colors = navColors
                        )
                        NavigationBarItem(
                            selected = currentTab == 4,
                            onClick = { onTabSelected(4) },
                            icon = { Icon(painterResource(id = R.drawable.ic_settings_heart), null) },
                            label = { Text(stringResource(R.string.nav_settings)) },
                            colors = navColors
                        )
                    }
                }
            }
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun parseDurationToMs(durationStr: String): Long {
    try {
        val parts = durationStr.trim().split(":")
        if (parts.size == 2) {
            val m = parts[0].toLong()
            val s = parts[1].toLong()
            return (m * 60 + s) * 1000
        } else if (parts.size == 3) {
            val h = parts[0].toLong()
            val m = parts[1].toLong()
            val s = parts[2].toLong()
            return (h * 3600 + m * 60 + s) * 1000
        }
    } catch (e: Exception) { }
    return -1L
}