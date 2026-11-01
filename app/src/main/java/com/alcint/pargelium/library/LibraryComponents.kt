@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.alcint.pargelium.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alcint.pargelium.AlbumModel
import com.alcint.pargelium.AudioRepository
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.R
import com.alcint.pargelium.AlbumCard
import com.alcint.pargelium.TrackRow
import kotlinx.coroutines.launch

@Composable
fun FastScrollThumb(
    itemCount: Int,
    state: LazyGridState,
    getLetter: (Int) -> String
) {
    if (itemCount <= 0) return

    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    val isScrolling by remember { derivedStateOf { state.isScrollInProgress } }
    val showThumb = isScrolling || isDragging

    val thumbAlpha by animateFloatAsState(
        targetValue = if (showThumb) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = if (showThumb) 0 else 1000),
        label = "thumbAlpha"
    )

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 4.dp,
        animationSpec = tween(150),
        label = "thumbWidth"
    )

    val currentLetter by remember {
        derivedStateOf {
            val safeVisibleIndex = state.firstVisibleItemIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
            getLetter(safeVisibleIndex)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .width(24.dp)
            .pointerInput(itemCount) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isDragging = true
                    dragOffsetY = down.position.y

                    fun scrollToExact(y: Float) {
                        val fraction = (y / size.height).coerceIn(0f, 1f)
                        val exactIndex = fraction * (itemCount - 1).coerceAtLeast(0)
                        val index = exactIndex.toInt()

                        val itemHeight = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 300
                        val offset = ((exactIndex - index) * itemHeight).toInt()

                        scope.launch { state.scrollToItem(index, offset) }
                    }

                    scrollToExact(dragOffsetY)

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            change.consume()
                            dragOffsetY = change.position.y
                            scrollToExact(dragOffsetY)
                        }
                    } while (event.changes.any { it.pressed })
                    isDragging = false
                }
            }
    ) {
        val trackHeight = constraints.maxHeight.toFloat()
        val thumbHeightPx = with(LocalDensity.current) { 40.dp.toPx() }

        androidx.compose.animation.AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(tween(200)) + scaleIn(tween(200)),
            exit = fadeOut(tween(200)) + scaleOut(tween(200)),
            modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(-80, dragOffsetY.toInt() - 70) }
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .shadow(4.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentLetter,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (thumbAlpha > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .offset {
                        val targetThumbY = if (isDragging) {
                            dragOffsetY.coerceIn(0f, trackHeight - thumbHeightPx)
                        } else {
                            val firstItem = state.layoutInfo.visibleItemsInfo.firstOrNull()
                            val offsetFraction = if (firstItem != null && firstItem.size.height > 0) {
                                -firstItem.offset.y.toFloat() / firstItem.size.height
                            } else 0f
                            val currentFraction = (state.firstVisibleItemIndex + offsetFraction).coerceIn(0f, itemCount - 1f) / (itemCount - 1).coerceAtLeast(1)
                            (currentFraction * (trackHeight - thumbHeightPx)).coerceIn(0f, trackHeight - thumbHeightPx)
                        }
                        IntOffset(0, targetThumbY.toInt())
                    }
                    .width(thumbWidth)
                    .height(40.dp)
                    .graphicsLayer { alpha = thumbAlpha }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
            )
        }
    }
}

@Composable
fun ArtistCard(artist: ArtistModel, onClick: () -> Unit) {
    val meta = remember(artist.name) { PrefsManager.getArtistMetadata(artist.name) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
                if (meta.avatarUri != null) {
                    AsyncImage(model = meta.avatarUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center).size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = artist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = pluralStringResource(id = R.plurals.tracks_count, count = artist.tracks.size, artist.tracks.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ArtistProfileView(
    artist: ArtistModel,
    animatedVisibilityScope: AnimatedVisibilityScope,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackSelect: (AudioTrack, List<AudioTrack>) -> Unit,
    onAlbumSelect: (AlbumModel) -> Unit,
    onPlayPause: () -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onShareTrack: (AudioTrack) -> Unit
) {
    var meta by remember(artist.name) { mutableStateOf(PrefsManager.getArtistMetadata(artist.name)) }
    var isEditingDesc by remember { mutableStateOf(false) }
    var descInput by remember { mutableStateOf(meta.description) }

    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(e: Exception){}
            val newMeta = meta.copy(avatarUri = uri.toString())
            PrefsManager.saveArtistMetadata(artist.name, newMeta)
            meta = newMeta
        }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(e: Exception){}
            val newMeta = meta.copy(bannerUri = uri.toString())
            PrefsManager.saveArtistMetadata(artist.name, newMeta)
            meta = newMeta
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 240.dp)) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                if (meta.bannerUri != null) {
                    AsyncImage(model = meta.bannerUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clickable { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).clickable { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
                    }
                }
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))

                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp, start = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }

                Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 40.dp).size(140.dp).shadow(16.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    if (meta.avatarUri != null) {
                        AsyncImage(model = meta.avatarUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center).size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(56.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(artist.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(16.dp))

                if (isEditingDesc) {
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(id = R.string.description_label)) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val newMeta = meta.copy(description = descInput)
                                PrefsManager.saveArtistMetadata(artist.name, newMeta)
                                meta = newMeta
                                isEditingDesc = false
                            }) { Icon(Icons.Rounded.Check, "Save") }
                        }
                    )
                } else {
                    Text(
                        text = if (meta.description.isNotBlank()) meta.description else stringResource(id = R.string.click_to_add_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (meta.description.isNotBlank()) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable { isEditingDesc = true }.padding(8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        if (artist.albums.isNotEmpty()) {
            item {
                Text(stringResource(id = R.string.tab_albums), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onBackground)
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(artist.albums, key = { it.id }) { alb ->
                        Box(modifier = Modifier.width(160.dp)) { AlbumCard(alb, animatedVisibilityScope) { onAlbumSelect(alb) } }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        item { Text(stringResource(id = R.string.popular_tracks), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onBackground) }

        items(artist.tracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                isPlaying = isPlaying,
                isCurrentTrack = currentTrack?.id == track.id,
                albumArt = remember(track.albumId) { AudioRepository.getAlbumArtUri(track.albumId) },
                onClick = { onTrackSelect(track, artist.tracks) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onShare = { onShareTrack(track) }
            )
        }
    }
}