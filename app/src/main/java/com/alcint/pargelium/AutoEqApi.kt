package com.alcint.pargelium

import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

data class AutoEqProfile(
    val name: String,
    val path: String,
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
    private const val INDEX_URL = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/INDEX.md"

    fun getDatabaseIndex(): List<AutoEqProfile> {
        val profiles = mutableListOf<AutoEqProfile>()
        try {
            val url = URL(INDEX_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            connection.inputStream.bufferedReader().use { reader ->
                val regex = Regex("""^\s*-\s+\[(.+?)\]\((.+?)\)""")
                reader.forEachLine { line ->
                    val match = regex.find(line)
                    if (match != null) {
                        val name = match.groupValues[1]
                        val path = match.groupValues[2]
                        if (!path.startsWith("http")) {
                            profiles.add(AutoEqProfile(name, path))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Ошибка загрузки базы: ${e.message}")
        }
        return profiles.distinctBy { it.name }
    }

    fun downloadPresetGains(profile: AutoEqProfile): FloatArray {
        var fileUrl = ""
        try {
            val folderName = profile.path.substringAfterLast("/")

            fileUrl = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/${profile.path}/${folderName}%20GraphicEQ.txt"

            val url = URL(fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) {
                throw Exception("Код ${connection.responseCode} (Файл не найден)")
            }

            val eqString = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            val parsedGains = mutableMapOf<Float, Float>()

            if (eqString.startsWith("GraphicEQ:")) {
                val data = eqString.substringAfter("GraphicEQ:")
                val parts = data.split(";")
                for (part in parts) {
                    if (part.isBlank()) continue
                    val kv = part.trim().split(Regex("\\s+"))
                    if (kv.size >= 2) {
                        val freq = kv[0].toFloatOrNull()
                        val gain = kv[1].toFloatOrNull()
                        if (freq != null && gain != null) {
                            parsedGains[freq] = gain
                        }
                    }
                }
            } else {
                throw Exception("Неверный формат файла")
            }

            if (parsedGains.isEmpty()) {
                throw Exception("Пустой файл или не удалось распарсить частоты")
            }

            val targetFreqs = floatArrayOf(25f, 40f, 63f, 100f, 160f, 250f, 400f, 630f, 1000f, 1600f, 2500f, 4000f, 6300f, 10000f, 16000f)
            val resultGains = FloatArray(15)

            for (i in targetFreqs.indices) {
                val target = targetFreqs[i]
                var closestFreq = 0f
                var minDiff = Float.MAX_VALUE
                for ((f, _) in parsedGains) {
                    val diff = abs(f - target)
                    if (diff < minDiff) {
                        minDiff = diff
                        closestFreq = f
                    }
                }
                resultGains[i] = parsedGains[closestFreq] ?: 0f
            }

            return resultGains

        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("${e.message}\nURL: $fileUrl")
        }
    }
}