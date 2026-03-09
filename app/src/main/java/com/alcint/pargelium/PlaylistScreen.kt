package com.alcint.pargelium

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    allTracks: List<AudioTrack>,
    seedColor: Color,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    val useDynamicColors = PrefsManager.getPlaylistDynamicColors()
    val showBanners = PrefsManager.getPlaylistBanners()
    val showCovers = PrefsManager.getPlaylistTrackCovers()
    val useAnimations = PrefsManager.getPlaylistAnimations()

    var playlists by remember { mutableStateOf(PrefsManager.getPlaylists()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<PlaylistModel?>(null) }

    var showAddTracksSheet by remember { mutableStateOf(false) }
    var trackSearchQuery by remember { mutableStateOf("") }

    // Выбор обложки с вечным доступом
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null && selectedPlaylist != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) { e.printStackTrace() }

                val updated = selectedPlaylist!!.copy(coverUri = uri.toString())
                PrefsManager.savePlaylist(updated)
                selectedPlaylist = updated
                playlists = PrefsManager.getPlaylists()
            }
        }
    )

    // Выбор баннера с вечным доступом
    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null && selectedPlaylist != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) { e.printStackTrace() }

                val updated = selectedPlaylist!!.copy(bannerUri = uri.toString())
                PrefsManager.savePlaylist(updated)
                selectedPlaylist = updated
                playlists = PrefsManager.getPlaylists()
            }
        }
    )

    fun refresh() {
        playlists = PrefsManager.getPlaylists()
        if (selectedPlaylist != null) {
            selectedPlaylist = playlists.find { it.id == selectedPlaylist!!.id }
        }
    }

    // Обработка динамических цветов
    val themeMode = PrefsManager.getThemeMode()
    val systemDark = isSystemInDarkTheme()
    val safeColor = if (useDynamicColors) seedColor else Color(0xFFD0BCFF)
    val localScheme = rememberPargeliumScheme(safeColor, themeMode, systemDark)

    MaterialTheme(colorScheme = localScheme) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

            AnimatedContent(
                targetState = selectedPlaylist,
                label = "Playlist Transition",
                transitionSpec = {
                    if (useAnimations) {
                        (fadeIn(animationSpec = tween(300)) + slideInHorizontally { it / 4 }) togetherWith fadeOut(animationSpec = tween(300))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                }
            ) { currentPlaylist ->
                if (currentPlaylist == null) {

                    // ==========================================
                    // Главный экран
                    // ==========================================
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

                        Text(
                            text = stringResource(id = R.string.nav_playlists),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )

                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 250.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Кнопка создания нового плейлиста
                            item {
                                Card(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tiltOnTouch() // 3D-Эффект нажатия
                                        .clip(RoundedCornerShape(24.dp))
                                        .clickable { showCreateDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                text = stringResource(id = R.string.new_playlist),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = stringResource(id = R.string.create_your_collection),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }

                            items(playlists, key = { it.id }) { playlist ->
                                PlaylistKsuCard(
                                    playlist = playlist,
                                    showBanners = showBanners,
                                    onClick = { selectedPlaylist = playlist }
                                )
                            }
                        }
                    }
                } else {

                    // ==========================================
                    // Экран внутри плейлиста
                    // ==========================================
                    val playlistTracks = currentPlaylist.trackIds.mapNotNull { id -> allTracks.find { it.id == id } }

                    Box(modifier = Modifier.fillMaxSize()) {

                        // Основной скроллируемый контент
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 250.dp)
                        ) {

                            // 1. ШАПКА (Баннер, Обложка, Название)
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(340.dp)
                                ) {
                                    val imgModel = currentPlaylist.bannerUri ?: currentPlaylist.coverUri

                                    if (showBanners && imgModel != null) {
                                        AsyncImage(
                                            model = imgModel,
                                            contentDescription = stringResource(id = R.string.cd_banner),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            Text(
                                                stringResource(id = R.string.click_to_add_banner),
                                                modifier = Modifier.padding(top = 100.dp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }

                                    // Градиент
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    0.0f to Color.Black.copy(alpha = 0.2f),
                                                    0.4f to Color.Transparent,
                                                    0.8f to MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                                    1.0f to MaterialTheme.colorScheme.background
                                                )
                                            )
                                    )

                                    // Обложка и Тексты
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(horizontal = 24.dp)
                                            .padding(bottom = 8.dp)
                                    ) {
                                        if (currentPlaylist.coverUri != null) {
                                            AsyncImage(
                                                model = currentPlaylist.coverUri,
                                                contentDescription = stringResource(id = R.string.cd_cover),
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(130.dp)
                                                    .tiltOnTouch()
                                                    .clip(RoundedCornerShape(24.dp))
                                                    .clickable { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(130.dp)
                                                    .tiltOnTouch()
                                                    .clip(RoundedCornerShape(24.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                                    .clickable { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Image,
                                                    null,
                                                    modifier = Modifier.size(56.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            text = currentPlaylist.name,
                                            style = MaterialTheme.typography.displaySmall,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = pluralStringResource(id = R.plurals.tracks_count, count = playlistTracks.size, playlistTracks.size),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // 2. ПАНЕЛЬ УПРАВЛЕНИЯ
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Массивная кнопка Play
                                    Button(
                                        onClick = { if (playlistTracks.isNotEmpty()) onPlayTrack(playlistTracks.first(), playlistTracks) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(64.dp)
                                            .tiltOnTouch(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp),
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Слушать",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }

                                    // Кнопка Shuffle
                                    IconButton(
                                        onClick = {
                                            if (playlistTracks.isNotEmpty()) {
                                                val shuffled = playlistTracks.shuffled()
                                                onPlayTrack(shuffled.first(), shuffled)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(64.dp)
                                            .tiltOnTouch()
                                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    ) {
                                        Icon(Icons.Default.Shuffle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                                    }

                                    // Кнопка Добавить треки
                                    IconButton(
                                        onClick = { showAddTracksSheet = true },
                                        modifier = Modifier
                                            .size(64.dp)
                                            .tiltOnTouch()
                                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    ) {
                                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                                    }

                                    // Кнопка Удалить плейлист
                                    IconButton(
                                        onClick = {
                                            PrefsManager.deletePlaylist(currentPlaylist.id)
                                            selectedPlaylist = null
                                            refresh()
                                        },
                                        modifier = Modifier
                                            .size(64.dp)
                                            .tiltOnTouch()
                                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }

                            // 3. СПИСОК ТРЕКОВ
                            if (playlistTracks.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            stringResource(id = R.string.playlist_empty_msg),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            items(playlistTracks, key = { it.id }) { track ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = track.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = track.artist,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                        )
                                    },
                                    leadingContent = {
                                        if (showCovers) {
                                            val artUri = remember(track.id) { AudioRepository.getAlbumArtUri(track.albumId) }
                                            AsyncImage(
                                                model = artUri,
                                                contentDescription = stringResource(id = R.string.cd_track_cover),
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { PrefsManager.removeTrackFromPlaylist(currentPlaylist.id, track.id); refresh() }) {
                                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                        .tiltOnTouch()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { onPlayTrack(track, playlistTracks) }
                                )
                            }
                        }

                        IconButton(
                            onClick = { selectedPlaylist = null },
                            modifier = Modifier
                                .padding(top = 48.dp, start = 16.dp)
                                .background(Color.Black.copy(0.4f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(id = R.string.new_playlist), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(id = R.string.playlist_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        PrefsManager.savePlaylist(PlaylistModel(name = newPlaylistName, trackIds = emptyList()))
                        newPlaylistName = ""
                        refresh()
                        showCreateDialog = false
                    }
                }) { Text(stringResource(id = R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(id = R.string.action_cancel)) } }
        )
    }

    if (showAddTracksSheet && selectedPlaylist != null) {
        val filteredTracks = allTracks.filter { it.title.contains(trackSearchQuery, ignoreCase = true) || it.artist.contains(trackSearchQuery, ignoreCase = true) }

        ModalBottomSheet(onDismissRequest = { showAddTracksSheet = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxHeight(0.85f).padding(bottom = 16.dp)) {
                Text(stringResource(id = R.string.add_tracks_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                OutlinedTextField(value = trackSearchQuery, onValueChange = { trackSearchQuery = it }, placeholder = { Text(stringResource(id = R.string.search_track_hint)) }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(24.dp), singleLine = true)

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredTracks) { track ->
                        val inPlaylist = selectedPlaylist!!.trackIds.contains(track.id)
                        ListItem(
                            headlineContent = { Text(track.title, maxLines = 1, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(track.artist, maxLines = 1) },
                            leadingContent = {
                                if (showCovers) {
                                    val artUri = remember(track.id) { AudioRepository.getAlbumArtUri(track.albumId) }
                                    AsyncImage(
                                        model = artUri,
                                        contentDescription = stringResource(id = R.string.cd_track_cover),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                } else {
                                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            trailingContent = {
                                if (inPlaylist) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                else IconButton(onClick = { PrefsManager.addTrackToPlaylist(selectedPlaylist!!.id, track.id); refresh() }) { Icon(Icons.Default.Add, null) }
                            },
                            modifier = Modifier.clickable { if (!inPlaylist) { PrefsManager.addTrackToPlaylist(selectedPlaylist!!.id, track.id); refresh() } }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Карточка плейлиста
// ==========================================
@Composable
fun PlaylistKsuCard(
    playlist: PlaylistModel,
    showBanners: Boolean,
    onClick: () -> Unit
) {
    val imgModel = playlist.bannerUri ?: playlist.coverUri
    val hasBanner = showBanners && imgModel != null

    val textColor = if (hasBanner) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (hasBanner) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .tiltOnTouch()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // 1. Слой с картинкой
            if (hasBanner) {
                AsyncImage(
                    model = imgModel,
                    contentDescription = stringResource(id = R.string.cd_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Плавный градиент для того, чтобы текст внизу легко читался
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.4f to Color.Black.copy(alpha = 0.2f),
                                1.0f to Color.Black.copy(alpha = 0.85f)
                            )
                        )
                )
            }

            // 2. Слой с контентом
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Обложка плейлиста
                if (playlist.coverUri != null) {
                    AsyncImage(
                        model = playlist.coverUri,
                        contentDescription = stringResource(id = R.string.cd_cover),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (hasBanner) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = if (hasBanner) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Текстовая информация
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val dateStr = remember(playlist.createdAt) {
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(playlist.createdAt))
                    }
                    val tracksText = pluralStringResource(
                        id = R.plurals.tracks_count,
                        count = playlist.trackIds.size,
                        playlist.trackIds.size
                    )

                    Text(
                        text = "$tracksText • $dateStr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}