@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.alcint.pargelium.player

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.ImageDecoderDecoder
import coil.imageLoader
import coil.request.ImageRequest
import com.alcint.pargelium.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlin.math.absoluteValue
import androidx.compose.ui.graphics.toArgb
import com.alcint.pargelium.R

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
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val audioOutput = rememberAudioOutputState(context)
    val playerBackdrop = rememberLayerBackdrop()

    val showTrackInfoBar = PrefsManager.getShowTrackInfoBar()
    val isRadio = duration <= 0

    var showLyrics by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var isCoverExpanded by remember { mutableStateOf(false) }
    var isSearchingLyricsOnline by remember { mutableStateOf(false) }

    val trackKey = remember(track.artist, track.title) { (track.artist.hashCode() xor track.title.hashCode()).toString() }

    var canvasUri by remember { mutableStateOf<Uri?>(null) }
    var isVideo by remember { mutableStateOf(false) }
    var dynamicSeedColor by remember(seedColor) { mutableStateOf(seedColor) }
    val iconTint = if (seedColor == Color.Unspecified || seedColor == Color.Transparent) MaterialTheme.colorScheme.primary else seedColor

    val initialPage = currentQueueIndex.coerceAtLeast(0)
    val pageCount = playlist.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })

    val canvasPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            PrefsManager.saveCustomCanvas(trackKey, uri.toString())
            canvasUri = uri
            isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true || uri.toString().contains("video", ignoreCase = true)
        }
    }

    LaunchedEffect(currentQueueIndex) {
        if (currentQueueIndex != -1 && currentQueueIndex != pagerState.currentPage) {
            if ((currentQueueIndex - pagerState.currentPage).absoluteValue > 2) pagerState.scrollToPage(currentQueueIndex)
            else pagerState.animateScrollToPage(page = currentQueueIndex, animationSpec = tween(500, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (currentQueueIndex != -1 && pagerState.settledPage != currentQueueIndex && playlist.isNotEmpty()) {
            onQueueItemClick(pagerState.settledPage)
        }
    }

    LaunchedEffect(track) {
        lyrics = LyricsManager.getLyrics(appContext, track)
        val customCanvasStr = PrefsManager.getCustomCanvas(trackKey)
        if (customCanvasStr != null) {
            val uri = Uri.parse(customCanvasStr)
            canvasUri = uri
            isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true || customCanvasStr.contains("video", ignoreCase = true)
        } else {
            val foundCanvas = withContext(Dispatchers.IO) { AudioRepository.findCanvasForTrack(appContext, track.uri) }
            canvasUri = foundCanvas
            isVideo = foundCanvas?.let { context.contentResolver.getType(it)?.startsWith("video") == true || it.toString().contains("video", ignoreCase = true) } ?: false
        }
    }

    val seedColorCache = remember { mutableMapOf<String, Color>() }
    LaunchedEffect(track, canvasUri, seedColor) {
        val cacheKey = canvasUri?.toString() ?: track.id.toString()
        if (seedColorCache.containsKey(cacheKey)) {
            dynamicSeedColor = seedColorCache[cacheKey]!!
            return@LaunchedEffect
        }
        val saavnTrack = SaavnApi.trackCache[track.id]
        val imageUrl: Any? = canvasUri ?: saavnTrack?.coverUrl?.takeIf { it.isNotEmpty() } ?: (track.albumId.takeIf { it != 0L }?.let { ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), it) })

        if (imageUrl != null && !isVideo) {
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(appContext).data(imageUrl).size(100).allowHardware(false).build()
                    val result = appContext.imageLoader.execute(request)
                    val drawable = result.drawable as? android.graphics.drawable.BitmapDrawable
                    drawable?.bitmap?.let { bitmap ->
                        var r = 0L; var g = 0L; var b = 0L; var count = 0
                        for (x in 0 until bitmap.width step 4) {
                            for (y in 0 until bitmap.height step 4) {
                                val color = bitmap.getPixel(x, y)
                                r += android.graphics.Color.red(color); g += android.graphics.Color.green(color); b += android.graphics.Color.blue(color); count++
                            }
                        }
                        if (count > 0) {
                            val avgColor = Color(android.graphics.Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt()))
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(avgColor.toArgb(), hsv)
                            hsv[1] = (hsv[1] * 1.5f).coerceIn(0.4f, 1f)
                            hsv[2] = (hsv[2] * 1.5f).coerceIn(0.5f, 1f)
                            val newColor = Color(android.graphics.Color.HSVToColor(hsv))
                            dynamicSeedColor = newColor
                            seedColorCache[cacheKey] = newColor
                        }
                    }
                } catch (_: Exception) {}
            }
        } else {
            dynamicSeedColor = seedColor
            seedColorCache[cacheKey] = seedColor
        }
    }

    val imageLoader = remember(appContext) { ImageLoader.Builder(appContext).components { add(ImageDecoderDecoder.Factory()) }.build() }

    if (showBookmarks) BookmarksSheet(track = track, currentPosition = currentPosition, onSeek = onSeek, onDismiss = { showBookmarks = false })
    if (showQueue) {
        val listState = rememberLazyListState()
        LaunchedEffect(showQueue) { if (currentQueueIndex in playlist.indices) listState.scrollToItem((currentQueueIndex - 2).coerceAtLeast(0)) }
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
    val showFullscreenCover = isCoverExpanded || canvasUri != null
    val backgroundState = remember(canvasUri, showFullscreenCover) { if (canvasUri != null) "canvas" else if (showFullscreenCover) "cover" else "aurora" }

    val parallaxTransitionSpec: AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = remember {
        {
            if (targetState) {
                (slideInVertically(tween(500, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(500)))
                    .togetherWith(slideOutVertically(tween(500, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(500)))
            } else {
                (slideInVertically(tween(500, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(500)))
                    .togetherWith(slideOutVertically(tween(500, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(500)))
            }
        }
    }

    val trackInfoTransitionSpec: AnimatedContentTransitionScope<AudioTrack>.() -> ContentTransform = remember {
        {
            (fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400)))
                .togetherWith(fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 0.95f, animationSpec = tween(400)))
                .using(SizeTransform(clip = false))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PlayerBackground(
            playerBackdrop = playerBackdrop,
            backgroundState = backgroundState,
            blurRadius = blurRadius,
            isVideo = isVideo,
            canvasUri = canvasUri,
            showLyrics = showLyrics,
            track = track,
            dynamicSeedColor = dynamicSeedColor,
            isPlaying = isPlaying,
            imageLoader = imageLoader,
            appContext = appContext
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            val isLandscape = maxWidth > maxHeight || maxWidth > 600.dp
            val coverAlpha by animateFloatAsState(targetValue = if (backgroundState != "aurora") 0f else 1f, animationSpec = tween(400), label = "coverAlpha")

            if (isLandscape) {
                // ... Landscape компоновка
                Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 16.dp), contentAlignment = Alignment.Center) {
                        AnimatedContent(targetState = showLyrics, transitionSpec = parallaxTransitionSpec, label = "TabletLyricsSwitch") { isLyricsVisible ->
                            if (isLyricsVisible) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 8.dp).align(Alignment.TopCenter).zIndex(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                            Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f, fill = false).basicMarquee())
                                            Text(" • ${track.artist}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.7f), maxLines = 1, modifier = Modifier.weight(1f, fill = false).basicMarquee())
                                        }
                                    }
                                    LyricsView(
                                        lyrics = lyrics, currentPosition = currentPosition, isPlaying = isPlaying, onLineClick = { onSeek(it) },
                                        onSearchOnline = {
                                            scope.launch {
                                                isSearchingLyricsOnline = true
                                                try {
                                                    val newLyrics = withContext(Dispatchers.IO) { LyricsManager.searchLyricsOnline(appContext, track) }
                                                    if (newLyrics.isNotEmpty()) lyrics = newLyrics
                                                } catch (_: Exception) {}
                                                isSearchingLyricsOnline = false
                                            }
                                        }, isSearching = isSearchingLyricsOnline, contentPadding = PaddingValues(top = 80.dp, bottom = 32.dp)
                                    )
                                }
                            } else {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight(0.8f).aspectRatio(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { isCoverExpanded = !isCoverExpanded }) {
                                    val saavnTrack = SaavnApi.trackCache[track.id]
                                    val artModel: Any? = when { saavnTrack != null && saavnTrack.coverUrl.isNotEmpty() -> saavnTrack.coverUrl; track.albumId != 0L -> ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), track.albumId); else -> null }
                                    Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(if (coverAlpha > 0f) 12.dp else 0.dp), modifier = Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }) {
                                        if (artModel != null) AsyncImage(model = artModel, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        else Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) { Icon(if (isRadio) Icons.Default.Radio else Icons.Rounded.GraphicEq, null, tint = Color.White.copy(0.2f), modifier = Modifier.size(80.dp)) }
                                    }
                                }
                            }
                        }
                        IconButton(onClick = onCollapse, modifier = Modifier.align(Alignment.TopStart)) { Icon(Icons.Default.KeyboardArrowDown, stringResource(id = R.string.action_collapse), tint = Color.White) }
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.weight(1f))
                        TrackTitleArtist(track = track, onArtistClick = onArtistClick)
                        Spacer(Modifier.height(32.dp))

                        PlayerProgressSlider(track = track, isRadio = isRadio, isPlaying = isPlaying, currentPosition = currentPosition, duration = duration, iconTint = iconTint, onSeek = onSeek)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            PlayerControlsRow(
                                repeatMode = repeatMode, isPlaying = isPlaying, iconTint = iconTint, showQueue = showQueue, playerBackdrop = playerBackdrop,
                                onToggleRepeat = onToggleRepeat, onSkipPrevious = onSkipPrevious, onPlayPause = onPlayPause, onSkipNext = onSkipNext, onToggleQueue = { showQueue = true }
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        Spacer(Modifier.weight(1f))
                        PlayerBottomInfo(audioOutput = audioOutput, iconTint = iconTint, showTrackInfoBar = showTrackInfoBar, metadata = metadata)
                    }
                }
            } else {
                // Portrait компоновка
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.padding(top = 12.dp).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f)))

                    PlayerTopBar(
                        onCollapse = onCollapse,
                        hasCanvas = canvasUri != null,
                        onPickCanvas = { canvasPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        onResetCanvas = {
                            PrefsManager.saveCustomCanvas(trackKey, null)
                            scope.launch {
                                val foundCanvas = withContext(Dispatchers.IO) { AudioRepository.findCanvasForTrack(appContext, track.uri) }
                                canvasUri = foundCanvas
                                isVideo = foundCanvas?.let { context.contentResolver.getType(it)?.startsWith("video") == true || it.toString().contains("video", ignoreCase = true) } ?: false
                            }
                        },
                        onBookmarksClick = { showBookmarks = true },
                        lyrics = lyrics,
                        isTranslating = isTranslating,
                        onTranslateClick = {
                            if (lyrics.isNotEmpty() && !isTranslating) {
                                showLyrics = true
                                if (!lyrics.any { !it.translation.isNullOrBlank() }) {
                                    isTranslating = true
                                    scope.launch {
                                        try { lyrics = LyricsManager.translateLyrics(appContext, track, lyrics) }
                                        finally { isTranslating = false }
                                    }
                                }
                            }
                        },
                        showLyrics = showLyrics,
                        onToggleLyrics = { showLyrics = !showLyrics },
                        iconTint = iconTint
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AnimatedContent(targetState = showLyrics, transitionSpec = parallaxTransitionSpec, label = "LyricsSwitch") { isLyricsVisible ->
                            if (isLyricsVisible) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LyricsView(
                                        lyrics = lyrics, currentPosition = currentPosition, isPlaying = isPlaying, onLineClick = { onSeek(it) },
                                        onSearchOnline = {
                                            scope.launch {
                                                isSearchingLyricsOnline = true
                                                try {
                                                    val newLyrics = withContext(Dispatchers.IO) { LyricsManager.searchLyricsOnline(appContext, track) }
                                                    if (newLyrics.isNotEmpty()) lyrics = newLyrics
                                                } catch (_: Exception) {}
                                                isSearchingLyricsOnline = false
                                            }
                                        }, isSearching = isSearchingLyricsOnline, contentPadding = PaddingValues(top = 100.dp, bottom = 250.dp)
                                    )
                                    Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 8.dp).align(Alignment.TopCenter).zIndex(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                            Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f, fill = false).basicMarquee())
                                            Text(" • ${track.artist}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.7f), maxLines = 1, modifier = Modifier.weight(1f, fill = false).basicMarquee())
                                        }
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 48.dp),
                                        pageSpacing = 16.dp,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) { page ->
                                        val itemTrack = if (playlist.isNotEmpty()) playlist.getOrNull(page) else track
                                        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                        val scale = 1f - (pageOffset.absoluteValue * 0.15f).coerceIn(0f, 0.3f)
                                        val baseAlpha = 1f - (pageOffset.absoluteValue * 0.5f).coerceIn(0f, 0.5f)

                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (pagerState.currentPage == page) isCoverExpanded = !isCoverExpanded }
                                        ) {
                                            val saavnTrack = itemTrack?.let { SaavnApi.trackCache[it.id] }
                                            val artModel: Any? = when {
                                                saavnTrack != null && saavnTrack.coverUrl.isNotEmpty() -> saavnTrack.coverUrl
                                                itemTrack != null && itemTrack.albumId != 0L -> ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), itemTrack.albumId)
                                                else -> null
                                            }

                                            Card(
                                                shape = RoundedCornerShape(32.dp),
                                                elevation = CardDefaults.cardElevation(if (coverAlpha > 0f) 12.dp else 0.dp),
                                                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = baseAlpha * coverAlpha }
                                            ) {
                                                if (artModel != null) AsyncImage(model = artModel, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                else Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) { Icon(if (isRadio) Icons.Default.Radio else Icons.Rounded.GraphicEq, null, tint = Color.White.copy(0.2f), modifier = Modifier.size(120.dp)) }
                                            }
                                        }
                                    }
                                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        AnimatedContent(targetState = track, transitionSpec = trackInfoTransitionSpec, label = "TrackInfo") { targetTrack ->
                                            TrackTitleArtist(track = targetTrack, onArtistClick = onArtistClick)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        PlayerControlsRow(
                            repeatMode = repeatMode, isPlaying = isPlaying, iconTint = iconTint, showQueue = showQueue, playerBackdrop = playerBackdrop,
                            onToggleRepeat = onToggleRepeat, onSkipPrevious = onSkipPrevious, onPlayPause = onPlayPause, onSkipNext = onSkipNext, onToggleQueue = { showQueue = true }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                            PlayerProgressSlider(track = track, isRadio = isRadio, isPlaying = isPlaying, currentPosition = currentPosition, duration = duration, iconTint = iconTint, onSeek = onSeek)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        PlayerBottomInfo(audioOutput = audioOutput, iconTint = iconTint, showTrackInfoBar = showTrackInfoBar, metadata = metadata)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}