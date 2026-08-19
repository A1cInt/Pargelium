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
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null
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

    @GET
    suspend fun translateGoogle(@Url url: String): JsonElement
}

object LyricsManager {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("http://localhost/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LyricsNetworkApi::class.java)

    private val gson = Gson()
    private val lrcRegex = Regex("\\[(\\d{2,}):(\\d{2})(?:\\.(\\d{2,3}))?](.*)")

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
            val cachedLyrics = loadFromCache(context, track)
            if (!cachedLyrics.isNullOrEmpty()) {
                return@withContext cachedLyrics
            }

            val localLrcContent = findLocalLrcFile(context, track.uri)
            if (!localLrcContent.isNullOrBlank()) {
                val parsed = parseLrcOrPlain(localLrcContent, track.duration)
                if (parsed.isNotEmpty()) {
                    saveToCache(context, track, parsed)
                    return@withContext parsed
                }
            }

            val embeddedLyrics = getEmbeddedLyrics(context, track.uri)
            if (!embeddedLyrics.isNullOrBlank()) {
                val parsed = parseLrcOrPlain(embeddedLyrics, track.duration)
                if (parsed.isNotEmpty()) {
                    saveToCache(context, track, parsed)
                    return@withContext parsed
                }
            }

            val validMetadata = track.artist.isNotBlank() && track.title.isNotBlank() && !track.artist.contains("Unknown", true)
            if (!validMetadata) return@withContext emptyList()

            val cleanArtist = cleanMetadata(track.artist)
            val cleanTitle = cleanMetadata(track.title)

            var rawLyrics: String? = null

            try {
                val response = api.getLrcLib(cleanArtist, cleanTitle, track.album.takeIf { it.isNotBlank() }, (track.duration / 1000).toInt())
                rawLyrics = response.syncedLyrics ?: response.plainLyrics
            } catch (e: Exception) {
                try {
                    val searchResponse = api.searchLrcLib("$cleanArtist $cleanTitle")
                    if (searchResponse.isNotEmpty()) {
                        rawLyrics = searchResponse[0].syncedLyrics ?: searchResponse[0].plainLyrics
                    }
                } catch (ex: Exception) {
                    Log.e("LyricsManager", "LRCLIB error: ${ex.message}")
                }
            }

            if (rawLyrics.isNullOrBlank()) {
                try {
                    val response = api.getMusixmatch(cleanArtist, cleanTitle)
                    rawLyrics = response.message?.body?.lyrics?.lyrics_body
                } catch (e: Exception) {
                    Log.e("LyricsManager", "Musixmatch error: ${e.message}")
                }
            }

            if (rawLyrics.isNullOrBlank()) {
                try {
                    val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8").replace("+", "%20")
                    val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8").replace("+", "%20")
                    val ovhUrl = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"

                    val response = api.getOvh(ovhUrl)
                    rawLyrics = response.lyrics
                } catch (e: Exception) {
                    Log.e("LyricsManager", "OVH error: ${e.message}")
                }
            }

            if (!rawLyrics.isNullOrBlank()) {
                val parsed = parseLrcOrPlain(rawLyrics, track.duration)
                if (parsed.isNotEmpty()) {
                    saveToCache(context, track, parsed)
                    return@withContext parsed
                }
            }

            return@withContext emptyList()
        }
    }

    private fun parseLrcOrPlain(lrcContent: String, trackDuration: Long): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        var isSynced = false

        lrcContent.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val match = lrcRegex.find(line)
            if (match != null) {
                isSynced = true
                val min = match.groupValues[1]
                val sec = match.groupValues[2]
                val msStr = match.groupValues[3]
                val text = match.groupValues[4]

                val ms = if (msStr.isEmpty()) 0L else if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                val timestamp = (min.toLong() * 60 * 1000) + (sec.toLong() * 1000) + ms
                lines.add(LyricLine(timestamp, text.trim()))
            }
        }

        if (isSynced && lines.isNotEmpty()) return lines.sortedBy { it.timeMs }

        val plainLines = lrcContent.lines().filter { it.isNotBlank() }
        if (plainLines.isEmpty()) return emptyList()

        val safeDuration = if (trackDuration > 0) trackDuration else 180_000L
        val timePerLine = safeDuration / plainLines.size

        return plainLines.mapIndexed { index, text ->
            LyricLine(index * timePerLine, text.trim())
        }
    }

    suspend fun translateLyrics(context: Context, track: AudioTrack, lines: List<LyricLine>): List<LyricLine> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext lines

        try {
            val targetLang = getCurrentAppLanguage()
            val chunkedLines = lines.chunked(25)
            val translatedLines = mutableListOf<String>()

            for (chunk in chunkedLines) {
                val originalText = chunk.joinToString("\n") { it.text }

                try {
                    val encodedQuery = URLEncoder.encode(originalText, "UTF-8")
                    val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encodedQuery"

                    // Исправлен вызов на translateGoogle
                    val response = api.translateGoogle(url)
                    val sentences = response.asJsonArray.get(0).asJsonArray

                    val translatedTextBuilder = StringBuilder()
                    for (i in 0 until sentences.size()) {
                        translatedTextBuilder.append(sentences.get(i).asJsonArray.get(0).asString)
                    }

                    val translatedChunk = translatedTextBuilder.toString().split("\n")
                    translatedLines.addAll(translatedChunk)

                } catch (e: Exception) {
                    Log.e("LyricsManager", "Translation chunk error", e)
                    translatedLines.addAll(List(chunk.size) { "" })
                }
            }

            val finalLines = lines.mapIndexed { index, line ->
                val translated = translatedLines.getOrNull(index)?.trim() ?: ""
                if (translated.isNotEmpty() && translated.lowercase() != line.text.trim().lowercase()) {
                    line.copy(translation = translated)
                } else {
                    line
                }
            }

            saveToCache(context, track, finalLines)
            return@withContext finalLines

        } catch (e: Exception) {
            Log.e("LyricsManager", "Google Translation error: ${e.message}")
        }
        return@withContext lines
    }

    private fun findLocalLrcFile(context: Context, uri: Uri): String? {
        var realPath: String? = null

        if (uri.scheme == "content") {
            try {
                val projection = arrayOf(MediaStore.Audio.Media.DATA)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        realPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    }
                }
            } catch (e: Exception) {}
        } else if (uri.scheme == "file") {
            realPath = uri.path
        }

        var finalPath = realPath ?: uri.path ?: return null

        try {
            if (finalPath.startsWith("/document/raw:")) {
                finalPath = finalPath.replaceFirst("/document/raw:", "")
            }
            finalPath = URLDecoder.decode(finalPath, "UTF-8")
        } catch (e: Exception) {}

        try {
            val pathNoExt = finalPath.substringBeforeLast(".")
            val possibleExtensions = listOf(".lrc", ".LRC", ".txt")

            for (ext in possibleExtensions) {
                val lrcFile = File("$pathNoExt$ext")
                if (lrcFile.exists() && lrcFile.canRead() && lrcFile.length() > 0) {
                    return lrcFile.readText()
                }
            }
        } catch (e: Exception) {}

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
            } catch (e: Exception) {}
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
        } catch (e: Exception) {
            Log.e("LyricsManager", "Failed to save JSON cache", e)
        }
    }

    private fun loadFromCache(context: Context, track: AudioTrack): List<LyricLine>? {
        val file = getCacheFile(context, track)
        if (file.exists() && file.length() > 0) {
            try {
                val json = file.readText()
                val type = object : TypeToken<List<LyricLine>>() {}.type
                return gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e("LyricsManager", "Failed to parse JSON cache", e)
            }
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
        } catch (e: Exception) {}
        return deletedCount
    }
}