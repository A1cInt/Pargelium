@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.alcint.pargelium.library

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alcint.pargelium.AlbumArtworkItem
import com.alcint.pargelium.AlbumModel
import com.alcint.pargelium.ArtworkCategory
import com.alcint.pargelium.AudioRepository
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.R as AppR
import com.alcint.pargelium.rememberDominantColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

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
    onArtistClick: (String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val mainCoverUri = remember(album.id) { AudioRepository.getAlbumArtUri(album.id) }
    val dominantColor = rememberDominantColor(mainCoverUri)
    val accentColor = if (dominantColor != Color.Unspecified) dominantColor else MaterialTheme.colorScheme.primary

    var activeSection by remember { mutableIntStateOf(0) }
    var selectedCategory by remember { mutableStateOf<ArtworkCategory?>(null) }
    var selectedDisc by remember { mutableIntStateOf(0) }
    var isTrackSearchOpen by remember { mutableStateOf(false) }
    var trackSearchQuery by remember { mutableStateOf("") }
    var showBatchMenu by remember { mutableStateOf(false) }

    var artworksList by remember(album.id) { mutableStateOf<List<AlbumArtworkItem>>(emptyList()) }
    var isScanningArtworks by remember(album.id) { mutableStateOf(true) }
    var albumTechMetadata by remember(album.id) { mutableStateOf("") }
    var albumFolderPath by remember(album.id) { mutableStateOf<String?>(null) }
    var lightboxInitialIndex by remember { mutableStateOf<Int?>(null) }

    val isAlbumCurrentlyPlaying = remember(currentTrack, album.id, isPlaying) {
        currentTrack?.albumId == album.id && isPlaying
    }
    val isAlbumLoadedInPlayer = remember(currentTrack, album.id) {
        currentTrack?.albumId == album.id
    }

    LaunchedEffect(album.id) {
        isScanningArtworks = true
        withContext(Dispatchers.IO) {
            val loadedArt = AudioRepository.getAlbumArtworks(context, album)
            val firstUri = album.tracks.firstOrNull()?.uri
            val techMeta = if (firstUri != null) AudioRepository.getTrackMetadata(context, firstUri) else ""
            val folder = AudioRepository.getAlbumFolderPath(context, album)
            withContext(Dispatchers.Main) {
                artworksList = loadedArt
                albumTechMetadata = techMeta
                albumFolderPath = folder
                isScanningArtworks = false
            }
        }
    }

    val discNumbers = remember(album.tracks) {
        album.tracks.map { it.discNumber }.distinct().sorted()
    }
    val isMultiDisc = discNumbers.size > 1

    val displayedTracks = remember(album.tracks, selectedDisc, trackSearchQuery) {
        album.tracks.filter { track ->
            val matchesDisc = selectedDisc == 0 || track.discNumber == selectedDisc
            val matchesQuery = trackSearchQuery.isBlank() ||
                    track.title.contains(trackSearchQuery, ignoreCase = true) ||
                    track.artist.contains(trackSearchQuery, ignoreCase = true)
            matchesDisc && matchesQuery
        }
    }

    val groupedTracks = remember(displayedTracks) {
        displayedTracks.groupBy { it.discNumber }.toSortedMap()
    }

    val filteredArtworks = remember(artworksList, selectedCategory) {
        if (selectedCategory == null) artworksList
        else artworksList.filter { it.category == selectedCategory }
    }

    val discArtworkUri = remember(artworksList, mainCoverUri) {
        artworksList.firstOrNull { it.category == ArtworkCategory.DISC }?.uri ?: mainCoverUri
    }

    val totalDurationMs = remember(album.tracks) {
        album.tracks.sumOf { it.duration }
    }

    val collapsedHeaderAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 500f).coerceIn(0f, 1f)
        }
    }

    BackHandler(enabled = lightboxInitialIndex != null || activeSection == 1 || isTrackSearchOpen) {
        when {
            lightboxInitialIndex != null -> lightboxInitialIndex = null
            isTrackSearchOpen -> {
                isTrackSearchOpen = false
                trackSearchQuery = ""
                focusManager.clearFocus()
            }
            activeSection == 1 -> activeSection = 0
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.38f),
                            accentColor.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 250.dp)
        ) {
            item(key = "album_hero_header") {
                AlbumHeroSection(
                    album = album,
                    mainCoverUri = mainCoverUri,
                    discArtworkUri = discArtworkUri,
                    animatedVisibilityScope = animatedVisibilityScope,
                    isAlbumPlaying = isAlbumCurrentlyPlaying,
                    accentColor = accentColor,
                    totalDurationMs = totalDurationMs,
                    techMetadata = albumTechMetadata,
                    artworksCount = artworksList.size,
                    activeSection = activeSection,
                    onSectionChange = { activeSection = it },
                    onArtistClick = { onArtistClick(album.artist) },
                    onCoverClick = {
                        activeSection = 1
                        if (artworksList.isNotEmpty()) lightboxInitialIndex = 0
                    },
                    onPlayAlbumClick = {
                        if (isAlbumLoadedInPlayer) {
                            onPlayPause()
                        } else if (displayedTracks.isNotEmpty()) {
                            onTrackSelect(displayedTracks.first())
                        }
                    },
                    onShuffleAlbumClick = {
                        if (displayedTracks.isNotEmpty()) {
                            val randomTrack = displayedTracks.random()
                            onTrackSelect(randomTrack)
                        }
                    }
                )
            }

            if (activeSection == 0) {
                item(key = "album_track_controls") {
                    AlbumTrackControlsBar(
                        isMultiDisc = isMultiDisc,
                        discNumbers = discNumbers,
                        selectedDisc = selectedDisc,
                        onSelectDisc = { selectedDisc = it },
                        isSearchOpen = isTrackSearchOpen,
                        searchQuery = trackSearchQuery,
                        onSearchQueryChange = { trackSearchQuery = it },
                        onToggleSearch = {
                            isTrackSearchOpen = !isTrackSearchOpen
                            if (!isTrackSearchOpen) {
                                trackSearchQuery = ""
                                focusManager.clearFocus()
                            }
                        }
                    )
                }

                if (displayedTracks.isEmpty()) {
                    item(key = "empty_tracks") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = AppR.string.search_no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    groupedTracks.forEach { (discNum, tracksInDisc) ->
                        if (isMultiDisc && selectedDisc == 0) {
                            item(key = "disc_header_$discNum") {
                                DiscDividerHeader(
                                    discNumber = discNum,
                                    trackCount = tracksInDisc.size,
                                    accentColor = accentColor,
                                    onPlayDisc = {
                                        tracksInDisc.firstOrNull()?.let { onTrackSelect(it) }
                                    }
                                )
                            }
                        }

                        items(
                            items = tracksInDisc,
                            key = { it.id }
                        ) { track ->
                            AlbumTrackItem(
                                track = track,
                                albumArtist = album.artist,
                                isCurrentTrack = currentTrack?.id == track.id,
                                isPlaying = isPlaying,
                                accentColor = accentColor,
                                onClick = { onTrackSelect(track) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onShare = { onShareTrack(track) }
                            )
                        }
                    }
                }

                item(key = "album_footer_specs") {
                    AlbumReleaseSpecsCard(
                        totalDurationMs = totalDurationMs,
                        techMetadata = albumTechMetadata,
                        artworksCount = artworksList.size,
                        folderPath = albumFolderPath,
                        accentColor = accentColor,
                        onOpenGallery = { activeSection = 1 }
                    )
                }
            } else {
                item(key = "gallery_section") {
                    AlbumGallerySection(
                        allArtworks = artworksList,
                        filteredArtworks = filteredArtworks,
                        isScanning = isScanningArtworks,
                        selectedCategory = selectedCategory,
                        accentColor = accentColor,
                        onSelectCategory = { selectedCategory = it },
                        onArtworkClick = { clickedItem ->
                            val idx = artworksList.indexOfFirst { it.uri == clickedItem.uri }.coerceAtLeast(0)
                            lightboxInitialIndex = idx
                        }
                    )
                }
            }
        }

        AlbumTopCollapsedBar(
            title = album.title,
            artist = album.artist,
            coverUri = mainCoverUri,
            collapsedAlpha = collapsedHeaderAlpha,
            activeSection = activeSection,
            artworksCount = artworksList.size,
            showBatchMenu = showBatchMenu,
            onToggleBatchMenu = { showBatchMenu = it },
            onBack = onBack,
            onToggleGallery = { activeSection = if (activeSection == 0) 1 else 0 },
            onPlayNextAll = {
                displayedTracks.asReversed().forEach { onPlayNext(it) }
                showBatchMenu = false
            },
            onAddToQueueAll = {
                displayedTracks.forEach { onAddToQueue(it) }
                showBatchMenu = false
            }
        )

        AnimatedVisibility(
            visible = lightboxInitialIndex != null && artworksList.isNotEmpty(),
            enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.94f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.94f),
            modifier = Modifier.zIndex(20f)
        ) {
            lightboxInitialIndex?.let { startIndex ->
                AlbumArtworkLightbox(
                    artworks = artworksList,
                    initialIndex = startIndex.coerceIn(0, (artworksList.size - 1).coerceAtLeast(0)),
                    onDismiss = { lightboxInitialIndex = null }
                )
            }
        }
    }
}

