package com.alcint.pargelium.streaming

import android.net.Uri
import android.util.LruCache
import com.alcint.pargelium.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object InnerTubeClient {

    internal data class ResolvedYtStream(
        val url: String,
        val userAgent: String,
        val expiresAtMs: Long,
        val mimeType: String = "audio/mp4"
    )

    private val streamUrlCache = LruCache<String, ResolvedYtStream>(128)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var cachedApiKey: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3"

    @Volatile
    private var cachedWebVisitorData: String? = null

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun ensureWebVisitorData(): String? {
        cachedWebVisitorData?.let { return it }
        return try {
            val req = Request.Builder()
                .url("https://music.youtube.com/sw.js_data")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val match = Regex(""""Cgt[a-zA-Z0-9_\-]+"""").find(body)
                    val visitor = match?.value?.trim('"')
                    if (!visitor.isNullOrBlank()) {
                        cachedWebVisitorData = visitor
                        return visitor
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun searchTracks(query: String, limit: Int = 25): List<AudioTrack> = withContext(Dispatchers.IO) {
        val list = mutableListOf<AudioTrack>()
        val visitor = ensureWebVisitorData()
        val safeQuery = JSONObject.quote(query)

        val payload = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "1.20250310.01.00",
                        "hl": "en",
                        "gl": "US"
                        ${if (visitor != null) ""","visitorData": "$visitor"""" else ""}
                    }
                },
                "query": $safeQuery,
                "params": "EgWKAQIIAWoMEAMQBBAJEA4QChAF"
            }
        """.trimIndent()

        val reqBuilder = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false&key=$cachedApiKey")
            .post(payload.toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20250310.01.00")

        if (visitor != null) {
            reqBuilder.header("X-Goog-Visitor-Id", visitor)
        }

        val responseStr = try {
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        } ?: return@withContext list

        try {
            val contents = JSONObject(responseStr).optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return@withContext list

            for (i in 0 until contents.length()) {
                val shelf = contents.optJSONObject(i)?.optJSONObject("musicShelfRenderer") ?: continue
                val items = shelf.optJSONArray("contents") ?: continue

                for (j in 0 until items.length()) {
                    if (list.size >= limit) break
                    val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val videoId = item.optJSONObject("playlistItemData")?.optString("videoId")
                    if (videoId.isNullOrEmpty()) continue

                    val columns = item.optJSONArray("flexColumns") ?: continue
                    val title = columns.optJSONObject(0)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                        ?.optString("text") ?: continue

                    var artist = "YouTube Music"
                    var albumName = "YouTube Music"
                    var parsedDurationMs = 0L

                    val secondColRuns = columns.optJSONObject(1)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs")

                    if (secondColRuns != null) {
                        val textParts = mutableListOf<String>()
                        for (r in 0 until secondColRuns.length()) {
                            val runText = secondColRuns.optJSONObject(r)?.optString("text")?.trim().orEmpty()
                            if (runText.isNotEmpty() && runText != "•" && runText != "&" && runText != ",") {
                                val maybeDur = parseDurationStringMs(runText)
                                if (maybeDur > 0L) {
                                    parsedDurationMs = maybeDur
                                } else if (!runText.equals("Song", ignoreCase = true) && !runText.equals("Video", ignoreCase = true)) {
                                    textParts.add(runText)
                                }
                            }
                        }
                        if (textParts.isNotEmpty()) artist = textParts.first()
                        if (textParts.size >= 2) albumName = textParts[1]
                    }

                    if (parsedDurationMs == 0L) {
                        val fixedCols = item.optJSONArray("fixedColumns")
                        val fixedText = fixedCols?.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                            ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                            ?.optString("text")
                        if (!fixedText.isNullOrBlank()) {
                            parsedDurationMs = parseDurationStringMs(fixedText)
                        }
                    }

                    var coverUrl = ""
                    val thumbs = item.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    if (thumbs != null && thumbs.length() > 0) {
                        coverUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
                    }
                    val hiResCover = coverUrl
                        .replace("w60-h60", "w544-h544")
                        .replace("w120-h120", "w544-h544")
                        .ifEmpty { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

                    val idLong = videoId.hashCode().toLong()
                    if (list.none { it.id == idLong }) {
                        list.add(
                            AudioTrack(
                                id = idLong,
                                title = title,
                                artist = artist,
                                album = albumName,
                                uri = Uri.parse("youtube://$videoId"),
                                albumId = -7L,
                                duration = parsedDurationMs.coerceAtLeast(180_000L),
                                trackNumber = list.size + 1,
                                discNumber = 1,
                                source = "youtube",
                                coverUrl = hiResCover
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        list
    }

    suspend fun resolveStreamUri(videoId: String): Uri = withContext(Dispatchers.IO) {
        val cleanId = videoId.substringAfter("youtube://").substringBefore("?").trim()
        resolveDirectStream(cleanId, forceRefresh = false)
        val proxyPort = LocalYoutubeStreamProxy.ensureStarted()
        Uri.parse("http://127.0.0.1:$proxyPort/yt/$cleanId")
    }

    internal fun resolveDirectStream(videoId: String, forceRefresh: Boolean = false): ResolvedYtStream {
        val cleanId = videoId.substringAfter("youtube://").substringBefore("?").trim()
        val now = System.currentTimeMillis()

        if (!forceRefresh) {
            streamUrlCache.get(cleanId)?.let { cached ->
                if (cached.expiresAtMs > now + 60_000L) {
                    return cached
                }
            }
        } else {
            streamUrlCache.remove(cleanId)
        }

        data class InnerTubePlayerClient(
            val clientJson: String,
            val userAgent: String,
            val clientIdHeader: String,
            val clientVersionHeader: String,
            val isEmbedded: Boolean = false
        )

        // Важно: для нативных клиентов НЕ передаем веб-заголовок Origin и чужой visitorData,
        // иначе YouTube помечает запрос как поддельный и отдает 403 на googlevideo.com
        val clients = listOf(
            InnerTubePlayerClient(
                clientJson = """
                    {
                        "clientName": "ANDROID_VR",
                        "clientVersion": "1.43.32",
                        "deviceMake": "Oculus",
                        "deviceModel": "Quest 3",
                        "androidSdkVersion": 32,
                        "osName": "Android",
                        "osVersion": "12L",
                        "hl": "en",
                        "gl": "US"
                    }
                """.trimIndent(),
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                clientIdHeader = "28",
                clientVersionHeader = "1.43.32"
            ),
            InnerTubePlayerClient(
                clientJson = """
                    {
                        "clientName": "ANDROID_VR",
                        "clientVersion": "1.61.48",
                        "deviceMake": "Oculus",
                        "deviceModel": "Quest 3",
                        "androidSdkVersion": 32,
                        "osName": "Android",
                        "osVersion": "12L",
                        "hl": "en",
                        "gl": "US"
                    }
                """.trimIndent(),
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                clientIdHeader = "28",
                clientVersionHeader = "1.61.48"
            ),
            InnerTubePlayerClient(
                clientJson = """
                    {
                        "clientName": "VISIONOS",
                        "clientVersion": "0.1",
                        "deviceMake": "Apple",
                        "deviceModel": "RealityDevice14,1",
                        "osName": "VisionOS",
                        "osVersion": "1.3.21O771",
                        "hl": "en",
                        "gl": "US"
                    }
                """.trimIndent(),
                userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
                clientIdHeader = "101",
                clientVersionHeader = "0.1"
            ),
            InnerTubePlayerClient(
                clientJson = """
                    {
                        "clientName": "IOS",
                        "clientVersion": "19.45.4",
                        "deviceMake": "Apple",
                        "deviceModel": "iPhone16,2",
                        "osName": "iPhone",
                        "osVersion": "18.1.0.22B83",
                        "hl": "en",
                        "gl": "US"
                    }
                """.trimIndent(),
                userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
                clientIdHeader = "5",
                clientVersionHeader = "19.45.4"
            ),
            InnerTubePlayerClient(
                clientJson = """
                    {
                        "clientName": "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                        "clientVersion": "2.0",
                        "hl": "en",
                        "gl": "US"
                    }
                """.trimIndent(),
                userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.02) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15",
                clientIdHeader = "85",
                clientVersionHeader = "2.0",
                isEmbedded = true
            )
        )

        for (client in clients) {
            val payload = """
                {
                    "context": {
                        "client": ${client.clientJson}
                        ${if (client.isEmbedded) ""","thirdParty": { "embedUrl": "https://www.youtube.com/" }""" else ""}
                    },
                    "videoId": "$cleanId",
                    "playbackContext": {
                        "contentPlaybackContext": {
                            "html5Preference": "HTML5_PREF_WANTS",
                            "signatureTimestamp": 20073
                        }
                    },
                    "contentCheckOk": true,
                    "racyCheckOk": true
                }
            """.trimIndent()

            val reqBuilder = Request.Builder()
                .url("https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false&key=$cachedApiKey")
                .post(payload.toRequestBody(jsonMediaType))
                .header("User-Agent", client.userAgent)
                .header("X-YouTube-Client-Name", client.clientIdHeader)
                .header("X-YouTube-Client-Version", client.clientVersionHeader)
                .header("X-Goog-Api-Format-Version", "2")

            try {
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JSONObject(body)
                            val streamingData = json.optJSONObject("streamingData")
                            val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")
                            if (adaptiveFormats != null) {
                                val candidates = mutableListOf<Triple<String, String, Int>>()
                                for (i in 0 until adaptiveFormats.length()) {
                                    val fmt = adaptiveFormats.getJSONObject(i)
                                    val mime = fmt.optString("mimeType", "")
                                    val url = fmt.optString("url", "")
                                    if (mime.startsWith("audio/") && url.isNotEmpty()) {
                                        val bitrate = fmt.optInt("bitrate", 0)
                                        val isM4a = mime.contains("mp4a")
                                        val score = bitrate + (if (isM4a) 80_000 else 0)
                                        candidates.add(Triple(url, mime.substringBefore(";").trim(), score))
                                    }
                                }
                                candidates.sortByDescending { it.third }

                                for ((candUrl, candMime, _) in candidates.take(2)) {
                                    if (verifyStreamAccessible(candUrl, client.userAgent)) {
                                        val parsedUri = Uri.parse(candUrl)
                                        val expireSec = parsedUri.getQueryParameter("expire")?.toLongOrNull()
                                        val expiresAt = if (expireSec != null) expireSec * 1000L else (now + 3_600_000L)
                                        val resolved = ResolvedYtStream(
                                            url = candUrl,
                                            userAgent = client.userAgent,
                                            expiresAtMs = expiresAt,
                                            mimeType = candMime
                                        )
                                        streamUrlCache.put(cleanId, resolved)
                                        return resolved
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Резервный пул Invidious с проксированием (&local=true), чтобы поток шел через сервер Invidious без IP-блокировки 403
        val invidiousInstances = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://invidious.Jing.rocks",
            "https://invidious.privacyredirect.com"
        )
        val browserUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"

        for (instance in invidiousInstances) {
            val proxiedUrl = "$instance/latest_version?id=$cleanId&itag=140&local=true"
            if (verifyStreamAccessible(proxiedUrl, browserUa)) {
                val resolved = ResolvedYtStream(
                    url = proxiedUrl,
                    userAgent = browserUa,
                    expiresAtMs = now + 1_800_000L,
                    mimeType = "audio/mp4"
                )
                streamUrlCache.put(cleanId, resolved)
                return resolved
            }
        }

        // Резервный пул Piped с обязательной проверкой verifyStreamAccessible
        val pipedInstances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.tokhmi.xyz",
            "https://pipedapi.smnz.de",
            "https://api.piped.private.coffee"
        )

        for (instance in pipedInstances) {
            try {
                val req = Request.Builder()
                    .url("$instance/streams/$cleanId")
                    .header("User-Agent", browserUa)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val streams: JSONArray? = JSONObject(body).optJSONArray("audioStreams")
                            if (streams != null) {
                                var bestUrl = ""
                                var highestBitrate = 0
                                for (i in 0 until streams.length()) {
                                    val s = streams.getJSONObject(i)
                                    val bitrate = s.optInt("bitrate", 0)
                                    val url = s.optString("url", "")
                                    if (url.isNotEmpty() && bitrate > highestBitrate) {
                                        highestBitrate = bitrate
                                        bestUrl = url
                                    }
                                }
                                if (bestUrl.isNotEmpty() && verifyStreamAccessible(bestUrl, browserUa)) {
                                    val resolved = ResolvedYtStream(
                                        url = bestUrl,
                                        userAgent = browserUa,
                                        expiresAtMs = now + 1_800_000L
                                    )
                                    streamUrlCache.put(cleanId, resolved)
                                    return resolved
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        throw IllegalStateException("YouTube Music stream unavailable for $cleanId")
    }

    private fun verifyStreamAccessible(url: String, userAgent: String): Boolean {
        return try {
            val probeReq = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-511")
                .build()
            httpClient.newCall(probeReq).execute().use { resp ->
                if (resp.code !in 200..299) return false
                val firstByte = resp.body?.byteStream()?.read() ?: -1
                firstByte != -1
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseDurationStringMs(raw: String): Long {
        if (!raw.contains(":")) return 0L
        val parts = raw.trim().split(":")
        if (parts.any { it.toLongOrNull() == null }) return 0L
        return when (parts.size) {
            2 -> ((parts[0].toLong() * 60L) + parts[1].toLong()) * 1000L
            3 -> ((parts[0].toLong() * 3600L) + (parts[1].toLong() * 60L) + parts[2].toLong()) * 1000L
            else -> 0L
        }
    }
}

private object LocalYoutubeStreamProxy {

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var boundPort: Int = 0

    private val executor = Executors.newCachedThreadPool()

    @Synchronized
    fun ensureStarted(): Int {
        if (serverSocket != null && !serverSocket!!.isClosed && boundPort > 0) {
            return boundPort
        }
        val socket = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        boundPort = socket.localPort

        executor.execute {
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    executor.execute { handleClient(client) }
                } catch (_: Exception) {
                    if (socket.isClosed) break
                }
            }
        }
        return boundPort
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = 15_000
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.US_ASCII))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val path = parts[1]
                val videoId = path.substringAfter("/yt/").substringBefore("?").trim()
                if (videoId.isEmpty()) return

                var rangeHeader: String? = null
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substringAfter(":").trim()
                    }
                }

                var stream = runCatching {
                    InnerTubeClient.resolveDirectStream(videoId, forceRefresh = false)
                }.getOrNull() ?: return

                var upstreamResp = openUpstream(stream, rangeHeader)

                if (upstreamResp == null || !upstreamResp.isSuccessful) {
                    upstreamResp?.close()
                    stream = runCatching {
                        InnerTubeClient.resolveDirectStream(videoId, forceRefresh = true)
                    }.getOrNull() ?: return
                    upstreamResp = openUpstream(stream, rangeHeader)
                }

                val resp = upstreamResp ?: return
                resp.use { activeResp ->
                    val out = BufferedOutputStream(client.getOutputStream(), 32 * 1024)
                    val statusLine = when {
                        activeResp.code == 206 -> "HTTP/1.1 206 Partial Content\r\n"
                        activeResp.isSuccessful -> "HTTP/1.1 200 OK\r\n"
                        else -> "HTTP/1.1 ${activeResp.code} Error\r\n"
                    }

                    val contentType = activeResp.header("Content-Type") ?: stream.mimeType
                    val contentLength = activeResp.header("Content-Length")
                    val contentRange = activeResp.header("Content-Range")

                    val headersSb = StringBuilder(statusLine)
                    headersSb.append("Content-Type: $contentType\r\n")
                    headersSb.append("Accept-Ranges: bytes\r\n")
                    if (!contentLength.isNullOrBlank()) {
                        headersSb.append("Content-Length: $contentLength\r\n")
                    }
                    if (!contentRange.isNullOrBlank()) {
                        headersSb.append("Content-Range: $contentRange\r\n")
                    }
                    headersSb.append("Connection: close\r\n\r\n")

                    out.write(headersSb.toString().toByteArray(Charsets.US_ASCII))
                    out.flush()

                    if (method != "HEAD" && activeResp.isSuccessful) {
                        val bodyStream = activeResp.body?.byteStream()
                        if (bodyStream != null) {
                            val buf = ByteArray(32 * 1024)
                            while (true) {
                                val read = bodyStream.read(buf)
                                if (read == -1) break
                                out.write(buf, 0, read)
                            }
                            out.flush()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun openUpstream(stream: InnerTubeClient.ResolvedYtStream, rangeHeader: String?): okhttp3.Response? {
        return try {
            val reqBuilder = Request.Builder()
                .url(stream.url)
                .header("User-Agent", stream.userAgent)
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")
            if (!rangeHeader.isNullOrBlank()) {
                reqBuilder.header("Range", rangeHeader)
            }
            InnerTubeClient.httpClient.newCall(reqBuilder.build()).execute()
        } catch (_: Exception) {
            null
        }
    }
}