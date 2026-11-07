package com.alcint.pargelium

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import android.util.Xml
import androidx.appcompat.app.AppCompatDelegate
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParser
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.io.File
import java.io.StringReader
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val LRC_RED_CLIENT_IDENTITY =
    "Pargelium/1.2 (https://github.com/A1cInt/Pargelium; 148353020+A1cInt@users.noreply.github.com)"

data class LrcRedHit(
    @SerializedName("isrc") val isrc: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("artist") val artist: String?,
    @SerializedName("album") val album: String?,
    @SerializedName("year") val year: Int?,
    @SerializedName("duration") val duration: Double?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("color") val color: String?
)

data class LrcRedSearchResponse(
    @SerializedName("hits") val hits: List<LrcRedHit>?,
    @SerializedName("next") val next: String?
)

data class LrcRedFiles(
    @SerializedName("ttml") val ttml: String?,
    @SerializedName("lrc") val lrc: String?,
    @SerializedName("lyricsfile") val lyricsfile: String?
)

data class LrcRedTrackResponse(
    @SerializedName("isrc") val isrc: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("artist") val artist: String?,
    @SerializedName("album") val album: String?,
    @SerializedName("duration") val duration: Double?,
    @SerializedName("sync") val sync: String?,
    @SerializedName("page") val page: String?,
    @SerializedName("files") val files: LrcRedFiles?,
    @SerializedName("cover") val cover: String?
)

data class BetterLyricsWord(
    @SerializedName("text") val text: String?,
    @SerializedName("offset") val offset: Long?,
    @SerializedName("duration") val duration: Long?
)

data class BetterLyricsLine(
    @SerializedName("text") val text: String?,
    @SerializedName("offset") val offset: Long?,
    @SerializedName("duration") val duration: Long?,
    @SerializedName("words") val words: List<BetterLyricsWord>?
)

data class BetterLyricsResponse(
    @SerializedName("lines") val lines: List<BetterLyricsLine>?,
    @SerializedName("lyrics") val lyrics: String?
)

data class LyricsPlusWord(
    @SerializedName("string") val string: String?,
    @SerializedName("time") val time: Long?,
    @SerializedName("duration") val duration: Long?
)

data class LyricsPlusLine(
    @SerializedName("words") val words: List<LyricsPlusWord>?,
    @SerializedName("time") val time: Long?
)

data class LyricsPlusResponse(
    @SerializedName("lines") val lines: List<LyricsPlusLine>?
)

data class LrcLibResponse(
    @SerializedName("syncedLyrics") val syncedLyrics: String?,
    @SerializedName("plainLyrics") val plainLyrics: String?
)

data class OvhResponse(
    val lyrics: String?
)

data class MxResponse(
    val message: MxMessage?
)

data class MxMessage(
    val body: MxBody?
)

data class MxBody(
    val lyrics: MxLyrics?
)

data class MxLyrics(
    val lyrics_body: String?
)

interface LyricsNetworkApi {
    @Headers("X-Client-ID: $LRC_RED_CLIENT_IDENTITY")
    @GET("https://lrc.red/search.json")
    suspend fun searchLrcRed(
        @Query("q") query: String
    ): LrcRedSearchResponse

    @Headers("X-Client-ID: $LRC_RED_CLIENT_IDENTITY")
    @GET("https://lrc.red/s/{isrc}.json")
    suspend fun getLrcRedTrack(
        @Path("isrc") isrc: String
    ): LrcRedTrackResponse

    @Headers("X-Client-ID: $LRC_RED_CLIENT_IDENTITY")
    @GET
    suspend fun downloadRawText(@Url url: String): ResponseBody

    @GET("https://lyrics-api.boidu.dev/lyrics")
    suspend fun getBetterLyrics(
        @Query("song") song: String,
        @Query("artist") artist: String,
        @Query("duration") duration: Int
    ): BetterLyricsResponse

    @GET("https://lyricsplus.prjktla.my.id/v1/lyrics")
    suspend fun getLyricsPlus(
        @Query("title") title: String,
        @Query("artist") artist: String,
        @Query("duration") duration: Int
    ): LyricsPlusResponse

