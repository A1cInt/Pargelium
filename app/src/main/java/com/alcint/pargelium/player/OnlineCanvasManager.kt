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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(9, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
                )
                .header("Accept", "application/json, text/html, */*")
                .build()
            chain.proceed(req)
        }
        .build()

    private val gson = Gson()
    private val memoryCache = LruCache<String, String>(128)

    private val parenCleanupRegex = Regex("(?i)\\s*\\(?(feat\\.|ft\\.|remaster|official|lyric|video|audio|radio|bonus|deluxe|explicit|clean).*?\\)?")
    private val bracketCleanupRegex = Regex("(?i)\\s*\\[(feat\\.|ft\\.|remaster|official|lyric|video|audio|radio|bonus|deluxe|explicit|clean).*?]")
    private val dashCleanupRegex = Regex("(?i)\\s*-.*?(Remaster|Edit|Mix|Version|Live|Mono|Stereo).*")

    private val m3u8StreamRegex = Regex("""(https?://[^\s"'<>\\]+\.m3u8[^\s"'<>\\]*)""")
    private val mp4DirectRegex = Regex("""(https?://[^\s"'<>\\]+(?:canvaz\.scdn\.co|\.mp4)[^\s"'<>\\]*)""", RegexOption.IGNORE_CASE)
    private val spotifyTrackIdRegex = Regex("""(?:open\.spotify\.com/track/|spotify:track:|"id"\s*:\s*")([a-zA-Z0-9]{22})""")

    @Volatile
    private var cachedSpotifyAnonToken: String? = null
    @Volatile
    private var tokenExpirationMs: Long = 0L

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

        val videoDir = File(context.filesDir, "spotify_canvas_videos").apply { if (!exists()) mkdirs() }
        val localMp4File = File(videoDir, "canvas_${baseKey.hashCode()}.mp4")

        // 1. Если MP4 канвас уже скачан локально — отдаем его мгновенно!
        if (mode != CanvasSourceMode.APPLE_ONLY && localMp4File.exists() && localMp4File.length() > 10_240L) {
            val fileUri = Uri.fromFile(localMp4File)
            memoryCache.put(cacheKey, fileUri.toString())
            return@withContext fileUri
        }

        // 2. Проверяем оперативный кэш и кэш HLS-ссылок Apple Music
        memoryCache.get(cacheKey)?.let { cached ->
            return@withContext Uri.parse(cached)
        }

        val urlCacheDir = context.getDir("online_canvas_cache", Context.MODE_PRIVATE)
        val diskUrlFile = File(urlCacheDir, "canvas_${cacheKey.hashCode()}.txt")
        if (diskUrlFile.exists()) {
            val cachedUrl = runCatching { diskUrlFile.readText().trim() }.getOrNull()
            if (!cachedUrl.isNullOrEmpty() && cachedUrl.startsWith("http")) {
                memoryCache.put(cacheKey, cachedUrl)
                return@withContext Uri.parse(cachedUrl)
            }
        }

        // 3. Ищем онлайн в зависимости от выбранного режима
        val resolvedUri: Uri? = coroutineScope {
            when (mode) {
                CanvasSourceMode.SPOTIFY_ONLY -> {
                    fetchAndSaveSpotifyCanvas(cleanArtist, cleanTitle, localMp4File)
                }
                CanvasSourceMode.APPLE_ONLY -> {
                    fetchAppleMusicVideo(cleanArtist, cleanTitle)?.let { Uri.parse(it) }
                }
                CanvasSourceMode.BOTH_SPOTIFY_PRIORITY -> {
                    val spotifyDeferred = async { fetchAndSaveSpotifyCanvas(cleanArtist, cleanTitle, localMp4File) }
                    val appleDeferred = async { fetchAppleMusicVideo(cleanArtist, cleanTitle) }
                    spotifyDeferred.await() ?: appleDeferred.await()?.let { Uri.parse(it) }
                }
                CanvasSourceMode.BOTH_APPLE_PRIORITY -> {
                    val appleDeferred = async { fetchAppleMusicVideo(cleanArtist, cleanTitle) }
                    val spotifyDeferred = async { fetchAndSaveSpotifyCanvas(cleanArtist, cleanTitle, localMp4File) }
                    appleDeferred.await()?.let { Uri.parse(it) } ?: spotifyDeferred.await()
                }
            }
        }

        if (resolvedUri != null) {
            val uriStr = resolvedUri.toString()
            memoryCache.put(cacheKey, uriStr)
            if (uriStr.startsWith("http")) {
                runCatching { diskUrlFile.writeText(uriStr) }
            }
        }

        return@withContext resolvedUri
    }

    private fun fetchAndSaveSpotifyCanvas(artist: String, title: String, targetMp4File: File): Uri? {
        val remoteMp4Url = fetchSpotifyCanvasUrl(artist, title) ?: return null
        val cleanUrl = remoteMp4Url.replace("\\/", "/").replace("&amp;", "&")

        // Скачиваем .mp4 локально, чтобы ExoPlayer воспроизводил его без сетевых задержек и ошибок
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

        // Шаг 1: Резолвим 22-символьный Spotify Track ID для точного попадания в Canvas API
        val trackId = resolveSpotifyTrackId(artist, title)

        val endpoints = buildList {
            if (!trackId.isNullOrBlank()) {
                add("https://api.paxsenix.biz.id/spotify/canvas?id=$trackId")
                add("https://canvas-api.boidu.dev/canvas?trackId=$trackId")
                add("https://spotify-canvas-api.vercel.app/api/canvas?trackId=$trackId")
                add("https://www.canvasdownloader.com/canvas?link=https://open.spotify.com/track/$trackId")
            }
            add("https://api.paxsenix.biz.id/spotify/canvas?q=$encQuery")
            add("https://canvas-api.boidu.dev/canvas?song=$encTitle&artist=$encArtist")
            add("https://api.siputzx.my.id/api/s/spotify?query=$encQuery")
            add("https://spotify-canvas-api.vercel.app/api/canvas?artist=$encArtist&track=$encTitle")
        }

        for (url in endpoints) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use

                    // 1. Ищем прямую ссылку на canvaz.scdn.co / .mp4 в теле ответа (JSON или HTML)
                    val directMatch = mp4DirectRegex.findAll(body)
                        .map { it.value.replace("\\/", "/").trimEnd('"', '\'', '\\') }
                        .firstOrNull { it.contains("canvaz", true) || it.endsWith(".mp4", true) }

                    if (!directMatch.isNullOrBlank()) {
                        return directMatch
                    }

                    // 2. Рекурсивный обход JSON-дерева
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

        // Способ A: Через анонимный токен веб-плеера Spotify
        try {
            val token = getSpotifyAnonymousToken()
            if (!token.isNullOrBlank()) {
                val searchUrl = "https://api.spotify.com/v1/search?q=$query&type=track&limit=3"
                val req = Request.Builder()
                    .url(searchUrl)
                    .header("Authorization", "Bearer $token")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val items = json.getAsJsonObject("tracks")?.getAsJsonArray("items")
                        if (items != null && items.size() > 0) {
                            val id = items.get(0).asJsonObject.get("id")?.asString
                            if (!id.isNullOrBlank() && id.length == 22) return id
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Способ B: Через публичные поисковые эндпоинты и веб-страницу поиска Spotify
        val searchUrls = listOf(
            "https://api.paxsenix.biz.id/spotify/search?q=$query",
            "https://api.siputzx.my.id/api/s/spotify?query=$query",
            "https://open.spotify.com/search/$query"
        )

        for (url in searchUrls) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val foundId = spotifyTrackIdRegex.find(body)?.groupValues?.getOrNull(1)
                        if (!foundId.isNullOrBlank() && foundId.length == 22) {
                            return foundId
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun getSpotifyAnonymousToken(): String? {
        val now = System.currentTimeMillis()
        if (cachedSpotifyAnonToken != null && now < tokenExpirationMs) {
            return cachedSpotifyAnonToken
        }
        return try {
            val req = Request.Builder()
                .url("https://open.spotify.com/get_access_token?reason=transport&productType=web_player")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = gson.fromJson(body, JsonObject::class.java)
                val token = json.get("accessToken")?.asString
                val exp = json.get("accessTokenExpirationTimestampMs")?.asLong ?: (now + 1800_000L)
                if (!token.isNullOrBlank()) {
                    cachedSpotifyAnonToken = token
                    tokenExpirationMs = exp - 30_000L
                }
                token
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchAppleMusicVideo(artist: String, title: String): String? {
        try {
            val term = URLEncoder.encode("$artist $title", "UTF-8")
            val searchUrl = "https://itunes.apple.com/search?term=$term&entity=song&limit=4"
            var trackViewUrl: String? = null

            client.newCall(Request.Builder().url(searchUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = gson.fromJson(body, JsonObject::class.java)
                val results = json.getAsJsonArray("results")
                if (results != null && results.size() > 0) {
                    for (i in 0 until results.size()) {
                        val item = results.get(i).asJsonObject
                        val foundArtist = item.get("artistName")?.asString ?: ""
                        if (foundArtist.contains(artist, ignoreCase = true) || artist.contains(foundArtist, ignoreCase = true)) {
                            trackViewUrl = item.get("collectionViewUrl")?.asString ?: item.get("trackViewUrl")?.asString
                            if (trackViewUrl != null) break
                        }
                    }
                    if (trackViewUrl == null) {
                        val first = results.get(0).asJsonObject
                        trackViewUrl = first.get("collectionViewUrl")?.asString ?: first.get("trackViewUrl")?.asString
                    }
                }
            }

            val pageUrl = trackViewUrl ?: return null
            client.newCall(Request.Builder().url(pageUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null
                val matches = m3u8StreamRegex.findAll(html).map { it.value }.toList()
                val editorialStream = matches.firstOrNull {
                    it.contains("editorial", ignoreCase = true) ||
                            it.contains("tall", ignoreCase = true) ||
                            it.contains("motion", ignoreCase = true) ||
                            it.contains("video", ignoreCase = true)
                } ?: matches.firstOrNull()

                if (!editorialStream.isNullOrBlank()) {
                    return editorialStream.replace("\\/", "/")
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
