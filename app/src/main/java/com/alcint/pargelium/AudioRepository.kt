package com.alcint.pargelium

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.StringReader
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

enum class ArtworkCategory {
    COVER, DISC, BACK, BOOKLET, ARTIST, EXTRA
}

@Immutable
data class AlbumArtworkItem(
    val uri: Uri,
    val name: String,
    val category: ArtworkCategory,
    val resolution: String = "",
    val sizeBytes: Long = 0L,
    val isRemoteFetched: Boolean = false
)

@Immutable
data class DiscoveredM3uPlaylist(
    val filePath: String,
    val name: String,
    val folderName: String,
    val trackIds: List<Long>,
    val isSingleAlbumDump: Boolean,
    val dominantAlbumTitle: String? = null,
    val coverAlbumId: Long = 0L
)

object AudioRepository {

    private const val ARTWORK_PREFS = "pargelium_artwork_overrides"

    private var cachedTracks: List<AudioTrack>? = null
    private var cachedAlbums: List<AlbumModel>? = null
    private val cachedAlbumArtworks = ConcurrentHashMap<Long, List<AlbumArtworkItem>>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "PargeliumMusicPlayer/2.5 (https://github.com/AlcInt)")
                .header("Accept", "application/json, text/html, */*")
                .build()
            chain.proceed(req)
        }
        .build()

    private val gson = Gson()

    fun clearCache() {
        cachedTracks = null
        cachedAlbums = null
        cachedAlbumArtworks.clear()
    }

    fun invalidateAlbumArtworkCache(albumId: Long) {
        cachedAlbumArtworks.remove(albumId)
    }

    fun getAlbumArtUri(albumId: Long): Uri {
        return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
    }

    fun setArtworkCategoryOverride(context: Context, albumId: Long, artworkUri: Uri, category: ArtworkCategory) {
        val prefs = context.applicationContext.getSharedPreferences(ARTWORK_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("override_${albumId}_${artworkUri}", category.name).apply()

        cachedAlbumArtworks[albumId]?.let { currentList ->
            val updated = currentList.map { item ->
                if (item.uri == artworkUri) item.copy(category = category) else item
            }.sortedWith(compareBy<AlbumArtworkItem> { it.category.ordinal }.thenBy { it.name.lowercase() })
            cachedAlbumArtworks[albumId] = updated
        }
    }

    private fun getArtworkCategoryOverride(context: Context, albumId: Long, artworkUri: Uri): ArtworkCategory? {
        val prefs = context.applicationContext.getSharedPreferences(ARTWORK_PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString("override_${albumId}_${artworkUri}", null) ?: return null
        return runCatching { ArtworkCategory.valueOf(saved) }.getOrNull()
    }

    fun getPreferredDiscArtworkUri(context: Context, track: AudioTrack): Uri? {
        if (track.albumId <= 0L) return null
        cachedAlbumArtworks[track.albumId]?.let { cached ->
            cached.firstOrNull { it.category == ArtworkCategory.DISC }?.uri?.let { return it }
        }
        val dummyAlbum = AlbumModel(
            id = track.albumId,
            title = track.album,
            artist = track.artist,
            tracks = listOf(track)
        )
        val artworks = getAlbumArtworks(context, dummyAlbum)
        return artworks.firstOrNull { it.category == ArtworkCategory.DISC }?.uri
    }

    fun getAlbumFolderPath(context: Context, album: AlbumModel): String? {
        val firstTrack = album.tracks.firstOrNull() ?: return null
        val filePath = getAudioFilePath(context, firstTrack.uri) ?: return null
        return File(filePath).parentFile?.absolutePath
    }

    /**
     * Парсит любой выбранный пользователем .m3u / .m3u8 файл через ContentResolver (SAF)
     * и возвращает название плейлиста и найденные треки (либо все треки из той же папки, если пути относительные).
     */
    suspend fun parseM3uFromUri(
        context: Context,
        uri: Uri,
        allTracks: List<AudioTrack>
    ): Pair<String, List<Long>> = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        var fileName = uri.lastPathSegment?.substringAfterLast("/")?.substringAfterLast(":") ?: "Imported M3U8"
        if (fileName.contains(".")) {
            fileName = fileName.substringBeforeLast(".")
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.forEachLine { line -> lines.add(line) }
                }
            }
        } catch (_: Exception) {}

        val matchedIds = resolveM3uLinesToTrackIds(lines, parentFolder = null, allTracks = allTracks)
        fileName.ifBlank { "M3U8 Playlist" } to matchedIds
    }

    /**
     * Сканирует локальные папки музыки на наличие .m3u и .m3u8 файлов.
     * Автоматически помечает `isSingleAlbumDump = true`, если .m3u8 просто дублирует один альбом из папки,
     * чтобы пользователь мог скрыть сотни одноальбомных .m3u8 одним переключателем!
     */
    suspend fun scanDeviceM3uPlaylists(
        context: Context,
        allTracks: List<AudioTrack>,
        includeSingleAlbumM3u: Boolean = false
    ): List<DiscoveredM3uPlaylist> = withContext(Dispatchers.IO) {
        if (allTracks.isEmpty()) return@withContext emptyList()

        // Индексируем реальные пути аудиофайлов для мгновенного сопоставления O(1)
        val pathToTrackId = HashMap<String, AudioTrack>(allTracks.size)
        val fileNameToTrack = HashMap<String, MutableList<AudioTrack>>(allTracks.size)
        val trackById = allTracks.associateBy { it.id }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        try {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val track = trackById[id] ?: continue
                    val path = if (dataCol != -1) cursor.getString(dataCol) else null
                    val dispName = if (nameCol != -1) cursor.getString(nameCol) else null

                    if (!path.isNullOrBlank()) {
                        pathToTrackId[path.lowercase()] = track
                        val fName = File(path).name.lowercase()
                        fileNameToTrack.getOrPut(fName) { mutableListOf() }.add(track)
                    } else if (!dispName.isNullOrBlank()) {
                        fileNameToTrack.getOrPut(dispName.lowercase()) { mutableListOf() }.add(track)
                    }
                }
            }
        } catch (_: Exception) {}

        val m3uFiles = mutableListOf<File>()
        val seenM3uPaths = HashSet<String>()
        val roots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        roots.forEach { root ->
            if (root != null && root.exists() && root.isDirectory) {
                try {
                    root.walkTopDown()
                        .maxDepth(5)
                        .onFail { _, _ -> }
                        .forEach { file ->
                            if (file.isFile && (file.extension.equals("m3u8", true) || file.extension.equals("m3u", true))) {
                                if (seenM3uPaths.add(file.absolutePath)) {
                                    m3uFiles.add(file)
                                }
                            }
                        }
                } catch (_: Exception) {}
            }
        }

        val discovered = mutableListOf<DiscoveredM3uPlaylist>()

        for (m3uFile in m3uFiles) {
            val lines = try {
                m3uFile.readLines(Charsets.UTF_8)
            } catch (_: Exception) {
                try { m3uFile.readLines(charset("windows-1251")) } catch (_: Exception) { emptyList() }
            }
            if (lines.isEmpty()) continue

            val parentDir = m3uFile.parentFile
            val matchedTracks = mutableListOf<AudioTrack>()

            for (rawLine in lines) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                val cleanLine = try {
                    URLDecoder.decode(trimmed.replace('\\', '/'), "UTF-8")
                } catch (_: Exception) {
                    trimmed.replace('\\', '/')
                }.removePrefix("file://")

                // 1. Пробуем относительный путь от папки с .m3u8
                val resolvedFile = if (cleanLine.startsWith("/")) {
                    File(cleanLine)
                } else if (parentDir != null) {
                    File(parentDir, cleanLine)
                } else null

                val directMatch = resolvedFile?.canonicalPath?.lowercase()?.let { pathToTrackId[it] }
                    ?: resolvedFile?.absolutePath?.lowercase()?.let { pathToTrackId[it] }

                if (directMatch != null) {
                    matchedTracks.add(directMatch)
                    continue
                }

                // 2. Сопоставление по имени файла (приоритет файлам из той же папки)
                val targetFileName = cleanLine.substringAfterLast('/').lowercase()
                val candidates = fileNameToTrack[targetFileName]
                if (!candidates.isNullOrEmpty()) {
                    val sameFolderCandidate = if (parentDir != null) {
                        val parentPrefix = parentDir.absolutePath.lowercase()
                        candidates.firstOrNull { cand ->
                            pathToTrackId.entries.any { (p, t) -> t.id == cand.id && p.startsWith(parentPrefix) }
                        }
                    } else null
                    matchedTracks.add(sameFolderCandidate ?: candidates.first())
                }
            }

            // Если в .m3u8 были битые пути, но в этой же папке лежат треки — предлагаем треки самой папки
            if (matchedTracks.isEmpty() && parentDir != null) {
                val parentPrefix = parentDir.absolutePath.lowercase() + "/"
                val folderTracks = pathToTrackId.entries
                    .filter { it.key.startsWith(parentPrefix) }
                    .map { it.value }
                    .sortedWith(compareBy<AudioTrack> { it.discNumber }.thenBy { it.trackNumber })
                matchedTracks.addAll(folderTracks)
            }

            val distinctTracks = matchedTracks.distinctBy { it.id }
            if (distinctTracks.isEmpty()) continue

            val distinctAlbums = distinctTracks.map { it.albumId }.distinct()
            val isSingleAlbum = distinctAlbums.size == 1 && distinctTracks.size >= 2

            if (!includeSingleAlbumM3u && isSingleAlbum) {
                continue
            }

            val playlistTitle = m3uFile.nameWithoutExtension
                .replace(Regex("^\\d+\\s*[-_.]\\s*"), "")
                .trim()
                .ifEmpty { parentDir?.name ?: "M3U8 Playlist" }

            val firstTrack = distinctTracks.first()
            discovered.add(
                DiscoveredM3uPlaylist(
                    filePath = m3uFile.absolutePath,
                    name = playlistTitle,
                    folderName = parentDir?.name ?: "",
                    trackIds = distinctTracks.map { it.id },
                    isSingleAlbumDump = isSingleAlbum,
                    dominantAlbumTitle = if (isSingleAlbum) firstTrack.album else null,
                    coverAlbumId = firstTrack.albumId
                )
            )
        }

        discovered.sortedWith(
            compareBy<DiscoveredM3uPlaylist> { it.isSingleAlbumDump }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun resolveM3uLinesToTrackIds(
        lines: List<String>,
        parentFolder: File?,
        allTracks: List<AudioTrack>
    ): List<Long> {
        if (allTracks.isEmpty()) return emptyList()

        val matchedIds = mutableListOf<Long>()
        var pendingExtInfTitle: String? = null

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                pendingExtInfTitle = trimmed.substringAfter(",", "").trim().takeIf { it.isNotEmpty() }
                continue
            }
            if (trimmed.startsWith("#")) continue

            val normalized = try {
                URLDecoder.decode(trimmed.replace('\\', '/'), "UTF-8")
            } catch (_: Exception) {
                trimmed.replace('\\', '/')
            }
            val fileName = normalized.substringAfterLast('/').substringBeforeLast('.')

            val foundTrack = allTracks.firstOrNull { t ->
                t.title.equals(fileName, ignoreCase = true) ||
                        fileName.contains(t.title, ignoreCase = true) ||
                        (pendingExtInfTitle != null && pendingExtInfTitle!!.contains(t.title, ignoreCase = true))
            }

            if (foundTrack != null) {
                matchedIds.add(foundTrack.id)
            }
            pendingExtInfTitle = null
        }

        return matchedIds.distinct()
    }

    /**
     * Экспортирует плейлист в стандартный файл .m3u8 в папку Music/Playlists.
     */
    suspend fun exportPlaylistToM3u8(
        context: Context,
        playlistName: String,
        tracks: List<AudioTrack>
    ): File? = withContext(Dispatchers.IO) {
        try {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val playlistsDir = File(musicDir, "Playlists")
            if (!playlistsDir.exists()) playlistsDir.mkdirs()

            val safeFileName = playlistName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Playlist" }
            val outFile = File(playlistsDir, "$safeFileName.m3u8")

            val sb = StringBuilder()
            sb.appendLine("#EXTM3U")
            sb.appendLine("#PLAYLIST:$playlistName")

            for (track in tracks) {
                val sec = (track.duration / 1000L).coerceAtLeast(0L)
                sb.appendLine("#EXTINF:$sec,${track.artist} - ${track.title}")
                val realPath = getAudioFilePath(context, track.uri) ?: track.uri.toString()
                sb.appendLine(realPath)
            }

            outFile.writeText(sb.toString(), Charsets.UTF_8)
            MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), null, null)
            outFile
        } catch (_: Exception) {
            null
        }
    }

    fun getAlbumArtworks(context: Context, album: AlbumModel, forceRefresh: Boolean = false): List<AlbumArtworkItem> {
        if (!forceRefresh) {
            cachedAlbumArtworks[album.id]?.let { return it }
        }

        val results = mutableListOf<AlbumArtworkItem>()
        val seenNames = HashSet<String>()
        val seenPaths = HashSet<String>()

        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "avif")
        val subFolderKeywords = setOf("scans", "scan", "artwork", "artworks", "booklet", "covers", "cover", "images", "art", "extra", "discogs")

        val firstTrack = album.tracks.firstOrNull()
        val trackPath = if (firstTrack != null) getAudioFilePath(context, firstTrack.uri) else null
        val albumDir = trackPath?.let { File(it).parentFile }

        fun inspectAndAddFile(file: File, isRemote: Boolean = false) {
            if (!file.exists() || !file.isFile || !file.canRead()) return
            val ext = file.extension.lowercase()
            if (!imageExtensions.contains(ext)) return
            if (!seenPaths.add(file.absolutePath)) return

            val baseName = file.nameWithoutExtension
            seenNames.add(baseName.lowercase())

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                BitmapFactory.decodeFile(file.absolutePath, options)
            } catch (_: Exception) {}

            val res = if (options.outWidth > 0 && options.outHeight > 0) {
                "${options.outWidth} × ${options.outHeight}"
            } else ""

            val fileUri = Uri.fromFile(file)
            val effectiveCategory = getArtworkCategoryOverride(context, album.id, fileUri)
                ?: classifyArtworkName(baseName)

            results.add(
                AlbumArtworkItem(
                    uri = fileUri,
                    name = baseName,
                    category = effectiveCategory,
                    resolution = res,
                    sizeBytes = file.length(),
                    isRemoteFetched = isRemote
                )
            )
        }

        if (albumDir != null && albumDir.exists() && albumDir.isDirectory) {
            try {
                albumDir.listFiles()?.forEach { child ->
                    if (child.isFile) {
                        inspectAndAddFile(child)
                    } else if (child.isDirectory && subFolderKeywords.any { child.name.contains(it, ignoreCase = true) }) {
                        child.listFiles()?.forEach { subFile ->
                            if (subFile.isFile) inspectAndAddFile(subFile)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val remoteCacheDir = File(context.getDir("album_remote_artworks", Context.MODE_PRIVATE), "album_${album.id}")
        if (remoteCacheDir.exists() && remoteCacheDir.isDirectory) {
            remoteCacheDir.listFiles()?.forEach { remoteFile ->
                if (remoteFile.isFile) inspectAndAddFile(remoteFile, isRemote = true)
            }
        }

        if (firstTrack != null) {
            var bucketId: String? = null
            try {
                context.contentResolver.query(
                    firstTrack.uri,
                    arrayOf(MediaStore.Audio.Media.BUCKET_ID),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_ID)
                        if (idx != -1) bucketId = cursor.getString(idx)
                    }
                }
            } catch (_: Exception) {}

            if (bucketId != null) {
                try {
                    val imgCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val projection = arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.WIDTH,
                        MediaStore.Images.Media.HEIGHT,
                        MediaStore.Images.Media.SIZE,
                        MediaStore.Images.Media.DATA
                    )
                    context.contentResolver.query(
                        imgCollection,
                        projection,
                        "${MediaStore.Images.Media.BUCKET_ID} = ?",
                        arrayOf(bucketId),
                        null
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val wCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                        val hCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                        val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                        val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            val fullName = cursor.getString(nameCol) ?: "Artwork"
                            val baseName = fullName.substringBeforeLast(".")
                            val dataPath = if (dataCol != -1) cursor.getString(dataCol) else null

                            if (dataPath != null && seenPaths.contains(dataPath)) continue
                            if (!seenNames.add(baseName.lowercase())) continue

                            val w = if (wCol != -1) cursor.getInt(wCol) else 0
                            val h = if (hCol != -1) cursor.getInt(hCol) else 0
                            val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                            val res = if (w > 0 && h > 0) "$w × $h" else ""

                            val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                            val effectiveCategory = getArtworkCategoryOverride(context, album.id, contentUri)
                                ?: classifyArtworkName(baseName)

                            results.add(
                                AlbumArtworkItem(
                                    uri = contentUri,
                                    name = baseName,
                                    category = effectiveCategory,
                                    resolution = res,
                                    sizeBytes = size
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val sorted = results.sortedWith(
            compareBy<AlbumArtworkItem> { it.category.ordinal }
                .thenBy { it.name.lowercase() }
        ).toMutableList()

        val mainCoverUri = getAlbumArtUri(album.id)
        val mainCoverOverride = getArtworkCategoryOverride(context, album.id, mainCoverUri) ?: ArtworkCategory.COVER
        if (sorted.none { it.uri == mainCoverUri } && sorted.none { it.category == ArtworkCategory.COVER }) {
            sorted.add(
                0,
                AlbumArtworkItem(
                    uri = mainCoverUri,
                    name = album.title,
                    category = mainCoverOverride
                )
            )
        }

        cachedAlbumArtworks[album.id] = sorted
        return sorted
    }

    suspend fun fetchOnlineReleaseArtworks(context: Context, album: AlbumModel): List<AlbumArtworkItem> = withContext(Dispatchers.IO) {
        val remoteCacheDir = File(context.getDir("album_remote_artworks", Context.MODE_PRIVATE), "album_${album.id}")
        if (!remoteCacheDir.exists()) remoteCacheDir.mkdirs()

        val cleanArtist = album.artist.replace(Regex("(?i)<unknown>"), "").trim()
        val cleanAlbum = album.title.replace(Regex("(?i)\\(.*?edition.*?\\)|\\[.*?]"), "").trim()

        if (cleanAlbum.isEmpty()) return@withContext getAlbumArtworks(context, album, forceRefresh = true)

        val discoveredImages = mutableListOf<Pair<String, String>>()

        try {
            val mbQuery = URLEncoder.encode("release:\"$cleanAlbum\" AND artist:\"$cleanArtist\"", "UTF-8")
            val mbUrl = "https://musicbrainz.org/ws/2/release/?query=$mbQuery&fmt=json&limit=4"

            val mbIds = mutableListOf<String>()
            httpClient.newCall(Request.Builder().url(mbUrl).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val releases = json.getAsJsonArray("releases")
                        if (releases != null) {
                            for (i in 0 until releases.size()) {
                                val r = releases.get(i).asJsonObject
                                r.get("id")?.asString?.let { mbIds.add(it) }
                            }
                        }
                    }
                }
            }

            for (mbId in mbIds.take(2)) {
                val caaUrl = "https://coverartarchive.org/release/$mbId"
                httpClient.newCall(Request.Builder().url(caaUrl).build()).execute().use { caaResp ->
                    if (caaResp.isSuccessful) {
                        val caaBody = caaResp.body?.string()
                        if (!caaBody.isNullOrBlank()) {
                            val caaJson = gson.fromJson(caaBody, JsonObject::class.java)
                            val images = caaJson.getAsJsonArray("images")
                            if (images != null) {
                                for (j in 0 until images.size()) {
                                    val imgObj = images.get(j).asJsonObject
                                    val typesArr = imgObj.getAsJsonArray("types")
                                    val typesList = mutableListOf<String>()
                                    if (typesArr != null) {
                                        for (k in 0 until typesArr.size()) {
                                            typesList.add(typesArr.get(k).asString.lowercase())
                                        }
                                    }
                                    val thumbs = imgObj.getAsJsonObject("thumbnails")
                                    val imgUrl = thumbs?.get("large")?.asString
                                        ?: thumbs?.get("1200")?.asString
                                        ?: imgObj.get("image")?.asString

                                    if (!imgUrl.isNullOrBlank()) {
                                        val prefix = when {
                                            typesList.any { it.contains("medium") || it.contains("disc") || it.contains("vinyl") } -> "Disc_Scan_${j + 1}"
                                            typesList.any { it.contains("back") || it.contains("spine") || it.contains("tray") } -> "Back_Cover_${j + 1}"
                                            typesList.any { it.contains("booklet") || it.contains("liner") } -> "Booklet_Page_${j + 1}"
                                            typesList.any { it.contains("front") } -> "Front_Cover_${j + 1}"
                                            else -> "Extra_Art_${j + 1}"
                                        }
                                        discoveredImages.add(imgUrl.replace("http://", "https://") to prefix)
                                    }
                                }
                            }
                        }
                    }
                }
                if (discoveredImages.size >= 4) break
            }
        } catch (_: Exception) {}

        try {
            val discogsQuery = URLEncoder.encode("$cleanArtist $cleanAlbum", "UTF-8")
            val searchUrl = "https://www.discogs.com/search/?q=$discogsQuery&type=release"
            var releasePath: String? = null

            httpClient.newCall(Request.Builder().url(searchUrl).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val releaseRegex = Regex("""href="(/release/\d+-[^"]+)"""")
                    releasePath = releaseRegex.find(html)?.groupValues?.getOrNull(1)
                }
            }

            if (releasePath != null) {
                val releaseUrl = "https://www.discogs.com$releasePath"
                httpClient.newCall(Request.Builder().url(releaseUrl).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        val discogsImgRegex = Regex("""(https://i\.discogs\.com/[^\s"']+\.(?:jpeg|jpg|png|webp))""", RegexOption.IGNORE_CASE)
                        val foundUrls = discogsImgRegex.findAll(html)
                            .map { it.value.replace("&amp;", "&") }
                            .filter { !it.contains("spacer.gif") && (it.contains("Rs:fit") || it.contains("h:") || it.length > 45) }
                            .distinct()
                            .take(6)
                            .toList()

                        foundUrls.forEachIndexed { idx, url ->
                            val guessedRole = when (idx) {
                                0 -> if (discoveredImages.isEmpty()) "Discogs_Cover_1" else "Discogs_Art_1"
                                1 -> "Discogs_Disc_Vinyl_2"
                                2 -> "Discogs_Back_Cover_3"
                                else -> "Discogs_Booklet_Scan_${idx + 1}"
                            }
                            discoveredImages.add(url to guessedRole)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val uniqueDownloads = discoveredImages.distinctBy { it.first }.take(8)
        for ((url, baseName) in uniqueDownloads) {
            val targetFile = File(remoteCacheDir, "$baseName.jpg")
            if (targetFile.exists() && targetFile.length() > 1024L) continue
            try {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.size > 2048) {
                            targetFile.writeBytes(bytes)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return@withContext getAlbumArtworks(context, album, forceRefresh = true)
    }

    private fun classifyArtworkName(rawName: String): ArtworkCategory {
        val n = rawName.trim().lowercase()
        return when {
            n in setOf("cover", "folder", "front", "album", "albumart", "thumb", "default") ||
                    n.startsWith("cover") || n.startsWith("front") || n.startsWith("folder") || n.contains("albumart") -> ArtworkCategory.COVER
            n.contains("disc") || n.contains("disk") || n.contains("vinyl") || n.contains("cd") ||
                    n.contains("lp") || n.contains("plate") || n.contains("matrix") || n.contains("media") || n.contains("side") -> ArtworkCategory.DISC
            n.contains("back") || n.contains("rear") || n.contains("tray") || n.contains("inlay") ||
                    n.contains("inside") || n.contains("spine") || n.contains("obi") -> ArtworkCategory.BACK
            n.contains("booklet") || n.contains("page") || n.contains("scan") || n.contains("insert") ||
                    n.contains("leaflet") || n.contains("lyric") || n.contains("credits") || n.contains("liner") || n.contains("notes") -> ArtworkCategory.BOOKLET
            n.contains("artist") || n.contains("band") || n.contains("photo") || n.contains("portrait") ||
                    n.contains("group") || n.contains("member") -> ArtworkCategory.ARTIST
            else -> ArtworkCategory.EXTRA
        }
    }

    fun processAlbums(tracks: List<AudioTrack>): List<AlbumModel> {
        if (cachedAlbums != null) return cachedAlbums!!

        val albums = tracks.groupBy { it.albumId }.map { (_, albumTracks) ->
            val first = albumTracks.first()
            val sortedTracks = albumTracks.sortedWith(
                compareBy<AudioTrack> { it.discNumber }.thenBy { it.trackNumber }
            )

            val artistCounts = HashMap<String, Int>()
            var mainArtist = first.artist
            var maxCount = 0
            for (t in albumTracks) {
                val count = (artistCounts[t.artist] ?: 0) + 1
                artistCounts[t.artist] = count
                if (count > maxCount) {
                    maxCount = count
                    mainArtist = t.artist
                }
            }

            AlbumModel(first.albumId, first.album, mainArtist, sortedTracks)
        }.sortedBy { it.title }

        cachedAlbums = albums
        return albums
    }

    fun getAudioFilePath(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val colIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                if (colIdx != -1 && cursor.moveToFirst()) {
                    cursor.getString(colIdx)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun findLrcFileForTrack(context: Context, track: AudioTrack): File? {
        val filePath = getAudioFilePath(context, track.uri) ?: return null
        val audioFile = File(filePath)
        val parentDir = audioFile.parentFile ?: return null

        val nameWithoutExt = audioFile.nameWithoutExtension
        val extensions = listOf("ttml", "lrc")

        for (ext in extensions) {
            val sameName = File(parentDir, "$nameWithoutExt.$ext")
            if (sameName.exists() && sameName.canRead()) return sameName
        }

        for (ext in extensions) {
            val titleFile = File(parentDir, "${track.title}.$ext")
            if (titleFile.exists() && titleFile.canRead()) return titleFile
        }

        try {
            val matchedFile = parentDir.listFiles { file ->
                file.isFile && (file.extension.equals("ttml", ignoreCase = true) || file.extension.equals("lrc", ignoreCase = true)) &&
                        (file.nameWithoutExtension.equals(nameWithoutExt, ignoreCase = true) ||
                                file.nameWithoutExtension.equals(track.title, ignoreCase = true))
            }?.firstOrNull()

            if (matchedFile != null && matchedFile.canRead()) {
                return matchedFile
            }
        } catch (e: Exception) { }

        return null
    }

    fun findEmbeddedLyrics(context: Context, trackUri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, trackUri)
            val filePath = getAudioFilePath(context, trackUri)
            if (filePath != null) {
                readMetadataTagsFromFile(File(filePath))
            } else null
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    private fun readMetadataTagsFromFile(file: File): String? {
        if (!file.exists() || !file.canRead()) return null
        return try {
            val headerBytes = ByteArray(1024 * 512)
            val read = file.inputStream().use { it.read(headerBytes) }
            if (read <= 0) return null
            val content = String(headerBytes, 0, read, Charsets.ISO_8859_1)

            val tagKeys = listOf("LYRICS-TTML", "TTML", "lyrics-ttml", "ttml", "LYRICS", "lyrics", "USLT")
            for (key in tagKeys) {
                val index = content.indexOf(key)
                if (index != -1) {
                    val xmlStart = content.indexOf("<tt", index)
                    if (xmlStart != -1 && xmlStart - index < 150) {
                        val xmlEnd = content.indexOf("</tt>", xmlStart)
                        if (xmlEnd != -1) {
                            val rawXml = content.substring(xmlStart, xmlEnd + 5)
                            return String(rawXml.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun findLrcContentForTrack(context: Context, track: AudioTrack): String? {
        val embedded = findEmbeddedLyrics(context, track.uri)
        if (!embedded.isNullOrBlank()) {
            return embedded
        }

        val file = findLrcFileForTrack(context, track) ?: return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                file.readText(charset("windows-1251"))
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun parseLyrics(rawContent: String?): List<LyricLine> {
        if (rawContent.isNullOrBlank()) return emptyList()
        val text = rawContent.trim()

        val isTtml = text.contains("<tt", ignoreCase = true) ||
                text.contains("xmlns=\"http://www.w3.org/ns/ttml\"", ignoreCase = true) ||
                text.contains("<body", ignoreCase = true)

        return if (isTtml) {
            parseTtml(text)
        } else {
            parseLrc(text)
        }
    }

    private fun parseTtmlTime(timeStr: String): Long {
        val clean = timeStr.trim().removeSuffix("s")
        return try {
            if (clean.contains(":")) {
                val parts = clean.split(":")
                if (parts.size == 2) {
                    val min = parts[0].toLong()
                    val secParts = parts[1].split(".")
                    val sec = secParts[0].toLong()
                    val ms = if (secParts.size > 1) {
                        secParts[1].take(3).padEnd(3, '0').toLong()
                    } else 0L
                    (min * 60_000L) + (sec * 1000L) + ms
                } else if (parts.size == 3) {
                    val hours = parts[0].toLong()
                    val min = parts[1].toLong()
                    val secParts = parts[2].split(".")
                    val sec = secParts[0].toLong()
                    val ms = if (secParts.size > 1) {
                        secParts[1].take(3).padEnd(3, '0').toLong()
                    } else 0L
                    (hours * 3_600_000L) + (min * 60_000L) + (sec * 1000L) + ms
                } else 0L
            } else {
                (clean.toDouble() * 1000.0).toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseTtml(xml: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentLineBeginMs = 0L
            var currentLineWords = mutableListOf<SyllableWord>()
            var currentLineTextBuilder = StringBuilder()

            var currentSpanBeginMs = 0L
            var currentSpanEndMs = 0L
            var insideSpan = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.lowercase()

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "p" -> {
                                currentLineWords = mutableListOf()
                                currentLineTextBuilder = StringBuilder()
                                val beginAttr = parser.getAttributeValue(null, "begin")
                                    ?: (0 until parser.attributeCount).firstNotNullOfOrNull { i ->
                                        if (parser.getAttributeName(i).equals("begin", ignoreCase = true)) parser.getAttributeValue(i) else null
                                    }
                                currentLineBeginMs = if (beginAttr != null) parseTtmlTime(beginAttr) else 0L
                            }
                            "span" -> {
                                val beginAttr = parser.getAttributeValue(null, "begin")
                                    ?: (0 until parser.attributeCount).firstNotNullOfOrNull { i ->
                                        if (parser.getAttributeName(i).equals("begin", ignoreCase = true)) parser.getAttributeValue(i) else null
                                    }
                                val endAttr = parser.getAttributeValue(null, "end")
                                    ?: (0 until parser.attributeCount).firstNotNullOfOrNull { i ->
                                        if (parser.getAttributeName(i).equals("end", ignoreCase = true)) parser.getAttributeValue(i) else null
                                    }

                                currentSpanBeginMs = if (beginAttr != null) parseTtmlTime(beginAttr) else currentLineBeginMs
                                currentSpanEndMs = if (endAttr != null) parseTtmlTime(endAttr) else (currentSpanBeginMs + 300L)
                                insideSpan = true
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            if (insideSpan) {
                                currentLineWords.add(
                                    SyllableWord(
                                        text = text,
                                        startMs = currentSpanBeginMs,
                                        endMs = currentSpanEndMs
                                    )
                                )
                            }
                            if (currentLineTextBuilder.isNotEmpty()) {
                                currentLineTextBuilder.append(" ")
                            }
                            currentLineTextBuilder.append(text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tagName) {
                            "span" -> {
                                insideSpan = false
                            }
                            "p" -> {
                                val fullText = currentLineTextBuilder.toString().trim()
                                if (fullText.isNotEmpty()) {
                                    val effectiveTime = currentLineWords.firstOrNull()?.startMs ?: currentLineBeginMs
                                    lines.add(
                                        LyricLine(
                                            timeMs = effectiveTime,
                                            text = fullText,
                                            words = currentLineWords
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return lines.sortedBy { it.timeMs }
    }

    private fun parseLrc(content: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val linePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})\\](.*)")

        content.lines().forEach { rawLine ->
            val matcher = linePattern.matcher(rawLine.trim())
            if (matcher.matches()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msStr = matcher.group(3) ?: "0"
                val ms = if (msStr.length == 2) msStr.toLong() * 10L else msStr.take(3).padEnd(3, '0').toLong()
                val text = matcher.group(4)?.trim() ?: ""

                val timeMs = (min * 60_000L) + (sec * 1000L) + ms
                if (text.isNotBlank()) {
                    lines.add(LyricLine(timeMs = timeMs, text = text, words = emptyList()))
                }
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    fun findCanvasForTrack(context: Context, trackUri: Uri): Uri? {
        var trackNameClean: String? = null
        var bucketId: String? = null

        try {
            val projection = arrayOf(MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.BUCKET_ID)
            context.contentResolver.query(trackUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val fullName = cursor.getString(0) ?: ""
                    trackNameClean = fullName.substringBeforeLast(".")
                    val bIdx = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_ID)
                    if (bIdx != -1) {
                        bucketId = cursor.getString(bIdx)
                    }
                }
            }
        } catch (e: Exception) { }

        val cleanName = trackNameClean ?: return null
        val bId = bucketId ?: return null

        try {
            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
            val selection = "${MediaStore.Video.Media.BUCKET_ID} = ?"
            val selectionArgs = arrayOf(bId)

            context.contentResolver.query(videoUri, projection, selection, selectionArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val vidFullName = cursor.getString(1) ?: ""
                    val vidNameClean = vidFullName.substringBeforeLast(".")

                    if (vidNameClean.equals(cleanName, ignoreCase = true) ||
                        vidNameClean.contains(cleanName, ignoreCase = true) ||
                        cleanName.contains(vidNameClean, ignoreCase = true)) {
                        return ContentUris.withAppendedId(videoUri, id)
                    }
                }
            }
        } catch (e: Exception) { }
        return null
    }

    fun getTrackMetadata(context: Context, uri: Uri): String {
        val retriever = MediaMetadataRetriever()
        var mime = ""
        var bitrateRaw = 0
        var sampleRate = 0
        var bitsPerSample = 0

        try {
            retriever.setDataSource(context, uri)
            mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            bitrateRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
                bitsPerSample = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }

        if (sampleRate == 0 || bitsPerSample == 0) {
            var extractor: MediaExtractor? = null
            try {
                extractor = MediaExtractor()
                extractor.setDataSource(context, uri, null)
                if (extractor.trackCount > 0) {
                    val format = extractor.getTrackFormat(0)

                    if (mime.isEmpty() && format.containsKey(MediaFormat.KEY_MIME)) {
                        mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    }
                    if (sampleRate == 0 && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (bitsPerSample == 0 && format.containsKey("bits-per-sample")) {
                        bitsPerSample = format.getInteger("bits-per-sample")
                    } else if (bitsPerSample == 0 && format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        val pcm = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        bitsPerSample = when (pcm) {
                            android.media.AudioFormat.ENCODING_PCM_8BIT -> 8
                            android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
                            android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                            android.media.AudioFormat.ENCODING_PCM_32BIT,
                            android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
                            else -> 0
                        }
                    }
                }
            } catch (e: Exception) {
            } finally {
                extractor?.release()
            }
        }

        val formatName = when {
            mime.contains("flac", true) -> "FLAC"
            mime.contains("alac", true) -> "ALAC"
            mime.contains("wav", true) || mime.contains("x-wav", true) -> "WAV"
            mime.contains("mpeg", true) -> "MP3"
            mime.contains("mp4", true) || mime.contains("m4a", true) || mime.contains("aac", true) -> "AAC"
            mime.contains("ogg", true) -> "OGG"
            mime.contains("opus", true) -> "OPUS"
            mime.contains("/", true) -> mime.substringAfter("/").uppercase()
            else -> "AUDIO"
        }

        val parts = mutableListOf<String>()
        parts.add(formatName)

        if (bitsPerSample > 0) {
            parts.add("${bitsPerSample}-BIT")
        }
        if (sampleRate > 0) {
            val khz = sampleRate / 1000f
            val khzStr = if (khz % 1.0f == 0.0f) "${khz.toInt()} kHz" else "$khz kHz"
            parts.add(khzStr)
        }

        if (bitrateRaw > 0) {
            val kbps = bitrateRaw / 1000
            parts.add("$kbps kbps")
        }

        val result = parts.joinToString(" • ")
        return if (result.isNotBlank()) result else "UNKNOWN FORMAT"
    }

    fun forceScan(context: Context, onComplete: () -> Unit) {
        val pathsToScan = mutableListOf<String>()
        val extensions = setOf(
            "mp3", "wav", "flac", "m4a", "mp4", "ogg", "opus", "m4b", "lrc", "ttml",
            "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "avif", "m3u", "m3u8"
        )

        val folders = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        )

        try {
            folders.forEach { folder ->
                if (folder.exists() && folder.isDirectory) {
                    folder.walkTopDown()
                        .onFail { _, _ -> }
                        .forEach { file ->
                            if (file.isFile && extensions.contains(file.extension.lowercase())) {
                                pathsToScan.add(file.absolutePath)
                            }
                        }
                }
            }
        } catch (e: Exception) { }

        if (pathsToScan.isNotEmpty()) {
            val count = AtomicInteger(0)
            val total = pathsToScan.size
            MediaScannerConnection.scanFile(context, pathsToScan.toTypedArray(), null) { _, _ ->
                if (count.incrementAndGet() == total) {
                    onComplete()
                }
            }
        } else {
            onComplete()
        }
    }

    fun getAudioTracks(context: Context, forceReload: Boolean = false): List<AudioTrack> {
        if (!forceReload && cachedTracks != null) {
            return cachedTracks!!
        }

        val tracks = mutableListOf<AudioTrack>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TRACK,
            "is_music"
        )
        val discColumn = "disc_number"
        val finalProjection = try { projection + discColumn } catch (e: Exception) { projection }
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            context.contentResolver.query(collection, finalProjection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val discColIdx = cursor.getColumnIndex(discColumn)

                while (cursor.moveToNext()) {
                    val duration = cursor.getLong(durCol)
                    if (duration in 1..14999) continue

                    val id = cursor.getLong(idCol)
                    var title = cursor.getString(titleCol)
                    val artist = cursor.getString(artistCol) ?: "<Unknown>"
                    val album = cursor.getString(albumCol) ?: "<Unknown Album>"
                    val albumId = cursor.getLong(albumIdCol)
                    val displayName = cursor.getString(nameCol) ?: ""
                    val trackRaw = cursor.getInt(trackCol)
                    val trackNum = if (trackRaw >= 1000) trackRaw % 1000 else trackRaw
                    val discNum = if (discColIdx != -1) cursor.getInt(discColIdx) else 1

                    if (title.isNullOrEmpty()) {
                        title = if (displayName.contains(".")) displayName.substringBeforeLast(".") else displayName
                    }
                    if (title.isEmpty()) title = "Unknown Title"

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    tracks.add(AudioTrack(id, title, artist, album, contentUri, albumId, duration, trackNum, if (discNum < 1) 1 else discNum))
                }
            }
        } catch (e: Exception) { }

        cachedTracks = tracks
        cachedAlbums = null

        return tracks
    }
}