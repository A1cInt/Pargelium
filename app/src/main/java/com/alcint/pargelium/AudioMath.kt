package com.alcint.pargelium

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

var currentAudioLoudness by mutableFloatStateOf(0f)

data class EqPreset(val name: String, val gains: FloatArray)
object PresetManagerList {
    val presets = listOf(
        EqPreset("Flat", FloatArray(15) { 0f }),
        EqPreset("Bass Boost", floatArrayOf(6f, 5f, 4f, 3f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Rock", floatArrayOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 1f, 2f, 3f, 4f, 4f, 3f, 2f, 1f)),
        EqPreset("Pop", floatArrayOf(-1f, 2f, 3f, 4f, 4f, 3f, 1f, 0f, 1f, 2f, 3f, 3f, 2f, 1f, 1f)),
        EqPreset("Vocal", floatArrayOf(-3f, -2f, -1f, 0f, 1f, 3f, 4f, 4f, 3f, 2f, 1f, 0f, 0f, 0f, -1f)),
        EqPreset("Harman Target", floatArrayOf(5f, 5f, 4f, 2f, 0f, -1f, -1f, 0f, 1f, 2f, 3f, 4f, 5f, 3f, 0f))
    )
}
enum class FilterType { PEAKING, LOW_SHELF, HIGH_SHELF, HIGH_PASS, LOW_PASS }

class BiquadFilter(var frequency: Double, var sampleRate: Int, var type: FilterType = FilterType.PEAKING) {
    private var b0=0.0; private var b1=0.0; private var b2=0.0
    private var a1=0.0; private var a2=0.0
    private var x1=0.0; private var x2=0.0
    private var y1=0.0; private var y2=0.0
    private var currentDb = 0.0

    private var Q = 0.707

    private var b0f=0f; private var b1f=0f; private var b2f=0f
    private var a1f=0f; private var a2f=0f

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
        val w0 = 2.0 * PI * frequency / sampleRate
        val cosw0 = cos(w0)
        val sinw0 = sin(w0)
        val A = 10.0.pow(dbGain / 40.0)

        val alpha = sinw0 / (2.0 * Q)

        var a0 = 0.0

        when (type) {
            FilterType.PEAKING -> {
                b0 = 1.0 + alpha * A; b1 = -2.0 * cosw0; b2 = 1.0 - alpha * A
                a0 = 1.0 + alpha / A; a1 = -2.0 * cosw0; a2 = 1.0 - alpha / A
            }
            FilterType.LOW_SHELF -> {
                b0 = A*((A+1)-(A-1)*cosw0+2*sqrt(A)*alpha); b1 = 2*A*((A-1)-(A+1)*cosw0); b2 = A*((A+1)-(A-1)*cosw0-2*sqrt(A)*alpha)
                a0 = (A+1)+(A-1)*cosw0+2*sqrt(A)*alpha; a1 = -2*((A-1)+(A+1)*cosw0); a2 = (A+1)+(A-1)*cosw0-2*sqrt(A)*alpha
            }
            FilterType.HIGH_SHELF -> {
                b0 = A*((A+1)+(A-1)*cosw0+2*sqrt(A)*alpha); b1 = -2*A*((A-1)+(A+1)*cosw0); b2 = A*((A+1)+(A-1)*cosw0-2*sqrt(A)*alpha)
                a0 = (A+1)-(A-1)*cosw0+2*sqrt(A)*alpha; a1 = 2*((A-1)-(A+1)*cosw0); a2 = (A+1)-(A-1)*cosw0-2*sqrt(A)*alpha
            }
            FilterType.HIGH_PASS -> {
                b0 = (1 + cosw0) / 2; b1 = -(1 + cosw0); b2 = (1 + cosw0) / 2
                a0 = 1 + alpha; a1 = -2 * cosw0; a2 = 1 - alpha
            }
            FilterType.LOW_PASS -> {
                b0 = (1 - cosw0) / 2; b1 = 1 - cosw0; b2 = (1 - cosw0) / 2
                a0 = 1 + alpha; a1 = -2 * cosw0; a2 = 1 - alpha
            }
        }

        val invA0 = 1.0 / a0
        b0f = (b0 * invA0).toFloat(); b1f = (b1 * invA0).toFloat(); b2f = (b2 * invA0).toFloat()
        a1f = (a1 * invA0).toFloat(); a2f = (a2 * invA0).toFloat()
    }

    fun process(sample: Float): Float {
        val input = sample.toDouble()
        val output = b0f*input + b1f*x1 + b2f*x2 - a1f*y1 - a2f*y2

        val outClean = if (abs(output) < 1e-20) 0.0 else output

        x2=x1; x1=input; y2=y1; y1=outClean
        return outClean.toFloat()
    }
}

// Ревеобация
class DelayLine(val length: Int) {
    private val buffer = FloatArray(length)
    private var index = 0
    fun process(input: Float): Float {
        val output = buffer[index]
        buffer[index] = input
        index = (index + 1) % length
        return output
    }
    fun read(): Float = buffer[index]
    fun write(value: Float) { buffer[index] = value }
}

class CombFilter(val size: Int) {
    private val delay = DelayLine(size)
    private var feedback = 0.8f
    private var damping = 0.2f
    private var filterStore = 0f
    fun setFeedback(fb: Float) { feedback = fb }
    fun process(input: Float): Float {
        val output = delay.read()
        filterStore = (output * (1 - damping)) + (filterStore * damping)
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
        delay.write(input + delayed * feedback)
        return output
    }
}

class SchroederReverb(sampleRate: Int) {
    private val combs = listOf(
        CombFilter((sampleRate * 0.0297).toInt()),
        CombFilter((sampleRate * 0.0371).toInt()),
        CombFilter((sampleRate * 0.0411).toInt()),
        CombFilter((sampleRate * 0.0437).toInt())
    )
    private val allPass1 = AllPassFilter((sampleRate * 0.005).toInt())
    private val allPass2 = AllPassFilter((sampleRate * 0.0017).toInt())
    private var mixLevel = 0.0f

