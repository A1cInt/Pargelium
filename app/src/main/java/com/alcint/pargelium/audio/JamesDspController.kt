package com.alcint.pargelium.audio

import com.alcint.pargelium.PrefsManager
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

object JamesDspController {
    init {
        try {
            System.loadLibrary("jamesdsp-jni")
        } catch (_: UnsatisfiedLinkError) {}
    }

    private external fun nativeInit(sampleRate: Int): Long
    private external fun nativeRelease(handle: Long)
    private external fun nativeProcess(handle: Long, buffer: FloatArray, numFrames: Int, preAmpMultiplier: Float)

    private external fun nativeSetBass(handle: Long, enabled: Boolean, gainDb: Float)
    private external fun nativeSetEqualizer(handle: Long, enabled: Boolean, gains: FloatArray?)
    private external fun nativeSetTube(handle: Long, enabled: Boolean, driveDb: Float)
    private external fun nativeSetReverb(handle: Long, enabled: Boolean, preset: Int)
    private external fun nativeSetStereoWide(handle: Long, enabled: Boolean, width: Int)
    private external fun nativeSetCrossfeed(handle: Long, enabled: Boolean, mode: Int)
    private external fun nativeSetLimiter(handle: Long, thresholdDb: Float, releaseMs: Float, postGainDb: Float)

    private var engineHandle: Long = 0L
    private var currentSampleRate: Int = 0
    private var hasRestoredFromPrefs = false

    @Volatile
    var isMasterEnabled: Boolean = true
        private set

    @Volatile
    var isAutoHeadroomEnabled: Boolean = true

    @Volatile
    var currentHeadroomDb: Float = 0f
        private set

    @Volatile
    private var preAmpLinear: Float = 1.0f

    private var isBassEnabled = false
    private var bassGainDb = 6.0f

    private var isUserEqEnabled = false
    private var userEqGains: FloatArray = FloatArray(10) { 0f }

    private var isAutoEqEnabled = false
    private var autoEqGains: FloatArray? = null

    private var isTubeEnabled = false
    private var tubeDriveDb = 2.0f

    private var isReverbEnabled = false
    private var reverbPreset = 0

    private var isStereoWideEnabled = false
    private var stereoWidth = 50

    private var isBs2bEnabled = false
    private var bs2bMode = 0

    private var limThresholdDb = -1.0f
    private var limReleaseMs = 60.0f
    private var postGainDb = 0.0f

    private val uiFreqs10 = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    private val isoFreqs15 = doubleArrayOf(
        25.0, 40.0, 63.0, 100.0, 160.0, 250.0, 400.0, 630.0,
        1000.0, 1600.0, 2500.0, 4000.0, 6300.0, 10000.0, 16000.0
    )

    @Synchronized
    fun syncFromPrefs() {
        try {
            isMasterEnabled = PrefsManager.getEnabled()
            isUserEqEnabled = PrefsManager.getUserEqEnabled()
            userEqGains = PrefsManager.getUserEqGains()

            isAutoEqEnabled = PrefsManager.getAutoEqEnabled()
            autoEqGains = PrefsManager.getCurrentAutoEqProfile()?.gains ?: PrefsManager.getEqGains()

            isBassEnabled = PrefsManager.getBassEnabled()
            bassGainDb = PrefsManager.getBass().toFloat()

            isReverbEnabled = PrefsManager.getRoom()
            reverbPreset = PrefsManager.getReverbMode()

            isStereoWideEnabled = PrefsManager.getStereoWideEnabled()
            stereoWidth = PrefsManager.getStereoWidth().toInt()

            isBs2bEnabled = PrefsManager.getBs2bEnabled()
            bs2bMode = PrefsManager.getBs2bMode()

            isTubeEnabled = PrefsManager.getTubeEnabled()
            tubeDriveDb = PrefsManager.getTubeDrive()

            limThresholdDb = PrefsManager.getLimThreshold()
            limReleaseMs = PrefsManager.getLimRelease()
            postGainDb = PrefsManager.getPostGain()
            hasRestoredFromPrefs = true
        } catch (_: Exception) {}
    }

    @Synchronized
    fun init(sampleRate: Int = 48000) {
        if (!hasRestoredFromPrefs) {
            syncFromPrefs()
        }
        if (engineHandle != 0L && currentSampleRate != sampleRate) {
            release()
        }
        if (engineHandle == 0L) {
            currentSampleRate = sampleRate
            engineHandle = nativeInit(sampleRate)
            applyAllCurrentSettings()
        }
    }

