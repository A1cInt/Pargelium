#include <jni.h>
#include <cmath>
#include <vector>
#include <algorithm>

static constexpr float PI = 3.14159265358979323846f;

struct EqBand {
    float b0=1, b1=0, b2=0, a1=0, a2=0;
    float s1L=0, s2L=0, s1R=0, s2R=0;

    void update(float gainDb, float freq, float q, float sampleRate) {
        float a = powf(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * PI * freq / sampleRate;
        float alpha = sinf(w0) / (2.0f * q);
        float a0 = 1.0f + alpha / a;
        float cosW0 = cosf(w0);
        b0 = (1.0f + alpha * a) / a0;
        b1 = (-2.0f * cosW0) / a0;
        b2 = (1.0f - alpha * a) / a0;
        a1 = (-2.0f * cosW0) / a0;
        a2 = (1.0f - alpha / a) / a0;
    }

    inline float processL(float x) {
        float y = b0 * x + s1L;
        s1L = b1 * x - a1 * y + s2L;
        s2L = b2 * x - a2 * y;
        return y;
    }

    inline float processR(float x) {
        float y = b0 * x + s1R;
        s1R = b1 * x - a1 * y + s2R;
        s2R = b2 * x - a2 * y;
        return y;
    }

    void reset() {
        s1L=0; s2L=0; s1R=0; s2R=0;
    }
};

struct CombFilter {
    std::vector<float> buffer;
    float* bufData = nullptr;
    int idx = 0;
    float store = 0.0f;
    int sz = 0;

    void resize(int newSize) {
        if (buffer.size() != newSize) buffer.assign(newSize, 0.0f);
        else std::fill(buffer.begin(), buffer.end(), 0.0f);
        bufData = buffer.data();
        sz = newSize;
        idx = 0;
        store = 0.0f;
    }

    inline float process(float input, float damp1, float damp2, float feedback) {
        float out = bufData[idx];
        store = (out * damp1) + (store * damp2);
        bufData[idx] = input + (store * feedback);
        if (++idx >= sz) idx = 0;
        return out;
    }
};

struct AllpassFilter {
    std::vector<float> buffer;
    float* bufData = nullptr;
    int idx = 0;
    int sz = 0;

    void resize(int newSize) {
        if (buffer.size() != newSize) buffer.assign(newSize, 0.0f);
        else std::fill(buffer.begin(), buffer.end(), 0.0f);
        bufData = buffer.data();
        sz = newSize;
        idx = 0;
    }

    inline float process(float input) {
        float bufOut = bufData[idx];
        float out = -input + bufOut;
        bufData[idx] = input + (bufOut * 0.5f);
        if (++idx >= sz) idx = 0;
        return out;
    }
};

class DspEngine {
public:
    float autoEqFreqs[15] = {25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000};
    EqBand autoEqBands[15];
    float lastAutoEqGains[15];

    float userEqFreqs[10] = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    EqBand userEqBands[10];
    float lastUserEqGains[10];

    EqBand* activeEqBands[25];
    int activeEqBandsCount = 0;

    EqBand bassBiquad;
    float lastBassGain = -1.0f;
    float lastBassFreq = -1.0f;

    int lastSampleRate = -1;
    int lastReverbMode = -1;

    CombFilter combsL[8];
    CombFilter combsR[8];
    AllpassFilter allpassesL[4];
    AllpassFilter allpassesR[4];

    std::vector<float> haasBuffer;
    int haasIndex = 0;
    float reverbHpState = 0.0f;

    float exciteL = 0.0f;
    float exciteR = 0.0f;

    DspEngine() {
        std::fill_n(lastAutoEqGains, 15, -999.0f);
        std::fill_n(lastUserEqGains, 10, -999.0f);
        haasBuffer.assign(96000, 0.0f);
    }

