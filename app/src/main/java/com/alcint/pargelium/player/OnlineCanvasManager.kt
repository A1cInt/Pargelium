package com.alcint.pargelium.player

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.alcint.pargelium.AudioTrack
import com.google.gson.Gson
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

object OnlineCanvasManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                )
                .build()
            chain.proceed(req)
        }
        .build()

    private val gson = Gson()
    private val memoryCache = LruCache<String, String>(64)
    private const val NONE_SENTINEL = "NONE"

    private val parenCleanupRegex = Regex("(?i)\\s*\\(?(feat\\.|ft\\.|remaster|official|lyric|video|audio|radio).*?\\)?")
    private val bracketCleanupRegex = Regex("(?i)\\s*\\[(feat\\.|ft\\.|remaster|official|lyric|video|audio|radio).*?]")
    private val dashCleanupRegex = Regex("(?i)\\s*-.*?(Remaster|Edit|Mix|Version).*")
    private val m3u8StreamRegex = Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""")

    private fun cleanMetadata(text: String): String {
        return text
            .replace(parenCleanupRegex, "")
            .replace(bracketCleanupRegex, "")
            .replace(dashCleanupRegex, "")
            .trim()
    }

    suspend fun findOnlineCanvas(context: Context, track: AudioTrack): Uri? = withContext(Dispatchers.IO) {
        val valid = track.artist.isNotBlank() && track.title.isNotBlank() && !track.artist.contains("Unknown", true)
        if (!valid) return@withContext null

        val cleanArtist = cleanMetadata(track.artist)
        val cleanTitle = cleanMetadata(track.title)
        if (cleanArtist.isEmpty() || cleanTitle.isEmpty()) return@withContext null

        val cacheKey = "${cleanArtist.lowercase()}_${cleanTitle.lowercase()}"

        memoryCache.get(cacheKey)?.let { cached ->
            return@withContext if (cached == NONE_SENTINEL) null else Uri.parse(cached)
        }

        val diskCacheFile = File(context.getDir("online_canvas_cache", Context.MODE_PRIVATE), "canvas_${cacheKey.hashCode()}.txt")
        if (diskCacheFile.exists()) {
            val cachedUrl = runCatching { diskCacheFile.readText().trim() }.getOrNull()
            if (!cachedUrl.isNullOrEmpty()) {
                memoryCache.put(cacheKey, cachedUrl)
                return@withContext if (cachedUrl == NONE_SENTINEL) null else Uri.parse(cachedUrl)
            }
        }

        val resolvedUrl = coroutineScope {
            val spotifyDeferred = async { fetchSpotifyCanvas(cleanArtist, cleanTitle) }
            val appleDeferred = async { fetchAppleMusicVideo(cleanArtist, cleanTitle) }

            spotifyDeferred.await() ?: appleDeferred.await()
        }

        val valueToStore = resolvedUrl ?: NONE_SENTINEL
        memoryCache.put(cacheKey, valueToStore)
        runCatching { diskCacheFile.writeText(valueToStore) }

        return@withContext resolvedUrl?.let { Uri.parse(it) }
    }

    private fun fetchSpotifyCanvas(artist: String, title: String): String? {
        val query = URLEncoder.encode("$artist $title", "UTF-8")
        val endpoints = listOf(
            "https://canvas-api.boidu.dev/canvas?song=${URLEncoder.encode(title, "UTF-8")}&artist=${URLEncoder.encode(artist, "UTF-8")}",
            "https://api.paxsenix.biz.id/spotify/canvas?q=$query"
        )

        for (url in endpoints) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
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
            val searchUrl = "https://itunes.apple.com/search?term=$term&entity=song&limit=3"
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
            val obj = gson.fromJson(jsonStr, JsonObject::class.java)
            when {
                obj.has("canvasUrl") && !obj.get("canvasUrl").isJsonNull -> obj.get("canvasUrl").asString
                obj.has("canvas_url") && !obj.get("canvas_url").isJsonNull -> obj.get("canvas_url").asString
                obj.has("url") && !obj.get("url").isJsonNull -> obj.get("url").asString
                obj.has("canvases") && obj.get("canvases").isJsonArray -> {
                    val arr = obj.getAsJsonArray("canvases")
                    if (arr.size() > 0) {
                        val first = arr.get(0).asJsonObject
                        first.get("canvasUrl")?.asString
                            ?: first.get("canvas_url")?.asString
                            ?: first.get("url")?.asString
                    } else null
                }
                obj.has("data") && obj.get("data").isJsonObject -> {
                    val data = obj.getAsJsonObject("data")
                    data.get("canvasUrl")?.asString
                        ?: data.get("canvas_url")?.asString
                        ?: data.get("url")?.asString
                }
                else -> null
            }?.takeIf { it.startsWith("http") }
        } catch (_: Exception) {
            null
        }
    }
}