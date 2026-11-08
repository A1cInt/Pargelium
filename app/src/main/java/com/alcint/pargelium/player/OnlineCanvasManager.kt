package com.alcint.pargelium.player

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.alcint.pargelium.AudioTrack
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class CanvasSourceMode(val label: String) {
    BOTH_SPOTIFY_PRIORITY("Spotify + Apple Music"),
    BOTH_APPLE_PRIORITY("Apple Music + Spotify"),
    SPOTIFY_ONLY("Spotify Canvas Only"),
    APPLE_ONLY("Apple Music Only")
}

object OnlineCanvasManager {

    private const val PREFS_NAME = "pargelium_canvas_prefs"
    private const val KEY_ENABLED = "online_canvas_enabled"
    private const val KEY_SOURCE_MODE = "online_canvas_source_mode"
    private const val NO_CANVAS_SENTINEL = "NONE"
    private const val NEGATIVE_CACHE_TTL_MS = 24 * 3600 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                .header("Accept", "application/json, text/html, */*")
                .build()
            chain.proceed(req)
        }
        .build()

    private val gson = Gson()
    private val memoryCache = LruCache<String, String>(256)

    private val parenCleanupRegex = Regex("(?i)\\s*\\(?(feat\\.|ft\\.|remaster|official|lyric|video|audio|radio|bonus|deluxe|explicit|clean|vinyl).*?\\)?")
    private val bracketCleanupRegex = Regex("(?i)\\s*\\[(feat\\.|ft\\.|remaster|official|lyric|video|audio|radio|bonus|deluxe|explicit|clean|vinyl).*?]")
    private val dashCleanupRegex = Regex("(?i)\\s*-.*?(Remaster|Edit|Mix|Version|Live|Mono|Stereo|Vinyl).*")

    private val m3u8StreamRegex = Regex("""(https?://[^\s"'<>\\]+\.m3u8[^\s"'<>\\]*)""")
    private val mp4DirectRegex = Regex("""(https?://[^\s"'<>\\]+(?:canvaz\.scdn\.co|\.mp4)[^\s"'<>\\]*)""", RegexOption.IGNORE_CASE)
    private val spotifyTrackIdRegex = Regex("""(?:open\.spotify\.com/track/|spotify:track:|SPOTIFY_SONG::|"id"\s*:\s*")([a-zA-Z0-9]{22})""")

    fun isOnlineCanvasEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    fun setOnlineCanvasEnabled(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getCanvasSourceMode(context: Context): CanvasSourceMode {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ord = prefs.getInt(KEY_SOURCE_MODE, 0)
        return CanvasSourceMode.entries.getOrElse(ord) { CanvasSourceMode.BOTH_SPOTIFY_PRIORITY }
    }

    fun setCanvasSourceMode(context: Context, mode: CanvasSourceMode) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SOURCE_MODE, mode.ordinal).apply()
        memoryCache.evictAll()
    }

    fun clearCache(context: Context) {
        memoryCache.evictAll()
        runCatching {
            File(context.filesDir, "spotify_canvas_videos").listFiles()?.forEach { it.delete() }
            File(context.getDir("online_canvas_cache", Context.MODE_PRIVATE), "").listFiles()?.forEach { it.delete() }
        }
    }

    private fun cleanMetadata(text: String): String {
        return text
            .replace(parenCleanupRegex, "")
            .replace(bracketCleanupRegex, "")
            .replace(dashCleanupRegex, "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun findOnlineCanvas(context: Context, track: AudioTrack): Uri? = withContext(Dispatchers.IO) {
        if (!isOnlineCanvasEnabled(context)) return@withContext null

        val valid = track.artist.isNotBlank() && track.title.isNotBlank() && !track.artist.contains("Unknown", true)
        if (!valid) return@withContext null

        val cleanArtist = cleanMetadata(track.artist)
        val cleanTitle = cleanMetadata(track.title)
        if (cleanArtist.isEmpty() || cleanTitle.isEmpty()) return@withContext null

        val mode = getCanvasSourceMode(context)
        val baseKey = "${cleanArtist.lowercase()}_${cleanTitle.lowercase()}"
        val cacheKey = "${mode.ordinal}_$baseKey"

        // 1. Проверяем оперативный кэш (включая негативный результат "NONE")
        memoryCache.get(cacheKey)?.let { cached ->
            return@withContext if (cached == NO_CANVAS_SENTINEL) null else Uri.parse(cached)
        }

        val videoDir = File(context.filesDir, "spotify_canvas_videos").apply { if (!exists()) mkdirs() }
        val localMp4File = File(videoDir, "canvas_${baseKey.hashCode()}.mp4")

        if (mode != CanvasSourceMode.APPLE_ONLY && localMp4File.exists() && localMp4File.length() > 10_240L) {
            val fileUri = Uri.fromFile(localMp4File)
            memoryCache.put(cacheKey, fileUri.toString())
            return@withContext fileUri
        }

        val urlCacheDir = context.getDir("online_canvas_cache", Context.MODE_PRIVATE)
        val diskUrlFile = File(urlCacheDir, "canvas_${cacheKey.hashCode()}.txt")
        if (diskUrlFile.exists()) {
            val cachedUrl = runCatching { diskUrlFile.readText().trim() }.getOrNull()
            if (cachedUrl == NO_CANVAS_SENTINEL) {
                if (System.currentTimeMillis() - diskUrlFile.lastModified() < NEGATIVE_CACHE_TTL_MS) {
                    memoryCache.put(cacheKey, NO_CANVAS_SENTINEL)
                    return@withContext null
                }
            } else if (!cachedUrl.isNullOrEmpty() && cachedUrl.startsWith("http")) {
                memoryCache.put(cacheKey, cachedUrl)
                return@withContext Uri.parse(cachedUrl)
            }
        }

        // 2. Последовательный приоритетный поиск (не жжёт батарею двойным параллельным скачиванием)
        val resolvedUri: Uri? = when (mode) {
            CanvasSourceMode.SPOTIFY_ONLY -> {
                fetchAndSaveSpotifyCanvas(cleanArtist, cleanTitle, localMp4File)
            }
            CanvasSourceMode.APPLE_ONLY -> {
                fetchAppleMusicVideo(cleanArtist, cleanTitle)?.let { Uri.parse(it) }
            }
            CanvasSourceMode.BOTH_SPOTIFY_PRIORITY -> {
                fetchAndSaveSpotifyCanvas(cleanArtist, cleanTitle, localMp4File)
                    ?: fetchAppleMusicVideo(cleanArtist, cleanTitle)?.let { Uri.parse(it) }
            }
            CanvasSourceMode.BOTH_APPLE_PRIORITY -> {
                fetchAppleMusicVideo(cleanArtist, cleanTitle)?.let { Uri.parse(it) }
                    ?: fetchAndSaveSpotifyCanvas(cleanArtist, cleanTitle, localMp4File)
            }
        }

        if (resolvedUri != null) {
            val uriStr = resolvedUri.toString()
            memoryCache.put(cacheKey, uriStr)
            if (uriStr.startsWith("http")) {
                runCatching { diskUrlFile.writeText(uriStr) }
            }
        } else {
            // Кэшируем отсутствие канваса, чтобы не спамить сеть при каждом открытии трека
            memoryCache.put(cacheKey, NO_CANVAS_SENTINEL)
            runCatching { diskUrlFile.writeText(NO_CANVAS_SENTINEL) }
        }

        return@withContext resolvedUri
    }

    private fun fetchAndSaveSpotifyCanvas(artist: String, title: String, targetMp4File: File): Uri? {
        val remoteMp4Url = fetchSpotifyCanvasUrl(artist, title) ?: return null
        val cleanUrl = remoteMp4Url
            .replace("\\u002F", "/")
            .replace("\\/", "/")
            .replace("&amp;", "&")

        try {
            val req = Request.Builder().url(cleanUrl).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.size > 10_240) {
                        targetMp4File.writeBytes(bytes)
                        return Uri.fromFile(targetMp4File)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        return Uri.parse(cleanUrl)
    }

    private fun fetchSpotifyCanvasUrl(artist: String, title: String): String? {
        val encArtist = URLEncoder.encode(artist, "UTF-8")
        val encTitle = URLEncoder.encode(title, "UTF-8")
        val encQuery = URLEncoder.encode("$artist $title", "UTF-8")

        val trackId = resolveSpotifyTrackId(artist, title)

        val endpoints = buildList {
            if (!trackId.isNullOrBlank()) {
                add("https://www.canvasdownloader.com/canvas?link=https://open.spotify.com/track/$trackId")
                add("https://api.paxsenix.biz.id/spotify/canvas?id=$trackId")
                add("https://canvas-api.boidu.dev/canvas?trackId=$trackId")
            }
            add("https://api.paxsenix.biz.id/spotify/canvas?q=$encQuery")
            add("https://canvas-api.boidu.dev/canvas?song=$encTitle&artist=$encArtist")
        }

        for (url in endpoints) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Referer", "https://www.canvasdownloader.com/")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val rawBody = response.body?.string() ?: return@use
                    val body = rawBody.replace("\\u002F", "/").replace("\\/", "/")

                    val directMatch = mp4DirectRegex.findAll(body)
                        .map { it.value.trimEnd('"', '\'', '\\', '>') }
                        .firstOrNull { it.contains("canvaz", true) || it.contains(".mp4", true) }

                    if (!directMatch.isNullOrBlank()) {
                        return directMatch
                    }

                    val extracted = extractVideoUrlFromJson(body)
                    if (!extracted.isNullOrBlank()) {
                        return extracted
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
        return null
    }

    private fun resolveSpotifyTrackId(artist: String, title: String): String? {
        val query = URLEncoder.encode("$artist $title", "UTF-8")

        try {
            val deezerUrl = "https://api.deezer.com/search?q=$query&limit=1"
            var deezerTrackLink: String? = null
            client.newCall(Request.Builder().url(deezerUrl).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val data = gson.fromJson(body, JsonObject::class.java)?.getAsJsonArray("data")
                    if (data != null && data.size() > 0) {
                        deezerTrackLink = data.get(0).asJsonObject.get("link")?.asString
                    }
                }
            }

            if (!deezerTrackLink.isNullOrBlank()) {
                val odesliUrl = "https://api.song.link/v1-alpha.1/links?url=${URLEncoder.encode(deezerTrackLink!!, "UTF-8")}"
                client.newCall(Request.Builder().url(odesliUrl).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val match = spotifyTrackIdRegex.find(body)?.groupValues?.getOrNull(1)
                        if (!match.isNullOrBlank() && match.length == 22) {
                            return match
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val searchUrl = "https://html.duckduckgo.com/html/?q=${URLEncoder.encode("site:open.spotify.com/track $artist $title", "UTF-8")}"
        try {
            client.newCall(Request.Builder().url(searchUrl).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                        .replace("%2F", "/")
                        .replace("\\/", "/")
                    val foundId = spotifyTrackIdRegex.find(body)?.groupValues?.getOrNull(1)
                    if (!foundId.isNullOrBlank() && foundId.length == 22) {
                        return foundId
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun fetchAppleMusicVideo(artist: String, title: String): String? {
        try {
            val term = URLEncoder.encode("$artist $title", "UTF-8")
            val searchUrl = "https://itunes.apple.com/search?term=$term&entity=song&limit=4"
            var albumViewUrl: String? = null

            client.newCall(Request.Builder().url(searchUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = gson.fromJson(body, JsonObject::class.java)
                val results = json.getAsJsonArray("results")
                if (results != null && results.size() > 0) {
                    for (i in 0 until results.size()) {
                        val item = results.get(i).asJsonObject
                        val foundArtist = item.get("artistName")?.asString.orEmpty()
                        if (foundArtist.contains(artist, ignoreCase = true) || artist.contains(foundArtist, ignoreCase = true)) {
                            albumViewUrl = item.get("collectionViewUrl")?.asString ?: item.get("trackViewUrl")?.asString
                            if (albumViewUrl != null) break
                        }
                    }
                    if (albumViewUrl == null) {
                        val first = results.get(0).asJsonObject
                        albumViewUrl = first.get("collectionViewUrl")?.asString ?: first.get("trackViewUrl")?.asString
                    }
                }
            }

            val cleanAlbumPageUrl = albumViewUrl?.substringBefore("?") ?: return null

            client.newCall(Request.Builder().url(cleanAlbumPageUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val rawHtml = response.body?.string() ?: return null
                val unescapedHtml = rawHtml
                    .replace("\\u002F", "/")
                    .replace("\\/", "/")
                    .replace("&amp;", "&")

                val matches = m3u8StreamRegex.findAll(unescapedHtml)
                    .map { it.value.trimEnd('"', '\'', '\\') }
                    .toList()

                val editorialStream = matches.firstOrNull {
                    it.contains("mvod.itunes.apple.com", ignoreCase = true) ||
                            it.contains("editorial", ignoreCase = true) ||
                            it.contains("tall", ignoreCase = true) ||
                            it.contains("motion", ignoreCase = true) ||
                            it.contains("video", ignoreCase = true)
                } ?: matches.firstOrNull()

                if (!editorialStream.isNullOrBlank()) {
                    return editorialStream
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
        return null
    }

    private fun extractVideoUrlFromJson(jsonStr: String): String? {
        return try {
            val element = gson.fromJson(jsonStr, JsonElement::class.java)
            findVideoUrlRecursively(element)
        } catch (_: Exception) {
            null
        }
    }

    private fun findVideoUrlRecursively(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonPrimitive) {
            val str = element.asString
            if (str.startsWith("http") && (str.contains(".mp4", true) || str.contains(".webm", true) || str.contains("canvaz", true))) {
                return str
            }
            return null
        }
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val priorityKeys = listOf("canvasUrl", "canvas_url", "videoUrl", "video_url", "mp4Url", "download_url", "url")
            for (k in priorityKeys) {
                if (obj.has(k) && obj.get(k).isJsonPrimitive) {
                    val candidate = obj.get(k).asString
                    if (candidate.startsWith("http") && (candidate.contains(".mp4", true) || candidate.contains("canvaz", true) || candidate.contains("video", true))) {
                        return candidate
                    }
                }
            }
            for ((_, value) in obj.entrySet()) {
                val found = findVideoUrlRecursively(value)
                if (found != null) return found
            }
        }
        if (element.isJsonArray) {
            val arr = element.asJsonArray
            for (i in 0 until arr.size()) {
                val found = findVideoUrlRecursively(arr.get(i))
                if (found != null) return found
            }
        }
        return null
    }
}