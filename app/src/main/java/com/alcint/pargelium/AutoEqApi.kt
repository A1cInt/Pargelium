package com.alcint.pargelium

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ln

data class AutoEqProfile(
    val name: String,
    val path: String,
    val source: String = "",
    val gains: FloatArray = FloatArray(15) { 0f }
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AutoEqProfile
        return name == other.name && path == other.path
    }

    override fun hashCode(): Int = name.hashCode() * 31 + path.hashCode()
}

object AutoEqApi {

    private val INDEX_URLS = listOf(
        "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/INDEX.md",
        "https://cdn.jsdelivr.net/gh/jaakkopasanen/AutoEq@master/results/INDEX.md",
        "https://fastly.jsdelivr.net/gh/jaakkopasanen/AutoEq@master/results/INDEX.md"
    )

    private val BASE_RESULT_URLS = listOf(
        "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results",
        "https://cdn.jsdelivr.net/gh/jaakkopasanen/AutoEq@master/results",
        "https://fastly.jsdelivr.net/gh/jaakkopasanen/AutoEq@master/results"
    )

    private val targetIsoFreqs = floatArrayOf(
        25f, 40f, 63f, 100f, 160f, 250f, 400f, 630f,
        1000f, 1600f, 2500f, 4000f, 6300f, 10000f, 16000f
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // Захватывает полный URL до последней закрывающей скобки в строке (не обрезая модели со скобками в имени)
    private val markdownLinkRegex = Regex("""^\s*-\s+\[([^\]]+)]\((.+)\)\s*$""")
    private val sourceMeasureRegex = Regex("""by\s+(.+)$""", RegexOption.IGNORE_CASE)

    @Volatile
    private var cachedProfiles: List<AutoEqProfile>? = null

    fun getDatabaseIndex(forceRefresh: Boolean = false): List<AutoEqProfile> {
        if (!forceRefresh) {
            cachedProfiles?.let { if (it.isNotEmpty()) return it }
        }

        var lastError: Exception? = null

        for (indexUrl in INDEX_URLS) {
            try {
                val request = Request.Builder()
                    .url(indexUrl)
                    .header("User-Agent", "PargeliumAudioPlayer/2.5")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }

                    val profiles = ArrayList<AutoEqProfile>(4096)
                    val seenKeys = HashSet<String>(4096)

                    response.body?.charStream()?.buffered()?.useLines { lines ->
                        lines.forEach { line ->
                            val match = markdownLinkRegex.find(line) ?: return@forEach
                            val name = match.groupValues[1].trim()
                            val rawPath = match.groupValues[2].trim()
                                .removePrefix("./")
                                .trimStart('/')

                            if (rawPath.isEmpty() || rawPath.startsWith("http")) return@forEach

                            val sourceHint = sourceMeasureRegex.find(line)?.groupValues?.getOrNull(1)?.trim()
                                ?: rawPath.substringBefore("/")

                            val displayName = if (seenKeys.add(name.lowercase())) {
                                name
                            } else {
                                "$name ($sourceHint)"
                            }

                            profiles.add(
                                AutoEqProfile(
                                    name = displayName,
                                    path = rawPath,
                                    source = sourceHint
                                )
                            )
                        }
                    }

                    if (profiles.isNotEmpty()) {
                        cachedProfiles = profiles
                        return profiles
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw Exception("Не удалось загрузить базу AutoEQ: ${lastError?.localizedMessage ?: "нет сети"}")
    }

    private fun normalizeAndEncodePath(rawPath: String): Pair<String, String> {
        val clean = rawPath.removePrefix("./").trim('/')
        val segments = clean.split("/").filter { it.isNotBlank() }
        val decodedSegments = segments.map { seg ->
            runCatching { URLDecoder.decode(seg, "UTF-8") }.getOrDefault(seg)
        }
        val encodedPath = decodedSegments.joinToString("/") { seg ->
            Uri.encode(seg)
        }
        val folderNameDecoded = decodedSegments.lastOrNull() ?: clean
        return encodedPath to folderNameDecoded
    }

    fun downloadPresetGains(profile: AutoEqProfile): FloatArray {
        val (encodedPath, folderNameDecoded) = normalizeAndEncodePath(profile.path)
        val encodedGraphicFile = Uri.encode("$folderNameDecoded GraphicEQ.txt")
        val encodedFixedBandFile = Uri.encode("$folderNameDecoded FixedBandEQ.txt")

        var lastErrorMsg = "Файл профиля не найден"

        for (baseUrl in BASE_RESULT_URLS) {
            val graphicUrl = "$baseUrl/$encodedPath/$encodedGraphicFile"
            val graphicBody = fetchTextOrNull(graphicUrl)
            if (!graphicBody.isNullOrBlank() && graphicBody.contains("GraphicEQ:", ignoreCase = true)) {
                val parsed = parseGraphicEqTo15IsoBands(graphicBody)
                if (parsed != null) return parsed
            }

            val fixedBandUrl = "$baseUrl/$encodedPath/$encodedFixedBandFile"
            val fixedBody = fetchTextOrNull(fixedBandUrl)
            if (!fixedBody.isNullOrBlank() && fixedBody.contains("Filter", ignoreCase = true)) {
                val parsed = parseFixedBandEqTo15IsoBands(fixedBody)
                if (parsed != null) return parsed
            }
        }

        throw Exception(lastErrorMsg)
    }

    private fun fetchTextOrNull(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "PargeliumAudioPlayer/2.5")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string()?.trim() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseGraphicEqTo15IsoBands(eqString: String): FloatArray? {
        val data = eqString.substringAfter("GraphicEQ:", "").trim()
        if (data.isEmpty()) return null

        val points = ArrayList<Pair<Float, Float>>(128)
        val parts = data.split(";")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            val kv = trimmed.split(Regex("\\s+"))
            if (kv.size >= 2) {
                val freq = kv[0].toFloatOrNull()
                val gain = kv[1].toFloatOrNull()
                if (freq != null && gain != null && freq > 0f) {
                    points.add(freq to gain)
                }
            }
        }

        if (points.isEmpty()) return null
        points.sortBy { it.first }

        return interpolatePointsTo15Iso(points)
    }

    private fun parseFixedBandEqTo15IsoBands(text: String): FloatArray? {
        val points = ArrayList<Pair<Float, Float>>(16)
        val preAmpRegex = Regex("""Preamp:\s*([-+]?\d*\.?\d+)\s*dB""", RegexOption.IGNORE_CASE)
        val filterRegex = Regex(
            """Filter\s+\d+:\s+ON\s+\w+\s+Fc\s+([-+]?\d*\.?\d+)\s*Hz\s+Gain\s+([-+]?\d*\.?\d+)\s*dB""",
            RegexOption.IGNORE_CASE
        )

        text.lineSequence().forEach { line ->
            val match = filterRegex.find(line)
            if (match != null) {
                val fc = match.groupValues[1].toFloatOrNull()
                val gain = match.groupValues[2].toFloatOrNull()
                if (fc != null && gain != null && fc > 0f) {
                    points.add(fc to gain)
                }
            }
        }

        if (points.isEmpty()) return null
        points.sortBy { it.first }
        return interpolatePointsTo15Iso(points)
    }

    private fun interpolatePointsTo15Iso(points: List<Pair<Float, Float>>): FloatArray {
        val result = FloatArray(15)
        val firstF = points.first().first
        val lastF = points.last().first

        for (i in targetIsoFreqs.indices) {
            val target = targetIsoFreqs[i]
            when {
                target <= firstF -> result[i] = points.first().second
                target >= lastF -> result[i] = points.last().second
                else -> {
                    var interpolated = 0f
                    for (j in 0 until points.lastIndex) {
                        val (f0, g0) = points[j]
                        val (f1, g1) = points[j + 1]
                        if (target in f0..f1) {
                            val denom = ln(f1) - ln(f0)
                            val t = if (abs(denom) > 1e-5f) (ln(target) - ln(f0)) / denom else 0f
                            interpolated = g0 + t * (g1 - g0)
                            break
                        }
                    }
                    result[i] = interpolated.coerceIn(-15f, 15f)
                }
            }
        }
        return result
    }
}