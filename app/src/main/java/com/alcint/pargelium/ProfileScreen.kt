package com.alcint.pargelium

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.cos
import kotlin.math.sin

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

    // Статы за всё время
    val allEvents = remember { PrefsManager.getPlayEvents() }
    val totalPlays = allEvents.size

    val unknownArtistStr = stringResource(id = R.string.unknown_artist)

    val topTracksDetails = remember(allEvents, allTracks) {
        val trackPlays = allEvents.groupingBy { it.trackId }.eachCount()
        trackPlays.entries.mapNotNull { entry ->
            val track = allTracks.find { it.id == entry.key }
            if (track != null) Pair(track, entry.value) else null
        }.sortedByDescending { it.second }.take(50)
    }

    val topArtists = remember(allEvents, allTracks, unknownArtistStr) {
        val artistPlays = mutableMapOf<String, Int>()
        for (event in allEvents) {
            val track = allTracks.find { it.id == event.trackId }
            if (track != null) {
                val artist = getPrimaryArtist(track.artist, unknownArtistStr)
                artistPlays[artist] = (artistPlays[artist] ?: 0) + 1
            }
        }
        artistPlays.entries.sortedByDescending { it.value }.take(10)
    }

    val topArtistName = topArtists.firstOrNull()?.key ?: stringResource(id = R.string.unknown_yet)

    val ranksList = listOf(
        0 to stringResource(id = R.string.rank_novice),
        10 to stringResource(id = R.string.rank_listener),
        50 to stringResource(id = R.string.rank_melomane),
        200 to stringResource(id = R.string.rank_audiophile),
        1000 to stringResource(id = R.string.rank_music_maniac)
    )
    val userRank = ranksList.last { totalPlays >= it.first }.second
    val nextRank = ranksList.firstOrNull { totalPlays < it.first }

    val color1 = rememberDominantColor(if (topTracksDetails.isNotEmpty()) AudioRepository.getAlbumArtUri(topTracksDetails[0].first.albumId) else null)
    val color2 = rememberDominantColor(if (topTracksDetails.size > 1) AudioRepository.getAlbumArtUri(topTracksDetails[1].first.albumId) else null)
    val color3 = rememberDominantColor(if (topTracksDetails.size > 2) AudioRepository.getAlbumArtUri(topTracksDetails[2].first.albumId) else null)

    val c1 = if (color1 != Color.Unspecified) color1 else MaterialTheme.colorScheme.primary
    val c2 = if (color2 != Color.Unspecified) color2 else MaterialTheme.colorScheme.tertiary
    val c3 = if (color3 != Color.Unspecified) color3 else MaterialTheme.colorScheme.secondary

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        val infiniteTransition = rememberInfiniteTransition(label = "aura")
        val anim1 by infiniteTransition.animateFloat(0f, 6.28f, infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Restart), label = "a1")
        val anim2 by infiniteTransition.animateFloat(0f, 6.28f, infiniteRepeatable(tween(35000, easing = LinearEasing), RepeatMode.Restart), label = "a2")

        Canvas(modifier = Modifier.fillMaxSize().blur(120.dp).alpha(0.3f)) {
            val w = size.width
            val h = size.height
            drawCircle(Brush.radialGradient(listOf(c1, Color.Transparent)), radius = w * 0.8f, center = Offset(w * 0.5f + cos(anim1)*w*0.4f, h * 0.4f + sin(anim1)*h*0.3f))
            drawCircle(Brush.radialGradient(listOf(c2, Color.Transparent)), radius = w * 0.7f, center = Offset(w * 0.3f + cos(anim2)*w*0.3f, h * 0.7f + sin(anim2)*h*0.4f))
            drawCircle(Brush.radialGradient(listOf(c3, Color.Transparent)), radius = w * 0.9f, center = Offset(w * 0.8f + sin(anim1)*w*0.3f, h * 0.6f + cos(anim2)*h*0.3f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 240.dp)
        ) {
            // Баннер и аватарка
            item {
                Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    // Баннер
                    if (bannerUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(bannerUri).build(),
                            contentDescription = stringResource(id = R.string.profile_banner_desc),
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
                            Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.6f to Color.Transparent,
                                0.85f to MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                1.0f to MaterialTheme.colorScheme.background
                            )
                        )
                    )

                    // Кнопка назад
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(start = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }

                    // Аватарка
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 20.dp)
                            .size(112.dp)
                            .shadow(16.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    ) {
                        if (avatarUri != null) {
                            AsyncImage(model = ImageRequest.Builder(context).data(avatarUri).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center).size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Имя и звание
            item {
                Spacer(modifier = Modifier.height(36.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isEditingName) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    PrefsManager.saveUserName(nameInput)
                                    userName = nameInput
                                    isEditingName = false
                                }) { Icon(Icons.Default.Check, "Save") }
                            }
                        )
                    } else {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { isEditingName = true }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Значок
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { showRanksSheet = true }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(userRank, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Карточка статистики
            item {
                Spacer(Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Карточка "Прослушиваний"
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(totalPlays.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            Text(stringResource(id = R.string.plays_count_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Карточка "Любимый артист" (ТЕПЕРЬ КЛИКАБЕЛЬНАЯ!)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onArtistClick(topArtistName) },
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.tertiary)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(topArtistName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(id = R.string.favorite_artist_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Топ 10 исполнителей
            if (topArtists.isNotEmpty()) {
                item {
                    Text(
                        stringResource(id = R.string.top_10_artists),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 40.dp, bottom = 16.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(topArtists) { artistEntry ->
                            val artistMeta = PrefsManager.getArtistMetadata(artistEntry.key)
                            // АВАТАРКИ АРТИСТОВ ТЕПЕРЬ КЛИКАБЕЛЬНЫЕ!
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(90.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onArtistClick(artistEntry.key) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    if (artistMeta.avatarUri != null) {
                                        AsyncImage(model = artistMeta.avatarUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center).size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(artistEntry.key, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(pluralStringResource(id = R.plurals.plays_times, count = artistEntry.value, artistEntry.value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Топ треков
            if (topTracksDetails.isNotEmpty()) {
                item {
                    Text(
                        stringResource(id = R.string.your_music_dna),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 40.dp, bottom = 16.dp)
                    )
                }
                itemsIndexed(topTracksDetails) { index, pair ->
                    val track = pair.first
                    val plays = pair.second
                    ListItem(
                        headlineContent = { Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                text = track.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onArtistClick(track.artist) }
                                    .padding(vertical = 2.dp, horizontal = 2.dp)
                            )
                        },
                        leadingContent = {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.width(32.dp),
                                textAlign = TextAlign.Center
                            )
                        },
                        trailingContent = {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp)) {
                                Text(pluralStringResource(id = R.plurals.plays_times, count = plays, plays), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            } else {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(id = R.string.listen_music_for_stats), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    // Звания (хз зачем, но забавно)
    if (showRanksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRanksSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = stringResource(id = R.string.music_ranks_title),
                    style = MaterialTheme.typography.headlineSmall,
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