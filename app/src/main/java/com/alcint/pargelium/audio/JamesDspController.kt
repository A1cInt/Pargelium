package com.alcint.pargelium.audio

object JamesDspController {
    init {
        System.loadLibrary("jamesdsp-jni")
    }

    private external fun nativeInit(sampleRate: Int): Long
    private external fun nativeRelease(handle: Long)
    private external fun nativeProcess(handle: Long, buffer: FloatArray, numFrames: Int)

    private external fun nativeSetBass(handle: Long, enabled: Boolean, gainDb: Int)
    private external fun nativeSetEqualizer(handle: Long, enabled: Boolean, gains: FloatArray?)
    private external fun nativeSetTube(handle: Long, enabled: Boolean, drive: Int)
    private external fun nativeSetReverb(handle: Long, enabled: Boolean, preset: Int)
    private external fun nativeSetStereoWide(handle: Long, enabled: Boolean, width: Int)
    private external fun nativeSetCrossfeed(handle: Long, enabled: Boolean, mode: Int)

    private var engineHandle: Long = 0
    private var currentSampleRate: Int = 0

    private var isMasterEnabled = true
    private var isBassEnabled = false
    private var bassGain = 60

    private var isEqEnabled = false
    private var eqGains: FloatArray? = null

    private var isTubeEnabled = false
    private var tubeDrive = 200

    private var isReverbEnabled = false
    private var reverbPreset = 1

    private var isStereoWideEnabled = false
    private var stereoWidth = 50

    private var isBs2bEnabled = false
    private var bs2bMode = 1

    @Synchronized
    fun init(sampleRate: Int = 44100) {
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

    @Synchronized
    fun process(buffer: FloatArray) {
        if (engineHandle != 0L && isMasterEnabled && buffer.isNotEmpty()) {
            nativeProcess(engineHandle, buffer, buffer.size / 2)
        }
    }

    private fun applyAllCurrentSettings() {
        if (engineHandle == 0L) return
        nativeSetBass(engineHandle, isBassEnabled, bassGain)
        nativeSetEqualizer(engineHandle, isEqEnabled, eqGains)
        nativeSetTube(engineHandle, isTubeEnabled, tubeDrive)
        nativeSetReverb(engineHandle, isReverbEnabled, reverbPreset)
        nativeSetStereoWide(engineHandle, isStereoWideEnabled, stereoWidth)
        nativeSetCrossfeed(engineHandle, isBs2bEnabled, bs2bMode)
    }

    fun setMasterEnabled(enabled: Boolean) {
        isMasterEnabled = enabled
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        isEqEnabled = enabled
        if (engineHandle != 0L) nativeSetEqualizer(engineHandle, enabled, eqGains)
    }

    fun setEqualizerGains(gains: FloatArray) {
        eqGains = gains
        if (engineHandle != 0L && isEqEnabled) nativeSetEqualizer(engineHandle, true, gains)
    }

    fun setAutoEqEnabled(enabled: Boolean) {
        setEqualizerEnabled(enabled)
    }

    fun applyAutoEq(gains: FloatArray) {
        setEqualizerGains(gains)
    }

    fun setBassEnabled(enabled: Boolean) {
        isBassEnabled = enabled
        if (engineHandle != 0L) nativeSetBass(engineHandle, enabled, bassGain)
    }

    fun setBassGain(gainDb: Short) {
        bassGain = gainDb.toInt()
        if (engineHandle != 0L && isBassEnabled) nativeSetBass(engineHandle, true, bassGain)
    }

    fun setReverbEnabled(enabled: Boolean) {
        isReverbEnabled = enabled
        if (engineHandle != 0L) nativeSetReverb(engineHandle, enabled, reverbPreset)
    }

    fun setReverbPreset(preset: Short) {
        reverbPreset = preset.toInt()
        if (engineHandle != 0L && isReverbEnabled) nativeSetReverb(engineHandle, true, reverbPreset)
    }

    fun setStereoWideEnabled(enabled: Boolean) {
        isStereoWideEnabled = enabled
        if (engineHandle != 0L) nativeSetStereoWide(engineHandle, enabled, stereoWidth)
    }

    fun setStereoWidth(percent: Short) {
        stereoWidth = percent.toInt()
        if (engineHandle != 0L && isStereoWideEnabled) nativeSetStereoWide(engineHandle, true, stereoWidth)
    }

    fun setBs2bEnabled(enabled: Boolean) {
        isBs2bEnabled = enabled
        if (engineHandle != 0L) nativeSetCrossfeed(engineHandle, enabled, bs2bMode)
    }

    fun setBs2bMode(mode: Short) {
        bs2bMode = mode.toInt()
        if (engineHandle != 0L && isBs2bEnabled) nativeSetCrossfeed(engineHandle, true, bs2bMode)
    }

    fun setTubeEnabled(enabled: Boolean) {
        isTubeEnabled = enabled
        if (engineHandle != 0L) nativeSetTube(engineHandle, enabled, tubeDrive)
    }

    fun setTubeDrive(drive: Float) {
        tubeDrive = (drive * 1000f).toInt()
        if (engineHandle != 0L && isTubeEnabled) nativeSetTube(engineHandle, true, tubeDrive)
    }

    fun setLimiterParams(threshold: Float, release: Float, postgain: Float) {
    }
}