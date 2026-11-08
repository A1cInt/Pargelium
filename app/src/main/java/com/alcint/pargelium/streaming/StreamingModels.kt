package com.alcint.pargelium.streaming

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.R

enum class StreamProvider(
    val sourceKey: String,
    val titleRes: Int,
    val brandColor: Color
) {
    ALL("all", R.string.stream_provider_all, Color(0xFFD0BCFF)),
    YOUTUBE("youtube", R.string.stream_provider_yt_music, Color(0xFFFF0033)),
    SOUNDCLOUD("soundcloud", R.string.stream_provider_soundcloud, Color(0xFFFF5500)),
    AUDIUS("audius", R.string.stream_provider_audius, Color(0xFFCC0FE0)),
    SAAVN("saavn", R.string.stream_provider_saavn, Color(0xFF2BC5B4)),
    HEARTHIS("hearthis", R.string.stream_provider_hearthis, Color(0xFF00E676)),
    JAMENDO("jamendo", R.string.stream_provider_jamendo, Color(0xFFFFB300));

    companion object {
        fun fromSource(source: String): StreamProvider {
            return entries.firstOrNull { it.sourceKey.equals(source, ignoreCase = true) } ?: ALL
        }
    }
}

@Immutable
data class StreamMoodChip(
    val id: String,
    val query: String,
    val titleRes: Int
)

@Immutable
data class StreamingDiscoveryFeed(
    val ytMusicTrending: List<AudioTrack> = emptyList(),
    val soundCloudHot: List<AudioTrack> = emptyList(),
    val audiusUnderground: List<AudioTrack> = emptyList(),
    val globalCharts: List<AudioTrack> = emptyList()
)