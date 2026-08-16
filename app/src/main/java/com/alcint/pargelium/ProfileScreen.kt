package com.alcint.pargelium

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    allTracks: List<AudioTrack>,
    seedColor: Color,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var userName by remember { mutableStateOf(PrefsManager.getUserName()) }
    var avatarUri by remember { mutableStateOf(PrefsManager.getAvatarUri()) }
    var bannerUri by remember { mutableStateOf(PrefsManager.getBannerUri()) }

    var isEditingName by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(userName) }
    var showRanksSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(e: Exception){}
            PrefsManager.saveAvatarUri(uri.toString())
            avatarUri = uri.toString()
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(e: Exception){}
            PrefsManager.saveBannerUri(uri.toString())
            bannerUri = uri.toString()
        }
    }

    val allEvents = remember { PrefsManager.getPlayEvents() }
    val totalPlays = allEvents.size
    val unknownArtistStr = stringResource(id = R.string.unknown_artist)

    var topTracksDetails by remember { mutableStateOf<List<Pair<AudioTrack, Int>>>(emptyList()) }
    var topArtists by remember { mutableStateOf<List<Map.Entry<String, Int>>>(emptyList()) }
    var topArtistName by remember { mutableStateOf(unknownArtistStr) }

    LaunchedEffect(allEvents, allTracks) {
        withContext(Dispatchers.Default) {
            val trackPlays = allEvents.groupingBy { it.trackId }.eachCount()
            topTracksDetails = trackPlays.entries.mapNotNull { entry ->
                val track = allTracks.find { it.id == entry.key }
                if (track != null) Pair(track, entry.value) else null
            }.sortedByDescending { it.second }.take(50)

            val artistPlays = mutableMapOf<String, Int>()
            for (event in allEvents) {
                val track = allTracks.find { it.id == event.trackId }
                if (track != null) {
                    val artist = getPrimaryArtist(track.artist, unknownArtistStr)
                    artistPlays[artist] = (artistPlays[artist] ?: 0) + 1
                }
            }
            topArtists = artistPlays.entries.sortedByDescending { it.value }.take(10)
            topArtistName = topArtists.firstOrNull()?.key ?: unknownArtistStr
        }
    }

    val ranksList = listOf(
        0 to stringResource(id = R.string.rank_novice),
        10 to stringResource(id = R.string.rank_listener),
        50 to stringResource(id = R.string.rank_melomane),
        200 to stringResource(id = R.string.rank_audiophile),
        1000 to stringResource(id = R.string.rank_music_maniac)
    )
    val userRank = ranksList.lastOrNull { totalPlays >= it.first }?.second ?: ""
    val nextRank = ranksList.firstOrNull { totalPlays < it.first }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 240.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .graphicsLayer {
                            if (listState.firstVisibleItemIndex == 0) {
                                translationY = listState.firstVisibleItemScrollOffset * 0.4f
                            }
                        }
                ) {
                    if (bannerUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(bannerUri).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clickable { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.4f),
                                0.3f to Color.Transparent,
                                0.6f to MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                1.0f to MaterialTheme.colorScheme.background
                            )
                        )
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-80).dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        ) {
                            if (avatarUri != null) {
                                AsyncImage(model = ImageRequest.Builder(context).data(avatarUri).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center).size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            if (isEditingName) {
                                OutlinedTextField(
                                    value = nameInput,
                                    onValueChange = { nameInput = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            PrefsManager.saveUserName(nameInput)
                                            userName = nameInput
                                            isEditingName = false
                                        }) { Icon(Icons.Default.Check, null) }
                                    }
                                )
                            } else {
                                Text(
                                    text = userName,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { isEditingName = true }
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .clickable { showRanksSheet = true }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(userRank, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-60).dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                            }
                            Column {
                                Text(totalPlays.toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(stringResource(id = R.string.plays_count_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f).aspectRatio(1f).clickable { onArtistClick(topArtistName) },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                            }
                            Column {
                                Text(topArtistName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(stringResource(id = R.string.favorite_artist_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }

            if (topArtists.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.top_10_artists),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 24.dp, bottom = 16.dp).offset(y = (-30).dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.offset(y = (-30).dp)
                    ) {
                        items(topArtists) { artistEntry ->
                            val artistMeta = PrefsManager.getArtistMetadata(artistEntry.key)
                            Card(
                                modifier = Modifier.width(140.dp).aspectRatio(0.8f).clickable { onArtistClick(artistEntry.key) },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (artistMeta.avatarUri != null) {
                                        AsyncImage(model = artistMeta.avatarUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Person, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(
                                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))
                                        )
                                    )
                                    Column(
                                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                                    ) {
                                        Text(artistEntry.key, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                                        Text(pluralStringResource(id = R.plurals.plays_times, count = artistEntry.value, artistEntry.value), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (topTracksDetails.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.your_music_dna),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 16.dp)
                    )
                }
                itemsIndexed(topTracksDetails) { index, pair ->
                    val track = pair.first
                    val plays = pair.second

                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.03f),
                            modifier = Modifier.align(Alignment.CenterStart).offset(x = (-8).dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(AudioRepository.getAlbumArtUri(track.albumId)).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onArtistClick(track.artist) }
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                Text(plays.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                Text(stringResource(id = R.string.plays_count_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(start = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }
    }

    if (showRanksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRanksSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = stringResource(id = R.string.music_ranks_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                if (nextRank != null) {
                    val progress = totalPlays.toFloat() / nextRank.first.toFloat()
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Text(stringResource(id = R.string.tracks_left_for_rank_fmt, nextRank.second, nextRank.first - totalPlays), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                } else {
                    Text(stringResource(id = R.string.max_rank_achieved), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    Spacer(Modifier.height(16.dp))
                }

                LazyColumn {
                    items(ranksList) { rank ->
                        val isAchieved = totalPlays >= rank.first
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = rank.second,
                                    fontWeight = if (isAchieved) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isAchieved) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = if (isAchieved) stringResource(id = R.string.rank_unlocked) else stringResource(id = R.string.rank_required_plays_fmt, rank.first),
                                    color = if (isAchieved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            trailingContent = {
                                if (isAchieved) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}