    @Synchronized
    fun release() {
        if (engineHandle != 0L) {
            nativeRelease(engineHandle)
            engineHandle = 0L
            currentSampleRate = 0
        }
    }

    /**
     * Обработка буфера с передачей реального числа сэмплов (sampleCount),
     * чтобы не обрабатывать мусор в хвосте массива.
     */
    @Synchronized
    fun process(buffer: FloatArray, sampleCount: Int = buffer.size) {
        val validSamples = sampleCount.coerceAtMost(buffer.size)
        if (engineHandle != 0L && isMasterEnabled && validSamples >= 2) {
            nativeProcess(engineHandle, buffer, validSamples shr 1, preAmpLinear)
        }
    }

    private fun interpolateTo15Bands(source: FloatArray): FloatArray {
        if (source.size == 15) return source.copyOf()
        if (source.isEmpty()) return FloatArray(15) { 0f }
        if (source.size != 10) {
            return FloatArray(15) { i -> source.getOrElse(i) { 0f } }
        }

        val out = FloatArray(15)
        for (i in 0 until 15) {
            val f = isoFreqs15[i]
            when {
                f <= uiFreqs10[0] -> out[i] = source[0]
                f >= uiFreqs10[9] -> out[i] = source[9]
                else -> {
                    for (j in 0 until 9) {
                        if (f >= uiFreqs10[j] && f <= uiFreqs10[j + 1]) {
                            val t = ((ln(f) - ln(uiFreqs10[j])) / (ln(uiFreqs10[j + 1]) - ln(uiFreqs10[j]))).toFloat()
                            out[i] = source[j] + t * (source[j + 1] - source[j])
                            break
                        }
                    }
                }
            }
        }
        return out
    }

    fun getCombined15BandResponse(): FloatArray {
        val combined = FloatArray(15) { 0f }
        if (isUserEqEnabled) {
            val u15 = interpolateTo15Bands(userEqGains)
            for (i in 0 until 15) combined[i] += u15[i]
        }
        if (isAutoEqEnabled && autoEqGains != null) {
            val a15 = interpolateTo15Bands(autoEqGains!!)
            for (i in 0 until 15) combined[i] += a15[i]
        }
        for (i in 0 until 15) {
            combined[i] = combined[i].coerceIn(-15f, 15f)
        }
        return combined
    }

    private fun recalculateHeadroomAndSyncEq() {
        val anyEqActive = isUserEqEnabled || (isAutoEqEnabled && autoEqGains != null)
        val combinedGains = getCombined15BandResponse()

        var maxPositiveEqDb = 0f
        if (anyEqActive) {
            for (g in combinedGains) {
                if (g > maxPositiveEqDb) maxPositiveEqDb = g
            }
        }

        val bassBoostHeadroom = if (isBassEnabled) bassGainDb * 0.55f else 0f
        val tubeHeadroom = if (isTubeEnabled) tubeDriveDb * 0.35f else 0f
        val reverbHeadroom = if (isReverbEnabled) 1.5f else 0f

        // Вычисляем необходимый запас по перегрузу (Anti-Clip Preamp)
        val requiredAttenuationDb = if (isAutoHeadroomEnabled) {
            -(maxPositiveEqDb * 0.85f + bassBoostHeadroom + tubeHeadroom + reverbHeadroom).coerceIn(0f, 14f)
        } else {
            0f
        }

        currentHeadroomDb = requiredAttenuationDb
        preAmpLinear = 10.0.pow(requiredAttenuationDb.toDouble() / 20.0).toFloat().coerceIn(0.15f, 1.0f)

        if (engineHandle != 0L) {
            nativeSetEqualizer(engineHandle, anyEqActive, combinedGains)
        }
    }

    private fun applyAllCurrentSettings() {
        if (engineHandle == 0L) return
        recalculateHeadroomAndSyncEq()
        nativeSetBass(engineHandle, isBassEnabled, bassGainDb)
        nativeSetTube(engineHandle, isTubeEnabled, tubeDriveDb)
        nativeSetReverb(engineHandle, isReverbEnabled, reverbPreset)
        nativeSetStereoWide(engineHandle, isStereoWideEnabled, stereoWidth)
        nativeSetCrossfeed(engineHandle, isBs2bEnabled, bs2bMode)
        nativeSetLimiter(engineHandle, limThresholdDb, limReleaseMs, postGainDb)
    }

