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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.alcint.pargelium.AdvancedSettingsScreen
import com.alcint.pargelium.AlbumModel
import com.alcint.pargelium.AudioRepository
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.EqualizerScreen
import com.alcint.pargelium.FloatingActionRow
import com.alcint.pargelium.MiniPlayer
import com.alcint.pargelium.MorphingPillIndicator
import com.alcint.pargelium.PlaybackService
import com.alcint.pargelium.PlaylistScreen
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.ProfileScreen
import com.alcint.pargelium.R
import com.alcint.pargelium.RadioScreen
import com.alcint.pargelium.ShareTrackSheet
import com.alcint.pargelium.SortOption
import com.alcint.pargelium.StreamingManager
import com.alcint.pargelium.player.FullPlayerScreen
import com.alcint.pargelium.rememberDominantColor
import com.alcint.pargelium.rememberPargeliumScheme
import com.alcint.pargelium.settings.SettingsScreen
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlin.coroutines.resume

@Composable
fun LibraryScreen(onSecureRequest: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val backdrop = rememberLayerBackdrop()

    var themeMode by remember { mutableIntStateOf(PrefsManager.getThemeMode()) }
    var navBarStyle by remember { mutableIntStateOf(PrefsManager.getNavBarStyle()) }
    val systemDark = isSystemInDarkTheme()

    val hasPlaylists = remember { PrefsManager.getFeaturePlaylists() }
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
    var trackAddingToPlaylist: AudioTrack? by remember { mutableStateOf(null) }
    var trackToShare: AudioTrack? by remember { mutableStateOf(null) }
    var hasPermission by remember { mutableStateOf(false) }

    var allTracks: List<AudioTrack> by remember { mutableStateOf(emptyList()) }
    var tracksById: Map<Long, AudioTrack> by remember { mutableStateOf(emptyMap()) }
    var allAlbums: List<AlbumModel> by remember { mutableStateOf(emptyList()) }
    var allArtists: List<ArtistModel> by remember { mutableStateOf(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    var selectedAlbum: AlbumModel? by remember { mutableStateOf(null) }
    var selectedArtist: ArtistModel? by remember { mutableStateOf(null) }

    var playerController: MediaController? by remember { mutableStateOf(null) }
    var currentTrack: AudioTrack? by remember { mutableStateOf(null) }
    var currentPlaylist: List<AudioTrack> by remember { mutableStateOf(emptyList()) }

    var isPlaying by remember { mutableStateOf(false) }
    var trackDuration by remember { mutableLongStateOf(1L) }
    var trackMetadata by remember { mutableStateOf("") }
    var currentAlbumArt: Uri? by remember { mutableStateOf(null) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var currentQueueIndex by remember { mutableIntStateOf(-1) }

    var lastPreviousClickTime by remember { mutableLongStateOf(0L) }
    val unknownArtistStr = stringResource(id = R.string.unknown_artist)

    val currentTracksById by rememberUpdatedState(tracksById)
    val currentPlaylistState by rememberUpdatedState(currentPlaylist)
    val currentPlayerState by rememberUpdatedState(playerController)
    val currentTrackState by rememberUpdatedState(currentTrack)
    val allTracksState by rememberUpdatedState(allTracks)
    val updatedAllArtists by rememberUpdatedState(allArtists)

    LaunchedEffect(isProfileOpen) {
        if (!isProfileOpen) {
            userName = PrefsManager.getUserName()
            avatarUri = PrefsManager.getAvatarUri()
        }
    }

    LaunchedEffect(currentPlaylist) {
        if (currentPlaylist.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                PrefsManager.saveLastQueue(currentPlaylist.map { it.id })
            }
        }
    }

    DisposableEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try { playerController = controllerFuture.get() } catch (_: Exception) {}
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
                currentQueueIndex = player.currentMediaItemIndex
                val trackId = mediaItem?.mediaId?.toLongOrNull()
                val found = if (trackId != null) {
                    currentTracksById[trackId] ?: currentPlaylistState.find { it.id == trackId }
                } else null

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
        currentQueueIndex = player.currentMediaItemIndex

        val currentDuration = player.duration
        if (currentDuration != C.TIME_UNSET) trackDuration = currentDuration.coerceAtLeast(1L)

        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        if (currentId != null) {
            currentTracksById[currentId]?.let { currentTrack = it }
        }
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(playerController, tracksById) {
        val player = playerController ?: return@LaunchedEffect
        if (tracksById.isEmpty()) return@LaunchedEffect

        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        if (currentId != null) {
            if (currentTrack == null || currentTrack?.id != currentId) {
                tracksById[currentId]?.let { foundTrack ->
                    currentTrack = foundTrack
                    val currentDuration = player.duration
                    if (currentDuration != C.TIME_UNSET) trackDuration = currentDuration.coerceAtLeast(1L)
                    isPlaying = player.isPlaying
                    currentQueueIndex = player.currentMediaItemIndex
                }
            }
        } else {
            val lastTrackId = PrefsManager.getLastTrackId()
            if (lastTrackId != -1L && currentTrack == null) {
                val finalQueue = withContext(Dispatchers.Default) {
                    val lastQueueIds = PrefsManager.getLastQueue()
                    val restoredQueue = lastQueueIds.mapNotNull { id -> tracksById[id] }
                    if (restoredQueue.isNotEmpty() && restoredQueue.any { it.id == lastTrackId }) {
                        restoredQueue
                    } else {
                        tracksById[lastTrackId]?.let { listOf(it) } ?: emptyList()
                    }
                }

                if (finalQueue.isNotEmpty()) {
                    val targetIndex = finalQueue.indexOfFirst { it.id == lastTrackId }.coerceAtLeast(0)
                    val targetTrack = finalQueue[targetIndex]
                    currentTrack = targetTrack
                    val lastPos = PrefsManager.getLastPosition()
                    currentPlaylist = finalQueue
                    val mediaItems = withContext(Dispatchers.Default) {
                        finalQueue.map { createMediaItem(it) }
                    }
                    player.setMediaItems(mediaItems, targetIndex, lastPos)
                    player.prepare()
                    currentQueueIndex = targetIndex
                }
            }
        }
    }

    LaunchedEffect(currentTrack) {
        val track = currentTrack
        if (track != null) {
            if (!track.coverUrl.isNullOrEmpty() && track.coverUrl != "null") {
                currentAlbumArt = Uri.parse(track.coverUrl)
                val sourceName = if (track.source == "local") context.getString(R.string.tab_online_radio) else track.source.uppercase()
                trackMetadata = "$sourceName • ${context.getString(R.string.streaming)}"
            } else if (track.albumId != 0L && track.albumId != -1L) {
                currentAlbumArt = AudioRepository.getAlbumArtUri(track.albumId)
                trackMetadata = withContext(Dispatchers.IO) {
                    AudioRepository.getTrackMetadata(context, track.uri)
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
            if (forceScan) {
                suspendCancellableCoroutine { cont ->
                    AudioRepository.forceScan(context) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
            val loadedTracks = withContext(Dispatchers.IO) { AudioRepository.getAudioTracks(context, forceScan) }

            val (loadedAlbums, loadedArtists, idMap) = withContext(Dispatchers.Default) {
                val albums = AudioRepository.processAlbums(loadedTracks)
                val albumsByArtist = albums.groupBy { getPrimaryArtist(it.artist, unknownArtistStr) }

                val artists = loadedTracks
                    .groupBy { getPrimaryArtist(it.artist, unknownArtistStr) }
                    .map { (name, artistTracks) ->
                        val artistAlbums = albumsByArtist[name]?.sortedBy { it.title } ?: emptyList()
                        val (folderAvatar, folderBanner, folderLogo) = ArtistRepository.resolveQuickFolderAssets(
                            context = context,
                            artistName = name,
                            tracks = artistTracks
                        )
                        val firstAlbumId = artistAlbums.firstOrNull()?.id ?: artistTracks.firstOrNull()?.albumId
                        val fallbackCover = if (firstAlbumId != null && firstAlbumId > 0L) {
                            AudioRepository.getAlbumArtUri(firstAlbumId)
                        } else null

                        ArtistModel(
                            name = name,
                            tracks = artistTracks.sortedBy { it.title },
                            albums = artistAlbums,
                            customAvatarUri = folderAvatar,
                            customBannerUri = folderBanner,
                            customLogoUri = folderLogo,
                            fallbackCoverUri = fallbackCover
                        )
                    }
                    .sortedBy { it.name.trim().lowercase() }
                val map = loadedTracks.associateBy { it.id }
                Triple(albums, artists, map)
            }

            allTracks = loadedTracks
            tracksById = idMap
            allAlbums = loadedAlbums
            allArtists = loadedArtists
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
        val permissionsToRequest = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    fun playTrack(track: AudioTrack, playlistContext: List<AudioTrack>) {
        scope.launch {
            val isNetwork = track.source != "local" || track.uri.toString().startsWith("http")
            if (isNetwork) isLoading = true

            val readyTrack = try {
                track.copy(uri = StreamingManager.getPlayableUri(context, track))
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.stream_server_unavailable_msg), Toast.LENGTH_SHORT).show()
                return@launch
            } finally {
                if (isNetwork) isLoading = false
            }

            val player = currentPlayerState ?: return@launch
            if (currentTrackState?.id == readyTrack.id && player.mediaItemCount > 0) {
                if (!player.isPlaying) player.play()
                return@launch
            }
            currentTrack = readyTrack
            PrefsManager.saveLastTrackId(readyTrack.id)

            val oldList = currentPlaylistState
            val isSamePlaylist = oldList === playlistContext || (
                    oldList.size == playlistContext.size &&
                            oldList.indices.all { i -> oldList[i].id == playlistContext[i].id }
                    )

            val startIndex = playlistContext.indexOfFirst { it.id == readyTrack.id }.coerceAtLeast(0)
            if (isSamePlaylist && player.mediaItemCount > 0) {
                player.seekTo(startIndex, 0)
                player.play()
                currentQueueIndex = startIndex
            } else {
                currentPlaylist = playlistContext
                val mediaItems = withContext(Dispatchers.Default) {
                    playlistContext.map { if (it.id == readyTrack.id) createMediaItem(readyTrack) else createMediaItem(it) }
                }
                player.setMediaItems(mediaItems, startIndex, 0)
                player.prepare()
                player.play()
                currentQueueIndex = startIndex
            }
        }
    }

    fun playNext(track: AudioTrack) {
        scope.launch {
            val readyTrack = try { track.copy(uri = StreamingManager.getPlayableUri(context, track)) } catch (_: Exception) { track }
            val player = currentPlayerState ?: return@launch
            val index = (player.currentMediaItemIndex + 1).coerceAtMost(currentPlaylistState.size)
            player.addMediaItem(index, createMediaItem(readyTrack))
            val newList = currentPlaylistState.toMutableList()
            newList.add(index, readyTrack)
            currentPlaylist = newList
            Toast.makeText(context, context.getString(R.string.play_next_msg), Toast.LENGTH_SHORT).show()
        }
    }

    fun addToQueue(track: AudioTrack) {
        scope.launch {
            val readyTrack = try { track.copy(uri = StreamingManager.getPlayableUri(context, track)) } catch (_: Exception) { track }
            val player = currentPlayerState ?: return@launch
            player.addMediaItem(createMediaItem(readyTrack))
            currentPlaylist = currentPlaylistState + readyTrack
            Toast.makeText(context, context.getString(R.string.added_to_queue_msg), Toast.LENGTH_SHORT).show()
        }
    }

    fun removeFromQueue(index: Int) {
        val player = currentPlayerState ?: return
        val list = currentPlaylistState
        if (index in list.indices) {
            player.removeMediaItem(index)
            val newList = list.toMutableList()
            newList.removeAt(index)
            currentPlaylist = newList
            currentQueueIndex = player.currentMediaItemIndex
        }
    }

    fun jumpToQueueItem(index: Int) { currentPlayerState?.seekTo(index, 0) }
    fun shuffleAll() {
        val tracks = allTracksState
        if (tracks.isNotEmpty()) {
            val shuffled = tracks.shuffled()
            playTrack(shuffled[0], shuffled)
        }
    }
    fun togglePlayPause() { currentPlayerState?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun seekTo(position: Long) { currentPlayerState?.seekTo(position); PrefsManager.saveLastPosition(position) }

    fun skipNext() {
        val player = currentPlayerState ?: return
        if (player.repeatMode == Player.REPEAT_MODE_ONE) player.seekTo(0)
        else if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun skipPrevious() {
        val player = currentPlayerState ?: return
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
        val player = currentPlayerState ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    val filteredAlbums by remember {
        derivedStateOf {
            val query = searchQuery
            val base = if (query.isEmpty()) allAlbums
            else allAlbums.filter {
                it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
            }
            when (sortOption) {
                SortOption.TITLE -> base.sortedBy { it.title.trim().lowercase() }
                SortOption.ARTIST -> base.sortedBy { it.artist.trim().lowercase() }
                SortOption.DURATION -> base.sortedByDescending { it.tracks.size }
            }
        }
    }

    val filteredArtists by remember {
        derivedStateOf {
            val query = searchQuery
            val base = if (query.isEmpty()) allArtists
            else allArtists.filter {
                it.name.contains(query, ignoreCase = true)
            }
            when (sortOption) {
                SortOption.TITLE, SortOption.ARTIST -> base.sortedBy { it.name.trim().lowercase() }
                SortOption.DURATION -> base.sortedByDescending { it.tracks.size }
            }
        }
    }

    val sortedAllTracks by remember {
        derivedStateOf {
            when (sortOption) {
                SortOption.TITLE -> allTracks.sortedBy { it.title.lowercase() }
                SortOption.ARTIST -> allTracks.sortedBy { it.artist.lowercase() }
                SortOption.DURATION -> allTracks.sortedByDescending { it.duration }
            }
        }
    }

    val filteredTracks by remember {
        derivedStateOf {
            val query = searchQuery
            if (query.isEmpty()) emptyList() else {
                sortedAllTracks.filter {
                    it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
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

    val onPlayPauseCallback = remember { { togglePlayPause() } }
    val onSkipNextCallback = remember { { skipNext() } }
    val onSkipPrevCallback = remember { { skipPrevious() } }
    val onToggleRepeatCallback = remember { { toggleRepeat() } }
    val onSeekCallback = remember { { pos: Long -> seekTo(pos) } }
    val onPlayTrackCallback = remember { { t: AudioTrack, list: List<AudioTrack> -> playTrack(t, list) } }
    val onAddToQueueCallback = remember { { t: AudioTrack -> addToQueue(t) } }
    val onPlayNextCallback = remember { { t: AudioTrack -> playNext(t) } }
    val onAddToPlaylistCallback = remember { { t: AudioTrack -> trackAddingToPlaylist = t; showAddToPlaylistSheet = true } }
    val onShareTrackCallback = remember { { t: AudioTrack -> trackToShare = t } }
    val onQueueItemClickCallback = remember { { index: Int -> jumpToQueueItem(index) } }
    val onRemoveFromQueueCallback = remember { { index: Int -> removeFromQueue(index) } }
    val onRefreshCallback = remember { { loadTracks(true) } }
    val onShuffleAllCallback = remember { { shuffleAll() } }

    MaterialTheme(colorScheme = targetScheme) {
        SharedTransitionLayout {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val containerHeightPx = constraints.maxHeight.toFloat()
                    val hiddenOffsetPx = containerHeightPx + 500f

                    var isPlayerExpanded by remember { mutableStateOf(false) }
                    val playerOffsetY = remember { Animatable(10000f) }

                    val isFullPlayerVisible by remember(hiddenOffsetPx) {
                        derivedStateOf {
                            isPlayerExpanded || playerOffsetY.value < containerHeightPx - 1f
                        }
                    }

                    LaunchedEffect(hiddenOffsetPx) {
                        if (!isPlayerExpanded) playerOffsetY.snapTo(hiddenOffsetPx)
                    }

                    fun snapPlayer(toExpanded: Boolean) {
                        isPlayerExpanded = toExpanded
                        scope.launch {
                            playerOffsetY.animateTo(
                                targetValue = if (toExpanded) 0f else hiddenOffsetPx,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 220f)
                            )
                        }
                    }

                    val handleArtistClick: (String) -> Unit = remember(unknownArtistStr) {
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
                                    allTracks = allTracks,
                                    seedColor = targetColor,
                                    onBack = remember { { isProfileOpen = false } },
                                    onArtistClick = handleArtistClick
                                )
                            } else {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    beyondViewportPageCount = 0
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
                                                transitionSpec = {
                                                    (fadeIn(animationSpec = tween(280)) + slideInVertically { it / 5 })
                                                        .togetherWith(fadeOut(animationSpec = tween(180)))
                                                },
                                                label = "DetailSwitch"
                                            ) { detailState ->
                                                when (detailState) {
                                                    1 -> {
                                                        selectedAlbum?.let { album ->
                                                            val onAlbumTrackSelect = remember(album) {
                                                                { track: AudioTrack -> playTrack(track, album.tracks) }
                                                            }
                                                            AlbumDetailView(
                                                                album = album,
                                                                animatedVisibilityScope = this@AnimatedContent,
                                                                currentTrack = currentTrack,
                                                                isPlaying = isPlaying,
                                                                onBack = remember { { selectedAlbum = null } },
                                                                onTrackSelect = onAlbumTrackSelect,
                                                                onPlayPause = onPlayPauseCallback,
                                                                onAddToQueue = onAddToQueueCallback,
                                                                onPlayNext = onPlayNextCallback,
                                                                onAddToPlaylist = onAddToPlaylistCallback,
                                                                onShareTrack = onShareTrackCallback,
                                                                onArtistClick = handleArtistClick
                                                            )
                                                        }
                                                    }
                                                    2 -> {
                                                        selectedArtist?.let { artist ->
                                                            ArtistProfileView(
                                                                artist = artist,
                                                                animatedVisibilityScope = this@AnimatedContent,
                                                                currentTrack = currentTrack,
                                                                isPlaying = isPlaying,
                                                                onBack = remember { { selectedArtist = null } },
                                                                onTrackSelect = onPlayTrackCallback,
                                                                onAlbumSelect = remember { { selectedAlbum = it } },
                                                                onPlayPause = onPlayPauseCallback,
                                                                onAddToQueue = onAddToQueueCallback,
                                                                onPlayNext = onPlayNextCallback,
                                                                onAddToPlaylist = onAddToPlaylistCallback,
                                                                onShareTrack = onShareTrackCallback
                                                            )
                                                        }
                                                    }
                                                    0 -> {
                                                        LibraryDefaultView(
                                                            isRefreshing = isRefreshing,
                                                            isLoading = isLoading,
                                                            onRefresh = onRefreshCallback,
                                                            isSearchActive = isSearchActive,
                                                            searchQuery = searchQuery,
                                                            onSearchQueryChange = remember { { searchQuery = it } },
                                                            onSearchClose = remember { { isSearchActive = false; searchQuery = ""; focusManager.clearFocus() } },
                                                            onSearchOpen = remember { { isSearchActive = true } },
                                                            userName = userName,
                                                            avatarUri = avatarUri,
                                                            onProfileClick = remember { { isProfileOpen = true } },
                                                            libraryTab = libraryTab,
                                                            onTabSelected = remember { { libraryTab = it } },
                                                            filteredAlbums = filteredAlbums,
                                                            filteredArtists = filteredArtists,
                                                            filteredTracks = filteredTracks,
                                                            allTracks = sortedAllTracks,
                                                            sortOption = sortOption,
                                                            onSortOptionChange = remember { { sortOption = it } },
                                                            albumGridState = albumGridState,
                                                            artistGridState = artistGridState,
                                                            onAlbumClick = remember { { selectedAlbum = it } },
                                                            onArtistClick = remember { { selectedArtist = it } },
                                                            currentTrack = currentTrack,
                                                            isPlaying = isPlaying,
                                                            onTrackClick = onPlayTrackCallback,
                                                            onPlayNext = onPlayNextCallback,
                                                            onAddToQueue = onAddToQueueCallback,
                                                            onAddToPlaylist = onAddToPlaylistCallback,
                                                            onShare = onShareTrackCallback,
                                                            animatedVisibilityScope = this@AnimatedContent
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        1 -> RadioScreen(onStationSelect = remember { { playTrack(it, listOf(it)) } })
                                        2 -> PlaylistScreen(
                                            allTracks = allTracks,
                                            seedColor = targetColor,
                                            onPlayTrack = onPlayTrackCallback,
                                            onAddToQueue = onPlayNextCallback
                                        )
                                        3 -> EqualizerScreen(seedColor = targetColor)
                                        4 -> {
                                            if (showAdvancedSettings) {
                                                AdvancedSettingsScreen(onBackClick = remember { { showAdvancedSettings = false } })
                                            } else {
                                                SettingsScreen(
                                                    currentThemeMode = themeMode,
                                                    onThemeChanged = remember { { newMode -> themeMode = newMode; PrefsManager.saveThemeMode(newMode) } },
                                                    onNavBarStyleChanged = remember { { newStyle -> navBarStyle = newStyle } },
                                                    onSecureChanged = onSecureRequest,
                                                    onFossWearChanged = remember { { Toast.makeText(context, context.getString(R.string.watch_settings_saved), Toast.LENGTH_SHORT).show() } },
                                                    onNavigateToAdvanced = remember { { showAdvancedSettings = true } }
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
                                FloatingActionRow(onRefresh = onRefreshCallback, onShuffleAll = onShuffleAllCallback)
                            }

                            currentTrack?.let { activeTrack ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    PlayerPositionProvider(playerController, isPlaying && !isFullPlayerVisible) { currentPosProvider ->
                                        val currentPos = currentPosProvider()
                                        val calculatedProgress = if (trackDuration > 0L) currentPos.toFloat() / trackDuration else 0f

                                        MiniPlayer(
                                            track = activeTrack,
                                            isPlaying = isPlaying,
                                            progress = calculatedProgress,
                                            albumArt = currentAlbumArt,
                                            backdrop = backdrop,
                                            onPlayPause = onPlayPauseCallback,
                                            onSkipNext = onSkipNextCallback,
                                            onClick = remember { { snapPlayer(true) } }
                                        )
                                    }
                                }
                            }

                            LibraryBottomNav(
                                pagerState = pagerState,
                                navItems = navItems,
                                backdrop = backdrop,
                                navBarStyle = navBarStyle,
                                onTabSelected = remember {
                                    { index ->
                                        selectedAlbum = null
                                        selectedArtist = null
                                        isProfileOpen = false
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                }
                            )
                        }

                        currentTrack?.let { activeTrack ->
                            FullPlayerDraggableWrapper(
                                playerOffsetY = playerOffsetY,
                                containerHeightPx = containerHeightPx,
                                hiddenOffsetPx = hiddenOffsetPx,
                                snapPlayer = { snapPlayer(it) }
                            ) {
                                if (isFullPlayerVisible) {
                                    PlayerPositionProvider(playerController, isPlaying) { currentPosProvider ->
                                        FullPlayerScreen(
                                            track = activeTrack,
                                            isPlaying = isPlaying,
                                            currentPosition = currentPosProvider(),
                                            duration = trackDuration,
                                            metadata = trackMetadata,
                                            seedColor = targetColor,
                                            repeatMode = repeatMode,
                                            playlist = currentPlaylist,
                                            currentQueueIndex = currentQueueIndex,
                                            onPlayPause = onPlayPauseCallback,
                                            onSeek = onSeekCallback,
                                            onSkipNext = onSkipNextCallback,
                                            onSkipPrevious = onSkipPrevCallback,
                                            onToggleRepeat = onToggleRepeatCallback,
                                            onQueueItemClick = onQueueItemClickCallback,
                                            onRemoveFromQueue = onRemoveFromQueueCallback,
                                            onCollapse = remember { { snapPlayer(false) } },
                                            currentTab = pagerState.currentPage,
                                            onTabSelected = remember {
                                                { newTab: Int ->
                                                    scope.launch { pagerState.scrollToPage(newTab) }
                                                    snapPlayer(false)
                                                }
                                            },
                                            onArtistClick = handleArtistClick
                                        )
                                    }
                                }
                            }
                        }

                        trackToShare?.let { track ->
                            ShareTrackSheet(track = track, onDismiss = remember { { trackToShare = null } })
                        }
                        if (showAddToPlaylistSheet && trackAddingToPlaylist != null) {
                            AddToPlaylistSheet(
                                trackAddingToPlaylist = trackAddingToPlaylist!!,
                                onDismiss = remember { { showAddToPlaylistSheet = false } }
                            )
                        }
                    }
                }
            }
        }
    }
}
