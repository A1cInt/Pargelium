package com.alcint.pargelium

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Volatile
var globalAudioLoudness = 0f

@UnstableApi
class CustomAudioProcessor : AudioProcessor {
    companion object {
        init {
            System.loadLibrary("pargelium_dsp")
        }
    }

    private var nativePtr: Long = 0L
    private var inputFormat = AudioFormat.NOT_SET
    private var outputFormat = AudioFormat.NOT_SET
    private var isActive = false
    private var buffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    private val settingsArray = FloatArray(45)
    private var settingsUpdateCounter = 0

    init {
        nativePtr = nativeInit()
    }

    private external fun nativeInit(): Long
    private external fun nativeRelease(ptr: Long)
    private external fun nativeFlush(ptr: Long)
    private external fun nativeProcess(
        ptr: Long,
        inBuffer: ByteBuffer,
        outBuffer: ByteBuffer,
        sizeBytes: Int,
        sampleRate: Int,
        channels: Int,
        settings: FloatArray,
        currentLoudness: Float
    ): Float

    private fun checkPrefs() {
        settingsArray[0] = if (PrefsManager.getEnabled()) 1f else 0f
        settingsArray[1] = PrefsManager.getReverbMode().toFloat()
        settingsArray[2] = if (PrefsManager.getAutoEqEnabled()) 1f else 0f
        settingsArray[3] = if (PrefsManager.getUserEqEnabled()) 1f else 0f
        settingsArray[4] = if (PrefsManager.getBassEnabled()) 1f else 0f
        settingsArray[5] = PrefsManager.getBass().toFloat()
        settingsArray[6] = PrefsManager.getBassFreq().toFloat()
        settingsArray[7] = if (PrefsManager.getHaas()) 1f else 0f
        settingsArray[8] = PrefsManager.getHaasDelay().toFloat()
        settingsArray[9] = if (PrefsManager.getRoom()) 1f else 0f
        settingsArray[10] = PrefsManager.getReverbMix().toFloat()
        settingsArray[11] = PrefsManager.getReverbSize().toFloat()
        settingsArray[12] = PrefsManager.getReverbDamp().toFloat()
        settingsArray[13] = if (PrefsManager.getSpatializer()) 1f else 0f
        settingsArray[14] = PrefsManager.getSpatialWidth().toFloat()

        val autoEq = PrefsManager.getEqGains()
        for (i in 0..14) settingsArray[15 + i] = autoEq[i]

        val userEq = PrefsManager.getUserEqGains()
        for (i in 0..9) settingsArray[30 + i] = userEq[i]

        settingsArray[40] = if (PrefsManager.getTube()) 1f else 0f
        settingsArray[41] = if (PrefsManager.getCrossfeed()) 1f else 0f
        settingsArray[42] = if (PrefsManager.getMp3Restorer()) 1f else 0f
        settingsArray[43] = PrefsManager.getExciterIntensity().toFloat()
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        inputFormat = inputAudioFormat
        outputFormat = inputAudioFormat
        isActive = true
        checkPrefs()
        return outputFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        if (size == 0 || nativePtr == 0L) return

        val resultBuffer = replaceOutputBuffer(size)

        if (settingsUpdateCounter++ % 20 == 0) {
            checkPrefs()
        }

        globalAudioLoudness = nativeProcess(
            nativePtr,
            inputBuffer,
            resultBuffer,
            size,
            inputFormat.sampleRate,
            inputFormat.channelCount,
            settingsArray,
            globalAudioLoudness
        )

        inputBuffer.position(limit)
        resultBuffer.position(size)
        resultBuffer.flip()
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else buffer.clear()
        outputBuffer = buffer
        return buffer
    }

    override fun queueEndOfStream() { inputEnded = true }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        globalAudioLoudness = 0f
        if (nativePtr != 0L) nativeFlush(nativePtr)
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
    }

    fun release() {
        if (nativePtr != 0L) {
            nativeRelease(nativePtr)
            nativePtr = 0L
        }
    }
}