    void initReverb(int sampleRate, int mode) {
        float scale = sampleRate / 44100.0f;
        int cL[8], aL[4], stereoSpread;

        if (mode == 1) { int t1[]={556,588,631,678,714,743,778,809}; int t2[]={112,170,223,278}; std::copy(t1,t1+8,cL); std::copy(t2,t2+4,aL); stereoSpread=12; }
        else if (mode == 3) { int t1[]={1536,1611,1713,1823,1922,2031,2144,2267}; int t2[]={313,461,593,751}; std::copy(t1,t1+8,cL); std::copy(t2,t2+4,aL); stereoSpread=46; }
        else if (mode == 4) { int t1[]={2341,2467,2633,2811,2963,3137,3307,3491}; int t2[]={479,691,887,1123}; std::copy(t1,t1+8,cL); std::copy(t2,t2+4,aL); stereoSpread=86; }
        else if (mode == 5) { int t1[]={4463,4751,5107,5419,5689,5981,6263,6491}; int t2[]={911,1361,1777,2239}; std::copy(t1,t1+8,cL); std::copy(t2,t2+4,aL); stereoSpread=180; }
        else { int t1[]={1116,1188,1277,1356,1422,1491,1557,1617}; int t2[]={225,341,441,556}; std::copy(t1,t1+8,cL); std::copy(t2,t2+4,aL); stereoSpread=23; }

        for (int i=0; i<8; ++i) {
            combsL[i].resize(std::max(1, (int)(cL[i] * scale)));
            combsR[i].resize(std::max(1, (int)((cL[i] + stereoSpread) * scale)));
        }
        for (int i=0; i<4; ++i) {
            allpassesL[i].resize(std::max(1, (int)(aL[i] * scale)));
            allpassesR[i].resize(std::max(1, (int)((aL[i] + stereoSpread) * scale)));
        }
    }

    inline float softClip(float x) {
        x = fmaxf(-1.3f, fminf(1.3f, x));
        return x - 0.15f * x * x * x;
    }

    void reset() {
        std::fill_n(lastAutoEqGains, 15, -999.0f);
        std::fill_n(lastUserEqGains, 10, -999.0f);
        lastBassGain = -1.0f;
        reverbHpState = 0.0f;
        for (int i=0; i<15; ++i) autoEqBands[i].reset();
        for (int i=0; i<10; ++i) userEqBands[i].reset();
        bassBiquad.reset();
        std::fill(haasBuffer.begin(), haasBuffer.end(), 0.0f);
        haasIndex = 0;
        exciteL = 0.0f; exciteR = 0.0f;
    }

