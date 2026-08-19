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

static inline void fht16(float A[16]) {
    float alpha, beta, beta2, alpha1, alpha2, y1, y2, y3;
    alpha = A[0]; beta = A[1]; beta2 = A[2]; alpha1 = A[3];
    alpha2 = alpha + beta; y1 = alpha - beta; y2 = beta2 + alpha1; y3 = beta2 - alpha1;
    A[0] = alpha2 + y2; A[2] = alpha2 - y2; A[1] = y1 + y3; A[3] = y1 - y3;
    alpha = A[4]; beta = A[5]; beta2 = A[6]; alpha1 = A[7];
    alpha2 = alpha + beta; y1 = alpha - beta; y2 = beta2 + alpha1; y3 = beta2 - alpha1;
    A[4] = alpha2 + y2; A[6] = alpha2 - y2; A[5] = y1 + y3; A[7] = y1 - y3;
    alpha = A[8]; beta = A[9]; beta2 = A[10]; alpha1 = A[11];
    alpha2 = alpha + beta; y1 = alpha - beta; y2 = beta2 + alpha1; y3 = beta2 - alpha1;
    A[8] = alpha2 + y2; A[10] = alpha2 - y2; A[9] = y1 + y3; A[11] = y1 - y3;
    alpha = A[12]; beta = A[13]; beta2 = A[14]; alpha1 = A[15];
    alpha2 = alpha + beta; y1 = alpha - beta; y2 = beta2 + alpha1; y3 = beta2 - alpha1;
    A[12] = alpha2 + y2; A[14] = alpha2 - y2; A[13] = y1 + y3; A[15] = y1 - y3;
    alpha = A[0]; beta = A[4]; A[0] = alpha + beta; A[4] = alpha - beta;
    alpha = A[2]; beta = A[6]; A[2] = alpha + beta; A[6] = alpha - beta;
    alpha = A[1]; beta = 0.707106769f*(A[5] + A[7]); beta2 = 0.707106769f*(A[5] - A[7]);
    A[1] = alpha + beta; A[5] = alpha - beta;
    alpha = A[3]; A[3] = alpha + beta2; A[7] = alpha - beta2;
    alpha = A[8]; beta = A[12]; A[8] = alpha + beta; A[12] = alpha - beta;
    alpha = A[10]; beta = A[14]; A[10] = alpha + beta; A[14] = alpha - beta;
    alpha = A[9]; beta = 0.707106769f*(A[13] + A[15]); beta2 = 0.707106769f*(A[13] - A[15]);
    A[9] = alpha + beta; A[13] = alpha - beta;
    alpha = A[11]; A[11] = alpha + beta2; A[15] = alpha - beta2;
    alpha = A[0]; beta = A[8]; A[0] = alpha + beta; A[8] = alpha - beta;
    alpha = A[4]; beta = A[12]; A[4] = alpha + beta; A[12] = alpha - beta;
    alpha1 = A[1]; alpha2 = A[7];
    beta = A[9] * 0.923879504f + A[15] * 0.382683426f;
    beta2 = A[9] * 0.382683426f - A[15] * 0.923879504f;
    A[1] = alpha1 + beta; A[9] = alpha1 - beta; A[7] = alpha2 + beta2; A[15] = alpha2 - beta2;
    alpha1 = A[10] * 0.707106769f; alpha2 = A[14] * 0.707106769f;
    beta = alpha1 + alpha2; beta2 = alpha1 - alpha2;
    alpha1 = A[2]; alpha2 = A[6];
    A[2] = alpha1 + beta; A[10] = alpha1 - beta; A[6] = alpha2 + beta2; A[14] = alpha2 - beta2;
    alpha1 = A[3]; alpha2 = A[5];
    beta = A[11] * 0.382683426f + A[13] * 0.923879504f;
    beta2 = A[11] * 0.923879504f - A[13] * 0.382683426f;
    A[3] = alpha1 + beta; A[11] = alpha1 - beta; A[5] = alpha2 + beta2; A[13] = alpha2 - beta2;
}

struct IntegerDelayLine {
    std::vector<float> buffer;
    int inPoint = 0;
    int outPoint = 0;
    int sz = 0;

