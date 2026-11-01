package com.alcint.pargelium.library

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.alcint.pargelium.AlbumModel
import com.alcint.pargelium.AudioTrack

data class ArtistModel(val name: String, val tracks: List<AudioTrack>, val albums: List<AlbumModel>)

data class NavItem(
    val iconRes: Int,
    val labelRes: Int
)

private val artistDelimiterRegex = Regex(" feat\\.? | ft\\.? | & | vs\\.? | x |,")

fun getPrimaryArtist(rawName: String, unknownStr: String): String {
    if (rawName.isBlank() || rawName.lowercase() == "<unknown>") return unknownStr
    return rawName.split(artistDelimiterRegex, limit = 2).first().trim()
}

fun createMediaItem(audioTrack: AudioTrack): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(audioTrack.title)
        .setArtist(audioTrack.artist)
        .setAlbumTitle(audioTrack.album)
        .build()

    return MediaItem.Builder()
        .setUri(audioTrack.uri)
        .setMediaId(audioTrack.id.toString())
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(audioTrack.uri).build())
        .setMediaMetadata(metadata)
        .build()
}