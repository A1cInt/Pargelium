package com.alcint.pargelium

import android.net.Uri

enum class SortOption {
    TITLE, ARTIST, DURATION
}

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val albumId: Long,
    val duration: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val source: String = "local", // "local", "jamendo", "audius", "soundcloud", "saavn", "hearthis"
    val coverUrl: String? = null
)

data class AlbumModel(
    val id: Long,
    val title: String,
    val artist: String,
    val tracks: List<AudioTrack>
)