@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.alcint.pargelium.library

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alcint.pargelium.AlbumCard
import com.alcint.pargelium.AlbumModel
import com.alcint.pargelium.AudioRepository
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.R as AppR
import com.alcint.pargelium.TrackRow
import com.alcint.pargelium.rememberDominantColor
import com.alcint.pargelium.tiltOnTouch
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

enum class ArtistImageRole {
    AVATAR, BANNER, LOGO, GALLERY
}

@Immutable
data class ArtistGalleryItem(
    val uri: Uri,
    val name: String,
    val role: ArtistImageRole,
    val width: Int = 0,
    val height: Int = 0,
    val hasAlpha: Boolean = false,
    val sizeBytes: Long = 0L,
    val isRemote: Boolean = false
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f

    val resolutionText: String
        get() = if (width > 0 && height > 0) "$width × $height" else ""
}

@Immutable
data class ArtistProfileBundle(
    val rootFolderPath: String? = null,
    val avatarItem: ArtistGalleryItem? = null,
    val bannerItem: ArtistGalleryItem? = null,
    val logoItem: ArtistGalleryItem? = null,
    val galleryItems: List<ArtistGalleryItem> = emptyList(),
    val localBioText: String? = null
)

object ArtistRepository {
    private const val PREFS_ARTIST_EXTRAS = "pargelium_artist_extras"
    private val bundleCache = ConcurrentHashMap<String, ArtistProfileBundle>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "PargeliumMusicPlayer/2.5 (Android; https://github.com/AlcInt)")
                .header("Accept", "application/json, text/plain, */*")
                .build()
            chain.proceed(req)
        }
        .build()

    private val gson = Gson()
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "avif")
    private val textExtensions = setOf("txt", "md", "info", "nfo")
    private val genericRootFolderNames = setOf(
        "music", "audio", "downloads", "download", "media", "storage",
        "emulated", "0", "sdcard", "files", "tracks", "albums", "flac", "mp3"
    )
    private val yearRegex = Regex("(?:19[5-9]\\d|20[0-3]\\d)")
    private val singleEpRegex = Regex("(?i)(\\bep\\b|\\bsingle\\b|\\bmaxi\\b|\\bpromo\\b|\\bost\\b|\\bremix(?:es)?\\b)")

    fun invalidateArtistCache(artistName: String) {
        bundleCache.remove(artistName.lowercase())
    }

    fun saveRoleOverride(context: Context, artistName: String, role: ArtistImageRole, uri: Uri?) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_ARTIST_EXTRAS, Context.MODE_PRIVATE)
        val key = "${artistName.lowercase()}_role_${role.name}"
        if (uri == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, uri.toString()).apply()
        }

        val meta = PrefsManager.getArtistMetadata(artistName)
        when (role) {
            ArtistImageRole.AVATAR -> PrefsManager.saveArtistMetadata(artistName, meta.copy(avatarUri = uri?.toString()))
            ArtistImageRole.BANNER -> PrefsManager.saveArtistMetadata(artistName, meta.copy(bannerUri = uri?.toString()))
            else -> {}
        }
        invalidateArtistCache(artistName)
    }

    private fun getRoleOverride(context: Context, artistName: String, role: ArtistImageRole): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_ARTIST_EXTRAS, Context.MODE_PRIVATE)
        return prefs.getString("${artistName.lowercase()}_role_${role.name}", null)
    }

    fun resolveQuickFolderAssets(context: Context, artistName: String, tracks: List<AudioTrack>): Triple<String?, String?, String?> {
        val meta = try { PrefsManager.getArtistMetadata(artistName) } catch (_: Exception) { null }
        val customAvatar = getRoleOverride(context, artistName, ArtistImageRole.AVATAR) ?: meta?.avatarUri
        val customBanner = getRoleOverride(context, artistName, ArtistImageRole.BANNER) ?: meta?.bannerUri
        val customLogo = getRoleOverride(context, artistName, ArtistImageRole.LOGO)

        val rootDir = detectArtistRootFolder(context, artistName, tracks)
        if (rootDir == null) {
            return Triple(customAvatar, customBanner, customLogo)
        }

        var foundAvatar = customAvatar
        var foundBanner = customBanner
        var foundLogo = customLogo

        val files = rootDir.listFiles().orEmpty()
        for (file in files) {
            if (!file.isFile || !imageExtensions.contains(file.extension.lowercase())) continue
            val role = classifyArtistImageFile(file.nameWithoutExtension)
            val uriStr = Uri.fromFile(file).toString()
            when (role) {
                ArtistImageRole.AVATAR -> if (foundAvatar.isNullOrBlank()) foundAvatar = uriStr
                ArtistImageRole.BANNER -> if (foundBanner.isNullOrBlank()) foundBanner = uriStr
                ArtistImageRole.LOGO -> if (foundLogo.isNullOrBlank()) foundLogo = uriStr
                else -> {}
            }
        }
        return Triple(foundAvatar, foundBanner, foundLogo)
    }

    fun detectArtistRootFolder(context: Context, artistName: String, tracks: List<AudioTrack>): File? {
        val sampleTracks = tracks.take(8)
        val parentCounts = HashMap<File, Int>()
        val cleanArtist = artistName.trim().lowercase()

        for (track in sampleTracks) {
            val path = AudioRepository.getAudioFilePath(context, track.uri) ?: continue
            val audioFile = File(path)
            val albumDir = audioFile.parentFile ?: continue
            val parentDir = albumDir.parentFile

            if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
                val pName = parentDir.name.trim().lowercase()
                if (pName !in genericRootFolderNames) {
                    if (pName.contains(cleanArtist) || cleanArtist.contains(pName)) {
                        return parentDir
                    }
                    parentCounts[parentDir] = (parentCounts[parentDir] ?: 0) + 1
                }
            }

            if (albumDir.name.trim().lowercase() == cleanArtist) {
                return albumDir
            }
        }

        return parentCounts.maxByOrNull { it.value }?.key
    }

    fun loadArtistProfileBundle(
        context: Context,
        artist: ArtistModel,
        forceRefresh: Boolean = false
    ): ArtistProfileBundle {
        val cacheKey = artist.name.lowercase()
        if (!forceRefresh) {
            bundleCache[cacheKey]?.let { return it }
        }

        val rootDir = detectArtistRootFolder(context, artist.name, artist.tracks)
        val remoteCacheDir = File(
            context.getDir("artist_remote_assets", Context.MODE_PRIVATE),
            "artist_${artist.name.lowercase().hashCode()}"
        )

        val discoveredImages = mutableListOf<ArtistGalleryItem>()
        val seenPaths = HashSet<String>()
        var localBioText: String? = null

        fun inspectImageFile(file: File, isRemote: Boolean) {
            if (!file.exists() || !file.isFile || !file.canRead()) return
            val ext = file.extension.lowercase()
            if (!imageExtensions.contains(ext)) return
            if (!seenPaths.add(file.absolutePath)) return

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                BitmapFactory.decodeFile(file.absolutePath, options)
            } catch (_: Exception) {}

            val baseName = file.nameWithoutExtension
            val role = classifyArtistImageFile(baseName)
            val mime = options.outMimeType?.lowercase() ?: ""
            val isAlphaCapable = ext == "png" || ext == "webp" || mime.contains("png") || mime.contains("webp")

            discoveredImages.add(
                ArtistGalleryItem(
                    uri = Uri.fromFile(file),
                    name = baseName,
                    role = role,
                    width = options.outWidth.coerceAtLeast(0),
                    height = options.outHeight.coerceAtLeast(0),
                    hasAlpha = isAlphaCapable,
                    sizeBytes = file.length(),
                    isRemote = isRemote
                )
            )
        }

        fun inspectTextFile(file: File) {
            if (localBioText != null || !file.exists() || !file.isFile || !file.canRead()) return
            val ext = file.extension.lowercase()
            if (!textExtensions.contains(ext)) return
            val lowerName = file.nameWithoutExtension.lowercase()
            if (
                lowerName in setOf("bio", "biography", "description", "info", "about", "artist", "readme", "notes") ||
                lowerName.contains("bio") || lowerName.contains("info") || lowerName.contains("about")
            ) {
                val raw = try {
                    file.readText(Charsets.UTF_8)
                } catch (_: Exception) {
                    try { file.readText(charset("windows-1251")) } catch (_: Exception) { null }
                }
                val cleaned = raw?.let { cleanBiographyText(it) }
                if (!cleaned.isNullOrBlank()) {
                    localBioText = cleaned
                }
            }
        }

        if (rootDir != null && rootDir.exists() && rootDir.isDirectory) {
            val subFolderNames = setOf("artwork", "artworks", "images", "scans", "gallery", "photos", "extra", "logo")
            try {
                rootDir.listFiles()?.forEach { child ->
                    if (child.isFile) {
                        inspectImageFile(child, isRemote = false)
                        inspectTextFile(child)
                    } else if (child.isDirectory && subFolderNames.any { child.name.contains(it, ignoreCase = true) }) {
                        child.listFiles()?.forEach { sub ->
                            if (sub.isFile) {
                                inspectImageFile(sub, isRemote = false)
                                inspectTextFile(sub)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val firstAlbumTrack = artist.tracks.firstOrNull()
        val firstAlbumDir = firstAlbumTrack?.let { AudioRepository.getAudioFilePath(context, it.uri) }?.let { File(it).parentFile }
        if (firstAlbumDir != null && firstAlbumDir != rootDir && firstAlbumDir.exists()) {
            try {
                firstAlbumDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val n = file.nameWithoutExtension.lowercase()
                        if (n.contains("artist") || n.contains("logo") || n.contains("avatar") || n.contains("banner") || n.contains("bio")) {
                            inspectImageFile(file, isRemote = false)
                            inspectTextFile(file)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (remoteCacheDir.exists() && remoteCacheDir.isDirectory) {
            remoteCacheDir.listFiles()?.forEach { remoteFile ->
                if (remoteFile.isFile) {
                    inspectImageFile(remoteFile, isRemote = true)
                    inspectTextFile(remoteFile)
                }
            }
        }

        val savedMeta = try { PrefsManager.getArtistMetadata(artist.name) } catch (_: Exception) { null }
        val overrideAvatarStr = getRoleOverride(context, artist.name, ArtistImageRole.AVATAR) ?: savedMeta?.avatarUri
        val overrideBannerStr = getRoleOverride(context, artist.name, ArtistImageRole.BANNER) ?: savedMeta?.bannerUri
        val overrideLogoStr = getRoleOverride(context, artist.name, ArtistImageRole.LOGO)

        fun resolveOverrideItem(uriStr: String?, role: ArtistImageRole): ArtistGalleryItem? {
            if (uriStr.isNullOrBlank()) return null
            val parsed = Uri.parse(uriStr)
            discoveredImages.find { it.uri == parsed }?.let { return it.copy(role = role) }

            var w = 0
            var h = 0
            var alpha = uriStr.endsWith(".png", true) || uriStr.endsWith(".webp", true)
            if (parsed.scheme == "file") {
                val f = File(parsed.path ?: "")
                if (f.exists()) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    try { BitmapFactory.decodeFile(f.absolutePath, opts) } catch (_: Exception) {}
                    w = opts.outWidth
                    h = opts.outHeight
                    val mime = opts.outMimeType?.lowercase() ?: ""
                    alpha = alpha || mime.contains("png") || mime.contains("webp")
                }
            }
            return ArtistGalleryItem(
                uri = parsed,
                name = role.name.lowercase(),
                role = role,
                width = w,
                height = h,
                hasAlpha = alpha
            )
        }

        val avatarItem = resolveOverrideItem(overrideAvatarStr, ArtistImageRole.AVATAR)
            ?: discoveredImages.firstOrNull { it.role == ArtistImageRole.AVATAR }
            ?: discoveredImages.firstOrNull { it.role == ArtistImageRole.GALLERY }

        val bannerItem = resolveOverrideItem(overrideBannerStr, ArtistImageRole.BANNER)
            ?: discoveredImages.firstOrNull { it.role == ArtistImageRole.BANNER }
            ?: discoveredImages.firstOrNull { it.aspectRatio >= 1.35f && it.role != ArtistImageRole.LOGO }

        val logoItem = resolveOverrideItem(overrideLogoStr, ArtistImageRole.LOGO)
            ?: discoveredImages.firstOrNull { it.role == ArtistImageRole.LOGO }

        val sortedGallery = discoveredImages.sortedWith(
            compareBy<ArtistGalleryItem> { it.role.ordinal }
                .thenBy { AudioRepository.extractArtworkSequenceIndex(it.name) }
                .thenBy { it.name.lowercase() }
        )

        val bundle = ArtistProfileBundle(
            rootFolderPath = rootDir?.absolutePath,
            avatarItem = avatarItem,
            bannerItem = bannerItem,
            logoItem = logoItem,
            galleryItems = sortedGallery,
            localBioText = localBioText
        )
        bundleCache[cacheKey] = bundle
        return bundle
    }

    private fun classifyArtistImageFile(rawName: String): ArtistImageRole {
        val n = rawName.trim().lowercase()
        return when {
            n.contains("logo") || n.contains("clearlogo") || n.contains("brand") || n.contains("wordmark") -> ArtistImageRole.LOGO
            n.contains("banner") || n.contains("backdrop") || n.contains("header") ||
                    n.contains("background") || n.contains("fanart") || n.contains("wide") -> ArtistImageRole.BANNER
            n in setOf("avatar", "artist", "profile", "photo", "portrait", "folder", "cover", "thumb") ||
                    n.startsWith("avatar") || n.startsWith("artist") || n.startsWith("profile") || n.startsWith("portrait") -> ArtistImageRole.AVATAR
            else -> ArtistImageRole.GALLERY
        }
    }

    fun cleanBiographyText(rawText: String): String {
        return rawText
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\[\\s*(?:\\d+|[a-zA-Z]|citation needed|править|источник[^]]*)\\s*]"), "")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\(\\s*[;,]?\\s*\\)"), "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    suspend fun fetchWikipediaBiography(artistName: String): String? = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isEmpty() || cleanName.equals("<unknown>", ignoreCase = true)) return@withContext null

        val isRussianLocale = Locale.getDefault().language.equals("ru", ignoreCase = true)
        val langPriority = if (isRussianLocale) listOf("ru", "en") else listOf("en", "ru")

        for (lang in langPriority) {
            val queriesToTry = if (lang == "ru") {
                listOf("$cleanName (группа)", "$cleanName (музыкант)", cleanName)
            } else {
                listOf("$cleanName (band)", "$cleanName (musician)", cleanName)
            }

            for (candidateQuery in queriesToTry) {
                try {
                    val encodedQuery = URLEncoder.encode(candidateQuery, "UTF-8")
                    val searchUrl = "https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&utf8=1&format=json&srlimit=3"

                    var bestTitle: String? = null
                    httpClient.newCall(Request.Builder().url(searchUrl).build()).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val json = gson.fromJson(body, JsonObject::class.java)
                                val searchArr = json.getAsJsonObject("query")?.getAsJsonArray("search")
                                if (searchArr != null && searchArr.size() > 0) {
                                    for (i in 0 until searchArr.size()) {
                                        val item = searchArr.get(i).asJsonObject
                                        val title = item.get("title")?.asString ?: continue
                                        val snippet = item.get("snippet")?.asString?.lowercase() ?: ""
                                        if (title.contains(cleanName, ignoreCase = true) ||
                                            snippet.contains("music") || snippet.contains("band") ||
                                            snippet.contains("музык") || snippet.contains("групп") ||
                                            snippet.contains("певец") || snippet.contains("диджей") ||
                                            snippet.contains("producer") || snippet.contains("dj")
                                        ) {
                                            bestTitle = title
                                            break
                                        }
                                    }
                                    if (bestTitle == null && candidateQuery == cleanName) {
                                        bestTitle = searchArr.get(0).asJsonObject.get("title")?.asString
                                    }
                                }
                            }
                        }
                    }

                    if (!bestTitle.isNullOrBlank()) {
                        val encTitle = URLEncoder.encode(bestTitle!!, "UTF-8")
                        val extractUrl = "https://$lang.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1&explaintext=1&redirects=1&titles=$encTitle&format=json"

                        httpClient.newCall(Request.Builder().url(extractUrl).build()).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string()
                                if (!body.isNullOrBlank()) {
                                    val json = gson.fromJson(body, JsonObject::class.java)
                                    val pages = json.getAsJsonObject("query")?.getAsJsonObject("pages")
                                    if (pages != null) {
                                        for ((_, pageVal) in pages.entrySet()) {
                                            val extract = pageVal.asJsonObject.get("extract")?.asString
                                            if (!extract.isNullOrBlank()) {
                                                val cleaned = cleanBiographyText(extract)
                                                if (cleaned.length >= 60) {
                                                    return@withContext cleaned
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        null
    }

    suspend fun fetchOnlineArtistImages(
        context: Context,
        artist: ArtistModel
    ): ArtistProfileBundle = withContext(Dispatchers.IO) {
        val cleanName = artist.name.trim()
        if (cleanName.isEmpty() || cleanName.equals("<unknown>", ignoreCase = true)) {
            return@withContext loadArtistProfileBundle(context, artist, forceRefresh = true)
        }

        val remoteCacheDir = File(
            context.getDir("artist_remote_assets", Context.MODE_PRIVATE),
            "artist_${cleanName.lowercase().hashCode()}"
        )
        if (!remoteCacheDir.exists()) remoteCacheDir.mkdirs()

        val downloads = mutableListOf<Pair<String, String>>()
        val encName = URLEncoder.encode(cleanName, "UTF-8")

        try {
            val audioDbUrl = "https://www.theaudiodb.com/api/v1/json/2/search.php?s=$encName"
            httpClient.newCall(Request.Builder().url(audioDbUrl).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val artistsArr = json.getAsJsonArray("artists")
                        if (artistsArr != null && artistsArr.size() > 0) {
                            val aObj = artistsArr.get(0).asJsonObject
                            aObj.get("strArtistLogo")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.startsWith("http") }?.let {
                                downloads.add(it to "logo_clearlogo.png")
                            }
                            aObj.get("strArtistClearart")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.startsWith("http") }?.let {
                                if (downloads.none { d -> d.second.startsWith("logo") }) {
                                    downloads.add(it to "logo_art.png")
                                }
                            }
                            aObj.get("strArtistThumb")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.startsWith("http") }?.let {
                                downloads.add(it to "avatar_thumb.jpg")
                            }
                            aObj.get("strArtistFanart")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.startsWith("http") }?.let {
                                downloads.add(it to "banner_fanart_1.jpg")
                            }
                            aObj.get("strArtistFanart2")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.startsWith("http") }?.let {
                                downloads.add(it to "gallery_fanart_2.jpg")
                            }
                            aObj.get("strArtistFanart3")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.startsWith("http") }?.let {
                                downloads.add(it to "gallery_fanart_3.jpg")
                            }
                            aObj.get("strArtistWideThumb")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.startsWith("http") }?.let {
                                downloads.add(it to "banner_wide.jpg")
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val deezerUrl = "https://api.deezer.com/search/artist?q=$encName&limit=3"
            httpClient.newCall(Request.Builder().url(deezerUrl).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val dataArr = json.getAsJsonArray("data")
                        if (dataArr != null && dataArr.size() > 0) {
                            val first = dataArr.get(0).asJsonObject
                            val xlPic = first.get("picture_xl")?.asString ?: first.get("picture_big")?.asString
                            if (!xlPic.isNullOrBlank() && xlPic.startsWith("http")) {
                                downloads.add(xlPic to "avatar_deezer_xl.jpg")
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val wikiImgUrl = "https://en.wikipedia.org/w/api.php?action=query&prop=pageimages&piprop=original&titles=$encName&redirects=1&format=json"
            httpClient.newCall(Request.Builder().url(wikiImgUrl).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val pages = json.getAsJsonObject("query")?.getAsJsonObject("pages")
                        if (pages != null) {
                            for ((_, pVal) in pages.entrySet()) {
                                val orig = pVal.asJsonObject.getAsJsonObject("original")?.get("source")?.asString
                                if (!orig.isNullOrBlank() && orig.startsWith("http") && !orig.endsWith(".svg", true)) {
                                    downloads.add(orig to "gallery_wiki_portrait.jpg")
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        for ((url, fileName) in downloads.distinctBy { it.first }.take(8)) {
            val outFile = File(remoteCacheDir, fileName)
            if (outFile.exists() && outFile.length() > 1024L) continue
            try {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.size > 1024) {
                            outFile.writeBytes(bytes)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        loadArtistProfileBundle(context, artist, forceRefresh = true)
    }

    fun isSingleOrEpRelease(album: AlbumModel): Boolean {
        val totalDurationMs = album.tracks.sumOf { it.duration }
        if (singleEpRegex.containsMatchIn(album.title)) return true
        return album.tracks.size <= 3 || (album.tracks.size <= 5 && totalDurationMs in 1..1_260_000L)
    }

    fun extractReleaseYear(album: AlbumModel): Int? {
        val match = yearRegex.find(album.title)
        return match?.value?.toIntOrNull()
    }

    fun findLatestRelease(context: Context, albums: List<AlbumModel>): AlbumModel? {
        if (albums.isEmpty()) return null
        if (albums.size == 1) return albums.first()

        val withExplicitYear = albums.mapNotNull { alb ->
            extractReleaseYear(alb)?.let { yr -> alb to yr }
        }
        if (withExplicitYear.isNotEmpty()) {
            return withExplicitYear.maxByOrNull { it.second }?.first
        }

        return albums.maxByOrNull { alb ->
            val firstUri = alb.tracks.firstOrNull()?.uri
            val path = if (firstUri != null) AudioRepository.getAudioFilePath(context, firstUri) else null
            val file = path?.let { File(it) }
            file?.parentFile?.lastModified() ?: file?.lastModified() ?: alb.id
        }
    }
}

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
                    .shadow(10.dp, CircleShape)
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

            if (!artist.customLogoUri.isNullOrBlank()) {
                AsyncImage(
                    model = remember(artist.customLogoUri) {
                        ImageRequest.Builder(context)
                            .data(artist.customLogoUri)
                            .size(240)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = artist.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(22.dp)
                        .widthIn(max = 120.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

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
                            id = AppR.plurals.tracks_count,
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    var meta by remember(artist.name) { mutableStateOf(PrefsManager.getArtistMetadata(artist.name)) }
    var profileBundle by remember(artist.name) { mutableStateOf(ArtistProfileBundle()) }
    var isScanningBundle by remember(artist.name) { mutableStateOf(true) }
    var isFetchingWiki by remember(artist.name) { mutableStateOf(false) }
    var isFetchingImages by remember(artist.name) { mutableStateOf(false) }

    var activeSection by remember { mutableIntStateOf(0) }
    var discographyFilter by remember { mutableIntStateOf(0) }
    var isEditingBio by remember { mutableStateOf(false) }
    var bioInput by remember(meta.description) { mutableStateOf(meta.description) }
    var isBioExpanded by remember { mutableStateOf(false) }
    var isTrackSearchOpen by remember { mutableStateOf(false) }
    var trackSearchQuery by remember { mutableStateOf("") }
    var showAllTracks by remember { mutableStateOf(false) }
    var lightboxInitialIndex by remember { mutableStateOf<Int?>(null) }

    val fallbackCover = remember(artist) { resolveArtistCoverUri(artist) }
    val effectiveAvatarModel: Any? = profileBundle.avatarItem?.uri ?: meta.avatarUri ?: fallbackCover
    val effectiveBannerModel: Any? = profileBundle.bannerItem?.uri ?: meta.bannerUri ?: effectiveAvatarModel
    val dominantUri = remember(effectiveAvatarModel) {
        when (effectiveAvatarModel) {
            is Uri -> effectiveAvatarModel
            is String -> Uri.parse(effectiveAvatarModel)
            else -> null
        }
    }
    val extractedColor = rememberDominantColor(dominantUri)
    val accentColor = if (extractedColor != Color.Unspecified) extractedColor else MaterialTheme.colorScheme.primary

    val isArtistPlaying = remember(currentTrack, artist.tracks, isPlaying) {
        isPlaying && currentTrack != null && artist.tracks.any { it.id == currentTrack.id }
    }

    LaunchedEffect(artist.name) {
        isScanningBundle = true
        val loaded = withContext(Dispatchers.IO) {
            ArtistRepository.loadArtistProfileBundle(context, artist)
        }
        profileBundle = loaded
        if (meta.description.isBlank() && !loaded.localBioText.isNullOrBlank()) {
            val updated = meta.copy(description = loaded.localBioText)
            PrefsManager.saveArtistMetadata(artist.name, updated)
            meta = updated
            bioInput = loaded.localBioText
        }
        isScanningBundle = false

        if (meta.description.isBlank() && loaded.localBioText.isNullOrBlank()) {
            isFetchingWiki = true
            val wikiBio = ArtistRepository.fetchWikipediaBiography(artist.name)
            if (!wikiBio.isNullOrBlank()) {
                val updated = meta.copy(description = wikiBio)
                PrefsManager.saveArtistMetadata(artist.name, updated)
                meta = updated
                bioInput = wikiBio
            }
            isFetchingWiki = false
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            ArtistRepository.saveRoleOverride(context, artist.name, ArtistImageRole.AVATAR, uri)
            meta = PrefsManager.getArtistMetadata(artist.name)
            scope.launch {
                profileBundle = withContext(Dispatchers.IO) {
                    ArtistRepository.loadArtistProfileBundle(context, artist, forceRefresh = true)
                }
            }
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            ArtistRepository.saveRoleOverride(context, artist.name, ArtistImageRole.BANNER, uri)
            meta = PrefsManager.getArtistMetadata(artist.name)
            scope.launch {
                profileBundle = withContext(Dispatchers.IO) {
                    ArtistRepository.loadArtistProfileBundle(context, artist, forceRefresh = true)
                }
            }
        }
    }

    val fullAlbums = remember(artist.albums) {
        artist.albums.filterNot { ArtistRepository.isSingleOrEpRelease(it) }
    }
    val singlesAndEps = remember(artist.albums) {
        artist.albums.filter { ArtistRepository.isSingleOrEpRelease(it) }
    }
    val latestRelease = remember(artist.albums) {
        ArtistRepository.findLatestRelease(context, artist.albums)
    }

    val featuredTracks = remember(artist.tracks, artist.name) {
        artist.tracks.filter {
            it.artist.contains("feat", ignoreCase = true) ||
                    it.artist.contains("&") ||
                    it.artist.contains(",") ||
                    it.artist.contains(" vs", ignoreCase = true)
        }
    }

    val displayedTracks = remember(artist.tracks, trackSearchQuery, showAllTracks) {
        val base = if (trackSearchQuery.isBlank()) {
            artist.tracks
        } else {
            artist.tracks.filter {
                it.title.contains(trackSearchQuery, ignoreCase = true) ||
                        it.album.contains(trackSearchQuery, ignoreCase = true)
            }
        }
        if (showAllTracks || trackSearchQuery.isNotBlank()) base else base.take(10)
    }

    val totalDurationMs = remember(artist.tracks) {
        artist.tracks.sumOf { it.duration }
    }

    val collapsedHeaderAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 520f).coerceIn(0f, 1f)
        }
    }

    BackHandler(enabled = lightboxInitialIndex != null || activeSection == 1 || isTrackSearchOpen) {
        when {
            lightboxInitialIndex != null -> lightboxInitialIndex = null
            isTrackSearchOpen -> {
                isTrackSearchOpen = false
                trackSearchQuery = ""
                focusManager.clearFocus()
            }
            activeSection == 1 -> activeSection = 0
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
            item(key = "artist_expressive_hero", contentType = "hero") {
                ArtistExpressiveHero(
                    artist = artist,
                    avatarModel = effectiveAvatarModel,
                    bannerModel = effectiveBannerModel,
                    logoItem = profileBundle.logoItem,
                    accentColor = accentColor,
                    isArtistPlaying = isArtistPlaying,
                    totalDurationMs = totalDurationMs,
                    fullAlbumsCount = fullAlbums.size,
                    singlesCount = singlesAndEps.size,
                    galleryCount = profileBundle.galleryItems.size,
                    activeSection = activeSection,
                    onSectionChange = { activeSection = it },
                    onAvatarClick = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onBannerClick = {
                        bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onLogoClick = {
                        val logoIdx = profileBundle.galleryItems.indexOfFirst { it.role == ArtistImageRole.LOGO }
                        if (logoIdx >= 0) lightboxInitialIndex = logoIdx
                    },
                    onPlayAllClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isArtistPlaying) {
                            onPlayPause()
                        } else if (artist.tracks.isNotEmpty()) {
                            onTrackSelect(artist.tracks.first(), artist.tracks)
                        }
                    },
                    onShuffleClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (artist.tracks.isNotEmpty()) {
                            val shuffled = artist.tracks.shuffled()
                            onTrackSelect(shuffled.first(), shuffled)
                        }
                    },
                    onDeepCutsRadioClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (artist.tracks.isNotEmpty()) {
                            val deepMix = artist.tracks.sortedByDescending { it.duration }.shuffled()
                            onTrackSelect(deepMix.first(), deepMix)
                        }
                    }
                )
            }

            if (activeSection == 0) {
                item(key = "artist_biography_card", contentType = "bio") {
                    val effectiveBio = meta.description.ifBlank { profileBundle.localBioText ?: "" }
                    ArtistBiographyCard(
                        bioText = effectiveBio,
                        isEditing = isEditingBio,
                        bioInput = bioInput,
                        isExpanded = isBioExpanded,
                        isFetchingWiki = isFetchingWiki,
                        hasLocalFile = !profileBundle.localBioText.isNullOrBlank(),
                        accentColor = accentColor,
                        onToggleExpand = { isBioExpanded = !isBioExpanded },
                        onStartEdit = {
                            bioInput = effectiveBio
                            isEditingBio = true
                        },
                        onBioInputChange = { bioInput = it },
                        onSaveBio = {
                            val cleaned = ArtistRepository.cleanBiographyText(bioInput)
                            val newMeta = meta.copy(description = cleaned)
                            PrefsManager.saveArtistMetadata(artist.name, newMeta)
                            meta = newMeta
                            isEditingBio = false
                        },
                        onFetchWikipedia = {
                            if (!isFetchingWiki) {
                                scope.launch {
                                    isFetchingWiki = true
                                    val fetched = ArtistRepository.fetchWikipediaBiography(artist.name)
                                    if (!fetched.isNullOrBlank()) {
                                        val newMeta = meta.copy(description = fetched)
                                        PrefsManager.saveArtistMetadata(artist.name, newMeta)
                                        meta = newMeta
                                        bioInput = fetched
                                    }
                                    isFetchingWiki = false
                                }
                            }
                        }
                    )
                }

                if (latestRelease != null && artist.albums.size > 1) {
                    item(key = "artist_latest_release", contentType = "latest_release") {
                        ArtistLatestReleaseSpotlight(
                            album = latestRelease,
                            accentColor = accentColor,
                            onAlbumClick = { onAlbumSelect(latestRelease) },
                            onPlayAlbum = {
                                if (latestRelease.tracks.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTrackSelect(latestRelease.tracks.first(), latestRelease.tracks)
                                }
                            }
                        )
                    }
                }

                if (artist.albums.isNotEmpty()) {
                    item(key = "artist_discography_section", contentType = "discography") {
                        ArtistDiscographySection(
                            allAlbums = artist.albums,
                            fullAlbums = fullAlbums,
                            singlesAndEps = singlesAndEps,
                            selectedFilter = discographyFilter,
                            accentColor = accentColor,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onFilterChange = { discographyFilter = it },
                            onAlbumSelect = onAlbumSelect
                        )
                    }
                }

                item(key = "artist_tracks_header", contentType = "tracks_header") {
                    ArtistTracksHeaderBar(
                        totalTracksCount = artist.tracks.size,
                        isSearchOpen = isTrackSearchOpen,
                        searchQuery = trackSearchQuery,
                        onSearchQueryChange = { trackSearchQuery = it },
                        onToggleSearch = {
                            isTrackSearchOpen = !isTrackSearchOpen
                            if (!isTrackSearchOpen) {
                                trackSearchQuery = ""
                                focusManager.clearFocus()
                            }
                        }
                    )
                }

                items(
                    items = displayedTracks,
                    key = { it.id },
                    contentType = { "artist_track_row" }
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

                if (artist.tracks.size > 10 && trackSearchQuery.isBlank()) {
                    item(key = "artist_show_more_tracks", contentType = "show_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedButton(
                                onClick = { showAllTracks = !showAllTracks },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (showAllTracks) {
                                        stringResource(id = AppR.string.artist_show_less_tracks)
                                    } else {
                                        stringResource(id = AppR.string.artist_show_all_tracks_fmt, artist.tracks.size)
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (featuredTracks.isNotEmpty()) {
                    item(key = "artist_collabs_section", contentType = "collabs") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(id = AppR.string.artist_appears_on_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    items(
                        items = featuredTracks.take(8),
                        key = { "collab_${it.id}" },
                        contentType = { "artist_track_row" }
                    ) { collabTrack ->
                        TrackRow(
                            track = collabTrack,
                            isPlaying = isPlaying,
                            isCurrentTrack = currentTrack?.id == collabTrack.id,
                            albumArt = remember(collabTrack.albumId) { AudioRepository.getAlbumArtUri(collabTrack.albumId) },
                            onClick = { onTrackSelect(collabTrack, featuredTracks) },
                            onPlayNext = { onPlayNext(collabTrack) },
                            onAddToQueue = { onAddToQueue(collabTrack) },
                            onAddToPlaylist = { onAddToPlaylist(collabTrack) },
                            onShare = { onShareTrack(collabTrack) }
                        )
                    }
                }

                if (!profileBundle.rootFolderPath.isNullOrBlank()) {
                    item(key = "artist_folder_specs", contentType = "folder_specs") {
                        ArtistFolderInfoCard(
                            rootFolderPath = profileBundle.rootFolderPath!!,
                            galleryCount = profileBundle.galleryItems.size,
                            hasLogo = profileBundle.logoItem != null,
                            hasLocalBio = !profileBundle.localBioText.isNullOrBlank(),
                            accentColor = accentColor,
                            onOpenGallery = { activeSection = 1 }
                        )
                    }
                }
            } else {
                item(key = "artist_gallery_header", contentType = "gallery_header") {
                    ArtistGallerySectionHeader(
                        galleryCount = profileBundle.galleryItems.size,
                        isFetchingImages = isFetchingImages,
                        accentColor = accentColor,
                        onFetchOnlineImages = {
                            if (!isFetchingImages) {
                                scope.launch {
                                    isFetchingImages = true
                                    profileBundle = ArtistRepository.fetchOnlineArtistImages(context, artist)
                                    isFetchingImages = false
                                }
                            }
                        }
                    )
                }

                when {
                    isScanningBundle -> {
                        item(key = "artist_gallery_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = accentColor)
                            }
                        }
                    }
                    profileBundle.galleryItems.isEmpty() -> {
                        item(key = "artist_gallery_empty") {
                            ArtistGalleryEmptyCard(
                                isFetching = isFetchingImages,
                                onFetchClick = {
                                    if (!isFetchingImages) {
                                        scope.launch {
                                            isFetchingImages = true
                                            profileBundle = ArtistRepository.fetchOnlineArtistImages(context, artist)
                                            isFetchingImages = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                    else -> {
                        val rows = profileBundle.galleryItems.chunked(2)
                        items(
                            items = rows,
                            key = { row -> row.first().uri.toString() },
                            contentType = { "artist_gallery_row" }
                        ) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { item ->
                                    ArtistGalleryCardItem(
                                        item = item,
                                        accentColor = accentColor,
                                        modifier = Modifier.weight(1f),
                                        onAssignRole = { newRole ->
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            ArtistRepository.saveRoleOverride(context, artist.name, newRole, item.uri)
                                            meta = PrefsManager.getArtistMetadata(artist.name)
                                            scope.launch {
                                                profileBundle = withContext(Dispatchers.IO) {
                                                    ArtistRepository.loadArtistProfileBundle(context, artist, forceRefresh = true)
                                                }
                                            }
                                        },
                                        onClick = {
                                            val idx = profileBundle.galleryItems.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
                                            lightboxInitialIndex = idx
                                        }
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        ArtistTopCollapsedBar(
            artistName = artist.name,
            avatarModel = effectiveAvatarModel,
            logoItem = profileBundle.logoItem,
            collapsedAlphaProvider = { collapsedHeaderAlpha },
            activeSection = activeSection,
            galleryCount = profileBundle.galleryItems.size,
            onBack = onBack,
            onToggleGallery = { activeSection = if (activeSection == 0) 1 else 0 },
            onPlayNextAll = {
                artist.tracks.asReversed().forEach { onPlayNext(it) }
            },
            onAddToQueueAll = {
                artist.tracks.forEach { onAddToQueue(it) }
            }
        )

        AnimatedVisibility(
            visible = lightboxInitialIndex != null && profileBundle.galleryItems.isNotEmpty(),
            enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.94f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.94f),
            modifier = Modifier.zIndex(20f)
        ) {
            lightboxInitialIndex?.let { startIdx ->
                ArtistArtworkLightbox(
                    items = profileBundle.galleryItems,
                    initialIndex = startIdx.coerceIn(0, (profileBundle.galleryItems.size - 1).coerceAtLeast(0)),
                    accentColor = accentColor,
                    onAssignRole = { item, role ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        ArtistRepository.saveRoleOverride(context, artist.name, role, item.uri)
                        meta = PrefsManager.getArtistMetadata(artist.name)
                        scope.launch {
                            profileBundle = withContext(Dispatchers.IO) {
                                ArtistRepository.loadArtistProfileBundle(context, artist, forceRefresh = true)
                            }
                        }
                    },
                    onDismiss = { lightboxInitialIndex = null }
                )
            }
        }
    }
}

@Composable
private fun AdaptiveArtistLogoPlate(
    logoItem: ArtistGalleryItem,
    modifier: Modifier = Modifier,
    maxHeightDp: Int = 62,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val ratio = logoItem.aspectRatio.coerceIn(0.5f, 6.0f)
    val isWideLogo = ratio >= 1.7f

    val targetWidth = when {
        ratio >= 3.0f -> 220.dp
        ratio >= 1.8f -> 185.dp
        else -> (maxHeightDp * ratio).dp.coerceIn(64.dp, 150.dp)
    }
    val targetHeight = if (isWideLogo) {
        (maxHeightDp * 0.9f).dp
    } else {
        maxHeightDp.dp
    }

    if (logoItem.hasAlpha) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .widthIn(min = 72.dp, max = 240.dp)
                .heightIn(min = 34.dp, max = maxHeightDp.dp)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            AsyncImage(
                model = remember(logoItem.uri) {
                    ImageRequest.Builder(context)
                        .data(logoItem.uri)
                        .size(640)
                        .crossfade(true)
                        .build()
                },
                contentDescription = logoItem.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(targetWidth)
                    .height(targetHeight)
            )
        }
    } else {
        Surface(
            onClick = { onClick?.invoke() },
            enabled = onClick != null,
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.52f),
            shadowElevation = 8.dp,
            modifier = modifier
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                AsyncImage(
                    model = remember(logoItem.uri) {
                        ImageRequest.Builder(context)
                            .data(logoItem.uri)
                            .size(640)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = logoItem.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(min = 68.dp, max = 210.dp)
                        .heightIn(min = 30.dp, max = (maxHeightDp - 8).dp)
                )
            }
        }
    }
}

@Composable
private fun ArtistTopCollapsedBar(
    artistName: String,
    avatarModel: Any?,
    logoItem: ArtistGalleryItem?,
    collapsedAlphaProvider: () -> Float,
    activeSection: Int,
    galleryCount: Int,
    onBack: () -> Unit,
    onToggleGallery: () -> Unit,
    onPlayNextAll: () -> Unit,
    onAddToQueueAll: () -> Unit
) {
    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(10f)
            .drawBehind {
                val alpha = (collapsedAlphaProvider() * 0.95f).coerceIn(0f, 0.95f)
                drawRect(color = surfaceColor.copy(alpha = alpha))
                if (alpha > 0.8f) {
                    drawLine(
                        color = dividerColor.copy(alpha = (alpha - 0.8f) * 2f),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
    ) {
        Column {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = AppR.string.album_action_back)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val a = collapsedAlphaProvider()
                            alpha = a
                            translationY = (1f - a) * 20f
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = remember(avatarModel) {
                            ImageRequest.Builder(context)
                                .data(avatarModel)
                                .size(120)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    if (logoItem != null && logoItem.hasAlpha) {
                        AsyncImage(
                            model = remember(logoItem.uri) {
                                ImageRequest.Builder(context)
                                    .data(logoItem.uri)
                                    .size(240)
                                    .crossfade(true)
                                    .build()
                            },
                            contentDescription = artistName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(26.dp)
                                .widthIn(max = 140.dp)
                        )
                    } else {
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                BadgedBox(
                    badge = {
                        if (galleryCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(text = galleryCount.toString(), fontSize = 10.sp)
                            }
                        }
                    }
                ) {
                    FilledTonalIconButton(
                        onClick = onToggleGallery,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (activeSection == 1) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (activeSection == 1) Icons.Rounded.LibraryMusic else Icons.Rounded.Collections,
                            contentDescription = stringResource(id = AppR.string.album_tab_gallery)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box {
                    FilledTonalIconButton(
                        onClick = { showMenu = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(id = AppR.string.album_more_options)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = AppR.string.album_play_next_all)) },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onPlayNextAll()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = AppR.string.album_add_to_queue_all)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onAddToQueueAll()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistExpressiveHero(
    artist: ArtistModel,
    avatarModel: Any?,
    bannerModel: Any?,
    logoItem: ArtistGalleryItem?,
    accentColor: Color,
    isArtistPlaying: Boolean,
    totalDurationMs: Long,
    fullAlbumsCount: Int,
    singlesCount: Int,
    galleryCount: Int,
    activeSection: Int,
    onSectionChange: (Int) -> Unit,
    onAvatarClick: () -> Unit,
    onBannerClick: () -> Unit,
    onLogoClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onDeepCutsRadioClick: () -> Unit
) {
    val context = LocalContext.current
    val onAccentColor = if (accentColor.luminance() > 0.55f) Color(0xFF121214) else Color.White

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
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
                        .clickable { onBannerClick() }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        )
                        .clickable { onBannerClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.35f),
                            0.45f to Color.Transparent,
                            0.82f to MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            1.0f to MaterialTheme.colorScheme.background
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .tiltOnTouch()
                        .shadow(20.dp, RoundedCornerShape(34.dp))
                        .clip(RoundedCornerShape(34.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.5.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(34.dp))
                        .clickable { onAvatarClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarModel != null) {
                        AsyncImage(
                            model = remember(avatarModel) {
                                ImageRequest.Builder(context)
                                    .data(avatarModel)
                                    .size(512)
                                    .crossfade(true)
                                    .build()
                            },
                            contentDescription = artist.name,
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

                if (logoItem != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    AdaptiveArtistLogoPlate(
                        logoItem = logoItem,
                        maxHeightDp = 64,
                        onClick = onLogoClick
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (fullAlbumsCount > 0) {
                    ArtistStatChip(
                        icon = Icons.Rounded.Album,
                        text = stringResource(id = AppR.string.artist_stat_albums_fmt, fullAlbumsCount),
                        accentColor = accentColor
                    )
                }
                if (singlesCount > 0) {
                    ArtistStatChip(
                        icon = Icons.Rounded.GraphicEq,
                        text = stringResource(id = AppR.string.artist_stat_singles_fmt, singlesCount)
                    )
                }
                ArtistStatChip(
                    icon = Icons.Rounded.MusicNote,
                    text = pluralStringResource(
                        id = AppR.plurals.tracks_count,
                        count = artist.tracks.size,
                        artist.tracks.size
                    )
                )
                ArtistStatChip(
                    icon = Icons.Rounded.Schedule,
                    text = formatArtistDuration(totalDurationMs)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlayAllClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = onAccentColor
                    )
                ) {
                    Icon(
                        imageVector = if (isArtistPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            id = if (isArtistPlaying) AppR.string.album_action_pause else AppR.string.album_action_play
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = onShuffleClick,
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = AppR.string.album_action_shuffle),
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalIconButton(
                    onClick = onDeepCutsRadioClick,
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Casino,
                        contentDescription = stringResource(id = AppR.string.artist_action_radio_mix)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isOverview = activeSection == 0
                    Surface(
                        onClick = { onSectionChange(0) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isOverview) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (isOverview) 2.dp else 0.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LibraryMusic,
                                contentDescription = null,
                                tint = if (isOverview) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = AppR.string.artist_tab_discography),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isOverview) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }

                    Surface(
                        onClick = { onSectionChange(1) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (!isOverview) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (!isOverview) 2.dp else 0.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Collections,
                                contentDescription = null,
                                tint = if (!isOverview) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${stringResource(id = AppR.string.album_tab_gallery)} ($galleryCount)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (!isOverview) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistStatChip(
    icon: ImageVector,
    text: String,
    accentColor: Color? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (accentColor != null) {
            accentColor.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArtistBiographyCard(
    bioText: String,
    isEditing: Boolean,
    bioInput: String,
    isExpanded: Boolean,
    isFetchingWiki: Boolean,
    hasLocalFile: Boolean,
    accentColor: Color,
    onToggleExpand: () -> Unit,
    onStartEdit: () -> Unit,
    onBioInputChange: (String) -> Unit,
    onSaveBio: () -> Unit,
    onFetchWikipedia: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(id = AppR.string.artist_bio_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (hasLocalFile) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = stringResource(id = AppR.string.artist_bio_local_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = onFetchWikipedia,
                        enabled = !isFetchingWiki,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        if (isFetchingWiki) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = accentColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(id = AppR.string.artist_fetch_wiki_btn),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onStartEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = bioInput,
                    onValueChange = onBioInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    label = { Text(stringResource(id = AppR.string.description_label)) },
                    trailingIcon = {
                        IconButton(onClick = onSaveBio) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = accentColor)
                        }
                    }
                )
            } else {
                Text(
                    text = bioText.ifBlank { stringResource(id = AppR.string.click_to_add_description) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (bioText.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isExpanded) 50 else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (bioText.isBlank()) onStartEdit() else onToggleExpand()
                        }
                )

                if (bioText.length > 180) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            id = if (isExpanded) AppR.string.artist_bio_read_less else AppR.string.artist_bio_read_more
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        modifier = Modifier.clickable { onToggleExpand() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistLatestReleaseSpotlight(
    album: AlbumModel,
    accentColor: Color,
    onAlbumClick: () -> Unit,
    onPlayAlbum: () -> Unit
) {
    val context = LocalContext.current
    val coverUri = remember(album.id) { AudioRepository.getAlbumArtUri(album.id) }
    val year = remember(album.title) { ArtistRepository.extractReleaseYear(album) }
    val isSingle = remember(album) { ArtistRepository.isSingleOrEpRelease(album) }

    Surface(
        onClick = onAlbumClick,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.24f),
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = remember(coverUri) {
                        ImageRequest.Builder(context)
                            .data(coverUri)
                            .size(320)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(84.dp)
                        .shadow(8.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = stringResource(id = AppR.string.artist_latest_release_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val typeStr = stringResource(
                        id = if (isSingle) AppR.string.artist_release_type_single_ep else AppR.string.artist_release_type_album
                    )
                    Text(
                        text = buildString {
                            append(typeStr)
                            if (year != null) append(" • $year")
                            append(" • ")
                            append(
                                pluralStringResource(
                                    id = AppR.plurals.tracks_count,
                                    count = album.tracks.size,
                                    album.tracks.size
                                )
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                FilledTonalIconButton(
                    onClick = onPlayAlbum,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentColor,
                        contentColor = if (accentColor.luminance() > 0.55f) Color.Black else Color.White
                    ),
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun SharedTransitionScope.ArtistDiscographySection(
    allAlbums: List<AlbumModel>,
    fullAlbums: List<AlbumModel>,
    singlesAndEps: List<AlbumModel>,
    selectedFilter: Int,
    accentColor: Color,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onFilterChange: (Int) -> Unit,
    onAlbumSelect: (AlbumModel) -> Unit
) {
    val displayed = when (selectedFilter) {
        1 -> fullAlbums.ifEmpty { allAlbums }
        2 -> singlesAndEps.ifEmpty { allAlbums }
        else -> allAlbums
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = AppR.string.artist_tab_discography),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (fullAlbums.isNotEmpty() && singlesAndEps.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == 0,
                        onClick = { onFilterChange(0) },
                        label = {
                            Text("${stringResource(id = AppR.string.album_gallery_filter_all)} (${allAlbums.size})")
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == 1,
                        onClick = { onFilterChange(1) },
                        label = {
                            Text("${stringResource(id = AppR.string.tab_albums)} (${fullAlbums.size})")
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == 2,
                        onClick = { onFilterChange(2) },
                        label = {
                            Text("${stringResource(id = AppR.string.artist_singles_and_eps_tab)} (${singlesAndEps.size})")
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(
                items = displayed,
                key = { it.id },
                contentType = { "artist_album_card" }
            ) { alb ->
                Box(modifier = Modifier.width(162.dp)) {
                    AlbumCard(alb, animatedVisibilityScope) { onAlbumSelect(alb) }
                }
            }
        }
    }
}

@Composable
private fun ArtistTracksHeaderBar(
    totalTracksCount: Int,
    isSearchOpen: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${stringResource(id = AppR.string.popular_tracks)} ($totalTracksCount)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = isSearchOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(id = AppR.string.album_filter_tracks_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun ArtistFolderInfoCard(
    rootFolderPath: String,
    galleryCount: Int,
    hasLogo: Boolean,
    hasLocalBio: Boolean,
    accentColor: Color,
    onOpenGallery: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = accentColor)
                Text(
                    text = stringResource(id = AppR.string.artist_root_folder_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = rootFolderPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArtistStatChip(
                    icon = Icons.Rounded.Collections,
                    text = stringResource(id = AppR.string.artist_folder_images_count_fmt, galleryCount),
                    accentColor = if (galleryCount > 0) accentColor else null
                )
                if (hasLogo) {
                    ArtistStatChip(
                        icon = Icons.Rounded.AutoAwesome,
                        text = stringResource(id = AppR.string.artist_role_logo),
                        accentColor = accentColor
                    )
                }
                if (hasLocalBio) {
                    ArtistStatChip(
                        icon = Icons.Rounded.Language,
                        text = stringResource(id = AppR.string.artist_bio_local_badge),
                        accentColor = accentColor
                    )
                }
            }

            if (galleryCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenGallery,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = AppR.string.album_open_gallery_btn, galleryCount))
                }
            }
        }
    }
}

@Composable
private fun ArtistGallerySectionHeader(
    galleryCount: Int,
    isFetchingImages: Boolean,
    accentColor: Color,
    onFetchOnlineImages: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${stringResource(id = AppR.string.album_tab_gallery)} ($galleryCount)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = stringResource(id = AppR.string.artist_gallery_role_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        FilledTonalButton(
            onClick = onFetchOnlineImages,
            enabled = !isFetchingImages,
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isFetchingImages) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = accentColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(id = AppR.string.action_searching_short))
            } else {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(id = AppR.string.artist_fetch_online_images_btn),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ArtistGalleryEmptyCard(
    isFetching: Boolean,
    onFetchClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.HideImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(id = AppR.string.album_gallery_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = AppR.string.artist_gallery_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onFetchClick,
                enabled = !isFetching,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(id = AppR.string.artist_fetch_online_images_btn))
            }
        }
    }
}

@Composable
private fun ArtistGalleryCardItem(
    item: ArtistGalleryItem,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onAssignRole: (ArtistImageRole) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var showRoleMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = remember(item.uri) {
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .size(600)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = item.name,
                    contentScale = if (item.role == ArtistImageRole.LOGO) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (item.role == ArtistImageRole.LOGO) 12.dp else 0.dp)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Surface(
                        onClick = { showRoleMenu = true },
                        shape = RoundedCornerShape(10.dp),
                        color = if (item.role != ArtistImageRole.GALLERY) {
                            accentColor.copy(alpha = 0.92f)
                        } else {
                            Color.Black.copy(alpha = 0.72f)
                        }
                    ) {
                        Text(
                            text = artistRoleLabel(item.role),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (item.role != ArtistImageRole.GALLERY) Color.Black else Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showRoleMenu,
                        onDismissRequest = { showRoleMenu = false },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        ArtistImageRole.entries.forEach { role ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = artistRoleLabel(role),
                                        fontWeight = if (item.role == role) FontWeight.ExtraBold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    showRoleMenu = false
                                    onAssignRole(role)
                                }
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val info = buildString {
                    if (item.resolutionText.isNotEmpty()) append(item.resolutionText)
                    if (item.sizeBytes > 0L) {
                        if (isNotEmpty()) append(" • ")
                        append(Formatter.formatShortFileSize(context, item.sizeBytes))
                    }
                }
                if (info.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = info,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistArtworkLightbox(
    items: List<ArtistGalleryItem>,
    initialIndex: Int,
    accentColor: Color,
    onAssignRole: (ArtistGalleryItem, ArtistImageRole) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialIndex) { items.size }
    val currentItem = items.getOrNull(pagerState.currentPage)
    var showRoleMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page]
            var scale by remember(page) { mutableFloatStateOf(1f) }
            var offset by remember(page) { mutableStateOf(Offset.Zero) }
            var containerSize by remember(page) { mutableStateOf(IntSize.Zero) }

            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != page) {
                    scale = 1f
                    offset = Offset.Zero
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it }
                    .pointerInput(page) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(page) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val pointerCount = event.changes.size

                                if (scale > 1.01f || pointerCount > 1) {
                                    val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                                    val maxX = ((containerSize.width * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                                    val maxY = ((containerSize.height * (newScale - 1f)) / 2f).coerceAtLeast(0f)

                                    scale = newScale
                                    offset = if (newScale > 1.01f) {
                                        Offset(
                                            x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                            y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                                        )
                                    } else {
                                        Offset.Zero
                                    }

                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) {
                                            change.consume()
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = remember(item.uri) {
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(id = AppR.string.album_action_close))
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${items.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            FilledTonalIconButton(
                onClick = {
                    currentItem?.let { art ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, art.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, art.name))
                    }
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Rounded.Share, contentDescription = stringResource(id = AppR.string.album_track_share))
            }
        }

        currentItem?.let { item ->
            Surface(
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = buildString {
                            append(artistRoleLabel(item.role))
                            if (item.resolutionText.isNotEmpty()) append(" • ${item.resolutionText}")
                            if (item.sizeBytes > 0L) append(" • ${Formatter.formatShortFileSize(context, item.sizeBytes)}")
                        }
                        Text(
                            text = sub,
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box {
                        FilledTonalButton(
                            onClick = { showRoleMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = accentColor,
                                contentColor = Color.Black
                            )
                        ) {
                            Text(
                                text = artistRoleLabel(item.role),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        DropdownMenu(
                            expanded = showRoleMenu,
                            onDismissRequest = { showRoleMenu = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            ArtistImageRole.entries.forEach { role ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = artistRoleLabel(role),
                                            fontWeight = if (item.role == role) FontWeight.ExtraBold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        showRoleMenu = false
                                        onAssignRole(item, role)
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

@Composable
private fun artistRoleLabel(role: ArtistImageRole): String {
    return when (role) {
        ArtistImageRole.AVATAR -> stringResource(id = AppR.string.artist_role_avatar)
        ArtistImageRole.BANNER -> stringResource(id = AppR.string.artist_role_banner)
        ArtistImageRole.LOGO -> stringResource(id = AppR.string.artist_role_logo)
        ArtistImageRole.GALLERY -> stringResource(id = AppR.string.artist_role_gallery)
    }
}

private fun formatArtistDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val mins = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${mins}m" else "${mins}m"
}
