package com.alcint.pargelium

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

@Volatile
var currentAudioLoudness: Float = 0f

@Immutable
data class EqPreset(
    val name: String,
    val gains10: FloatArray,
    val gains15: FloatArray = gains10
)

object PresetManagerList {
    // 10 полос: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k
    val presets = listOf(
        EqPreset(
            name = "Flat",
            gains10 = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        ),
        EqPreset(
            name = "Harman Target",
            gains10 = floatArrayOf(5.5f, 4.5f, 2.0f, 0.0f, -1.0f, 0.5f, 2.5f, 4.0f, 3.0f, 1.0f)
        ),
        EqPreset(
            name = "Bass & Sub",
            gains10 = floatArrayOf(6.5f, 5.5f, 3.5f, 1.0f, 0.0f, 0.0f, 0.5f, 1.5f, 2.0f, 1.5f)
        ),
        EqPreset(
            name = "V-Shape Club",
            gains10 = floatArrayOf(5.0f, 4.5f, 2.0f, -1.5f, -2.5f, -1.0f, 2.0f, 4.0f, 5.0f, 4.5f)
        ),
        EqPreset(
            name = "Warm Tube & Vinyl",
            gains10 = floatArrayOf(3.0f, 3.5f, 2.5f, 1.5f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f)
        ),
        EqPreset(
            name = "Studio Vocal",
            gains10 = floatArrayOf(-2.5f, -1.5f, 0.0f, 1.5f, 3.5f, 4.5f, 3.5f, 2.0f, 1.0f, 0.0f)
        ),
        EqPreset(
            name = "Rock / Metal",
            gains10 = floatArrayOf(4.5f, 3.5f, 1.5f, -1.0f, -1.5f, 0.5f, 2.5f, 4.0f, 3.5f, 2.5f)
        ),
        EqPreset(
            name = "Pop & EDM",
            gains10 = floatArrayOf(4.0f, 4.5f, 3.0f, 0.5f, -0.5f, 1.0f, 2.5f, 3.5f, 4.0f, 3.0f)
        )
    )
}

enum class FilterType { PEAKING, LOW_SHELF, HIGH_SHELF, HIGH_PASS, LOW_PASS }

class BiquadFilter(var frequency: Double, var sampleRate: Int, var type: FilterType = FilterType.PEAKING) {
    private var currentDb = 0.0
    private var Q = 0.707

    private var b0f = 1f; private var b1f = 0f; private var b2f = 0f
    private var a1f = 0f; private var a2f = 0f

    private var x1 = 0f; private var x2 = 0f
    private var y1 = 0f; private var y2 = 0f

    init { calculateCoefficients(0.0) }

    fun setGain(db: Double) {
        if (abs(currentDb - db) < 0.05) return
        currentDb = db
        calculateCoefficients(db)
    }

    fun setQ(newQ: Double) {
        if (newQ <= 0.0) return
        if (abs(Q - newQ) < 0.01) return
        Q = newQ
        calculateCoefficients(currentDb)
    }

