@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.alcint.pargelium.library

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alcint.pargelium.*
import com.alcint.pargelium.R
import com.alcint.pargelium.liquid.FloatingBottomBar
import com.alcint.pargelium.liquid.FloatingBottomBarItem
import top.yukonga.miuix.kmp.blur.LayerBackdrop

@Composable
fun LibraryBottomNav(
    pagerState: PagerState,
    navItems: List<NavItem>,
    backdrop: LayerBackdrop,
    onTabSelected: (Int) -> Unit
) {
    FloatingBottomBar(
        selectedIndex = pagerState.currentPage,
        onSelected = onTabSelected,
        backdrop = backdrop,
        tabsCount = navItems.size
    ) { activateTab ->
        navItems.forEachIndexed { index, item ->
            val isSelected = pagerState.currentPage == index
            FloatingBottomBarItem(
                selected = isSelected,
                onClick = { activateTab(index) },
                // Этот паддинг аккуратно расширяет вкладки под пальцы
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = stringResource(id = item.labelRes),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(id = item.labelRes),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun LibraryTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchOpen: () -> Unit,
    userName: String,
    avatarUri: String?,
    onProfileClick: () -> Unit,
    libraryTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    AnimatedContent(
        targetState = isSearchActive,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) using SizeTransform(clip = false) },
        label = "SearchHeaderAnimation"
    ) { active ->
        if (active) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearchClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground) }
                OutlinedTextField(
                    value = searchQuery, onValueChange = onSearchQueryChange, placeholder = { Text(stringResource(id = R.string.search_hint)) }, modifier = Modifier.weight(1f).height(56.dp), singleLine = true, shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
                    trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearchQueryChange("") }) { Icon(Icons.Default.Close, null) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )
            }
        } else {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(32.dp))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onProfileClick)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            if (avatarUri != null) AsyncImage(model = ImageRequest.Builder(context).data(avatarUri).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(text = userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onSearchOpen) { Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.onBackground) }
                }
                TabRow(selectedTabIndex = libraryTab, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary, divider = {}) {
                    Tab(selected = libraryTab == 0, onClick = { onTabSelected(0) }) {
                        Text(stringResource(id = R.string.tab_albums), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                    }
                    Tab(selected = libraryTab == 1, onClick = { onTabSelected(1) }) {
                        Text(stringResource(id = R.string.tab_artists), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SharedTransitionScope.LibrarySearchResults(
    filteredArtists: List<ArtistModel>,
    filteredAlbums: List<AlbumModel>,
    filteredTracks: List<AudioTrack>,
    isLoading: Boolean,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onArtistClick: (ArtistModel) -> Unit,
    onAlbumClick: (AlbumModel) -> Unit,
    onTrackClick: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onShare: (AudioTrack) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    if (filteredAlbums.isEmpty() && filteredTracks.isEmpty() && filteredArtists.isEmpty() && !isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(id = R.string.search_no_results)) }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 240.dp)) {
            if (filteredArtists.isNotEmpty()) {
                item { Text(stringResource(id = R.string.tab_artists), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filteredArtists, key = { it.name }) { artist -> Box(modifier = Modifier.width(160.dp)) { ArtistCard(artist) { onArtistClick(artist) } } }
                    }
                }
            }
            if (filteredAlbums.isNotEmpty()) {
                item { Text(stringResource(id = R.string.tab_albums), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filteredAlbums, key = { it.id }) { alb -> Box(modifier = Modifier.width(160.dp)) { AlbumCard(alb, animatedVisibilityScope) { onAlbumClick(alb) } } }
                    }
                }
            }
            if (filteredTracks.isNotEmpty()) {
                item { Text(stringResource(id = R.string.tab_tracks), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                items(filteredTracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track, isPlaying = isPlaying, isCurrentTrack = currentTrack?.id == track.id,
                        albumArt = remember(track.albumId) { AudioRepository.getAlbumArtUri(track.albumId) },
                        onClick = { onTrackClick(track, filteredTracks) }, onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) }, onAddToPlaylist = { onAddToPlaylist(track) }, onShare = { onShare(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun SharedTransitionScope.LibraryMainGrids(
    libraryTab: Int,
    isLoading: Boolean,
    filteredAlbums: List<AlbumModel>,
    filteredArtists: List<ArtistModel>,
    albumGridState: LazyGridState,
    artistGridState: LazyGridState,
    onAlbumClick: (AlbumModel) -> Unit,
    onArtistClick: (ArtistModel) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    if (libraryTab == 0) {
        if (filteredAlbums.isEmpty() && !isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.music_not_found)) }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    state = albumGridState,
                    contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(filteredAlbums, key = { it.id }) { alb -> AlbumCard(alb, animatedVisibilityScope) { onAlbumClick(alb) } }
                }

                if (filteredAlbums.isNotEmpty()) {
                    Box(modifier = Modifier.align(Alignment.CenterEnd).padding(bottom = 240.dp, top = 8.dp)) {
                        FastScrollThumb(
                            itemCount = filteredAlbums.size,
                            state = albumGridState,
                            getLetter = { index -> filteredAlbums.getOrNull(index)?.title?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "" }
                        )
                    }
                }
            }
        }
    } else {
        if (filteredArtists.isEmpty() && !isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(id = R.string.no_artists)) }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    state = artistGridState,
                    contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(filteredArtists, key = { it.name }) { artist -> ArtistCard(artist) { onArtistClick(artist) } }
                }

                if (filteredArtists.isNotEmpty()) {
                    Box(modifier = Modifier.align(Alignment.CenterEnd).padding(bottom = 240.dp, top = 8.dp)) {
                        FastScrollThumb(
                            itemCount = filteredArtists.size,
                            state = artistGridState,
                            getLetter = { index -> filteredArtists.getOrNull(index)?.name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "" }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SharedTransitionScope.LibraryDefaultView(
    isRefreshing: Boolean,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchOpen: () -> Unit,
    userName: String,
    avatarUri: String?,
    onProfileClick: () -> Unit,
    libraryTab: Int,
    onTabSelected: (Int) -> Unit,
    filteredAlbums: List<AlbumModel>,
    filteredArtists: List<ArtistModel>,
    filteredTracks: List<AudioTrack>,
    albumGridState: LazyGridState,
    artistGridState: LazyGridState,
    onAlbumClick: (AlbumModel) -> Unit,
    onArtistClick: (ArtistModel) -> Unit,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onTrackClick: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onShare: (AudioTrack) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val focusManager = LocalFocusManager.current

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            LibraryTopBar(
                isSearchActive = isSearchActive, searchQuery = searchQuery, onSearchQueryChange = onSearchQueryChange,
                onSearchClose = onSearchClose, onSearchOpen = onSearchOpen, userName = userName, avatarUri = avatarUri,
                onProfileClick = onProfileClick, libraryTab = libraryTab, onTabSelected = onTabSelected
            )

            if (isSearchActive && searchQuery.isNotEmpty()) {
                LibrarySearchResults(
                    filteredArtists = filteredArtists, filteredAlbums = filteredAlbums, filteredTracks = filteredTracks,
                    isLoading = isLoading, currentTrack = currentTrack, isPlaying = isPlaying, onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick, onTrackClick = onTrackClick, onPlayNext = onPlayNext, onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist, onShare = onShare, animatedVisibilityScope = animatedVisibilityScope
                )
            } else {
                LibraryMainGrids(
                    libraryTab = libraryTab, isLoading = isLoading, filteredAlbums = filteredAlbums, filteredArtists = filteredArtists,
                    albumGridState = albumGridState, artistGridState = artistGridState, onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick, animatedVisibilityScope = animatedVisibilityScope
                )
            }
        }
    }
}