    void flush() {
        reverbHpState = 0.0f;
        std::fill(haasBuffer.begin(), haasBuffer.end(), 0.0f);
        haasIndex = 0;
        for (int i=0; i<8; ++i) {
            std::fill(combsL[i].buffer.begin(), combsL[i].buffer.end(), 0.0f);
            std::fill(combsR[i].buffer.begin(), combsR[i].buffer.end(), 0.0f);
            combsL[i].store = combsR[i].store = 0.0f;
        }
        for (int i=0; i<4; ++i) {
            std::fill(allpassesL[i].buffer.begin(), allpassesL[i].buffer.end(), 0.0f);
            std::fill(allpassesR[i].buffer.begin(), allpassesR[i].buffer.end(), 0.0f);
        }
    }
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_alcint_pargelium_CustomAudioProcessor_nativeInit(JNIEnv *env, jobject thiz) {
    return reinterpret_cast<jlong>(new DspEngine());
}

extern "C" JNIEXPORT void JNICALL
Java_com_alcint_pargelium_CustomAudioProcessor_nativeRelease(JNIEnv *env, jobject thiz, jlong ptr) {
    delete reinterpret_cast<DspEngine*>(ptr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_alcint_pargelium_CustomAudioProcessor_nativeFlush(JNIEnv *env, jobject thiz, jlong ptr) {
    reinterpret_cast<DspEngine*>(ptr)->flush();
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_alcint_pargelium_CustomAudioProcessor_nativeProcess(JNIEnv *env, jobject thiz, jlong ptr, jobject inBuffer, jobject outBuffer, jint sizeBytes, jint sampleRate, jint channels, jfloatArray jSettings, jfloat currentLoudness) {
    auto* engine = reinterpret_cast<DspEngine*>(ptr);
    int16_t* in = static_cast<int16_t*>(env->GetDirectBufferAddress(inBuffer));
    int16_t* out = static_cast<int16_t*>(env->GetDirectBufferAddress(outBuffer));
    int samples = sizeBytes / sizeof(int16_t);

    jfloat* settings = env->GetFloatArrayElements(jSettings, nullptr);
    bool enabled = settings[0] > 0.5f;

    if (!enabled) {
        std::copy(in, in + samples, out);
        float peak = 0.0f;
        for (int i = 0; i < samples; i += 32) {
            float absSample = fabsf(out[i] * 0.000030517578f);
            if (absSample > peak) peak = absSample;
        }
        env->ReleaseFloatArrayElements(jSettings, settings, JNI_ABORT);
        return currentLoudness * 0.8f + peak * 0.2f;
    }

    int reverbMode = (int)settings[1];
    if (sampleRate != engine->lastSampleRate || reverbMode != engine->lastReverbMode) {
        engine->lastSampleRate = sampleRate;
        engine->lastReverbMode = reverbMode;
        engine->initReverb(sampleRate, reverbMode);
        engine->reset();
    }

    engine->activeEqBandsCount = 0;
    bool autoEqEnabled = settings[2] > 0.5f;
    bool userEqEnabled = settings[3] > 0.5f;

    if (autoEqEnabled) {
        for (int i=0; i<15; ++i) {
            float g = settings[15 + i];
            if (g != engine->lastAutoEqGains[i]) {
                engine->lastAutoEqGains[i] = g;
                engine->autoEqBands[i].update(g, engine->autoEqFreqs[i], 1.41f, sampleRate);
            }
            if (g != 0.0f) engine->activeEqBands[engine->activeEqBandsCount++] = &engine->autoEqBands[i];
        }
    }

    if (userEqEnabled) {
        for (int i=0; i<10; ++i) {
            float g = settings[30 + i];
            if (g != engine->lastUserEqGains[i]) {
                engine->lastUserEqGains[i] = g;
                engine->userEqBands[i].update(g, engine->userEqFreqs[i], 1.41f, sampleRate);
            }
            if (g != 0.0f) engine->activeEqBands[engine->activeEqBandsCount++] = &engine->userEqBands[i];
        }
    }

    bool isStereo = channels == 2;
    bool bassEnabled = settings[4] > 0.5f;
    float bassGain = settings[5];
    float bassFreq = settings[6];
    bool doBass = bassEnabled && bassGain > 0.0f;

    if (doBass) {
        if (bassGain != engine->lastBassGain || bassFreq != engine->lastBassFreq) {
            engine->lastBassGain = bassGain;
            engine->lastBassFreq = bassFreq;
            engine->bassBiquad.update(bassGain * 0.15f, bassFreq, 0.707f, sampleRate);
        }
    }

    bool haas = settings[7] > 0.5f;
    float haasDelayMs = settings[8];
    bool doHaas = haas && isStereo;
    int haasDelaySamples = doHaas ? (int)(sampleRate * (haasDelayMs * 0.001f)) : 0;
    int haasBufSize = engine->haasBuffer.size();

    bool room = settings[9] > 0.5f;
    float reverbMix = settings[10];
    float reverbSize = settings[11];
    float reverbDamp = settings[12];

    float mix = reverbMix * 0.01f;
    bool doRoom = room && engine->combsL[0].sz > 0 && mix > 0.01f;
    float revRoom = reverbSize * 0.0028f + 0.7f;
    float revDamp2 = reverbDamp * 0.005f;
    float revDamp1 = 1.0f - revDamp2;
    float revWet = mix * 1.5f;
    float revDry = 1.0f - (mix * 0.5f);

    bool spatializer = settings[13] > 0.5f;
    float spatialWidthVal = settings[14];
    float spatialWidth = spatialWidthVal * 0.02f;
    bool doSpatializer = spatializer && isStereo && spatialWidth > 0.0f;
    float spatDiffMult = spatialWidth * 0.5f;

    bool tube = settings[40] > 0.5f;
    bool crossfeed = settings[41] > 0.5f;
    bool exciter = settings[42] > 0.5f;
    float exciterInt = settings[43] * 0.01f;

    float bufferPeak = 0.0f;
    const float inv32768 = 1.0f / 32768.0f;

    float* haasData = engine->haasBuffer.data();
    EqBand** activeBands = engine->activeEqBands;
    int activeBandsCount = engine->activeEqBandsCount;

    for (int i = 0; i < samples; i += channels) {
        float inL = (in[i] * inv32768) + 1e-12f;
        float inR = isStereo ? (in[i + 1] * inv32768) + 1e-12f : inL;

        for (int eq = 0; eq < activeBandsCount; ++eq) {
            inL = activeBands[eq]->processL(inL);
            inR = activeBands[eq]->processR(inR);
        }

        if (doBass) {
            inL = engine->bassBiquad.processL(inL);
            inR = engine->bassBiquad.processR(inR);
        }

        if (doHaas) {
            haasData[engine->haasIndex] = inR;
            int readIndex = engine->haasIndex - haasDelaySamples;
            if (readIndex < 0) readIndex += haasBufSize;
            inR = haasData[readIndex];
            if (++engine->haasIndex >= haasBufSize) engine->haasIndex = 0;
        }

        if (exciter) {
            engine->exciteL += 0.1f * (inL - engine->exciteL);
            engine->exciteR += 0.1f * (inR - engine->exciteR);
            float hL = inL - engine->exciteL;
            float hR = inR - engine->exciteR;
            inL += hL * fabsf(hL) * exciterInt;
            inR += hR * fabsf(hR) * exciterInt;
        }

        if (doSpatializer) {
            float diff = (inL - inR) * spatDiffMult;
            inL += diff;
            inR -= diff;
        }

        if (crossfeed) {
            float mixSignal = (inL + inR) * 0.2f;
            inL = inL * 0.6f + mixSignal;
            inR = inR * 0.6f + mixSignal;
        }

        if (tube) {
            float absL = fabsf(inL);
            float absR = fabsf(inR);
            inL = (inL * 1.75f + absL * 0.25f) / (1.0f + absL) * 0.75f;
            inR = (inR * 1.75f + absR * 0.25f) / (1.0f + absR) * 0.75f;
        }

        if (doRoom) {
            float monoMix = (inL + inR) * 0.5f;
            engine->reverbHpState += 0.05f * (monoMix - engine->reverbHpState);
            float revIn = (monoMix - engine->reverbHpState) * 0.03f;

            float outL = 0.0f, outR = 0.0f;
            for (int r = 0; r < 8; ++r) {
                outL += engine->combsL[r].process(revIn, revDamp1, revDamp2, revRoom);
                outR += engine->combsR[r].process(revIn, revDamp1, revDamp2, revRoom);
            }
            for (int a = 0; a < 4; ++a) {
                outL = engine->allpassesL[a].process(outL);
                outR = engine->allpassesR[a].process(outR);
            }

            outL *= 1.0f / (1.0f + fabsf(outL));
            outR *= 1.0f / (1.0f + fabsf(outR));

            inL = (inL * revDry) + (outL * revWet);
            inR = (inR * revDry) + (outR * revWet);
        }

        inL = engine->softClip(inL);
        inR = engine->softClip(inR);

        float absInL = fabsf(inL);
        float absInR = fabsf(inR);
        float samplePeak = absInL > absInR ? absInL : absInR;
        if (samplePeak > bufferPeak) bufferPeak = samplePeak;

        out[i] = (int16_t)(inL * 32767.0f);
        if (isStereo) {
            out[i + 1] = (int16_t)(inR * 32767.0f);
        }
    }

    env->ReleaseFloatArrayElements(jSettings, settings, JNI_ABORT);
    return currentLoudness * 0.7f + bufferPeak * 0.3f;
}