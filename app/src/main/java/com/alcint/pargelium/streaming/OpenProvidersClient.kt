package com.alcint.pargelium.streaming

import android.net.Uri
import android.util.LruCache
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.SaavnApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object OpenProvidersClient {

    @Volatile
    private var cachedScClientId: String? = null

    @Volatile
    private var cachedAudiusHost: String? = null

    private val scResolvedCache = LruCache<String, Uri>(128)

    private fun httpGet(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
                .build()
            InnerTubeClient.httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getSoundCloudClientId(forceRefresh: Boolean = false): String {
        if (!forceRefresh) {
            cachedScClientId?.let { return it }
        }
        val html = httpGet("https://soundcloud.com")
        if (html != null) {
            val scripts = Regex("""<script crossorigin src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""")
                .findAll(html)
                .map { it.groupValues[1] }
                .toList()
            for (scriptUrl in scripts.asReversed().take(5)) {
                val scriptContent = httpGet(scriptUrl) ?: continue
                val match = Regex("""client_id\s*[:=]\s*"([a-zA-Z0-9]{32})"""").find(scriptContent)
                if (match != null) {
                    val extracted = match.groupValues[1]
                    cachedScClientId = extracted
                    return extracted
                }
            }
        }
        val fallback = "a3e059563d7fd3372b49b37f00a00bcf"
        cachedScClientId = fallback
        return fallback
    }

    suspend fun searchSoundCloud(query: String, limit: Int = 20): List<AudioTrack> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        var clientId = getSoundCloudClientId(false)
        var body = httpGet("https://api-v2.soundcloud.com/search/tracks?q=$encoded&client_id=$clientId&limit=$limit")
        if (body == null) {
            clientId = getSoundCloudClientId(true)
            body = httpGet("https://api-v2.soundcloud.com/search/tracks?q=$encoded&client_id=$clientId&limit=$limit")
        }
        parseSoundCloudCollection(body)
    }

    private fun parseSoundCloudCollection(response: String?): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        if (response.isNullOrBlank()) return list
        try {
            val results = JSONObject(response).optJSONArray("collection") ?: return list
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val title = item.optString("title", "").takeIf { it.isNotBlank() } ?: continue
                val transcodings = item.optJSONObject("media")?.optJSONArray("transcodings") ?: continue

                var streamUrl = ""
                for (j in 0 until transcodings.length()) {
                    val trans = transcodings.getJSONObject(j)
                    if (trans.optJSONObject("format")?.optString("protocol") == "progressive") {
                        streamUrl = trans.optString("url", "")
                        break
                    }
                }
                if (streamUrl.isEmpty() && transcodings.length() > 0) {
                    streamUrl = transcodings.getJSONObject(0).optString("url", "")
                }
                if (streamUrl.isEmpty()) continue

                var coverUrl = item.optString("artwork_url", "")
                if (coverUrl.isEmpty() || coverUrl == "null") {
                    coverUrl = item.optJSONObject("user")?.optString("avatar_url", "").orEmpty()
                }
                val finalCover = coverUrl
                    .replace("-large", "-t500x500")
                    .replace("http://", "https://")
                    .takeIf { it.isNotBlank() && it != "null" }

                val genre = item.optString("genre", "").takeIf { it.isNotBlank() } ?: "SoundCloud"

                list.add(
                    AudioTrack(
                        id = item.optLong("id", title.hashCode().toLong()),
                        title = title,
                        artist = item.optJSONObject("user")?.optString("username", "SoundCloud Artist") ?: "SoundCloud",
                        album = genre,
                        uri = Uri.parse(streamUrl),
                        albumId = -4L,
                        duration = item.optLong("duration", 180_000L),
                        trackNumber = list.size + 1,
                        discNumber = 1,
                        source = "soundcloud",
                        coverUrl = finalCover
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    suspend fun resolveSoundCloudStream(transcodingUrl: String): Uri = withContext(Dispatchers.IO) {
        scResolvedCache.get(transcodingUrl)?.let { return@withContext it }

        var clientId = getSoundCloudClientId(false)
        val sep = if (transcodingUrl.contains("?")) "&" else "?"
        var response = httpGet("$transcodingUrl${sep}client_id=$clientId")
        if (response == null) {
            clientId = getSoundCloudClientId(true)
            response = httpGet("$transcodingUrl${sep}client_id=$clientId")
        }
        if (response != null) {
            val resolved = Uri.parse(JSONObject(response).getString("url"))
            scResolvedCache.put(transcodingUrl, resolved)
            return@withContext resolved
        }
        throw IllegalStateException("SoundCloud stream unavailable")
    }

    private fun resolveAudiusHost(): String? {
        cachedAudiusHost?.let { return it }
        val hostResponse = httpGet("https://api.audius.co") ?: return null
        return try {
            val host = JSONObject(hostResponse).getJSONArray("data").getString(0)
            cachedAudiusHost = host
            host
        } catch (_: Exception) {
            null
        }
    }

    suspend fun searchAudius(query: String, limit: Int = 20): List<AudioTrack> = withContext(Dispatchers.IO) {
        val host = resolveAudiusHost() ?: return@withContext emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val response = httpGet("$host/v1/tracks/search?query=$encoded&app_name=Pargelium&limit=$limit")
        parseAudiusTracks(host, response)
    }

    suspend fun getAudiusTrending(limit: Int = 16): List<AudioTrack> = withContext(Dispatchers.IO) {
        val host = resolveAudiusHost() ?: return@withContext emptyList()
        val response = httpGet("$host/v1/tracks/trending?app_name=Pargelium&limit=$limit")
        parseAudiusTracks(host, response)
    }

    private fun parseAudiusTracks(host: String, response: String?): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        if (response.isNullOrBlank()) return list
        try {
            val results = JSONObject(response).optJSONArray("data") ?: return list
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val trackId = item.optString("id", "").takeIf { it.isNotEmpty() } ?: continue

                var coverUrl = item.optJSONObject("artwork")?.optString("480x480", "").orEmpty()
                if (coverUrl.isEmpty() || coverUrl == "null") {
                    coverUrl = item.optJSONObject("user")?.optJSONObject("profile_picture")?.optString("480x480", "").orEmpty()
                }
                val finalCover = coverUrl.replace("http://", "https://").takeIf { it.isNotBlank() && it != "null" }

                list.add(
                    AudioTrack(
                        id = trackId.hashCode().toLong(),
                        title = item.optString("title", "Unknown"),
                        artist = item.optJSONObject("user")?.optString("name", "Audius Artist") ?: "Audius",
                        album = item.optString("genre", "Audius 320kbps"),
                        uri = Uri.parse("$host/v1/tracks/$trackId/stream?app_name=Pargelium"),
                        albumId = -3L,
                        duration = item.optLong("duration", 180L) * 1000L,
                        trackNumber = i + 1,
                        discNumber = 1,
                        source = "audius",
                        coverUrl = finalCover
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    suspend fun searchHearthis(query: String, limit: Int = 16): List<AudioTrack> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val response = httpGet("https://api-v2.hearthis.at/search?type=tracks&t=$encoded&count=$limit")
        parseHearthisTracks(response)
    }

    suspend fun getHearthisPopular(limit: Int = 12): List<AudioTrack> = withContext(Dispatchers.IO) {
        val response = httpGet("https://api-v2.hearthis.at/feed/?type=popular&count=$limit")
        parseHearthisTracks(response)
    }

    private fun parseHearthisTracks(response: String?): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        if (response.isNullOrBlank()) return list
        try {
            val results = JSONArray(response)
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val streamUrl = item.optString("stream_url", "")
                if (streamUrl.isEmpty()) continue

                val cover = sequenceOf(
                    item.optString("artwork_url", ""),
                    item.optString("thumb", ""),
                    item.optJSONObject("user")?.optString("avatar_url", "") ?: ""
                ).firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()

                list.add(
                    AudioTrack(
                        id = item.optString("id", "$i").hashCode().toLong(),
                        title = item.optString("title", "Unknown"),
                        artist = item.optJSONObject("user")?.optString("username", "Hearthis DJ") ?: "Hearthis",
                        album = item.optString("genre", "Hearthis Mix"),
                        uri = Uri.parse(streamUrl),
                        albumId = -2L,
                        duration = item.optLong("duration", 180L) * 1000L,
                        trackNumber = i + 1,
                        discNumber = 1,
                        source = "hearthis",
                        coverUrl = cover.replace("http://", "https://").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    suspend fun searchJamendo(query: String, limit: Int = 15): List<AudioTrack> = withContext(Dispatchers.IO) {
        val list = mutableListOf<AudioTrack>()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val response = httpGet("https://api.jamendo.com/v3.0/tracks/?client_id=56d30c95&format=json&limit=$limit&search=$encoded")
            ?: return@withContext list
        try {
            val results = JSONObject(response).getJSONArray("results")
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val audioUrl = item.optString("audio", "").takeIf { it.isNotBlank() } ?: continue
                val coverUrl = item.optString("image", "").replace("http://", "https://").takeIf { it.isNotBlank() && it != "null" }
                list.add(
                    AudioTrack(
                        id = item.optString("id", "$i").hashCode().toLong(),
                        title = item.optString("name", "Unknown"),
                        artist = item.optString("artist_name", "Unknown"),
                        album = item.optString("album_name", "Jamendo"),
                        uri = Uri.parse(audioUrl),
                        albumId = -1L,
                        duration = item.optLong("duration", 180L) * 1000L,
                        trackNumber = i + 1,
                        discNumber = 1,
                        source = "jamendo",
                        coverUrl = coverUrl
                    )
                )
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun searchSaavn(query: String): List<AudioTrack> = withContext(Dispatchers.IO) {
        try {
            SaavnApi.searchTrack(query).map {
                AudioTrack(
                    id = it.longId,
                    title = it.title,
                    artist = it.artist,
                    album = "JioSaavn • 320 kbps",
                    uri = Uri.parse(it.streamUrl),
                    albumId = -5L,
                    duration = it.duration * 1000L,
                    trackNumber = 1,
                    discNumber = 1,
                    source = "saavn",
                    coverUrl = it.coverUrl
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getSaavnTrending(): List<AudioTrack> = withContext(Dispatchers.IO) {
        try {
            SaavnApi.getTopTracks().map {
                AudioTrack(
                    id = it.longId,
                    title = it.title,
                    artist = it.artist,
                    album = "JioSaavn • 320 kbps",
                    uri = Uri.parse(it.streamUrl),
                    albumId = -5L,
                    duration = it.duration * 1000L,
                    trackNumber = 1,
                    discNumber = 1,
                    source = "saavn",
                    coverUrl = it.coverUrl
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}