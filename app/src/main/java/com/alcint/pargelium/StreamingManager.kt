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

    private fun getYtApiKey(): String {
        cachedYtApiKey?.let { return it }
        try {
            val conn = URL("https://music.youtube.com/").openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().readText()
                val regex = """"INNERTUBE_API_KEY":"([^"]+)"""".toRegex()
                val match = regex.find(html)
                if (match != null) {
                    cachedYtApiKey = match.groupValues[1]
                    return cachedYtApiKey!!
                }
            }
        } catch (e: Exception) {}

        cachedYtApiKey = "AIzaSyC2AmrG_vF0qA018wE21bI06R7Mh9HwT9c"
        return cachedYtApiKey!!
    }

    private fun getSCClientId(): String {
        cachedScClientId?.let { return it }
        try {
            val html = URL("https://soundcloud.com").openConnection().apply {
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }.inputStream.bufferedReader().readText()
            val scriptRegex = """<script crossorigin src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""".toRegex()
            val scripts = scriptRegex.findAll(html).map { it.groupValues[1] }.toList()
            for (scriptUrl in scripts.reversed()) {
                try {
                    val scriptContent = URL(scriptUrl).openConnection().inputStream.bufferedReader().readText()
                    val idRegex = """client_id:"([^"]+)"""".toRegex()
                    val match = idRegex.find(scriptContent)
                    if (match != null) {
                        cachedScClientId = match.groupValues[1]
                        return cachedScClientId!!
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
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
            e.printStackTrace()
            throw e
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
            try {
                val url = URL("https://youtubei.googleapis.com/youtubei/v1/player?key=$key")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.setRequestProperty("X-Goog-Api-Format-Version", "2")
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.doOutput = true

                val payload = """
                    {
                        "context": { "client": $client },
                        "videoId": "$videoId"
                    }
                """.trimIndent()

                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val streamingData = json.optJSONObject("streamingData") ?: continue
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
            } catch (e: Exception) {}
        }

        val pipedInstances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.smnz.de",
            "https://api.piped.projectsegfau.lt",
            "https://piped-api.garudalinux.org"
        )
        for (instance in pipedInstances) {
            try {
                val url = URL("$instance/streams/$videoId")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
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
                }
            } catch (e: Exception) {}
        }

        throw Exception("YouTube Stream не найден ни одним клиентом API")
    }

    private fun searchYouTubeMusicInnerTube(query: String): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        try {
            val key = getYtApiKey()
            val url = URL("https://music.youtube.com/youtubei/v1/search?key=$key")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.doOutput = true

            val payload = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20230522.01.00"
                        }
                    },
                    "query": "$query",
                    "params": "EgWKAQIIAWoMEAMQBBAJEA4QChAF"
                }
            """.trimIndent()

            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)

                val contents = json.optJSONObject("contents")
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
                        try {
                            val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                            val videoId = item.optJSONObject("playlistItemData")?.optString("videoId")
                            if (videoId.isNullOrEmpty()) continue

                            val columns = item.optJSONArray("flexColumns") ?: continue
                            val titleObj = columns.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                            val title = titleObj?.optString("text") ?: "Unknown"

                            var artist = "YouTube Music"
                            val artistObj = columns.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")?.optJSONArray("runs")
                            if (artistObj != null && artistObj.length() > 0) {
                                artist = artistObj.optJSONObject(0)?.optString("text") ?: "YouTube Music"
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
                        } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {}
        return list
    }

    private suspend fun resolveSoundCloudStream(transcodingUrl: String): Uri = withContext(Dispatchers.IO) {
        val clientId = getSCClientId()
        val url = "$transcodingUrl?client_id=$clientId"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        if (conn.responseCode == 200) {
            val res = conn.inputStream.bufferedReader().readText()
            val finalUrl = JSONObject(res).getString("url")
            return@withContext Uri.parse(finalUrl)
        }
        throw Exception("SoundCloud Stream не найден")
    }

    private fun searchJamendo(query: String): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        try {
            val url = URL("https://api.jamendo.com/v3.0/tracks/?client_id=56d30c95&format=json&limit=15&search=$query")
            val conn = url.openConnection() as HttpURLConnection
            if (conn.responseCode == 200) {
                val results = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("results")
                for (i in 0 until results.length()) {
                    try {
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
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {}
        return list
    }

    private fun searchHearthis(query: String): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        try {
            val url = URL("https://api-v2.hearthis.at/search?type=tracks&t=$query&count=15")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode == 200) {
                val results = JSONArray(conn.inputStream.bufferedReader().readText())
                for (i in 0 until results.length()) {
                    try {
                        val item = results.getJSONObject(i)
                        val streamUrl = item.optString("stream_url", "")
                        if (streamUrl.isEmpty()) continue

                        var cover = item.optString("artwork_url", "")
                        if (cover.isEmpty() || cover == "null") cover = item.optString("thumb", "")
                        if (cover.isEmpty() || cover == "null") cover = item.optString("background_url", "")
                        if (cover.isEmpty() || cover == "null") cover = item.optJSONObject("user")?.optString("avatar_url", "") ?: ""

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
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {}
        return list
    }

    private fun searchAudius(query: String): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        try {
            val hostConn = URL("https://api.audius.co").openConnection() as HttpURLConnection
            val host = JSONObject(hostConn.inputStream.bufferedReader().readText()).getJSONArray("data").getString(0)

            val url = URL("$host/v1/tracks/search?query=$query&app_name=Pargelium")
            val conn = url.openConnection() as HttpURLConnection
            if (conn.responseCode == 200) {
                val results = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("data")
                for (i in 0 until results.length()) {
                    try {
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
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {}
        return list
    }

    private fun searchSoundCloud(query: String): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        try {
            val clientId = getSCClientId()
            val url = URL("https://api-v2.soundcloud.com/search/tracks?q=$query&client_id=$clientId&limit=15")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode == 200) {
                val results = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("collection")
                for (i in 0 until results.length()) {
                    try {
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
                        if (streamUrl.isEmpty() && transcodings.length() > 0) streamUrl = transcodings.getJSONObject(0).getString("url")

                        if (streamUrl.isNotEmpty()) {
                            var coverUrl = item.optString("artwork_url", "")
                            if (coverUrl.isEmpty() || coverUrl == "null") coverUrl = item.optJSONObject("user")?.optString("avatar_url", "") ?: ""
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
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {}
        return list
    }

    private suspend fun searchSaavnAdapter(query: String): List<AudioTrack> = try {
        SaavnApi.searchTrack(query).map { AudioTrack(id = it.longId, title = it.title, artist = it.artist, album = "JioSaavn", uri = Uri.parse(it.streamUrl), albumId = -5L, duration = it.duration * 1000L, trackNumber = 1, discNumber = 1, source = "saavn", coverUrl = it.coverUrl) }
    } catch (e: Exception) { emptyList() }
}