#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <math.h>
#include <android/log.h>

extern "C" {
#include "jdsp_header.h"

extern void JamesDSPGlobalMemoryAllocation(void);
extern void JamesDSPGlobalMemoryDeallocation(void);
extern void JamesDSPInit(JamesDSPLib *jdsp, int blockSizeMax, float sample_rate);
extern void JamesDSPFree(JamesDSPLib *jdsp);
extern void JamesDSPProcess(JamesDSPLib *jdsp, size_t n);

extern void BassBoostEnable(JamesDSPLib *jdsp);
extern void BassBoostDisable(JamesDSPLib *jdsp);
extern void BassBoostSetParam(JamesDSPLib *jdsp, float maxG);

extern void MultimodalEqualizerEnable(JamesDSPLib *jdsp, char enable);
extern void MultimodalEqualizerAxisInterpolation(JamesDSPLib *jdsp, int interpolationMode, int operatingMode, double *freqAx, double *gaindB);

extern void VacuumTubeEnable(JamesDSPLib *jdsp);
extern void VacuumTubeDisable(JamesDSPLib *jdsp);
extern void VacuumTubeSetGain(JamesDSPLib *jdsp, double gain);

extern void ReverbEnable(JamesDSPLib *jdsp);
extern void ReverbDisable(JamesDSPLib *jdsp);
extern void Reverb_SetParam(JamesDSPLib *jdsp, int preset);

extern void StereoEnhancementEnable(JamesDSPLib *jdsp);
extern void StereoEnhancementDisable(JamesDSPLib *jdsp);
extern void StereoEnhancementSetParam(JamesDSPLib *jdsp, float mix);

extern void CrossfeedEnable(JamesDSPLib *jdsp, char enable);
extern void CrossfeedChangeMode(JamesDSPLib *jdsp, int nMode);

extern void JLimiterSetCoefficients(JamesDSPLib *jdsp, double threshold, double release);
extern void JamesDSPSetPostGain(JamesDSPLib *jdsp, double postGaindB);
}

#define TAG "JamesDSP-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define MAX_BLOCK_SIZE 4096

static int gInitCount = 0;

// Частоты 10-полосного эквалайзера из UI и 15 стандартных частот ISO для libjamesdsp
static const double kUiFreqs10[10] = {
        31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0
};

static const double kIsoFreqs15[15] = {
        25.0, 40.0, 63.0, 100.0, 160.0, 250.0, 400.0, 630.0,
        1000.0, 1600.0, 2500.0, 4000.0, 6300.0, 10000.0, 16000.0
};

