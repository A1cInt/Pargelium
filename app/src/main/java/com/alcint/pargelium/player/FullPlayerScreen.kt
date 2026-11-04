@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.alcint.pargelium.player

import android.content.ContentUris
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlin.math.absoluteValue

private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
private val GlobalSeedColorCache = LruCache<String, Color>(128)

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
    val haptic = LocalHapticFeedback.current
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
    var isVinylMode by rememberSaveable { mutableStateOf(false) }
    var isSearchingLyricsOnline by remember { mutableStateOf(false) }

    var isOnlineCanvasEnabled by remember {
        mutableStateOf(OnlineCanvasManager.isOnlineCanvasEnabled(appContext))
    }
    var canvasSourceMode by remember {
        mutableStateOf(OnlineCanvasManager.getCanvasSourceMode(appContext))
    }

    var seekRippleDirection by remember { mutableIntStateOf(0) }

    LaunchedEffect(seekRippleDirection) {
        if (seekRippleDirection != 0) {
            delay(650L)
            seekRippleDirection = 0
        }
    }

    val currentPosState = rememberUpdatedState(currentPosition)

    val trackKey = remember(track.artist, track.title) {
        "${track.artist.trim().lowercase()}_${track.title.trim().lowercase()}".hashCode().toString()
    }
    val legacyTrackKey = remember(track.artist, track.title) {
        (track.artist.hashCode() xor track.title.hashCode()).toString()
    }

    var canvasUri by remember { mutableStateOf<Uri?>(null) }
    var isVideo by remember { mutableStateOf(false) }
    var dynamicSeedColor by remember(seedColor) { mutableStateOf(seedColor) }
    val iconTint = if (dynamicSeedColor == Color.Unspecified || dynamicSeedColor == Color.Transparent) {
        MaterialTheme.colorScheme.primary
    } else {
        dynamicSeedColor
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
            scope.launch {
                isVideo = isVideoUriAsync(appContext, uri)
            }
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
                        animationSpec = tween(420, easing = FastOutSlowInEasing)
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
        lyrics = LyricsManager.getLyrics(appContext, track)
    }

    LaunchedEffect(track.id, track.uri, trackKey, isOnlineCanvasEnabled, canvasSourceMode) {
        canvasUri = null
        isVideo = false

        val customCanvasStr = PrefsManager.getCustomCanvas(trackKey)
            ?: PrefsManager.getCustomCanvas(legacyTrackKey)

        if (!customCanvasStr.isNullOrBlank()) {
            val uri = Uri.parse(customCanvasStr)
            canvasUri = uri
            isVideo = isVideoUriAsync(appContext, uri)
            return@LaunchedEffect
        }

        val localCanvas = withContext(Dispatchers.IO) {
            AudioRepository.findCanvasForTrack(appContext, track.uri)
        }
        if (localCanvas != null) {
            canvasUri = localCanvas
            isVideo = isVideoUriAsync(appContext, localCanvas)
            return@LaunchedEffect
        }

        if (isOnlineCanvasEnabled) {
            val onlineCanvas = OnlineCanvasManager.findOnlineCanvas(appContext, track)
            if (onlineCanvas != null) {
                canvasUri = onlineCanvas
                isVideo = isVideoUriAsync(appContext, onlineCanvas)
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
            currentPosition = currentPosState.value,
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            scrimColor = Color.Black.copy(alpha = 0.55f)
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(id = R.string.playback_queue),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        val remainingTracks = (playlist.size - (currentQueueIndex + 1)).coerceAtLeast(0)
                        Text(
                            text = "${currentQueueIndex + 1} / ${playlist.size} • $remainingTracks up next",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(0.62f)) {
                    itemsIndexed(
                        items = playlist,
                        key = { index, item -> "${index}_${item.id}" },
                        contentType = { _, _ -> "queue_item" }
                    ) { index, item ->
                        val isCurrent = index == currentQueueIndex || (currentQueueIndex == -1 && item.id == track.id)
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = item.title,
                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isCurrent) iconTint else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = item.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                if (isCurrent) {
                                    Icon(Icons.Rounded.GraphicEq, null, tint = iconTint)
                                } else {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { onRemoveFromQueue(index) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isCurrent) iconTint.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
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
        animationSpec = tween(550, easing = LinearOutSlowInEasing),
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
                (slideInVertically(tween(450, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(450)))
                    .togetherWith(slideOutVertically(tween(450, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(450)))
            } else {
                (slideInVertically(tween(450, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(450)))
                    .togetherWith(slideOutVertically(tween(450, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(450)))
            }
        }
    }

    val trackInfoTransitionSpec: AnimatedContentTransitionScope<AudioTrack>.() -> ContentTransform = remember {
        {
            (fadeIn(animationSpec = tween(350)) + scaleIn(initialScale = 0.95f, animationSpec = tween(350)))
                .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300)))
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
                animationSpec = tween(380),
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
                                    LyricsHeaderBadge(
                                        track = track,
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .align(Alignment.TopCenter)
                                            .zIndex(1f)
                                    )
                                    LyricsView(
                                        lyrics = lyrics,
                                        currentPosition = currentPosState.value,
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
                                val artModel = remember(track.id, track.albumId) {
                                    val saavnTrack = SaavnApi.trackCache[track.id]
                                    when {
                                        saavnTrack != null && saavnTrack.coverUrl.isNotEmpty() -> saavnTrack.coverUrl
                                        track.albumId != 0L -> ContentUris.withAppendedId(ALBUM_ART_URI, track.albumId)
                                        else -> null
                                    }
                                }
                                var preferredDiscUri by remember(track.id, track.albumId) { mutableStateOf<Uri?>(null) }
                                LaunchedEffect(track.id, track.albumId, isVinylMode) {
                                    if (isVinylMode) {
                                        preferredDiscUri = withContext(Dispatchers.IO) {
                                            AudioRepository.getPreferredDiscArtworkUri(appContext, track)
                                        }
                                    }
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxHeight(0.82f)
                                        .aspectRatio(1f)
                                ) {
                                    PlayerArtworkDeck(
                                        artModel = if (isVinylMode && preferredDiscUri != null) preferredDiscUri else artModel,
                                        hasAuthenticDiscArt = isVinylMode && preferredDiscUri != null,
                                        isRadio = isRadio,
                                        isPlaying = isPlaying,
                                        isVinylMode = isVinylMode,
                                        coverAlpha = coverAlpha,
                                        accentColor = iconTint,
                                        onSingleTap = { isCoverExpanded = !isCoverExpanded },
                                        onLongPress = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isVinylMode = !isVinylMode
                                        },
                                        onDoubleTapSeek = { forward ->
                                            if (!isRadio) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                seekRippleDirection = if (forward) 1 else -1
                                                val delta = if (forward) 10_000L else -10_000L
                                                onSeek((currentPosState.value + delta).coerceIn(0L, duration))
                                            }
                                        }
                                    )
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
                        Spacer(Modifier.height(28.dp))

                        PlayerProgressSlider(
                            track = track,
                            isRadio = isRadio,
                            isPlaying = isPlaying,
                            currentPosition = currentPosState.value,
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
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.32f))
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
                                val resolved = localCanvas ?: if (isOnlineCanvasEnabled) {
                                    OnlineCanvasManager.findOnlineCanvas(appContext, track)
                                } else null
                                canvasUri = resolved
                                isVideo = resolved?.let { isVideoUriAsync(appContext, it) } ?: false
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
                        iconTint = iconTint,
                        isVinylMode = isVinylMode,
                        onToggleVinylMode = { isVinylMode = !isVinylMode },
                        isOnlineCanvasEnabled = isOnlineCanvasEnabled,
                        onToggleOnlineCanvas = { enabled ->
                            OnlineCanvasManager.setOnlineCanvasEnabled(appContext, enabled)
                            isOnlineCanvasEnabled = enabled
                        },
                        canvasSourceMode = canvasSourceMode,
                        onCanvasSourceModeChange = { mode ->
                            OnlineCanvasManager.setCanvasSourceMode(appContext, mode)
                            canvasSourceMode = mode
                        }
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
                                        currentPosition = currentPosState.value,
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
                                    LyricsHeaderBadge(
                                        track = track,
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .align(Alignment.TopCenter)
                                            .zIndex(1f)
                                    )
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        HorizontalPager(
                                            state = pagerState,
                                            key = { page -> playlist.getOrNull(page)?.id ?: page },
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(horizontal = 44.dp),
                                            pageSpacing = 18.dp,
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

                                            var pageDiscUri by remember(itemTrack?.id, itemTrack?.albumId) {
                                                mutableStateOf<Uri?>(null)
                                            }
                                            LaunchedEffect(itemTrack?.id, itemTrack?.albumId, isVinylMode) {
                                                if (isVinylMode && itemTrack != null) {
                                                    pageDiscUri = withContext(Dispatchers.IO) {
                                                        AudioRepository.getPreferredDiscArtworkUri(appContext, itemTrack)
                                                    }
                                                }
                                            }

                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f)
                                                    .graphicsLayer {
                                                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                                                        val s = 1f - (pageOffset * 0.14f).coerceIn(0f, 0.28f)
                                                        val a = 1f - (pageOffset * 0.45f).coerceIn(0f, 0.5f)
                                                        scaleX = s
                                                        scaleY = s
                                                        this.alpha = a
                                                    }
                                            ) {
                                                PlayerArtworkDeck(
                                                    artModel = if (isVinylMode && pageDiscUri != null) pageDiscUri else artModel,
                                                    hasAuthenticDiscArt = isVinylMode && pageDiscUri != null,
                                                    isRadio = isRadio,
                                                    isPlaying = isPlaying && pagerState.currentPage == page,
                                                    isVinylMode = isVinylMode,
                                                    coverAlpha = coverAlpha,
                                                    accentColor = iconTint,
                                                    onSingleTap = {
                                                        if (pagerState.currentPage == page) {
                                                            isCoverExpanded = !isCoverExpanded
                                                        }
                                                    },
                                                    onLongPress = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        isVinylMode = !isVinylMode
                                                    },
                                                    onDoubleTapSeek = { forward ->
                                                        if (!isRadio && pagerState.currentPage == page) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            seekRippleDirection = if (forward) 1 else -1
                                                            val delta = if (forward) 10_000L else -10_000L
                                                            onSeek((currentPosState.value + delta).coerceIn(0L, duration))
                                                        }
                                                    }
                                                )
                                            }
                                        }

                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = seekRippleDirection != 0,
                                            enter = fadeIn(tween(150)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 400f)),
                                            exit = fadeOut(tween(200)) + scaleOut(tween(200))
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = Color.Black.copy(alpha = 0.75f),
                                                contentColor = Color.White,
                                                modifier = Modifier.border(1.dp, iconTint.copy(alpha = 0.6f), RoundedCornerShape(50))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (seekRippleDirection > 0) Icons.Rounded.Forward10 else Icons.Rounded.Replay10,
                                                        contentDescription = null,
                                                        tint = iconTint,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                    Text(
                                                        text = if (seekRippleDirection > 0) "+10 sec" else "-10 sec",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
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
                        Spacer(modifier = Modifier.height(12.dp))
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
                        Spacer(modifier = Modifier.height(20.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            PlayerProgressSlider(
                                track = track,
                                isRadio = isRadio,
                                isPlaying = isPlaying,
                                currentPosition = currentPosState.value,
                                duration = duration,
                                iconTint = iconTint,
                                onSeek = onSeek
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        PlayerBottomInfo(
                            audioOutput = audioOutput,
                            iconTint = iconTint,
                            showTrackInfoBar = showTrackInfoBar,
                            metadata = metadata
                        )
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerArtworkDeck(
    artModel: Any?,
    hasAuthenticDiscArt: Boolean,
    isRadio: Boolean,
    isPlaying: Boolean,
    isVinylMode: Boolean,
    coverAlpha: Float,
    accentColor: Color,
    onSingleTap: () -> Unit,
    onLongPress: () -> Unit,
    onDoubleTapSeek: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val cornerPercent by animateIntAsState(
        targetValue = if (isVinylMode) 50 else 12,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "vinylShapeMorph"
    )

    val rotationAnim = remember { Animatable(0f) }
    LaunchedEffect(isVinylMode, isPlaying) {
        if (isVinylMode && isPlaying) {
            while (isActive) {
                val start = rotationAnim.value % 360f
                rotationAnim.snapTo(start)
                rotationAnim.animateTo(
                    targetValue = start + 360f,
                    animationSpec = tween(durationMillis = 11000, easing = LinearEasing)
                )
            }
        } else if (!isVinylMode && rotationAnim.value != 0f) {
            rotationAnim.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isRadio) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onLongPress = { onLongPress() },
                    onDoubleTap = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        when {
                            offset.x < width * 0.32f -> onDoubleTapSeek(false)
                            offset.x > width * 0.68f -> onDoubleTapSeek(true)
                            else -> onLongPress()
                        }
                    }
                )
            }
    ) {
        if (isVinylMode && coverAlpha > 0.1f) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .graphicsLayer { alpha = coverAlpha }
            ) {
                val loud = if (isPlaying) globalAudioLoudness.coerceIn(0f, 0.35f) else 0f
                val pulseRadius = (size.minDimension / 2f) * (0.98f + loud * 0.16f)
                drawCircle(
                    color = accentColor.copy(alpha = 0.25f + loud * 0.9f),
                    radius = pulseRadius,
                    style = Stroke(width = (3.dp + (loud * 18).dp).toPx())
                )
            }
        }

        Card(
            shape = RoundedCornerShape(percent = cornerPercent),
            elevation = CardDefaults.cardElevation(if (coverAlpha > 0f) 16.dp else 0.dp),
            modifier = Modifier
                .fillMaxSize(if (isVinylMode) 0.92f else 1f)
                .graphicsLayer {
                    alpha = coverAlpha
                    rotationZ = rotationAnim.value
                }
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = if (isVinylMode) 0.22f else 0.12f),
                    shape = RoundedCornerShape(percent = cornerPercent)
                )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (artModel != null) {
                    AsyncImage(
                        model = remember(artModel) {
                            ImageRequest.Builder(context)
                                .data(artModel)
                                .size(900)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E24)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRadio) Icons.Default.Radio else Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier.size(110.dp)
                        )
                    }
                }

                if (isVinylMode) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val shine = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.16f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.22f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        )
                        drawCircle(brush = shine)

                        if (!hasAuthenticDiscArt) {
                            val grooveColor = Color.White.copy(alpha = 0.14f)
                            drawCircle(color = grooveColor, radius = size.minDimension * 0.45f, style = Stroke(width = 1.5f))
                            drawCircle(color = grooveColor, radius = size.minDimension * 0.36f, style = Stroke(width = 1f))
                            drawCircle(color = grooveColor, radius = size.minDimension * 0.27f, style = Stroke(width = 1f))
                            drawCircle(color = accentColor.copy(alpha = 0.85f), radius = size.minDimension * 0.14f)
                        }

                        drawCircle(color = Color(0xFF0E0E10), radius = size.minDimension * 0.085f)
                        drawCircle(color = Color.White.copy(alpha = 0.85f), radius = size.minDimension * 0.028f)
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsHeaderBadge(
    track: AudioTrack,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50),
        modifier = modifier.border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false).basicMarquee()
            )
            Text(
                text = " • ${track.artist}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false).basicMarquee()
            )
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
                backdrop = playerBackdropOrDefault(backdrop),
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

private fun playerBackdropOrDefault(backdrop: Backdrop): Backdrop = backdrop