    void init(int allocateLen, int lag) {
        sz = allocateLen > 2048 ? 2048 : allocateLen;
        buffer.assign(sz, 0.0f);
        inPoint = 0;
        outPoint = inPoint - lag;
        while (outPoint < 0) outPoint += sz;
    }

    inline float process(float sample) {
        buffer[inPoint++] = sample;
        if (inPoint == sz) inPoint = 0;
        float lastOutput = buffer[outPoint++];
        if (outPoint >= sz) outPoint = 0;
        return lastOutput;
    }
};

struct DynamicSVF {
    float gCoeff = 1.0f, RCoeff = 1.0f, KCoeff = 0.0f;
    float p1 = 0.0f, p2 = 0.0f, p3 = 0.0f, p4 = 0.0f;
    float z1 = 0.0f, z2 = 0.0f;

    void update(float fs, float cutoff, float q, float shelfGain) {
        float T = 1.0f / fs;
        float wa = (2.0f / T) * tanf((cutoff * 2.0f * PI) * T / 2.0f);
        gCoeff = wa * T / 2.0f;
        RCoeff = 1.0f / (2.0f * q);
        KCoeff = shelfGain - 1.0f;
        p1 = 2.0f * RCoeff + gCoeff;
        p2 = 1.0f / (1.0f + (2.0f * RCoeff * gCoeff) + gCoeff * gCoeff);
        p3 = 2.0f * RCoeff;
        p4 = 4.0f * RCoeff;
    }

    inline void processStereo(DynamicSVF& svfR, float xL, float xR, float& yL, float& yR) {
        float HPL = (xL - p1 * z1 - z2) * p2;
        float BPL = HPL * gCoeff + z1;
        float LPL = BPL * gCoeff + z2;
        float PeakL = xL + (p3 * BPL) * KCoeff;
        z1 = gCoeff * HPL + BPL;
        z2 = gCoeff * BPL + LPL;
        yL = PeakL;

        float HPR = (xR - p1 * svfR.z1 - svfR.z2) * p2;
        float BPR = HPR * gCoeff + svfR.z1;
        float LPR = BPR * gCoeff + svfR.z2;
        float PeakR = xR + (p3 * BPR) * KCoeff;
        svfR.z1 = gCoeff * HPR + BPR;
        svfR.z2 = gCoeff * BPR + LPR;
        yR = PeakR;
    }
};

class DBBEngine {
public:
    float maxGain = 0.0f;
    float fs = 48000.0f;
    int dsFactor = 1;
    int dsPos = 0;
    float dsSum = 0.0f;

    float freq[9] = {0};
    float maxSmooth = 0.0f, minusMaxSmooth = 0.0f;
    float gainSmooth = 0.0f, minusGainSmooth = 0.0f;
    float boostdB = 0.0f;
    float smoothMaxFreq = 0.0f;

    float delayLine[16] = {0};
    float fftBuf[16] = {0};

    IntegerDelayLine dL[2];
    DynamicSVF svf[2];

    void update(float sampleRate, float maxG) {
        maxGain = fmaxf(0.0f, maxG);
        fs = sampleRate;
        dsFactor = (int)roundf(fs / 500.0f);
        if (dsFactor < 1) dsFactor = 1;
        float targetFs = fs / dsFactor;

        for (int i = 0; i < 9; i++) {
            freq[i] = (i * (targetFs / 16.0f) + i * (targetFs / 16.0f)) * 0.5f;
        }

        maxSmooth = 1.0f - expf(-1.0f / (0.5f / 1000.0f * fs));
        minusMaxSmooth = 1.0f - maxSmooth;
        gainSmooth = 1.0f - expf(-1.0f / (2.0f / 1000.0f * fs));
        minusGainSmooth = 1.0f - gainSmooth;

        dL[0].init(2048, dsFactor + (int)(2.0f * (fs / 1000.0f)));
        dL[1].init(2048, dsFactor + (int)(2.0f * (fs / 1000.0f)));
    }

    void reset() {
        boostdB = 0.0f;
        smoothMaxFreq = freq[0];
        std::fill_n(delayLine, 16, 0.0f);
        std::fill_n(fftBuf, 16, 0.0f);
        svf[0].z1 = svf[0].z2 = svf[1].z1 = svf[1].z2 = 0.0f;
        std::fill(dL[0].buffer.begin(), dL[0].buffer.end(), 0.0f);
        std::fill(dL[1].buffer.begin(), dL[1].buffer.end(), 0.0f);
    }