// Логарифмическая интерполяция из 10 полос UI в 15 полос ISO движка JamesDSP
static void interpolate10To15Bands(const jfloat *in10, double *out15) {
    for (int i = 0; i < 15; ++i) {
        double f = kIsoFreqs15[i];
        if (f <= kUiFreqs10[0]) {
            out15[i] = (double)in10[0];
        } else if (f >= kUiFreqs10[9]) {
            out15[i] = (double)in10[9];
        } else {
            for (int j = 0; j < 9; ++j) {
                if (f >= kUiFreqs10[j] && f <= kUiFreqs10[j + 1]) {
                    double logF = log(f);
                    double logF0 = log(kUiFreqs10[j]);
                    double logF1 = log(kUiFreqs10[j + 1]);
                    double t = (logF - logF0) / (logF1 - logF0);
                    out15[i] = (double)in10[j] + t * ((double)in10[j + 1] - (double)in10[j]);
                    break;
                }
            }
        }
    }
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeInit(JNIEnv *env, jobject thiz, jint sampleRate) {
    if (gInitCount == 0) {
        JamesDSPGlobalMemoryAllocation();
    }
    gInitCount++;

    JamesDSPLib *jdsp = (JamesDSPLib *)malloc(sizeof(JamesDSPLib));
    if (!jdsp) return 0;

    memset(jdsp, 0, sizeof(JamesDSPLib));

    // Поддержка реального Hi-Res аудио (44.1 kHz .. 192 kHz) без принудительного даунсемплинга коэффициентов
    float targetRate = (float)sampleRate;
    if (targetRate < 8000.0f) targetRate = 44100.0f;
    if (targetRate > 192000.0f) targetRate = 192000.0f;

    JamesDSPInit(jdsp, MAX_BLOCK_SIZE, targetRate);

    // Дефолтная инициализация лимитера для предотвращения жесткого клиппинга
    JLimiterSetCoefficients(jdsp, -0.5, 60.0);
    JamesDSPSetPostGain(jdsp, 0.0);

    LOGD("JamesDSP initialized: sampleRate=%d (engine=%.0f Hz)", sampleRate, targetRate);
    return reinterpret_cast<jlong>(jdsp);
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeRelease(JNIEnv *env, jobject thiz, jlong handle) {
    if (handle == 0) return;
    JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);
    JamesDSPFree(jdsp);
    free(jdsp);

    if (gInitCount > 0) {
        gInitCount--;
        if (gInitCount == 0) {
            JamesDSPGlobalMemoryDeallocation();
        }
    }
    LOGD("JamesDSP released");
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeProcess(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jfloatArray buffer,
        jint numFrames,
        jfloat preAmpMultiplier
) {
    if (handle == 0 || numFrames <= 0) return;
    JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);

    jfloat *buf = (jfloat *)env->GetPrimitiveArrayCritical(buffer, nullptr);
    if (!buf) return;

    int offsetFrame = 0;
    while (offsetFrame < numFrames) {
        int chunkFrames = numFrames - offsetFrame;
        if (chunkFrames > MAX_BLOCK_SIZE) {
            chunkFrames = MAX_BLOCK_SIZE;
        }

        for (int i = 0; i < chunkFrames; ++i) {
            int idx = (offsetFrame + i) * 2;
            float l = buf[idx] * preAmpMultiplier;
            float r = buf[idx + 1] * preAmpMultiplier;

            // Защита от NaN / Denormals на входе
            if (!isfinite(l) || fabsf(l) < 1e-20f) l = 0.0f;
            if (!isfinite(r) || fabsf(r) < 1e-20f) r = 0.0f;

            jdsp->tmpBuffer[0][i] = l;
            jdsp->tmpBuffer[1][i] = r;
        }

        JamesDSPProcess(jdsp, (size_t)chunkFrames);

        for (int i = 0; i < chunkFrames; ++i) {
            int idx = (offsetFrame + i) * 2;
            float outL = jdsp->tmpBuffer[0][i];
            float outR = jdsp->tmpBuffer[1][i];

            if (!isfinite(outL)) outL = 0.0f;
            if (!isfinite(outR)) outR = 0.0f;

            buf[idx] = outL;
            buf[idx + 1] = outR;
        }

        offsetFrame += chunkFrames;
    }

    env->ReleasePrimitiveArrayCritical(buffer, buf, 0);
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetBass(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jboolean enabled,
        jfloat gainDb
) {
    if (handle == 0) return;
    JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);
    if (enabled && gainDb > 0.05f) {
        float clampedDb = gainDb > 15.0f ? 15.0f : gainDb;
        BassBoostEnable(jdsp);
        BassBoostSetParam(jdsp, clampedDb);
    } else {
        BassBoostDisable(jdsp);
    }
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetEqualizer(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jboolean enabled,
        jfloatArray gains
) {
    if (handle == 0) return;
    JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);

    if (enabled && gains != nullptr) {
        jsize len = env->GetArrayLength(gains);
        jfloat *g = env->GetFloatArrayElements(gains, nullptr);
        if (!g) return;

        double stdFreqs[15];
        double dGains[15];
        memcpy(stdFreqs, kIsoFreqs15, sizeof(kIsoFreqs15));

        if (len == 10) {
            interpolate10To15Bands(g, dGains);
        } else {
            for (int i = 0; i < 15; ++i) {
                dGains[i] = (i < len) ? (double)g[i] : 0.0;
            }
        }

        // Ограничиваем экстремальные пики, чтобы FIR/IIR фильтр не уходил в резонанс
        for (int i = 0; i < 15; ++i) {
            if (dGains[i] > 15.0) dGains[i] = 15.0;
            if (dGains[i] < -15.0) dGains[i] = -15.0;
        }

        MultimodalEqualizerAxisInterpolation(jdsp, 0, 2, stdFreqs, dGains);
        MultimodalEqualizerEnable(jdsp, 1);
        env->ReleaseFloatArrayElements(gains, g, JNI_ABORT);
    } else {
        MultimodalEqualizerEnable(jdsp, 0);
    }
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetTube(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jboolean enabled,
        jfloat driveDb
) {
    if (handle == 0) return;
    JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);
    if (enabled && driveDb > 0.05f) {
        // В VacuumTube.c функция VacuumTubeSetGain внутри умножает входное значение на 100.0f (db2mag),
        // либо ждет dB в диапазоне 0.0 .. 6.0. Масштабируем 0..10 плавно и без перегруза:
        double safeDrive = (double)(driveDb * 0.55f);
        if (safeDrive > 6.0) safeDrive = 6.0;
        VacuumTubeSetGain(jdsp, safeDrive);
        VacuumTubeEnable(jdsp);
    } else {
        VacuumTubeDisable(jdsp);
    }
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetReverb(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jboolean enabled,
        jint preset
) {
    if (handle == 0) return;
    JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);
    if (enabled) {
        int safePreset = preset < 0 ? 0 : (preset > 11 ? 11 : preset);
        Reverb_SetParam(jdsp, safePreset);
        ReverbEnable(jdsp);
    } else {
        ReverbDisable(jdsp);
    }
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetStereoWide(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jboolean enabled,
        jint width
) {
    if (handle == 0) return;
    JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);
    if (enabled) {
        // StereoEnhancementSetParam ждет значение 0.0 .. 1.0, но выше 0.85 начинается противофаза
        float normalized = ((float)width / 100.0f) * 0.82f;
        if (normalized < 0.0f) normalized = 0.0f;
        if (normalized > 0.85f) normalized = 0.85f;
        StereoEnhancementSetParam(jdsp, normalized);
        StereoEnhancementEnable(jdsp);
    } else {
        StereoEnhancementDisable(jdsp);
    }
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetCrossfeed(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jboolean enabled,
        jint mode
) {
    if (handle == 0) return;
    JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);
    if (enabled) {
        int safeMode = mode < 0 ? 0 : (mode > 5 ? 5 : mode);
        CrossfeedChangeMode(jdsp, safeMode);
        CrossfeedEnable(jdsp, 1);
    } else {
        CrossfeedEnable(jdsp, 0);
    }
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetLimiter(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jfloat thresholdDb,
        jfloat releaseMs,
        jfloat postGainDb
) {
    if (handle == 0) return;
    JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);

    double safeThresh = (double)thresholdDb;
    if (safeThresh > -0.05) safeThresh = -0.05;
    if (safeThresh < -24.0) safeThresh = -24.0;

    double safeRelease = (double)releaseMs;
    if (safeRelease < 5.0) safeRelease = 5.0;
    if (safeRelease > 500.0) safeRelease = 500.0;

    double safePostGain = (double)postGainDb;
    if (safePostGain > 12.0) safePostGain = 12.0;
    if (safePostGain < -15.0) safePostGain = -15.0;

    JLimiterSetCoefficients(jdsp, safeThresh, safeRelease);
    JamesDSPSetPostGain(jdsp, safePostGain);
}

} // extern "C"