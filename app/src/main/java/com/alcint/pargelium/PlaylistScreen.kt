@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.alcint.pargelium

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlaylistScreen(
    allTracks: List<AudioTrack>,
    seedColor: Color,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Гарантируем инициализацию SQLite базы плейлистов!
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            PlaylistDatabase.init(context)
        }
    }

    val useDynamicColors = remember { PrefsManager.getPlaylistDynamicColors() }
    val showCovers = remember { PrefsManager.getPlaylistTrackCovers() }
    val useAnimations = remember { PrefsManager.getPlaylistAnimations() }
    val themeMode = remember { PrefsManager.getThemeMode() }
    val systemDark = isSystemInDarkTheme()

    val trackMap = remember(allTracks) { allTracks.associateBy { it.id } }

    var playlists by remember {
        PlaylistDatabase.init(context)
        mutableStateOf(PlaylistDatabase.getPlaylists())
    }
    var playlistSearchQuery by remember { mutableStateOf("") }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    // M3U / M3U8 Менеджер
    var showM3uBrowserSheet by remember { mutableStateOf(false) }
    var discoveredM3uList by remember { mutableStateOf<List<DiscoveredM3uPlaylist>>(emptyList()) }
    var isScanningM3u by remember { mutableStateOf(false) }
    var includeAlbumM3uInScan by remember { mutableStateOf(false) }

    val selectedPlaylist = remember(selectedPlaylistId, playlists) {
        playlists.find { it.id == selectedPlaylistId }
    }

    var showAddTracksSheet by remember { mutableStateOf(false) }
    var trackSearchQuery by remember { mutableStateOf("") }
    var detailTrackFilter by remember { mutableStateOf("") }

    fun refresh() {
        playlists = PlaylistDatabase.getPlaylists(forceRefresh = true)
    }

    BackHandler(enabled = selectedPlaylistId != null) {
        selectedPlaylistId = null
        detailTrackFilter = ""
    }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null && selectedPlaylist != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}

                val updated = selectedPlaylist.copy(coverUri = uri.toString())
                PlaylistDatabase.savePlaylist(updated)
                refresh()
            }
        }
    )

    // Ручной выбор любого .m3u / .m3u8 файла через системный файловый менеджер
    val m3uFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    val (title, matchedIds) = AudioRepository.parseM3uFromUri(context, uri, allTracks)
                    if (matchedIds.isNotEmpty()) {
                        val imported = PlaylistModel(
                            name = title,
                            trackIds = matchedIds
                        )
                        PlaylistDatabase.savePlaylist(imported)
                        refresh()
                        selectedPlaylistId = imported.id
                        Toast.makeText(
                            context,
                            "Импортировано: $title (${matchedIds.size} треков)",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Не удалось сопоставить треки из .m3u8 с медиатекой",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    )

    val displayedPlaylists = remember(playlists, playlistSearchQuery) {
        if (playlistSearchQuery.isBlank()) playlists
        else playlists.filter { it.name.contains(playlistSearchQuery, ignoreCase = true) }
    }

    val safeColor = if (useDynamicColors && seedColor != Color.Unspecified) seedColor else Color(0xFFD0BCFF)
    val localScheme = rememberPargeliumScheme(safeColor, themeMode, systemDark)

    MaterialTheme(colorScheme = localScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = selectedPlaylist != null,
                label = "PlaylistTransition",
                transitionSpec = {
                    if (useAnimations) {
                        (fadeIn(animationSpec = tween(260)) + slideInHorizontally { it / 5 })
                            .togetherWith(fadeOut(animationSpec = tween(200)))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                }
            ) { isDetailView ->
                if (!isDetailView) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

                        // Верхняя панель с заголовком и быстрыми действиями M3U8 / Создания
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.nav_playlists),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${playlists.size} коллекций • Поддержка .M3U8",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalIconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showM3uBrowserSheet = true
                                    },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Icon(Icons.Rounded.FolderSpecial, contentDescription = "M3U8 Hub")
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showCreateDialog = true
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(id = R.string.action_create),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 250.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // 1. Панель Smart Mixes и Импорта .M3U8
                            item(key = "smart_mix_and_m3u_bar") {
                                SmartMixAndM3uDeck(
                                    allTracks = allTracks,
                                    onPlaySmartMix = { mixName, tracks ->
                                        if (tracks.isNotEmpty()) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onPlayTrack(tracks.first(), tracks)
                                            Toast.makeText(context, "Запущен микс: $mixName", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onOpenM3uFilePicker = {
                                        m3uFilePicker.launch(
                                            arrayOf(
                                                "audio/x-mpegurl",
                                                "application/vnd.apple.mpegurl",
                                                "application/x-mpegurl",
                                                "audio/mpegurl",
                                                "*/*"
                                            )
                                        )
                                    },
                                    onOpenM3uScanner = { showM3uBrowserSheet = true }
                                )
                            }

                            if (playlists.size > 3) {
                                item(key = "playlist_search_bar") {
                                    OutlinedTextField(
                                        value = playlistSearchQuery,
                                        onValueChange = { playlistSearchQuery = it },
                                        placeholder = { Text(stringResource(id = R.string.search_hint)) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        trailingIcon = {
                                            if (playlistSearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { playlistSearchQuery = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = null)
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(22.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp)
                                    )
                                }
                            }

                            if (displayedPlaylists.isEmpty()) {
                                item(key = "empty_playlists_card") {
                                    Surface(
                                        onClick = { showCreateDialog = true },
                                        shape = RoundedCornerShape(26.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(32.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Text(
                                                text = stringResource(id = R.string.new_playlist),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = stringResource(id = R.string.create_your_collection),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = displayedPlaylists,
                                    key = { it.id },
                                    contentType = { "playlist_card" }
                                ) { playlist ->
                                    val resolvedTracks = remember(playlist.trackIds, trackMap) {
                                        playlist.trackIds.mapNotNull { trackMap[it] }
                                    }
                                    val totalDurationMs = remember(resolvedTracks) {
                                        resolvedTracks.sumOf { it.duration }
                                    }
                                    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
                                    val dateStr = remember(playlist.createdAt) {
                                        dateFormatter.format(Date(playlist.createdAt))
                                    }

                                    ExpressivePlaylistCard(
                                        playlist = playlist,
                                        resolvedTracks = resolvedTracks,
                                        totalDurationMs = totalDurationMs,
                                        dateStr = dateStr,
                                        onClick = { selectedPlaylistId = playlist.id },
                                        onQuickPlay = {
                                            if (resolvedTracks.isNotEmpty()) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onPlayTrack(resolvedTracks.first(), resolvedTracks)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else if (selectedPlaylist != null) {
                    val playlistTracks = remember(selectedPlaylist.trackIds, trackMap) {
                        selectedPlaylist.trackIds.mapNotNull { trackMap[it] }
                    }
                    val filteredPlaylistTracks = remember(playlistTracks, detailTrackFilter) {
                        if (detailTrackFilter.isBlank()) playlistTracks
                        else playlistTracks.filter {
                            it.title.contains(detailTrackFilter, ignoreCase = true) ||
                                    it.artist.contains(detailTrackFilter, ignoreCase = true)
                        }
                    }
                    val totalPlaylistDurationMs = remember(playlistTracks) {
                        playlistTracks.sumOf { it.duration }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    selectedPlaylistId = null
                                    detailTrackFilter = ""
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Кнопка переименования плейлиста
                                FilledTonalIconButton(
                                    onClick = {
                                        renameInput = selectedPlaylist.name
                                        showRenameDialog = true
                                    }
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Rename")
                                }

                                // Кнопка экспорта плейлиста в .M3U8 файл
                                FilledTonalIconButton(
                                    onClick = {
                                        if (playlistTracks.isNotEmpty()) {
                                            scope.launch {
                                                val exported = AudioRepository.exportPlaylistToM3u8(
                                                    context = context,
                                                    playlistName = selectedPlaylist.name,
                                                    tracks = playlistTracks
                                                )
                                                if (exported != null) {
                                                    Toast.makeText(
                                                        context,
                                                        "Экспортировано в: ${exported.absolutePath}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.IosShare, contentDescription = "Export M3U8")
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        PlaylistDatabase.deletePlaylist(selectedPlaylist.id)
                                        selectedPlaylistId = null
                                        refresh()
                                    },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 250.dp)
                        ) {
                            item(key = "playlist_detail_hero") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(184.dp)
                                            .tiltOnTouch()
                                            .shadow(20.dp, RoundedCornerShape(28.dp))
                                            .clip(RoundedCornerShape(28.dp))
                                            .clickable {
                                                coverPicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            }
                                    ) {
                                        PlaylistCoverCollage(
                                            customCoverUri = selectedPlaylist.coverUri,
                                            tracks = playlistTracks,
                                            size = 184.dp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Text(
                                        text = selectedPlaylist.name,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                        ) {
                                            Text(
                                                text = pluralStringResource(
                                                    id = R.plurals.tracks_count,
                                                    count = playlistTracks.size,
                                                    playlistTracks.size
                                                ),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }

                                        if (totalPlaylistDurationMs > 0L) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Schedule,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = formatPlaylistTotalTime(totalPlaylistDurationMs),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                if (playlistTracks.isNotEmpty()) {
                                                    onPlayTrack(playlistTracks.first(), playlistTracks)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(54.dp)
                                                .tiltOnTouch(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(id = R.string.action_play),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        FilledTonalIconButton(
                                            onClick = {
                                                if (playlistTracks.isNotEmpty()) {
                                                    val shuffled = playlistTracks.shuffled()
                                                    onPlayTrack(shuffled.first(), shuffled)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(54.dp)
                                                .tiltOnTouch()
                                        ) {
                                            Icon(Icons.Default.Shuffle, contentDescription = null)
                                        }

                                        FilledTonalIconButton(
                                            onClick = { showAddTracksSheet = true },
                                            modifier = Modifier
                                                .size(54.dp)
                                                .tiltOnTouch()
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                        }
                                    }

                                    if (playlistTracks.size > 5) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        OutlinedTextField(
                                            value = detailTrackFilter,
                                            onValueChange = { detailTrackFilter = it },
                                            placeholder = { Text(stringResource(id = R.string.search_track_hint)) },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                            trailingIcon = {
                                                if (detailTrackFilter.isNotEmpty()) {
                                                    IconButton(onClick = { detailTrackFilter = "" }) {
                                                        Icon(Icons.Default.Close, contentDescription = null)
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(18.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            if (filteredPlaylistTracks.isEmpty()) {
                                item(key = "empty_playlist_msg") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.playlist_empty_msg),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    items = filteredPlaylistTracks,
                                    key = { _, t -> t.id },
                                    contentType = { _, _ -> "playlist_track_item" }
                                ) { index, track ->
                                    val context = LocalContext.current
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                text = track.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        supportingContent = {
                                            Text(
                                                text = track.artist,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingContent = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${index + 1}",
                                                    modifier = Modifier.width(26.dp),
                                                    textAlign = TextAlign.Center,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                if (showCovers) {
                                                    val artUri = remember(track.albumId) {
                                                        AudioRepository.getAlbumArtUri(track.albumId)
                                                    }
                                                    AsyncImage(
                                                        model = remember(artUri) {
                                                            ImageRequest.Builder(context)
                                                                .data(artUri)
                                                                .size(144)
                                                                .crossfade(true)
                                                                .build()
                                                        },
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    )
                                                }
                                            }
                                        },
                                        trailingContent = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (detailTrackFilter.isBlank() && playlistTracks.size > 1) {
                                                    IconButton(
                                                        onClick = {
                                                            if (index > 0) {
                                                                PlaylistDatabase.moveTrackInPlaylist(
                                                                    selectedPlaylist.id,
                                                                    index,
                                                                    index - 1
                                                                )
                                                                refresh()
                                                            }
                                                        },
                                                        enabled = index > 0,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.KeyboardArrowUp,
                                                            contentDescription = "Move Up",
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            if (index < playlistTracks.lastIndex) {
                                                                PlaylistDatabase.moveTrackInPlaylist(
                                                                    selectedPlaylist.id,
                                                                    index,
                                                                    index + 1
                                                                )
                                                                refresh()
                                                            }
                                                        },
                                                        enabled = index < playlistTracks.lastIndex,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                                            contentDescription = "Move Down",
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        PlaylistDatabase.removeTrackFromPlaylist(
                                                            selectedPlaylist.id,
                                                            track.id
                                                        )
                                                        refresh()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 2.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onPlayTrack(track, playlistTracks) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(id = R.string.new_playlist), fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(id = R.string.playlist_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            val created = PlaylistModel(name = newPlaylistName.trim(), trackIds = emptyList())
                            PlaylistDatabase.savePlaylist(created)
                            newPlaylistName = ""
                            refresh()
                            showCreateDialog = false
                            selectedPlaylistId = created.id
                        }
                    }
                ) {
                    Text(stringResource(id = R.string.action_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

    if (showRenameDialog && selectedPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Переименовать плейлист", fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text(stringResource(id = R.string.playlist_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            PlaylistDatabase.savePlaylist(selectedPlaylist.copy(name = renameInput.trim()))
                            refresh()
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text(stringResource(id = R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

    // Модальное окно умного поиска и выборочного импорта .M3U8 / .M3U файлов
    if (showM3uBrowserSheet) {
        LaunchedEffect(showM3uBrowserSheet, includeAlbumM3uInScan) {
            isScanningM3u = true
            discoveredM3uList = AudioRepository.scanDeviceM3uPlaylists(
                context = context,
                allTracks = allTracks,
                includeSingleAlbumM3u = includeAlbumM3uInScan
            )
            isScanningM3u = false
        }

        ModalBottomSheet(
            onDismissRequest = { showM3uBrowserSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.85f)
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Менеджер .M3U / .M3U8",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Выбери файл вручную или импортируй найденные списки",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            showM3uBrowserSheet = false
                            m3uFilePicker.launch(
                                arrayOf(
                                    "audio/x-mpegurl",
                                    "application/vnd.apple.mpegurl",
                                    "application/x-mpegurl",
                                    "*/*"
                                )
                            )
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Файл...", fontWeight = FontWeight.Bold)
                    }
                }

                // Переключатель защиты от засорения плейлистов одноальбомными .m3u8
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Скрывать одноальбомные .M3U8",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Не засорять список файлами .m3u8, которые просто дублируют один альбом",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = !includeAlbumM3uInScan,
                            onCheckedChange = { hideSingleAlbums ->
                                includeAlbumM3uInScan = !hideSingleAlbums
                            }
                        )
                    }
                }

                if (isScanningM3u) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (discoveredM3uList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (!includeAlbumM3uInScan) {
                                    "Сборных .M3U8 не найдено (одноальбомные скрыты фильтром)"
                                } else {
                                    "Файлы .M3U / .M3U8 не найдены в папках музыки"
                                },
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(discoveredM3uList, key = { it.filePath }) { item ->
                            val alreadyImported = remember(playlists, item.name) {
                                playlists.any { it.name.equals(item.name, ignoreCase = true) }
                            }
                            ListItem(
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item.coverAlbumId > 0L) {
                                            AsyncImage(
                                                model = AudioRepository.getAlbumArtUri(item.coverAlbumId),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(Icons.Rounded.Album, contentDescription = null)
                                        }
                                    }
                                },
                                headlineContent = {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    val badge = if (item.isSingleAlbumDump) "Альбом (${item.dominantAlbumTitle})" else "Папка: ${item.folderName}"
                                    Text(
                                        text = "${item.trackIds.size} треков • $badge",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingContent = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                val tracksToPlay = item.trackIds.mapNotNull { trackMap[it] }
                                                if (tracksToPlay.isNotEmpty()) {
                                                    showM3uBrowserSheet = false
                                                    onPlayTrack(tracksToPlay.first(), tracksToPlay)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                                contentDescription = "Play Immediately",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        FilledTonalButton(
                                            onClick = {
                                                val model = PlaylistModel(
                                                    name = item.name,
                                                    trackIds = item.trackIds
                                                )
                                                PlaylistDatabase.savePlaylist(model)
                                                refresh()
                                                Toast.makeText(context, "Добавлен: ${item.name}", Toast.LENGTH_SHORT).show()
                                            },
                                            enabled = !alreadyImported,
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(if (alreadyImported) "Добавлен" else "Импорт")
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddTracksSheet && selectedPlaylist != null) {
        val inPlaylistSet = remember(selectedPlaylist.trackIds) {
            selectedPlaylist.trackIds.toHashSet()
        }
        val filteredTracks = remember(trackSearchQuery, allTracks) {
            if (trackSearchQuery.isBlank()) {
                allTracks
            } else {
                allTracks.filter {
                    it.title.contains(trackSearchQuery, ignoreCase = true) ||
                            it.artist.contains(trackSearchQuery, ignoreCase = true) ||
                            it.album.contains(trackSearchQuery, ignoreCase = true)
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showAddTracksSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.85f)
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.add_tracks_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )

                    if (trackSearchQuery.isNotBlank() && filteredTracks.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                PlaylistDatabase.addTracksToPlaylist(
                                    selectedPlaylist.id,
                                    filteredTracks.map { it.id }
                                )
                                refresh()
                            }
                        ) {
                            Text("Добавить все (${filteredTracks.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = trackSearchQuery,
                    onValueChange = { trackSearchQuery = it },
                    placeholder = { Text(stringResource(id = R.string.search_track_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (trackSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { trackSearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(
                        items = filteredTracks,
                        key = { it.id },
                        contentType = { "add_track_row" }
                    ) { track ->
                        val inPlaylist = inPlaylistSet.contains(track.id)

                        ListItem(
                            headlineContent = {
                                Text(
                                    text = track.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "${track.artist} • ${track.album}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                if (showCovers) {
                                    val artUri = remember(track.albumId) {
                                        AudioRepository.getAlbumArtUri(track.albumId)
                                    }
                                    AsyncImage(
                                        model = remember(artUri) {
                                            ImageRequest.Builder(context)
                                                .data(artUri)
                                                .size(144)
                                                .crossfade(true)
                                                .build()
                                        },
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                if (inPlaylist) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            PlaylistDatabase.addTrackToPlaylist(selectedPlaylist.id, track.id)
                                            refresh()
                                        }
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                if (inPlaylist) {
                                    PlaylistDatabase.removeTrackFromPlaylist(selectedPlaylist.id, track.id)
                                } else {
                                    PlaylistDatabase.addTrackToPlaylist(selectedPlaylist.id, track.id)
                                }
                                refresh()
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Уникальная витрина быстрых авто-миксов (Smart Mixes) и импорта .M3U8
 */
@Composable
private fun SmartMixAndM3uDeck(
    allTracks: List<AudioTrack>,
    onPlaySmartMix: (String, List<AudioTrack>) -> Unit,
    onOpenM3uFilePicker: () -> Unit,
    onOpenM3uScanner: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "pick_m3u_chip") {
            SmartMixActionCard(
                icon = Icons.Rounded.FileOpen,
                title = "Открыть .M3U8",
                subtitle = "Выбрать файл / папку",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onOpenM3uFilePicker
            )
        }

        item(key = "scan_m3u_chip") {
            SmartMixActionCard(
                icon = Icons.Rounded.FolderSpecial,
                title = "Сканер M3U8",
                subtitle = "Умный фильтр альбомов",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onOpenM3uScanner
            )
        }

        if (allTracks.isNotEmpty()) {
            item(key = "smart_lucky_25") {
                SmartMixActionCard(
                    icon = Icons.Rounded.Casino,
                    title = "Быстрый Микс 25",
                    subtitle = "Случайная волна",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = {
                        onPlaySmartMix("Быстрый Микс 25", allTracks.shuffled().take(25))
                    }
                )
            }

            item(key = "smart_heavy_rotation") {
                SmartMixActionCard(
                    icon = Icons.Rounded.LocalFireDepartment,
                    title = "Топ Ротация",
                    subtitle = "Твои главные хиты",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        val events = PrefsManager.getPlayEvents()
                        val counts = events.groupingBy { it.trackId }.eachCount()
                        val top = allTracks
                            .sortedByDescending { counts[it.id] ?: 0 }
                            .take(30)
                        onPlaySmartMix("Топ Ротация", top)
                    }
                )
            }

            item(key = "smart_night_flow") {
                SmartMixActionCard(
                    icon = Icons.Rounded.NightlightRound,
                    title = "Длинный Формат",
                    subtitle = "Глубокое погружение",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        val longTracks = allTracks
                            .filter { it.duration >= 210_000L }
                            .shuffled()
                            .take(25)
                            .ifEmpty { allTracks.shuffled().take(20) }
                        onPlaySmartMix("Длинный Формат", longTracks)
                    }
                )
            }
        }
    }
}

@Composable
private fun SmartMixActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.width(172.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpressivePlaylistCard(
    playlist: PlaylistModel,
    resolvedTracks: List<AudioTrack>,
    totalDurationMs: Long,
    dateStr: String,
    onClick: () -> Unit,
    onQuickPlay: () -> Unit
) {
    val tracksText = pluralStringResource(
        id = R.plurals.tracks_count,
        count = resolvedTracks.size,
        resolvedTracks.size
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistCoverCollage(
                customCoverUri = playlist.coverUri,
                tracks = resolvedTracks,
                size = 64.dp
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(tracksText)
                        if (totalDurationMs > 0L) {
                            append(" • ")
                            append(formatPlaylistTotalTime(totalDurationMs))
                        }
                        append(" • ")
                        append(dateStr)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (resolvedTracks.isNotEmpty()) {
                FilledTonalIconButton(
                    onClick = onQuickPlay,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Автоматический коллаж 2x2 из обложек альбомов треков плейлиста,
 * если пользователь не задал кастомную обложку.
 */
@Composable
private fun PlaylistCoverCollage(
    customCoverUri: String?,
    tracks: List<AudioTrack>,
    size: Dp
) {
    val context = LocalContext.current
    val distinctAlbumIds = remember(tracks) {
        tracks.map { it.albumId }.filter { it > 0L }.distinct().take(4)
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            !customCoverUri.isNullOrBlank() -> {
                AsyncImage(
                    model = remember(customCoverUri) {
                        ImageRequest.Builder(context)
                            .data(customCoverUri)
                            .size(400)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            distinctAlbumIds.size >= 4 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        CollageCell(albumId = distinctAlbumIds[0], modifier = Modifier.weight(1f))
                        CollageCell(albumId = distinctAlbumIds[1], modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        CollageCell(albumId = distinctAlbumIds[2], modifier = Modifier.weight(1f))
                        CollageCell(albumId = distinctAlbumIds[3], modifier = Modifier.weight(1f))
                    }
                }
            }
            distinctAlbumIds.isNotEmpty() -> {
                CollageCell(albumId = distinctAlbumIds.first(), modifier = Modifier.fillMaxSize())
            }
            else -> {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.42f)
                )
            }
        }
    }
}

@Composable
private fun CollageCell(
    albumId: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uri = remember(albumId) { AudioRepository.getAlbumArtUri(albumId) }
    AsyncImage(
        model = remember(uri) {
            ImageRequest.Builder(context)
                .data(uri)
                .size(240)
                .crossfade(true)
                .build()
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}

private fun formatPlaylistTotalTime(durationMs: Long): String {
    val totalMinutes = (durationMs / 60000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val mins = totalMinutes % 60L
    return if (hours > 0L) "${hours}ч ${mins}м" else "${mins}м"
}