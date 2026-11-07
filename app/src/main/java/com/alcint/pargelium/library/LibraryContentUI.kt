@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.alcint.pargelium.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alcint.pargelium.AlbumCard
import com.alcint.pargelium.AlbumModel
import com.alcint.pargelium.AudioRepository
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.R
import com.alcint.pargelium.SortOption
import com.alcint.pargelium.TrackRow
import com.alcint.pargelium.liquid.FloatingBottomBar
import com.alcint.pargelium.liquid.FloatingBottomBarItem
import com.alcint.pargelium.material.MaterialBottomNavStyle
import com.alcint.pargelium.material.PillBottomNavStyle
import top.yukonga.miuix.kmp.blur.LayerBackdrop

@Composable
fun LibraryBottomNav(
    pagerState: PagerState,
    navItems: List<NavItem>,
    backdrop: LayerBackdrop,
    navBarStyle: Int = 0,
    onTabSelected: (Int) -> Unit
) {
    when (navBarStyle) {
        0 -> LiquidBottomNavStyle(pagerState, navItems, backdrop, onTabSelected)
        1 -> MaterialBottomNavStyle(pagerState, navItems, onTabSelected)
        2 -> PillBottomNavStyle(pagerState, navItems, onTabSelected)
        else -> LiquidBottomNavStyle(pagerState, navItems, backdrop, onTabSelected)
    }
}

