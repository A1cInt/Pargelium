@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class
)
package com.alcint.pargelium.library

import android.Manifest
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.alcint.pargelium.*
import com.alcint.pargelium.R
import com.alcint.pargelium.player.FullPlayerScreen
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.alcint.pargelium.settings.SettingsScreen

@Composable
fun LibraryScreen(onSecureRequest: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val backdrop = rememberLayerBackdrop()

    var themeMode by remember { mutableIntStateOf(PrefsManager.getThemeMode()) }
    val systemDark = isSystemInDarkTheme()

    val hasPlaylists = PrefsManager.getFeaturePlaylists()
    val tabCount = if (hasPlaylists) 5 else 4
    val pagerState = rememberPagerState { tabCount }

    var isProfileOpen by remember { mutableStateOf(false) }
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
    var isRefreshing by remember { mutableStateOf(false) }

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

    LaunchedEffect(isProfileOpen) {
        if (!isProfileOpen) {
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

        onDispose {
            controllerFuture.cancel(true)
            playerController?.release()
        }
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

                val currentDuration = player.duration
                if (currentDuration != C.TIME_UNSET) trackDuration = currentDuration.coerceAtLeast(1L)
            }
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
                if (!isPlayingState) PrefsManager.saveLastPosition(player.currentPosition)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val currentDuration = player.duration
                    if (currentDuration != C.TIME_UNSET) trackDuration = currentDuration.coerceAtLeast(1L)
                }
            }
            override fun onRepeatModeChanged(mode: Int) { repeatMode = mode }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        repeatMode = player.repeatMode

        val currentDuration = player.duration
        if (currentDuration != C.TIME_UNSET) trackDuration = currentDuration.coerceAtLeast(1L)

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
                    val currentDuration = player.duration
                    if (currentDuration != C.TIME_UNSET) trackDuration = currentDuration.coerceAtLeast(1L)
                    isPlaying = player.isPlaying
                }
            }
        } else {
            val lastTrackId = PrefsManager.getLastTrackId()
            if (lastTrackId != -1L && currentTrack == null) {
                val lastQueueIds = PrefsManager.getLastQueue()
                val restoredQueue = lastQueueIds.mapNotNull { id -> allTracks.find { it.id == id } }

                val finalQueue = if (restoredQueue.isNotEmpty() && restoredQueue.any { it.id == lastTrackId }) restoredQueue
                else {
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
            if (forceScan) isRefreshing = true else isLoading = true
            if (forceScan) withContext(Dispatchers.IO) {
                val lock = java.util.concurrent.CountDownLatch(1)
                AudioRepository.forceScan(context) { lock.countDown() }
                lock.await()
            }
            val loadedTracks = withContext(Dispatchers.IO) { AudioRepository.getAudioTracks(context, forceScan) }
            val loadedAlbums = withContext(Dispatchers.Default) { AudioRepository.processAlbums(loadedTracks) }
            allTracks = loadedTracks
            allAlbums = loadedAlbums
            isLoading = false
            isRefreshing = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] == true || permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        hasPermission = readGranted
        if (readGranted) loadTracks(false)
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
            if (searchQuery.isEmpty()) allAlbums
            else allAlbums.filter { it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true) }.sortedBy { it.title.trim().lowercase() }
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
            if (searchQuery.isEmpty()) allArtists
            else allArtists.filter { it.name.contains(searchQuery, ignoreCase = true) }.sortedBy { it.name.trim().lowercase() }
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

    val navItems = remember(hasPlaylists) {
        listOfNotNull(
            NavItem(R.drawable.ic_action_key, R.string.nav_library),
            NavItem(R.drawable.ic_satellite_alt, R.string.nav_radio),
            if (hasPlaylists) NavItem(R.drawable.ic_package_2, R.string.nav_playlists) else null,
            NavItem(R.drawable.ic_instant_mix, R.string.nav_eq),
            NavItem(R.drawable.ic_settings_heart, R.string.nav_settings)
        )
    }

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

                    val updatedAllArtists by rememberUpdatedState(allArtists)

                    val handleArtistClick: (String) -> Unit = remember {
                        { rawArtistName ->
                            val primaryName = getPrimaryArtist(rawArtistName, unknownArtistStr)
                            val foundArtist = updatedAllArtists.find { it.name.equals(primaryName, ignoreCase = true) }
                            if (foundArtist != null) {
                                selectedArtist = foundArtist
                                selectedAlbum = null
                                isProfileOpen = false
                                isSearchActive = false
                                scope.launch { pagerState.scrollToPage(0) }
                                snapPlayer(false)
                            } else {
                                Toast.makeText(context, context.getString(R.string.artist_not_found), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    BackHandler(enabled = isPlayerExpanded || selectedAlbum != null || selectedArtist != null || isProfileOpen || isSearchActive || trackToShare != null || showAdvancedSettings) {
                        when {
                            isPlayerExpanded -> snapPlayer(false)
                            trackToShare != null -> trackToShare = null
                            showAdvancedSettings -> showAdvancedSettings = false
                            selectedAlbum != null -> selectedAlbum = null
                            selectedArtist != null -> selectedArtist = null
                            isProfileOpen -> isProfileOpen = false
                            isSearchActive -> { isSearchActive = false; searchQuery = "" }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .layerBackdrop(backdrop)
                        ) {
                            if (isProfileOpen) {
                                ProfileScreen(
                                    allTracks = allTracks, seedColor = targetColor,
                                    onBack = remember {{ isProfileOpen = false }},
                                    onArtistClick = handleArtistClick
                                )
                            } else {
                                HorizontalPager(
                                    state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1
                                ) { page ->
                                    val currentScreenIndex = if (!hasPlaylists && page >= 2) page + 1 else page

                                    when (currentScreenIndex) {
                                        0 -> {
                                            val currentDetailView = when {
                                                selectedAlbum != null -> 1
                                                selectedArtist != null -> 2
                                                else -> 0
                                            }

                                            AnimatedContent(
                                                targetState = currentDetailView,
                                                transitionSpec = { (fadeIn(animationSpec = tween(300)) + slideInVertically { it / 4 }).togetherWith(fadeOut(animationSpec = tween(200))) },
                                                label = "DetailSwitch"
                                            ) { detailState ->
                                                when (detailState) {
                                                    1 -> {
                                                        selectedAlbum?.let { album ->
                                                            AlbumDetailView(
                                                                album = album, animatedVisibilityScope = this@AnimatedContent, currentTrack = currentTrack,
                                                                isPlaying = isPlaying, onBack = remember {{ selectedAlbum = null }}, onTrackSelect = remember {{ track -> playTrack(track, album.tracks) }},
                                                                onPlayPause = remember {{ togglePlayPause() }}, onAddToQueue = remember {{ addToQueue(it) }}, onPlayNext = remember {{ playNext(it) }},
                                                                onAddToPlaylist = remember {{ track -> trackAddingToPlaylist = track; showAddToPlaylistSheet = true }},
                                                                onShareTrack = remember {{ trackToShare = it }}, onArtistClick = handleArtistClick
                                                            )
                                                        }
                                                    }
                                                    2 -> {
                                                        selectedArtist?.let { artist ->
                                                            ArtistProfileView(
                                                                artist = artist, animatedVisibilityScope = this@AnimatedContent, currentTrack = currentTrack,
                                                                isPlaying = isPlaying, onBack = remember {{ selectedArtist = null }}, onTrackSelect = remember {{ t, list -> playTrack(t, list) }},
                                                                onAlbumSelect = remember {{ selectedAlbum = it }}, onPlayPause = remember {{ togglePlayPause() }},
                                                                onAddToQueue = remember {{ addToQueue(it) }}, onPlayNext = remember {{ playNext(it) }},
                                                                onAddToPlaylist = remember {{ track -> trackAddingToPlaylist = track; showAddToPlaylistSheet = true }},
                                                                onShareTrack = remember {{ trackToShare = it }}
                                                            )
                                                        }
                                                    }
                                                    0 -> {
                                                        LibraryDefaultView(
                                                            isRefreshing = isRefreshing, isLoading = isLoading, onRefresh = remember {{ loadTracks(true) }},
                                                            isSearchActive = isSearchActive, searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it },
                                                            onSearchClose = remember {{ isSearchActive = false; searchQuery = ""; focusManager.clearFocus() }},
                                                            onSearchOpen = remember {{ isSearchActive = true }}, userName = userName, avatarUri = avatarUri,
                                                            onProfileClick = remember {{ isProfileOpen = true }}, libraryTab = libraryTab, onTabSelected = { libraryTab = it },
                                                            filteredAlbums = filteredAlbums, filteredArtists = filteredArtists, filteredTracks = filteredTracks,
                                                            albumGridState = albumGridState, artistGridState = artistGridState,
                                                            onAlbumClick = remember {{ selectedAlbum = it }}, onArtistClick = remember {{ selectedArtist = it }},
                                                            currentTrack = currentTrack, isPlaying = isPlaying,
                                                            onTrackClick = remember {{ t, list -> playTrack(t, list) }}, onPlayNext = remember {{ playNext(it) }},
                                                            onAddToQueue = remember {{ addToQueue(it) }}, onAddToPlaylist = remember {{ trackAddingToPlaylist = it; showAddToPlaylistSheet = true }},
                                                            onShare = remember {{ trackToShare = it }}, animatedVisibilityScope = this@AnimatedContent
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        1 -> RadioScreen(onStationSelect = remember {{ playTrack(it, listOf(it)) }})
                                        2 -> PlaylistScreen(allTracks = allTracks, seedColor = targetColor, onPlayTrack = remember {{ track, list -> playTrack(track, list) }}, onAddToQueue = remember {{ track -> playNext(track) }})
                                        3 -> EqualizerScreen(seedColor = targetColor)
                                        4 -> {
                                            if (showAdvancedSettings) {
                                                AdvancedSettingsScreen(onBackClick = remember {{ showAdvancedSettings = false }})
                                            } else {
                                                SettingsScreen(
                                                    currentThemeMode = themeMode,
                                                    onThemeChanged = { newMode -> themeMode = newMode; PrefsManager.saveThemeMode(newMode) },
                                                    onSecureChanged = onSecureRequest,
                                                    onFossWearChanged = { Toast.makeText(context, context.getString(R.string.watch_settings_saved), Toast.LENGTH_SHORT).show() },
                                                    onNavigateToAdvanced = remember {{ showAdvancedSettings = true }}
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (isLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                                        .clickable(enabled = false) { },
                                    contentAlignment = Alignment.Center
                                ) { MorphingPillIndicator() }
                            }
                        }

                        // ПАНЕЛЬ ПЛЕЕРА И НАВБАР
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 12.dp)
                                .zIndex(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (pagerState.currentPage == 0 && !isSearchActive && selectedAlbum == null && selectedArtist == null && !isProfileOpen) {
                                FloatingActionRow(onRefresh = remember {{ loadTracks(true) }}, onShuffleAll = remember {{ shuffleAll() }})
                            }

                            // 1. МИНИ-ПЛЕЕР
                            if (currentTrack != null) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    PlayerPositionProvider(playerController, isPlaying) { currentPosProvider ->
                                        val currentPos = currentPosProvider()
                                        val calculatedProgress = if (trackDuration > 0) currentPos.toFloat() / trackDuration else 0f

                                        MiniPlayer(
                                            track = currentTrack!!,
                                            isPlaying = isPlaying,
                                            progress = calculatedProgress,
                                            albumArt = currentAlbumArt,
                                            backdrop = backdrop,
                                            onPlayPause = remember {{ togglePlayPause() }},
                                            onSkipNext = remember {{ skipNext() }},
                                            onClick = remember {{ snapPlayer(true) }}
                                        )
                                    }
                                }
                            }

                            // 2. НАВБАР
                            LibraryBottomNav(
                                pagerState = pagerState,
                                navItems = navItems,
                                backdrop = backdrop,
                                onTabSelected = { index ->
                                    selectedAlbum = null
                                    selectedArtist = null
                                    isProfileOpen = false
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                            )
                        }

                        if (currentTrack != null) {
                            FullPlayerDraggableWrapper(
                                playerOffsetY = playerOffsetY, containerHeightPx = containerHeightPx, hiddenOffsetPx = hiddenOffsetPx, snapPlayer = { snapPlayer(it) }
                            ) {
                                PlayerPositionProvider(playerController, isPlaying) { currentPosProvider ->
                                    FullPlayerScreen(
                                        track = currentTrack!!, isPlaying = isPlaying, currentPosition = currentPosProvider(), duration = trackDuration, metadata = trackMetadata,
                                        seedColor = targetColor, repeatMode = repeatMode, playlist = currentPlaylist, currentQueueIndex = if (playerController != null) playerController!!.currentMediaItemIndex else -1,
                                        onPlayPause = remember {{ togglePlayPause() }}, onSeek = remember {{ pos: Long -> seekTo(pos) }}, onSkipNext = remember {{ skipNext() }},
                                        onSkipPrevious = remember {{ skipPrevious() }}, onToggleRepeat = remember {{ toggleRepeat() }}, onQueueItemClick = remember {{ index: Int -> jumpToQueueItem(index) }},
                                        onRemoveFromQueue = remember {{ index: Int -> removeFromQueue(index) }}, onCollapse = remember {{ snapPlayer(false) }}, currentTab = pagerState.currentPage,
                                        onTabSelected = remember {{ newTab: Int -> scope.launch { pagerState.scrollToPage(newTab) }; snapPlayer(false) }}, onArtistClick = handleArtistClick
                                    )
                                }
                            }
                        }

                        if (trackToShare != null) { ShareTrackSheet(track = trackToShare!!, onDismiss = { trackToShare = null }) }
                        if (showAddToPlaylistSheet && trackAddingToPlaylist != null) {
                            AddToPlaylistSheet(trackAddingToPlaylist = trackAddingToPlaylist!!, onDismiss = { showAddToPlaylistSheet = false })
                        }
                    }
                }
            }
        }
    }
}