    @Synchronized
    fun setMasterEnabled(enabled: Boolean) {
        isMasterEnabled = enabled
    }

    @Synchronized
    fun setAutoHeadroom(enabled: Boolean) {
        isAutoHeadroomEnabled = enabled
        recalculateHeadroomAndSyncEq()
    }

    @Synchronized
    fun setEqualizerEnabled(enabled: Boolean) {
        isUserEqEnabled = enabled
        recalculateHeadroomAndSyncEq()
    }

    @Synchronized
    fun setEqualizerGains(gains: FloatArray) {
        userEqGains = gains.copyOf()
        recalculateHeadroomAndSyncEq()
    }

    @Synchronized
    fun setAutoEqEnabled(enabled: Boolean) {
        isAutoEqEnabled = enabled
        recalculateHeadroomAndSyncEq()
    }

    @Synchronized
    fun applyAutoEq(gains: FloatArray) {
        autoEqGains = gains.copyOf()
        isAutoEqEnabled = true
        recalculateHeadroomAndSyncEq()
    }

    @Synchronized
    fun setBassEnabled(enabled: Boolean) {
        isBassEnabled = enabled
        recalculateHeadroomAndSyncEq()
        if (engineHandle != 0L) nativeSetBass(engineHandle, enabled, bassGainDb)
    }

    @Synchronized
    fun setBassGain(gainDb: Short) {
        bassGainDb = gainDb.toFloat().coerceIn(0f, 15f)
        recalculateHeadroomAndSyncEq()
        if (engineHandle != 0L && isBassEnabled) nativeSetBass(engineHandle, true, bassGainDb)
    }

    @Synchronized
    fun setBassGainFloat(gainDb: Float) {
        bassGainDb = gainDb.coerceIn(0f, 15f)
        recalculateHeadroomAndSyncEq()
        if (engineHandle != 0L && isBassEnabled) nativeSetBass(engineHandle, true, bassGainDb)
    }

    @Synchronized
    fun setReverbEnabled(enabled: Boolean) {
        isReverbEnabled = enabled
        recalculateHeadroomAndSyncEq()
        if (engineHandle != 0L) nativeSetReverb(engineHandle, enabled, reverbPreset)
    }

    @Synchronized
    fun setReverbPreset(preset: Short) {
        reverbPreset = preset.toInt()
        if (engineHandle != 0L && isReverbEnabled) nativeSetReverb(engineHandle, true, reverbPreset)
    }

    @Synchronized
    fun setStereoWideEnabled(enabled: Boolean) {
        isStereoWideEnabled = enabled
        if (engineHandle != 0L) nativeSetStereoWide(engineHandle, enabled, stereoWidth)
    }

    @Synchronized
    fun setStereoWidth(percent: Short) {
        stereoWidth = percent.toInt().coerceIn(0, 100)
        if (engineHandle != 0L && isStereoWideEnabled) nativeSetStereoWide(engineHandle, true, stereoWidth)
    }

    @Synchronized
    fun setBs2bEnabled(enabled: Boolean) {
        isBs2bEnabled = enabled
        if (engineHandle != 0L) nativeSetCrossfeed(engineHandle, enabled, bs2bMode)
    }

    @Synchronized
    fun setBs2bMode(mode: Short) {
        bs2bMode = mode.toInt()
        if (engineHandle != 0L && isBs2bEnabled) nativeSetCrossfeed(engineHandle, true, bs2bMode)
    }

    @Synchronized
    fun setTubeEnabled(enabled: Boolean) {
        isTubeEnabled = enabled
        recalculateHeadroomAndSyncEq()
        if (engineHandle != 0L) nativeSetTube(engineHandle, enabled, tubeDriveDb)
    }

    @Synchronized
    fun setTubeDrive(drive: Float) {
        tubeDriveDb = drive.coerceIn(0f, 10f)
        recalculateHeadroomAndSyncEq()
        if (engineHandle != 0L && isTubeEnabled) nativeSetTube(engineHandle, true, tubeDriveDb)
    }

    @Synchronized
    fun setLimiterParams(threshold: Float, release: Float, postgain: Float) {
        limThresholdDb = threshold.coerceIn(-12f, -0.05f)
        limReleaseMs = release.coerceIn(10f, 300f)
        postGainDb = postgain.coerceIn(-12f, 12f)
        if (engineHandle != 0L) {
            nativeSetLimiter(engineHandle, limThresholdDb, limReleaseMs, postGainDb)
        }
    }
}