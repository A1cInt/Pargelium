@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)
package com.alcint.pargelium

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object RadioState {
    var selectedTab by mutableIntStateOf(0)

    var radioQuery by mutableStateOf("")
    var stations by mutableStateOf<List<AudioTrack>>(emptyList())
    var isRadioLoading by mutableStateOf(false)

    var streamQuery by mutableStateOf("")
    var streamTracks by mutableStateOf<List<AudioTrack>>(emptyList())
    var isStreamLoading by mutableStateOf(false)
}

@Composable
fun RadioScreen(
    onStationSelect: (AudioTrack) -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary

    val tabs = listOf(
        stringResource(id = R.string.tab_online_radio),
        "Стриминг" // YouTube Music, SoundCloud, Audius, Saavn и др.
    )

    LaunchedEffect(RadioState.selectedTab) {
        if (RadioState.selectedTab == 0 && RadioState.stations.isEmpty() && RadioState.radioQuery.isBlank()) {
            RadioState.isRadioLoading = true
            RadioState.stations = RadioRepository.getStations("")
            RadioState.isRadioLoading = false
        }
    }

    LaunchedEffect(RadioState.selectedTab) {
        if (RadioState.selectedTab == 1 && RadioState.streamTracks.isEmpty() && RadioState.streamQuery.isBlank()) {
            RadioState.isStreamLoading = true
            scope.launch {
                try {
                    val tracks = withContext(Dispatchers.IO) {
                        val saavnTop = try { SaavnApi.getTopTracks() } catch (e: Exception) { emptyList() }

                        val mappedSaavn = saavnTop.map { AudioTrack(id = it.longId, title = it.title, artist = it.artist, album = "JioSaavn", uri = Uri.parse(it.streamUrl), albumId = -5L, duration = it.duration * 1000L, trackNumber = 1, discNumber = 1, source = "saavn", coverUrl = it.coverUrl) }

                        (mappedSaavn).shuffled()
                    }
                    RadioState.streamTracks = tracks
                } catch (e: Exception) {
                    android.util.Log.e("RadioScreen", "Ошибка загрузки топа стриминга", e)
                } finally { RadioState.isStreamLoading = false }
            }
        }
    }

    fun executeSearch() {
        focusManager.clearFocus()
        when (RadioState.selectedTab) {
            0 -> {
                scope.launch {
                    RadioState.isRadioLoading = true
                    try { RadioState.stations = RadioRepository.getStations(RadioState.radioQuery) }
                    finally { RadioState.isRadioLoading = false }
                }
            }
            1 -> {
                if (RadioState.streamQuery.isBlank()) return
                scope.launch {
                    RadioState.isStreamLoading = true
                    try {
                        RadioState.streamTracks = StreamingManager.searchAll(RadioState.streamQuery)
                    } catch (e: Exception) {
                        if (e !is kotlinx.coroutines.CancellationException) Toast.makeText(context, "Ошибка поиска: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally { RadioState.isStreamLoading = false }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground(seedColor = primaryColor)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 100.dp)
        ) {
            TabRow(
                selectedTabIndex = RadioState.selectedTab,
                containerColor = Color.Transparent,
                contentColor = primaryColor,
                indicator = { tabPositions ->
                    if (RadioState.selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[RadioState.selectedTab]),
                            color = primaryColor
                        )
                    }
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = RadioState.selectedTab == index,
                        onClick = { RadioState.selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                color = if (RadioState.selectedTab == index) primaryColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = when (RadioState.selectedTab) {
                    0 -> RadioState.radioQuery
                    else -> RadioState.streamQuery
                },
                onValueChange = {
                    when (RadioState.selectedTab) {
                        0 -> RadioState.radioQuery = it
                        else -> RadioState.streamQuery = it
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = {
                    Text(
                        when (RadioState.selectedTab) {
                            0 -> stringResource(id = R.string.search_station_hint)
                            else -> "Поиск музыки (YouTube, SoundCloud...)"
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { executeSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(Modifier.height(16.dp))

            when (RadioState.selectedTab) {
                0 -> {
                    if (RadioState.isRadioLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ContainedLoadingIndicator() }
                    } else if (RadioState.stations.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(id = R.string.search_no_results), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                    } else {
                        LazyColumn { items(RadioState.stations) { station -> RadioItem(station) { onStationSelect(station) } } }
                    }
                }
                1 -> {
                    if (RadioState.isStreamLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ContainedLoadingIndicator() }
                    } else if (RadioState.streamTracks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(id = R.string.search_no_results), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                    } else {
                        LazyColumn {
                            items(RadioState.streamTracks) { track ->
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
fun RadioItem(track: AudioTrack, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Radio, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
            Text(text = track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun StreamItem(track: AudioTrack, primaryColor: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (!track.coverUrl.isNullOrEmpty() && track.coverUrl != "null") {
                AsyncImage(model = track.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
            } else {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
            Text(text = track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(color = primaryColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
            Text(text = track.source.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = primaryColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
fun AuroraBackground(seedColor: Color) {
    val smoothColor by animateColorAsState(targetValue = seedColor, animationSpec = tween(1000, easing = LinearOutSlowInEasing), label = "smoothColor")
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val alpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 0.1f, animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "alpha")
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(smoothColor.copy(alpha = alpha), MaterialTheme.colorScheme.background))))
}