    fun setRoomSize(value: Int) {
        mixLevel = (value / 10f) * 0.4f
        val feedback = 0.7f + (value / 50f)
        combs.forEach { it.setFeedback(feedback) }
    }

    fun process(l: Float, r: Float, out: FloatArray) {
        if (mixLevel < 0.01f) { out[0] = l; out[1] = r; return }
        val input = (l + r) * 0.5f * 0.05f
        var wet = 0f
        for (comb in combs) wet += comb.process(input)
        wet = allPass1.process(wet)
        wet = allPass2.process(wet)
        out[0] = l + wet * mixLevel
        out[1] = r + wet * mixLevel
    }
}

// Тупой улучшатор
class SmartEnhancer(sampleRate: Int) {
    private val hpFilterLeft = BiquadFilter(3500.0, sampleRate, FilterType.HIGH_PASS)
    private val hpFilterRight = BiquadFilter(3500.0, sampleRate, FilterType.HIGH_PASS)
    private var enabled = false
    private var mix = 0.0f

    fun setEnabled(on: Boolean) {
        enabled = on
        mix = if (on) 0.15f else 0.0f
    }

    fun process(l: Float, r: Float, out: FloatArray) {
        if (!enabled) { out[0]=l; out[1]=r; return }
        val highL = hpFilterLeft.process(l)
        val highR = hpFilterRight.process(r)
        val exciterL = if (highL > 0) highL else 0f
        val exciterR = if (highR > 0) highR else 0f
        out[0] = l + exciterL * mix
        out[1] = r + exciterR * mix
    }
}

// мп3 восстановление (но мп3 уже ничего не поможет)
class Mp3Restorer(sampleRate: Int) {
    private var enabled = false
    private val hpLeft = BiquadFilter(3000.0, sampleRate, FilterType.HIGH_PASS)
    private val hpRight = BiquadFilter(3000.0, sampleRate, FilterType.HIGH_PASS)
    fun setEnabled(on: Boolean) { enabled = on }
    fun process(l: Float, r: Float, out: FloatArray) {
        if (!enabled) { out[0]=l; out[1]=r; return }
        val highL = hpLeft.process(l)
        val highR = hpRight.process(r)
        val harmL = highL * highL * sign(highL)
        val harmR = highR * highR * sign(highR)
        out[0] = l + harmL * 0.1f
        out[1] = r + harmR * 0.1f
    }
}

// Кросфид
class HeadphoneCrossfeed(sampleRate: Int) {
    private var enabled = false
    private val lpFilter = BiquadFilter(700.0, sampleRate, FilterType.LOW_PASS)
    fun setEnabled(on: Boolean) { enabled = on }
    fun process(l: Float, r: Float, out: FloatArray) {
        if (!enabled) { out[0]=l; out[1]=r; return }
        val crossL = lpFilter.process(l) * 0.25f
        val crossR = lpFilter.process(r) * 0.25f
        out[0] = l * 0.8f + crossR
        out[1] = r * 0.8f + crossL
    }
}

object DynamicsProcessor {
    fun softLimit(sample: Float): Float {
        if (sample < -1.0f) return -1.0f
        if (sample > 1.0f) return 1.0f
        return sample - (sample * sample * sample) / 3f
    }

    fun tubeDrive(sample: Float, enabled: Boolean): Float {
        if (!enabled) return sample
        val s = sample * 1.5f
        val q = s - 0.15f
        val driven = if (q > 0) 1.0f - exp(-q) else -1.0f + exp(q)
        return driven * 0.8f
    }
}