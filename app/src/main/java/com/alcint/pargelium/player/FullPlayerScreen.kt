@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.alcint.pargelium.player

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.LruCache
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.alcint.pargelium.R
import com.alcint.pargelium.liquid.LiquidGlassIconButton
import com.alcint.pargelium.liquid.LiquidGlassPlayPauseButton
import com.alcint.pargelium.material.ExpressiveIconButton
import com.alcint.pargelium.material.ExpressivePlayButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlin.math.absoluteValue

private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
private val GlobalSeedColorCache = LruCache<String, Color>(128)

private fun isVideoUri(context: Context, uri: Uri): Boolean {
    val str = uri.toString().lowercase()
    if (
        str.endsWith(".mp4") ||
        str.endsWith(".webm") ||
        str.endsWith(".m3u8") ||
        str.endsWith(".mov") ||
        str.contains(".mp4?") ||
        str.contains(".m3u8?") ||
        str.contains("video") ||
        str.contains("canvaz")
    ) {
        return true
    }
    return context.contentResolver.getType(uri)?.startsWith("video") == true
}

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
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val audioOutput = rememberAudioOutputState(context)
    val playerBackdrop = rememberLayerBackdrop()

    val showTrackInfoBar = remember { PrefsManager.getShowTrackInfoBar() }
    val playerBtnStyle by remember { mutableIntStateOf(PrefsManager.getPlayerButtonStyle()) }

    val isRadio = duration <= 0L

    var showLyrics by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var isCoverExpanded by remember { mutableStateOf(false) }
    var isSearchingLyricsOnline by remember { mutableStateOf(false) }

    val trackKey = remember(track.artist, track.title) {
        "${track.artist.trim().lowercase()}_${track.title.trim().lowercase()}".hashCode().toString()
    }
    val legacyTrackKey = remember(track.artist, track.title) {
        (track.artist.hashCode() xor track.title.hashCode()).toString()
    }

    var canvasUri by remember { mutableStateOf<Uri?>(null) }
    var isVideo by remember { mutableStateOf(false) }
    var dynamicSeedColor by remember(seedColor) { mutableStateOf(seedColor) }
    val iconTint = if (seedColor == Color.Unspecified || seedColor == Color.Transparent) {
        MaterialTheme.colorScheme.primary
    } else {
        seedColor
    }

    val initialPage = currentQueueIndex.coerceAtLeast(0)
    val pageCount = playlist.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    val canvasPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            PrefsManager.saveCustomCanvas(trackKey, uri.toString())
            canvasUri = uri
            isVideo = isVideoUri(appContext, uri)
        }
    }

    LaunchedEffect(currentQueueIndex, playlist.size) {
        if (currentQueueIndex in 0 until pageCount && currentQueueIndex != pagerState.currentPage) {
            isProgrammaticScroll = true
            try {
                if ((currentQueueIndex - pagerState.currentPage).absoluteValue > 2) {
                    pagerState.scrollToPage(currentQueueIndex)
                } else {
                    pagerState.animateScrollToPage(
                        page = currentQueueIndex,
                        animationSpec = tween(450, easing = FastOutSlowInEasing)
                    )
                }
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    val latestQueueIndex by rememberUpdatedState(currentQueueIndex)
    val latestPlaylistSize by rememberUpdatedState(playlist.size)
    val latestOnQueueItemClick by rememberUpdatedState(onQueueItemClick)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                if (
                    !isProgrammaticScroll &&
                    latestQueueIndex != -1 &&
                    settled != latestQueueIndex &&
                    settled in 0 until latestPlaylistSize
                ) {
                    latestOnQueueItemClick(settled)
                }
            }
    }

    LaunchedEffect(track.id, track.uri, trackKey) {
        canvasUri = null
        isVideo = false

        launch {
            lyrics = LyricsManager.getLyrics(appContext, track)
        }

        launch {
            val customCanvasStr = PrefsManager.getCustomCanvas(trackKey)
                ?: PrefsManager.getCustomCanvas(legacyTrackKey)

            if (!customCanvasStr.isNullOrBlank()) {
                val uri = Uri.parse(customCanvasStr)
                canvasUri = uri
                isVideo = isVideoUri(appContext, uri)
                return@launch
            }

            val localCanvas = withContext(Dispatchers.IO) {
                AudioRepository.findCanvasForTrack(appContext, track.uri)
            }
            if (localCanvas != null) {
                canvasUri = localCanvas
                isVideo = isVideoUri(appContext, localCanvas)
                return@launch
            }

            val onlineCanvas = OnlineCanvasManager.findOnlineCanvas(appContext, track)
            if (onlineCanvas != null) {
                canvasUri = onlineCanvas
                isVideo = isVideoUri(appContext, onlineCanvas)
            }
        }
    }

    LaunchedEffect(track.id, track.albumId, canvasUri, isVideo, seedColor) {
        val cacheKey = if (!isVideo && canvasUri != null) canvasUri.toString() else "track_${track.id}_${track.albumId}"
        GlobalSeedColorCache.get(cacheKey)?.let { cachedColor ->
            dynamicSeedColor = cachedColor
            return@LaunchedEffect
        }

        val saavnTrack = SaavnApi.trackCache[track.id]
        val imageUrl: Any? = when {
            !isVideo && canvasUri != null -> canvasUri
            saavnTrack != null && saavnTrack.coverUrl.isNotEmpty() -> saavnTrack.coverUrl
            track.albumId != 0L -> ContentUris.withAppendedId(ALBUM_ART_URI, track.albumId)
            else -> null
        }

        if (imageUrl != null) {
            val extractedColor = withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(appContext)
                        .data(imageUrl)
                        .size(64)
                        .allowHardware(false)
                        .build()
                    val result = appContext.imageLoader.execute(request)
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@withContext null

                    var r = 0L
                    var g = 0L
                    var b = 0L
                    var count = 0
                    val width = bitmap.width
                    val height = bitmap.height
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                    var i = 0
                    while (i < pixels.size) {
                        val color = pixels[i]
                        r += (color shr 16) and 0xFF
                        g += (color shr 8) and 0xFF
                        b += color and 0xFF
                        count++
                        i += 4
                    }

                    if (count > 0) {
                        val avgColorInt = android.graphics.Color.rgb(
                            (r / count).toInt(),
                            (g / count).toInt(),
                            (b / count).toInt()
                        )
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(avgColorInt, hsv)
                        hsv[1] = (hsv[1] * 1.5f).coerceIn(0.4f, 1f)
                        hsv[2] = (hsv[2] * 1.5f).coerceIn(0.5f, 1f)
                        Color(android.graphics.Color.HSVToColor(hsv))
                    } else null
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }

            val finalColor = extractedColor ?: seedColor
            dynamicSeedColor = finalColor
            GlobalSeedColorCache.put(cacheKey, finalColor)
        } else {
            dynamicSeedColor = seedColor
            GlobalSeedColorCache.put(cacheKey, seedColor)
        }
    }

    val imageLoader = remember(appContext) {
        ImageLoader.Builder(appContext)
            .components { add(ImageDecoderDecoder.Factory()) }
            .build()
    }

    if (showBookmarks) {
        BookmarksSheet(
            track = track,
            currentPosition = currentPosition,
            onSeek = onSeek,
            onDismiss = { showBookmarks = false }
        )
    }

    if (showQueue) {
        val listState = rememberLazyListState()
        LaunchedEffect(showQueue) {
            if (currentQueueIndex in playlist.indices) {
                listState.scrollToItem((currentQueueIndex - 2).coerceAtLeast(0))
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    stringResource(id = R.string.playback_queue),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(0.6f)) {
                    itemsIndexed(
                        items = playlist,
                        key = { index, item -> "${index}_${item.id}" }
                    ) { index, item ->
                        val isCurrent = index == currentQueueIndex || (currentQueueIndex == -1 && item.id == track.id)
                        ListItem(
                            headlineContent = {
                                Text(
                                    item.title,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) iconTint else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = { Text(item.artist) },
                            leadingContent = {
                                if (isCurrent) {
                                    Icon(Icons.Rounded.GraphicEq, null, tint = iconTint)
                                } else {
                                    Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            trailingContent = {
                                HookedIconButton(
                                    buttonStyle = playerBtnStyle,
                                    icon = Icons.Default.Close,
                                    onClick = { onRemoveFromQueue(index) },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable {
                                onQueueItemClick(index)
                                showQueue = false
                            }
                        )
                    }
                }
            }
        }
    }

    val blurRadius by animateDpAsState(
        targetValue = if (showLyrics) 25.dp else 0.dp,
        animationSpec = tween(600, easing = LinearOutSlowInEasing),
        label = "mainBlur"
    )
    val showFullscreenCover = isCoverExpanded || canvasUri != null
    val backgroundState = remember(canvasUri, showFullscreenCover) {
        when {
            canvasUri != null -> "canvas"
            showFullscreenCover -> "cover"
            else -> "aurora"
        }
    }

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
            val coverAlpha by animateFloatAsState(
                targetValue = if (backgroundState != "aurora") 0f else 1f,
                animationSpec = tween(400),
                label = "coverAlpha"
            )

            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = showLyrics,
                            transitionSpec = parallaxTransitionSpec,
                            label = "TabletLyricsSwitch"
                        ) { isLyricsVisible ->
                            if (isLyricsVisible) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Surface(
                                        color = Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .align(Alignment.TopCenter)
                                            .zIndex(1f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.MusicNote,
                                                null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                track.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f, fill = false).basicMarquee()
                                            )
                                            Text(
                                                " • ${track.artist}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f, fill = false).basicMarquee()
                                            )
                                        }
                                    }
                                    LyricsView(
                                        lyrics = lyrics,
                                        currentPosition = currentPosition,
                                        isPlaying = isPlaying,
                                        onLineClick = onSeek,
                                        onSearchOnline = {
                                            scope.launch {
                                                isSearchingLyricsOnline = true
                                                try {
                                                    val newLyrics = LyricsManager.searchLyricsOnline(appContext, track)
                                                    if (newLyrics.isNotEmpty()) lyrics = newLyrics
                                                } finally {
                                                    isSearchingLyricsOnline = false
                                                }
                                            }
                                        },
                                        isSearching = isSearchingLyricsOnline,
                                        contentPadding = PaddingValues(top = 80.dp, bottom = 32.dp)
                                    )
                                }
                            } else {
                                val coverInteraction = remember { MutableInteractionSource() }
                                val artModel = remember(track.id, track.albumId) {
                                    val saavnTrack = SaavnApi.trackCache[track.id]
                                    when {
                                        saavnTrack != null && saavnTrack.coverUrl.isNotEmpty() -> saavnTrack.coverUrl
                                        track.albumId != 0L -> ContentUris.withAppendedId(ALBUM_ART_URI, track.albumId)
                                        else -> null
                                    }
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxHeight(0.8f)
                                        .aspectRatio(1f)
                                        .clickable(
                                            interactionSource = coverInteraction,
                                            indication = null
                                        ) { isCoverExpanded = !isCoverExpanded }
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = CardDefaults.cardElevation(if (coverAlpha > 0f) 12.dp else 0.dp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { alpha = coverAlpha }
                                    ) {
                                        if (artModel != null) {
                                            AsyncImage(
                                                model = artModel,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.DarkGray),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    if (isRadio) Icons.Default.Radio else Icons.Rounded.GraphicEq,
                                                    null,
                                                    tint = Color.White.copy(alpha = 0.2f),
                                                    modifier = Modifier.size(80.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.align(Alignment.TopStart)) {
                            HookedIconButton(
                                buttonStyle = playerBtnStyle,
                                icon = Icons.Default.KeyboardArrowDown,
                                onClick = onCollapse,
                                tint = Color.White,
                                backdrop = playerBackdrop
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.weight(1f))
                        TrackTitleArtist(track = track, onArtistClick = onArtistClick)
                        Spacer(Modifier.height(32.dp))

                        PlayerProgressSlider(
                            track = track,
                            isRadio = isRadio,
                            isPlaying = isPlaying,
                            currentPosition = currentPosition,
                            duration = duration,
                            iconTint = iconTint,
                            onSeek = onSeek
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            PlayerControlsRowHooked(
                                buttonStyle = playerBtnStyle,
                                repeatMode = repeatMode,
                                isPlaying = isPlaying,
                                iconTint = iconTint,
                                showQueue = showQueue,
                                playerBackdrop = playerBackdrop,
                                onToggleRepeat = onToggleRepeat,
                                onSkipPrevious = onSkipPrevious,
                                onPlayPause = onPlayPause,
                                onSkipNext = onSkipNext,
                                onToggleQueue = { showQueue = true }
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        Spacer(Modifier.weight(1f))
                        PlayerBottomInfo(
                            audioOutput = audioOutput,
                            iconTint = iconTint,
                            showTrackInfoBar = showTrackInfoBar,
                            metadata = metadata
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    )

                    PlayerTopBar(
                        onCollapse = onCollapse,
                        hasCanvas = canvasUri != null,
                        onPickCanvas = {
                            canvasPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                        onResetCanvas = {
                            PrefsManager.saveCustomCanvas(trackKey, null)
                            PrefsManager.saveCustomCanvas(legacyTrackKey, null)
                            scope.launch {
                                val localCanvas = withContext(Dispatchers.IO) {
                                    AudioRepository.findCanvasForTrack(appContext, track.uri)
                                }
                                val resolved = localCanvas ?: OnlineCanvasManager.findOnlineCanvas(appContext, track)
                                canvasUri = resolved
                                isVideo = resolved?.let { isVideoUri(appContext, it) } ?: false
                            }
                        },
                        onBookmarksClick = { showBookmarks = true },
                        lyrics = lyrics,
                        isTranslating = isTranslating,
                        onTranslateClick = {
                            if (lyrics.isNotEmpty() && !isTranslating) {
                                showLyrics = true
                                if (lyrics.none { !it.translation.isNullOrBlank() }) {
                                    isTranslating = true
                                    scope.launch {
                                        try {
                                            lyrics = LyricsManager.translateLyrics(appContext, track, lyrics)
                                        } finally {
                                            isTranslating = false
                                        }
                                    }
                                }
                            }
                        },
                        showLyrics = showLyrics,
                        onToggleLyrics = { showLyrics = !showLyrics },
                        iconTint = iconTint
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = showLyrics,
                            transitionSpec = parallaxTransitionSpec,
                            label = "LyricsSwitch"
                        ) { isLyricsVisible ->
                            if (isLyricsVisible) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LyricsView(
                                        lyrics = lyrics,
                                        currentPosition = currentPosition,
                                        isPlaying = isPlaying,
                                        onLineClick = onSeek,
                                        onSearchOnline = {
                                            scope.launch {
                                                isSearchingLyricsOnline = true
                                                try {
                                                    val newLyrics = LyricsManager.searchLyricsOnline(appContext, track)
                                                    if (newLyrics.isNotEmpty()) lyrics = newLyrics
                                                } finally {
                                                    isSearchingLyricsOnline = false
                                                }
                                            }
                                        },
                                        isSearching = isSearchingLyricsOnline,
                                        contentPadding = PaddingValues(top = 100.dp, bottom = 250.dp)
                                    )
                                    Surface(
                                        color = Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .align(Alignment.TopCenter)
                                            .zIndex(1f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.MusicNote,
                                                null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                track.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f, fill = false).basicMarquee()
                                            )
                                            Text(
                                                " • ${track.artist}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f, fill = false).basicMarquee()
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    HorizontalPager(
                                        state = pagerState,
                                        key = { page -> playlist.getOrNull(page)?.id ?: page },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 48.dp),
                                        pageSpacing = 16.dp,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) { page ->
                                        val itemTrack = if (playlist.isNotEmpty()) playlist.getOrNull(page) else track
                                        val artModel = remember(itemTrack?.id, itemTrack?.albumId) {
                                            val saavnTrack = itemTrack?.let { SaavnApi.trackCache[it.id] }
                                            when {
                                                saavnTrack != null && saavnTrack.coverUrl.isNotEmpty() -> saavnTrack.coverUrl
                                                itemTrack != null && itemTrack.albumId != 0L -> ContentUris.withAppendedId(ALBUM_ART_URI, itemTrack.albumId)
                                                else -> null
                                            }
                                        }
                                        val pageInteraction = remember { MutableInteractionSource() }

                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clickable(
                                                    interactionSource = pageInteraction,
                                                    indication = null
                                                ) {
                                                    if (pagerState.currentPage == page) {
                                                        isCoverExpanded = !isCoverExpanded
                                                    }
                                                }
                                        ) {
                                            Card(
                                                shape = RoundedCornerShape(32.dp),
                                                elevation = CardDefaults.cardElevation(if (coverAlpha > 0f) 12.dp else 0.dp),
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .graphicsLayer {
                                                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                                                        val s = 1f - (pageOffset * 0.15f).coerceIn(0f, 0.3f)
                                                        val a = 1f - (pageOffset * 0.5f).coerceIn(0f, 0.5f)
                                                        scaleX = s
                                                        scaleY = s
                                                        this.alpha = a * coverAlpha
                                                    }
                                            ) {
                                                if (artModel != null) {
                                                    AsyncImage(
                                                        model = artModel,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.DarkGray),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            if (isRadio) Icons.Default.Radio else Icons.Rounded.GraphicEq,
                                                            null,
                                                            tint = Color.White.copy(alpha = 0.2f),
                                                            modifier = Modifier.size(120.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AnimatedContent(
                                            targetState = track,
                                            transitionSpec = trackInfoTransitionSpec,
                                            label = "TrackInfo"
                                        ) { targetTrack ->
                                            TrackTitleArtist(track = targetTrack, onArtistClick = onArtistClick)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        PlayerControlsRowHooked(
                            buttonStyle = playerBtnStyle,
                            repeatMode = repeatMode,
                            isPlaying = isPlaying,
                            iconTint = iconTint,
                            showQueue = showQueue,
                            playerBackdrop = playerBackdrop,
                            onToggleRepeat = onToggleRepeat,
                            onSkipPrevious = onSkipPrevious,
                            onPlayPause = onPlayPause,
                            onSkipNext = onSkipNext,
                            onToggleQueue = { showQueue = true }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            PlayerProgressSlider(
                                track = track,
                                isRadio = isRadio,
                                isPlaying = isPlaying,
                                currentPosition = currentPosition,
                                duration = duration,
                                iconTint = iconTint,
                                onSeek = onSeek
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        PlayerBottomInfo(
                            audioOutput = audioOutput,
                            iconTint = iconTint,
                            showTrackInfoBar = showTrackInfoBar,
                            metadata = metadata
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerControlsRowHooked(
    buttonStyle: Int,
    repeatMode: Int,
    isPlaying: Boolean,
    iconTint: Color,
    showQueue: Boolean,
    playerBackdrop: Backdrop,
    onToggleRepeat: () -> Unit,
    onSkipPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val repeatIcon = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
            Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
            else -> Icons.Default.Repeat
        }
        val repeatTint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.5f) else iconTint

        HookedIconButton(
            buttonStyle = buttonStyle,
            icon = repeatIcon,
            onClick = onToggleRepeat,
            tint = repeatTint,
            backdrop = playerBackdrop
        )

        HookedIconButton(
            buttonStyle = buttonStyle,
            icon = Icons.Default.SkipPrevious,
            onClick = onSkipPrevious,
            tint = Color.White,
            backdrop = playerBackdrop
        )

        HookedPlayPauseButton(
            buttonStyle = buttonStyle,
            isPlaying = isPlaying,
            onClick = onPlayPause,
            accentColor = iconTint,
            backdrop = playerBackdrop
        )

        HookedIconButton(
            buttonStyle = buttonStyle,
            icon = Icons.Default.SkipNext,
            onClick = onSkipNext,
            tint = Color.White,
            backdrop = playerBackdrop
        )

        val queueTint = if (showQueue) iconTint else Color.White.copy(alpha = 0.5f)
        HookedIconButton(
            buttonStyle = buttonStyle,
            icon = Icons.Default.QueueMusic,
            onClick = onToggleQueue,
            tint = queueTint,
            backdrop = playerBackdrop
        )
    }
}

@Composable
private fun HookedIconButton(
    buttonStyle: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color,
    backdrop: Backdrop? = null
) {
    when (buttonStyle) {
        0 -> {
            if (backdrop != null) {
                LiquidGlassIconButton(
                    onClick = onClick,
                    backdrop = backdrop,
                    size = 56.dp,
                    activeColor = tint
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
                }
            } else {
                IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
                }
            }
        }
        1 -> {
            ExpressiveIconButton(
                icon = icon,
                onClick = onClick,
                size = 56.dp,
                iconSize = 28.dp
            )
        }
        else -> {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = tint
                )
            ) {
                Icon(icon, null, modifier = Modifier.size(28.dp), tint = tint)
            }
        }
    }
}

@Composable
private fun HookedPlayPauseButton(
    buttonStyle: Int,
    isPlaying: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    backdrop: Backdrop
) {
    when (buttonStyle) {
        0 -> {
            LiquidGlassPlayPauseButton(
                isPlaying = isPlaying,
                onClick = onClick,
                accentColor = accentColor,
                backdrop = backdrop,
                size = 78.dp
            )
        }
        1 -> {
            ExpressivePlayButton(
                isPlaying = isPlaying,
                onClick = onClick,
                size = 78.dp,
                iconSize = 38.dp
            )
        }
        else -> {
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(78.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
    }
}