@Composable
fun LiquidBottomNavStyle(
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
    onTabSelected: (Int) -> Unit,
    albumsCount: Int = 0,
    artistsCount: Int = 0,
    tracksCount: Int = 0,
    gridDensity: GridDensity = GridDensity.COZY,
    onToggleGridDensity: () -> Unit = {},
    sortOption: SortOption = SortOption.TITLE,
    onSortOptionChange: (SortOption) -> Unit = {},
    showBentoSpotlight: Boolean = true,
    onToggleBentoSpotlight: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180)) using SizeTransform(clip = false)
            },
            label = "ExpressiveHeaderState"
        ) { active ->
            if (active) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onSearchClose,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_back)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(stringResource(id = R.string.search_hint)) },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(26.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        onClick = onProfileClick,
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarUri != null) {
                                    AsyncImage(
                                        model = remember(avatarUri) {
                                            ImageRequest.Builder(context)
                                                .data(avatarUri)
                                                .size(160)
                                                .crossfade(true)
                                                .build()
                                        },
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = userName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(id = R.string.library_header_stats_fmt, albumsCount, tracksCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleBentoSpotlight()
                            },
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (showBentoSpotlight) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = stringResource(id = R.string.cd_sonic_deck),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (libraryTab != 2) {
                            FilledTonalIconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleGridDensity()
                                },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Icon(
                                    imageVector = when (gridDensity) {
                                        GridDensity.COMPACT -> Icons.Rounded.ViewComfy
                                        GridDensity.COZY -> Icons.Rounded.GridView
                                        GridDensity.SPACIOUS -> Icons.Rounded.ViewModule
                                    },
                                    contentDescription = stringResource(id = R.string.cd_grid_density),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Box {
                            FilledTonalIconButton(
                                onClick = { sortMenuExpanded = true },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                                    contentDescription = stringResource(id = R.string.cd_sort),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                SortOption.entries.forEach { option ->
                                    val optionLabel = when (option) {
                                        SortOption.TITLE -> stringResource(id = R.string.sort_by_title)
                                        SortOption.ARTIST -> stringResource(id = R.string.sort_by_artist)
                                        SortOption.DURATION -> stringResource(id = R.string.sort_by_duration)
                                    }
                                    DropdownMenuItem(
                                        text = { Text(optionLabel, fontWeight = if (sortOption == option) FontWeight.ExtraBold else FontWeight.Medium) },
                                        trailingIcon = {
                                            if (sortOption == option) {
                                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        onClick = {
                                            onSortOptionChange(option)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        FilledTonalIconButton(
                            onClick = onSearchOpen,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(id = R.string.search_hint),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!isSearchActive) {
            Spacer(modifier = Modifier.height(10.dp))
            ExpressiveLibraryPillTabs(
                selectedTab = libraryTab,
                albumsCount = albumsCount,
                artistsCount = artistsCount,
                tracksCount = tracksCount,
                onTabSelected = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTabSelected(it)
                }
            )
        }
    }
}

@Composable
private fun ExpressiveLibraryPillTabs(
    selectedTab: Int,
    albumsCount: Int,
    artistsCount: Int,
    tracksCount: Int,
    onTabSelected: (Int) -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val w0 by animateFloatAsState(if (selectedTab == 0) 1.2f else 0.9f, motionScheme.defaultSpatialSpec(), label = "w0")
    val w1 by animateFloatAsState(if (selectedTab == 1) 1.2f else 0.9f, motionScheme.defaultSpatialSpec(), label = "w1")
    val w2 by animateFloatAsState(if (selectedTab == 2) 1.2f else 0.9f, motionScheme.defaultSpatialSpec(), label = "w2")

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ExpressiveTabPill(
                selected = selectedTab == 0,
                icon = Icons.Rounded.Album,
                label = stringResource(id = R.string.tab_albums),
                badgeCount = albumsCount,
                onClick = { onTabSelected(0) },
                modifier = Modifier.weight(w0)
            )
            ExpressiveTabPill(
                selected = selectedTab == 1,
                icon = Icons.Rounded.People,
                label = stringResource(id = R.string.tab_artists),
                badgeCount = artistsCount,
                onClick = { onTabSelected(1) },
                modifier = Modifier.weight(w1)
            )
            ExpressiveTabPill(
                selected = selectedTab == 2,
                icon = Icons.Rounded.MusicNote,
                label = stringResource(id = R.string.tab_tracks),
                badgeCount = tracksCount,
                onClick = { onTabSelected(2) },
                modifier = Modifier.weight(w2)
            )
        }
    }
}

@Composable
private fun ExpressiveTabPill(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motionScheme = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerRadius by animateDpAsState(
        targetValue = when {
            isPressed -> 12.dp
            selected -> 20.dp
            else -> 14.dp
        },
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "tabCorner"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "tabBg"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(cornerRadius),
        color = bgColor,
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected && badgeCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$badgeCount",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SonicAuraBentoDeck(
    albums: List<AlbumModel>,
    totalTracksCount: Int,
    onAlbumClick: (AlbumModel) -> Unit,
    onMysteryAlbumPlay: (AlbumModel) -> Unit
) {
    if (albums.isEmpty()) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bentoScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "bentoScale"
    )
    val bentoCorner by animateDpAsState(
        targetValue = if (isPressed) 32.dp else 26.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "bentoCorner"
    )

    // Фиксируем витрину по количеству альбомов, чтобы при смене сортировки она не вызывала лишних рекомпозиций
    val spotlightAlbums = remember(albums.size) {
        if (albums.size <= 8) albums else albums.shuffled().take(8)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onMysteryAlbumPlay(albums.random())
            },
            interactionSource = interactionSource,
            shape = RoundedCornerShape(bentoCorner),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .scale(bentoScale)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Casino,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.bento_mystery_vinyl_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(id = R.string.album_action_shuffle),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(id = R.string.bento_mystery_vinyl_sub_fmt, albums.size, totalTracksCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        }
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = spotlightAlbums,
                key = { "spot_${it.id}" },
                contentType = { "spotlight_chip" }
            ) { album ->
                val coverUri = remember(album.id) { AudioRepository.getAlbumArtUri(album.id) }
                Surface(
                    onClick = { onAlbumClick(album) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.width(196.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = remember(coverUri) {
                                ImageRequest.Builder(context)
                                    .data(coverUri)
                                    .size(160)
                                    .crossfade(false)
                                    .build()
                            },
                            contentDescription = album.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = album.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(id = R.string.search_no_results),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 240.dp)
        ) {
            if (filteredArtists.isNotEmpty()) {
                item(key = "search_artists_header", contentType = "header") {
                    Text(
                        text = "${stringResource(id = R.string.tab_artists)} (${filteredArtists.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                item(key = "search_artists_row", contentType = "row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredArtists, key = { it.name }, contentType = { "artist_card" }) { artist ->
                            Box(modifier = Modifier.width(156.dp)) {
                                ArtistCard(artist) { onArtistClick(artist) }
                            }
                        }
                    }
                }
            }

            if (filteredAlbums.isNotEmpty()) {
                item(key = "search_albums_header", contentType = "header") {
                    Text(
                        text = "${stringResource(id = R.string.tab_albums)} (${filteredAlbums.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                item(key = "search_albums_row", contentType = "row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredAlbums, key = { it.id }, contentType = { "album_card" }) { alb ->
                            Box(modifier = Modifier.width(160.dp)) {
                                AlbumCard(alb, animatedVisibilityScope) { onAlbumClick(alb) }
                            }
                        }
                    }
                }
            }

            if (filteredTracks.isNotEmpty()) {
                item(key = "search_tracks_header", contentType = "header") {
                    Text(
                        text = "${stringResource(id = R.string.tab_tracks)} (${filteredTracks.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(
                    items = filteredTracks,
                    key = { it.id },
                    contentType = { "track_row" }
                ) { track ->
                    TrackRow(
                        track = track,
                        isPlaying = isPlaying,
                        isCurrentTrack = currentTrack?.id == track.id,
                        albumArt = remember(track.albumId) { AudioRepository.getAlbumArtUri(track.albumId) },
                        onClick = { onTrackClick(track, filteredTracks) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onShare = { onShare(track) }
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
    allTracks: List<AudioTrack>,
    albumGridState: LazyGridState,
    artistGridState: LazyGridState,
    trackListState: LazyListState,
    gridDensity: GridDensity,
    showBentoSpotlight: Boolean,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onAlbumClick: (AlbumModel) -> Unit,
    onMysteryAlbumPlay: (AlbumModel) -> Unit,
    onArtistClick: (ArtistModel) -> Unit,
    onTrackClick: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onShare: (AudioTrack) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val cellSize = gridDensity.minCellSizeDp.dp

    when (libraryTab) {
        0 -> {
            if (filteredAlbums.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.music_not_found))
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = cellSize),
                        state = albumGridState,
                        contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (showBentoSpotlight && filteredAlbums.size > 2) {
                            item(
                                key = "sonic_bento_deck",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "bento_deck"
                            ) {
                                SonicAuraBentoDeck(
                                    albums = filteredAlbums,
                                    totalTracksCount = allTracks.size,
                                    onAlbumClick = onAlbumClick,
                                    onMysteryAlbumPlay = onMysteryAlbumPlay
                                )
                            }
                        }

                        gridItems(
                            items = filteredAlbums,
                            key = { it.id },
                            contentType = { "album_grid_item" }
                        ) { alb ->
                            AlbumCard(alb, animatedVisibilityScope) { onAlbumClick(alb) }
                        }
                    }

                    if (filteredAlbums.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(bottom = 240.dp, top = 8.dp)
                        ) {
                            FastScrollThumb(
                                itemCount = filteredAlbums.size,
                                state = albumGridState,
                                getLetter = { index ->
                                    filteredAlbums.getOrNull(index)?.title?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: ""
                                }
                            )
                        }
                    }
                }
            }
        }
        1 -> {
            if (filteredArtists.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(id = R.string.no_artists))
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = cellSize),
                        state = artistGridState,
                        contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItems(
                            items = filteredArtists,
                            key = { it.name },
                            contentType = { "artist_grid_item" }
                        ) { artist ->
                            ArtistCard(artist = artist) { onArtistClick(artist) }
                        }
                    }

                    if (filteredArtists.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(bottom = 240.dp, top = 8.dp)
                        ) {
                            FastScrollThumb(
                                itemCount = filteredArtists.size,
                                state = artistGridState,
                                getLetter = { index ->
                                    filteredArtists.getOrNull(index)?.name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: ""
                                }
                            )
                        }
                    }
                }
            }
        }
        else -> {
            if (allTracks.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(id = R.string.music_not_found))
                }
            } else {
                LazyColumn(
                    state = trackListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 240.dp)
                ) {
                    items(
                        items = allTracks,
                        key = { it.id },
                        contentType = { "track_row" }
                    ) { track ->
                        TrackRow(
                            track = track,
                            isPlaying = isPlaying,
                            isCurrentTrack = currentTrack?.id == track.id,
                            albumArt = remember(track.albumId) { AudioRepository.getAlbumArtUri(track.albumId) },
                            onClick = { onTrackClick(track, allTracks) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onShare = { onShare(track) }
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
    allTracks: List<AudioTrack> = emptyList(),
    sortOption: SortOption = SortOption.TITLE,
    onSortOptionChange: (SortOption) -> Unit = {},
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
    val trackListState = rememberLazyListState()
    var gridDensity by rememberSaveable { mutableStateOf(GridDensity.COZY) }
    var showBentoSpotlight by rememberSaveable { mutableStateOf(true) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            LibraryTopBar(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onSearchClose = onSearchClose,
                onSearchOpen = onSearchOpen,
                userName = userName,
                avatarUri = avatarUri,
                onProfileClick = onProfileClick,
                libraryTab = libraryTab,
                onTabSelected = onTabSelected,
                albumsCount = filteredAlbums.size,
                artistsCount = filteredArtists.size,
                tracksCount = allTracks.size,
                gridDensity = gridDensity,
                onToggleGridDensity = {
                    gridDensity = when (gridDensity) {
                        GridDensity.COZY -> GridDensity.COMPACT
                        GridDensity.COMPACT -> GridDensity.SPACIOUS
                        GridDensity.SPACIOUS -> GridDensity.COZY
                    }
                },
                sortOption = sortOption,
                onSortOptionChange = onSortOptionChange,
                showBentoSpotlight = showBentoSpotlight,
                onToggleBentoSpotlight = { showBentoSpotlight = !showBentoSpotlight }
            )

            if (isSearchActive && searchQuery.isNotEmpty()) {
                LibrarySearchResults(
                    filteredArtists = filteredArtists,
                    filteredAlbums = filteredAlbums,
                    filteredTracks = filteredTracks,
                    isLoading = isLoading,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick,
                    onTrackClick = onTrackClick,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShare = onShare,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            } else {
                LibraryMainGrids(
                    libraryTab = libraryTab,
                    isLoading = isLoading,
                    filteredAlbums = filteredAlbums,
                    filteredArtists = filteredArtists,
                    allTracks = allTracks,
                    albumGridState = albumGridState,
                    artistGridState = artistGridState,
                    trackListState = trackListState,
                    gridDensity = gridDensity,
                    showBentoSpotlight = showBentoSpotlight,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    onAlbumClick = onAlbumClick,
                    onMysteryAlbumPlay = { randomAlbum ->
                        if (randomAlbum.tracks.isNotEmpty()) {
                            onTrackClick(randomAlbum.tracks.first(), randomAlbum.tracks)
                        } else {
                            onAlbumClick(randomAlbum)
                        }
                    },
                    onArtistClick = onArtistClick,
                    onTrackClick = onTrackClick,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShare = onShare,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        }
    }
}