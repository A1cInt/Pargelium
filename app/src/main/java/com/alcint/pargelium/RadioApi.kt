package com.alcint.pargelium

import android.net.Uri
import android.util.Log
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
    val stationuuid: String,
    val name: String,
    val url_resolved: String,
    val favicon: String?,
    val tags: String,
    val country: String
)

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

object RadioRepository {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("https://de1.api.radio-browser.info/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RadioBrowserApi::class.java)

    suspend fun getStations(query: String): List<AudioTrack> = withContext(Dispatchers.IO) {
        try {
            val stations = if (query.isBlank()) api.getTop() else api.search(query)

            stations.map { station ->
                val trackId = try {
                    UUID.fromString(station.stationuuid).mostSignificantBits
                } catch (e: Exception) {
                    station.stationuuid.hashCode().toLong()
                }

                AudioTrack(
                    id = trackId,
                    title = station.name.trim().ifEmpty { "Unknown Station" },
                    artist = station.tags.takeIf { it.isNotBlank() } ?: station.country.takeIf { it.isNotBlank() } ?: "Radio",
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