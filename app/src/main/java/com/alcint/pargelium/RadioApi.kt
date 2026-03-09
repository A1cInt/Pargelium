package com.alcint.pargelium

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Модель данных для радио
data class RadioStation(
    val stationuuid: String,
    val name: String,
    val url_resolved: String,
    val favicon: String?,
    val tags: String,
    val country: String
)

// API интерфейс
interface RadioBrowserApi {
    @GET("json/stations/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("limit") limit: Int = 40,
        @Query("hidebroken") hidebroken: Boolean = true
    ): List<RadioStation>

    @GET("json/stations/topclick")
    suspend fun getTop(@Query("limit") limit: Int = 40): List<RadioStation>
}

// Репозиторий
object RadioRepository {
    private val api = Retrofit.Builder()
        .baseUrl("https://de1.api.radio-browser.info/") // Стабильный немецкий сервер. Потом добавлю больше
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RadioBrowserApi::class.java)

    suspend fun getStations(query: String): List<AudioTrack> {
        return withContext(Dispatchers.IO) {
            try {
                val stations = if (query.isBlank()) api.getTop() else api.search(query)

                stations.map { station ->
                    // Превращаем станцию в понятный плееру AudioTrack
                    AudioTrack(
                        id = station.stationuuid.hashCode().toLong(),
                        title = station.name.trim(),
                        artist = if (station.tags.isNotEmpty()) station.tags else station.country,
                        album = "Radio Stream",
                        uri = Uri.parse(station.url_resolved),
                        albumId = 0L,
                        duration = -1L,
                        trackNumber = 0,
                        discNumber = 1
                    )
                }
            } catch (e: Exception) {
                Log.e("Radio", "Error: ${e.message}")
                emptyList()
            }
        }
    }
}