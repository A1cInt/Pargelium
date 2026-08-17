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
import androidx.compose.foundation.lazy.itemsIndexed
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

    val useDynamicColors = remember { PrefsManager.getPlaylistDynamicColors() }
    val showCovers = remember { PrefsManager.getPlaylistTrackCovers() }
    val useAnimations = remember { PrefsManager.getPlaylistAnimations() }
    val themeMode = remember { PrefsManager.getThemeMode() }
    val systemDark = isSystemInDarkTheme()

    val trackMap = remember(allTracks) { allTracks.associateBy { it.id } }

    var playlists by remember { mutableStateOf(PlaylistDatabase.getPlaylists()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }

    val selectedPlaylist = remember(selectedPlaylistId, playlists) {
        playlists.find { it.id == selectedPlaylistId }
    }

    var showAddTracksSheet by remember { mutableStateOf(false) }
    var trackSearchQuery by remember { mutableStateOf("") }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null && selectedPlaylist != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}

                val updated = selectedPlaylist.copy(coverUri = uri.toString())
                PlaylistDatabase.savePlaylist(updated)
                playlists = PlaylistDatabase.getPlaylists()
            }
        }
    )

    fun refresh() {
        playlists = PlaylistDatabase.getPlaylists()
    }

    val safeColor = if (useDynamicColors) seedColor else Color(0xFFD0BCFF)
    val localScheme = rememberPargeliumScheme(safeColor, themeMode, systemDark)

    MaterialTheme(colorScheme = localScheme) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

            AnimatedContent(
                targetState = selectedPlaylist != null,
                label = "Playlist Transition",
                transitionSpec = {
                    if (useAnimations) {
                        (fadeIn(animationSpec = tween(300)) + slideInHorizontally { it / 4 }) togetherWith fadeOut(animationSpec = tween(300))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                }
            ) { isDetailView ->
                if (!isDetailView) {
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
                            contentPadding = PaddingValues(bottom = 250.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            stringResource(id = R.string.new_playlist),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    supportingContent = {
                                        Text(stringResource(id = R.string.create_your_collection))
                                    },
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { showCreateDialog = true },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }

                            items(playlists, key = { it.id }) { playlist ->
                                val tracksText = pluralStringResource(id = R.plurals.tracks_count, count = playlist.trackIds.size, playlist.trackIds.size)
                                val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
                                val dateStr = remember(playlist.createdAt) { dateFormatter.format(Date(playlist.createdAt)) }

                                ListItem(
                                    headlineContent = {
                                        Text(
                                            playlist.name,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    supportingContent = {
                                        Text("$tracksText • $dateStr", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    leadingContent = {
                                        if (playlist.coverUri != null) {
                                            AsyncImage(
                                                model = playlist.coverUri,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { selectedPlaylistId = playlist.id },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                } else if (selectedPlaylist != null) {
                    val playlistTracks = remember(selectedPlaylist.trackIds, trackMap) {
                        selectedPlaylist.trackIds.mapNotNull { trackMap[it] }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { selectedPlaylistId = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 250.dp)
                        ) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (selectedPlaylist.coverUri != null) {
                                        AsyncImage(
                                            model = selectedPlaylist.coverUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(160.dp)
                                                .tiltOnTouch()
                                                .clip(RoundedCornerShape(24.dp))
                                                .clickable { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(160.dp)
                                                .tiltOnTouch()
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Image, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Text(
                                        text = selectedPlaylist.name,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        textAlign = TextAlign.Center
                                    )

                                    Text(
                                        text = pluralStringResource(id = R.plurals.tracks_count, count = playlistTracks.size, playlistTracks.size),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { if (playlistTracks.isNotEmpty()) onPlayTrack(playlistTracks.first(), playlistTracks) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(56.dp)
                                                .tiltOnTouch(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(id = R.string.action_play), fontWeight = FontWeight.Bold)
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        IconButton(
                                            onClick = {
                                                if (playlistTracks.isNotEmpty()) {
                                                    val shuffled = playlistTracks.shuffled()
                                                    onPlayTrack(shuffled.first(), shuffled)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(56.dp)
                                                .tiltOnTouch()
                                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                        ) {
                                            Icon(Icons.Default.Shuffle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        IconButton(
                                            onClick = { showAddTracksSheet = true },
                                            modifier = Modifier
                                                .size(56.dp)
                                                .tiltOnTouch()
                                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                        ) {
                                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        IconButton(
                                            onClick = {
                                                PlaylistDatabase.deletePlaylist(selectedPlaylist.id)
                                                selectedPlaylistId = null
                                                refresh()
                                            },
                                            modifier = Modifier
                                                .size(56.dp)
                                                .tiltOnTouch()
                                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }

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

                            itemsIndexed(playlistTracks, key = { _, t -> t.id }) { index, track ->
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
                                                modifier = Modifier.width(24.dp),
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            if (showCovers) {
                                                val artUri = remember(track.albumId) { AudioRepository.getAlbumArtUri(track.albumId) }
                                                AsyncImage(
                                                    model = artUri,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                )
                                            }
                                        }
                                    },
                                    trailingContent = {
                                        IconButton(onClick = {
                                            PlaylistDatabase.removeTrackFromPlaylist(selectedPlaylist.id, track.id)
                                            refresh()
                                        }) {
                                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
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
                        PlaylistDatabase.savePlaylist(PlaylistModel(name = newPlaylistName, trackIds = emptyList()))
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
        val filteredTracks = remember(trackSearchQuery, allTracks) {
            if (trackSearchQuery.isBlank()) {
                allTracks
            } else {
                allTracks.filter {
                    it.title.contains(trackSearchQuery, ignoreCase = true) ||
                            it.artist.contains(trackSearchQuery, ignoreCase = true)
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showAddTracksSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxHeight(0.85f).padding(bottom = 16.dp)) {
                Text(
                    stringResource(id = R.string.add_tracks_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                OutlinedTextField(
                    value = trackSearchQuery,
                    onValueChange = { trackSearchQuery = it },
                    placeholder = { Text(stringResource(id = R.string.search_track_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredTracks, key = { it.id }) { track ->
                        val inPlaylist = remember(selectedPlaylist.trackIds, track.id) {
                            selectedPlaylist.trackIds.contains(track.id)
                        }

                        ListItem(
                            headlineContent = { Text(track.title, maxLines = 1, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(track.artist, maxLines = 1) },
                            leadingContent = {
                                if (showCovers) {
                                    val artUri = remember(track.albumId) { AudioRepository.getAlbumArtUri(track.albumId) }
                                    AsyncImage(
                                        model = artUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            trailingContent = {
                                if (inPlaylist) {
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    IconButton(onClick = {
                                        PlaylistDatabase.addTrackToPlaylist(selectedPlaylist.id, track.id)
                                        refresh()
                                    }) { Icon(Icons.Default.Add, null) }
                                }
                            },
                            modifier = Modifier.clickable(enabled = !inPlaylist) {
                                PlaylistDatabase.addTrackToPlaylist(selectedPlaylist.id, track.id)
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