package com.alcint.pargelium

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.pow

@Volatile
var globalAudioLoudness = 0f

@UnstableApi
class CustomAudioProcessor : AudioProcessor {
    private var inputFormat = AudioFormat.NOT_SET
    private var outputFormat = AudioFormat.NOT_SET
    private var isActive = false
    private var buffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    private class EqBand {
        var b0 = 1f; var b1 = 0f; var b2 = 0f
        var a1 = 0f; var a2 = 0f
        var x1L = 0f; var x2L = 0f; var y1L = 0f; var y2L = 0f
        var x1R = 0f; var x2R = 0f; var y1R = 0f; var y2R = 0f

        fun update(gainDb: Float, freq: Float, Q: Float, sampleRate: Float) {
            val A = 10.0.pow(gainDb.toDouble() / 40.0).toFloat()
            val w0 = 2f * PI.toFloat() * freq / sampleRate
            val alpha = sin(w0.toDouble()).toFloat() / (2f * Q)
            val a0 = 1f + alpha / A
            b0 = (1f + alpha * A) / a0
            b1 = (-2f * cos(w0.toDouble())).toFloat() / a0
            b2 = (1f - alpha * A) / a0
            a1 = (-2f * cos(w0.toDouble())).toFloat() / a0
            a2 = (1f - alpha / A) / a0
        }

        @Suppress("NOTHING_TO_INLINE")
        inline fun processL(x: Float): Float {
            val y = b0 * x + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
            x2L = x1L; x1L = x; y2L = y1L; y1L = y
            return y
        }

        @Suppress("NOTHING_TO_INLINE")
        inline fun processR(x: Float): Float {
            val y = b0 * x + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
            x2R = x1R; x1R = x; y2R = y1R; y1R = y
            return y
        }
    }

    private val autoEqFreqs = floatArrayOf(25f, 40f, 63f, 100f, 160f, 250f, 400f, 630f, 1000f, 1600f, 2500f, 4000f, 6300f, 10000f, 16000f)
    private val autoEqBands = Array(15) { EqBand() }
    private val lastAutoEqGains = FloatArray(15) { -999f }

    private val userEqFreqs = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private val userEqBands = Array(10) { EqBand() }
    private val lastUserEqGains = FloatArray(10) { -999f }

    private val activeEqBands = Array<EqBand?>(25) { null }
    private var activeEqBandsCount = 0

    private val bassBiquad = EqBand()
    private var lastBassGain = -1f
    private var lastBassFreq = -1f

    private var lastSampleRate = -1
    private var lastReverbMode = -1

    private var settingsUpdateCounter = 0
    private var cachedEnabled = false
    private var cachedAutoEqEnabled = false
    private var cachedUserEqEnabled = false
    private var cachedBassEnabled = false
    private var cachedHaas = false
    private var cachedRoom = false
    private var cachedSpatializer = false
    private var cachedTube = false
    private var cachedCrossfeed = false
    private var cachedExciter = false
    private var cachedBassGain = 0f
    private var cachedBassFreq = 0f
    private var cachedHaasDelay = 0f
    private var cachedReverbMix = 0f
    private var cachedReverbSize = 0f
    private var cachedReverbDamp = 0f
    private var cachedSpatialWidth = 0f
    private var cachedExciterInt = 0f
    private var cachedAutoEqGains = FloatArray(15)
    private var cachedUserEqGains = FloatArray(10)

    private class CombFilter(val size: Int) {
        val buffer = FloatArray(size)
        var idx = 0
        var store = 0f

        @Suppress("NOTHING_TO_INLINE")
        inline fun process(input: Float, damp1: Float, damp2: Float, feedback: Float): Float {
            val out = buffer[idx]
            store = (out * damp1) + (store * damp2)
            buffer[idx] = input + (store * feedback)
            if (++idx >= size) idx = 0
            return out
        }
    }

    private class AllpassFilter(val size: Int) {
        val buffer = FloatArray(size)
        var idx = 0

        @Suppress("NOTHING_TO_INLINE")
        inline fun process(input: Float): Float {
            val bufOut = buffer[idx]
            val out = -input + bufOut
            buffer[idx] = input + (bufOut * 0.5f)
            if (++idx >= size) idx = 0
            return out
        }
    }

    private var combsL = emptyArray<CombFilter>()
    private var combsR = emptyArray<CombFilter>()
    private var allpassesL = emptyArray<AllpassFilter>()
    private var allpassesR = emptyArray<AllpassFilter>()

