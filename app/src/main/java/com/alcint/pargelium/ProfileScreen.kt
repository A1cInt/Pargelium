@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.alcint.pargelium

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

@Immutable
private data class ProfileStatsSnapshot(
    val filteredPlaysCount: Int = 0,
    val listenedMinutes: Long = 0L,
    val topArtistName: String = "",
    val topArtists: List<TopArtistUiModel> = emptyList(),
    val topTracks: List<Pair<AudioTrack, Int>> = emptyList(),
    val timeBuckets: List<Int> = listOf(0, 0, 0, 0)
)

@Composable
fun ProfileScreen(
    allTracks: List<AudioTrack>,
    seedColor: Color,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme

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
    var selectedPeriodDays by rememberSaveable { mutableIntStateOf(-1) } // -1: All time, 30: 30d, 7: 7d

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
    val allTimeTotalPlays = allEvents.size
    val unknownArtistStr = stringResource(id = R.string.unknown_artist)

    var statsSnapshot by remember {
        mutableStateOf(ProfileStatsSnapshot(topArtistName = unknownArtistStr))
    }

    LaunchedEffect(allEvents, allTracks, selectedPeriodDays, unknownArtistStr) {
        val computed = withContext(Dispatchers.Default) {
            val cutoffMs = if (selectedPeriodDays <= 0) {
                0L
            } else {
                System.currentTimeMillis() - (selectedPeriodDays * 86_400_000L)
            }

            val tracksById = HashMap<Long, AudioTrack>(allTracks.size)
            for (t in allTracks) tracksById[t.id] = t

            val trackPlays = HashMap<Long, Int>()
            val artistPlays = HashMap<String, Int>()
            val artistFallbackAlbumId = HashMap<String, Long>()
            var listenedMs = 0L
            var periodCount = 0
            val buckets = IntArray(4)
            val calendar = Calendar.getInstance()

            for (event in allEvents) {
                if (event.timestamp < cutoffMs) continue
                periodCount++
                trackPlays[event.trackId] = (trackPlays[event.trackId] ?: 0) + 1

                if (event.timestamp > 0L) {
                    calendar.timeInMillis = event.timestamp
                    when (calendar.get(Calendar.HOUR_OF_DAY)) {
                        in 6..11 -> buckets[0]++
                        in 12..17 -> buckets[1]++
                        in 18..23 -> buckets[2]++
                        else -> buckets[3]++
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

            ProfileStatsSnapshot(
                filteredPlaysCount = periodCount,
                listenedMinutes = (listenedMs / 60000L).coerceAtLeast(periodCount * 3L),
                topArtistName = resolvedTopArtists.firstOrNull()?.name ?: unknownArtistStr,
                topArtists = resolvedTopArtists,
                topTracks = resolvedTopTracks,
                timeBuckets = buckets.toList()
            )
        }
        statsSnapshot = computed
    }

    val ranksList = listOf(
        0 to stringResource(id = R.string.rank_novice),
        10 to stringResource(id = R.string.rank_listener),
        50 to stringResource(id = R.string.rank_melomane),
        200 to stringResource(id = R.string.rank_audiophile),
        1000 to stringResource(id = R.string.rank_music_maniac)
    )
    val userRank = ranksList.lastOrNull { allTimeTotalPlays >= it.first }?.second ?: ""
    val nextRank = ranksList.firstOrNull { allTimeTotalPlays < it.first }

    val bannerImageRequest = remember(context, bannerUri) {
        bannerUri?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(1080)
                .crossfade(true)
                .build()
        }
    }

    val avatarImageRequest = remember(context, avatarUri) {
        avatarUri?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(360)
                .crossfade(true)
                .build()
        }
    }

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
            item(key = "profile_banner", contentType = "banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(264.dp)
                        .graphicsLayer {
                            if (listState.firstVisibleItemIndex == 0) {
                                translationY = listState.firstVisibleItemScrollOffset * 0.36f
                            }
                        }
                ) {
                    if (bannerImageRequest != null) {
                        AsyncImage(
                            model = bannerImageRequest,
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
                                            accentColor.copy(alpha = 0.52f),
                                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
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
                                    0.0f to Color.Black.copy(alpha = 0.30f),
                                    0.5f to Color.Transparent,
                                    1.0f to MaterialTheme.colorScheme.background
                                )
                            )
                    )
                }
            }

            item(key = "profile_identity", contentType = "identity") {
                val avatarInteraction = remember { MutableInteractionSource() }
                val isAvatarPressed by avatarInteraction.collectIsPressedAsState()
                val avatarCornerPercent by animateDpAsState(
                    targetValue = if (isAvatarPressed) 32.dp else 58.dp,
                    animationSpec = motionScheme.fastSpatialSpec(),
                    label = "avatarMorph"
                )
                val avatarScale by animateFloatAsState(
                    targetValue = if (isAvatarPressed) 0.93f else 1f,
                    animationSpec = motionScheme.fastSpatialSpec(),
                    label = "avatarScale"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-52).dp)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .scale(avatarScale)
                            .shadow(18.dp, RoundedCornerShape(avatarCornerPercent), spotColor = accentColor)
                            .clip(RoundedCornerShape(avatarCornerPercent))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(3.dp, MaterialTheme.colorScheme.background, RoundedCornerShape(avatarCornerPercent))
                            .clickable(
                                interactionSource = avatarInteraction,
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarImageRequest != null) {
                            AsyncImage(
                                model = avatarImageRequest,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = accentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isEditingName) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(0.82f),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val clean = nameInput.trim().ifEmpty { userName }
                                        PrefsManager.saveUserName(clean)
                                        userName = clean
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
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { isEditingName = true }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
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

                    Spacer(Modifier.height(8.dp))

                    // Экспрессивная капсула ранга с индикатором прогресса до следующего звания
                    val rankInteraction = remember { MutableInteractionSource() }
                    val isRankPressed by rankInteraction.collectIsPressedAsState()
                    val rankCorner by animateDpAsState(
                        targetValue = if (isRankPressed) 14.dp else 26.dp,
                        animationSpec = motionScheme.fastSpatialSpec(),
                        label = "rankCorner"
                    )

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showRanksSheet = true
                        },
                        interactionSource = rankInteraction,
                        shape = RoundedCornerShape(rankCorner),
                        color = accentColor.copy(alpha = 0.16f),
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = accentColor.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(rankCorner)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = userRank,
                                style = MaterialTheme.typography.labelLarge,
                                color = accentColor,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (nextRank != null) {
                                Text(
                                    text = "• $allTimeTotalPlays / ${nextRank.first}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Морфинг-селектор периода статистики (All Time / 30 Days / 7 Days)
                    ProfilePeriodSelector(
                        selectedPeriodDays = selectedPeriodDays,
                        accentColor = accentColor,
                        onSelectPeriod = { days ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedPeriodDays = days
                        }
                    )
                }
            }

            item(key = "profile_bento_stats", contentType = "bento_stats") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-28).dp)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accentColor.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = statsSnapshot.filteredPlaysCount.toString(),
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
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.weight(1f)
                        ) {
                            val hours = (statsSnapshot.listenedMinutes / 60L).toInt()
                            val mins = (statsSnapshot.listenedMinutes % 60L).toInt()
                            val timeText = if (hours > 0) {
                                stringResource(id = R.string.duration_hours_mins_fmt, hours, mins)
                            } else {
                                stringResource(id = R.string.duration_mins_fmt, mins)
                            }

                            Column(modifier = Modifier.padding(18.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accentColor.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = timeText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = stringResource(id = R.string.profile_listening_time_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    val favArtistInteraction = remember { MutableInteractionSource() }
                    val isFavPressed by favArtistInteraction.collectIsPressedAsState()
                    val favCorner by animateDpAsState(
                        targetValue = if (isFavPressed) 18.dp else 26.dp,
                        animationSpec = motionScheme.fastSpatialSpec(),
                        label = "favArtistCorner"
                    )

                    Surface(
                        onClick = { onArtistClick(statsSnapshot.topArtistName) },
                        interactionSource = favArtistInteraction,
                        shape = RoundedCornerShape(favCorner),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.favorite_artist_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = statsSnapshot.topArtistName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (statsSnapshot.timeBuckets.sum() > 0) {
                        ListeningChronotypeCard(
                            buckets = statsSnapshot.timeBuckets,
                            accentColor = accentColor
                        )
                    }
                }
            }

            if (statsSnapshot.topArtists.isNotEmpty()) {
                item(key = "top_artists_section", contentType = "top_artists") {
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
                        itemsIndexed(
                            items = statsSnapshot.topArtists,
                            key = { _, item -> item.name },
                            contentType = { _, _ -> "top_artist_chip" }
                        ) { index, artistItem ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val shapeRadius by animateDpAsState(
                                targetValue = if (isPressed || index == 0) 28.dp else 46.dp,
                                animationSpec = motionScheme.fastSpatialSpec(),
                                label = "artistAvatarShape"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(104.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) { onArtistClick(artistItem.name) }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(92.dp)
                                        .shadow(6.dp, RoundedCornerShape(shapeRadius))
                                        .clip(RoundedCornerShape(shapeRadius))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            width = if (index == 0) 2.dp else 0.dp,
                                            color = if (index == 0) accentColor else Color.Transparent,
                                            shape = RoundedCornerShape(shapeRadius)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (artistItem.coverModel != null) {
                                        AsyncImage(
                                            model = remember(artistItem.coverModel) {
                                                ImageRequest.Builder(context)
                                                    .data(artistItem.coverModel)
                                                    .size(240)
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

            if (statsSnapshot.topTracks.isNotEmpty()) {
                item(key = "music_dna_header", contentType = "dna_header") {
                    Text(
                        text = stringResource(id = R.string.your_music_dna),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                    )
                }

                val maxPlays = statsSnapshot.topTracks.firstOrNull()?.second?.coerceAtLeast(1) ?: 1

                itemsIndexed(
                    items = statsSnapshot.topTracks,
                    key = { _, pair -> pair.first.id },
                    contentType = { _, _ -> "dna_track_item" }
                ) { index, (track, plays) ->
                    val artUri = remember(track.albumId) { AudioRepository.getAlbumArtUri(track.albumId) }
                    val ratio = (plays.toFloat() / maxPlays).coerceIn(0.08f, 1f)

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (index < 3) {
                            accentColor.copy(alpha = 0.09f)
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
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = remember(artUri) {
                                        ImageRequest.Builder(context)
                                            .data(artUri)
                                            .size(144)
                                            .crossfade(false)
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
                                Spacer(Modifier.height(5.dp))
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .drawWithCache {
                                            val barColor = accentColor.copy(alpha = 0.65f)
                                            val corner = CornerRadius(size.height / 2f, size.height / 2f)
                                            val barWidth = (size.width * ratio * 0.88f).coerceAtLeast(size.height)
                                            onDrawBehind {
                                                drawRoundRect(
                                                    color = barColor,
                                                    size = Size(barWidth, size.height),
                                                    cornerRadius = corner
                                                )
                                            }
                                        }
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
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(id = R.string.action_back)
            )
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
                    val progress = (allTimeTotalPlays.toFloat() / nextRank.first.toFloat()).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                id = R.string.tracks_left_for_rank_fmt,
                                nextRank.second,
                                nextRank.first - allTimeTotalPlays
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
                        val isAchieved = allTimeTotalPlays >= rank.first
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

@Composable
private fun ProfilePeriodSelector(
    selectedPeriodDays: Int,
    accentColor: Color,
    onSelectPeriod: (Int) -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val periods = listOf(
        -1 to stringResource(id = R.string.profile_period_all_time),
        30 to stringResource(id = R.string.profile_period_30_days),
        7 to stringResource(id = R.string.profile_period_7_days)
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            periods.forEach { (days, label) ->
                val isSelected = selectedPeriodDays == days
                val weight by animateFloatAsState(
                    targetValue = if (isSelected) 1.25f else 0.9f,
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    label = "periodWeight"
                )
                val corner by animateDpAsState(
                    targetValue = if (isSelected) 18.dp else 12.dp,
                    animationSpec = motionScheme.fastSpatialSpec(),
                    label = "periodCorner"
                )
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor.copy(alpha = 0.20f) else Color.Transparent,
                    animationSpec = motionScheme.fastEffectsSpec(),
                    label = "periodBg"
                )

                Surface(
                    onClick = { onSelectPeriod(days) },
                    shape = RoundedCornerShape(corner),
                    color = bgColor,
                    modifier = Modifier.weight(weight)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningChronotypeCard(
    buckets: List<Int>,
    accentColor: Color
) {
    val labels = listOf(
        stringResource(id = R.string.chronotype_morning),
        stringResource(id = R.string.chronotype_afternoon),
        stringResource(id = R.string.chronotype_evening),
        stringResource(id = R.string.chronotype_night)
    )
    val maxIdx = buckets.indices.maxByOrNull { buckets[it] } ?: 2
    val chronotypeTitle = when (maxIdx) {
        0 -> stringResource(id = R.string.chronotype_title_morning)
        1 -> stringResource(id = R.string.chronotype_title_afternoon)
        2 -> stringResource(id = R.string.chronotype_title_evening)
        else -> stringResource(id = R.string.chronotype_title_night)
    }
    val maxVal = (buckets.maxOrNull() ?: 1).coerceAtLeast(1)

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.profile_chronotype_subtitle),
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
                Icon(
                    imageVector = Icons.Rounded.AutoGraph,
                    contentDescription = null,
                    tint = accentColor
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                for (i in 0..3) {
                    val count = buckets.getOrElse(i) { 0 }
                    val fraction = (count.toFloat() / maxVal).coerceIn(0.14f, 1f)
                    val isPeak = i == maxIdx
                    val barColor = if (isPeak) accentColor else accentColor.copy(alpha = 0.26f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .drawWithCache {
                                    val barH = size.height * fraction
                                    val radius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                    onDrawBehind {
                                        drawRoundRect(
                                            color = barColor,
                                            topLeft = Offset(0f, size.height - barH),
                                            size = Size(size.width, barH),
                                            cornerRadius = radius
                                        )
                                    }
                                }
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = labels[i],
                            fontSize = 11.sp,
                            fontWeight = if (isPeak) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isPeak) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}