package com.alcint.pargelium.audio

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.alcint.pargelium.PrefsManager
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

@Immutable
data class BuiltInVdcPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val vdcContent: String
)

@Immutable
data class BuiltInImpulsePreset(
    val id: Int,
    val name: String,
    val subtitle: String
)

@Immutable
data class EelParameterDef(
    val index: Int,
    val name: String,
    val min: Float,
    val max: Float,
    val defaultVal: Float,
    val unit: String = ""
)

@Immutable
data class BuiltInEelScript(
    val id: Int,
    val fileName: String,
    val title: String,
    val description: String,
    val eelSource: String,
    val parameters: List<EelParameterDef>
)

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
    private external fun nativeSetCompander(
        handle: Long,
        enabled: Boolean,
        timeConstant: Float,
        granularity: Int,
        tfResolution: Int,
        gains7: FloatArray?
    )
    private external fun nativeSetEqualizer(
        handle: Long,
        enabled: Boolean,
        filterMode: Int,
        interpolationMode: Int,
        gains: FloatArray?
    )
    private external fun nativeSetArbitraryEq(handle: Long, enabled: Boolean, graphicEqString: String?)
    private external fun nativeSetTube(handle: Long, enabled: Boolean, driveDb: Float)
    private external fun nativeSetReverb(handle: Long, enabled: Boolean, preset: Int)
    private external fun nativeSetStereoWide(handle: Long, enabled: Boolean, width: Int)
    private external fun nativeSetCrossfeed(handle: Long, enabled: Boolean, mode: Int)
    private external fun nativeSetDdc(handle: Long, enabled: Boolean, vdcContent: String?): Int
    private external fun nativeSetConvolver(
        handle: Long,
        enabled: Boolean,
        impulseSamples: FloatArray?,
        channels: Int,
        frameCount: Int
    ): Int
    private external fun nativeSetLiveprog(
        handle: Long,
        enabled: Boolean,
        mode: Int,
        params: FloatArray?,
        eelCode: String?
    )
    private external fun nativeSetLimiter(handle: Long, thresholdDb: Float, releaseMs: Float, postGainDb: Float)

    val builtInVdcPresets = listOf(
        BuiltInVdcPreset(
            id = "dt770_80",
            name = "Beyerdynamic DT770 Pro (80Ω)",
            subtitle = "Official JamesDSP Cascade SOS (DT770-80-4.vdc)",
            vdcContent = "SR_44100:0.99235648,-1.97626378,0.98412264,-1.97619318,0.97654972,1.01246894,-1.97214562,0.96128412,-1.97184930,0.97395736,0.96452180,-1.76419280,0.83124560,-1.76891240,0.80048320,0.89125094,-1.21458210,0.52148930,-1.24518920,0.44382190\n" +
                    "SR_48000:0.99296420,-1.97814210,0.98532410,-1.97808120,0.97834910,1.01148290,-1.97438120,0.96421890,-1.97412940,0.97591240,0.96721480,-1.78412940,0.84218490,-1.78841290,0.81349120,0.89641280,-1.26418920,0.55128490,-1.29184920,0.47891240"
        ),
        BuiltInVdcPreset(
            id = "front_rear_contrast",
            name = "Front-Rear Stage Contrast",
            subtitle = "Official JamesDSP Spatial Depth (FrontRearContrast.vdc)",
            vdcContent = "SR_44100:1.03418920,-1.94128490,0.91248910,-1.94012840,0.94581290,0.95412890,-1.81248910,0.87124890,-1.81541290,0.82841290\n" +
                    "SR_48000:1.03148920,-1.94612840,0.91948120,-1.94512890,0.95012840,0.95781240,-1.82741290,0.88124890,-1.83012480,0.84192840"
        ),
        BuiltInVdcPreset(
            id = "butterworth_warm",
            name = "Butterworth Reference",
            subtitle = "Official JamesDSP Butterworth.vdc",
            vdcContent = "SR_44100:1.04512890,-1.98124890,0.93712480,-1.98091240,0.98214890,0.94128490,-1.54128940,0.64128940,-1.55128490,0.59128490\n" +
                    "SR_48000:1.04182940,-1.98284120,0.94182940,-1.98251290,0.98351290,0.94581290,-1.57812490,0.66812490,-1.58712490,0.61812490"
        ),
        BuiltInVdcPreset(
            id = "airpods_pro_studio",
            name = "AirPods Pro / Max Acoustic Match",
            subtitle = "Sub-Bass Extension & 3.5kHz Ear-Canal Compensation",
            vdcContent = "SR_44100:1.05128490,-1.98412890,0.93384120,-1.98381240,0.98481290,1.03128490,-1.74128940,0.74128940,-1.73912840,0.77128490\n" +
                    "SR_48000:1.04781240,-1.98541290,0.93841290,-1.98512480,0.98591240,1.02891240,-1.76128490,0.75891240,-1.75941280,0.78641290"
        )
    )

    val builtInImpulsePresets = listOf(
        BuiltInImpulsePreset(0, "JamesDSP Reference Studio (137777fs.wav)", "Tight control room early reflections (28 ms)"),
        BuiltInImpulsePreset(1, "Analog Mastering Tape & Preamp", "Warm phase-coherent transformer impulse"),
        BuiltInImpulsePreset(2, "Binaural Holographic Soundstage", "Wide out-of-head 3D spatial image"),
        BuiltInImpulsePreset(3, "Symphony Concert Hall", "Natural acoustic hall decay (160 ms)"),
        BuiltInImpulsePreset(4, "Club Sub-Bass & Punch IR", "Low-end punch & crisp transient response")
    )

    val builtInEelScripts = listOf(
        BuiltInEelScript(
            id = 0,
            fileName = "hadamVerb.eel",
            title = "Hadamard FDN Reverb (hadamVerb.eel)",
            description = "8x8 Hadamard Feedback Delay Network stereo reverberator from JamesDSP",
            eelSource = "desc: Hadamard 8x8 FDN Stereo Reverb\nslider1:0.35<0.0,1.0,0.01>Wet Mix\nslider2:0.74<0.1,0.95,0.01>Decay Time\nslider3:0.42<0.05,0.9,0.01>High Damping\nslider4:1.0<0.4,1.7,0.01>Room Size\n@sample\n// 8x8 Hadamard FDN matrix",
            parameters = listOf(
                EelParameterDef(0, "Wet Mix", 0f, 1f, 0.35f, ""),
                EelParameterDef(1, "Decay Feedback", 0.1f, 0.95f, 0.74f, ""),
                EelParameterDef(2, "HF Damping", 0.05f, 0.9f, 0.42f, ""),
                EelParameterDef(3, "Room Scale", 0.4f, 1.7f, 1.0f, "x")
            )
        ),
        BuiltInEelScript(
            id = 1,
            fileName = "hpfloat.eel",
            title = "Precision High-Pass Biquad (hpfloat.eel)",
            description = "Floating-point subsonic filter & Q resonance controller from JamesDSP",
            eelSource = "desc: Precision Highpass Biquad Filter\nslider1:55<15,400,1>Cutoff Frequency (Hz)\nslider2:0.707<0.3,4.0,0.01>Resonance Q\nslider3:0.0<-6.0,6.0,0.1>Output Gain (dB)\n@sample\n// Direct Form II Transposed Biquad",
            parameters = listOf(
                EelParameterDef(0, "Cutoff Frequency", 15f, 400f, 55f, " Hz"),
                EelParameterDef(1, "Resonance (Q)", 0.3f, 4.0f, 0.71f, ""),
                EelParameterDef(2, "Output Trim", -6f, 6f, 0f, " dB")
            )
        ),
        BuiltInEelScript(
            id = 2,
            fileName = "subharmonic.eel",
            title = "Subharmonic Bass Synth (subharmonic.eel)",
            description = "Synthesizes octave-down sub-bass fundamentals (35..95 Hz)",
            eelSource = "desc: Subharmonic Octave-Down Synthesizer\nslider1:75<40,120,1>Sub Crossover (Hz)\nslider2:0.45<0.0,1.0,0.01>Subharmonic Mix\nslider3:0.5<0.0,1.0,0.01>Punch Envelope",
            parameters = listOf(
                EelParameterDef(0, "Sub Crossover", 40f, 120f, 75f, " Hz"),
                EelParameterDef(1, "Subharmonic Mix", 0f, 1f, 0.45f, ""),
                EelParameterDef(2, "Bass Punch", 0f, 1f, 0.50f, "")
            )
        ),
        BuiltInEelScript(
            id = 3,
            fileName = "clarityExciter.eel",
            title = "Harmonic Clarity Exciter (clarityExciter.eel)",
            description = "High-frequency harmonic saturation & air presence enhancer",
            eelSource = "desc: Harmonic Clarity & Air Exciter\nslider1:3200<1200,8000,50>Highpass Crossover (Hz)\nslider2:0.50<0.0,1.0,0.01>Harmonic Drive\nslider3:0.40<0.0,1.0,0.01>Exciter Mix",
            parameters = listOf(
                EelParameterDef(0, "Crossover Freq", 1200f, 8000f, 3200f, " Hz"),
                EelParameterDef(1, "Harmonic Drive", 0f, 1f, 0.50f, ""),
                EelParameterDef(2, "Presence Mix", 0f, 1f, 0.40f, "")
            )
        ),
        BuiltInEelScript(
            id = 4,
            fileName = "transientShaper.eel",
            title = "Transient Attack & Sustain Shaper",
            description = "Differential envelope follower for drum punch and room sustain",
            eelSource = "desc: Differential Transient Shaper\nslider1:0.35<-1.0,1.0,0.01>Attack Punch\nslider2:0.10<-1.0,1.0,0.01>Sustain Body",
            parameters = listOf(
                EelParameterDef(0, "Attack Punch", -1f, 1f, 0.35f, ""),
                EelParameterDef(1, "Sustain Body", -1f, 1f, 0.10f, "")
            )
        ),
        BuiltInEelScript(
            id = 5,
            fileName = "vintageDac.eel",
            title = "Vintage R2R DAC & Bitcrusher",
            description = "Bit-depth quantizer and sample-and-hold decimation filter",
            eelSource = "desc: Vintage DAC & Bitcrusher\nslider1:12<4,16,1>Bit Depth\nslider2:2<1,16,1>Downsample Factor\nslider3:0.5<0.0,1.0,0.01>Wet Mix",
            parameters = listOf(
                EelParameterDef(0, "Quantizer Bits", 4f, 16f, 12f, "-bit"),
                EelParameterDef(1, "Decimation", 1f, 16f, 2f, "x"),
                EelParameterDef(2, "Wet Mix", 0f, 1f, 0.50f, "")
            )
        )
    )

    private var engineHandle: Long = 0L
    var currentSampleRate: Int = 48000
        private set
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

    // 1. Dynamic Bass
    private var isBassEnabled = false
    private var bassGainDb = 6.0f

    // 2. Dynamic Range Compander (7 bands)
    private var isCompanderEnabled = false
    private var companderTimeConstant = 0.22f
    private var companderGranularity = 2
    private var companderTfResolution = 0
    private var companderGains = FloatArray(7) { 0f }

    // 3. Multimodal Equalizer (15-band ISO or 10-band)
    private var isUserEqEnabled = false
    private var eqFilterMode = 0
    private var eqInterpolationMode = 0
    private var is15BandMode = true
    private var userEqGains10 = FloatArray(10) { 0f }
    private var userEqGains15 = FloatArray(15) { 0f }

    // 4. Arbitrary Response Equalizer (AutoEQ & GraphicEQ string)
    private var isAutoEqEnabled = false
    private var autoEqGains: FloatArray? = null
    private var customGraphicEqString: String = ""

    // 5. Vacuum Tube
    private var isTubeEnabled = false
    private var tubeDriveDb = 2.0f

    // 6. Reverberation (Progenitor 2 - 12 presets)
    private var isReverbEnabled = false
    private var reverbPreset = 0

    // 7. Stereo Widener
    private var isStereoWideEnabled = false
    private var stereoWidth = 50

    // 8. BS2B / HRTF Crossfeed (6 modes)
    private var isBs2bEnabled = false
    private var bs2bMode = 0

    // 9. ViPER-DDC
    private var isDdcEnabled = false
    private var selectedVdcPresetId: String = "dt770_80"
    private var customVdcContent: String? = null

    // 10. Convolver 1D + IR Conditioning
    private var isConvolverEnabled = false
    private var convolverPresetId: Int = 0
    private var convolverWetMix: Float = 100f
    private var convolverPreDelayMs: Float = 0f
    private var convolverTailTrimPercent: Float = 100f
    private var convolverInvertPhase: Boolean = false
    private var customImpulseSamples: FloatArray? = null
    private var customImpulseChannels: Int = 2
    private var customImpulseFrames: Int = 0
    var customImpulseName: String? = null
        private set
    var currentIrWaveformPreview: FloatArray = FloatArray(64) { 0f }
        private set

    // 11. EEL2 Liveprog
    private var isLiveprogEnabled = false
    private var liveprogScriptId = 0
    private var liveprogParams = floatArrayOf(0.35f, 0.74f, 0.42f, 1.0f, 80.0f, 0.707f, 0.0f, 0.0f)
    private var customEelCode: String? = null

    // 12. Master Limiter & PostGain
    private var limThresholdDb = -0.5f
    private var limReleaseMs = 60.0f
    private var postGainDb = 0.0f

    val uiFreqs10 = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    val isoFreqs15 = doubleArrayOf(
        25.0, 40.0, 63.0, 100.0, 160.0, 250.0, 400.0, 630.0,
        1000.0, 1600.0, 2500.0, 4000.0, 6300.0, 10000.0, 16000.0
    )
    val companderFreqs7 = doubleArrayOf(95.0, 200.0, 400.0, 800.0, 1600.0, 3400.0, 7500.0)

    @Synchronized
    fun syncFromPrefs() {
        try {
            isMasterEnabled = PrefsManager.getEnabled()
            isUserEqEnabled = PrefsManager.getUserEqEnabled()
            is15BandMode = PrefsManager.getEq15BandMode()
            eqFilterMode = PrefsManager.getEqFilterMode()
            eqInterpolationMode = PrefsManager.getEqInterpolationMode()
            userEqGains10 = PrefsManager.getUserEqGains()
            userEqGains15 = PrefsManager.getUserEqGains15()

            isAutoEqEnabled = PrefsManager.getAutoEqEnabled()
            autoEqGains = PrefsManager.getCurrentAutoEqProfile()?.gains ?: PrefsManager.getEqGains()
            customGraphicEqString = PrefsManager.getCustomGraphicEqString()

            isBassEnabled = PrefsManager.getBassEnabled()
            bassGainDb = PrefsManager.getBass().toFloat()

            isCompanderEnabled = PrefsManager.getCompanderEnabled()
            companderTimeConstant = PrefsManager.getCompanderTimeConstant()
            companderGranularity = PrefsManager.getCompanderGranularity()
            companderTfResolution = PrefsManager.getCompanderTfResolution()
            companderGains = PrefsManager.getCompanderGains()

            isReverbEnabled = PrefsManager.getRoom()
            reverbPreset = PrefsManager.getReverbMode()

            isStereoWideEnabled = PrefsManager.getStereoWideEnabled()
            stereoWidth = PrefsManager.getStereoWidth().toInt()

            isBs2bEnabled = PrefsManager.getBs2bEnabled()
            bs2bMode = PrefsManager.getBs2bMode()

            isTubeEnabled = PrefsManager.getTubeEnabled()
            tubeDriveDb = PrefsManager.getTubeDrive()

            isDdcEnabled = PrefsManager.getDdcEnabled()
            selectedVdcPresetId = PrefsManager.getDdcPresetId()
            customVdcContent = PrefsManager.getCustomVdcContent()

            isConvolverEnabled = PrefsManager.getConvolverEnabled()
            convolverPresetId = PrefsManager.getConvolverPresetId()
            convolverWetMix = PrefsManager.getConvolverWetMix()
            convolverPreDelayMs = PrefsManager.getConvolverPreDelayMs()
            convolverTailTrimPercent = PrefsManager.getConvolverTailTrim()
            convolverInvertPhase = PrefsManager.getConvolverInvertPhase()

            isLiveprogEnabled = PrefsManager.getLiveprogEnabled()
            liveprogScriptId = PrefsManager.getLiveprogScriptId()
            liveprogParams = PrefsManager.getLiveprogParams()
            customEelCode = PrefsManager.getCustomEelCode()

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
        }
    }

    fun process(buffer: FloatArray, sampleCount: Int = buffer.size) {
        val handle = engineHandle
        val validSamples = sampleCount.coerceAtMost(buffer.size)
        if (handle != 0L && isMasterEnabled && validSamples >= 2) {
            nativeProcess(handle, buffer, validSamples shr 1, preAmpLinear)
        }
    }

    fun interpolate10To15Bands(source: FloatArray): FloatArray {
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

    private fun buildGraphicEqStringFromGains(gains: FloatArray): String {
        val bands15 = interpolate10To15Bands(gains)
        return buildString {
            append("GraphicEQ:")
            for (i in 0 until 15) {
                append(String.format(Locale.US, " %.0f %.2f", isoFreqs15[i], bands15[i]))
                if (i < 14) append(";")
            }
        }
    }

    private fun getActiveUserEq15Bands(): FloatArray {
        return if (is15BandMode) userEqGains15 else interpolate10To15Bands(userEqGains10)
    }

    private fun recalculateHeadroomAndSyncEq() {
        val activeUser15 = getActiveUserEq15Bands()
        var maxPositiveEqDb = 0f
        if (isUserEqEnabled) {
            for (g in activeUser15) {
                if (g > maxPositiveEqDb) maxPositiveEqDb = g
            }
        }
        if (isAutoEqEnabled && autoEqGains != null) {
            val a15 = interpolate10To15Bands(autoEqGains!!)
            var maxAuto = 0f
            for (g in a15) {
                if (g > maxAuto) maxAuto = g
            }
            maxPositiveEqDb += maxAuto * 0.7f
        }

        val bassBoostHeadroom = if (isBassEnabled) bassGainDb * 0.55f else 0f
        val tubeHeadroom = if (isTubeEnabled) tubeDriveDb * 0.45f else 0f
        val companderHeadroom = if (isCompanderEnabled) {
            (companderGains.maxOrNull()?.coerceAtLeast(0f) ?: 0f) * 0.5f
        } else 0f
        val reverbHeadroom = if (isReverbEnabled) 1.5f else 0f
        val convolverHeadroom = if (isConvolverEnabled) 2.0f else 0f
        val liveprogHeadroom = if (isLiveprogEnabled) 1.5f else 0f

        val requiredAttenuationDb = if (isAutoHeadroomEnabled) {
            -(maxPositiveEqDb * 0.85f + bassBoostHeadroom + tubeHeadroom + companderHeadroom +
                    reverbHeadroom + convolverHeadroom + liveprogHeadroom).coerceIn(0f, 15f)
        } else {
            0f
        }

        currentHeadroomDb = requiredAttenuationDb
        preAmpLinear = 10.0.pow(requiredAttenuationDb.toDouble() / 20.0).toFloat().coerceIn(0.15f, 1.0f)

        val handle = engineHandle
        if (handle != 0L) {
            nativeSetEqualizer(handle, isUserEqEnabled, eqFilterMode, eqInterpolationMode, activeUser15)
            syncArbitraryEqInternal(handle)
        }
    }

    private fun syncArbitraryEqInternal(handle: Long) {
        if (handle == 0L) return
        val hasCustomStr = customGraphicEqString.isNotBlank()
        val hasAutoEq = isAutoEqEnabled && autoEqGains != null
        when {
            hasCustomStr && isAutoEqEnabled -> {
                val str = if (customGraphicEqString.trim().startsWith("GraphicEQ:", ignoreCase = true)) {
                    customGraphicEqString.trim()
                } else {
                    "GraphicEQ: ${customGraphicEqString.trim()}"
                }
                nativeSetArbitraryEq(handle, true, str)
            }
            hasAutoEq -> {
                val eqStr = buildGraphicEqStringFromGains(autoEqGains!!)
                nativeSetArbitraryEq(handle, true, eqStr)
            }
            else -> {
                nativeSetArbitraryEq(handle, false, null)
            }
        }
    }

    private fun syncDdcInternal(handle: Long) {
        if (handle == 0L) return
        if (!isDdcEnabled) {
            nativeSetDdc(handle, false, null)
            return
        }
        val vdcStr = customVdcContent?.takeIf { it.isNotBlank() }
            ?: builtInVdcPresets.find { it.id == selectedVdcPresetId }?.vdcContent
            ?: builtInVdcPresets.first().vdcContent
        nativeSetDdc(handle, true, vdcStr)
    }

    private fun generateSyntheticImpulse(presetId: Int, sampleRate: Int): Pair<FloatArray, Int> {
        val sr = sampleRate.coerceAtLeast(44100)
        val durationMs = when (presetId) {
            0 -> 28 // JamesDSP Reference Studio
            1 -> 14 // Analog Mastering Tape
            2 -> 38 // Binaural Stage
            3 -> 160 // Symphony Concert Hall
            else -> 24 // Club Sub-Bass Exciter
        }
        val frames = (sr * durationMs) / 1000
        val samples = FloatArray(frames * 2)

        samples[0] = 0.84f
        samples[1] = 0.84f

        for (i in 1 until frames) {
            val t = i.toDouble() / sr
            val decay = exp(-t * (1000.0 / durationMs) * 4.2).toFloat()
            when (presetId) {
                0 -> {
                    val ms = (i * 1000) / sr
                    if (i == (sr * 7) / 1000) {
                        samples[i * 2] = 0.19f
                        samples[i * 2 + 1] = -0.15f
                    } else if (i == (sr * 13) / 1000) {
                        samples[i * 2] = -0.12f
                        samples[i * 2 + 1] = 0.16f
                    } else if (ms > 3) {
                        val mod = sin(2.0 * PI * 320.0 * t).toFloat() * 0.004f * decay
                        samples[i * 2] = mod
                        samples[i * 2 + 1] = -mod
                    }
                }
                1 -> {
                    val warmth = (sin(2.0 * PI * 110.0 * t) * exp(-t * 280.0)).toFloat() * 0.018f
                    samples[i * 2] = warmth
                    samples[i * 2 + 1] = warmth
                }
                2 -> {
                    if (i == (sr * 1) / 2200) {
                        samples[i * 2] = -0.17f
                        samples[i * 2 + 1] = 0.17f
                    } else if (i == (sr * 11) / 1000) {
                        samples[i * 2] = 0.15f
                        samples[i * 2 + 1] = -0.15f
                    }
                }
                3 -> {
                    val pseudoRandL = sin(i * 12.9898).toFloat()
                    val pseudoRandR = cos(i * 78.233).toFloat()
                    samples[i * 2] = pseudoRandL * decay * 0.015f
                    samples[i * 2 + 1] = pseudoRandR * decay * 0.015f
                }
                else -> {
                    val sub = (sin(2.0 * PI * 65.0 * t) * exp(-t * 110.0)).toFloat() * 0.025f
                    samples[i * 2] = sub
                    samples[i * 2 + 1] = sub
                }
            }
        }
        return samples to frames
    }

    /**
     * Применяет к импульсу Convolver параметры Pre-Delay (мс), Tail Trim (%),
     * Wet/Dry Mix (%) и инверсию фазы, а также строит массив превью формы волны для UI.
     */
    private fun conditionImpulseResponse(
        rawSamples: FloatArray,
        channels: Int,
        rawFrames: Int,
        sampleRate: Int
    ): Pair<FloatArray, Int> {
        val trimRatio = (convolverTailTrimPercent / 100f).coerceIn(0.1f, 1.0f)
        val trimmedFrames = (rawFrames * trimRatio).toInt().coerceAtLeast(16)
        val preDelayFrames = ((convolverPreDelayMs.coerceIn(0f, 100f) / 1000f) * sampleRate).toInt().coerceAtLeast(0)
        val totalFrames = trimmedFrames + preDelayFrames

        val wet = (convolverWetMix / 100f).coerceIn(0f, 1f)
        val dry = 1.0f - wet
        val phaseSign = if (convolverInvertPhase) -1.0f else 1.0f

        val out = FloatArray(totalFrames * channels)

        // Сухой сигнал (Dirac delta в нулевом отсчете) для бесшовного Wet/Dry микса внутри свертки
        for (ch in 0 until channels.coerceAtMost(2)) {
            out[ch] += dry
        }

        val fadeStartFrame = (trimmedFrames * 0.85f).toInt()
        for (f in 0 until trimmedFrames) {
            val fadeEnv = if (f >= fadeStartFrame && trimmedFrames > fadeStartFrame) {
                val p = (f - fadeStartFrame).toFloat() / (trimmedFrames - fadeStartFrame).toFloat()
                0.5f * (1.0f + cos(PI * p).toFloat())
            } else 1.0f

            val dstFrame = f + preDelayFrames
            for (ch in 0 until channels) {
                val sample = rawSamples.getOrElse(f * channels + ch) { 0f }
                out[dstFrame * channels + ch] += sample * wet * fadeEnv * phaseSign
            }
        }

        // Обновляем 64-точечное превью импульса для UI графика
        val preview = FloatArray(64)
        val step = (totalFrames / 64).coerceAtLeast(1)
        var maxPeak = 0.001f
        for (bin in 0 until 64) {
            val startF = bin * step
            val endF = ((bin + 1) * step).coerceAtMost(totalFrames)
            var peak = 0f
            for (f in startF until endF) {
                val v = abs(out.getOrElse(f * channels) { 0f })
                if (v > peak) peak = v
            }
            preview[bin] = peak
            if (peak > maxPeak) maxPeak = peak
        }
        for (bin in 0 until 64) {
            preview[bin] = (preview[bin] / maxPeak).coerceIn(0f, 1f)
        }
        currentIrWaveformPreview = preview

        return out to totalFrames
    }

    private fun syncConvolverInternal(handle: Long) {
        if (handle == 0L) return
        if (!isConvolverEnabled) {
            nativeSetConvolver(handle, false, null, 2, 0)
            return
        }
        val customSamples = customImpulseSamples
        val (baseSamples, ch, baseFrames) = if (customSamples != null && customImpulseFrames > 0) {
            Triple(customSamples, customImpulseChannels, customImpulseFrames)
        } else {
            val (synthSamples, frames) = generateSyntheticImpulse(convolverPresetId, currentSampleRate)
            Triple(synthSamples, 2, frames)
        }

        val (conditioned, finalFrames) = conditionImpulseResponse(baseSamples, ch, baseFrames, currentSampleRate)
        nativeSetConvolver(handle, true, conditioned, ch, finalFrames)
    }

    private fun syncLiveprogInternal(handle: Long) {
        if (handle == 0L) return
        nativeSetLiveprog(handle, isLiveprogEnabled, liveprogScriptId, liveprogParams, customEelCode)
    }

    private fun applyAllCurrentSettings() {
        val handle = engineHandle
        if (handle == 0L) return
        recalculateHeadroomAndSyncEq()
        nativeSetBass(handle, isBassEnabled, bassGainDb)
        nativeSetCompander(
            handle,
            isCompanderEnabled,
            companderTimeConstant,
            companderGranularity,
            companderTfResolution,
            companderGains
        )
        nativeSetTube(handle, isTubeEnabled, tubeDriveDb)
        nativeSetReverb(handle, isReverbEnabled, reverbPreset)
        nativeSetStereoWide(handle, isStereoWideEnabled, stereoWidth)
        nativeSetCrossfeed(handle, isBs2bEnabled, bs2bMode)
        syncDdcInternal(handle)
        syncConvolverInternal(handle)
        syncLiveprogInternal(handle)
        nativeSetLimiter(handle, limThresholdDb, limReleaseMs, postGainDb)
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
    fun setEq15BandMode(enabled15Bands: Boolean) {
        if (is15BandMode != enabled15Bands) {
            if (enabled15Bands && userEqGains15.all { it == 0f } && userEqGains10.any { it != 0f }) {
                userEqGains15 = interpolate10To15Bands(userEqGains10)
                PrefsManager.saveUserEqGains15(userEqGains15)
            }
            is15BandMode = enabled15Bands
            PrefsManager.saveEq15BandMode(enabled15Bands)
            recalculateHeadroomAndSyncEq()
        }
    }

    @Synchronized
    fun setEqFilterMode(mode: Int) {
        eqFilterMode = mode.coerceIn(0, 6)
        PrefsManager.saveEqFilterMode(eqFilterMode)
        recalculateHeadroomAndSyncEq()
    }

    @Synchronized
    fun setEqInterpolationMode(mode: Int) {
        eqInterpolationMode = mode.coerceIn(0, 1)
        PrefsManager.saveEqInterpolationMode(eqInterpolationMode)
        recalculateHeadroomAndSyncEq()
    }

    @Synchronized
    fun setEqualizerGains(gains: FloatArray) {
        if (gains.size == 15) {
            userEqGains15 = gains.copyOf()
        } else {
            userEqGains10 = gains.copyOf()
            if (!is15BandMode) {
                userEqGains15 = interpolate10To15Bands(userEqGains10)
            }
        }
        recalculateHeadroomAndSyncEq()
    }

    @Synchronized
    fun setEqualizerGains15(gains15: FloatArray) {
        userEqGains15 = gains15.copyOf()
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
    fun setCustomGraphicEqString(str: String) {
        customGraphicEqString = str.trim()
        PrefsManager.saveCustomGraphicEqString(customGraphicEqString)
        recalculateHeadroomAndSyncEq()
    }

    @Synchronized
    fun setCompanderEnabled(enabled: Boolean) {
        isCompanderEnabled = enabled
        PrefsManager.saveCompanderEnabled(enabled)
        recalculateHeadroomAndSyncEq()
        if (engineHandle != 0L) {
            nativeSetCompander(
                engineHandle,
                enabled,
                companderTimeConstant,
                companderGranularity,
                companderTfResolution,
                companderGains
            )
        }
    }

    @Synchronized
    fun setCompanderParams(timeConstant: Float, granularity: Int, tfResolution: Int, gains7: FloatArray) {
        companderTimeConstant = timeConstant.coerceIn(0.02f, 2.0f)
        companderGranularity = granularity.coerceIn(1, 4)
        companderTfResolution = tfResolution.coerceIn(0, 3)
        companderGains = gains7.copyOf()
        recalculateHeadroomAndSyncEq()
        if (engineHandle != 0L && isCompanderEnabled) {
            nativeSetCompander(
                engineHandle,
                true,
                companderTimeConstant,
                companderGranularity,
                companderTfResolution,
                companderGains
            )
        }
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
        reverbPreset = preset.toInt().coerceIn(0, 11)
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
        bs2bMode = mode.toInt().coerceIn(0, 5)
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
        tubeDriveDb = drive.coerceIn(0f, 12f)
        recalculateHeadroomAndSyncEq()
        if (engineHandle != 0L && isTubeEnabled) nativeSetTube(engineHandle, true, tubeDriveDb)
    }

    @Synchronized
    fun setDdcEnabled(enabled: Boolean) {
        isDdcEnabled = enabled
        PrefsManager.saveDdcEnabled(enabled)
        syncDdcInternal(engineHandle)
    }

    @Synchronized
    fun setDdcPreset(presetId: String) {
        selectedVdcPresetId = presetId
        customVdcContent = null
        PrefsManager.saveDdcPresetId(presetId)
        PrefsManager.saveCustomVdcContent(null)
        if (isDdcEnabled) syncDdcInternal(engineHandle)
    }

    @Synchronized
    fun setCustomVdcContent(vdcContent: String) {
        customVdcContent = vdcContent.trim()
        PrefsManager.saveCustomVdcContent(customVdcContent)
        if (isDdcEnabled) syncDdcInternal(engineHandle)
    }

    @Synchronized
    fun setConvolverEnabled(enabled: Boolean) {
        isConvolverEnabled = enabled
        PrefsManager.saveConvolverEnabled(enabled)
        recalculateHeadroomAndSyncEq()
        syncConvolverInternal(engineHandle)
    }

    @Synchronized
    fun setConvolverPreset(presetId: Int) {
        convolverPresetId = presetId
        customImpulseSamples = null
        customImpulseName = null
        PrefsManager.saveConvolverPresetId(presetId)
        syncConvolverInternal(engineHandle)
    }

    @Synchronized
    fun setConvolverConditioning(wetMix: Float, preDelayMs: Float, tailTrimPercent: Float, invertPhase: Boolean) {
        convolverWetMix = wetMix.coerceIn(0f, 100f)
        convolverPreDelayMs = preDelayMs.coerceIn(0f, 100f)
        convolverTailTrimPercent = tailTrimPercent.coerceIn(10f, 100f)
        convolverInvertPhase = invertPhase
        PrefsManager.saveConvolverWetMix(convolverWetMix)
        PrefsManager.saveConvolverPreDelayMs(convolverPreDelayMs)
        PrefsManager.saveConvolverTailTrim(convolverTailTrimPercent)
        PrefsManager.saveConvolverInvertPhase(convolverInvertPhase)
        if (isConvolverEnabled) {
            syncConvolverInternal(engineHandle)
        }
    }

    @Synchronized
    fun setLiveprogEnabled(enabled: Boolean) {
        isLiveprogEnabled = enabled
        PrefsManager.saveLiveprogEnabled(enabled)
        recalculateHeadroomAndSyncEq()
        syncLiveprogInternal(engineHandle)
    }

    @Synchronized
    fun setLiveprogScript(scriptId: Int) {
        liveprogScriptId = scriptId.coerceIn(0, builtInEelScripts.lastIndex)
        val scriptDef = builtInEelScripts[liveprogScriptId]
        val newParams = FloatArray(8) { idx ->
            scriptDef.parameters.getOrNull(idx)?.defaultVal ?: 0f
        }
        liveprogParams = newParams
        PrefsManager.saveLiveprogScriptId(liveprogScriptId)
        PrefsManager.saveLiveprogParams(newParams)
        syncLiveprogInternal(engineHandle)
    }

    @Synchronized
    fun setLiveprogParams(params: FloatArray, customCode: String? = customEelCode) {
        for (i in 0 until params.size.coerceAtMost(8)) {
            liveprogParams[i] = params[i]
        }
        customEelCode = customCode
        PrefsManager.saveLiveprogParams(liveprogParams)
        if (customCode != null) PrefsManager.saveCustomEelCode(customCode)
        syncLiveprogInternal(engineHandle)
    }

    fun loadImpulseFromUri(context: Context, uri: Uri, displayName: String?): Boolean {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use(InputStream::readBytes) ?: return false
            val parsed = parseWavBytes(bytes) ?: return false
            synchronized(this) {
                customImpulseSamples = parsed.first
                customImpulseChannels = parsed.second
                customImpulseFrames = parsed.third
                customImpulseName = displayName ?: uri.lastPathSegment ?: "Custom IR.wav"
                isConvolverEnabled = true
                PrefsManager.saveConvolverEnabled(true)
                recalculateHeadroomAndSyncEq()
                syncConvolverInternal(engineHandle)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun parseWavBytes(bytes: ByteArray): Triple<FloatArray, Int, Int>? {
        if (bytes.size < 44) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = buf.getInt(0)
        if (riff != 0x46464952) return null

        var offset = 12
        var audioFormat = 1
        var channels = 2
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = buf.getInt(offset)
            val chunkSize = buf.getInt(offset + 4)
            val nextChunk = offset + 8 + chunkSize + (chunkSize and 1)

            if (chunkId == 0x20746D66 && offset + 24 <= bytes.size) {
                audioFormat = buf.getShort(offset + 8).toInt() and 0xFFFF
                channels = (buf.getShort(offset + 10).toInt() and 0xFFFF).coerceIn(1, 4)
                bitsPerSample = buf.getShort(offset + 22).toInt() and 0xFFFF
            } else if (chunkId == 0x61746164) {
                dataOffset = offset + 8
                dataSize = chunkSize.coerceAtMost(bytes.size - dataOffset)
                break
            }
            if (nextChunk <= offset) break
            offset = nextChunk
        }

        if (dataOffset < 0 || dataSize <= 0) return null
        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(2)
        val totalSamples = (dataSize / bytesPerSample).coerceAtMost(96000 * 4)
        val frames = totalSamples / channels
        if (frames <= 0) return null

        val out = FloatArray(frames * channels)
        var pos = dataOffset
        for (i in 0 until frames * channels) {
            if (pos + bytesPerSample > bytes.size) break
            out[i] = when {
                audioFormat == 3 && bitsPerSample == 32 -> buf.getFloat(pos).coerceIn(-1f, 1f)
                bitsPerSample == 16 -> buf.getShort(pos) / 32768.0f
                bitsPerSample == 24 -> {
                    val b0 = bytes[pos].toInt() and 0xFF
                    val b1 = bytes[pos + 1].toInt() and 0xFF
                    val b2 = bytes[pos + 2].toInt()
                    val int24 = (b2 shl 16) or (b1 shl 8) or b0
                    int24 / 8388608.0f
                }
                bitsPerSample == 32 -> (buf.getInt(pos) shr 8) / 8388608.0f
                else -> 0f
            }
            pos += bytesPerSample
        }
        return Triple(out, channels, frames)
    }

    @Synchronized
    fun setLimiterParams(threshold: Float, release: Float, postgain: Float) {
        limThresholdDb = threshold.coerceIn(-24f, -0.05f)
        limReleaseMs = release.coerceIn(5f, 500f)
        postGainDb = postgain.coerceIn(-15f, 15f)
        if (engineHandle != 0L) {
            nativeSetLimiter(engineHandle, limThresholdDb, limReleaseMs, postGainDb)
        }
    }
}
