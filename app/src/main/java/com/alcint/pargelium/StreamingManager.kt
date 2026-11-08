package com.alcint.pargelium

import android.content.Context
import android.net.Uri
import com.alcint.pargelium.streaming.InnerTubeClient
import com.alcint.pargelium.streaming.OpenProvidersClient
import com.alcint.pargelium.streaming.StreamProvider
import com.alcint.pargelium.streaming.StreamingDiscoveryFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object StreamingManager {

    private var cachedDiscoveryFeed: StreamingDiscoveryFeed? = null
    private var cachedMoodKey: String? = null

    suspend fun loadDiscoveryFeed(
        moodQuery: String = "Electronic Synthwave French House",
        forceRefresh: Boolean = false
    ): StreamingDiscoveryFeed = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedDiscoveryFeed != null && cachedMoodKey == moodQuery) {
            return@withContext cachedDiscoveryFeed!!
        }

        val ytDeferred = async { runCatching { InnerTubeClient.searchTracks(moodQuery, limit = 16) }.getOrDefault(emptyList()) }
        val scDeferred = async { runCatching { OpenProvidersClient.searchSoundCloud(moodQuery, limit = 16) }.getOrDefault(emptyList()) }
        val audiusDeferred = async { runCatching { OpenProvidersClient.getAudiusTrending(limit = 16) }.getOrDefault(emptyList()) }
        val saavnDeferred = async { runCatching { OpenProvidersClient.getSaavnTrending() }.getOrDefault(emptyList()) }

        val feed = StreamingDiscoveryFeed(
            ytMusicTrending = ytDeferred.await(),
            soundCloudHot = scDeferred.await(),
            audiusUnderground = audiusDeferred.await(),
            globalCharts = saavnDeferred.await()
        )

        if (feed.ytMusicTrending.isNotEmpty() || feed.soundCloudHot.isNotEmpty() || feed.audiusUnderground.isNotEmpty()) {
            cachedDiscoveryFeed = feed
            cachedMoodKey = moodQuery
        }
        feed
    }

    suspend fun searchByProvider(
        query: String,
        provider: StreamProvider
    ): List<AudioTrack> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        when (provider) {
            StreamProvider.YOUTUBE -> InnerTubeClient.searchTracks(query, limit = 30)
            StreamProvider.SOUNDCLOUD -> OpenProvidersClient.searchSoundCloud(query, limit = 25)
            StreamProvider.AUDIUS -> OpenProvidersClient.searchAudius(query, limit = 25)
            StreamProvider.SAAVN -> OpenProvidersClient.searchSaavn(query)
            StreamProvider.HEARTHIS -> OpenProvidersClient.searchHearthis(query, limit = 20)
            StreamProvider.JAMENDO -> OpenProvidersClient.searchJamendo(query, limit = 20)
            StreamProvider.ALL -> searchAll(query)
        }
    }

    suspend fun searchAll(query: String): List<AudioTrack> = withContext(Dispatchers.IO) {
        val deferreds = listOf(
            async { runCatching { InnerTubeClient.searchTracks(query, limit = 14) }.getOrDefault(emptyList()) },
            async { runCatching { OpenProvidersClient.searchSoundCloud(query, limit = 12) }.getOrDefault(emptyList()) },
            async { runCatching { OpenProvidersClient.searchAudius(query, limit = 10) }.getOrDefault(emptyList()) },
            async { runCatching { OpenProvidersClient.searchSaavn(query) }.getOrDefault(emptyList()) },
            async { runCatching { OpenProvidersClient.searchHearthis(query, limit = 8) }.getOrDefault(emptyList()) },
            async { runCatching { OpenProvidersClient.searchJamendo(query, limit = 8) }.getOrDefault(emptyList()) }
        )

        val buckets = deferreds.awaitAll()
        // Умное чередование (Interleaving): сначала лучшие совпадения с YouTube Music, SoundCloud и Audius без хаотичного перемешивания
        val interleaved = mutableListOf<AudioTrack>()
        val maxLen = buckets.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) {
            for (bucket in buckets) {
                if (i < bucket.size) {
                    interleaved.add(bucket[i])
                }
            }
        }
        interleaved.distinctBy { "${it.source}_${it.id}" }
    }

    suspend fun getPlayableUri(context: Context, track: AudioTrack): Uri = withContext(Dispatchers.IO) {
        when (track.source.lowercase()) {
            "youtube" -> InnerTubeClient.resolveStreamUri(track.uri.toString())
            "soundcloud" -> OpenProvidersClient.resolveSoundCloudStream(track.uri.toString())
            "saavn" -> SaavnApi.getStreamUri(track.id)
            else -> track.uri
        }
    }
}