@Composable
private fun AlbumTopCollapsedBar(
    title: String,
    artist: String,
    coverUri: Uri,
    collapsedAlpha: Float,
    activeSection: Int,
    artworksCount: Int,
    showBatchMenu: Boolean,
    onToggleBatchMenu: (Boolean) -> Unit,
    onBack: () -> Unit,
    onToggleGallery: () -> Unit,
    onPlayNextAll: () -> Unit,
    onAddToQueueAll: () -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.surface.copy(alpha = (collapsedAlpha * 0.94f).coerceIn(0f, 0.94f))

    Surface(
        color = bgColor,
        tonalElevation = if (collapsedAlpha > 0.6f) 4.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(10f)
    ) {
        Column {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = AppR.string.album_action_back)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            alpha = collapsedAlpha
                            translationY = (1f - collapsedAlpha) * 20f
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = coverUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                BadgedBox(
                    badge = {
                        if (artworksCount > 1) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(text = artworksCount.toString(), fontSize = 10.sp)
                            }
                        }
                    }
                ) {
                    FilledTonalIconButton(
                        onClick = onToggleGallery,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (activeSection == 1) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (activeSection == 1) Icons.Rounded.LibraryMusic else Icons.Rounded.Collections,
                            contentDescription = stringResource(id = AppR.string.album_tab_gallery)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box {
                    FilledTonalIconButton(
                        onClick = { onToggleBatchMenu(true) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(id = AppR.string.album_more_options)
                        )
                    }

                    DropdownMenu(
                        expanded = showBatchMenu,
                        onDismissRequest = { onToggleBatchMenu(false) },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = AppR.string.album_play_next_all)) },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                            onClick = onPlayNextAll
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = AppR.string.album_add_to_queue_all)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
                            onClick = onAddToQueueAll
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedTransitionScope.AlbumHeroSection(
    album: AlbumModel,
    mainCoverUri: Uri,
    discArtworkUri: Uri,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isAlbumPlaying: Boolean,
    accentColor: Color,
    totalDurationMs: Long,
    techMetadata: String,
    artworksCount: Int,
    activeSection: Int,
    onSectionChange: (Int) -> Unit,
    onArtistClick: () -> Unit,
    onCoverClick: () -> Unit,
    onPlayAlbumClick: () -> Unit,
    onShuffleAlbumClick: () -> Unit
) {
    val context = LocalContext.current

    val discPeekOffset by animateDpAsState(
        targetValue = if (isAlbumPlaying) 54.dp else 26.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "discPeek"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(236.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .offset(y = 10.dp)
                    .blur(40.dp)
                    .background(accentColor.copy(alpha = 0.45f), RoundedCornerShape(32.dp))
            )

            Box(
                modifier = Modifier
                    .offset(x = discPeekOffset)
                    .size(196.dp)
                    .graphicsLayer {
                        rotationZ = if (isAlbumPlaying) spinAngle else 18f
                    }
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFF141416))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCoverClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(discArtworkUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.88f)
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeColor = Color.White.copy(alpha = 0.12f)
                    drawCircle(color = strokeColor, radius = size.minDimension * 0.44f, style = Stroke(width = 1.5f))
                    drawCircle(color = strokeColor, radius = size.minDimension * 0.34f, style = Stroke(width = 1f))
                    drawCircle(color = Color.Black.copy(alpha = 0.55f), radius = size.minDimension * 0.14f)
                    drawCircle(color = Color.White.copy(alpha = 0.6f), radius = size.minDimension * 0.04f)
                }
            }

            Box(
                modifier = Modifier
                    .offset(x = (-14).dp)
                    .size(216.dp)
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "album_${album.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .shadow(24.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .clickable(onClick = onCoverClick)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(mainCoverUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (artworksCount > 1) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Collections,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = artworksCount.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = album.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            onClick = onArtistClick,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumQuickStatPill(
                icon = Icons.Rounded.MusicNote,
                text = pluralStringResource(
                    id = AppR.plurals.tracks_count,
                    count = album.tracks.size,
                    album.tracks.size
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            AlbumQuickStatPill(
                icon = Icons.Rounded.Schedule,
                text = formatAlbumTotalDuration(totalDurationMs)
            )
            if (techMetadata.isNotBlank()) {
                val badgeText = remember(techMetadata) {
                    techMetadata.split("•").firstOrNull()?.trim() ?: techMetadata
                }
                Spacer(modifier = Modifier.width(8.dp))
                AlbumQuickStatPill(
                    icon = Icons.Rounded.HighQuality,
                    text = badgeText,
                    highlightColor = accentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPlayAlbumClick,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(
                    imageVector = if (isAlbumPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        id = if (isAlbumPlaying) AppR.string.album_action_pause else AppR.string.album_action_play
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            FilledTonalButton(
                onClick = onShuffleAlbumClick,
                modifier = Modifier.height(54.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = stringResource(id = AppR.string.album_action_shuffle)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = AppR.string.album_action_shuffle),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        AlbumSectionSegmentedSwitch(
            activeSection = activeSection,
            tracksCount = album.tracks.size,
            artworksCount = artworksCount,
            accentColor = accentColor,
            onSectionChange = onSectionChange
        )
    }
}

@Composable
private fun AlbumQuickStatPill(
    icon: ImageVector,
    text: String,
    highlightColor: Color? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlightColor != null) {
            highlightColor.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = highlightColor ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = highlightColor ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlbumSectionSegmentedSwitch(
    activeSection: Int,
    tracksCount: Int,
    artworksCount: Int,
    accentColor: Color,
    onSectionChange: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val isTracksActive = activeSection == 0
            val isGalleryActive = activeSection == 1

            Surface(
                onClick = { onSectionChange(0) },
                shape = RoundedCornerShape(14.dp),
                color = if (isTracksActive) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (isTracksActive) 2.dp else 0.dp,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = if (isTracksActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${stringResource(id = AppR.string.album_tab_tracks)} ($tracksCount)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isTracksActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isTracksActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                onClick = { onSectionChange(1) },
                shape = RoundedCornerShape(14.dp),
                color = if (isGalleryActive) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (isGalleryActive) 2.dp else 0.dp,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Collections,
                        contentDescription = null,
                        tint = if (isGalleryActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${stringResource(id = AppR.string.album_tab_gallery)} ($artworksCount)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isGalleryActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isGalleryActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumTrackControlsBar(
    isMultiDisc: Boolean,
    discNumbers: List<Int>,
    selectedDisc: Int,
    onSelectDisc: (Int) -> Unit,
    isSearchOpen: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isMultiDisc) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedDisc == 0,
                            onClick = { onSelectDisc(0) },
                            label = { Text(stringResource(id = AppR.string.album_all_discs)) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    items(discNumbers) { disc ->
                        FilterChip(
                            selected = selectedDisc == disc,
                            onClick = { onSelectDisc(disc) },
                            label = { Text(stringResource(id = AppR.string.album_disc_number, disc)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Album,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(id = AppR.string.album_tracklist_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = stringResource(id = AppR.string.album_filter_tracks_hint),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = isSearchOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(id = AppR.string.album_filter_tracks_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun DiscDividerHeader(
    discNumber: Int,
    trackCount: Int,
    accentColor: Color,
    onPlayDisc: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Album,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(id = AppR.string.album_disc_number, discNumber),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "• ${pluralStringResource(id = AppR.plurals.tracks_count, count = trackCount, trackCount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onPlayDisc,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(id = AppR.string.album_action_play),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AlbumTrackItem(
    track: AudioTrack,
    albumArtist: String,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val containerColor = if (isCurrentTrack) {
        accentColor.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(34.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentTrack) {
                    AnimatedPlayingBars(isPlaying = isPlaying, color = accentColor)
                } else {
                    Text(
                        text = if (track.trackNumber > 0) {
                            String.format(Locale.US, "%02d", track.trackNumber)
                        } else "-",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrentTrack) accentColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val showTrackArtist = !track.artist.equals(albumArtist, ignoreCase = true) && track.artist.isNotBlank()
                if (showTrackArtist) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatTrackTime(track.duration),
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrentTrack) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = AppR.string.album_track_play_next)) },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = AppR.string.album_track_add_queue)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onAddToQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = AppR.string.album_track_add_playlist)) },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = AppR.string.album_track_share)) },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedPlayingBars(isPlaying: Boolean, color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "bars")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isPlaying) 1f else 0.25f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = if (isPlaying) 0.2f else 0.45f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isPlaying) 0.95f else 0.3f,
        animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )

    Canvas(modifier = Modifier.size(18.dp)) {
        val barWidth = size.width / 5f
        val maxH = size.height
        val values = listOf(bar1, bar2, bar3)
        values.forEachIndexed { idx, fraction ->
            val x = (idx * 2 + 0.5f) * barWidth
            val h = (maxH * fraction).coerceAtLeast(4f)
            drawLine(
                color = color,
                start = Offset(x, maxH - (maxH - h) / 2f),
                end = Offset(x, (maxH - h) / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun AlbumGallerySection(
    allArtworks: List<AlbumArtworkItem>,
    filteredArtworks: List<AlbumArtworkItem>,
    isScanning: Boolean,
    selectedCategory: ArtworkCategory?,
    accentColor: Color,
    onSelectCategory: (ArtworkCategory?) -> Unit,
    onArtworkClick: (AlbumArtworkItem) -> Unit
) {
    val availableCategories = remember(allArtworks) {
        allArtworks.map { it.category }.distinct().sortedBy { it.ordinal }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(id = AppR.string.album_gallery_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(id = AppR.string.album_gallery_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (availableCategories.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text(stringResource(id = AppR.string.album_gallery_filter_all)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                items(availableCategories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            onSelectCategory(if (selectedCategory == category) null else category)
                        },
                        label = { Text(artworkCategoryLabel(category)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        when {
            isScanning -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accentColor)
                }
            }
            filteredArtworks.isEmpty() -> {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HideImage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(id = AppR.string.album_gallery_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(id = AppR.string.album_gallery_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                val rows = remember(filteredArtworks) { filteredArtworks.chunked(2) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { item ->
                                AlbumGalleryCard(
                                    item = item,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onArtworkClick(item) }
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumGalleryCard(
    item: AlbumArtworkItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = artworkCategoryLabel(item.category),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subInfo = buildString {
                    if (item.resolution.isNotEmpty()) append(item.resolution)
                    if (item.sizeBytes > 0L) {
                        if (isNotEmpty()) append(" • ")
                        append(Formatter.formatShortFileSize(context, item.sizeBytes))
                    }
                }
                if (subInfo.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumReleaseSpecsCard(
    totalDurationMs: Long,
    techMetadata: String,
    artworksCount: Int,
    folderPath: String?,
    accentColor: Color,
    onOpenGallery: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = accentColor
                )
                Text(
                    text = stringResource(id = AppR.string.album_specs_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (techMetadata.isNotBlank()) {
                SpecRowItem(
                    label = stringResource(id = AppR.string.album_specs_audio_format),
                    value = techMetadata
                )
            }
            SpecRowItem(
                label = stringResource(id = AppR.string.album_specs_total_duration),
                value = formatAlbumTotalDuration(totalDurationMs)
            )
            SpecRowItem(
                label = stringResource(id = AppR.string.album_specs_artwork_files),
                value = artworksCount.toString()
            )
            if (!folderPath.isNullOrBlank()) {
                SpecRowItem(
                    label = stringResource(id = AppR.string.album_specs_source_folder),
                    value = File(folderPath).name.ifEmpty { folderPath }
                )
            }

            if (artworksCount > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenGallery,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Collections,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = AppR.string.album_open_gallery_btn, artworksCount))
                }
            }
        }
    }
}

@Composable
private fun SpecRowItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
private fun AlbumArtworkLightbox(
    artworks: List<AlbumArtworkItem>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialIndex) { artworks.size }
    val currentItem = artworks.getOrNull(pagerState.currentPage)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = artworks[page]
            var scale by remember(page) { mutableFloatStateOf(1f) }
            var offset by remember(page) { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(page) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            scale = newScale
                            if (newScale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(id = AppR.string.album_action_close))
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${artworks.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            FilledTonalIconButton(
                onClick = {
                    currentItem?.let { art ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, art.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, art.name))
                    }
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Rounded.Share, contentDescription = stringResource(id = AppR.string.album_track_share))
            }
        }

        currentItem?.let { item ->
            Surface(
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val details = buildString {
                            append(artworkCategoryLabel(item.category))
                            if (item.resolution.isNotEmpty()) append(" • ${item.resolution}")
                            if (item.sizeBytes > 0L) append(" • ${Formatter.formatShortFileSize(context, item.sizeBytes)}")
                        }
                        Text(
                            text = details,
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun artworkCategoryLabel(category: ArtworkCategory): String {
    return when (category) {
        ArtworkCategory.COVER -> stringResource(id = AppR.string.artwork_type_cover)
        ArtworkCategory.DISC -> stringResource(id = AppR.string.artwork_type_disc)
        ArtworkCategory.BACK -> stringResource(id = AppR.string.artwork_type_back)
        ArtworkCategory.BOOKLET -> stringResource(id = AppR.string.artwork_type_booklet)
        ArtworkCategory.ARTIST -> stringResource(id = AppR.string.artwork_type_artist)
        ArtworkCategory.EXTRA -> stringResource(id = AppR.string.artwork_type_extra)
    }
}

private fun formatTrackTime(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun formatAlbumTotalDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60000).coerceAtLeast(1)
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}