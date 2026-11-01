package com.alcint.pargelium

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                .build()
            chain.proceed(request)
        }
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("http://localhost/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LyricsNetworkApi::class.java)

    private val gson = Gson()
    private val lrcRegex = Regex("\\[(\\d{2,}):(\\d{2})(?:\\.(\\d{2,3}))?](.*)")
    private val enhancedRegex = Regex("<(\\d{2,}):(\\d{2})(?:\\.(\\d{2,3}))?>([^<]*)")

    private fun getCurrentAppLanguage(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        return if (!appLocales.isEmpty) {
            appLocales[0]?.language ?: Locale.getDefault().language
        } else {
            Locale.getDefault().language
        }
    }

    private fun cleanMetadata(text: String): String {
        return text.replace(Regex("(?i)\\s*\\(?(feat\\.|ft\\.|remaster|official|lyric|video|audio|radio).*?\\)?"), "")
            .replace(Regex("(?i)\\s*\\[(feat\\.|ft\\.|remaster|official|lyric|video|audio|radio).*?]"), "")
            .replace(Regex("\\s*-.*?(Remaster|Edit|Mix|Version).*"), "")
            .trim()
    }

    suspend fun getLyrics(context: Context, track: AudioTrack): List<LyricLine> {
        return withContext(Dispatchers.IO) {
            // 1. Приоритет отдаем локальным файлам (.ttml, .lrc) и вшитым тегам (LYRICS-TTML, TTML, USLT)
            val localLrc = AudioRepository.findLrcContentForTrack(context, track)
                ?: findLocalLrcFile(context, track)

            if (!localLrc.isNullOrBlank()) {
                val parsed = AudioRepository.parseLyrics(localLrc)
                if (parsed.isNotEmpty()) {
                    saveToCache(context, track, parsed)
                    return@withContext parsed
                }
            }

            // 2. Проверяем кэш только если локальных текстов рядом нет
            val cachedLyrics = loadFromCache(context, track)
            if (!cachedLyrics.isNullOrEmpty()) {
                return@withContext cachedLyrics
            }

            // 3. Онлайн поиск (пословный)
            val betterLyrics = fetchBetterLyricsOnline(track)
            if (betterLyrics.isNotEmpty()) {
                saveToCache(context, track, betterLyrics)
                return@withContext betterLyrics
            }

            // 4. Fallback онлайн поиск (LRC-LIB / Musixmatch / OVH)
            val fallbackOnline = fetchFallbackOnlineLyrics(track)
            if (fallbackOnline.isNotEmpty()) {
                saveToCache(context, track, fallbackOnline)
                return@withContext fallbackOnline
            }

            // 5. Попытка прочесть медиа-ритривер (если остался незамеченным)
            val embeddedLyrics = getEmbeddedLyrics(context, track.uri)
            if (!embeddedLyrics.isNullOrBlank()) {
                val parsed = AudioRepository.parseLyrics(embeddedLyrics)
                if (parsed.isNotEmpty()) {
                    saveToCache(context, track, parsed)
                    return@withContext parsed
                }
            }

            return@withContext emptyList()
        }
    }

    suspend fun searchLyricsOnline(context: Context, track: AudioTrack): List<LyricLine> {
        return withContext(Dispatchers.IO) {
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
                val result = mutableListOf<LyricLine>()
                for (line in betterResp.lines) {
                    val lineStart = line.offset ?: 0L
                    val text = line.text ?: ""
                    val wordsList = mutableListOf<SyllableWord>()

                    if (!line.words.isNullOrEmpty()) {
                        for (w in line.words) {
                            val wStart = lineStart + (w.offset ?: 0L)
                            val wEnd = wStart + (w.duration ?: 300L)
                            val wText = w.text ?: ""
                            if (wText.isNotEmpty()) {
                                wordsList.add(SyllableWord(wText, wStart, wEnd))
                            }
                        }
                    }
                    result.add(LyricLine(lineStart, text.trim(), null, wordsList))
                }
                if (result.isNotEmpty()) {
                    return result.sortedBy { it.timeMs }
                }
            } else if (!betterResp.lyrics.isNullOrBlank()) {
                val parsed = AudioRepository.parseLyrics(betterResp.lyrics)
                if (parsed.isNotEmpty()) return parsed
            }
        } catch (_: Exception) {}

        try {
            val lpResp = api.getLyricsPlus(cleanTitle, cleanArtist, durationSec)
            if (!lpResp.lines.isNullOrEmpty()) {
                val result = mutableListOf<LyricLine>()
                for (line in lpResp.lines) {
                    val lineStart = line.time ?: 0L
                    val wordsList = mutableListOf<SyllableWord>()
                    var fullText = ""

                    if (!line.words.isNullOrEmpty()) {
                        for (w in line.words) {
                            val wStart = w.time ?: lineStart
                            val wEnd = wStart + (w.duration ?: 300L)
                            val wText = w.string ?: ""
                            if (wText.isNotEmpty()) {
                                wordsList.add(SyllableWord(wText, wStart, wEnd))
                                fullText += wText
                            }
                        }
                    }
                    if (fullText.isNotBlank()) {
                        result.add(LyricLine(lineStart, fullText.trim(), null, wordsList))
                    }
                }
                if (result.isNotEmpty()) {
                    return result.sortedBy { it.timeMs }
                }
            }
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
        } catch (e: Exception) {
            try {
                val searchResponse = api.searchLrcLib("$cleanArtist $cleanTitle")
                if (searchResponse.isNotEmpty()) {
                    rawLyrics = searchResponse[0].syncedLyrics ?: searchResponse[0].plainLyrics
                }
            } catch (ex: Exception) {}
        }

        if (rawLyrics.isNullOrBlank()) {
            try {
                val response = api.getMusixmatch(cleanArtist, cleanTitle)
                rawLyrics = response.message?.body?.lyrics?.lyrics_body
            } catch (e: Exception) {}
        }

        if (rawLyrics.isNullOrBlank()) {
            try {
                val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8").replace("+", "%20")
                val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8").replace("+", "%20")
                val ovhUrl = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"

                val response = api.getOvh(ovhUrl)
                rawLyrics = response.lyrics
            } catch (e: Exception) {}
        }

        if (!rawLyrics.isNullOrBlank()) {
            return AudioRepository.parseLyrics(rawLyrics)
        }

        return emptyList()
    }

    suspend fun translateLyrics(context: Context, track: AudioTrack, lines: List<LyricLine>): List<LyricLine> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext lines

        try {
            val targetLang = getCurrentAppLanguage()
            val chunkedLines = lines.chunked(25)
            val translatedLines = mutableListOf<String>()

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
                } catch (e: Exception) {
                    Log.e("LyricsManager", "Translation chunk error", e)
                    translatedLines.addAll(List(chunk.size) { "" })
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

        } catch (e: Exception) {
            Log.e("LyricsManager", "Translation general error", e)
        }
        return@withContext lines
    }

    private fun findLocalLrcFile(context: Context, track: AudioTrack): String? {
        val path = AudioRepository.getAudioFilePath(context, track.uri) ?: return null
        val audioFile = File(path)
        val parent = audioFile.parentFile ?: return null

        val candidates = listOf(
            File(parent, "${audioFile.nameWithoutExtension}.ttml"),
            File(parent, "${audioFile.nameWithoutExtension}.TTML"),
            File(parent, "${audioFile.nameWithoutExtension}.lrc"),
            File(parent, "${audioFile.nameWithoutExtension}.LRC"),
            File(parent, "${audioFile.nameWithoutExtension}.txt"),
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
                } catch (e: Exception) {
                    try { candidate.readText(charset("windows-1251")) } catch (e2: Exception) { null }
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
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun getCacheFile(context: Context, track: AudioTrack): File {
        val dir = context.getDir("lyrics_persistent_cache", Context.MODE_PRIVATE)
        val currentLang = getCurrentAppLanguage()
        val safeHash = "${track.artist}_${track.title}_$currentLang".hashCode()
        return File(dir, "lyrics_$safeHash.json")
    }

    private fun saveToCache(context: Context, track: AudioTrack, lines: List<LyricLine>) {
        try {
            val json = gson.toJson(lines)
            getCacheFile(context, track).writeText(json)
        } catch (_: Exception) {}
    }

    private fun loadFromCache(context: Context, track: AudioTrack): List<LyricLine>? {
        val file = getCacheFile(context, track)
        if (file.exists() && file.length() > 0) {
            try {
                val json = file.readText()
                val type = object : TypeToken<List<LyricLine>>() {}.type
                return gson.fromJson(json, type)
            } catch (_: Exception) {}
        }
        return null
    }

    fun clearCache(context: Context): Int {
        var deletedCount = 0
        try {
            val dir = context.getDir("lyrics_persistent_cache", Context.MODE_PRIVATE)
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("lyrics_") && (file.name.endsWith(".json") || file.name.endsWith(".lrc"))) {
                    if (file.delete()) deletedCount++
                }
            }
        } catch (_: Exception) {}
        return deletedCount
    }
}