    inline void processSample(float& inL, float& inR) {
        dsSum += (inL + inR) * 0.5f;
        if (++dsPos >= dsFactor) {
            float downsampled = dsSum / dsFactor;
            dsSum = 0.0f;
            dsPos = 0;

            for(int j=15; j>0; --j) delayLine[j] = delayLine[j-1];
            delayLine[0] = downsampled;

            fftBuf[0] = delayLine[0]; fftBuf[8] = delayLine[1];
            fftBuf[4] = delayLine[2]; fftBuf[12] = delayLine[3];
            fftBuf[2] = delayLine[4]; fftBuf[10] = delayLine[5];
            fftBuf[6] = delayLine[6]; fftBuf[14] = delayLine[7];
            fftBuf[1] = delayLine[8]; fftBuf[9] = delayLine[9];
            fftBuf[5] = delayLine[10]; fftBuf[13] = delayLine[11];
            fftBuf[3] = delayLine[12]; fftBuf[11] = delayLine[13];
            fftBuf[7] = delayLine[14]; fftBuf[15] = delayLine[15];

            fht16(fftBuf);

            float peak1 = fabsf(fftBuf[0]);
            float peak2 = peak1;
            float currentMaxFreq = freq[0];
            float maxdB = 20.0f * log10f(peak1 + 1e-12f);
            float mindB = maxdB;

            for (int k = 1; k < 9; ++k) {
                int symIdx = 16 - k;
                float lR = (fftBuf[k] + fftBuf[symIdx]) * 0.5f;
                float lI = (fftBuf[k] - fftBuf[symIdx]) * 0.5f;
                float mag = sqrtf(lR * lR + lI * lI);

                if (mag > peak1) {
                    peak1 = mag;
                    currentMaxFreq = freq[k];
                    maxdB = 20.0f * log10f(mag + 1e-12f);
                }
                if (mag < peak2) {
                    peak2 = mag;
                    mindB = 20.0f * log10f(mag + 1e-12f);
                }
            }

            float gainClamp = maxdB - mindB;
            if (gainClamp > maxGain) gainClamp = maxGain;

            boostdB = gainClamp * gainSmooth + boostdB * minusGainSmooth;
            smoothMaxFreq = currentMaxFreq * maxSmooth + smoothMaxFreq * minusMaxSmooth;

            svf[0].update(fs, smoothMaxFreq, 1.0f / (2.0f * (1.0f - 0.75f)), powf(10.0f, boostdB / 20.0f));
        }

        float dlL = dL[0].process(inL);
        float dlR = dL[1].process(inR);
        svf[0].processStereo(svf[1], dlL, dlR, inL, inR);
    }
};

struct TubeEngine {
    float dcBlockL = 0.0f, dcBlockR = 0.0f;
    float prevL = 0.0f, prevR = 0.0f;

    inline void process(float& inL, float& inR) {
        auto triodeShape = [](float x) {
            if (x > 0.0f) {
                return x / (1.0f + x);
            } else {
                return x / (1.0f - x * 1.5f);
            }
        };

        float yL = triodeShape(inL * 2.0f);
        float yR = triodeShape(inR * 2.0f);

        dcBlockL = yL - prevL + 0.995f * dcBlockL;
        dcBlockR = yR - prevR + 0.995f * dcBlockR;
        prevL = yL;
        prevR = yR;

        inL = inL * 0.5f + (dcBlockL * 0.4f);
        inR = inR * 0.5f + (dcBlockR * 0.4f);
    }

    void reset() {
        dcBlockL = dcBlockR = prevL = prevR = 0.0f;
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

    DBBEngine dbb;
    float lastBassGain = -1.0f;

    TubeEngine tubeSat;

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
        dbb.reset();
        tubeSat.reset();
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
    bool doBass = bassEnabled && bassGain > 0.0f;

    if (doBass) {
        if (bassGain != engine->lastBassGain) {
            engine->lastBassGain = bassGain;
            engine->dbb.update((float)sampleRate, bassGain);
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
            engine->dbb.processSample(inL, inR);
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
            engine->tubeSat.process(inL, inR);
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