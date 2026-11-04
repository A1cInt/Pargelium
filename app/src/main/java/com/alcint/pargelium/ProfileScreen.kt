@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.alcint.pargelium

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.alcint.pargelium.library.getPrimaryArtist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

@Immutable
private data class TopArtistUiModel(
    val name: String,
    val playCount: Int,
    val coverModel: Any?
)

@Composable
fun ProfileScreen(
    allTracks: List<AudioTrack>,
    seedColor: Color,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val accentColor = if (seedColor != Color.Unspecified && seedColor != Color.Transparent) {
        seedColor
    } else {
        MaterialTheme.colorScheme.primary
    }

    var userName by remember { mutableStateOf(PrefsManager.getUserName()) }
    var avatarUri by remember { mutableStateOf(PrefsManager.getAvatarUri()) }
    var bannerUri by remember { mutableStateOf(PrefsManager.getBannerUri()) }

    var isEditingName by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(userName) }
    var showRanksSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            PrefsManager.saveAvatarUri(uri.toString())
            avatarUri = uri.toString()
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            PrefsManager.saveBannerUri(uri.toString())
            bannerUri = uri.toString()
        }
    }

    val allEvents = remember { PrefsManager.getPlayEvents() }
    val totalPlays = allEvents.size
    val unknownArtistStr = stringResource(id = R.string.unknown_artist)

    var topTracksDetails by remember { mutableStateOf<List<Pair<AudioTrack, Int>>>(emptyList()) }
    var topArtistsUi by remember { mutableStateOf<List<TopArtistUiModel>>(emptyList()) }
    var topArtistName by remember { mutableStateOf(unknownArtistStr) }
    var totalListenedMinutes by remember { mutableLongStateOf(0L) }
    var timeOfDayBuckets by remember { mutableStateOf(intArrayOf(0, 0, 0, 0)) } // Утро, День, Вечер, Ночь

    // Быстрый O(1) расчет статистики в фоновом потоке без блокировки UI
    LaunchedEffect(allEvents, allTracks) {
        withContext(Dispatchers.Default) {
            val tracksById = allTracks.associateBy { it.id }

            val trackPlays = HashMap<Long, Int>()
            val artistPlays = HashMap<String, Int>()
            val artistFallbackAlbumId = HashMap<String, Long>()
            var listenedMs = 0L
            val buckets = IntArray(4)
            val calendar = Calendar.getInstance()

            for (event in allEvents) {
                trackPlays[event.trackId] = (trackPlays[event.trackId] ?: 0) + 1

                if (event.timestamp > 0L) {
                    calendar.timeInMillis = event.timestamp
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    when (hour) {
                        in 6..11 -> buckets[0]++   // Утро
                        in 12..17 -> buckets[1]++  // День
                        in 18..23 -> buckets[2]++  // Вечер
                        else -> buckets[3]++       // Ночь
                    }
                }

                val track = tracksById[event.trackId]
                if (track != null) {
                    listenedMs += track.duration.coerceAtLeast(180_000L)
                    val primaryArtist = getPrimaryArtist(track.artist, unknownArtistStr)
                    artistPlays[primaryArtist] = (artistPlays[primaryArtist] ?: 0) + 1
                    if (!artistFallbackAlbumId.containsKey(primaryArtist) && track.albumId > 0L) {
                        artistFallbackAlbumId[primaryArtist] = track.albumId
                    }
                }
            }

            val resolvedTopTracks = trackPlays.entries
                .mapNotNull { (id, count) -> tracksById[id]?.let { it to count } }
                .sortedByDescending { it.second }
                .take(50)

            val resolvedTopArtists = artistPlays.entries
                .sortedByDescending { it.value }
                .take(10)
                .map { (artistName, count) ->
                    val customAvatar = try {
                        PrefsManager.getArtistMetadata(artistName).avatarUri
                    } catch (_: Exception) { null }
                    val cover: Any? = if (!customAvatar.isNullOrBlank()) {
                        customAvatar
                    } else {
                        artistFallbackAlbumId[artistName]?.let { AudioRepository.getAlbumArtUri(it) }
                    }
                    TopArtistUiModel(
                        name = artistName,
                        playCount = count,
                        coverModel = cover
                    )
                }

            topTracksDetails = resolvedTopTracks
            topArtistsUi = resolvedTopArtists
            topArtistName = resolvedTopArtists.firstOrNull()?.name ?: unknownArtistStr
            totalListenedMinutes = (listenedMs / 60000L).coerceAtLeast(totalPlays * 3L)
            timeOfDayBuckets = buckets
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 240.dp)
        ) {
            item(key = "profile_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                        .graphicsLayer {
                            if (listState.firstVisibleItemIndex == 0) {
                                translationY = listState.firstVisibleItemScrollOffset * 0.38f
                            }
                        }
                ) {
                    if (bannerUri != null) {
                        AsyncImage(
                            model = remember(bannerUri) {
                                ImageRequest.Builder(context)
                                    .data(bannerUri)
                                    .size(1080)
                                    .crossfade(true)
                                    .build()
                            },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            accentColor.copy(alpha = 0.45f),
                                            MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                )
                                .clickable {
                                    bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Black.copy(alpha = 0.32f),
                                    0.5f to Color.Transparent,
                                    1.0f to MaterialTheme.colorScheme.background
                                )
                            )
                    )
                }
            }

            item(key = "profile_identity") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-48).dp)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .shadow(16.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                            .clickable {
                                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUri != null) {
                            AsyncImage(
                                model = remember(avatarUri) {
                                    ImageRequest.Builder(context)
                                        .data(avatarUri)
                                        .size(360)
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
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isEditingName) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(0.8f),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        PrefsManager.saveUserName(nameInput)
                                        userName = nameInput
                                        isEditingName = false
                                    }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { isEditingName = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Surface(
                        onClick = { showRanksSheet = true },
                        shape = RoundedCornerShape(50),
                        color = accentColor.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = userRank,
                                style = MaterialTheme.typography.labelLarge,
                                color = accentColor,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // Bento-сетка статистики профиля
            item(key = "profile_bento_stats") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-24).dp)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = totalPlays.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = stringResource(id = R.string.plays_count_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.weight(1f)
                        ) {
                            val hours = totalListenedMinutes / 60L
                            val mins = totalListenedMinutes % 60L
                            val timeText = if (hours > 0L) "${hours}h ${mins}m" else "${mins}m"

                            Column(modifier = Modifier.padding(16.dp)) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = timeText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Время в музыке",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { onArtistClick(topArtistName) },
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.favorite_artist_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = topArtistName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Хронотип активности слушателя по времени суток
                    if (timeOfDayBuckets.sum() > 0) {
                        ListeningChronotypeCard(
                            buckets = timeOfDayBuckets,
                            accentColor = accentColor
                        )
                    }
                }
            }

            if (topArtistsUi.isNotEmpty()) {
                item(key = "top_artists_section") {
                    Text(
                        text = stringResource(id = R.string.top_10_artists),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = topArtistsUi,
                            key = { it.name },
                            contentType = { "top_artist_chip" }
                        ) { artistItem ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(104.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onArtistClick(artistItem.name) }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(92.dp)
                                        .shadow(6.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (artistItem.coverModel != null) {
                                        AsyncImage(
                                            model = remember(artistItem.coverModel) {
                                                ImageRequest.Builder(context)
                                                    .data(artistItem.coverModel)
                                                    .size(256)
                                                    .crossfade(true)
                                                    .build()
                                            },
                                            contentDescription = artistItem.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = artistItem.name.take(1).uppercase(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = accentColor
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = artistItem.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = pluralStringResource(
                                        id = R.plurals.plays_times,
                                        count = artistItem.playCount,
                                        artistItem.playCount
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            if (topTracksDetails.isNotEmpty()) {
                item(key = "music_dna_header") {
                    Text(
                        text = stringResource(id = R.string.your_music_dna),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                    )
                }

                val maxPlays = topTracksDetails.firstOrNull()?.second?.coerceAtLeast(1) ?: 1

                itemsIndexed(
                    items = topTracksDetails,
                    key = { _, pair -> pair.first.id },
                    contentType = { _, _ -> "dna_track_item" }
                ) { index, (track, plays) ->
                    val artUri = remember(track.albumId) { AudioRepository.getAlbumArtUri(track.albumId) }
                    val ratio = (plays.toFloat() / maxPlays).coerceIn(0.1f, 1f)

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (index < 3) {
                            accentColor.copy(alpha = 0.08f)
                        } else {
                            Color.Transparent
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (index < 3) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(28.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
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
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onArtistClick(track.artist) }
                                )
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(ratio * 0.85f)
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(accentColor.copy(alpha = 0.55f))
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "${plays}×",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        FilledTonalIconButton(
            onClick = onBack,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, top = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
    }

    if (showRanksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRanksSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = stringResource(id = R.string.music_ranks_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                if (nextRank != null) {
                    val progress = (totalPlays.toFloat() / nextRank.first.toFloat()).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                id = R.string.tracks_left_for_rank_fmt,
                                nextRank.second,
                                nextRank.first - totalPlays
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = accentColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                } else {
                    Text(
                        text = stringResource(id = R.string.max_rank_achieved),
                        style = MaterialTheme.typography.bodyMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                }

                LazyColumn {
                    items(ranksList, key = { it.first }) { rank ->
                        val isAchieved = totalPlays >= rank.first
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = rank.second,
                                    fontWeight = if (isAchieved) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (isAchieved) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = if (isAchieved) stringResource(id = R.string.rank_unlocked)
                                    else stringResource(id = R.string.rank_required_plays_fmt, rank.first),
                                    color = if (isAchieved) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            trailingContent = {
                                if (isAchieved) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accentColor)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
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

/**
 * Уникальная карточка хронотипа слушателя (Утро / День / Вечер / Ночь)
 */
@Composable
private fun ListeningChronotypeCard(
    buckets: IntArray,
    accentColor: Color
) {
    val labels = listOf("Утро", "День", "Вечер", "Ночь")
    val maxIdx = buckets.indices.maxByOrNull { buckets[it] } ?: 2
    val chronotypeTitle = when (maxIdx) {
        0 -> "Утренний Энерджайзер"
        1 -> "Дневной Ритм"
        2 -> "Вечерний Меломан"
        else -> "Ночной Аудиофил"
    }
    val maxVal = (buckets.maxOrNull() ?: 1).coerceAtLeast(1)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Биоритм прослушиваний",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = chronotypeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                for (i in 0..3) {
                    val fraction = (buckets[i].toFloat() / maxVal).coerceIn(0.12f, 1f)
                    val isPeak = i == maxIdx
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            val barH = size.height * fraction
                            drawRoundRect(
                                color = if (isPeak) accentColor else accentColor.copy(alpha = 0.28f),
                                topLeft = Offset(0f, size.height - barH),
                                size = Size(size.width, barH),
                                cornerRadius = CornerRadius(6.dp.toPx())
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = labels[i],
                            fontSize = 11.sp,
                            fontWeight = if (isPeak) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isPeak) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}