    private fun calculateCoefficients(dbGain: Double) {
        val safeSr = sampleRate.coerceAtLeast(8000)
        val safeFreq = frequency.coerceIn(10.0, safeSr * 0.48)
        val w0 = 2.0 * PI * safeFreq / safeSr
        val cosw0 = cos(w0)
        val sinw0 = sin(w0)
        val A = 10.0.pow(dbGain / 40.0)
        val alpha = sinw0 / (2.0 * Q)

        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0
        var a0 = 1.0; var a1 = 0.0; var a2 = 0.0

        when (type) {
            FilterType.PEAKING -> {
                b0 = 1.0 + alpha * A; b1 = -2.0 * cosw0; b2 = 1.0 - alpha * A
                a0 = 1.0 + alpha / A; a1 = -2.0 * cosw0; a2 = 1.0 - alpha / A
            }
            FilterType.LOW_SHELF -> {
                val sqrtA = 2.0 * sqrt(A) * alpha
                b0 = A * ((A + 1.0) - (A - 1.0) * cosw0 + sqrtA)
                b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosw0)
                b2 = A * ((A + 1.0) - (A - 1.0) * cosw0 - sqrtA)
                a0 = (A + 1.0) + (A - 1.0) * cosw0 + sqrtA
                a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cosw0)
                a2 = (A + 1.0) + (A - 1.0) * cosw0 - sqrtA
            }
            FilterType.HIGH_SHELF -> {
                val sqrtA = 2.0 * sqrt(A) * alpha
                b0 = A * ((A + 1.0) + (A - 1.0) * cosw0 + sqrtA)
                b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosw0)
                b2 = A * ((A + 1.0) + (A - 1.0) * cosw0 - sqrtA)
                a0 = (A + 1.0) - (A - 1.0) * cosw0 + sqrtA
                a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cosw0)
                a2 = (A + 1.0) - (A - 1.0) * cosw0 - sqrtA
            }
            FilterType.HIGH_PASS -> {
                b0 = (1.0 + cosw0) / 2.0; b1 = -(1.0 + cosw0); b2 = (1.0 + cosw0) / 2.0
                a0 = 1.0 + alpha; a1 = -2.0 * cosw0; a2 = 1.0 - alpha
            }
            FilterType.LOW_PASS -> {
                b0 = (1.0 - cosw0) / 2.0; b1 = 1.0 - cosw0; b2 = (1.0 - cosw0) / 2.0
                a0 = 1.0 + alpha; a1 = -2.0 * cosw0; a2 = 1.0 - alpha
            }
        }

        val invA0 = 1.0 / a0
        b0f = (b0 * invA0).toFloat()
        b1f = (b1 * invA0).toFloat()
        b2f = (b2 * invA0).toFloat()
        a1f = (a1 * invA0).toFloat()
        a2f = (a2 * invA0).toFloat()
    }

    fun process(sample: Float): Float {
        val output = b0f * sample + b1f * x1 + b2f * x2 - a1f * y1 - a2f * y2
        val outClean = if (output.isNaN() || abs(output) < 1e-18f) 0f else output

        x2 = x1
        x1 = sample
        y2 = y1
        y1 = outClean

        return outClean
    }
}

class DelayLine(val length: Int) {
    private val safeLen = length.coerceAtLeast(1)
    private val buffer = FloatArray(safeLen)
    private var index = 0

    fun process(input: Float): Float {
        val output = buffer[index]
        buffer[index] = input
        if (++index >= safeLen) index = 0
        return output
    }

    fun read(): Float = buffer[index]

    fun write(value: Float) {
        buffer[index] = value
        if (++index >= safeLen) index = 0
    }
}

class CombFilter(val size: Int) {
    private val delay = DelayLine(size)
    private var feedback = 0.78f
    private var damping = 0.25f
    private var filterStore = 0f

    fun setFeedback(fb: Float) { feedback = fb.coerceIn(0.1f, 0.92f) }

    fun process(input: Float): Float {
        val output = delay.read()
        filterStore = (output * (1f - damping)) + (filterStore * damping)
        if (abs(filterStore) < 1e-18f) filterStore = 0f
        delay.write(input + filterStore * feedback)
        return output
    }
}

class AllPassFilter(val size: Int) {
    private val delay = DelayLine(size)
    private val feedback = 0.5f

    fun process(input: Float): Float {
        val delayed = delay.read()
        val output = -input + delayed
        val next = input + delayed * feedback
        delay.write(if (abs(next) < 1e-18f) 0f else next)
        return output
    }
}

class SchroederReverb(sampleRate: Int) {
    private val combs = arrayOf(
        CombFilter((sampleRate * 0.0297).toInt()),
        CombFilter((sampleRate * 0.0371).toInt()),
        CombFilter((sampleRate * 0.0411).toInt()),
        CombFilter((sampleRate * 0.0437).toInt())
    )
    private val allPass1 = AllPassFilter((sampleRate * 0.005).toInt())
    private val allPass2 = AllPassFilter((sampleRate * 0.0017).toInt())
    private var mixLevel = 0.0f

