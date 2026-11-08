@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)
package com.alcint.pargelium

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alcint.pargelium.streaming.StreamMoodChip
import com.alcint.pargelium.streaming.StreamProvider
import com.alcint.pargelium.streaming.StreamingDiscoveryFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

object RadioState {
    var selectedTab by mutableIntStateOf(0)

    var radioQuery by mutableStateOf("")
    var selectedGenreTag by mutableStateOf<String?>(null)
    var stations by mutableStateOf<List<AudioTrack>>(emptyList())
    var isRadioLoading by mutableStateOf(false)

    var streamQuery by mutableStateOf("")
    var selectedProvider by mutableStateOf(StreamProvider.ALL)
    var selectedMoodId by mutableStateOf("electronic")
    var discoveryFeed by mutableStateOf(StreamingDiscoveryFeed())
    var streamSearchResults by mutableStateOf<List<AudioTrack>>(emptyList())
    var isStreamLoading by mutableStateOf(false)
}

private val QUICK_RADIO_GENRES = listOf(
    R.string.genre_top_hits to null,
    R.string.genre_electronic to "electronic",
    R.string.genre_house to "house",
    R.string.genre_synthwave to "synthwave",
    R.string.genre_french_house to "french house",
    R.string.genre_lofi to "lofi",
    R.string.genre_rock to "rock",
    R.string.genre_dnb to "drum and bass",
    R.string.genre_ambient to "ambient",
    R.string.genre_jazz to "jazz"
)

private val STREAM_MOOD_CHIPS = listOf(
    StreamMoodChip("electronic", "Electronic Daft Punk Justice", R.string.genre_electronic),
    StreamMoodChip("synthwave", "Synthwave Retrowave Outrun", R.string.genre_synthwave),
    StreamMoodChip("french", "French House Ed Banger", R.string.genre_french_house),
    StreamMoodChip("indie_rock", "Indie Rock Foster The People Fall Out Boy", R.string.genre_rock),
    StreamMoodChip("edm", "Swedish House Mafia Progressive House", R.string.genre_house),
    StreamMoodChip("dnb", "Drum and Bass Liquid Neurofunk", R.string.genre_dnb),
    StreamMoodChip("lofi", "Lo-Fi Chillhop Beats", R.string.genre_lofi),
    StreamMoodChip("ambient", "Ambient Liminal Space", R.string.genre_ambient)
)