    @GET("https://lrclib.net/api/get")
    suspend fun getLrcLib(
        @Query("artist_name") artist: String,
        @Query("track_name") track: String,
        @Query("album_name") album: String?,
        @Query("duration") duration: Int
    ): LrcLibResponse

    @GET("https://lrclib.net/api/search")
    suspend fun searchLrcLib(
        @Query("q") query: String
    ): List<LrcLibResponse>

    @GET("https://api.musixmatch.com/ws/1.1/matcher.lyrics.get")
    suspend fun getMusixmatch(
        @Query("q_artist") artist: String,
        @Query("q_track") track: String,
        @Query("apikey") apiKey: String = ""
    ): MxResponse

    @GET
    suspend fun getOvh(@Url url: String): OvhResponse

    @FormUrlEncoded
    @POST("https://translate.googleapis.com/translate_a/single")
    suspend fun translateGooglePost(
        @Query("client") client: String = "gtx",
        @Query("sl") sl: String = "auto",
        @Query("tl") tl: String,
        @Query("dt") dt: String = "t",
        @Field("q") q: String
    ): JsonElement
}

object LyricsManager {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            val host = req.url.host.lowercase(Locale.ROOT)
            val builder = req.newBuilder()
            if (host.contains("lrc.red")) {
                builder.header("User-Agent", LRC_RED_CLIENT_IDENTITY)
                builder.header("X-Client-ID", LRC_RED_CLIENT_IDENTITY)
            } else {
                builder.header("User-Agent", LRC_RED_CLIENT_IDENTITY)
            }
            chain.proceed(builder.build())
        }
        .build()

    private val gson = Gson()
    private val lyricListType = object : TypeToken<List<LyricLine>>() {}.type
    private val memoryCache = LruCache<String, List<LyricLine>>(48)
    private val trackCreditUrlMap = ConcurrentHashMap<Long, String>()

    private val api = Retrofit.Builder()
        .baseUrl("https://lrc.red/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(LyricsNetworkApi::class.java)

    private val parenCleanupRegex = Regex("(?i)\\s*\\(?(feat\\.|ft\\.|remaster|official|lyric|video|audio|radio).*?\\)?")
    private val bracketCleanupRegex = Regex("(?i)\\s*\\[(feat\\.|ft\\.|remaster|official|lyric|video|audio|radio).*?]")
    private val dashCleanupRegex = Regex("(?i)\\s*-.*?(Remaster|Edit|Mix|Version).*")
    private val multiSpaceRegex = Regex("\\s+")

    fun getCreditUrlForTrack(trackId: Long): String? = trackCreditUrlMap[trackId]

    private fun getCurrentAppLanguage(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        return if (!appLocales.isEmpty) {
            appLocales[0]?.language ?: Locale.getDefault().language
        } else {
            Locale.getDefault().language
        }
    }

    private fun cleanMetadata(text: String): String {
        return text
            .replace(parenCleanupRegex, "")
            .replace(bracketCleanupRegex, "")
            .replace(dashCleanupRegex, "")
            .trim()
    }

    suspend fun getLyrics(context: Context, track: AudioTrack): List<LyricLine> = withContext(Dispatchers.IO) {
        val cacheKey = getCacheKey(track)
        memoryCache.get(cacheKey)?.let { cached ->
            if (cached.isNotEmpty()) return@withContext cached
        }

        val localContent = AudioRepository.findLrcContentForTrack(context, track)
            ?: findLocalLrcFile(context, track)

        if (!localContent.isNullOrBlank()) {
            val parsed = parseRawLyricsOrTtml(localContent)
            if (parsed.isNotEmpty()) {
                saveToCache(context, track, parsed)
                return@withContext parsed
            }
        }

        val diskCached = loadFromCache(context, track)
        if (!diskCached.isNullOrEmpty()) {
            return@withContext diskCached
        }

        // 1. Приоритет №1: lrc.red (TTML -> LRC)
        val lrcRedLyrics = fetchLrcRedOnline(context, track)
        if (lrcRedLyrics.isNotEmpty()) {
            saveToCache(context, track, lrcRedLyrics)
            return@withContext lrcRedLyrics
        }

        // 2. Приоритет №2: BetterLyrics / LyricsPlus
        val betterLyrics = fetchBetterLyricsOnline(track)
        if (betterLyrics.isNotEmpty()) {
            saveToCache(context, track, betterLyrics)
            return@withContext betterLyrics
        }

        // 3. Приоритет №3: LRCLIB / Musixmatch / Lyrics.ovh
        val fallbackOnline = fetchFallbackOnlineLyrics(track)
        if (fallbackOnline.isNotEmpty()) {
            saveToCache(context, track, fallbackOnline)
            return@withContext fallbackOnline
        }

        val embeddedLyrics = getEmbeddedLyrics(context, track.uri)
        if (!embeddedLyrics.isNullOrBlank()) {
            val parsed = parseRawLyricsOrTtml(embeddedLyrics)
            if (parsed.isNotEmpty()) {
                saveToCache(context, track, parsed)
                return@withContext parsed
            }
        }

        return@withContext emptyList()
    }

    suspend fun searchLyricsOnline(context: Context, track: AudioTrack): List<LyricLine> = withContext(Dispatchers.IO) {
        val lrcRedLyrics = fetchLrcRedOnline(context, track)
        if (lrcRedLyrics.isNotEmpty()) {
            saveToCache(context, track, lrcRedLyrics)
            return@withContext lrcRedLyrics
        }

        val betterLyrics = fetchBetterLyricsOnline(track)
        if (betterLyrics.isNotEmpty()) {
            saveToCache(context, track, betterLyrics)
            return@withContext betterLyrics
        }

        val fallback = fetchFallbackOnlineLyrics(track)
        if (fallback.isNotEmpty()) {
            saveToCache(context, track, fallback)
            return@withContext fallback
        }

        return@withContext emptyList()
    }

    /**
     * Поиск и загрузка текстов с lrc.red с приоритетом послогового/пословного формата TTML (.ttml).
     */
    private suspend fun fetchLrcRedOnline(context: Context, track: AudioTrack): List<LyricLine> {
        val validMetadata = track.artist.isNotBlank() && track.title.isNotBlank() && !track.artist.contains("Unknown", true)
        if (!validMetadata) return emptyList()

        val cleanArtist = cleanMetadata(track.artist)
        val cleanTitle = cleanMetadata(track.title)
        val targetDurationSec = track.duration / 1000.0

        val queries = listOfNotNull(
            "$cleanArtist $cleanTitle".trim().takeIf { it.isNotEmpty() },
            "${track.artist} ${track.title}".trim().takeIf { it != "$cleanArtist $cleanTitle" },
            cleanTitle.takeIf { it.isNotEmpty() }
        )

        var bestHit: LrcRedHit? = null
        var bestScore = -1

        for (query in queries) {
            try {
                val searchResp = api.searchLrcRed(query)
                val hits = searchResp.hits.orEmpty()
                for (hit in hits) {
                    val isrc = hit.isrc?.trim().orEmpty()
                    if (isrc.isEmpty()) continue

                    val score = scoreLrcRedHit(hit, cleanTitle, cleanArtist, track.album, targetDurationSec)
                    if (score > bestScore) {
                        bestScore = score
                        bestHit = hit
                    }
                }
                if (bestScore >= 55 && bestHit != null) break
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }

        val chosenHit = bestHit?.takeIf { bestScore >= 25 } ?: return emptyList()
        val isrc = chosenHit.isrc!!.trim()

        var ttmlUrl = "https://lrc.red/s/$isrc.ttml"
        var lrcUrl = "https://lrc.red/s/$isrc.lrc"
        var pageUrl = "https://lrc.red/s/$isrc"

        try {
            val trackMeta = api.getLrcRedTrack(isrc)
            trackMeta.page?.takeIf { it.isNotBlank() }?.let { pageUrl = it }
            trackMeta.files?.ttml?.takeIf { it.isNotBlank() }?.let { ttmlUrl = it }
            trackMeta.files?.lrc?.takeIf { it.isNotBlank() }?.let { lrcUrl = it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        // 1. В приоритете загружаем именно TTML (.ttml)
        try {
            val rawTtml = api.downloadRawText(ttmlUrl).use { it.string() }
            if (rawTtml.contains("<tt", ignoreCase = true) || rawTtml.contains("<p", ignoreCase = true)) {
                val parsedTtml = parseTtmlLyrics(rawTtml)
                if (parsedTtml.isNotEmpty()) {
                    trackCreditUrlMap[track.id] = pageUrl
                    saveCreditUrlToDisk(context, track, pageUrl)
                    return parsedTtml
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        // 2. Если TTML недоступен для этого трека, пробуем .lrc с lrc.red
        try {
            val rawLrc = api.downloadRawText(lrcUrl).use { it.string() }
            if (rawLrc.isNotBlank()) {
                val parsedLrc = AudioRepository.parseLyrics(rawLrc)
                if (parsedLrc.isNotEmpty()) {
                    trackCreditUrlMap[track.id] = pageUrl
                    saveCreditUrlToDisk(context, track, pageUrl)
                    return parsedLrc
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        return emptyList()
    }

    private fun scoreLrcRedHit(
        hit: LrcRedHit,
        cleanTitle: String,
        cleanArtist: String,
        album: String,
        targetDurationSec: Double
    ): Int {
        val hitTitle = cleanMetadata(hit.title.orEmpty()).lowercase(Locale.ROOT)
        val hitArtist = cleanMetadata(hit.artist.orEmpty()).lowercase(Locale.ROOT)
        val reqTitle = cleanTitle.lowercase(Locale.ROOT)
        val reqArtist = cleanArtist.lowercase(Locale.ROOT)

        var score = 0
        when {
            hitTitle == reqTitle -> score += 45
            hitTitle.contains(reqTitle) || reqTitle.contains(hitTitle) -> score += 28
            else -> return -10
        }

        val primaryArtist = reqArtist.split(",", "&", "/", ";").firstOrNull()?.trim().orEmpty()
        when {
            hitArtist == reqArtist -> score += 35
            primaryArtist.isNotEmpty() && (hitArtist.contains(primaryArtist) || primaryArtist.contains(hitArtist)) -> score += 25
            else -> score -= 12
        }

        if (album.isNotBlank() && !hit.album.isNullOrBlank()) {
            if (hit.album.equals(album, ignoreCase = true)) score += 10
        }

        if (targetDurationSec > 5.0 && hit.duration != null && hit.duration > 0.0) {
            val diff = abs(hit.duration - targetDurationSec)
            when {
                diff <= 2.5 -> score += 20
                diff <= 6.0 -> score += 12
                diff <= 15.0 -> score += 4
                diff > 35.0 -> score -= 20
            }
        }

        return score
    }

    fun parseRawLyricsOrTtml(rawContent: String): List<LyricLine> {
        val trimmed = rawContent.trimStart()
        if (trimmed.startsWith("<?xml", ignoreCase = true) ||
            trimmed.startsWith("<tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true)
        ) {
            val ttmlLines = parseTtmlLyrics(trimmed)
            if (ttmlLines.isNotEmpty()) return ttmlLines
        }
        return AudioRepository.parseLyrics(rawContent)
    }

    /**
     * Быстрый парсер TTML (Apple Music / lrc.red формат) на базе XmlPullParser.
     * Извлекает тайминги строк <p>, послоговые/пословные тайминги <span>, пробелы между словами,
     * бэк-вокал (ttm:role="x-bg") и переводы (ttm:role="x-translation").
     */
    fun parseTtmlLyrics(ttmlContent: String): List<LyricLine> {
        val lines = ArrayList<LyricLine>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(ttmlContent))

            var inParagraph = false
            var pBeginMs = 0L
            var pEndMs = 0L
            val pTextBuilder = StringBuilder()
            val pTranslationBuilder = StringBuilder()
            val pWords = ArrayList<SyllableWord>()

            var activeSpanBeginMs = -1L
            var activeSpanEndMs = -1L
            val activeSpanText = StringBuilder()
            var spanDepth = 0
            var inTranslationSpan = false
            var inBgVocalSpan = false

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name.substringAfterLast(':').lowercase(Locale.ROOT)
                        if (tagName == "p") {
                            inParagraph = true
                            pBeginMs = parseTtmlTimeMs(getAttr(parser, "begin")) ?: 0L
                            pEndMs = parseTtmlTimeMs(getAttr(parser, "end")) ?: (pBeginMs + 3500L)
                            pTextBuilder.setLength(0)
                            pTranslationBuilder.setLength(0)
                            pWords.clear()
                            spanDepth = 0
                            inTranslationSpan = false
                            inBgVocalSpan = false
                        } else if (inParagraph && tagName == "span") {
                            spanDepth++
                            val role = getAttr(parser, "ttm:role") ?: getAttr(parser, "role")
                            if (role.equals("x-translation", ignoreCase = true)) {
                                inTranslationSpan = true
                            } else if (role.equals("x-bg", ignoreCase = true)) {
                                inBgVocalSpan = true
                            }

                            val sBegin = parseTtmlTimeMs(getAttr(parser, "begin"))
                            val sEnd = parseTtmlTimeMs(getAttr(parser, "end"))
                            if (!inTranslationSpan && sBegin != null) {
                                activeSpanBeginMs = sBegin
                                activeSpanEndMs = sEnd ?: (sBegin + 300L)
                                activeSpanText.setLength(0)
                            }
                        } else if (inParagraph && tagName == "br") {
                            pTextBuilder.append(' ')
                        }
                    }

                    XmlPullParser.TEXT -> {
                        if (inParagraph) {
                            val text = parser.text ?: ""
                            if (inTranslationSpan) {
                                pTranslationBuilder.append(text)
                            } else if (activeSpanBeginMs >= 0L) {
                                activeSpanText.append(text)
                                pTextBuilder.append(text)
                            } else {
                                // Пробелы между </span> <span ...> означают границу между словами!
                                if (text.any { it.isWhitespace() } && pWords.isNotEmpty()) {
                                    val lastIdx = pWords.lastIndex
                                    val lastWord = pWords[lastIdx]
                                    if (!lastWord.text.endsWith(" ")) {
                                        pWords[lastIdx] = lastWord.copy(text = lastWord.text + " ")
                                    }
                                }
                                pTextBuilder.append(text)
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name.substringAfterLast(':').lowercase(Locale.ROOT)
                        if (inParagraph && tagName == "span") {
                            if (!inTranslationSpan && activeSpanBeginMs >= 0L) {
                                val rawSyllable = activeSpanText.toString()
                                val hasTrailingSpace = rawSyllable.endsWith(" ")
                                val cleanSyllable = rawSyllable.replace(multiSpaceRegex, " ").trim()
                                if (cleanSyllable.isNotEmpty()) {
                                    val finalSyllableText = if (hasTrailingSpace) "$cleanSyllable " else cleanSyllable
                                    val endMs = activeSpanEndMs.coerceAtLeast(activeSpanBeginMs + 40L)
                                    pWords.add(
                                        SyllableWord(
                                            text = finalSyllableText,
                                            startMs = activeSpanBeginMs,
                                            endMs = endMs
                                        )
                                    )
                                }
                                activeSpanBeginMs = -1L
                                activeSpanEndMs = -1L
                                activeSpanText.setLength(0)
                            }
                            spanDepth = (spanDepth - 1).coerceAtLeast(0)
                            if (spanDepth == 0) {
                                inTranslationSpan = false
                                inBgVocalSpan = false
                            }
                        } else if (tagName == "p" && inParagraph) {
                            inParagraph = false
                            val normalizedLineText = if (pWords.isNotEmpty()) {
                                buildString {
                                    pWords.forEach { w -> append(w.text) }
                                }.replace(multiSpaceRegex, " ").trim()
                            } else {
                                pTextBuilder.toString().replace(multiSpaceRegex, " ").trim()
                            }

                            if (normalizedLineText.isNotEmpty()) {
                                val lineStartMs = if (pBeginMs > 0L) {
                                    pBeginMs
                                } else {
                                    pWords.firstOrNull()?.startMs ?: 0L
                                }
                                val trans = pTranslationBuilder.toString().trim().takeIf { it.isNotEmpty() }
                                lines.add(
                                    LyricLine(
                                        timeMs = lineStartMs,
                                        text = normalizedLineText,
                                        translation = trans,
                                        words = pWords.toList()
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("LyricsManager", "TTML parse error", e)
        }

        return lines.sortedBy { it.timeMs }
    }

    private fun getAttr(parser: XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName.equals(name, ignoreCase = true) ||
                attrName.substringAfterLast(':').equals(name, ignoreCase = true)
            ) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun parseTtmlTimeMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().lowercase(Locale.ROOT)
        return try {
            when {
                s.endsWith("ms") -> s.removeSuffix("ms").trim().toDouble().toLong()
                s.endsWith("s") -> (s.removeSuffix("s").trim().toDouble() * 1000.0).toLong()
                s.endsWith("m") -> (s.removeSuffix("m").trim().toDouble() * 60_000.0).toLong()
                s.endsWith("h") -> (s.removeSuffix("h").trim().toDouble() * 3_600_000.0).toLong()
                s.contains(':') -> {
                    val parts = s.split(':')
                    when (parts.size) {
                        2 -> {
                            val min = parts[0].toLong()
                            val sec = parts[1].toDouble()
                            (min * 60_000L) + (sec * 1000.0).toLong()
                        }
                        3 -> {
                            val hrs = parts[0].toLong()
                            val min = parts[1].toLong()
                            val sec = parts[2].toDouble()
                            (hrs * 3_600_000L) + (min * 60_000L) + (sec * 1000.0).toLong()
                        }
                        else -> null
                    }
                }
                else -> (s.toDouble() * 1000.0).toLong()
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchBetterLyricsOnline(track: AudioTrack): List<LyricLine> {
        val validMetadata = track.artist.isNotBlank() && track.title.isNotBlank() && !track.artist.contains("Unknown", true)
        if (!validMetadata) return emptyList()

        val cleanArtist = cleanMetadata(track.artist)
        val cleanTitle = cleanMetadata(track.title)
        val durationSec = (track.duration / 1000).toInt()

        try {
            val betterResp = api.getBetterLyrics(cleanTitle, cleanArtist, durationSec)
            if (!betterResp.lines.isNullOrEmpty()) {
                val result = ArrayList<LyricLine>(betterResp.lines.size)
                for (line in betterResp.lines) {
                    val lineStart = line.offset ?: 0L
                    val text = line.text ?: ""
                    val wordsList = ArrayList<SyllableWord>(line.words?.size ?: 0)

                    if (!line.words.isNullOrEmpty()) {
                        for (w in line.words) {
                            val wStart = lineStart + (w.offset ?: 0L)
                            val wEnd = wStart + (w.duration ?: 300L).coerceAtLeast(1L)
                            val wText = w.text?.trim() ?: ""
                            if (wText.isNotEmpty()) {
                                wordsList.add(SyllableWord("$wText ", wStart, wEnd))
                            }
                        }
                    }
                    val finalText = text.trim().ifEmpty {
                        wordsList.joinToString("") { it.text }.trim()
                    }
                    if (finalText.isNotEmpty()) {
                        result.add(LyricLine(lineStart, finalText, null, wordsList))
                    }
                }
                if (result.isNotEmpty()) {
                    return result.sortedBy { it.timeMs }
                }
            } else if (!betterResp.lyrics.isNullOrBlank()) {
                val parsed = parseRawLyricsOrTtml(betterResp.lyrics)
                if (parsed.isNotEmpty()) return parsed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        try {
            val lpResp = api.getLyricsPlus(cleanTitle, cleanArtist, durationSec)
            if (!lpResp.lines.isNullOrEmpty()) {
                val result = ArrayList<LyricLine>(lpResp.lines.size)
                for (line in lpResp.lines) {
                    val lineStart = line.time ?: 0L
                    val wordsList = ArrayList<SyllableWord>(line.words?.size ?: 0)
                    val fullTextBuilder = StringBuilder()

                    if (!line.words.isNullOrEmpty()) {
                        for (w in line.words) {
                            val wStart = w.time ?: lineStart
                            val wEnd = wStart + (w.duration ?: 300L).coerceAtLeast(1L)
                            val rawWord = w.string ?: ""
                            val trimmedWord = rawWord.trim()
                            if (trimmedWord.isNotEmpty()) {
                                wordsList.add(SyllableWord("$trimmedWord ", wStart, wEnd))
                                if (fullTextBuilder.isNotEmpty()) {
                                    fullTextBuilder.append(' ')
                                }
                                fullTextBuilder.append(trimmedWord)
                            }
                        }
                    }
                    val fullText = fullTextBuilder.toString().trim()
                    if (fullText.isNotBlank()) {
                        result.add(LyricLine(lineStart, fullText, null, wordsList))
                    }
                }
                if (result.isNotEmpty()) {
                    return result.sortedBy { it.timeMs }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        return emptyList()
    }

    private suspend fun fetchFallbackOnlineLyrics(track: AudioTrack): List<LyricLine> {
        val validMetadata = track.artist.isNotBlank() && track.title.isNotBlank() && !track.artist.contains("Unknown", true)
        if (!validMetadata) return emptyList()

        val cleanArtist = cleanMetadata(track.artist)
        val cleanTitle = cleanMetadata(track.title)
        val durationSec = (track.duration / 1000).toInt()

        var rawLyrics: String? = null

        try {
            val response = api.getLrcLib(cleanArtist, cleanTitle, track.album.takeIf { it.isNotBlank() }, durationSec)
            rawLyrics = response.syncedLyrics ?: response.plainLyrics
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            try {
                val searchResponse = api.searchLrcLib("$cleanArtist $cleanTitle")
                if (searchResponse.isNotEmpty()) {
                    rawLyrics = searchResponse[0].syncedLyrics ?: searchResponse[0].plainLyrics
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {}
        }

        if (rawLyrics.isNullOrBlank()) {
            try {
                val response = api.getMusixmatch(cleanArtist, cleanTitle)
                rawLyrics = response.message?.body?.lyrics?.lyrics_body
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }

        if (rawLyrics.isNullOrBlank()) {
            try {
                val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8").replace("+", "%20")
                val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8").replace("+", "%20")
                val ovhUrl = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"

                val response = api.getOvh(ovhUrl)
                rawLyrics = response.lyrics
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }

        if (!rawLyrics.isNullOrBlank()) {
            return parseRawLyricsOrTtml(rawLyrics)
        }

        return emptyList()
    }

    suspend fun translateLyrics(context: Context, track: AudioTrack, lines: List<LyricLine>): List<LyricLine> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext lines

        try {
            val targetLang = getCurrentAppLanguage()
            val chunkedLines = lines.chunked(25)
            val translatedLines = ArrayList<String>(lines.size)

            for (chunk in chunkedLines) {
                val originalText = chunk.joinToString("\n") { it.text.ifBlank { " " } }

                try {
                    val response = api.translateGooglePost(
                        tl = targetLang,
                        q = originalText
                    )

                    val jsonArray = response.asJsonArray
                    val sentences = jsonArray.get(0).asJsonArray

                    val translatedTextBuilder = StringBuilder()
                    for (i in 0 until sentences.size()) {
                        val sentenceObj = sentences.get(i)
                        if (sentenceObj.isJsonArray) {
                            val part = sentenceObj.asJsonArray.get(0).asString
                            translatedTextBuilder.append(part)
                        }
                    }

                    val translatedChunk = translatedTextBuilder.toString().split("\n")
                    for (i in chunk.indices) {
                        val trans = translatedChunk.getOrNull(i)?.trim() ?: ""
                        translatedLines.add(trans)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("LyricsManager", "Translation chunk error", e)
                    repeat(chunk.size) { translatedLines.add("") }
                }
            }

            val finalLines = lines.mapIndexed { index, line ->
                val translated = translatedLines.getOrNull(index)?.trim() ?: ""
                if (translated.isNotBlank() && !translated.equals(line.text.trim(), ignoreCase = true)) {
                    line.copy(translation = translated)
                } else {
                    line
                }
            }

            saveToCache(context, track, finalLines)
            return@withContext finalLines

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("LyricsManager", "Translation general error", e)
        }
        return@withContext lines
    }

    private fun findLocalLrcFile(context: Context, track: AudioTrack): String? {
        val path = AudioRepository.getAudioFilePath(context, track.uri) ?: return null
        val audioFile = File(path)
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension

        val candidates = listOf(
            File(parent, "$baseName.ttml"),
            File(parent, "$baseName.TTML"),
            File(parent, "$baseName.lrc"),
            File(parent, "$baseName.LRC"),
            File(parent, "$baseName.txt"),
            File(parent, "${track.title}.ttml"),
            File(parent, "${track.title}.TTML"),
            File(parent, "${track.title}.lrc"),
            File(parent, "${track.title}.LRC"),
            File(parent, "${track.title}.txt")
        )

        for (candidate in candidates) {
            if (candidate.exists() && candidate.canRead() && candidate.length() > 0) {
                return try {
                    candidate.readText(Charsets.UTF_8)
                } catch (_: Exception) {
                    try {
                        candidate.readText(charset("windows-1251"))
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }

        return null
    }

    private fun getEmbeddedLyrics(context: Context, uri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun getCacheKey(track: AudioTrack): String {
        val currentLang = getCurrentAppLanguage()
        return "${track.artist}_${track.title}_${track.duration}_$currentLang"
    }

    private fun getCacheFile(context: Context, cacheKey: String): File {
        val dir = context.getDir("lyrics_persistent_cache", Context.MODE_PRIVATE)
        return File(dir, "lyrics_${cacheKey.hashCode()}.json")
    }

    private fun getCreditFile(context: Context, cacheKey: String): File {
        val dir = context.getDir("lyrics_persistent_cache", Context.MODE_PRIVATE)
        return File(dir, "lyrics_${cacheKey.hashCode()}.credit")
    }

    private fun saveCreditUrlToDisk(context: Context, track: AudioTrack, url: String) {
        try {
            getCreditFile(context, getCacheKey(track)).writeText(url)
        } catch (_: Exception) {}
    }

    private fun saveToCache(context: Context, track: AudioTrack, lines: List<LyricLine>) {
        val key = getCacheKey(track)
        memoryCache.put(key, lines)
        try {
            val json = gson.toJson(lines, lyricListType)
            getCacheFile(context, key).writeText(json)
        } catch (_: Exception) {}
    }

    private fun loadFromCache(context: Context, track: AudioTrack): List<LyricLine>? {
        val key = getCacheKey(track)
        memoryCache.get(key)?.let { return it }

        val creditFile = getCreditFile(context, key)
        if (creditFile.exists()) {
            runCatching { creditFile.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                trackCreditUrlMap[track.id] = it
            }
        }

        val file = getCacheFile(context, key)
        if (file.exists() && file.length() > 0) {
            try {
                val json = file.readText()
                val parsed: List<LyricLine>? = gson.fromJson(json, lyricListType)
                if (!parsed.isNullOrEmpty()) {
                    memoryCache.put(key, parsed)
                    return parsed
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun clearCache(context: Context): Int {
        memoryCache.evictAll()
        trackCreditUrlMap.clear()
        var deletedCount = 0
        try {
            val dir = context.getDir("lyrics_persistent_cache", Context.MODE_PRIVATE)
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("lyrics_")) {
                    if (file.delete()) deletedCount++
                }
            }
        } catch (_: Exception) {}
        return deletedCount
    }
}