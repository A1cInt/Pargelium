package com.alcint.pargelium

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ArtistModel(val name: String, val tracks: List<AudioTrack>, val albums: List<AlbumModel>)

fun getPrimaryArtist(rawName: String, unknownStr: String): String {
    if (rawName.isBlank() || rawName.lowercase() == "<unknown>") return unknownStr
    val delimiters = arrayOf(" feat. ", " ft. ", " feat ", " ft ", " & ", " vs. ", " vs ", " x ", ",")
    var primary = rawName
    for (delim in delimiters) {
        val index = primary.indexOf(delim, ignoreCase = true)
        if (index > 0) primary = primary.substring(0, index)
    }
    return primary.trim()
}

@Composable
fun HeadlessPlaybackTracker(
    playerController: MediaController?,
    currentTrack: AudioTrack?,
    isPlaying: Boolean
) {
    var hasCountedPlay by remember(currentTrack) { mutableStateOf(false) }

    LaunchedEffect(isPlaying, currentTrack) {
        var lastSaveTime = System.currentTimeMillis()
        while (isPlaying) {
            playerController?.let {
                val pos = it.currentPosition
                if (!hasCountedPlay && pos > 40000 && currentTrack != null) {
                    PrefsManager.recordPlay(currentTrack.id)
                    hasCountedPlay = true
                }
                val now = System.currentTimeMillis()
                if (now - lastSaveTime > 3000) {
                    PrefsManager.saveLastPosition(pos)
                    lastSaveTime = now
                }
            }
            delay(1000)
        }
    }
}

@Composable
fun PlayerPositionProvider(
    playerController: MediaController?,
    isPlaying: Boolean,
    content: @Composable (currentPosition: Long) -> Unit
) {
    var pos by remember { mutableLongStateOf(playerController?.currentPosition ?: 0L) }
    LaunchedEffect(isPlaying, playerController) {
        while (isPlaying) {
            playerController?.let { pos = it.currentPosition }
            delay(500)
        }
    }
    content(pos)
}