@Composable
fun RadioScreen(
    onStationSelect: (AudioTrack) -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme
    val primaryColor = MaterialTheme.colorScheme.primary

    var favoriteStations by remember {
        mutableStateOf(RadioRepository.getFavoriteStations(context))
    }
    var showOnlyFavorites by remember { mutableStateOf(false) }
    var activeSearchJob by remember { mutableStateOf<Job?>(null) }

    fun loadRadioStations(query: String, isTag: Boolean = false) {
        activeSearchJob?.cancel()
        activeSearchJob = scope.launch {
            RadioState.isRadioLoading = true
            try {
                RadioState.stations = RadioRepository.getStations(query, isTagSearch = isTag)
            } finally {
                RadioState.isRadioLoading = false
            }
        }
    }

    fun loadStreamDiscovery(moodQuery: String, forceRefresh: Boolean = false) {
        activeSearchJob?.cancel()
        activeSearchJob = scope.launch {
            RadioState.isStreamLoading = true
            try {
                RadioState.discoveryFeed = StreamingManager.loadDiscoveryFeed(moodQuery, forceRefresh)
            } finally {
                RadioState.isStreamLoading = false
            }
        }
    }

    fun executeStreamSearch(query: String, provider: StreamProvider = RadioState.selectedProvider) {
        if (query.isBlank()) return
        activeSearchJob?.cancel()
        activeSearchJob = scope.launch {
            RadioState.isStreamLoading = true
            try {
                RadioState.streamSearchResults = StreamingManager.searchByProvider(query, provider)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.stream_search_error_fmt, e.localizedMessage ?: ""),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                RadioState.isStreamLoading = false
            }
        }
    }

    LaunchedEffect(RadioState.selectedTab) {
        if (RadioState.selectedTab == 0 && RadioState.stations.isEmpty() && RadioState.radioQuery.isBlank()) {
            val tag = RadioState.selectedGenreTag
            loadRadioStations(tag ?: "", isTag = tag != null)
        } else if (RadioState.selectedTab == 1) {
            val feed = RadioState.discoveryFeed
            if (feed.ytMusicTrending.isEmpty() && feed.soundCloudHot.isEmpty() && RadioState.streamQuery.isBlank()) {
                val activeMood = STREAM_MOOD_CHIPS.firstOrNull { it.id == RadioState.selectedMoodId }
                    ?: STREAM_MOOD_CHIPS.first()
                loadStreamDiscovery(activeMood.query)
            }
        }
    }

    LaunchedEffect(RadioState.radioQuery) {
        if (RadioState.selectedTab == 0 && RadioState.radioQuery.length >= 2) {
            delay(450L)
            RadioState.selectedGenreTag = null
            loadRadioStations(RadioState.radioQuery, isTag = false)
        }
    }

    val displayedStations = remember(RadioState.stations, favoriteStations, showOnlyFavorites) {
        if (showOnlyFavorites) favoriteStations else RadioState.stations
    }

    val favoriteIds = remember(favoriteStations) {
        favoriteStations.map { it.id }.toSet()
    }

    val filteredSearchResults = remember(RadioState.streamSearchResults, RadioState.selectedProvider) {
        if (RadioState.selectedProvider == StreamProvider.ALL) {
            RadioState.streamSearchResults
        } else {
            RadioState.streamSearchResults.filter {
                it.source.equals(RadioState.selectedProvider.sourceKey, ignoreCase = true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        StaticAmbientBackdrop(seedColor = primaryColor)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 180.dp)
        ) {
            // Экспрессивный переключатель разделов (Онлайн Радио / Стриминг-Хаб)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isRadioTab = RadioState.selectedTab == 0
                val radioWeight by animateFloatAsState(
                    targetValue = if (isRadioTab) 1.18f else 0.88f,
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    label = "radioTabWeight"
                )
                val streamWeight by animateFloatAsState(
                    targetValue = if (!isRadioTab) 1.18f else 0.88f,
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    label = "streamTabWeight"
                )

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                RadioState.selectedTab = 0
                            },
                            shape = RoundedCornerShape(if (isRadioTab) 20.dp else 14.dp),
                            color = if (isRadioTab) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.weight(radioWeight)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sensors,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isRadioTab) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(id = R.string.tab_online_radio),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isRadioTab) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isRadioTab) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                RadioState.selectedTab = 1
                            },
                            shape = RoundedCornerShape(if (!isRadioTab) 20.dp else 14.dp),
                            color = if (!isRadioTab) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.weight(streamWeight)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudQueue,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (!isRadioTab) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(id = R.string.streaming),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (!isRadioTab) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (!isRadioTab) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val pool = if (RadioState.selectedTab == 0) {
                            displayedStations
                        } else if (RadioState.streamQuery.isNotBlank()) {
                            filteredSearchResults
                        } else {
                            val f = RadioState.discoveryFeed
                            f.ytMusicTrending + f.soundCloudHot + f.audiusUnderground + f.globalCharts
                        }
                        if (pool.isNotEmpty()) {
                            onStationSelect(pool.random())
                        }
                    },
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Casino,
                        contentDescription = stringResource(id = R.string.cd_random_wave)
                    )
                }
            }

            // Поисковая строка
            val activeQuery = if (RadioState.selectedTab == 0) RadioState.radioQuery else RadioState.streamQuery
            OutlinedTextField(
                value = activeQuery,
                onValueChange = { newText ->
                    if (RadioState.selectedTab == 0) {
                        RadioState.radioQuery = newText
                        if (newText.isEmpty()) {
                            loadRadioStations(RadioState.selectedGenreTag ?: "", isTag = RadioState.selectedGenreTag != null)
                        }
                    } else {
                        RadioState.streamQuery = newText
                        if (newText.isEmpty()) {
                            RadioState.streamSearchResults = emptyList()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = {
                    Text(
                        text = if (RadioState.selectedTab == 0) {
                            stringResource(id = R.string.search_station_hint)
                        } else {
                            stringResource(id = R.string.stream_search_hint)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (activeQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (RadioState.selectedTab == 0) {
                                    RadioState.radioQuery = ""
                                    loadRadioStations(RadioState.selectedGenreTag ?: "", isTag = RadioState.selectedGenreTag != null)
                                } else {
                                    RadioState.streamQuery = ""
                                    RadioState.streamSearchResults = emptyList()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        if (RadioState.selectedTab == 0) {
                            loadRadioStations(RadioState.radioQuery, isTag = false)
                        } else {
                            executeStreamSearch(RadioState.streamQuery, RadioState.selectedProvider)
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )

            Spacer(Modifier.height(10.dp))

            when (RadioState.selectedTab) {
                0 -> {
                    // Горизонтальная лента жанров радио и Избранного
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (favoriteStations.isNotEmpty()) {
                            item(key = "fav_chip") {
                                FilterChip(
                                    selected = showOnlyFavorites,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showOnlyFavorites = !showOnlyFavorites
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = if (showOnlyFavorites) primaryColor else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(id = R.string.radio_favorites_chip_fmt, favoriteStations.size),
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                )
                            }
                        }

                        items(QUICK_RADIO_GENRES, key = { it.first }) { (labelRes, tag) ->
                            val isSelected = !showOnlyFavorites && RadioState.selectedGenreTag == tag && RadioState.radioQuery.isBlank()
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showOnlyFavorites = false
                                    RadioState.radioQuery = ""
                                    RadioState.selectedGenreTag = tag
                                    loadRadioStations(tag ?: "", isTag = tag != null)
                                },
                                label = {
                                    Text(
                                        text = stringResource(id = labelRes),
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    if (RadioState.isRadioLoading && !showOnlyFavorites) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ContainedLoadingIndicator()
                        }
                    } else if (displayedStations.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(id = R.string.search_no_results),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = displayedStations,
                                key = { "${it.id}_${it.uri}" },
                                contentType = { "radio_station_card" }
                            ) { station ->
                                val isFav = favoriteIds.contains(station.id)
                                RadioItem(
                                    track = station,
                                    isFavorite = isFav,
                                    accentColor = primaryColor,
                                    onToggleFavorite = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        favoriteStations = RadioRepository.toggleFavoriteStation(context, station)
                                    },
                                    onClick = { onStationSelect(station) }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // Фильтр стриминговых платформ (All, YouTube Music, SoundCloud, Audius, JioSaavn, Hearthis, Jamendo)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(StreamProvider.entries, key = { it.name }) { provider ->
                            val isSelected = RadioState.selectedProvider == provider
                            val chipCorner by animateDpAsState(
                                targetValue = if (isSelected) 18.dp else 12.dp,
                                animationSpec = motionScheme.fastSpatialSpec(),
                                label = "providerCorner"
                            )

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    RadioState.selectedProvider = provider
                                    if (RadioState.streamQuery.isNotBlank()) {
                                        executeStreamSearch(RadioState.streamQuery, provider)
                                    }
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(provider.brandColor)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(id = provider.titleRes),
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                },
                                shape = RoundedCornerShape(chipCorner)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (RadioState.isStreamLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ContainedLoadingIndicator()
                        }
                    } else if (RadioState.streamQuery.isNotBlank()) {
                        // Режим результатов поиска по выбранному провайдеру
                        if (filteredSearchResults.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(id = R.string.search_no_results),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = filteredSearchResults,
                                    key = { "${it.source}_${it.id}" },
                                    contentType = { "stream_track_card" }
                                ) { track ->
                                    StreamItem(
                                        track = track,
                                        onClick = { onStationSelect(track) }
                                    )
                                }
                            }
                        }
                    } else {
                        // Структурированный Стриминг-Хаб (Discovery Hub)
                        StreamingDiscoveryHubContent(
                            feed = RadioState.discoveryFeed,
                            selectedProvider = RadioState.selectedProvider,
                            selectedMoodId = RadioState.selectedMoodId,
                            accentColor = primaryColor,
                            onSelectMood = { mood ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                RadioState.selectedMoodId = mood.id
                                loadStreamDiscovery(mood.query, forceRefresh = true)
                            },
                            onTrackSelect = onStationSelect
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingDiscoveryHubContent(
    feed: StreamingDiscoveryFeed,
    selectedProvider: StreamProvider,
    selectedMoodId: String,
    accentColor: Color,
    onSelectMood: (StreamMoodChip) -> Unit,
    onTrackSelect: (AudioTrack) -> Unit
) {
    val showYt = selectedProvider == StreamProvider.ALL || selectedProvider == StreamProvider.YOUTUBE
    val showSc = selectedProvider == StreamProvider.ALL || selectedProvider == StreamProvider.SOUNDCLOUD
    val showAudius = selectedProvider == StreamProvider.ALL || selectedProvider == StreamProvider.AUDIUS
    val showSaavn = selectedProvider == StreamProvider.ALL || selectedProvider == StreamProvider.SAAVN

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Быстрые музыкальные вайбы и жанры
        item(key = "hub_mood_chips", contentType = "mood_row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(STREAM_MOOD_CHIPS, key = { it.id }) { mood ->
                    val isSelected = selectedMoodId == mood.id
                    Surface(
                        onClick = { onSelectMood(mood) },
                        shape = RoundedCornerShape(if (isSelected) 18.dp else 50.dp),
                        color = if (isSelected) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.border(
                            width = if (isSelected) 1.5.dp else 0.dp,
                            color = if (isSelected) accentColor else Color.Transparent,
                            shape = RoundedCornerShape(if (isSelected) 18.dp else 50.dp)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = stringResource(id = mood.titleRes),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 2. Витрина YouTube Music (крупные карточки с обложками 544x544)
        if (showYt && feed.ytMusicTrending.isNotEmpty()) {
            item(key = "hub_yt_section", contentType = "yt_carousel") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HubSectionHeader(
                        title = stringResource(id = R.string.stream_section_yt_trending),
                        subtitle = stringResource(id = R.string.stream_section_yt_subtitle),
                        badgeColor = StreamProvider.YOUTUBE.brandColor
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = feed.ytMusicTrending,
                            key = { "yt_${it.id}" },
                            contentType = { "yt_hero_card" }
                        ) { track ->
                            FeaturedStreamHeroCard(
                                track = track,
                                badgeColor = StreamProvider.YOUTUBE.brandColor,
                                onClick = { onTrackSelect(track) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Витрина SoundCloud Radar (горизонтальные двухрядные карточки)
        if (showSc && feed.soundCloudHot.isNotEmpty()) {
            item(key = "hub_sc_section", contentType = "sc_carousel") {
                val pairs = remember(feed.soundCloudHot) { feed.soundCloudHot.chunked(2) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HubSectionHeader(
                        title = stringResource(id = R.string.stream_section_sc_radar),
                        subtitle = stringResource(id = R.string.stream_section_sc_subtitle),
                        badgeColor = StreamProvider.SOUNDCLOUD.brandColor
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pairs, key = { "sc_pair_${it.first().id}" }) { pair ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.width(276.dp)
                            ) {
                                pair.forEach { track ->
                                    StreamItem(
                                        track = track,
                                        onClick = { onTrackSelect(track) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Витрина Audius Underground (децентрализованный стриминг 320 kbps)
        if (showAudius && feed.audiusUnderground.isNotEmpty()) {
            item(key = "hub_audius_section", contentType = "audius_carousel") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HubSectionHeader(
                        title = stringResource(id = R.string.stream_section_audius_title),
                        subtitle = stringResource(id = R.string.stream_section_audius_subtitle),
                        badgeColor = StreamProvider.AUDIUS.brandColor
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = feed.audiusUnderground,
                            key = { "aud_${it.id}" },
                            contentType = { "audius_hero_card" }
                        ) { track ->
                            FeaturedStreamHeroCard(
                                track = track,
                                badgeColor = StreamProvider.AUDIUS.brandColor,
                                onClick = { onTrackSelect(track) }
                            )
                        }
                    }
                }
            }
        }

        // 5. Глобальный чарт JioSaavn 320kbps
        if (showSaavn && feed.globalCharts.isNotEmpty()) {
            item(key = "hub_global_header", contentType = "header") {
                HubSectionHeader(
                    title = stringResource(id = R.string.stream_section_global_charts),
                    subtitle = stringResource(id = R.string.stream_section_global_subtitle),
                    badgeColor = StreamProvider.SAAVN.brandColor
                )
            }

            items(
                items = feed.globalCharts.take(15),
                key = { "chart_${it.id}" },
                contentType = { "stream_track_card" }
            ) { track ->
                Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                    StreamItem(
                        track = track,
                        onClick = { onTrackSelect(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HubSectionHeader(
    title: String,
    subtitle: String,
    badgeColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(28.dp)
                .clip(CircleShape)
                .background(badgeColor)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeaturedStreamHeroCard(
    track: AudioTrack,
    badgeColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val motionScheme = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "heroScale"
    )
    val cardCorner by animateDpAsState(
        targetValue = if (isPressed) 18.dp else 24.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "heroCorner"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(cardCorner),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .width(164.dp)
            .scale(cardScale)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!track.coverUrl.isNullOrEmpty() && track.coverUrl != "null") {
                    AsyncImage(
                        model = remember(track.coverUrl) {
                            ImageRequest.Builder(context)
                                .data(track.coverUrl)
                                .size(320)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier
                            .size(42.dp)
                            .align(Alignment.Center)
                    )
                }

                if (track.duration > 0L) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = formatStreamDuration(track.duration),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = badgeColor,
                    contentColor = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RadioItem(
    track: AudioTrack,
    isFavorite: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onToggleFavorite: () -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!track.coverUrl.isNullOrEmpty() && track.coverUrl != "null") {
                    AsyncImage(
                        model = remember(track.coverUrl) {
                            ImageRequest.Builder(context)
                                .data(track.coverUrl)
                                .size(180)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track.album.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = track.album,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StreamItem(
    track: AudioTrack,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val provider = remember(track.source) { StreamProvider.fromSource(track.source) }
    val badgeColor = if (provider != StreamProvider.ALL) provider.brandColor else primaryColor

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (!track.coverUrl.isNullOrEmpty() && track.coverUrl != "null") {
                    AsyncImage(
                        model = remember(track.coverUrl) {
                            ImageRequest.Builder(context)
                                .data(track.coverUrl)
                                .size(180)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.duration > 0L) {
                        Text(
                            text = "• ${formatStreamDuration(track.duration)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                color = badgeColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = track.source.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun formatStreamDuration(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", min, sec)
}

@Composable
private fun StaticAmbientBackdrop(seedColor: Color) {
    val smoothColor by animateColorAsState(
        targetValue = seedColor,
        animationSpec = tween(800),
        label = "ambientRadioColor"
    )
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            smoothColor.copy(alpha = 0.22f),
                            smoothColor.copy(alpha = 0.06f),
                            backgroundColor
                        )
                    )
                )
            }
    )
}