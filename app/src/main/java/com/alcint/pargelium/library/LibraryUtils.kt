package com.alcint.pargelium.library

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.alcint.pargelium.AlbumModel
import com.alcint.pargelium.AudioRepository
import com.alcint.pargelium.AudioTrack

@Immutable
data class ArtistModel(
    val name: String,
    val tracks: List<AudioTrack>,
    val albums: List<AlbumModel>,
    val customAvatarUri: String? = null,
    val customBannerUri: String? = null,
    val customLogoUri: String? = null,
    val fallbackCoverUri: Uri? = null
)

@Immutable
data class NavItem(
    val iconRes: Int,
    val labelRes: Int
)

enum class GridDensity(val minCellSizeDp: Int) {
    COMPACT(112),
    COZY(156),
    SPACIOUS(220)
}

private val artistDelimiterRegex = Regex(" feat\\.? | ft\\.? | & | vs\\.? | x |,", RegexOption.IGNORE_CASE)

fun getPrimaryArtist(rawName: String, unknownStr: String): String {
    if (rawName.isBlank() || rawName.equals("<unknown>", ignoreCase = true)) return unknownStr
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

fun resolveArtistCoverUri(artist: ArtistModel): Any? {
    if (!artist.customAvatarUri.isNullOrBlank()) return artist.customAvatarUri
    if (artist.fallbackCoverUri != null) return artist.fallbackCoverUri
    val firstAlbumId = artist.albums.firstOrNull()?.id ?: artist.tracks.firstOrNull()?.albumId
    return if (firstAlbumId != null && firstAlbumId != 0L && firstAlbumId != -1L) {
        AudioRepository.getAlbumArtUri(firstAlbumId)
    } else null
}