    fun setRoomSize(value: Int) {
        val clamped = value.coerceIn(0, 10)
        mixLevel = (clamped / 10f) * 0.32f
        val feedback = 0.68f + (clamped / 55f)
        for (i in 0..3) {
            combs[i].setFeedback(feedback)
        }
    }

    fun process(l: Float, r: Float, out: FloatArray) {
        if (mixLevel < 0.005f) { out[0] = l; out[1] = r; return }

        val input = (l + r) * 0.02f
        var wet = 0f

        for (i in 0..3) {
            wet += combs[i].process(input)
        }

        wet = allPass1.process(wet)
        wet = allPass2.process(wet)

        val dryGain = 1.0f - mixLevel * 0.25f
        out[0] = l * dryGain + wet * mixLevel
        out[1] = r * dryGain + wet * mixLevel
    }
}

class SmartEnhancer(sampleRate: Int) {
    private val hpFilterLeft = BiquadFilter(3500.0, sampleRate, FilterType.HIGH_PASS)
    private val hpFilterRight = BiquadFilter(3500.0, sampleRate, FilterType.HIGH_PASS)
    private var enabled = false
    private var mix = 0.0f

    fun setEnabled(on: Boolean) {
        enabled = on
        mix = if (on) 0.12f else 0.0f
    }

    fun process(l: Float, r: Float, out: FloatArray) {
        if (!enabled) { out[0] = l; out[1] = r; return }

        val highL = hpFilterLeft.process(l)
        val highR = hpFilterRight.process(r)

        val exciterL = tanh((highL * 2.0f).toDouble()).toFloat()
        val exciterR = tanh((highR * 2.0f).toDouble()).toFloat()

        out[0] = (l * 0.94f) + exciterL * mix
        out[1] = (r * 0.94f) + exciterR * mix
    }
}

class Mp3Restorer(sampleRate: Int) {
    private var enabled = false
    private val hpLeft = BiquadFilter(3200.0, sampleRate, FilterType.HIGH_PASS)
    private val hpRight = BiquadFilter(3200.0, sampleRate, FilterType.HIGH_PASS)

    fun setEnabled(on: Boolean) { enabled = on }

    fun process(l: Float, r: Float, out: FloatArray) {
        if (!enabled) { out[0] = l; out[1] = r; return }

        val highL = hpLeft.process(l)
        val highR = hpRight.process(r)

        val harmL = highL * abs(highL)
        val harmR = highR * abs(highR)

        out[0] = (l * 0.96f) + harmL * 0.08f
        out[1] = (r * 0.96f) + harmR * 0.08f
    }
}

class HeadphoneCrossfeed(sampleRate: Int) {
    private var enabled = false
    private val lpFilterL = BiquadFilter(700.0, sampleRate, FilterType.LOW_PASS)
    private val lpFilterR = BiquadFilter(700.0, sampleRate, FilterType.LOW_PASS)

    fun setEnabled(on: Boolean) { enabled = on }

    fun process(l: Float, r: Float, out: FloatArray) {
        if (!enabled) { out[0] = l; out[1] = r; return }

        val crossL = lpFilterL.process(l) * 0.22f
        val crossR = lpFilterR.process(r) * 0.22f

        out[0] = l * 0.82f + crossR
        out[1] = r * 0.82f + crossL
    }
}

object DynamicsProcessor {
    fun softLimit(sample: Float): Float {
        if (sample.isNaN()) return 0f
        return tanh(sample.toDouble()).toFloat()
    }

    fun tubeDrive(sample: Float, enabled: Boolean): Float {
        if (!enabled) return sample
        val s = sample * 1.25f
        val q = s - 0.08f
        val driven = if (q > 0f) 1.0f - exp(-q) else -1.0f + exp(q)
        return (driven + 0.08f) * 0.78f
    }
}