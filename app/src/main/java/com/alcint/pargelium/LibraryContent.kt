package com.alcint.pargelium

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AlbumCard(
    album: AlbumModel,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val artUri = remember(album.id) { AudioRepository.getAlbumArtUri(album.id) }

    val imageRequest = remember(artUri) {
        ImageRequest.Builder(context)
            .data(artUri)
            .size(400, 400)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .width(160.dp)
            .tiltOnTouch()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.aspectRatio(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = onSurfaceColor
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = onSurfaceColor.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun MiniVisualizer(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    val bar1 by infiniteTransition.animateFloat(0.2f, 0.8f, infiniteRepeatable(tween(400), RepeatMode.Reverse), "1")
    val bar2 by infiniteTransition.animateFloat(0.3f, 0.9f, infiniteRepeatable(tween(550), RepeatMode.Reverse), "2")
    val bar3 by infiniteTransition.animateFloat(0.4f, 0.7f, infiniteRepeatable(tween(300), RepeatMode.Reverse), "3")

    Canvas(modifier = modifier) {
        val barWidth = size.width / 4
        val maxH = size.height
        val values = listOf(bar1, bar2, bar3)
        values.forEachIndexed { index, value ->
            val height = value * maxH
            drawRoundRect(
                color = color,
                topLeft = Offset(x = index * (barWidth + 4f), y = maxH - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackRow(
    track: AudioTrack,
    isPlaying: Boolean,
    isCurrentTrack: Boolean,
    albumArt: Uri? = null,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.StartToEnd) {
                onPlayNext()
                return@rememberSwipeToDismissBoxState false
            }
            false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }
    )

    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (isCurrentTrack)
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        else
            Color.Transparent,
        animationSpec = tween(400, easing = LinearOutSlowInEasing),
        label = "bg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isCurrentTrack)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(400, easing = LinearOutSlowInEasing),
        label = "contentColor"
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(backgroundColor, RoundedCornerShape(12.dp)),
        backgroundContent = {
            val isSwiping = dismissState.targetValue != SwipeToDismissBoxValue.Settled || dismissState.progress > 0f

            val iconScale by animateFloatAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) 1.3f else 0.8f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "iconScale"
            )

            val swipeIconColor by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(300),
                label = "swipeIconColor"
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isSwiping) {
                    Icon(Icons.Default.SkipNext, "Слушать следующим", tint = swipeIconColor, modifier = Modifier.scale(iconScale))
                }
            }
        },
        content = {
            var showMenu by remember { mutableStateOf(false) }
            val context = LocalContext.current

            val thumbRequest = remember(albumArt) {
                if (albumArt != null) ImageRequest.Builder(context).data(albumArt).size(100).crossfade(true).build() else null
            }

            ListItem(
                headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal, color = contentColor) },
                supportingContent = { Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = contentColor.copy(alpha = 0.7f)) },
                leadingContent = {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        // Чистая обложка без дополнительных слоев и свечения
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbRequest != null) {
                                AsyncImage(model = thumbRequest, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                            }
                            if (isCurrentTrack) {
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                                if (isPlaying) MiniVisualizer(color = Color.White, modifier = Modifier.size(24.dp))
                                else Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                },
                trailingContent = {
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = contentColor.copy(alpha = 0.7f)) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Слушать следующим") }, onClick = { onPlayNext(); showMenu = false }, leadingIcon = { Icon(Icons.Default.SkipNext, null) })
                            DropdownMenuItem(text = { Text("В конец очереди") }, onClick = { onAddToQueue(); showMenu = false }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) })
                            DropdownMenuItem(text = { Text("В плейлист") }, onClick = { onAddToPlaylist(); showMenu = false }, leadingIcon = { Icon(Icons.Filled.Add, null) })
                            DropdownMenuItem(text = { Text("Поделиться") }, onClick = { onShare(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Share, null) })
                        }
                    }
                },
                modifier = Modifier.clickable { onClick() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AlbumDetailView(
    album: AlbumModel,
    animatedVisibilityScope: AnimatedVisibilityScope,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackSelect: (AudioTrack) -> Unit,
    onPlayPause: () -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onShareTrack: (AudioTrack) -> Unit,
    onArtistClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val artUri = remember(album.id) { AudioRepository.getAlbumArtUri(album.id) }

    val tracksByDisc = remember(album.tracks) { album.tracks.groupBy { if (it.discNumber == 0) 1 else it.discNumber }.toSortedMap() }
    val hasMultipleDiscs = tracksByDisc.size > 1
    val scrollState = rememberLazyListState()
    val density = LocalDensity.current

    val bannerHeight = 400.dp
    val bannerHeightPx = with(density) { bannerHeight.toPx() }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onBgColor = MaterialTheme.colorScheme.onBackground
    val bgColor = MaterialTheme.colorScheme.background

    val barAlpha by remember {
        derivedStateOf {
            val offset = scrollState.firstVisibleItemScrollOffset
            val index = scrollState.firstVisibleItemIndex
            if (index > 0) 1f else (offset / (bannerHeightPx * 0.7f)).coerceIn(0f, 1f)
        }
    }

    val showBarTitle by remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex > 0
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {

        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            item(key = "header_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bannerHeight)
                        .graphicsLayer {
                            translationY = scrollState.firstVisibleItemScrollOffset * 0.4f
                            alpha = 1f - (scrollState.firstVisibleItemScrollOffset / bannerHeightPx).coerceIn(0f, 1f)
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(artUri).size(800).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(
                        colors = listOf(Color.Transparent, bgColor.copy(alpha = 0.6f), bgColor),
                        startY = 100f
                    )))
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = 16.dp)
                    ) {
                        Text(album.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = onBgColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

                        Text(
                            text = album.artist,
                            style = MaterialTheme.typography.titleLarge,
                            color = primaryColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onArtistClick(album.artist) }
                                .padding(vertical = 4.dp)
                        )

                        Spacer(Modifier.height(8.dp))
                        Text(pluralStringResource(R.plurals.tracks_count, album.tracks.size, album.tracks.size), style = MaterialTheme.typography.labelMedium, color = onBgColor.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            tracksByDisc.forEach { (discNum, tracks) ->
                if (hasMultipleDiscs) {
                    item(key = "disc_$discNum") {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Album, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.txt_disc, discNum), style = MaterialTheme.typography.titleSmall, color = primaryColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                itemsIndexed(tracks, key = { _, t -> t.id }) { _, track ->
                    val isActive = currentTrack?.id == track.id
                    TrackRow(track = track, isPlaying = isPlaying, isCurrentTrack = isActive, albumArt = artUri, onClick = { onTrackSelect(track) }, onPlayNext = { onPlayNext(track) }, onAddToQueue = { onAddToQueue(track) }, onAddToPlaylist = { onAddToPlaylist(track) }, onShare = { onShareTrack(track) })
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(bgColor.copy(alpha = barAlpha))
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .zIndex(2f)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
            ) {
                val btnBgAlpha = (1f - barAlpha).coerceIn(0f, 0.4f)
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Back",
                    modifier = Modifier.background(Color.Black.copy(alpha = btnBgAlpha), CircleShape),
                    tint = if (barAlpha > 0.5f) onBgColor else Color.White
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showBarTitle,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp)
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onBgColor
                )
            }
        }

        val fabBottomPadding by animateDpAsState(
            targetValue = if (currentTrack != null) 190.dp else 130.dp,
            animationSpec = tween(400, easing = LinearOutSlowInEasing),
            label = "fabBottomPadding"
        )

        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = fabBottomPadding, end = 16.dp)) {
            AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                FloatingActionButton(
                    onClick = {
                        if (currentTrack?.albumId == album.id) onPlayPause()
                        else if (album.tracks.isNotEmpty()) onTrackSelect(album.tracks[0])
                    },
                    containerColor = primaryColor
                ) {
                    Icon(if (isPlaying && currentTrack?.albumId == album.id) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShareTrackSheet(track: AudioTrack, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val artUri = remember(track.albumId) { AudioRepository.getAlbumArtUri(track.albumId) }

    val dominantColor = rememberDominantColor(artUri)
    val cardColor = if (dominantColor != Color.Unspecified) dominantColor else MaterialTheme.colorScheme.surface

    val isDarkBackground = cardColor.luminance() < 0.5f

    val primaryTextColor = if (isDarkBackground) Color.White else Color.Black
    val secondaryTextColor = primaryTextColor.copy(alpha = 0.7f)
    val watermarkColor = primaryTextColor.copy(alpha = 0.2f)

    var metadata by remember { mutableStateOf("Загрузка...") }
    var randomLyric by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(track) {
        metadata = withContext(Dispatchers.IO) { AudioRepository.getTrackMetadata(context, track.uri) }

        val lyrics = withContext(Dispatchers.IO) { LyricsManager.getLyrics(context, track) }
        val validLines = lyrics.map { it.text.trim() }.filter { it.isNotEmpty() && !it.startsWith("[") }
        if (validLines.isNotEmpty()) {
            randomLyric = validLines.random()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(28.dp, RoundedCornerShape(32.dp))
                    .clip(RoundedCornerShape(32.dp))
                    .background(cardColor)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(artUri).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(32.dp)
                        .alpha(0.5f)
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    cardColor.copy(alpha = 0.7f),
                                    cardColor
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                ) {
                    Text(
                        text = "PARGELIUM",
                        style = MaterialTheme.typography.labelLarge,
                        color = watermarkColor,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                        modifier = Modifier.padding(bottom = 28.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(artUri).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(160.dp)
                                .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = Color.Black)
                                .clip(RoundedCornerShape(24.dp))
                        )

                        Spacer(Modifier.width(24.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryTextColor,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 34.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.titleLarge,
                                color = secondaryTextColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = track.album,
                                style = MaterialTheme.typography.bodyLarge,
                                color = secondaryTextColor.copy(alpha = 0.6f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(36.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TrackInfoChip(metadata, primaryTextColor)
                        TrackInfoChip(formatTrackDuration(track.duration), primaryTextColor)
                        if (track.trackNumber > 0) {
                            TrackInfoChip("Трек ${track.trackNumber}", primaryTextColor)
                        }
                    }

                    if (randomLyric != null) {
                        Spacer(Modifier.height(28.dp))
                        Text(
                            text = "« $randomLyric »",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Отправить файл",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = {
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            type = "audio/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, track.uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Поделиться треком"))
                        onDismiss()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Поделиться",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun TrackInfoChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
    }
}

fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}