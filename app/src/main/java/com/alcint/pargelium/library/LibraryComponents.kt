@file:OptIn(
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.alcint.pargelium.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alcint.pargelium.AlbumCard
import com.alcint.pargelium.AlbumModel
import com.alcint.pargelium.AudioRepository
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.R
import com.alcint.pargelium.TrackRow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FastScrollThumb(
    itemCount: Int,
    state: LazyGridState,
    getLetter: (Int) -> String
) {
    if (itemCount <= 4) return

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thumbHeightPx = remember(density) { with(density) { 44.dp.toPx() } }

    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    var lastHapticLetter by remember { mutableStateOf("") }
    var lastScrolledIndex by remember { mutableIntStateOf(-1) }

    val isScrolling by remember { derivedStateOf { state.isScrollInProgress } }
    val showThumb = isScrolling || isDragging

    val thumbAlpha by animateFloatAsState(
        targetValue = if (showThumb) 1f else 0f,
        animationSpec = tween(durationMillis = 250, delayMillis = if (showThumb) 0 else 900),
        label = "thumbAlpha"
    )

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 14.dp else 5.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "thumbWidth"
    )

    val currentLetter by remember(itemCount) {
        derivedStateOf {
            val idx = if (isDragging && trackHeightPx > 1f) {
                val fraction = (dragOffsetY / trackHeightPx).coerceIn(0f, 1f)
                (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
            } else {
                state.firstVisibleItemIndex.coerceIn(0, itemCount - 1)
            }
            getLetter(idx)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .onSizeChanged { trackHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(itemCount) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isDragging = true
                    dragOffsetY = down.position.y
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    fun scrollToY(y: Float) {
                        val fraction = (y / trackHeightPx).coerceIn(0f, 1f)
                        val targetIndex = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                        if (targetIndex != lastScrolledIndex) {
                            lastScrolledIndex = targetIndex
                            val letter = getLetter(targetIndex)
                            if (letter != lastHapticLetter && letter.isNotEmpty()) {
                                lastHapticLetter = letter
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            scope.launch { state.scrollToItem(targetIndex) }
                        }
                    }

                    scrollToY(dragOffsetY)

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            change.consume()
                            dragOffsetY = change.position.y
                            scrollToY(dragOffsetY)
                        }
                    } while (event.changes.any { it.pressed })

                    isDragging = false
                    lastScrolledIndex = -1
                }
            }
    ) {
        AnimatedVisibility(
            visible = isDragging && currentLetter.isNotEmpty(),
            enter = fadeIn(tween(150)) + scaleIn(spring(dampingRatio = 0.65f, stiffness = 400f)),
            exit = fadeOut(tween(150)) + scaleOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val safeY = (dragOffsetY - thumbHeightPx * 1.4f)
                        .coerceIn(0f, (trackHeightPx - thumbHeightPx * 2f).coerceAtLeast(0f))
                    IntOffset(-76, safeY.roundToInt())
                }
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 28.dp, bottomEnd = 6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp,
                modifier = Modifier.size(58.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = currentLetter,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        if (thumbAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .offset {
                        val maxThumbY = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val targetY = if (isDragging) {
                            dragOffsetY.coerceIn(0f, maxThumbY)
                        } else {
                            val currentFraction = state.firstVisibleItemIndex.toFloat() / (itemCount - 1).coerceAtLeast(1)
                            (currentFraction * maxThumbY).coerceIn(0f, maxThumbY)
                        }
                        IntOffset(0, targetY.roundToInt())
                    }
                    .width(thumbWidth)
                    .height(44.dp)
                    .graphicsLayer { alpha = thumbAlpha }
                    .clip(CircleShape)
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
            )
        }
    }
}

/**
 * Оптимизированная экспрессивная карточка артиста:
 * Не читает SharedPreferences в UI-потоке! Использует предзагруженный URI или обложку первого альбома.
 */
@Composable
fun ArtistCard(
    artist: ArtistModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val coverData = remember(artist.name, artist.customAvatarUri, artist.fallbackCoverUri) {
        resolveArtistCoverUri(artist)
    }
    val initial = remember(artist.name) {
        artist.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "♪"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    )
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (coverData != null) {
                    AsyncImage(
                        model = remember(coverData) {
                            ImageRequest.Builder(context)
                                .data(coverData)
                                .size(320)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = pluralStringResource(
                            id = R.plurals.tracks_count,
                            count = artist.tracks.size,
                            artist.tracks.size
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

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
    var descInput by remember(meta.description) { mutableStateOf(meta.description) }

    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            val newMeta = meta.copy(avatarUri = uri.toString())
            PrefsManager.saveArtistMetadata(artist.name, newMeta)
            meta = newMeta
        }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            val newMeta = meta.copy(bannerUri = uri.toString())
            PrefsManager.saveArtistMetadata(artist.name, newMeta)
            meta = newMeta
        }
    }

    val fallbackCover = remember(artist) { resolveArtistCoverUri(artist) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 240.dp)
    ) {
        item(key = "artist_hero", contentType = "hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
            ) {
                val bannerModel = meta.bannerUri ?: fallbackCover
                if (bannerModel != null) {
                    AsyncImage(
                        model = remember(bannerModel) {
                            ImageRequest.Builder(context)
                                .data(bannerModel)
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
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )

                FilledTonalIconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 42.dp, start = 16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 42.dp)
                        .size(136.dp)
                        .shadow(20.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .clickable {
                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val avatarModel = meta.avatarUri ?: fallbackCover
                    if (avatarModel != null) {
                        AsyncImage(
                            model = remember(avatarModel) {
                                ImageRequest.Builder(context)
                                    .data(avatarModel)
                                    .size(512)
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
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "artist_bio_and_actions", contentType = "bio") {
            Spacer(modifier = Modifier.height(52.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Album, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "${artist.albums.size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = pluralStringResource(id = R.plurals.tracks_count, count = artist.tracks.size, artist.tracks.size),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (artist.tracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onTrackSelect(artist.tracks.first(), artist.tracks) },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.album_action_play),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                val shuffled = artist.tracks.shuffled()
                                onTrackSelect(shuffled.first(), shuffled)
                            },
                            modifier = Modifier.height(50.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.album_action_shuffle),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (isEditingDesc) {
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        label = { Text(stringResource(id = R.string.description_label)) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val newMeta = meta.copy(description = descInput)
                                PrefsManager.saveArtistMetadata(artist.name, newMeta)
                                meta = newMeta
                                isEditingDesc = false
                            }) {
                                Icon(Icons.Rounded.Check, contentDescription = "Save")
                            }
                        }
                    )
                } else {
                    Surface(
                        onClick = { isEditingDesc = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (meta.description.isNotBlank()) meta.description else stringResource(id = R.string.click_to_add_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (meta.description.isNotBlank()) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (artist.albums.isNotEmpty()) {
            item(key = "artist_albums_section", contentType = "albums_row") {
                Text(
                    text = stringResource(id = R.string.tab_albums),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(artist.albums, key = { it.id }, contentType = { "album_card" }) { alb ->
                        Box(modifier = Modifier.width(160.dp)) {
                            AlbumCard(alb, animatedVisibilityScope) { onAlbumSelect(alb) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        item(key = "artist_tracks_header", contentType = "header") {
            Text(
                text = stringResource(id = R.string.popular_tracks),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        items(
            items = artist.tracks,
            key = { it.id },
            contentType = { "track_row" }
        ) { track ->
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