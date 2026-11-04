package com.alcint.pargelium

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import com.alcint.pargelium.audio.JamesDspController
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.tanh

@Volatile
var globalAudioLoudness = 0f

@Volatile
var globalAudioPeakDb = -60f

@Volatile
var globalSoftClipActive = false

@UnstableApi
class CustomAudioProcessor : AudioProcessor {

    companion object {
        private const val INV_32768 = 1.0f / 32768.0f
        private const val INV_8388608 = 1.0f / 8388608.0f
        private const val LOUDNESS_SMOOTH_FACTOR = 0.22f
        private const val SOFT_KNEE_THRESHOLD = 0.92f
        private const val SOFT_KNEE_HEADROOM = 1.0f - SOFT_KNEE_THRESHOLD
    }

    private var inputFormat = AudioFormat.NOT_SET
    private var outputFormat = AudioFormat.NOT_SET
    private var isProcessorActive = false
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var allocatedBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    private var floatBuffer = FloatArray(8192)

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.channelCount != 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val encoding = inputAudioFormat.encoding
        if (
            encoding != C.ENCODING_PCM_16BIT &&
            encoding != C.ENCODING_PCM_FLOAT &&
            encoding != C.ENCODING_PCM_24BIT &&
            encoding != C.ENCODING_PCM_32BIT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        inputFormat = inputAudioFormat
        // Выдаем 16-бит или 32-бит Float в зависимости от входа для максимального динамического диапазона
        outputFormat = if (encoding == C.ENCODING_PCM_FLOAT) {
            inputAudioFormat
        } else {
            AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_16BIT)
        }
        isProcessorActive = true

        JamesDspController.init(inputAudioFormat.sampleRate)
        return outputFormat
    }

    override fun isActive(): Boolean = isProcessorActive

    /**
     * Мягкий прозрачный сатуратор верхнего колена (Soft-Knee Limiter):
     * До 0.92f сигнал проходит 1:1 без изменений. Всё, что выше 0.92f, плавно сжимается через tanh() к 0.999f.
     */
    private fun softKneeClip(sample: Float): Float {
        if (sample.isNaN() || sample.isInfinite()) return 0f
        val absVal = abs(sample)
        if (absVal <= SOFT_KNEE_THRESHOLD) return sample

        val excess = (absVal - SOFT_KNEE_THRESHOLD) / SOFT_KNEE_HEADROOM
        val compressed = SOFT_KNEE_THRESHOLD + SOFT_KNEE_HEADROOM * tanh(excess.toDouble()).toFloat()
        return if (sample >= 0f) compressed else -compressed
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val sizeBytes = limit - position
        if (sizeBytes <= 0) return

        val encoding = inputFormat.encoding
        val sampleCount = when (encoding) {
            C.ENCODING_PCM_16BIT -> sizeBytes shr 1
            C.ENCODING_PCM_24BIT -> sizeBytes / 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> sizeBytes shr 2
            else -> return
        }
        if (sampleCount <= 0) return

        if (floatBuffer.size < sampleCount) {
            floatBuffer = FloatArray(sampleCount)
        }

        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                val shortBuffer = inputBuffer.asShortBuffer()
                for (i in 0 until sampleCount) {
                    floatBuffer[i] = shortBuffer.get() * INV_32768
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val fb = inputBuffer.asFloatBuffer()
                for (i in 0 until sampleCount) {
                    floatBuffer[i] = fb.get()
                }
            }
            C.ENCODING_PCM_24BIT -> {
                var idx = position
                for (i in 0 until sampleCount) {
                    val b0 = inputBuffer.get(idx).toInt() and 0xFF
                    val b1 = inputBuffer.get(idx + 1).toInt() and 0xFF
                    val b2 = inputBuffer.get(idx + 2).toInt()
                    val int24 = (b2 shl 16) or (b1 shl 8) or b0
                    floatBuffer[i] = int24 * INV_8388608
                    idx += 3
                }
            }
            C.ENCODING_PCM_32BIT -> {
                val ib = inputBuffer.asIntBuffer()
                for (i in 0 until sampleCount) {
                    floatBuffer[i] = (ib.get() shr 8) * INV_8388608
                }
            }
        }

        // Передаем ТОЧНОЕ количество сэмплов в нативный движок JamesDSP
        JamesDspController.process(floatBuffer, sampleCount)

        val outBytes = if (outputFormat.encoding == C.ENCODING_PCM_FLOAT) {
            sampleCount shl 2
        } else {
            sampleCount shl 1
        }
        val resultBuffer = obtainOutputBuffer(outBytes)

        var peakMagnitude = 0.0f
        var softClippedInBlock = false

        if (outputFormat.encoding == C.ENCODING_PCM_FLOAT) {
            for (i in 0 until sampleCount) {
                val raw = floatBuffer[i]
                val absRaw = abs(raw)
                if (absRaw > peakMagnitude) peakMagnitude = absRaw
                if (absRaw > 0.96f) softClippedInBlock = true

                val clean = softKneeClip(raw)
                resultBuffer.putFloat(clean)
            }
        } else {
            for (i in 0 until sampleCount) {
                val raw = floatBuffer[i]
                val absRaw = abs(raw)
                if (absRaw > peakMagnitude) peakMagnitude = absRaw
                if (absRaw > 0.96f) softClippedInBlock = true

                val clean = softKneeClip(raw)
                val pcmShort = (clean * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                resultBuffer.putShort(pcmShort)
            }
        }

        val currentLoudness = globalAudioLoudness
        val smoothed = currentLoudness + (peakMagnitude.coerceAtMost(1.0f) - currentLoudness) * LOUDNESS_SMOOTH_FACTOR
        globalAudioLoudness = smoothed
        currentAudioLoudness = smoothed
        globalSoftClipActive = softClippedInBlock
        globalAudioPeakDb = if (peakMagnitude > 0.0001f) {
            (20.0 * log10(peakMagnitude.toDouble())).toFloat().coerceIn(-60f, 3f)
        } else -60f

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
        globalAudioPeakDb = -60f
        globalSoftClipActive = false
    }

    override fun reset() {
        flush()
        allocatedBuffer = AudioProcessor.EMPTY_BUFFER
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
        isProcessorActive = false
        floatBuffer = FloatArray(8192)
    }

    fun release() {
        JamesDspController.release()
    }
}