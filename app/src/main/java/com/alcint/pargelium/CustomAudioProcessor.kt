package com.alcint.pargelium

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import com.alcint.pargelium.audio.JamesDspController
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@Volatile
var globalAudioLoudness = 0f

@UnstableApi
class CustomAudioProcessor : AudioProcessor {

    companion object {
        private const val INV_32768 = 1.0f / 32768.0f
        private const val LOUDNESS_SMOOTH_FACTOR = 0.2f
    }

    private var inputFormat = AudioFormat.NOT_SET
    private var outputFormat = AudioFormat.NOT_SET
    private var isProcessorActive = false
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var allocatedBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    private var floatBuffer = FloatArray(4096)

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        inputFormat = inputAudioFormat
        outputFormat = inputAudioFormat
        isProcessorActive = true

        JamesDspController.init(inputAudioFormat.sampleRate)

        return outputFormat
    }

    override fun isActive(): Boolean = isProcessorActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val sizeBytes = limit - position
        if (sizeBytes <= 0) return

        val sampleCount = sizeBytes shr 1

        if (floatBuffer.size < sampleCount) {
            floatBuffer = FloatArray(sampleCount)
        }

        val shortBuffer = inputBuffer.asShortBuffer()
        for (i in 0 until sampleCount) {
            floatBuffer[i] = shortBuffer.get() * INV_32768
        }

        JamesDspController.process(floatBuffer)

        val resultBuffer = obtainOutputBuffer(sizeBytes)

        var peakMagnitude = 0.0f

        for (i in 0 until sampleCount) {
            val sample = floatBuffer[i]
            val absSample = abs(sample)
            if (absSample > peakMagnitude) {
                peakMagnitude = absSample
            }

            val clamped = sample.coerceIn(-1.0f, 1.0f)
            val pcmShort = (clamped * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            resultBuffer.putShort(pcmShort)
        }

        val currentLoudness = globalAudioLoudness
        globalAudioLoudness = currentLoudness + (peakMagnitude.coerceAtMost(1.0f) - currentLoudness) * LOUDNESS_SMOOTH_FACTOR

        inputBuffer.position(limit)
        resultBuffer.flip()
        outputBuffer = resultBuffer
    }

    private fun obtainOutputBuffer(size: Int): ByteBuffer {
        if (allocatedBuffer.capacity() < size) {
            allocatedBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            allocatedBuffer.clear()
        }
        return allocatedBuffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

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
    }

    override fun reset() {
        flush()
        allocatedBuffer = AudioProcessor.EMPTY_BUFFER
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
        isProcessorActive = false
        floatBuffer = FloatArray(4096)
    }

    fun release() {
        JamesDspController.release()
    }
}