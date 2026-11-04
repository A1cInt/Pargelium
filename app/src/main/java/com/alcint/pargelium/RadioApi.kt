package com.alcint.pargelium

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.UUID
import java.util.concurrent.TimeUnit

data class RadioStation(
    val stationuuid: String = "",
    val name: String = "",
    val url_resolved: String = "",
    val favicon: String? = null,
    val tags: String = "",
    val country: String = "",
    val countrycode: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val votes: Int = 0
)

interface RadioBrowserApi {
    @GET("json/stations/search")
    suspend fun searchByName(
        @Query("name") name: String,
        @Query("limit") limit: Int = 60,
        @Query("hidebroken") hidebroken: Boolean = true,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioStation>

    @GET("json/stations/search")
    suspend fun searchByTag(
        @Query("tag") tag: String,
        @Query("limit") limit: Int = 60,
        @Query("hidebroken") hidebroken: Boolean = true,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioStation>

    @GET("json/stations/topclick")
    suspend fun getTop(
        @Query("limit") limit: Int = 60,
        @Query("hidebroken") hidebroken: Boolean = true
    ): List<RadioStation>
}

object RadioRepository {
    private const val PREFS_FAV_RADIO = "pargelium_favorite_radios"
    private const val KEY_FAV_JSON = "fav_stations_json"
    private val gson = Gson()

    private val mirrorUrls = listOf(
        "https://de1.api.radio-browser.info/",
        "https://fi1.api.radio-browser.info/",
        "https://at1.api.radio-browser.info/"
    )

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "PargeliumMusicPlayer/2.5 (Android; GitHub/AlcInt)")
                .build()
            chain.proceed(request)
        }
        .build()

    private val apis: List<RadioBrowserApi> = mirrorUrls.map { baseUrl ->
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RadioBrowserApi::class.java)
    }

    suspend fun getStations(query: String, isTagSearch: Boolean = false): List<AudioTrack> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()

        for (api in apis) {
            try {
                val rawStations = when {
                    cleanQuery.isEmpty() -> api.getTop(limit = 60)
                    isTagSearch -> api.searchByTag(tag = cleanQuery.lowercase(), limit = 60)
                    else -> {
                        val byName = api.searchByName(name = cleanQuery, limit = 50)
                        if (byName.size < 10) {
                            (byName + api.searchByTag(tag = cleanQuery.lowercase(), limit = 30))
                                .distinctBy { it.stationuuid }
                        } else {
                            byName
                        }
                    }
                }

                val mapped = rawStations
                    .filter { it.url_resolved.startsWith("http", ignoreCase = true) && it.name.isNotBlank() }
                    .distinctBy { it.url_resolved }
                    .map { station -> stationToAudioTrack(station) }

                if (mapped.isNotEmpty() || cleanQuery.isNotEmpty()) {
                    return@withContext mapped
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("RadioRepository", "Mirror failed, trying next: ${e.message}")
            }
        }
        emptyList()
    }

    private fun stationToAudioTrack(station: RadioStation): AudioTrack {
        val trackId = try {
            UUID.fromString(station.stationuuid).mostSignificantBits
        } catch (_: Exception) {
            station.stationuuid.hashCode().toLong()
        }

        val cleanTags = station.tags
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 22 }
            .take(3)
            .joinToString(", ")

        val countryPart = station.countrycode.takeIf { it.isNotBlank() }
            ?: station.country.takeIf { it.isNotBlank() }
            ?: ""

        val subtitle = buildString {
            if (countryPart.isNotEmpty()) append(countryPart.uppercase())
            if (cleanTags.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append(cleanTags)
            }
            if (isEmpty()) append("Live Radio")
        }

        val qualityBadge = buildString {
            if (station.codec.isNotBlank()) append(station.codec.uppercase())
            if (station.bitrate > 0) {
                if (isNotEmpty()) append(" • ")
                append("${station.bitrate} kbps")
            }
            if (isEmpty()) append("LIVE STREAM")
        }

        val validFavicon = station.favicon
            ?.trim()
            ?.takeIf { it.startsWith("http", ignoreCase = true) }

        return AudioTrack(
            id = trackId,
            title = station.name.trim().replace(Regex("\\s+"), " ").ifEmpty { "Unknown Station" },
            artist = subtitle,
            album = qualityBadge,
            uri = Uri.parse(station.url_resolved),
            albumId = 0L,
            duration = -1L,
            trackNumber = station.bitrate,
            discNumber = 1,
            source = "radio",
            coverUrl = validFavicon
        )
    }

    fun getFavoriteStations(context: Context): List<AudioTrack> {
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_FAV_RADIO, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_FAV_JSON, null) ?: return emptyList()
            val type = object : TypeToken<List<SavedRadioDto>>() {}.type
            val list: List<SavedRadioDto> = gson.fromJson(json, type) ?: emptyList()
            list.map { it.toAudioTrack() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun toggleFavoriteStation(context: Context, track: AudioTrack): List<AudioTrack> {
        val current = getFavoriteStations(context).toMutableList()
        val existingIdx = current.indexOfFirst { it.id == track.id || it.uri == track.uri }
        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
        } else {
            current.add(0, track)
        }
        saveFavorites(context, current)
        return current
    }

    private fun saveFavorites(context: Context, list: List<AudioTrack>) {
        try {
            val dtos = list.map { SavedRadioDto.fromAudioTrack(it) }
            val prefs = context.applicationContext.getSharedPreferences(PREFS_FAV_RADIO, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_FAV_JSON, gson.toJson(dtos)).apply()
        } catch (_: Exception) {}
    }

    private data class SavedRadioDto(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val uriStr: String,
        val bitrate: Int,
        val coverUrl: String?
    ) {
        fun toAudioTrack() = AudioTrack(
            id = id,
            title = title,
            artist = artist,
            album = album,
            uri = Uri.parse(uriStr),
            albumId = 0L,
            duration = -1L,
            trackNumber = bitrate,
            discNumber = 1,
            source = "radio",
            coverUrl = coverUrl
        )

        companion object {
            fun fromAudioTrack(t: AudioTrack) = SavedRadioDto(
                id = t.id,
                title = t.title,
                artist = t.artist,
                album = t.album,
                uriStr = t.uri.toString(),
                bitrate = t.trackNumber,
                coverUrl = t.coverUrl
            )
        }
    }
}