    private val haasBuffer = FloatArray(48000)
    private var haasIndex = 0
    private var reverbHpState = 0f

    private fun initReverb(sampleRate: Int, mode: Int) {
        val scale = sampleRate / 44100f
        var cL: IntArray; var aL: IntArray; var stereoSpread: Int

        when (mode) {
            1 -> { cL = intArrayOf(556, 588, 631, 678, 714, 743, 778, 809); aL = intArrayOf(112, 170, 223, 278); stereoSpread = 12 }
            2 -> { cL = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617); aL = intArrayOf(225, 341, 441, 556); stereoSpread = 23 }
            3 -> { cL = intArrayOf(1536, 1611, 1713, 1823, 1922, 2031, 2144, 2267); aL = intArrayOf(313, 461, 593, 751); stereoSpread = 46 }
            4 -> { cL = intArrayOf(2341, 2467, 2633, 2811, 2963, 3137, 3307, 3491); aL = intArrayOf(479, 691, 887, 1123); stereoSpread = 86 }
            5 -> { cL = intArrayOf(4463, 4751, 5107, 5419, 5689, 5981, 6263, 6491); aL = intArrayOf(911, 1361, 1777, 2239); stereoSpread = 180 }
            else -> { cL = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617); aL = intArrayOf(225, 341, 441, 556); stereoSpread = 23 }
        }

        combsL = Array(8) { CombFilter((cL[it] * scale).toInt().coerceAtLeast(1)) }
        combsR = Array(8) { CombFilter(((cL[it] + stereoSpread) * scale).toInt().coerceAtLeast(1)) }
        allpassesL = Array(4) { AllpassFilter((aL[it] * scale).toInt().coerceAtLeast(1)) }
        allpassesR = Array(4) { AllpassFilter(((aL[it] + stereoSpread) * scale).toInt().coerceAtLeast(1)) }
    }

    private var exciteL = 0f
    private var exciteR = 0f

    private fun checkPrefs() {
        cachedEnabled = PrefsManager.getEnabled()
        if (!cachedEnabled) return

        cachedAutoEqEnabled = PrefsManager.getAutoEqEnabled()
        cachedUserEqEnabled = PrefsManager.getUserEqEnabled()
        cachedBassEnabled = PrefsManager.getBassEnabled()
        cachedHaas = PrefsManager.getHaas()
        cachedRoom = PrefsManager.getRoom()
        cachedSpatializer = PrefsManager.getSpatializer()
        cachedTube = PrefsManager.getTube()
        cachedCrossfeed = PrefsManager.getCrossfeed()
        cachedExciter = PrefsManager.getMp3Restorer()

        cachedBassGain = PrefsManager.getBass().toFloat()
        cachedBassFreq = PrefsManager.getBassFreq().toFloat()
        cachedHaasDelay = PrefsManager.getHaasDelay().toFloat()
        cachedReverbMix = PrefsManager.getReverbMix().toFloat()
        cachedReverbSize = PrefsManager.getReverbSize().toFloat()
        cachedReverbDamp = PrefsManager.getReverbDamp().toFloat()
        cachedSpatialWidth = PrefsManager.getSpatialWidth().toFloat()
        cachedExciterInt = PrefsManager.getExciterIntensity().toFloat()

        if (cachedAutoEqEnabled) cachedAutoEqGains = PrefsManager.getEqGains()
        if (cachedUserEqEnabled) cachedUserEqGains = PrefsManager.getUserEqGains()
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
        if (size == 0) return

        val resultBuffer = replaceOutputBuffer(size)

        if (settingsUpdateCounter++ % 20 == 0) {
            checkPrefs()
        }

        if (!cachedEnabled) {
            var peak = 0f
            while (inputBuffer.hasRemaining()) {
                val sample = inputBuffer.short
                resultBuffer.putShort(sample)
                val fSample = sample.toFloat() * 0.000030517578f
                val absSample = if (fSample < 0) -fSample else fSample
                if (absSample > peak) peak = absSample
            }
            resultBuffer.flip()
            globalAudioLoudness = globalAudioLoudness * 0.8f + peak * 0.2f
            return
        }

        val sampleRate = inputFormat.sampleRate
        val reverbMode = PrefsManager.getReverbMode()

        if (sampleRate != lastSampleRate || reverbMode != lastReverbMode) {
            lastSampleRate = sampleRate
            lastReverbMode = reverbMode
            initReverb(sampleRate, reverbMode)
            lastAutoEqGains.fill(-999f)
            lastUserEqGains.fill(-999f)
            lastBassGain = -1f
            reverbHpState = 0f
        }

        activeEqBandsCount = 0

        if (cachedAutoEqEnabled) {
            for (i in 0..14) {
                if (cachedAutoEqGains[i] != lastAutoEqGains[i]) {
                    lastAutoEqGains[i] = cachedAutoEqGains[i]
                    autoEqBands[i].update(cachedAutoEqGains[i], autoEqFreqs[i], 1.41f, sampleRate.toFloat())
                }
                if (cachedAutoEqGains[i] != 0f) activeEqBands[activeEqBandsCount++] = autoEqBands[i]
            }
        }

        if (cachedUserEqEnabled) {
            for (i in 0..9) {
                if (cachedUserEqGains[i] != lastUserEqGains[i]) {
                    lastUserEqGains[i] = cachedUserEqGains[i]
                    userEqBands[i].update(cachedUserEqGains[i], userEqFreqs[i], 1.41f, sampleRate.toFloat())
                }
                if (cachedUserEqGains[i] != 0f) activeEqBands[activeEqBandsCount++] = userEqBands[i]
            }
        }

        val channels = inputFormat.channelCount
        val isStereo = channels == 2

        val doBass = cachedBassEnabled && cachedBassGain > 0f
        if (doBass) {
            if (cachedBassGain != lastBassGain || cachedBassFreq != lastBassFreq) {
                lastBassGain = cachedBassGain
                lastBassFreq = cachedBassFreq
                bassBiquad.update(cachedBassGain * 0.15f, cachedBassFreq, 0.707f, sampleRate.toFloat())
            }
        }

        val doHaas = cachedHaas && isStereo
        val haasDelaySamples = if (doHaas) (sampleRate * (cachedHaasDelay * 0.001f)).toInt() else 0
        val haasBufSize = haasBuffer.size

        val mix = cachedReverbMix * 0.01f
        val doRoom = cachedRoom && combsL.isNotEmpty() && mix > 0.01f
        val revRoom = cachedReverbSize * 0.0028f + 0.7f
        val revDamp2 = cachedReverbDamp * 0.005f
        val revDamp1 = 1f - revDamp2
        val revWet = mix * 1.5f
        val revDry = 1f - (mix * 0.5f)

        val spatialWidth = cachedSpatialWidth * 0.02f
        val doSpatializer = cachedSpatializer && isStereo && spatialWidth > 0f

        val exciterInt = cachedExciterInt * 0.01f

        var bufferPeak = 0f
        val inv32768 = 1f / 32768f

        while (inputBuffer.hasRemaining()) {
            var inL = inputBuffer.short.toFloat() * inv32768
            var inR = if (isStereo) inputBuffer.short.toFloat() * inv32768 else inL

            for (i in 0 until activeEqBandsCount) {
                val band = activeEqBands[i]!!
                inL = band.processL(inL)
                inR = band.processR(inR)
            }

            if (doBass) {
                inL = bassBiquad.processL(inL)
                inR = bassBiquad.processR(inR)
            }

            if (doHaas) {
                haasBuffer[haasIndex] = inR
                var readIndex = haasIndex - haasDelaySamples
                if (readIndex < 0) readIndex += haasBufSize
                inR = haasBuffer[readIndex]
                if (++haasIndex >= haasBufSize) haasIndex = 0
            }

            if (cachedExciter) {
                exciteL += 0.1f * (inL - exciteL)
                exciteR += 0.1f * (inR - exciteR)
                val hL = inL - exciteL
                val hR = inR - exciteR
                inL += hL * (if (hL < 0) -hL else hL) * exciterInt
                inR += hR * (if (hR < 0) -hR else hR) * exciterInt
            }

            if (doSpatializer) {
                val mid = (inL + inR) * 0.5f
                val side = (inL - inR) * 0.5f
                val sideBoost = side * (1f + spatialWidth)
                inL = mid + sideBoost
                inR = mid - sideBoost
            }

            if (cachedCrossfeed) {
                val oldL = inL
                inL = oldL * 0.8f + inR * 0.2f
                inR = inR * 0.8f + oldL * 0.2f
            }

            if (cachedTube) {
                val absL = if (inL < 0) -inL else inL
                val absR = if (inR < 0) -inR else inR
                val factorL = 1f / (1f + absL)
                val factorR = 1f / (1f + absR)
                inL = (if (inL > 0) inL * 2f else inL * 1.5f) * factorL * 0.75f
                inR = (if (inR > 0) inR * 2f else inR * 1.5f) * factorR * 0.75f
            }

            if (doRoom) {
                val monoMix = (inL + inR) * 0.5f
                reverbHpState += 0.05f * (monoMix - reverbHpState)
                val revIn = (monoMix - reverbHpState) * 0.03f

                var outL = 0f
                var outR = 0f

                outL += combsL[0].process(revIn, revDamp1, revDamp2, revRoom)
                outL += combsL[1].process(revIn, revDamp1, revDamp2, revRoom)
                outL += combsL[2].process(revIn, revDamp1, revDamp2, revRoom)
                outL += combsL[3].process(revIn, revDamp1, revDamp2, revRoom)
                outL += combsL[4].process(revIn, revDamp1, revDamp2, revRoom)
                outL += combsL[5].process(revIn, revDamp1, revDamp2, revRoom)
                outL += combsL[6].process(revIn, revDamp1, revDamp2, revRoom)
                outL += combsL[7].process(revIn, revDamp1, revDamp2, revRoom)

                outR += combsR[0].process(revIn, revDamp1, revDamp2, revRoom)
                outR += combsR[1].process(revIn, revDamp1, revDamp2, revRoom)
                outR += combsR[2].process(revIn, revDamp1, revDamp2, revRoom)
                outR += combsR[3].process(revIn, revDamp1, revDamp2, revRoom)
                outR += combsR[4].process(revIn, revDamp1, revDamp2, revRoom)
                outR += combsR[5].process(revIn, revDamp1, revDamp2, revRoom)
                outR += combsR[6].process(revIn, revDamp1, revDamp2, revRoom)
                outR += combsR[7].process(revIn, revDamp1, revDamp2, revRoom)

                outL = allpassesL[0].process(outL)
                outL = allpassesL[1].process(outL)
                outL = allpassesL[2].process(outL)
                outL = allpassesL[3].process(outL)

                outR = allpassesR[0].process(outR)
                outR = allpassesR[1].process(outR)
                outR = allpassesR[2].process(outR)
                outR = allpassesR[3].process(outR)

                val absOutL = if (outL < 0) -outL else outL
                val absOutR = if (outR < 0) -outR else outR
                outL *= 1f / (1f + absOutL)
                outR *= 1f / (1f + absOutR)

                inL = (inL * revDry) + (outL * revWet)
                inR = (inR * revDry) + (outR * revWet)
            }

            inL = softClip(inL)
            inR = softClip(inR)

            val absInL = if (inL < 0) -inL else inL
            val absInR = if (inR < 0) -inR else inR
            val samplePeak = if (absInL > absInR) absInL else absInR
            if (samplePeak > bufferPeak) bufferPeak = samplePeak

            var outLInt = (inL * 32767f).toInt()
            if (outLInt > 32767) outLInt = 32767 else if (outLInt < -32768) outLInt = -32768
            resultBuffer.putShort(outLInt.toShort())

            if (isStereo) {
                var outRInt = (inR * 32767f).toInt()
                if (outRInt > 32767) outRInt = 32767 else if (outRInt < -32768) outRInt = -32768
                resultBuffer.putShort(outRInt.toShort())
            }
        }

        globalAudioLoudness = globalAudioLoudness * 0.7f + bufferPeak * 0.3f
        if (globalAudioLoudness.isNaN()) globalAudioLoudness = 0f

        resultBuffer.flip()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun softClip(valIn: Float): Float {
        var x = valIn
        if (x > 1.3f) x = 1.3f else if (x < -1.3f) x = -1.3f
        x -= 0.15f * x * x * x
        if (x > 1f) return 1f
        if (x < -1f) return -1f
        return x
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
        reverbHpState = 0f

        if (combsL.isNotEmpty()) {
            for(i in 0..7) { combsL[i].buffer.fill(0f); combsR[i].buffer.fill(0f); combsL[i].store = 0f; combsR[i].store = 0f }
            for(i in 0..3) { allpassesL[i].buffer.fill(0f); allpassesR[i].buffer.fill(0f) }
        }

        haasBuffer.fill(0f)
        haasIndex = 0
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
        lastSampleRate = -1
    }
}