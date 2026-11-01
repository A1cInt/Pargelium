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
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

object AudioRepository {

    private var cachedTracks: List<AudioTrack>? = null
    private var cachedAlbums: List<AlbumModel>? = null

    fun clearCache() {
        cachedTracks = null
        cachedAlbums = null
    }

    fun getAlbumArtUri(albumId: Long): Uri {
        return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
    }

    fun processAlbums(tracks: List<AudioTrack>): List<AlbumModel> {
        if (cachedAlbums != null) return cachedAlbums!!

        val albums = tracks.groupBy { it.albumId }.map { (_, albumTracks) ->
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

        cachedAlbums = albums
        return albums
    }

    fun getAudioFilePath(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val colIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                if (colIdx != -1 && cursor.moveToFirst()) {
                    cursor.getString(colIdx)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun findLrcFileForTrack(context: Context, track: AudioTrack): File? {
        val filePath = getAudioFilePath(context, track.uri) ?: return null
        val audioFile = File(filePath)
        val parentDir = audioFile.parentFile ?: return null

        val nameWithoutExt = audioFile.nameWithoutExtension
        val extensions = listOf("ttml", "lrc")

        for (ext in extensions) {
            val sameName = File(parentDir, "$nameWithoutExt.$ext")
            if (sameName.exists() && sameName.canRead()) return sameName
        }

        for (ext in extensions) {
            val titleFile = File(parentDir, "${track.title}.$ext")
            if (titleFile.exists() && titleFile.canRead()) return titleFile
        }

        try {
            val matchedFile = parentDir.listFiles { file ->
                file.isFile && (file.extension.equals("ttml", ignoreCase = true) || file.extension.equals("lrc", ignoreCase = true)) &&
                        (file.nameWithoutExtension.equals(nameWithoutExt, ignoreCase = true) ||
                                file.nameWithoutExtension.equals(track.title, ignoreCase = true))
            }?.firstOrNull()

            if (matchedFile != null && matchedFile.canRead()) {
                return matchedFile
            }
        } catch (e: Exception) { }

        return null
    }

    fun findEmbeddedLyrics(context: Context, trackUri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, trackUri)
            val filePath = getAudioFilePath(context, trackUri)
            if (filePath != null) {
                readMetadataTagsFromFile(File(filePath))
            } else null
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    private fun readMetadataTagsFromFile(file: File): String? {
        if (!file.exists() || !file.canRead()) return null
        return try {
            val headerBytes = ByteArray(1024 * 512)
            val read = file.inputStream().use { it.read(headerBytes) }
            if (read <= 0) return null
            val content = String(headerBytes, 0, read, Charsets.ISO_8859_1)

            val tagKeys = listOf("LYRICS-TTML", "TTML", "lyrics-ttml", "ttml", "LYRICS", "lyrics", "USLT")
            for (key in tagKeys) {
                val index = content.indexOf(key)
                if (index != -1) {
                    val xmlStart = content.indexOf("<tt", index)
                    if (xmlStart != -1 && xmlStart - index < 150) {
                        val xmlEnd = content.indexOf("</tt>", xmlStart)
                        if (xmlEnd != -1) {
                            val rawXml = content.substring(xmlStart, xmlEnd + 5)
                            return String(rawXml.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun findLrcContentForTrack(context: Context, track: AudioTrack): String? {
        val embedded = findEmbeddedLyrics(context, track.uri)
        if (!embedded.isNullOrBlank()) {
            return embedded
        }

        val file = findLrcFileForTrack(context, track) ?: return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                file.readText(charset("windows-1251"))
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun parseLyrics(rawContent: String?): List<LyricLine> {
        if (rawContent.isNullOrBlank()) return emptyList()
        val text = rawContent.trim()

        val isTtml = text.contains("<tt", ignoreCase = true) ||
                text.contains("xmlns=\"http://www.w3.org/ns/ttml\"", ignoreCase = true) ||
                text.contains("<body", ignoreCase = true)

        return if (isTtml) {
            parseTtml(text)
        } else {
            parseLrc(text)
        }
    }

    private fun parseTtmlTime(timeStr: String): Long {
        val clean = timeStr.trim().removeSuffix("s")
        return try {
            if (clean.contains(":")) {
                val parts = clean.split(":")
                if (parts.size == 2) {
                    val min = parts[0].toLong()
                    val secParts = parts[1].split(".")
                    val sec = secParts[0].toLong()
                    val ms = if (secParts.size > 1) {
                        secParts[1].take(3).padEnd(3, '0').toLong()
                    } else 0L
                    (min * 60_000L) + (sec * 1000L) + ms
                } else if (parts.size == 3) {
                    val hours = parts[0].toLong()
                    val min = parts[1].toLong()
                    val secParts = parts[2].split(".")
                    val sec = secParts[0].toLong()
                    val ms = if (secParts.size > 1) {
                        secParts[1].take(3).padEnd(3, '0').toLong()
                    } else 0L
                    (hours * 3_600_000L) + (min * 60_000L) + (sec * 1000L) + ms
                } else 0L
            } else {
                (clean.toDouble() * 1000.0).toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseTtml(xml: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentLineBeginMs = 0L
            var currentLineWords = mutableListOf<SyllableWord>()
            var currentLineTextBuilder = StringBuilder()

            var currentSpanBeginMs = 0L
            var currentSpanEndMs = 0L
            var insideSpan = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.lowercase()

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "p" -> {
                                currentLineWords = mutableListOf()
                                currentLineTextBuilder = StringBuilder()
                                val beginAttr = parser.getAttributeValue(null, "begin")
                                    ?: (0 until parser.attributeCount).firstNotNullOfOrNull { i ->
                                        if (parser.getAttributeName(i).equals("begin", ignoreCase = true)) parser.getAttributeValue(i) else null
                                    }
                                currentLineBeginMs = if (beginAttr != null) parseTtmlTime(beginAttr) else 0L
                            }
                            "span" -> {
                                val beginAttr = parser.getAttributeValue(null, "begin")
                                    ?: (0 until parser.attributeCount).firstNotNullOfOrNull { i ->
                                        if (parser.getAttributeName(i).equals("begin", ignoreCase = true)) parser.getAttributeValue(i) else null
                                    }
                                val endAttr = parser.getAttributeValue(null, "end")
                                    ?: (0 until parser.attributeCount).firstNotNullOfOrNull { i ->
                                        if (parser.getAttributeName(i).equals("end", ignoreCase = true)) parser.getAttributeValue(i) else null
                                    }

                                currentSpanBeginMs = if (beginAttr != null) parseTtmlTime(beginAttr) else currentLineBeginMs
                                currentSpanEndMs = if (endAttr != null) parseTtmlTime(endAttr) else (currentSpanBeginMs + 300L)
                                insideSpan = true
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            if (insideSpan) {
                                currentLineWords.add(
                                    SyllableWord(
                                        text = text,
                                        startMs = currentSpanBeginMs,
                                        endMs = currentSpanEndMs
                                    )
                                )
                            }
                            if (currentLineTextBuilder.isNotEmpty()) {
                                currentLineTextBuilder.append(" ")
                            }
                            currentLineTextBuilder.append(text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tagName) {
                            "span" -> {
                                insideSpan = false
                            }
                            "p" -> {
                                val fullText = currentLineTextBuilder.toString().trim()
                                if (fullText.isNotEmpty()) {
                                    val effectiveTime = currentLineWords.firstOrNull()?.startMs ?: currentLineBeginMs
                                    lines.add(
                                        LyricLine(
                                            timeMs = effectiveTime,
                                            text = fullText,
                                            words = currentLineWords
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return lines.sortedBy { it.timeMs }
    }

    private fun parseLrc(content: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val linePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})\\](.*)")

        content.lines().forEach { rawLine ->
            val matcher = linePattern.matcher(rawLine.trim())
            if (matcher.matches()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msStr = matcher.group(3) ?: "0"
                val ms = if (msStr.length == 2) msStr.toLong() * 10L else msStr.take(3).padEnd(3, '0').toLong()
                val text = matcher.group(4)?.trim() ?: ""

                val timeMs = (min * 60_000L) + (sec * 1000L) + ms
                if (text.isNotBlank()) {
                    lines.add(LyricLine(timeMs = timeMs, text = text, words = emptyList()))
                }
            }
        }

        return lines.sortedBy { it.timeMs }
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
        val extensions = setOf("mp3", "wav", "flac", "m4a", "mp4", "ogg", "opus", "m4b", "lrc", "ttml")

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

    fun getAudioTracks(context: Context, forceReload: Boolean = false): List<AudioTrack> {
        if (!forceReload && cachedTracks != null) {
            return cachedTracks!!
        }

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
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

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
                    val duration = cursor.getLong(durCol)
                    if (duration in 1..14999) continue

                    val id = cursor.getLong(idCol)
                    var title = cursor.getString(titleCol)
                    val artist = cursor.getString(artistCol) ?: "<Unknown>"
                    val album = cursor.getString(albumCol) ?: "<Unknown Album>"
                    val albumId = cursor.getLong(albumIdCol)
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

        cachedTracks = tracks
        cachedAlbums = null

        return tracks
    }
}