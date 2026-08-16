package com.alcint.pargelium

import android.net.Uri
import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class SaavnTrack(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Int,
    val coverUrl: String,
    val streamUrl: String,
    val longId: Long = java.util.UUID.nameUUIDFromBytes(id.toByteArray()).mostSignificantBits
)

object SaavnApi {
    val trackCache = LruCache<Long, SaavnTrack>(400)

    private const val BASE_URL = "https://www.jiosaavn.com/api.php?_format=json&_marker=0&api_version=4&ctx=android"
    private val secretKey = SecretKeySpec("38346591".toByteArray(), "DES")

    suspend fun getTopTracks(): List<SaavnTrack> = withContext(Dispatchers.IO) {
        searchTrack("Hits")
    }

    suspend fun searchTrack(query: String): List<SaavnTrack> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("$BASE_URL&__call=search.getResults&q=$encodedQuery&n=30&p=1")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; Pixel 5)")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseSaavnResponse(response)
            }
        } catch (e: Exception) {
            android.util.Log.e("SaavnApi", "Ошибка поиска JioSaavn", e)
        } finally {
            conn?.disconnect()
        }
        throw Exception("Не удалось загрузить треки. Проверьте подключение.")
    }

    suspend fun getStreamUri(trackId: Long): Uri = withContext(Dispatchers.IO) {
        val track = trackCache.get(trackId) ?: throw Exception("Трек не найден в кэше")

        if (track.streamUrl.isNotEmpty()) {
            return@withContext Uri.parse(track.streamUrl)
        } else {
            throw Exception("Прямая ссылка на аудио отсутствует.")
        }
    }

    private fun parseSaavnResponse(rawResponse: String): List<SaavnTrack> {
        val result = mutableListOf<SaavnTrack>()
        try {
            val startIndex = rawResponse.indexOf("{")
            val endIndex = rawResponse.lastIndexOf("}")
            if (startIndex == -1 || endIndex == -1) return emptyList()

            val cleanJson = rawResponse.substring(startIndex, endIndex + 1)
            val root = JSONObject(cleanJson)

            var resultsArray = root.optJSONArray("results")
            if (resultsArray == null) {
                resultsArray = root.optJSONObject("songs")?.optJSONArray("data")
            }
            if (resultsArray == null) return emptyList()

            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)

            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.optJSONObject(i) ?: continue

                val id = item.optString("id", "")
                if (id.isEmpty()) continue

                val title = item.optString("title", "Unknown").replace("&quot;", "\"").replace("&amp;", "&")
                val artist = item.optString("subtitle", "Unknown").replace("&quot;", "\"").replace("&amp;", "&")

                var coverUrl = item.optString("image", "")
                coverUrl = coverUrl.replace("150x150", "500x500").replace("50x50", "500x500")

                val moreInfo = item.optJSONObject("more_info")
                val duration = moreInfo?.optInt("duration", 0) ?: item.optInt("duration", 0)

                var encryptedUrl = item.optString("encrypted_media_url", "")
                if (encryptedUrl.isEmpty() && moreInfo != null) {
                    encryptedUrl = moreInfo.optString("encrypted_media_url", "")
                }

                if (encryptedUrl.isNotEmpty()) {
                    val streamUrl = decryptMediaUrl(encryptedUrl, cipher)
                    if (streamUrl.isNotEmpty()) {
                        val track = SaavnTrack(id, title, artist, duration, coverUrl, streamUrl)
                        trackCache.put(track.longId, track)
                        result.add(track)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SaavnApi", "Ошибка парсинга JSON", e)
        }
        return result
    }

    private fun decryptMediaUrl(encryptedUrl: String, cipher: Cipher): String {
        return try {
            val decodedBytes = Base64.decode(encryptedUrl, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            var url = String(decryptedBytes, Charsets.UTF_8)

            url = url.replace("_96.mp4", "_320.mp4")
            url = url.replace("_160.mp4", "_320.mp4")
            url = url.replace("http://", "https://")

            url
        } catch (e: Exception) {
            android.util.Log.e("SaavnApi", "Ошибка расшифровки потока", e)
            ""
        }
    }
}