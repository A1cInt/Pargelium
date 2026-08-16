package com.alcint.pargelium

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.util.concurrent.atomic.AtomicInteger

object AudioRepository {

    fun getAlbumArtUri(albumId: Long): Uri {
        return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
    }

    fun processAlbums(tracks: List<AudioTrack>): List<AlbumModel> {
        return tracks.groupBy { it.albumId }.map { (_, albumTracks) ->
            val first = albumTracks.first()
            val sortedTracks = albumTracks.sortedWith(
                compareBy<AudioTrack> { it.discNumber }.thenBy { it.trackNumber }
            )

            val artistCounts = HashMap<String, Int>()
            var mainArtist = first.artist
            var maxCount = 0
            for (t in albumTracks) {
                val count = (artistCounts[t.artist] ?: 0) + 1
                artistCounts[t.artist] = count
                if (count > maxCount) {
                    maxCount = count
                    mainArtist = t.artist
                }
            }

            AlbumModel(first.albumId, first.album, mainArtist, sortedTracks)
        }.sortedBy { it.title }
    }

    fun findCanvasForTrack(context: Context, trackUri: Uri): Uri? {
        var trackNameClean: String? = null
        var bucketId: String? = null

        try {
            val projection = arrayOf(MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.BUCKET_ID)
            context.contentResolver.query(trackUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val fullName = cursor.getString(0) ?: ""
                    trackNameClean = fullName.substringBeforeLast(".")
                    val bIdx = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_ID)
                    if (bIdx != -1) {
                        bucketId = cursor.getString(bIdx)
                    }
                }
            }
        } catch (e: Exception) { }

        val cleanName = trackNameClean ?: return null
        val bId = bucketId ?: return null

        try {
            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
            val selection = "${MediaStore.Video.Media.BUCKET_ID} = ?"
            val selectionArgs = arrayOf(bId)

            context.contentResolver.query(videoUri, projection, selection, selectionArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val vidFullName = cursor.getString(1) ?: ""
                    val vidNameClean = vidFullName.substringBeforeLast(".")

                    if (vidNameClean.equals(cleanName, ignoreCase = true) ||
                        vidNameClean.contains(cleanName, ignoreCase = true) ||
                        cleanName.contains(vidNameClean, ignoreCase = true)) {
                        return ContentUris.withAppendedId(videoUri, id)
                    }
                }
            }
        } catch (e: Exception) { }
        return null
    }

    fun getTrackMetadata(context: Context, uri: Uri): String {
        val retriever = MediaMetadataRetriever()
        var mime = ""
        var bitrateRaw = 0
        var sampleRate = 0
        var bitsPerSample = 0

        try {
            retriever.setDataSource(context, uri)
            mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            bitrateRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
                bitsPerSample = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }

        if (sampleRate == 0 || bitsPerSample == 0) {
            var extractor: MediaExtractor? = null
            try {
                extractor = MediaExtractor()
                extractor.setDataSource(context, uri, null)
                if (extractor.trackCount > 0) {
                    val format = extractor.getTrackFormat(0)

                    if (mime.isEmpty() && format.containsKey(MediaFormat.KEY_MIME)) {
                        mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    }
                    if (sampleRate == 0 && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (bitsPerSample == 0 && format.containsKey("bits-per-sample")) {
                        bitsPerSample = format.getInteger("bits-per-sample")
                    } else if (bitsPerSample == 0 && format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        val pcm = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        bitsPerSample = when (pcm) {
                            android.media.AudioFormat.ENCODING_PCM_8BIT -> 8
                            android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
                            android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                            android.media.AudioFormat.ENCODING_PCM_32BIT,
                            android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
                            else -> 0
                        }
                    }
                }
            } catch (e: Exception) {
            } finally {
                extractor?.release()
            }
        }

        val formatName = when {
            mime.contains("flac", true) -> "FLAC"
            mime.contains("alac", true) -> "ALAC"
            mime.contains("wav", true) || mime.contains("x-wav", true) -> "WAV"
            mime.contains("mpeg", true) -> "MP3"
            mime.contains("mp4", true) || mime.contains("m4a", true) || mime.contains("aac", true) -> "AAC"
            mime.contains("ogg", true) -> "OGG"
            mime.contains("opus", true) -> "OPUS"
            mime.contains("/", true) -> mime.substringAfter("/").uppercase()
            else -> "AUDIO"
        }

        val parts = mutableListOf<String>()
        parts.add(formatName)

        if (bitsPerSample > 0) {
            parts.add("${bitsPerSample}-BIT")
        }
        if (sampleRate > 0) {
            val khz = sampleRate / 1000f
            val khzStr = if (khz % 1.0f == 0.0f) "${khz.toInt()} kHz" else "$khz kHz"
            parts.add(khzStr)
        }

        if (bitrateRaw > 0) {
            val kbps = bitrateRaw / 1000
            parts.add("$kbps kbps")
        }

        val result = parts.joinToString(" • ")
        return if (result.isNotBlank()) result else "UNKNOWN FORMAT"
    }

    fun forceScan(context: Context, onComplete: () -> Unit) {
        val pathsToScan = mutableListOf<String>()
        val extensions = setOf("mp3", "wav", "flac", "m4a", "mp4")

        val folders = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        )

        try {
            folders.forEach { folder ->
                if (folder.exists() && folder.isDirectory) {
                    folder.walkTopDown()
                        .onFail { _, _ -> }
                        .forEach { file ->
                            if (file.isFile && extensions.contains(file.extension.lowercase())) {
                                pathsToScan.add(file.absolutePath)
                            }
                        }
                }
            }
        } catch (e: Exception) { }

        if (pathsToScan.isNotEmpty()) {
            val count = AtomicInteger(0)
            val total = pathsToScan.size
            MediaScannerConnection.scanFile(context, pathsToScan.toTypedArray(), null) { _, _ ->
                if (count.incrementAndGet() == total) {
                    onComplete()
                }
            }
        } else {
            onComplete()
        }
    }

    fun getAudioTracks(context: Context): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TRACK,
            "is_music"
        )
        val discColumn = "disc_number"
        val finalProjection = try { projection + discColumn } catch (e: Exception) { projection }
        val selection = "${MediaStore.Audio.Media.DURATION} >= 15000"

        try {
            context.contentResolver.query(collection, finalProjection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val discColIdx = cursor.getColumnIndex(discColumn)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    var title = cursor.getString(titleCol)
                    val artist = cursor.getString(artistCol) ?: "<Unknown>"
                    val album = cursor.getString(albumCol) ?: "<Unknown Album>"
                    val albumId = cursor.getLong(albumIdCol)
                    val duration = cursor.getLong(durCol)
                    val displayName = cursor.getString(nameCol) ?: ""
                    val trackRaw = cursor.getInt(trackCol)
                    val trackNum = if (trackRaw >= 1000) trackRaw % 1000 else trackRaw
                    val discNum = if (discColIdx != -1) cursor.getInt(discColIdx) else 1

                    if (title.isNullOrEmpty()) {
                        title = if (displayName.contains(".")) displayName.substringBeforeLast(".") else displayName
                    }
                    if (title.isEmpty()) title = "Unknown Title"

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    tracks.add(AudioTrack(id, title, artist, album, contentUri, albumId, duration, trackNum, if (discNum < 1) 1 else discNum))
                }
            }
        } catch (e: Exception) { }
        return tracks
    }
}