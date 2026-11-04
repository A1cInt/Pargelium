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
    SPOTIFY_ONLY("Только Spotify Canvas"),
    APPLE_ONLY("Только Apple Music")
}

object OnlineCanvasManager {

    private const val PREFS_NAME = "pargelium_canvas_prefs"
    private const val KEY_ENABLED = "online_canvas_enabled"
    private const val KEY_SOURCE_MODE = "online_canvas_source_mode"

    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                )
                .header("Accept", "application/json, text/html, */*")
                .build()
            chain.proceed(req)
        }
        .build()

    private val gson = Gson()
    private val memoryCache = LruCache<String, String>(96)
    private const val NONE_SENTINEL = "NONE"

    private val parenCleanupRegex = Regex("(?i)\\s*\\(?(feat\\.|ft\\.|remaster|official|lyric|video|audio|radio|bonus|deluxe).*?\\)?")
    private val bracketCleanupRegex = Regex("(?i)\\s*\\[(feat\\.|ft\\.|remaster|official|lyric|video|audio|radio|bonus|deluxe).*?]")
    private val dashCleanupRegex = Regex("(?i)\\s*-.*?(Remaster|Edit|Mix|Version|Live|Mono|Stereo).*")
    private val m3u8StreamRegex = Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""")
    private val mp4DirectRegex = Regex("""(https?://[^\s"']+canvaz\.scdn\.co[^\s"']+\.mp4[^\s"']*)""")

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
            File(context.getDir("online_canvas_cache", Context.MODE_PRIVATE), "").listFiles()?.forEach { it.delete() }
        }
    }

    private fun cleanMetadata(text: String): String {
        return text
            .replace(parenCleanupRegex, "")
            .replace(bracketCleanupRegex, "")
            .replace(dashCleanupRegex, "")
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
        val cacheKey = "${mode.ordinal}_${cleanArtist.lowercase()}_${cleanTitle.lowercase()}"

        memoryCache.get(cacheKey)?.let { cached ->
            return@withContext if (cached == NONE_SENTINEL) null else Uri.parse(cached)
        }

        val diskCacheFile = File(
            context.getDir("online_canvas_cache", Context.MODE_PRIVATE),
            "canvas_${cacheKey.hashCode()}.txt"
        )
        if (diskCacheFile.exists()) {
            val cachedUrl = runCatching { diskCacheFile.readText().trim() }.getOrNull()
            if (!cachedUrl.isNullOrEmpty() && cachedUrl != NONE_SENTINEL) {
                memoryCache.put(cacheKey, cachedUrl)
                return@withContext Uri.parse(cachedUrl)
            }
        }

        val resolvedUrl = coroutineScope {
            when (mode) {
                CanvasSourceMode.SPOTIFY_ONLY -> fetchSpotifyCanvas(cleanArtist, cleanTitle)
                CanvasSourceMode.APPLE_ONLY -> fetchAppleMusicVideo(cleanArtist, cleanTitle)
                CanvasSourceMode.BOTH_SPOTIFY_PRIORITY -> {
                    val spotifyDeferred = async { fetchSpotifyCanvas(cleanArtist, cleanTitle) }
                    val appleDeferred = async { fetchAppleMusicVideo(cleanArtist, cleanTitle) }
                    spotifyDeferred.await() ?: appleDeferred.await()
                }
                CanvasSourceMode.BOTH_APPLE_PRIORITY -> {
                    val appleDeferred = async { fetchAppleMusicVideo(cleanArtist, cleanTitle) }
                    val spotifyDeferred = async { fetchSpotifyCanvas(cleanArtist, cleanTitle) }
                    appleDeferred.await() ?: spotifyDeferred.await()
                }
            }
        }

        val valueToStore = resolvedUrl ?: NONE_SENTINEL
        memoryCache.put(cacheKey, valueToStore)
        if (resolvedUrl != null) {
            runCatching { diskCacheFile.writeText(resolvedUrl) }
        }

        return@withContext resolvedUrl?.let { Uri.parse(it) }
    }

    private fun fetchSpotifyCanvas(artist: String, title: String): String? {
        val encArtist = URLEncoder.encode(artist, "UTF-8")
        val encTitle = URLEncoder.encode(title, "UTF-8")
        val encQuery = URLEncoder.encode("$artist $title", "UTF-8")

        val endpoints = listOf(
            "https://canvas-api.boidu.dev/canvas?song=$encTitle&artist=$encArtist",
            "https://api.paxsenix.biz.id/spotify/canvas?q=$encQuery",
            "https://api.siputzx.my.id/api/s/spotify?query=$encQuery",
            "https://spotify-canvas-api.vercel.app/api/canvas?artist=$encArtist&track=$encTitle"
        )

        for (url in endpoints) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use

                    // 1. Прямой поиск ссылки на canvaz.scdn.co в ответе
                    mp4DirectRegex.find(body)?.value?.let { directMp4 ->
                        return directMp4.replace("\\/", "/")
                    }

                    // 2. Рекурсивный разбор JSON
                    val extracted = extractVideoUrlFromJson(body)
                    if (!extracted.isNullOrBlank()) return extracted
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
        return null
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
            val priorityKeys = listOf("canvasUrl", "canvas_url", "videoUrl", "video_url", "mp4Url", "url")
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