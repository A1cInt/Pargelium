package com.alcint.pargelium

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object StreamingManager {

    private var cachedScClientId: String? = null
    private var cachedYtApiKey: String? = null

    private fun performGet(urlStr: String, headers: Map<String, String> = mapOf("User-Agent" to "Mozilla/5.0")): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) { null }
    }

    private fun performPost(urlStr: String, payload: String, headers: Map<String, String> = mapOf("User-Agent" to "Mozilla/5.0", "Content-Type" to "application/json; utf-8")): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) { null }
    }

    private fun getYtApiKey(): String {
        cachedYtApiKey?.let { return it }
        val html = performGet("https://music.youtube.com/")
        if (html != null) {
            val match = """"INNERTUBE_API_KEY"\s*:\s*"([^"]+)"""".toRegex().find(html)
            if (match != null) {
                cachedYtApiKey = match.groupValues[1]
                return cachedYtApiKey!!
            }
        }
        cachedYtApiKey = "AIzaSyC2AmrG_vF0qA018wE21bI06R7Mh9HwT9c"
        return cachedYtApiKey!!
    }

    private fun getSCClientId(): String {
        cachedScClientId?.let { return it }
        val html = performGet("https://soundcloud.com")
        if (html != null) {
            val scripts = """<script crossorigin src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""".toRegex()
                .findAll(html).map { it.groupValues[1] }.toList()
            for (scriptUrl in scripts.reversed()) {
                val scriptContent = performGet(scriptUrl) ?: continue
                val match = """client_id[:=]"([^"]+)"""".toRegex().find(scriptContent)
                if (match != null) {
                    cachedScClientId = match.groupValues[1]
                    return cachedScClientId!!
                }
            }
        }
        cachedScClientId = "a3e059563d7fd3372b49b37f00a00bcf"
        return cachedScClientId!!
    }

    suspend fun searchAll(query: String): List<AudioTrack> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val results = listOf(
            async { searchYouTubeMusicInnerTube(query) },
            async { searchJamendo(encodedQuery) },
            async { searchHearthis(encodedQuery) },
            async { searchAudius(encodedQuery) },
            async { searchSoundCloud(encodedQuery) },
            async { searchSaavnAdapter(query) }
        )
        results.awaitAll().flatten().shuffled()
    }

    suspend fun getPlayableUri(context: Context, track: AudioTrack): Uri = withContext(Dispatchers.IO) {
        try {
            when (track.source) {
                "saavn" -> SaavnApi.getStreamUri(track.id)
                "soundcloud" -> resolveSoundCloudStream(track.uri.toString())
                "youtube" -> resolveYouTubeStream(track.uri.toString().substringAfter("youtube://"))
                else -> track.uri
            }
        } catch (e: Exception) {
            track.uri
        }
    }

    private suspend fun resolveYouTubeStream(videoId: String): Uri = withContext(Dispatchers.IO) {
        val key = getYtApiKey()
        val clients = listOf(
            """{"clientName": "IOS", "clientVersion": "19.29.1", "deviceMake": "Apple", "deviceModel": "iPhone16,2", "osName": "iOS", "osVersion": "17.5.1"}""",
            """{"clientName": "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "clientVersion": "2.0"}""",
            """{"clientName": "ANDROID", "clientVersion": "17.31.35", "androidSdkVersion": 33}"""
        )

        for (client in clients) {
            val payload = """{"context": { "client": $client }, "videoId": "$videoId"}"""
            val headers = mapOf(
                "Content-Type" to "application/json; utf-8",
                "User-Agent" to "Mozilla/5.0",
                "X-Goog-Api-Format-Version" to "2"
            )
            val response = performPost("https://youtubei.googleapis.com/youtubei/v1/player?key=$key", payload, headers)
            if (response != null) {
                try {
                    val streamingData = JSONObject(response).optJSONObject("streamingData")
                    if (streamingData != null) {
                        var bestUrl = ""
                        var highestBitrate = 0
                        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
                        if (adaptiveFormats != null) {
                            for (i in 0 until adaptiveFormats.length()) {
                                val format = adaptiveFormats.getJSONObject(i)
                                if (format.optString("mimeType", "").contains("audio/") && format.has("url")) {
                                    val bitrate = format.optInt("bitrate", 0)
                                    if (bitrate > highestBitrate) {
                                        highestBitrate = bitrate
                                        bestUrl = format.getString("url")
                                    }
                                }
                            }
                        }
                        if (bestUrl.isNotEmpty()) return@withContext Uri.parse(bestUrl)
                    }
                } catch (_: Exception) {}
            }
        }

        val pipedInstances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.smnz.de",
            "https://piped-api.lunar.icu",
            "https://api.piped.projectsegfau.lt",
            "https://piped-api.garudalinux.org"
        )

        for (instance in pipedInstances) {
            val response = performGet("$instance/streams/$videoId")
            if (response != null) {
                try {
                    val audioStreams = JSONObject(response).optJSONArray("audioStreams")
                    if (audioStreams != null) {
                        var bestUrl = ""
                        var highestBitrate = 0
                        for (i in 0 until audioStreams.length()) {
                            val stream = audioStreams.getJSONObject(i)
                            val bitrate = stream.optInt("bitrate", 0)
                            if (bitrate > highestBitrate) {
                                highestBitrate = bitrate
                                bestUrl = stream.getString("url")
                            }
                        }
                        if (bestUrl.isNotEmpty()) return@withContext Uri.parse(bestUrl)
                    }
                } catch (_: Exception) {}
            }
        }
        throw Exception("YouTube Stream is not available")
    }

    private fun searchYouTubeMusicInnerTube(query: String): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        val payload = """
            {
                "context": { "client": { "clientName": "WEB_REMIX", "clientVersion": "1.20230522.01.00" } },
                "query": "$query",
                "params": "EgWKAQIIAWoMEAMQBBAJEA4QChAF"
            }
        """.trimIndent()

        val response = performPost("https://music.youtube.com/youtubei/v1/search?key=${getYtApiKey()}", payload) ?: return list

        try {
            val contents = JSONObject(response).optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return list

            for (i in 0 until contents.length()) {
                val shelf = contents.optJSONObject(i)?.optJSONObject("musicShelfRenderer") ?: continue
                val items = shelf.optJSONArray("contents") ?: continue

                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val videoId = item.optJSONObject("playlistItemData")?.optString("videoId")
                    if (videoId.isNullOrEmpty()) continue

                    val columns = item.optJSONArray("flexColumns") ?: continue
                    val title = columns.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                        ?.optString("text") ?: "Unknown"

                    var artist = "YouTube Music"
                    val artistRuns = columns.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs")
                    if (artistRuns != null && artistRuns.length() > 0) {
                        artist = artistRuns.optJSONObject(0)?.optString("text") ?: "YouTube Music"
                    }

                    var coverUrl = ""
                    val thumbs = item.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    if (thumbs != null && thumbs.length() > 0) {
                        coverUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
                    }

                    if (list.none { it.id == videoId.hashCode().toLong() }) {
                        list.add(AudioTrack(
                            id = videoId.hashCode().toLong(),
                            title = title,
                            artist = artist,
                            album = "YouTube Stream",
                            uri = Uri.parse("youtube://$videoId"),
                            albumId = -7L,
                            duration = 0L,
                            trackNumber = 1, discNumber = 1, source = "youtube",
                            coverUrl = coverUrl.replace("w60-h60", "w540-h540").replace("w120-h120", "w540-h540")
                        ))
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private suspend fun resolveSoundCloudStream(transcodingUrl: String): Uri = withContext(Dispatchers.IO) {
        val response = performGet("$transcodingUrl?client_id=${getSCClientId()}")
        if (response != null) {
            try {
                return@withContext Uri.parse(JSONObject(response).getString("url"))
            } catch (_: Exception) {}
        }
        throw Exception("SoundCloud Stream is not available")
    }

    private fun searchJamendo(query: String): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        val response = performGet("https://api.jamendo.com/v3.0/tracks/?client_id=56d30c95&format=json&limit=15&search=$query") ?: return list
        try {
            val results = JSONObject(response).getJSONArray("results")
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val coverUrl = item.optString("image", "").replace("http://", "https://").takeIf { it.isNotBlank() && it != "null" }
                list.add(AudioTrack(
                    id = item.getString("id").hashCode().toLong(),
                    title = item.optString("name", "Unknown"),
                    artist = item.optString("artist_name", "Unknown"),
                    album = "Jamendo Stream",
                    uri = Uri.parse(item.getString("audio")),
                    albumId = -1L,
                    duration = item.optInt("duration", 0) * 1000L,
                    trackNumber = 1, discNumber = 1, source = "jamendo", coverUrl = coverUrl
                ))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun searchHearthis(query: String): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        val response = performGet("https://api-v2.hearthis.at/search?type=tracks&t=$query&count=15") ?: return list
        try {
            val results = JSONArray(response)
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val streamUrl = item.optString("stream_url", "")
                if (streamUrl.isEmpty()) continue

                val cover = sequenceOf(
                    item.optString("artwork_url", ""),
                    item.optString("thumb", ""),
                    item.optString("background_url", ""),
                    item.optJSONObject("user")?.optString("avatar_url", "") ?: ""
                ).firstOrNull { it.isNotBlank() && it != "null" } ?: ""

                val finalCover = cover.replace("http://", "https://").takeIf { it.isNotBlank() && it != "null" }

                list.add(AudioTrack(
                    id = item.getString("id").hashCode().toLong(),
                    title = item.optString("title", "Unknown"),
                    artist = item.optJSONObject("user")?.optString("username", "Hearthis Artist") ?: "Unknown",
                    album = "Hearthis.at Stream",
                    uri = Uri.parse(streamUrl),
                    albumId = -2L,
                    duration = item.optInt("duration", 0) * 1000L,
                    trackNumber = 1, discNumber = 1, source = "hearthis", coverUrl = finalCover
                ))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun searchAudius(query: String): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        val hostResponse = performGet("https://api.audius.co") ?: return list
        try {
            val host = JSONObject(hostResponse).getJSONArray("data").getString(0)
            val response = performGet("$host/v1/tracks/search?query=$query&app_name=Pargelium") ?: return list

            val results = JSONObject(response).getJSONArray("data")
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val trackId = item.getString("id")

                var coverUrl = item.optJSONObject("artwork")?.optString("480x480", "") ?: ""
                if (coverUrl.isEmpty() || coverUrl == "null") {
                    coverUrl = item.optJSONObject("user")?.optJSONObject("profile_picture")?.optString("480x480", "") ?: ""
                }
                val finalCover = coverUrl.replace("http://", "https://").takeIf { it.isNotBlank() && it != "null" }

                list.add(AudioTrack(
                    id = trackId.hashCode().toLong(),
                    title = item.optString("title", "Unknown"),
                    artist = item.optJSONObject("user")?.optString("name", "Audius Artist") ?: "Unknown",
                    album = "Audius Stream",
                    uri = Uri.parse("$host/v1/tracks/$trackId/stream?app_name=Pargelium"),
                    albumId = -3L,
                    duration = item.optInt("duration", 0) * 1000L,
                    trackNumber = 1, discNumber = 1, source = "audius", coverUrl = finalCover
                ))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun searchSoundCloud(query: String): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        val response = performGet("https://api-v2.soundcloud.com/search/tracks?q=$query&client_id=${getSCClientId()}&limit=15") ?: return list
        try {
            val results = JSONObject(response).getJSONArray("collection")
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                if (!item.has("title")) continue

                val transcodings = item.optJSONObject("media")?.optJSONArray("transcodings") ?: continue
                var streamUrl = ""
                for (j in 0 until transcodings.length()) {
                    val trans = transcodings.getJSONObject(j)
                    if (trans.getJSONObject("format").getString("protocol") == "progressive") {
                        streamUrl = trans.getString("url")
                        break
                    }
                }
                if (streamUrl.isEmpty() && transcodings.length() > 0) {
                    streamUrl = transcodings.getJSONObject(0).getString("url")
                }

                if (streamUrl.isNotEmpty()) {
                    var coverUrl = item.optString("artwork_url", "")
                    if (coverUrl.isEmpty() || coverUrl == "null") {
                        coverUrl = item.optJSONObject("user")?.optString("avatar_url", "") ?: ""
                    }
                    val finalCover = coverUrl.replace("-large", "-t500x500").replace("http://", "https://").takeIf { it.isNotBlank() && it != "null" }

                    list.add(AudioTrack(
                        id = item.getLong("id"),
                        title = item.getString("title"),
                        artist = item.optJSONObject("user")?.optString("username", "SoundCloud Artist") ?: "Unknown",
                        album = "SoundCloud",
                        uri = Uri.parse(streamUrl),
                        albumId = -4L,
                        duration = item.optLong("duration", 0),
                        trackNumber = 1, discNumber = 1, source = "soundcloud", coverUrl = finalCover
                    ))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private suspend fun searchSaavnAdapter(query: String): List<AudioTrack> = try {
        SaavnApi.searchTrack(query).map { AudioTrack(id = it.longId, title = it.title, artist = it.artist, album = "JioSaavn", uri = Uri.parse(it.streamUrl), albumId = -5L, duration = it.duration * 1000L, trackNumber = 1, discNumber = 1, source = "saavn", coverUrl = it.coverUrl) }
    } catch (_: Exception) { emptyList() }
}