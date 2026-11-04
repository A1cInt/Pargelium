@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)
package com.alcint.pargelium

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object RadioState {
    var selectedTab by mutableIntStateOf(0)

    var radioQuery by mutableStateOf("")
    var selectedGenreTag by mutableStateOf<String?>(null)
    var stations by mutableStateOf<List<AudioTrack>>(emptyList())
    var isRadioLoading by mutableStateOf(false)

    var streamQuery by mutableStateOf("")
    var streamTracks by mutableStateOf<List<AudioTrack>>(emptyList())
    var isStreamLoading by mutableStateOf(false)
}

private val QUICK_RADIO_GENRES = listOf(
    "Top Hits" to null,
    "Electronic" to "electronic",
    "House" to "house",
    "Synthwave" to "synthwave",
    "Daft / French" to "french house",
    "Lo-Fi" to "lofi",
    "Rock" to "rock",
    "Drum & Bass" to "drum and bass",
    "Ambient" to "ambient",
    "Jazz" to "jazz",
    "Classical" to "classical"
)

@Composable
fun RadioScreen(
    onStationSelect: (AudioTrack) -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
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

    fun executeStreamSearch(query: String) {
        if (query.isBlank()) return
        activeSearchJob?.cancel()
        activeSearchJob = scope.launch {
            RadioState.isStreamLoading = true
            try {
                RadioState.streamTracks = StreamingManager.searchAll(query)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Toast.makeText(context, "Ошибка поиска: ${e.message}", Toast.LENGTH_SHORT).show()
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
        } else if (RadioState.selectedTab == 1 && RadioState.streamTracks.isEmpty() && RadioState.streamQuery.isBlank()) {
            RadioState.isStreamLoading = true
            try {
                val tracks = withContext(Dispatchers.IO) {
                    val saavnTop = try { SaavnApi.getTopTracks() } catch (_: Exception) { emptyList() }
                    saavnTop.map {
                        AudioTrack(
                            id = it.longId,
                            title = it.title,
                            artist = it.artist,
                            album = "JioSaavn • 320 kbps",
                            uri = Uri.parse(it.streamUrl),
                            albumId = -5L,
                            duration = it.duration * 1000L,
                            trackNumber = 1,
                            discNumber = 1,
                            source = "saavn",
                            coverUrl = it.coverUrl
                        )
                    }.shuffled()
                }
                RadioState.streamTracks = tracks
            } catch (_: Exception) {
            } finally {
                RadioState.isStreamLoading = false
            }
        }
    }

    // Мягкий debounce-поиск для радио при наборе текста (без спама запросами)
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

    Box(modifier = Modifier.fillMaxSize()) {
        StaticAmbientBackdrop(seedColor = primaryColor)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 180.dp)
        ) {
            // Верхний экспрессивный переключатель режимов (Онлайн Радио / Стриминг)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isRadioTab = RadioState.selectedTab == 0
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                RadioState.selectedTab = 0
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isRadioTab) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.weight(1f)
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
                                    color = if (isRadioTab) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                RadioState.selectedTab = 1
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = if (!isRadioTab) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.weight(1f)
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
                                    color = if (!isRadioTab) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Кнопка "Случайная волна / Трек"
                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val pool = if (RadioState.selectedTab == 0) displayedStations else RadioState.streamTracks
                        if (pool.isNotEmpty()) {
                            onStationSelect(pool.random())
                        }
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.Casino, contentDescription = "Random Wave")
                }
            }

            // Поле поиска с кнопкой быстрой очистки
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
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = {
                    Text(
                        if (RadioState.selectedTab == 0) stringResource(id = R.string.search_station_hint)
                        else "Поиск музыки (JioSaavn, YouTube, SoundCloud...)"
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
                            executeStreamSearch(RadioState.streamQuery)
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

            // Горизонтальная лента жанровых волн и Избранного (для вкладки Радио)
            if (RadioState.selectedTab == 0) {
                Spacer(Modifier.height(10.dp))
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
                                        text = "Избранное (${favoriteStations.size})",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }

                    items(QUICK_RADIO_GENRES, key = { it.first }) { (label, tag) ->
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
                            label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            when (RadioState.selectedTab) {
                0 -> {
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
                    if (RadioState.isStreamLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ContainedLoadingIndicator()
                        }
                    } else if (RadioState.streamTracks.isEmpty()) {
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
                                items = RadioState.streamTracks,
                                key = { "${it.id}_${it.uri}" },
                                contentType = { "stream_track_card" }
                            ) { track ->
                                StreamItem(
                                    track = track,
                                    primaryColor = primaryColor,
                                    onClick = { onStationSelect(track) }
                                )
                            }
                        }
                    }
                }
            }
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
        shape = RoundedCornerShape(20.dp),
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
                    contentDescription = "Favorite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StreamItem(
    track: AudioTrack,
    primaryColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
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
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                color = primaryColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = track.source.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Энергоэффективный статический фон без бесконечного таймера рекомпозиции:
 * 0% нагрузки на батарею и GPU при просмотре списка радиостанций.
 */
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