@Composable
fun FastScrollThumb(
    itemCount: Int,
    state: LazyGridState,
    getLetter: (Int) -> String
) {
    if (itemCount <= 0) return
    val firstVisible by remember { derivedStateOf { state.firstVisibleItemIndex } }
    val safeVisibleIndex = firstVisible.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    val currentLetter = getLetter(safeVisibleIndex)

    var isDragging by remember { mutableStateOf(false) }
    var offsetY by remember { mutableFloatStateOf(0f) }
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .width(24.dp)
            .pointerInput(itemCount) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isDragging = true
                    offsetY = down.position.y

                    fun scrollToExact(y: Float) {
                        val fraction = (y / size.height).coerceIn(0f, 1f)
                        val exactIndex = fraction * (itemCount - 1).coerceAtLeast(0)
                        val index = exactIndex.toInt()

                        val itemHeight = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 300
                        val offset = ((exactIndex - index) * itemHeight).toInt()

                        scope.launch { state.scrollToItem(index, offset) }
                    }

                    scrollToExact(offsetY)

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            change.consume()
                            offsetY = change.position.y
                            scrollToExact(offsetY)
                        }
                    } while (event.changes.any { it.pressed })
                    isDragging = false
                }
            }
    ) {
        val trackHeight = constraints.maxHeight.toFloat()
        val thumbHeightPx = with(LocalDensity.current) { 40.dp.toPx() }

        val currentFraction = if (itemCount > 1) {
            val firstItem = state.layoutInfo.visibleItemsInfo.firstOrNull()
            val offsetFraction = if (firstItem != null && firstItem.size.height > 0) {
                -firstItem.offset.y.toFloat() / firstItem.size.height
            } else 0f
            (firstVisible + offsetFraction).coerceIn(0f, itemCount - 1f) / (itemCount - 1)
        } else 0f

        val targetThumbY = (currentFraction * (trackHeight - thumbHeightPx)).coerceIn(0f, trackHeight - thumbHeightPx)
        val thumbY by animateFloatAsState(
            targetValue = targetThumbY,
            animationSpec = if (isDragging) snap() else tween(50, easing = LinearOutSlowInEasing),
            label = "thumbY"
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(tween(200)) + scaleIn(tween(200)),
            exit = fadeOut(tween(200)) + scaleOut(tween(200)),
            modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(-80, offsetY.toInt() - 70) }
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
                    .offset { IntOffset(0, thumbY.toInt()) }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(onSecureRequest: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var themeMode by remember { mutableIntStateOf(PrefsManager.getThemeMode()) }
    val systemDark = isSystemInDarkTheme()
    var currentScreen by remember { mutableIntStateOf(0) }

    var showAdvancedSettings by remember { mutableStateOf(false) }

    val albumGridState = rememberLazyGridState()
    val artistGridState = rememberLazyGridState()

    var libraryTab by remember { mutableIntStateOf(0) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.TITLE) }

    var userName by remember { mutableStateOf(PrefsManager.getUserName()) }
    var avatarUri by remember { mutableStateOf(PrefsManager.getAvatarUri()) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var trackAddingToPlaylist by remember { mutableStateOf<AudioTrack?>(null) }
    var trackToShare by remember { mutableStateOf<AudioTrack?>(null) }
    var hasPermission by remember { mutableStateOf(false) }

    var allTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var allAlbums by remember { mutableStateOf<List<AlbumModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedAlbum by remember { mutableStateOf<AlbumModel?>(null) }
    var selectedArtist by remember { mutableStateOf<ArtistModel?>(null) }

    var playerController by remember { mutableStateOf<MediaController?>(null) }
    var currentTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var currentPlaylist by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var isPlaying by remember { mutableStateOf(false) }
    var trackDuration by remember { mutableStateOf(1L) }
    var trackMetadata by remember { mutableStateOf("") }
    var currentAlbumArt by remember { mutableStateOf<Uri?>(null) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }

    var lastPreviousClickTime by remember { mutableLongStateOf(0L) }
    val unknownArtistStr = stringResource(id = R.string.unknown_artist)

    LaunchedEffect(currentScreen) {
        if (currentScreen == 0) {
            userName = PrefsManager.getUserName()
            avatarUri = PrefsManager.getAvatarUri()
        }
    }

    LaunchedEffect(currentPlaylist) {
        if (currentPlaylist.isNotEmpty()) PrefsManager.saveLastQueue(currentPlaylist.map { it.id })
    }

    DisposableEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try { playerController = controllerFuture.get() } catch (e: Exception) {}
        }, MoreExecutors.directExecutor())
        onDispose { playerController?.release() }
    }

    DisposableEffect(playerController) {
        val player = playerController ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val trackId = mediaItem?.mediaId?.toLongOrNull()
                val found = allTracks.find { it.id == trackId } ?: currentPlaylist.find { it.id == trackId }
                if (found != null) {
                    currentTrack = found
                    PrefsManager.saveLastTrackId(found.id)
                }
                trackDuration = player.duration.coerceAtLeast(1L)
            }
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
                if (!isPlayingState) PrefsManager.saveLastPosition(player.currentPosition)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) trackDuration = player.duration.coerceAtLeast(1L)
            }
            override fun onRepeatModeChanged(mode: Int) { repeatMode = mode }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        repeatMode = player.repeatMode
        trackDuration = player.duration.coerceAtLeast(1L)

        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        if (currentId != null) {
            val found = allTracks.find { it.id == currentId }
            if (found != null) currentTrack = found
        }
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(playerController, allTracks) {
        val player = playerController ?: return@LaunchedEffect
        if (allTracks.isEmpty()) return@LaunchedEffect

        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        if (currentId != null) {
            if (currentTrack == null || currentTrack?.id != currentId) {
                val foundTrack = allTracks.find { it.id == currentId }
                if (foundTrack != null) {
                    currentTrack = foundTrack
                    trackDuration = player.duration.coerceAtLeast(1L)
                    isPlaying = player.isPlaying
                }
            }
        } else {
            val lastTrackId = PrefsManager.getLastTrackId()
            if (lastTrackId != -1L && currentTrack == null) {
                val lastQueueIds = PrefsManager.getLastQueue()
                val restoredQueue = lastQueueIds.mapNotNull { id -> allTracks.find { it.id == id } }

                val finalQueue = if (restoredQueue.isNotEmpty() && restoredQueue.any { it.id == lastTrackId }) {
                    restoredQueue
                } else {
                    val foundTrack = allTracks.find { it.id == lastTrackId }
                    if (foundTrack != null) listOf(foundTrack) else emptyList()
                }

                if (finalQueue.isNotEmpty()) {
                    val targetTrack = finalQueue.find { it.id == lastTrackId } ?: finalQueue.first()
                    currentTrack = targetTrack
                    val lastPos = PrefsManager.getLastPosition()
                    currentPlaylist = finalQueue
                    val mediaItems = finalQueue.map { createMediaItem(it) }
                    val startIndex = finalQueue.indexOf(targetTrack).coerceAtLeast(0)
                    player.setMediaItems(mediaItems, startIndex, lastPos)
                    player.prepare()
                }
            }
        }
    }

    LaunchedEffect(currentTrack) {
        if (currentTrack != null) {
            if (!currentTrack!!.coverUrl.isNullOrEmpty() && currentTrack!!.coverUrl != "null") {
                currentAlbumArt = Uri.parse(currentTrack!!.coverUrl)
                val sourceName = if (currentTrack!!.source == "local") "Онлайн Радио" else currentTrack!!.source.uppercase()
                trackMetadata = "$sourceName • Стриминг"
            } else if (currentTrack!!.albumId != 0L && currentTrack!!.albumId != -1L) {
                currentAlbumArt = AudioRepository.getAlbumArtUri(currentTrack!!.albumId)
                launch(Dispatchers.IO) {
                    val meta = AudioRepository.getTrackMetadata(context, currentTrack!!.uri)
                    withContext(Dispatchers.Main) { trackMetadata = meta }
                }
            } else {
                trackMetadata = context.getString(R.string.status_live_broadcast)
                currentAlbumArt = null
            }
        } else {
            currentAlbumArt = null
            trackMetadata = ""
        }
    }

    HeadlessPlaybackTracker(playerController, currentTrack, isPlaying)

    fun loadTracks(forceScan: Boolean = false) {
        scope.launch {
            isLoading = true
            if (forceScan) withContext(Dispatchers.IO) {
                val lock = java.util.concurrent.CountDownLatch(1)
                AudioRepository.forceScan(context) { lock.countDown() }
                lock.await()
            }
            val loadedTracks = withContext(Dispatchers.IO) { AudioRepository.getAudioTracks(context) }
            val loadedAlbums = withContext(Dispatchers.Default) { AudioRepository.processAlbums(loadedTracks) }
            allTracks = loadedTracks
            allAlbums = loadedAlbums
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] == true || permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        hasPermission = readGranted
        if (readGranted) loadTracks(true)
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else { permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE) }
        if (Build.VERSION.SDK_INT >= 31) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    fun playTrack(track: AudioTrack, playlistContext: List<AudioTrack>) {
        scope.launch {
            val isNetwork = track.source != "local" || track.uri.toString().startsWith("http")
            if (isNetwork) isLoading = true

            val readyTrack = try {
                track.copy(uri = StreamingManager.getPlayableUri(context, track))
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Стриминг: Сервер недоступен", Toast.LENGTH_SHORT).show() }
                return@launch
            } finally {
                if (isNetwork) isLoading = false
            }

            val player = playerController ?: return@launch
            if (currentTrack?.id == readyTrack.id && player.mediaItemCount > 0) {
                if (!player.isPlaying) player.play()
                return@launch
            }
            currentTrack = readyTrack
            PrefsManager.saveLastTrackId(readyTrack.id)

            val isSamePlaylist = currentPlaylist.size == playlistContext.size && currentPlaylist.map { it.id } == playlistContext.map { it.id }
            if (isSamePlaylist && player.mediaItemCount > 0) {
                val startIndex = playlistContext.indexOfFirst { it.id == readyTrack.id }.coerceAtLeast(0)
                player.seekTo(startIndex, 0)
                player.play()
            } else {
                currentPlaylist = playlistContext
                val mediaItems = playlistContext.map { if (it.id == readyTrack.id) createMediaItem(readyTrack) else createMediaItem(it) }
                val startIndex = playlistContext.indexOfFirst { it.id == readyTrack.id }.coerceAtLeast(0)
                player.setMediaItems(mediaItems, startIndex, 0)
                player.prepare()
                player.play()
            }
        }
    }

    fun playNext(track: AudioTrack) {
        scope.launch {
            val readyTrack = try { track.copy(uri = StreamingManager.getPlayableUri(context, track)) } catch (e: Exception) { track }
            val player = playerController ?: return@launch
            val index = player.currentMediaItemIndex + 1
            player.addMediaItem(index, createMediaItem(readyTrack))
            val newList = currentPlaylist.toMutableList()
            if (index <= newList.size) { newList.add(index, readyTrack); currentPlaylist = newList }
            withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.play_next_msg), Toast.LENGTH_SHORT).show() }
        }
    }

    fun addToQueue(track: AudioTrack) {
        scope.launch {
            val readyTrack = try { track.copy(uri = StreamingManager.getPlayableUri(context, track)) } catch (e: Exception) { track }
            val player = playerController ?: return@launch
            player.addMediaItem(createMediaItem(readyTrack))
            currentPlaylist = currentPlaylist + readyTrack
            withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.added_to_queue_msg), Toast.LENGTH_SHORT).show() }
        }
    }

    fun removeFromQueue(index: Int) {
        val player = playerController ?: return
        if (index in currentPlaylist.indices) {
            player.removeMediaItem(index)
            val newList = currentPlaylist.toMutableList()
            newList.removeAt(index)
            currentPlaylist = newList
        }
    }

    fun jumpToQueueItem(index: Int) { playerController?.seekTo(index, 0) }
    fun shuffleAll() { if (allTracks.isNotEmpty()) { val shuffled = allTracks.shuffled(); playTrack(shuffled[0], shuffled) } }
    fun togglePlayPause() { playerController?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun seekTo(position: Long) { playerController?.seekTo(position); PrefsManager.saveLastPosition(position) }

    fun skipNext() {
        val player = playerController ?: return
        if (player.repeatMode == Player.REPEAT_MODE_ONE) player.seekTo(0)
        else if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun skipPrevious() {
        val player = playerController ?: return
        val now = System.currentTimeMillis()
        if (now - lastPreviousClickTime <= 5000L || player.currentPosition < 3000L) {
            if (player.repeatMode == Player.REPEAT_MODE_ONE) player.seekTo(0)
            else if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
            else player.seekTo(0)
            lastPreviousClickTime = 0L
        } else {
            player.seekTo(0)
            lastPreviousClickTime = now
        }
    }

    fun toggleRepeat() {
        val player = playerController ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    val filteredAlbums by remember(allAlbums, searchQuery) {
        derivedStateOf {
            val list = if (searchQuery.isEmpty()) allAlbums else allAlbums.filter { it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true) }
            list.sortedBy { it.title.trim().lowercase() }
        }
    }

    val allArtists by remember(allTracks, allAlbums, unknownArtistStr) {
        derivedStateOf {
            allTracks.groupBy { getPrimaryArtist(it.artist, unknownArtistStr) }
                .map { (name, artistTracks) ->
                    ArtistModel(
                        name = name,
                        tracks = artistTracks.sortedBy { it.title },
                        albums = allAlbums.filter { getPrimaryArtist(it.artist, unknownArtistStr) == name }.sortedBy { it.title }
                    )
                }.sortedBy { it.name.trim().lowercase() }
        }
    }

    val filteredArtists by remember(allArtists, searchQuery) {
        derivedStateOf {
            val list = if (searchQuery.isEmpty()) allArtists else allArtists.filter { it.name.contains(searchQuery, ignoreCase = true) }
            list.sortedBy { it.name.trim().lowercase() }
        }
    }

    val filteredTracks by remember(allTracks, searchQuery, sortOption) {
        derivedStateOf {
            if (searchQuery.isEmpty()) emptyList() else {
                val list = allTracks.filter { it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true) }
                when (sortOption) {
                    SortOption.TITLE -> list.sortedBy { it.title }
                    SortOption.ARTIST -> list.sortedBy { it.artist }
                    SortOption.DURATION -> list.sortedBy { it.duration }
                }
            }
        }
    }

    val seedColor = rememberDominantColor(currentAlbumArt)
    val targetColor = if (seedColor != Color.Unspecified) seedColor else Color(0xFFD0BCFF)
    val targetScheme = rememberPargeliumScheme(targetColor, themeMode, systemDark)

    MaterialTheme(colorScheme = targetScheme) {
        SharedTransitionLayout {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val containerHeightPx = constraints.maxHeight.toFloat()
                    val hiddenOffsetPx = containerHeightPx + 500f

                    var isPlayerExpanded by remember { mutableStateOf(false) }
                    val playerOffsetY = remember { Animatable(10000f) }

                    LaunchedEffect(hiddenOffsetPx) {
                        if (!isPlayerExpanded) playerOffsetY.snapTo(hiddenOffsetPx)
                    }

                    fun snapPlayer(toExpanded: Boolean) {
                        isPlayerExpanded = toExpanded
                        scope.launch {
                            playerOffsetY.animateTo(
                                targetValue = if (toExpanded) 0f else hiddenOffsetPx,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 200f)
                            )
                        }
                    }

                    val handleArtistClick: (String) -> Unit = { rawArtistName ->
                        val primaryName = getPrimaryArtist(rawArtistName, unknownArtistStr)
                        val foundArtist = allArtists.find { it.name.equals(primaryName, ignoreCase = true) }
                        if (foundArtist != null) {
                            selectedArtist = foundArtist
                            selectedAlbum = null
                            currentScreen = 0
                            isSearchActive = false
                            snapPlayer(false)
                        } else {
                            Toast.makeText(context, context.getString(R.string.artist_not_found), Toast.LENGTH_SHORT).show()
                        }
                    }

                    BackHandler(enabled = isPlayerExpanded || selectedAlbum != null || selectedArtist != null || currentScreen == 5 || isSearchActive || trackToShare != null || showAdvancedSettings) {
                        when {
                            isPlayerExpanded -> snapPlayer(false)
                            trackToShare != null -> trackToShare = null
                            showAdvancedSettings -> showAdvancedSettings = false
                            selectedAlbum != null -> selectedAlbum = null
                            selectedArtist != null -> selectedArtist = null
                            currentScreen == 5 -> currentScreen = 0
                            isSearchActive -> { isSearchActive = false; searchQuery = "" }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {

                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(250, easing = LinearOutSlowInEasing)) togetherWith
                                        fadeOut(animationSpec = tween(250, easing = LinearOutSlowInEasing))
                            },
                            label = "TabTransition"
                        ) { screen ->
                            when (screen) {
                                0 -> {
                                    val currentDetailView = when {
                                        selectedAlbum != null -> 1
                                        selectedArtist != null -> 2
                                        else -> 0
                                    }

                                    AnimatedContent(
                                        targetState = currentDetailView,
                                        transitionSpec = {
                                            (fadeIn(animationSpec = tween(300)) + slideInVertically { it / 4 })
                                                .togetherWith(fadeOut(animationSpec = tween(200)))
                                        },
                                        label = "DetailSwitch"
                                    ) { detailState ->
                                        when (detailState) {
                                            1 -> {
                                                selectedAlbum?.let { album ->
                                                    AlbumDetailView(
                                                        album = album,
                                                        animatedVisibilityScope = this@AnimatedContent,
                                                        currentTrack = currentTrack,
                                                        isPlaying = isPlaying,
                                                        onBack = { selectedAlbum = null },
                                                        onTrackSelect = { playTrack(it, album.tracks) },
                                                        onPlayPause = { togglePlayPause() },
                                                        onAddToQueue = { addToQueue(it) },
                                                        onPlayNext = { playNext(it) },
                                                        onAddToPlaylist = { track -> trackAddingToPlaylist = track; showAddToPlaylistSheet = true },
                                                        onShareTrack = { trackToShare = it },
                                                        onArtistClick = handleArtistClick
                                                    )
                                                }
                                            }
                                            2 -> {
                                                selectedArtist?.let { artist ->
                                                    ArtistProfileView(artist = artist, animatedVisibilityScope = this@AnimatedContent, currentTrack = currentTrack, isPlaying = isPlaying, onBack = { selectedArtist = null }, onTrackSelect = { t, list -> playTrack(t, list) }, onAlbumSelect = { selectedAlbum = it }, onPlayPause = { togglePlayPause() }, onAddToQueue = { addToQueue(it) }, onPlayNext = { playNext(it) }, onAddToPlaylist = { track -> trackAddingToPlaylist = track; showAddToPlaylistSheet = true }, onShareTrack = { trackToShare = it })
                                                }
                                            }
                                            0 -> {
                                                Column(modifier = Modifier.fillMaxSize()) {
                                                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

                                                    AnimatedContent(
                                                        targetState = isSearchActive,
                                                        transitionSpec = {
                                                            fadeIn(tween(300)) togetherWith fadeOut(tween(300)) using SizeTransform(clip = false)
                                                        },
                                                        label = "SearchHeaderAnimation"
                                                    ) { active ->
                                                        if (active) {
                                                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                IconButton(onClick = { isSearchActive = false; searchQuery = ""; focusManager.clearFocus() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground) }
                                                                OutlinedTextField(
                                                                    value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text(stringResource(id = R.string.search_hint)) }, modifier = Modifier.weight(1f).height(56.dp), singleLine = true, shape = RoundedCornerShape(24.dp),
                                                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
                                                                    trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } },
                                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                                                                )
                                                            }
                                                        } else {
                                                            Column {
                                                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .clip(RoundedCornerShape(32.dp))
                                                                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { currentScreen = 5 }
                                                                            .padding(end = 8.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                                                            if (avatarUri != null) AsyncImage(model = ImageRequest.Builder(context).data(avatarUri).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                                            else Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                        }
                                                                        Spacer(Modifier.width(12.dp))
                                                                        Text(text = userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                    }
                                                                    IconButton(onClick = { isSearchActive = true }) { Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.onBackground) }
                                                                }
                                                                TabRow(selectedTabIndex = libraryTab, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary, divider = {}) {
                                                                    Tab(selected = libraryTab == 0, onClick = { libraryTab = 0 }) {
                                                                        Text(stringResource(id = R.string.tab_albums), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                                                                    }
                                                                    Tab(selected = libraryTab == 1, onClick = { libraryTab = 1 }) {
                                                                        Text(stringResource(id = R.string.tab_artists), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    if (isSearchActive && searchQuery.isNotEmpty()) {
                                                        if (filteredAlbums.isEmpty() && filteredTracks.isEmpty() && filteredArtists.isEmpty() && !isLoading) {
                                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(id = R.string.search_no_results)) }
                                                        } else {
                                                            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 240.dp)) {
                                                                if (filteredArtists.isNotEmpty()) {
                                                                    item { Text(stringResource(id = R.string.tab_artists), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                                                                    item {
                                                                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                                            items(filteredArtists, key = { it.name }) { artist -> Box(modifier = Modifier.width(160.dp)) { ArtistCard(artist) { selectedArtist = artist; isSearchActive = false } } }
                                                                        }
                                                                    }
                                                                }
                                                                if (filteredAlbums.isNotEmpty()) {
                                                                    item { Text(stringResource(id = R.string.tab_albums), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                                                                    item {
                                                                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                                            items(filteredAlbums, key = { it.id }) { alb -> Box(modifier = Modifier.width(160.dp)) { AlbumCard(alb, this@AnimatedContent) { selectedAlbum = alb } } }
                                                                        }
                                                                    }
                                                                }
                                                                if (filteredTracks.isNotEmpty()) {
                                                                    item { Text(stringResource(id = R.string.tab_tracks), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                                                                    items(filteredTracks, key = { it.id }) { track -> TrackRow(track = track, isPlaying = isPlaying, isCurrentTrack = currentTrack?.id == track.id, albumArt = remember(track.albumId) { AudioRepository.getAlbumArtUri(track.albumId) }, onClick = { playTrack(track, filteredTracks) }, onPlayNext = { playNext(track) }, onAddToQueue = { addToQueue(track) }, onAddToPlaylist = { trackAddingToPlaylist = track; showAddToPlaylistSheet = true }, onShare = { trackToShare = track }) }
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        if (libraryTab == 0) {
                                                            if (filteredAlbums.isEmpty() && !isLoading) {
                                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.music_not_found)) }
                                                            } else {
                                                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                                                    LazyVerticalGrid(
                                                                        columns = GridCells.Adaptive(minSize = 160.dp),
                                                                        state = albumGridState,
                                                                        contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 240.dp),
                                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                        modifier = Modifier.fillMaxSize()
                                                                    ) {
                                                                        gridItems(filteredAlbums, key = { it.id }) { alb -> AlbumCard(alb, this@AnimatedContent) { selectedAlbum = alb } }
                                                                    }

                                                                    if (filteredAlbums.isNotEmpty()) {
                                                                        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(bottom = 240.dp, top = 8.dp)) {
                                                                            FastScrollThumb(
                                                                                itemCount = filteredAlbums.size,
                                                                                state = albumGridState,
                                                                                getLetter = { index -> filteredAlbums.getOrNull(index)?.title?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "" }
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            if (filteredArtists.isEmpty() && !isLoading) {
                                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(id = R.string.no_artists)) }
                                                            } else {
                                                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                                                    LazyVerticalGrid(
                                                                        columns = GridCells.Adaptive(minSize = 160.dp),
                                                                        state = artistGridState,
                                                                        contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 240.dp),
                                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                        modifier = Modifier.fillMaxSize()
                                                                    ) {
                                                                        gridItems(filteredArtists, key = { it.name }) { artist -> ArtistCard(artist) { selectedArtist = artist } }
                                                                    }

                                                                    if (filteredArtists.isNotEmpty()) {
                                                                        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(bottom = 240.dp, top = 8.dp)) {
                                                                            FastScrollThumb(
                                                                                itemCount = filteredArtists.size,
                                                                                state = artistGridState,
                                                                                getLetter = { index -> filteredArtists.getOrNull(index)?.name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "" }
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                1 -> RadioScreen(onStationSelect = { playTrack(it, listOf(it)) })
                                2 -> PlaylistScreen(allTracks = allTracks, seedColor = targetColor, onPlayTrack = { track, list -> playTrack(track, list) }, onAddToQueue = { playNext(it) })
                                3 -> EqualizerScreen(seedColor = targetColor)
                                4 -> {
                                    if (showAdvancedSettings) {
                                        AdvancedSettingsScreen(onBackClick = { showAdvancedSettings = false })
                                    } else {
                                        SettingsScreen(
                                            currentThemeMode = themeMode,
                                            onThemeChanged = { newMode -> themeMode = newMode; PrefsManager.saveThemeMode(newMode) },
                                            onSecureChanged = onSecureRequest,
                                            onFossWearChanged = { Toast.makeText(context, context.getString(R.string.watch_settings_saved), Toast.LENGTH_SHORT).show() },
                                            onNavigateToAdvanced = { showAdvancedSettings = true }
                                        )
                                    }
                                }
                                5 -> ProfileScreen(
                                    allTracks = allTracks,
                                    seedColor = targetColor,
                                    onBack = { currentScreen = 0 },
                                    onArtistClick = handleArtistClick
                                )
                            }
                        }

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)).clickable(enabled = false) { }, contentAlignment = Alignment.Center) { MorphingPillIndicator() }
                        }

                        Column(modifier = Modifier.align(Alignment.BottomCenter).zIndex(0f)) {
                            if (currentScreen == 0 && !isSearchActive && selectedAlbum == null && selectedArtist == null) {
                                FloatingActionRow(
                                    onRefresh = { loadTracks(true) },
                                    onShuffleAll = { shuffleAll() })
                            }

                            if (currentTrack != null) {
                                PlayerPositionProvider(playerController, isPlaying) { currentPos ->
                                    MiniPlayer(
                                        track = currentTrack!!,
                                        isPlaying = isPlaying,
                                        progress = if (trackDuration > 0) currentPos.toFloat() / trackDuration else 0f,
                                        albumArt = currentAlbumArt,
                                        onPlayPause = { togglePlayPause() },
                                        onSkipNext = { skipNext() },
                                        onClick = { snapPlayer(true) }
                                    )
                                }
                            }

                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val navColors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                NavigationBarItem(selected = currentScreen == 0, onClick = { currentScreen = 0; selectedAlbum = null; selectedArtist = null }, icon = { Icon(painterResource(id = R.drawable.ic_action_key), null) }, label = { Text(stringResource(R.string.nav_library)) }, colors = navColors)
                                NavigationBarItem(selected = currentScreen == 1, onClick = { currentScreen = 1 }, icon = { Icon(painterResource(id = R.drawable.ic_satellite_alt), null) }, label = { Text(stringResource(R.string.nav_radio)) }, colors = navColors)

                                if (PrefsManager.getFeaturePlaylists()) {
                                    NavigationBarItem(selected = currentScreen == 2, onClick = { currentScreen = 2 }, icon = { Icon(painterResource(id = R.drawable.ic_package_2), null) }, label = { Text(stringResource(id = R.string.nav_playlists)) }, colors = navColors)
                                }

                                NavigationBarItem(selected = currentScreen == 3, onClick = { currentScreen = 3 }, icon = { Icon(painterResource(id = R.drawable.ic_instant_mix), null) }, label = { Text(stringResource(R.string.nav_eq)) }, colors = navColors)
                                NavigationBarItem(selected = currentScreen == 4, onClick = { currentScreen = 4 }, icon = { Icon(painterResource(id = R.drawable.ic_settings_heart), null) }, label = { Text(stringResource(R.string.nav_settings)) }, colors = navColors)
                            }
                        }
                        if (currentTrack != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationY = playerOffsetY.value }
                                    .shadow(16.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .zIndex(2f)
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onDragEnd = {
                                                val shouldExpand = playerOffsetY.value < (containerHeightPx * 0.20f)
                                                snapPlayer(shouldExpand)
                                            }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            val newOffset = (playerOffsetY.value + dragAmount).coerceIn(0f, hiddenOffsetPx)
                                            scope.launch { playerOffsetY.snapTo(newOffset) }
                                        }
                                    }
                            ) {
                                PlayerPositionProvider(playerController, isPlaying) { currentPos ->
                                    FullPlayerScreen(
                                        track = currentTrack!!,
                                        isPlaying = isPlaying,
                                        currentPosition = currentPos,
                                        duration = trackDuration,
                                        metadata = trackMetadata,
                                        seedColor = targetColor,
                                        repeatMode = repeatMode,
                                        playlist = currentPlaylist,
                                        currentQueueIndex = if (playerController != null) playerController!!.currentMediaItemIndex else -1,
                                        onPlayPause = { togglePlayPause() },
                                        onSeek = { seekTo(it) },
                                        onSkipNext = { skipNext() },
                                        onSkipPrevious = { skipPrevious() },
                                        onToggleRepeat = { toggleRepeat() },
                                        onQueueItemClick = { jumpToQueueItem(it) },
                                        onRemoveFromQueue = { removeFromQueue(it) },
                                        onCollapse = { snapPlayer(false) },
                                        currentTab = currentScreen,
                                        onTabSelected = { newTab -> currentScreen = newTab; snapPlayer(false) },
                                        onArtistClick = handleArtistClick
                                    )
                                }
                            }
                        }

                        if (trackToShare != null) { ShareTrackSheet(track = trackToShare!!, onDismiss = { trackToShare = null }) }

                        if (showAddToPlaylistSheet && trackAddingToPlaylist != null) {
                            val playlists = remember { PrefsManager.getPlaylists() }
                            ModalBottomSheet(onDismissRequest = { showAddToPlaylistSheet = false }, modifier = Modifier.zIndex(4f)) {
                                Column(Modifier.padding(bottom = 32.dp)) {
                                    Text(stringResource(id = R.string.add_to_playlist), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                                    if (playlists.isEmpty()) Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(id = R.string.no_playlists), color = MaterialTheme.colorScheme.error) }
                                    LazyColumn {
                                        items(playlists) { playlist ->
                                            ListItem(
                                                headlineContent = { Text(playlist.name) },
                                                supportingContent = { Text(pluralStringResource(id = R.plurals.tracks_count, count = playlist.trackIds.size, playlist.trackIds.size)) },
                                                leadingContent = { Icon(Icons.Default.Folder, null) },
                                                modifier = Modifier.clickable {
                                                    PrefsManager.addTrackToPlaylist(playlist.id, trackAddingToPlaylist!!.id)
                                                    Toast.makeText(context, context.getString(R.string.added_to_playlist_msg, playlist.name), Toast.LENGTH_SHORT).show()
                                                    showAddToPlaylistSheet = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
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

fun createMediaItem(audioTrack: AudioTrack): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(audioTrack.title)
        .setArtist(audioTrack.artist)
        .setAlbumTitle(audioTrack.album)
        .build()

    return MediaItem.Builder()
        .setUri(audioTrack.uri)
        .setMediaId(audioTrack.id.toString())
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(audioTrack.uri).build())
        .setMediaMetadata(metadata)
        .build()
}