package com.alcint.pargelium

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null
)

data class LrcLibResponse(
    @SerializedName("syncedLyrics") val syncedLyrics: String?,
    @SerializedName("plainLyrics") val plainLyrics: String?
)

interface LrcLibApi {
    @GET("get")
    suspend fun getLyrics(
        @Query("artist_name") artist: String,
        @Query("track_name") track: String,
        @Query("album_name") album: String?,
        @Query("duration") duration: Int
    ): LrcLibResponse
}

object LyricsManager {
    private val api = Retrofit.Builder()
        .baseUrl("https://lrclib.net/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LrcLibApi::class.java)

    private val gson = Gson()

    private fun getCurrentAppLanguage(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        return if (!appLocales.isEmpty) {
            appLocales[0]?.language ?: Locale.getDefault().language
        } else {
            Locale.getDefault().language
        }
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

            var rawLyrics: String? = null
            try {
                if (track.artist.isNotBlank() && track.title.isNotBlank() && !track.artist.contains("Unknown", true)) {
                    val response = api.getLyrics(track.artist, track.title, track.album.takeIf { it.isNotBlank() }, (track.duration / 1000).toInt())
                    rawLyrics = response.syncedLyrics ?: response.plainLyrics
                }
            } catch (e: Exception) { Log.e("LyricsManager", "LRCLIB error: ${e.message}") }

            if (rawLyrics.isNullOrBlank() && track.artist.isNotBlank() && track.title.isNotBlank()) {
                try {
                    val ovhUrl = URL("https://api.lyrics.ovh/v1/${URLEncoder.encode(track.artist, "UTF-8")}/${URLEncoder.encode(track.title, "UTF-8")}")
                    val conn = ovhUrl.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 4000
                    if (conn.responseCode == 200) {
                        val json = JSONObject(conn.inputStream.bufferedReader().readText())
                        rawLyrics = json.optString("lyrics", "")
                    }
                } catch (e: Exception) { Log.e("LyricsManager", "OVH fallback error: ${e.message}") }
            }

            if (!rawLyrics.isNullOrBlank()) {
                val parsed = parseLrcOrPlain(rawLyrics, track.duration)
                saveToCache(context, track, parsed)
                return@withContext parsed
            }

            return@withContext emptyList()
        }
    }

    private fun parseLrcOrPlain(lrcContent: String, trackDuration: Long): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)")
        var isSynced = false

        lrcContent.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            val match = regex.find(line)
            if (match != null) {
                isSynced = true
                val (min, sec, msStr, text) = match.destructured
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
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

            val chunkedLines = lines.chunked(15)
            val translatedLines = mutableListOf<String>()

            for (chunk in chunkedLines) {
                val originalText = chunk.joinToString(" || ") { it.text }
                val encodedQuery = URLEncoder.encode(originalText, "UTF-8").replace("+", "%20")

                val url = URL("https://lingva.ml/api/v1/auto/$targetLang/$encodedQuery")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Pargelium-FOSS-Player")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val translatedText = json.optString("translation", "").replace("+", " ")
                    translatedLines.addAll(translatedText.split(" || ", " | | ", "||"))
                } else {
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
            Log.e("LyricsManager", "Lingva Translation error: ${e.message}")
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
                        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                        realPath = cursor.getString(columnIndex)
                    }
                }
            } catch (e: Exception) {}
        } else if (uri.scheme == "file") { realPath = uri.path }

        val finalPath = realPath ?: uri.path ?: return null
        try {
            val pathNoExt = finalPath.substringBeforeLast(".")
            val lrcFile = File("$pathNoExt.lrc")
            if (lrcFile.exists() && lrcFile.canRead() && lrcFile.length() > 0) return lrcFile.readText()
        } catch (e: Exception) {}
        return null
    }

    private fun getEmbeddedLyrics(context: Context, uri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
        } catch (e: Exception) { null } finally { try { retriever.release() } catch (_: Exception) {} }
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
        } catch (e: Exception) { Log.e("LyricsManager", "Failed to save JSON cache", e) }
    }

    private fun loadFromCache(context: Context, track: AudioTrack): List<LyricLine>? {
        val file = getCacheFile(context, track)
        if (file.exists() && file.length() > 0) {
            try {
                val json = file.readText()
                val type = object : TypeToken<List<LyricLine>>() {}.type
                return gson.fromJson(json, type)
            } catch (e: Exception) { Log.e("LyricsManager", "Failed to parse JSON cache", e) }
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