@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.alcint.pargelium.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.R as AppR
import com.alcint.pargelium.rememberDominantColor
import com.alcint.pargelium.tiltOnTouch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

@Composable
fun SharedTransitionScope.AlbumDetailView(
    album: AlbumModel,
    animatedVisibilityScope: AnimatedVisibilityScope,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    artistAvatarUri: Any? = null,
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
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    val mainCoverUri = remember(album.id) { AudioRepository.getAlbumArtUri(album.id) }
    val dominantColor = rememberDominantColor(mainCoverUri)
    val accentColor = if (dominantColor != Color.Unspecified) dominantColor else MaterialTheme.colorScheme.primary

    var activeSection by remember { mutableIntStateOf(0) }
    var selectedCategory by remember { mutableStateOf<ArtworkCategory?>(null) }
    var selectedDisc by remember { mutableIntStateOf(0) }
    var manualVinylIndex by remember(album.id) { mutableStateOf<Int?>(null) }
    var isTrackSearchOpen by remember { mutableStateOf(false) }
    var trackSearchQuery by remember { mutableStateOf("") }
    var showBatchMenu by remember { mutableStateOf(false) }

    var artworksList by remember(album.id) { mutableStateOf<List<AlbumArtworkItem>>(emptyList()) }
    var resolvedArtistAvatar by remember(album.id, artistAvatarUri) { mutableStateOf(artistAvatarUri) }
    var isScanningArtworks by remember(album.id) { mutableStateOf(true) }
    var isFetchingOnlineArt by remember(album.id) { mutableStateOf(false) }
    var albumTechMetadata by remember(album.id) { mutableStateOf("") }
    var albumFolderPath by remember(album.id) { mutableStateOf<String?>(null) }
    var lightboxInitialIndex by remember { mutableStateOf<Int?>(null) }

    val isAlbumCurrentlyPlaying = remember(currentTrack?.albumId, album.id, isPlaying) {
        currentTrack?.albumId == album.id && isPlaying
    }
    val isAlbumLoadedInPlayer = remember(currentTrack?.albumId, album.id) {
        currentTrack?.albumId == album.id
    }

    LaunchedEffect(album.id, artistAvatarUri) {
        isScanningArtworks = true
        val unknownStr = context.getString(AppR.string.unknown_artist)
        val primaryArtistName = getPrimaryArtist(album.artist, unknownStr)

        val (loadedArt, techMeta, folder, foundAvatar) = withContext(Dispatchers.IO) {
            val art = AudioRepository.getAlbumArtworks(context, album)
            val firstUri = album.tracks.firstOrNull()?.uri
            val meta = if (firstUri != null) AudioRepository.getTrackMetadata(context, firstUri) else ""
            val path = AudioRepository.getAlbumFolderPath(context, album)

            val savedMetaAvatar = PrefsManager.getArtistMetadata(primaryArtistName).avatarUri
            val folderAvatar = if (artistAvatarUri == null && savedMetaAvatar.isNullOrBlank()) {
                ArtistRepository.resolveQuickFolderAssets(context, primaryArtistName, album.tracks).first
            } else null

            val artCategoryAvatar = art.firstOrNull { it.category == ArtworkCategory.ARTIST }?.uri
            val finalAvatar: Any? = artistAvatarUri
                ?: savedMetaAvatar?.takeIf { it.isNotBlank() }
                ?: folderAvatar?.takeIf { it.isNotBlank() }
                ?: artCategoryAvatar

            Quadruple(art, meta, path, finalAvatar)
        }
        artworksList = loadedArt
        albumTechMetadata = techMeta
        albumFolderPath = folder
        resolvedArtistAvatar = foundAvatar
        isScanningArtworks = false
    }

    LaunchedEffect(currentTrack?.id) {
        if (currentTrack?.albumId == album.id) {
            manualVinylIndex = null
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

    val galleryRows = remember(filteredArtworks) {
        filteredArtworks.chunked(2)
    }

    val vinylArtworks = remember(artworksList) {
        val allDiscs = artworksList.filter { it.category == ArtworkCategory.DISC }
        val namedVinyls = allDiscs.filter { AudioRepository.isVinylNamedArtwork(it.name) }
            .sortedWith(compareBy<AlbumArtworkItem> { AudioRepository.extractArtworkSequenceIndex(it.name) }.thenBy { it.name.lowercase() })
        namedVinyls.ifEmpty {
            allDiscs.sortedWith(compareBy<AlbumArtworkItem> { AudioRepository.extractArtworkSequenceIndex(it.name) }.thenBy { it.name.lowercase() })
        }
    }

    val backCoverArtwork = remember(artworksList) {
        artworksList.firstOrNull { it.category == ArtworkCategory.BACK }
    }

    val trackIdToVinylNumberMap = remember(album.tracks, vinylArtworks) {
        val map = HashMap<Long, Int>()
        if (vinylArtworks.isNotEmpty()) {
            val sortedTracks = album.tracks.sortedWith(compareBy<AudioTrack> { it.discNumber }.thenBy { it.trackNumber }.thenBy { it.id })
            val explicitVinylTracks = sortedTracks.filter { AudioRepository.isVinylTrack(it) }
            val pool = if (explicitVinylTracks.isNotEmpty()) explicitVinylTracks else if (vinylArtworks.size > 1) sortedTracks else emptyList()
            if (pool.isNotEmpty()) {
                val total = pool.size.coerceAtLeast(1)
                pool.forEachIndexed { idx, t ->
                    val vNum = ((idx * vinylArtworks.size) / total).coerceIn(0, vinylArtworks.lastIndex) + 1
                    map[t.id] = vNum
                }
            }
        }
        map
    }

    val resolvedAuthenticDiscUri = remember(artworksList, album.tracks, currentTrack, manualVinylIndex, vinylArtworks) {
        when {
            manualVinylIndex != null && manualVinylIndex in vinylArtworks.indices -> {
                vinylArtworks[manualVinylIndex!!].uri
            }
            currentTrack?.albumId == album.id -> {
                AudioRepository.resolveDiscArtworkForTrack(album.tracks, artworksList, currentTrack)
            }
            else -> {
                AudioRepository.resolveDiscArtworkForTrack(album.tracks, artworksList, album.tracks.firstOrNull())
            }
        }
    }
    val discArtworkUri = resolvedAuthenticDiscUri ?: mainCoverUri

    val activeVinylIndex = remember(resolvedAuthenticDiscUri, vinylArtworks) {
        vinylArtworks.indexOfFirst { it.uri == resolvedAuthenticDiscUri }
    }

    val totalDurationMs = remember(album.tracks) {
        album.tracks.sumOf { it.duration }
    }

    val collapsedHeaderAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 520f).coerceIn(0f, 1f)
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
                .height(440.dp)
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.35f),
                                accentColor.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
                }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 240.dp)
        ) {
            item(key = "album_hero_header", contentType = "hero") {
                AlbumHeroSection(
                    album = album,
                    mainCoverUri = mainCoverUri,
                    backCoverItem = backCoverArtwork,
                    discArtworkUri = discArtworkUri,
                    hasAuthenticDiscArt = resolvedAuthenticDiscUri != null,
                    vinylArtworks = vinylArtworks,
                    activeVinylIndex = activeVinylIndex,
                    artistAvatarModel = resolvedArtistAvatar,
                    onSelectVinylDisc = { idx ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        manualVinylIndex = idx
                    },
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
                        if (artworksList.isNotEmpty()) {
                            lightboxInitialIndex = 0
                        } else {
                            activeSection = 1
                        }
                    },
                    onPlayAlbumClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isAlbumLoadedInPlayer) {
                            onPlayPause()
                        } else if (displayedTracks.isNotEmpty()) {
                            onTrackSelect(displayedTracks.first())
                        }
                    },
                    onShuffleAlbumClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (displayedTracks.isNotEmpty()) {
                            onTrackSelect(displayedTracks.random())
                        }
                    }
                )
            }

            if (activeSection == 0) {
                if (displayedTracks.size > 1 && totalDurationMs > 0L) {
                    item(key = "album_flow_timeline", contentType = "timeline") {
                        AlbumDurationSpectrumBar(
                            tracks = displayedTracks,
                            currentTrackId = currentTrack?.id,
                            accentColor = accentColor,
                            onTrackClick = { track ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTrackSelect(track)
                            }
                        )
                    }
                }

                item(key = "album_track_controls", contentType = "controls") {
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
                    item(key = "empty_tracks", contentType = "empty") {
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
                            item(key = "disc_header_$discNum", contentType = "disc_header") {
                                DiscDividerHeader(
                                    discNumber = discNum,
                                    trackCount = tracksInDisc.size,
                                    discDurationMs = tracksInDisc.sumOf { it.duration },
                                    accentColor = accentColor,
                                    onPlayDisc = {
                                        tracksInDisc.firstOrNull()?.let { onTrackSelect(it) }
                                    }
                                )
                            }
                        }

                        items(
                            items = tracksInDisc,
                            key = { it.id },
                            contentType = { "track_item" }
                        ) { track ->
                            AlbumTrackItem(
                                track = track,
                                albumArtist = album.artist,
                                vinylDiscNumber = trackIdToVinylNumberMap[track.id],
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

                item(key = "album_footer_specs", contentType = "specs") {
                    AlbumReleaseSpecsCard(
                        totalDurationMs = totalDurationMs,
                        tracksCount = album.tracks.size,
                        techMetadata = albumTechMetadata,
                        artworksCount = artworksList.size,
                        folderPath = albumFolderPath,
                        accentColor = accentColor,
                        onOpenGallery = { activeSection = 1 }
                    )
                }
            } else {
                val booklets = artworksList.filter { it.category == ArtworkCategory.BOOKLET }
                val cards = artworksList.filter { it.category == ArtworkCategory.CARD }
                val stickers = artworksList.filter { it.category == ArtworkCategory.STICKER }
                val backs = artworksList.filter { it.category == ArtworkCategory.BACK }

                if (booklets.isNotEmpty() || cards.isNotEmpty() || stickers.isNotEmpty() || backs.isNotEmpty()) {
                    item(key = "interactive_collector_desk", contentType = "collector_desk") {
                        InteractiveCollectorDesk(
                            album = album,
                            backs = backs,
                            booklets = booklets,
                            cards = cards,
                            stickers = stickers,
                            accentColor = accentColor,
                            onOpenInLightbox = { clickedArt ->
                                val idx = artworksList.indexOfFirst { it.uri == clickedArt.uri }.coerceAtLeast(0)
                                lightboxInitialIndex = idx
                            }
                        )
                    }
                }

                item(key = "gallery_header", contentType = "gallery_header") {
                    AlbumGalleryHeader(
                        allArtworks = artworksList,
                        isFetchingOnline = isFetchingOnlineArt,
                        selectedCategory = selectedCategory,
                        accentColor = accentColor,
                        onSelectCategory = { selectedCategory = it },
                        onFetchOnlineClick = {
                            if (!isFetchingOnlineArt) {
                                scope.launch {
                                    isFetchingOnlineArt = true
                                    val fetched = AudioRepository.fetchOnlineReleaseArtworks(context, album)
                                    artworksList = fetched
                                    isFetchingOnlineArt = false
                                }
                            }
                        }
                    )
                }

                when {
                    isScanningArtworks -> {
                        item(key = "gallery_loading", contentType = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = accentColor)
                            }
                        }
                    }
                    filteredArtworks.isEmpty() -> {
                        item(key = "gallery_empty", contentType = "empty") {
                            AlbumGalleryEmptyState()
                        }
                    }
                    else -> {
                        items(
                            items = galleryRows,
                            key = { row -> row.first().uri.toString() },
                            contentType = { "gallery_row" }
                        ) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { item ->
                                    AlbumGalleryCard(
                                        item = item,
                                        accentColor = accentColor,
                                        modifier = Modifier.weight(1f),
                                        onCategoryChange = { newCat ->
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            AudioRepository.setArtworkCategoryOverride(context, album.id, item.uri, newCat)
                                            artworksList = AudioRepository.getAlbumArtworks(context, album, forceRefresh = true)
                                        },
                                        onClick = {
                                            val idx = artworksList
                                                .indexOfFirst { it.uri == item.uri }
                                                .coerceAtLeast(0)
                                            lightboxInitialIndex = idx
                                        }
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

        AlbumTopCollapsedBar(
            title = album.title,
            artist = album.artist,
            coverUri = mainCoverUri,
            collapsedAlphaProvider = { collapsedHeaderAlpha },
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
                    accentColor = accentColor,
                    onCategoryChange = { item, newCat ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        AudioRepository.setArtworkCategoryOverride(context, album.id, item.uri, newCat)
                        artworksList = AudioRepository.getAlbumArtworks(context, album, forceRefresh = true)
                    },
                    onDismiss = { lightboxInitialIndex = null }
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun AlbumTopCollapsedBar(
    title: String,
    artist: String,
    coverUri: Uri,
    collapsedAlphaProvider: () -> Float,
    activeSection: Int,
    artworksCount: Int,
    showBatchMenu: Boolean,
    onToggleBatchMenu: (Boolean) -> Unit,
    onBack: () -> Unit,
    onToggleGallery: () -> Unit,
    onPlayNextAll: () -> Unit,
    onAddToQueueAll: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(10f)
            .drawBehind {
                val alpha = (collapsedAlphaProvider() * 0.95f).coerceIn(0f, 0.95f)
                drawRect(color = surfaceColor.copy(alpha = alpha))
                if (alpha > 0.8f) {
                    drawLine(
                        color = dividerColor.copy(alpha = (alpha - 0.8f) * 2f),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
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
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
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
                            val a = collapsedAlphaProvider()
                            alpha = a
                            translationY = (1f - a) * 20f
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
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (activeSection == 1) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
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
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
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
    backCoverItem: AlbumArtworkItem?,
    discArtworkUri: Uri,
    hasAuthenticDiscArt: Boolean,
    vinylArtworks: List<AlbumArtworkItem>,
    activeVinylIndex: Int,
    artistAvatarModel: Any?,
    onSelectVinylDisc: (Int) -> Unit,
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
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme

    var isBoxFlippedToBack: Boolean by rememberSaveable(album.id) { mutableStateOf(false) }
    val boxFlipAngle by animateFloatAsState(
        targetValue = if (isBoxFlippedToBack) 180f else 0f,
        animationSpec = motionScheme.slowSpatialSpec(),
        label = "jewelBoxFlip"
    )

    val discPeekOffset by animateDpAsState(
        targetValue = if (isBoxFlippedToBack) 0.dp else if (isAlbumPlaying) 58.dp else 28.dp,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "discPeek"
    )

    val rotationAnim = remember { Animatable(18f) }
    var isUserScratching by remember { mutableStateOf(false) }

    LaunchedEffect(isAlbumPlaying, isUserScratching, isBoxFlippedToBack) {
        if (isAlbumPlaying && !isUserScratching && !isBoxFlippedToBack) {
            while (isActive) {
                val startAngle = rotationAnim.value % 360f
                rotationAnim.snapTo(startAngle)
                rotationAnim.animateTo(
                    targetValue = startAngle + 360f,
                    animationSpec = tween(durationMillis = 10000, easing = LinearEasing)
                )
            }
        }
    }

    val cachedShineBrush = remember {
        Brush.sweepGradient(
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
    }

    val onAccentColor = if (accentColor.luminance() > 0.55f) Color(0xFF121214) else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp, start = 20.dp, end = 20.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(244.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(270.dp)
                    .offset(y = 12.dp)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.48f),
                            accentColor.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension / 2f
                )
            }

            Box(
                modifier = Modifier
                    .offset(x = discPeekOffset)
                    .size(198.dp)
                    .graphicsLayer {
                        rotationZ = rotationAnim.value
                        alpha = 1f - (boxFlipAngle / 180f).coerceIn(0f, 1f)
                    }
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFF121215))
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                rotationAnim.snapTo((rotationAnim.value + delta * 0.45f) % 360f)
                            }
                        },
                        onDragStarted = {
                            isUserScratching = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragStopped = {
                            isUserScratching = false
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCoverClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = remember(discArtworkUri) {
                        ImageRequest.Builder(context)
                            .data(discArtworkUri)
                            .size(512)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (hasAuthenticDiscArt) 0.96f else 0.84f)
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(brush = cachedShineBrush)

                    if (!hasAuthenticDiscArt) {
                        val grooveColor = Color.White.copy(alpha = 0.12f)
                        drawCircle(color = grooveColor, radius = size.minDimension * 0.46f, style = Stroke(width = 1.2f))
                        drawCircle(color = grooveColor, radius = size.minDimension * 0.39f, style = Stroke(width = 1f))
                        drawCircle(color = grooveColor, radius = size.minDimension * 0.31f, style = Stroke(width = 1f))
                        drawCircle(color = accentColor.copy(alpha = 0.85f), radius = size.minDimension * 0.16f)
                    }

                    drawCircle(color = Color(0xFF101012), radius = size.minDimension * 0.10f)
                    drawCircle(color = Color.White.copy(alpha = 0.75f), radius = size.minDimension * 0.034f)
                }
            }

            Box(
                modifier = Modifier
                    .offset(x = if (isBoxFlippedToBack) 0.dp else (-14).dp)
                    .size(218.dp)
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "album_${album.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .graphicsLayer {
                        rotationY = boxFlipAngle
                        cameraDistance = 15f * density
                    }
                    .shadow(24.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .clickable(onClick = onCoverClick)
            ) {
                if (boxFlipAngle <= 90f) {
                    AsyncImage(
                        model = remember(mainCoverUri) {
                            ImageRequest.Builder(context)
                                .data(mainCoverUri)
                                .size(720)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(10.dp)
                            .align(Alignment.CenterStart)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.28f),
                                        Color.Black.copy(alpha = 0.18f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f }
                    ) {
                        AlbumBoxBackplateView(
                            album = album,
                            mainCoverUri = mainCoverUri,
                            backCoverItem = backCoverItem,
                            accentColor = accentColor,
                            totalDurationMs = totalDurationMs
                        )
                    }
                }

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isBoxFlippedToBack = !isBoxFlippedToBack
                    },
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .graphicsLayer {
                            if (boxFlipAngle > 90f) rotationY = 180f
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FlipCameraAndroid,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(
                                id = if (isBoxFlippedToBack) AppR.string.album_hero_flip_to_front else AppR.string.album_hero_flip_to_back
                            ),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (artworksCount > 1 && boxFlipAngle <= 90f) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.68f),
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

        if (vinylArtworks.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(vinylArtworks, key = { _, item -> item.uri.toString() }) { idx, _ ->
                    val isSelected = activeVinylIndex == idx
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectVinylDisc(idx) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Album,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(id = AppR.string.vinyl_disc_chip_fmt, idx + 1),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        shape = RoundedCornerShape(50)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (artistAvatarModel != null) {
                        AsyncImage(
                            model = remember(artistAvatarModel) {
                                ImageRequest.Builder(context)
                                    .data(artistAvatarModel)
                                    .size(120)
                                    .crossfade(true)
                                    .build()
                            },
                            contentDescription = album.artist,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
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
                text = formatAlbumTotalDurationComposable(totalDurationMs)
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

        val playInteractionSource = remember { MutableInteractionSource() }
        val isPlayPressed by playInteractionSource.collectIsPressedAsState()
        val playCornerRadius by animateDpAsState(
            targetValue = when {
                isPlayPressed -> 14.dp
                isAlbumPlaying -> 18.dp
                else -> 28.dp
            },
            animationSpec = motionScheme.fastSpatialSpec(),
            label = "playMorphCorner"
        )
        val playButtonScale by animateFloatAsState(
            targetValue = if (isPlayPressed) 0.96f else 1f,
            animationSpec = motionScheme.fastSpatialSpec(),
            label = "playScale"
        )

        val shuffleInteractionSource = remember { MutableInteractionSource() }
        val isShufflePressed by shuffleInteractionSource.collectIsPressedAsState()
        val shuffleCornerRadius by animateDpAsState(
            targetValue = if (isShufflePressed) 14.dp else 26.dp,
            animationSpec = motionScheme.fastSpatialSpec(),
            label = "shuffleMorphCorner"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPlayAlbumClick,
                interactionSource = playInteractionSource,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .scale(playButtonScale),
                shape = RoundedCornerShape(playCornerRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = onAccentColor
                )
            ) {
                AnimatedContent(
                    targetState = isAlbumPlaying,
                    transitionSpec = {
                        (scaleIn(motionScheme.fastSpatialSpec(), initialScale = 0.6f) + fadeIn(motionScheme.fastEffectsSpec()))
                            .togetherWith(scaleOut(motionScheme.fastSpatialSpec(), targetScale = 0.6f) + fadeOut(motionScheme.fastEffectsSpec()))
                    },
                    label = "playPauseIconMorph"
                ) { playing ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                id = if (playing) AppR.string.album_action_pause else AppR.string.album_action_play
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onShuffleAlbumClick,
                interactionSource = shuffleInteractionSource,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(shuffleCornerRadius),
                contentPadding = PaddingValues(horizontal = 22.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = stringResource(id = AppR.string.album_action_shuffle)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = AppR.string.album_action_shuffle),
                    fontWeight = FontWeight.ExtraBold
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
private fun AlbumBoxBackplateView(
    album: AlbumModel,
    mainCoverUri: Uri,
    backCoverItem: AlbumArtworkItem?,
    accentColor: Color,
    totalDurationMs: Long
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        if (backCoverItem != null) {
            AsyncImage(
                model = remember(backCoverItem.uri) {
                    ImageRequest.Builder(context)
                        .data(backCoverItem.uri)
                        .size(720)
                        .crossfade(true)
                        .build()
                },
                contentDescription = backCoverItem.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = remember(mainCoverUri) {
                    ImageRequest.Builder(context)
                        .data(mainCoverUri)
                        .size(300)
                        .crossfade(true)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.22f)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0E0E12).copy(alpha = 0.88f),
                                Color(0xFF14141A).copy(alpha = 0.95f)
                            )
                        )
                    )
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = AppR.string.back_cover_side_a),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "CAT#${abs(album.id % 90000) + 10000}-LP",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 9.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val previewTracks = remember(album.tracks) { album.tracks.take(6) }
                        previewTracks.forEachIndexed { idx, t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${idx + 1}. ${t.title}",
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatTrackTime(t.duration),
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        if (album.tracks.size > 6) {
                            Text(
                                text = "+ ${album.tracks.size - 6}…",
                                color = accentColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = album.artist.uppercase(Locale.ROOT),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatAlbumTotalDurationComposable(totalDurationMs),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 8.sp
                            )
                        }

                        Canvas(
                            modifier = Modifier
                                .width(54.dp)
                                .height(18.dp)
                                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            val bars = 16
                            val step = size.width / bars
                            for (i in 0 until bars) {
                                val w = if ((i + album.id.toInt()) % 3 == 0) step * 0.65f else step * 0.3f
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset(i * step, 0f),
                                    size = androidx.compose.ui.geometry.Size(w, size.height)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveCollectorDesk(
    album: AlbumModel,
    backs: List<AlbumArtworkItem>,
    booklets: List<AlbumArtworkItem>,
    cards: List<AlbumArtworkItem>,
    stickers: List<AlbumArtworkItem>,
    accentColor: Color,
    onOpenInLightbox: (AlbumArtworkItem) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = accentColor
                )
                Column {
                    Text(
                        text = stringResource(id = AppR.string.collector_desk_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = stringResource(id = AppR.string.collector_desk_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (booklets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                var isSpreadMode: Boolean by rememberSaveable { mutableStateOf(booklets.size >= 2) }

                val spreadPages = remember(booklets, isSpreadMode) {
                    if (isSpreadMode && booklets.size >= 2) {
                        booklets.chunked(2)
                    } else {
                        booklets.map { listOf(it) }
                    }
                }

                val bookletPager = rememberPagerState { spreadPages.size }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(id = AppR.string.booklet_reader_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (booklets.size >= 2) {
                            FilterChip(
                                selected = isSpreadMode,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isSpreadMode = !isSpreadMode
                                },
                                label = {
                                    Text(
                                        text = stringResource(
                                            id = if (isSpreadMode) AppR.string.booklet_mode_spread else AppR.string.booklet_mode_single
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                shape = RoundedCornerShape(50)
                            )
                        }

                        Text(
                            text = stringResource(
                                id = AppR.string.booklet_page_indicator_fmt,
                                (bookletPager.currentPage + 1).coerceAtMost(spreadPages.size),
                                spreadPages.size
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                HorizontalPager(
                    state = bookletPager,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    pageSpacing = 14.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isSpreadMode) 196.dp else 240.dp)
                ) { pageIdx ->
                    val pagePair = spreadPages.getOrNull(pageIdx).orEmpty()
                    val pageOffset = (bookletPager.currentPage - pageIdx) + bookletPager.currentPageOffsetFraction

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationY = (-pageOffset * 26f).coerceIn(-32f, 32f)
                                val scale = 1f - (abs(pageOffset) * 0.08f).coerceIn(0f, 0.15f)
                                scaleX = scale
                                scaleY = scale
                                cameraDistance = 16f * density
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                pagePair.forEach { item ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable { onOpenInLightbox(item) }
                                    ) {
                                        AsyncImage(
                                            model = remember(item.uri) {
                                                ImageRequest.Builder(context)
                                                    .data(item.uri)
                                                    .size(800)
                                                    .crossfade(true)
                                                    .build()
                                            },
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            if (pagePair.size == 2) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(24.dp)
                                        .align(Alignment.Center)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.45f),
                                                    Color.White.copy(alpha = 0.12f),
                                                    Color.Black.copy(alpha = 0.45f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }

                            Surface(
                                color = Color.Black.copy(alpha = 0.62f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = stringResource(id = AppR.string.booklet_tap_to_zoom),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (backs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(id = AppR.string.back_cover_box_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = stringResource(id = AppR.string.back_cover_box_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(backs, key = { it.uri.toString() }) { backItem ->
                        Card(
                            onClick = { onOpenInLightbox(backItem) },
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier
                                .width(220.dp)
                                .height(185.dp)
                                .tiltOnTouch()
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(18.dp)
                                        .background(Color(0xFF141418))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = album.title.uppercase(Locale.ROOT),
                                        color = accentColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        modifier = Modifier.graphicsLayer { rotationZ = -90f }
                                    )
                                }

                                AsyncImage(
                                    model = remember(backItem.uri) {
                                        ImageRequest.Builder(context)
                                            .data(backItem.uri)
                                            .size(640)
                                            .crossfade(true)
                                            .build()
                                    },
                                    contentDescription = backItem.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }

            if (cards.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                var isCardFlipped: Boolean by remember { mutableStateOf(false) }
                val flipRotation by animateFloatAsState(
                    targetValue = if (isCardFlipped) 180f else 0f,
                    animationSpec = motionScheme.slowSpatialSpec(),
                    label = "card3dFlip"
                )

                val frontCard = cards.first()
                val backCard = cards.getOrNull(1) ?: frontCard

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Style,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(id = AppR.string.collector_card_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isCardFlipped = !isCardFlipped
                        }
                    ) {
                        Icon(Icons.Rounded.FlipCameraAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(id = AppR.string.collector_card_flip_hint),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Card(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isCardFlipped = !isCardFlipped
                        },
                        shape = RoundedCornerShape(22.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        modifier = Modifier
                            .width(190.dp)
                            .height(250.dp)
                            .graphicsLayer {
                                rotationY = flipRotation
                                cameraDistance = 14f * density
                            }
                    ) {
                        val activeSide = if (flipRotation <= 90f) frontCard else backCard
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (flipRotation > 90f) {
                                        rotationY = 180f
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = remember(activeSide.uri) {
                                    ImageRequest.Builder(context)
                                        .data(activeSide.uri)
                                        .size(640)
                                        .crossfade(true)
                                        .build()
                                },
                                contentDescription = activeSide.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            if (stickers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = stringResource(id = AppR.string.sticker_pack_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(stickers, key = { _, s -> s.uri.toString() }) { idx, sticker ->
                        var isPeeled: Boolean by remember { mutableStateOf(false) }
                        val stickerScale by animateFloatAsState(
                            targetValue = if (isPeeled) 1.14f else 1f,
                            animationSpec = motionScheme.defaultSpatialSpec(),
                            label = "stickerScale"
                        )
                        val stickerTilt = remember(idx) {
                            if (idx % 2 == 0) -5f else 5f
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(110.dp)
                                .graphicsLayer {
                                    scaleX = stickerScale
                                    scaleY = stickerScale
                                    rotationZ = if (isPeeled) 0f else stickerTilt
                                }
                                .shadow(if (isPeeled) 12.dp else 4.dp, RoundedCornerShape(18.dp))
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isPeeled) {
                                        onOpenInLightbox(sticker)
                                    } else {
                                        isPeeled = true
                                    }
                                }
                                .padding(6.dp)
                        ) {
                            AsyncImage(
                                model = remember(sticker.uri) {
                                    ImageRequest.Builder(context)
                                        .data(sticker.uri)
                                        .size(320)
                                        .crossfade(true)
                                        .build()
                                },
                                contentDescription = sticker.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDurationSpectrumBar(
    tracks: List<AudioTrack>,
    currentTrackId: Long?,
    accentColor: Color,
    onTrackClick: (AudioTrack) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tracks.forEachIndexed { index, track ->
                val weight = track.duration.coerceAtLeast(15_000L).toFloat()
                val isCurrent = track.id == currentTrackId
                val segmentAlpha = if (isCurrent) 1f else (0.28f + (index % 3) * 0.1f)

                Box(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (isCurrent) accentColor
                            else accentColor.copy(alpha = segmentAlpha)
                        )
                        .clickable { onTrackClick(track) }
                )
            }
        }
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
    val motionScheme = MaterialTheme.motionScheme
    val isTracksActive = activeSection == 0
    val isGalleryActive = activeSection == 1

    val tracksWeight by animateFloatAsState(
        targetValue = if (isTracksActive) 1.15f else 0.85f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "tracksTabWeight"
    )
    val galleryWeight by animateFloatAsState(
        targetValue = if (isGalleryActive) 1.15f else 0.85f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "galleryTabWeight"
    )
    val tracksCorner by animateDpAsState(
        targetValue = if (isTracksActive) 18.dp else 12.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "tracksCorner"
    )
    val galleryCorner by animateDpAsState(
        targetValue = if (isGalleryActive) 18.dp else 12.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "galleryCorner"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                onClick = { onSectionChange(0) },
                shape = RoundedCornerShape(tracksCorner),
                color = if (isTracksActive) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (isTracksActive) 2.dp else 0.dp,
                modifier = Modifier.weight(tracksWeight)
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
                        fontWeight = if (isTracksActive) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isTracksActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                onClick = { onSectionChange(1) },
                shape = RoundedCornerShape(galleryCorner),
                color = if (isGalleryActive) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (isGalleryActive) 2.dp else 0.dp,
                modifier = Modifier.weight(galleryWeight)
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
                        fontWeight = if (isGalleryActive) FontWeight.ExtraBold else FontWeight.Medium,
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
    discDurationMs: Long,
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
                text = "• ${pluralStringResource(id = AppR.plurals.tracks_count, count = trackCount, trackCount)} • ${formatAlbumTotalDurationComposable(discDurationMs)}",
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
    vinylDiscNumber: Int?,
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
    val motionScheme = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val itemCorner by animateDpAsState(
        targetValue = when {
            isPressed -> 24.dp
            isCurrentTrack -> 20.dp
            else -> 14.dp
        },
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "trackCorner"
    )

    val containerColor = if (isCurrentTrack) {
        accentColor.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.background
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(itemCorner),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrentTrack) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isCurrentTrack) accentColor else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (vinylDiscNumber != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accentColor.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Album,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = stringResource(id = AppR.string.vinyl_badge_short_fmt, vinylDiscNumber),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accentColor
                                )
                            }
                        }
                    }
                }

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
    val bar1: Float
    val bar2: Float
    val bar3: Float

    if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "bars")
        val b1 by infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "b1"
        )
        val b2 by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "b2"
        )
        val b3 by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "b3"
        )
        bar1 = b1
        bar2 = b2
        bar3 = b3
    } else {
        bar1 = 0.28f
        bar2 = 0.48f
        bar3 = 0.34f
    }

    Canvas(modifier = Modifier.size(18.dp)) {
        val barWidth = size.width / 5f
        val maxH = size.height
        val fractions = floatArrayOf(bar1, bar2, bar3)
        for (idx in fractions.indices) {
            val fraction = fractions[idx]
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
private fun AlbumGalleryHeader(
    allArtworks: List<AlbumArtworkItem>,
    isFetchingOnline: Boolean,
    selectedCategory: ArtworkCategory?,
    accentColor: Color,
    onSelectCategory: (ArtworkCategory?) -> Unit,
    onFetchOnlineClick: () -> Unit
) {
    val availableCategories = remember(allArtworks) {
        allArtworks.map { it.category }.distinct().sortedBy { it.ordinal }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = AppR.string.album_gallery_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(id = AppR.string.album_gallery_assign_role_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onFetchOnlineClick,
                enabled = !isFetchingOnline,
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (isFetchingOnline) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = AppR.string.action_searching_short),
                        style = MaterialTheme.typography.labelMedium
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Collections,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = AppR.string.action_fetch_discogs),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (availableCategories.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
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
        }
    }
}

@Composable
private fun AlbumGalleryEmptyState() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
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

@Composable
private fun AlbumGalleryCard(
    item: AlbumArtworkItem,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onCategoryChange: (ArtworkCategory) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var showCategoryMenu by remember { mutableStateOf(false) }
    val remoteTagStr = stringResource(id = AppR.string.artwork_remote_source_tag)

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
                    model = remember(item.uri) {
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .size(512)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Surface(
                        onClick = { showCategoryMenu = true },
                        shape = RoundedCornerShape(10.dp),
                        color = if (item.category == ArtworkCategory.DISC) {
                            accentColor.copy(alpha = 0.92f)
                        } else {
                            Color.Black.copy(alpha = 0.72f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (item.category == ArtworkCategory.DISC) {
                                Icon(
                                    imageVector = Icons.Rounded.Album,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = artworkCategoryLabel(item.category),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (item.category == ArtworkCategory.DISC) Color.Black else Color.White
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        ArtworkCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = artworkCategoryLabel(category),
                                        fontWeight = if (item.category == category) FontWeight.ExtraBold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (category == ArtworkCategory.DISC) Icons.Rounded.Album else Icons.Rounded.Collections,
                                        contentDescription = null,
                                        tint = if (item.category == category) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showCategoryMenu = false
                                    onCategoryChange(category)
                                }
                            )
                        }
                    }
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
                val subInfo = remember(item.resolution, item.sizeBytes, item.isRemoteFetched, remoteTagStr) {
                    buildString {
                        if (item.isRemoteFetched) append("$remoteTagStr • ")
                        if (item.resolution.isNotEmpty()) append(item.resolution)
                        if (item.sizeBytes > 0L) {
                            if (isNotEmpty() && !endsWith("• ")) append(" • ")
                            append(Formatter.formatShortFileSize(context, item.sizeBytes))
                        }
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
    tracksCount: Int,
    techMetadata: String,
    artworksCount: Int,
    folderPath: String?,
    accentColor: Color,
    onOpenGallery: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val avgLabel = stringResource(id = AppR.string.album_specs_avg_suffix)

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
                value = "${formatAlbumTotalDurationComposable(totalDurationMs)} (${formatTrackTime(totalDurationMs / tracksCount.coerceAtLeast(1))} $avgLabel)"
            )
            SpecRowItem(
                label = stringResource(id = AppR.string.album_specs_artwork_files),
                value = artworksCount.toString()
            )
            if (!folderPath.isNullOrBlank()) {
                val folderName = remember(folderPath) {
                    File(folderPath).name.ifEmpty { folderPath }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Album Folder Path", folderPath))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast.makeText(context, folderPath, Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = AppR.string.album_specs_source_folder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.45f)
                    )
                    Row(
                        modifier = Modifier.weight(0.55f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (artworksCount > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenGallery,
                    shapes = ButtonDefaults.shapes(),
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
    accentColor: Color,
    onCategoryChange: (AlbumArtworkItem, ArtworkCategory) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialIndex) { artworks.size }
    val currentItem = artworks.getOrNull(pagerState.currentPage)
    var showLightboxCatMenu by remember { mutableStateOf(false) }
    val remoteTagStr = stringResource(id = AppR.string.artwork_remote_source_tag)

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
            var containerSize by remember(page) { mutableStateOf(IntSize.Zero) }

            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != page) {
                    scale = 1f
                    offset = Offset.Zero
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it }
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
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val pointerCount = event.changes.size

                                if (scale > 1.01f || pointerCount > 1) {
                                    val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                                    val maxX = ((containerSize.width * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                                    val maxY = ((containerSize.height * (newScale - 1f)) / 2f).coerceAtLeast(0f)

                                    scale = newScale
                                    offset = if (newScale > 1.01f) {
                                        Offset(
                                            x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                            y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                                        )
                                    } else {
                                        Offset.Zero
                                    }

                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) {
                                            change.consume()
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = remember(item.uri) {
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .crossfade(true)
                            .build()
                    },
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
                shapes = IconButtonDefaults.shapes(),
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
                shapes = IconButtonDefaults.shapes(),
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
                            if (item.isRemoteFetched) append("$remoteTagStr • ")
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

                    Spacer(modifier = Modifier.width(12.dp))

                    Box {
                        FilledTonalButton(
                            onClick = { showLightboxCatMenu = true },
                            shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = accentColor,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Rounded.Album, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = artworkCategoryLabel(item.category),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        DropdownMenu(
                            expanded = showLightboxCatMenu,
                            onDismissRequest = { showLightboxCatMenu = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            ArtworkCategory.entries.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = artworkCategoryLabel(cat),
                                            fontWeight = if (item.category == cat) FontWeight.ExtraBold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        showLightboxCatMenu = false
                                        onCategoryChange(item, cat)
                                    }
                                )
                            }
                        }
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
        ArtworkCategory.STICKER -> stringResource(id = AppR.string.artwork_type_sticker)
        ArtworkCategory.CARD -> stringResource(id = AppR.string.artwork_type_card)
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

@Composable
private fun formatAlbumTotalDurationComposable(durationMs: Long): String {
    val totalMinutes = (durationMs / 60000).coerceAtLeast(1)
    val hours = (totalMinutes / 60).toInt()
    val mins = (totalMinutes % 60).toInt()
    return if (hours > 0) {
        stringResource(id = AppR.string.duration_hours_mins_fmt, hours, mins)
    } else {
        stringResource(id = AppR.string.duration_mins_fmt, mins)
    }
}