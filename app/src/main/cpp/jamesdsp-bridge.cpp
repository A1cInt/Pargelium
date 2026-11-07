#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <math.h>
#include <pthread.h>
#include <android/log.h>

extern "C" {
#include "jdsp_header.h"

extern void JamesDSPGlobalMemoryAllocation(void);
extern void JamesDSPGlobalMemoryDeallocation(void);
extern void JamesDSPInit(JamesDSPLib *jdsp, int blockSizeMax, float sample_rate);
extern void JamesDSPFree(JamesDSPLib *jdsp);
extern void JamesDSPProcess(JamesDSPLib *jdsp, size_t n);

extern void JDSP_LiveProgSetConfig(int enabled, int mode, const float *params, int paramCount, float sampleRate);
extern void JDSP_LiveProgProcessInterleaved(float *buffer, int numFrames, float sampleRate);
}

#define TAG "JamesDSP-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define MAX_BLOCK_SIZE 2048

struct NativeJdspEngine {
    JamesDSPLib jdsp;
    pthread_mutex_t lock;
    float sampleRate;
    int lastCompGranularity;
    int lastCompTfRes;
    int eqInitialized;
    int arbEqInitialized;
    int compInitialized;
};

static pthread_mutex_t gGlobalAllocLock = PTHREAD_MUTEX_INITIALIZER;
static int gInitCount = 0;

static const double kUiFreqs10[10] = {
        31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0
};

static const double kIsoFreqs15[15] = {
        25.0, 40.0, 63.0, 100.0, 160.0, 250.0, 400.0, 630.0,
        1000.0, 1600.0, 2500.0, 4000.0, 6300.0, 10000.0, 16000.0
};

static const double kCompanderFreqs7[7] = {
        95.0, 200.0, 400.0, 800.0, 1600.0, 3400.0, 7500.0
};

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
    pthread_mutex_lock(&gGlobalAllocLock);
    if (gInitCount == 0) {
        JamesDSPGlobalMemoryAllocation();
    }
    gInitCount++;
    pthread_mutex_unlock(&gGlobalAllocLock);

    NativeJdspEngine *engine = (NativeJdspEngine *)calloc(1, sizeof(NativeJdspEngine));
    if (!engine) return 0;

    pthread_mutex_init(&engine->lock, nullptr);

    float targetRate = (float)sampleRate;
    if (targetRate < 8000.0f) targetRate = 48000.0f;
    if (targetRate > 192000.0f) targetRate = 192000.0f;
    engine->sampleRate = targetRate;
    engine->lastCompGranularity = -1;
    engine->lastCompTfRes = -1;

    JamesDSPInit(&engine->jdsp, MAX_BLOCK_SIZE, targetRate);
    JLimiterSetCoefficients(&engine->jdsp, -0.5, 60.0);
    JamesDSPSetPostGain(&engine->jdsp, 0.0);

    LOGD("JamesDSP Full Engine initialized: %.0f Hz", targetRate);
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeRelease(JNIEnv *env, jobject thiz, jlong handle) {
    if (handle == 0) return;
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);

    pthread_mutex_lock(&engine->lock);
    JamesDSPFree(&engine->jdsp);
    pthread_mutex_unlock(&engine->lock);
    pthread_mutex_destroy(&engine->lock);
    free(engine);

    pthread_mutex_lock(&gGlobalAllocLock);
    if (gInitCount > 0) {
        gInitCount--;
        if (gInitCount == 0) {
            JamesDSPGlobalMemoryDeallocation();
        }
    }
    pthread_mutex_unlock(&gGlobalAllocLock);
    LOGD("JamesDSP Full Engine released");
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
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);

    float safePreAmp = preAmpMultiplier;
    if (!isfinite(safePreAmp) || safePreAmp < 0.05f || safePreAmp > 4.0f) {
        safePreAmp = 1.0f;
    }

    if (pthread_mutex_trylock(&engine->lock) != 0) {
        return;
    }

    jfloat *buf = (jfloat *)env->GetPrimitiveArrayCritical(buffer, nullptr);
    if (!buf) {
        pthread_mutex_unlock(&engine->lock);
        return;
    }

    JDSP_LiveProgProcessInterleaved(buf, numFrames, engine->sampleRate);

    JamesDSPLib *jdsp = &engine->jdsp;
    int offsetFrame = 0;
    while (offsetFrame < numFrames) {
        int chunkFrames = numFrames - offsetFrame;
        if (chunkFrames > MAX_BLOCK_SIZE) {
            chunkFrames = MAX_BLOCK_SIZE;
        }

        float inEnergy = 0.0f;
        for (int i = 0; i < chunkFrames; ++i) {
            int idx = (offsetFrame + i) * 2;
            float l = buf[idx] * safePreAmp;
            float r = buf[idx + 1] * safePreAmp;

            if (!isfinite(l) || fabsf(l) < 1e-20f) l = 0.0f;
            if (!isfinite(r) || fabsf(r) < 1e-20f) r = 0.0f;

            inEnergy += fabsf(l) + fabsf(r);
            jdsp->tmpBuffer[0][i] = l;
            jdsp->tmpBuffer[1][i] = r;
        }

        JamesDSPProcess(jdsp, (size_t)chunkFrames);

        float outEnergy = 0.0f;
        for (int i = 0; i < chunkFrames; ++i) {
            float outL = jdsp->tmpBuffer[0][i];
            float outR = jdsp->tmpBuffer[1][i];
            if (isfinite(outL) && isfinite(outR)) {
                outEnergy += fabsf(outL) + fabsf(outR);
            }
        }

        // Если на входе есть звук, а после JamesDSPProcess получился NaN или полный 0 из-за переходного процесса FIR,
        // сохраняем мягкий сухой сигнал, чтобы звук НИКОГДА не пропадал!
        if (inEnergy > 0.001f && outEnergy < 1e-9f) {
            for (int i = 0; i < chunkFrames; ++i) {
                int idx = (offsetFrame + i) * 2;
                buf[idx] *= safePreAmp;
                buf[idx + 1] *= safePreAmp;
            }
        } else {
            for (int i = 0; i < chunkFrames; ++i) {
                int idx = (offsetFrame + i) * 2;
                float outL = jdsp->tmpBuffer[0][i];
                float outR = jdsp->tmpBuffer[1][i];

                if (!isfinite(outL)) outL = 0.0f;
                if (!isfinite(outR)) outR = 0.0f;

                buf[idx] = outL;
                buf[idx + 1] = outR;
            }
        }

        offsetFrame += chunkFrames;
    }

    env->ReleasePrimitiveArrayCritical(buffer, buf, 0);
    pthread_mutex_unlock(&engine->lock);
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
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);
    pthread_mutex_lock(&engine->lock);
    if (enabled && gainDb > 0.05f) {
        float clampedDb = gainDb > 15.0f ? 15.0f : gainDb;
        BassBoostEnable(&engine->jdsp);
        BassBoostSetParam(&engine->jdsp, clampedDb);
    } else {
        BassBoostDisable(&engine->jdsp);
    }
    pthread_mutex_unlock(&engine->lock);
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetCompander(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jboolean enabled,
        jfloat timeConstant,
        jint granularity,
        jint tfResolution,
        jfloatArray gains7
) {
    if (handle == 0) return;
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);
    pthread_mutex_lock(&engine->lock);

    if (enabled && gains7 != nullptr) {
        jsize len = env->GetArrayLength(gains7);
        jfloat *g = env->GetFloatArrayElements(gains7, nullptr);
        if (g) {
            double freqs[7];
            double dGains[7];
            memcpy(freqs, kCompanderFreqs7, sizeof(kCompanderFreqs7));
            for (int i = 0; i < 7; ++i) {
                double val = (i < len) ? (double)g[i] : 0.0;
                if (val > 12.0) val = 12.0;
                if (val < -12.0) val = -12.0;
                dGains[i] = val;
            }
            env->ReleaseFloatArrayElements(gains7, g, JNI_ABORT);

            float safeTc = timeConstant < 0.02f ? 0.02f : (timeConstant > 2.0f ? 2.0f : timeConstant);
            int safeGran = granularity < 1 ? 1 : (granularity > 4 ? 4 : granularity);
            int safeTf = tfResolution < 0 ? 0 : (tfResolution > 3 ? 3 : tfResolution);

            char needRefresh = (!engine->compInitialized ||
                                engine->lastCompGranularity != safeGran ||
                                engine->lastCompTfRes != safeTf) ? 1 : 0;

            if (!engine->compInitialized) {
                CompressorEnable(&engine->jdsp, 1);
                engine->compInitialized = 1;
            }
            CompressorSetParam(&engine->jdsp, safeTc, safeGran, safeTf, needRefresh);
            CompressorSetGain(&engine->jdsp, freqs, dGains, 1);

            engine->lastCompGranularity = safeGran;
            engine->lastCompTfRes = safeTf;
        }
    } else {
        CompressorDisable(&engine->jdsp);
        engine->compInitialized = 0;
    }

    pthread_mutex_unlock(&engine->lock);
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetEqualizer(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jboolean enabled,
        jint filterMode,
        jint interpolationMode,
        jfloatArray gains
) {
    if (handle == 0) return;
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);
    pthread_mutex_lock(&engine->lock);

    if (enabled && gains != nullptr) {
        jsize len = env->GetArrayLength(gains);
        jfloat *g = env->GetFloatArrayElements(gains, nullptr);
        if (g) {
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
            env->ReleaseFloatArrayElements(gains, g, JNI_ABORT);

            for (int i = 0; i < 15; ++i) {
                if (dGains[i] > 15.0) dGains[i] = 15.0;
                if (dGains[i] < -15.0) dGains[i] = -15.0;
            }

            int safeInterp = (interpolationMode == 1) ? 1 : 0;
            // По умолчанию в MultimodalEqualizer: 0 = MinPhase FIR, 1 = Exact LinPhase, 2..6 = IIR
            int safeOpMode = filterMode < 0 ? 0 : (filterMode > 6 ? 6 : filterMode);

            // ВАЖНО: Сначала включаем MultimodalEqualizerEnable, затем рассчитываем фильтр!
            if (!engine->eqInitialized) {
                MultimodalEqualizerEnable(&engine->jdsp, 1);
                engine->eqInitialized = 1;
            }
            MultimodalEqualizerAxisInterpolation(&engine->jdsp, safeInterp, safeOpMode, stdFreqs, dGains);
        }
    } else {
        MultimodalEqualizerDisable(&engine->jdsp);
        engine->eqInitialized = 0;
    }

    pthread_mutex_unlock(&engine->lock);
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetArbitraryEq(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jboolean enabled,
        jstring graphicEqString
) {
    if (handle == 0) return;
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);
    pthread_mutex_lock(&engine->lock);

    if (enabled && graphicEqString != nullptr) {
        const char *rawStr = env->GetStringUTFChars(graphicEqString, nullptr);
        if (rawStr && strlen(rawStr) > 3) {
            char *mutableCopy = strdup(rawStr);
            if (mutableCopy) {
                if (!engine->arbEqInitialized) {
                    ArbitraryResponseEqualizerEnable(&engine->jdsp, 1);
                    engine->arbEqInitialized = 1;
                }
                ArbitraryResponseEqualizerStringParser(&engine->jdsp, mutableCopy);
                free(mutableCopy);
            }
        } else {
            ArbitraryResponseEqualizerDisable(&engine->jdsp);
            engine->arbEqInitialized = 0;
        }
        if (rawStr) env->ReleaseStringUTFChars(graphicEqString, rawStr);
    } else {
        ArbitraryResponseEqualizerDisable(&engine->jdsp);
        engine->arbEqInitialized = 0;
    }

    pthread_mutex_unlock(&engine->lock);
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
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);
    pthread_mutex_lock(&engine->lock);

    if (enabled && driveDb > 0.05f) {
        double scaledGain = (double)driveDb * 100.0;
        if (scaledGain > 1200.0) scaledGain = 1200.0;
        VacuumTubeSetGain(&engine->jdsp, scaledGain);
        VacuumTubeEnable(&engine->jdsp);
    } else {
        VacuumTubeDisable(&engine->jdsp);
    }

    pthread_mutex_unlock(&engine->lock);
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
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);
    pthread_mutex_lock(&engine->lock);

    if (enabled) {
        int safePreset = preset < 0 ? 0 : (preset > 11 ? 11 : preset);
        Reverb_SetParam(&engine->jdsp, safePreset);
        ReverbEnable(&engine->jdsp);
    } else {
        ReverbDisable(&engine->jdsp);
    }

    pthread_mutex_unlock(&engine->lock);
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
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);
    pthread_mutex_lock(&engine->lock);

    if (enabled) {
        float normalized = ((float)width / 100.0f) * 0.85f;
        if (normalized < 0.05f) normalized = 0.05f;
        if (normalized > 0.90f) normalized = 0.90f;
        StereoEnhancementEnable(&engine->jdsp);
        StereoEnhancementSetParam(&engine->jdsp, normalized);
    } else {
        StereoEnhancementDisable(&engine->jdsp);
    }

    pthread_mutex_unlock(&engine->lock);
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
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);
    pthread_mutex_lock(&engine->lock);

    if (enabled) {
        int safeMode = mode < 0 ? 0 : (mode > 5 ? 5 : mode);
        CrossfeedEnable(&engine->jdsp, 1);
        CrossfeedChangeMode(&engine->jdsp, safeMode);
    } else {
        CrossfeedDisable(&engine->jdsp);
    }

    pthread_mutex_unlock(&engine->lock);
}

JNIEXPORT jint JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetDdc(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jboolean enabled,
        jstring vdcContent
) {
    if (handle == 0) return 0;
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);
    pthread_mutex_lock(&engine->lock);

    int status = 0;
    if (enabled && vdcContent != nullptr) {
        const char *rawVdc = env->GetStringUTFChars(vdcContent, nullptr);
        if (rawVdc && strlen(rawVdc) > 5) {
            char *mutableCopy = strdup(rawVdc);
            if (mutableCopy) {
                int parsed = DDCStringParser(&engine->jdsp, mutableCopy);
                if (parsed > 0) {
                    status = DDCEnable(&engine->jdsp, 1);
                } else {
                    DDCDisable(&engine->jdsp);
                }
                free(mutableCopy);
            }
        } else {
            DDCDisable(&engine->jdsp);
        }
        if (rawVdc) env->ReleaseStringUTFChars(vdcContent, rawVdc);
    } else {
        DDCDisable(&engine->jdsp);
    }

    pthread_mutex_unlock(&engine->lock);
    return status;
}

JNIEXPORT jint JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetConvolver(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jboolean enabled,
        jfloatArray impulseSamples,
        jint channels,
        jint frameCount
) {
    if (handle == 0) return 0;
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);
    pthread_mutex_lock(&engine->lock);

    int status = 0;
    if (enabled && impulseSamples != nullptr && frameCount > 0 && (channels == 1 || channels == 2 || channels == 4)) {
        jfloat *imp = env->GetFloatArrayElements(impulseSamples, nullptr);
        if (imp) {
            Convolver1DDisable(&engine->jdsp);
            status = Convolver1DLoadImpulseResponse(
                    &engine->jdsp,
                    imp,
                    (unsigned int)channels,
                    (size_t)frameCount,
                    1
            );
            Convolver1DEnable(&engine->jdsp);
            env->ReleaseFloatArrayElements(impulseSamples, imp, JNI_ABORT);
        }
    } else {
        Convolver1DDisable(&engine->jdsp);
    }

    pthread_mutex_unlock(&engine->lock);
    return status;
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetLiveprog(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jboolean enabled,
        jint mode,
        jfloatArray params,
        jstring eelCode
) {
    if (handle == 0) return;
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);
    pthread_mutex_lock(&engine->lock);

    if (eelCode != nullptr) {
        const char *rawCode = env->GetStringUTFChars(eelCode, nullptr);
        if (rawCode) {
            LiveProgStringParser(&engine->jdsp, const_cast<char *>(rawCode));
            env->ReleaseStringUTFChars(eelCode, rawCode);
        }
    }

    float pArr[8] = { 0 };
    int pLen = 0;
    if (params != nullptr) {
        pLen = env->GetArrayLength(params);
        if (pLen > 8) pLen = 8;
        jfloat *elements = env->GetFloatArrayElements(params, nullptr);
        if (elements) {
            for (int i = 0; i < pLen; ++i) pArr[i] = elements[i];
            env->ReleaseFloatArrayElements(params, elements, JNI_ABORT);
        }
    }

    JDSP_LiveProgSetConfig(enabled ? 1 : 0, mode, pArr, pLen, engine->sampleRate);
    if (enabled) {
        LiveProgEnable(&engine->jdsp);
    } else {
        LiveProgDisable(&engine->jdsp);
    }

    pthread_mutex_unlock(&engine->lock);
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
    NativeJdspEngine *engine = reinterpret_cast<NativeJdspEngine *>(handle);
    pthread_mutex_lock(&engine->lock);

    double safeThresh = (double)thresholdDb;
    if (safeThresh > -0.05) safeThresh = -0.05;
    if (safeThresh < -24.0) safeThresh = -24.0;

    double safeRelease = (double)releaseMs;
    if (safeRelease < 5.0) safeRelease = 5.0;
    if (safeRelease > 500.0) safeRelease = 500.0;

    double safePostGain = (double)postGainDb;
    if (safePostGain > 15.0) safePostGain = 15.0;
    if (safePostGain < -15.0) safePostGain = -15.0;

    JLimiterSetCoefficients(&engine->jdsp, safeThresh, safeRelease);
    JamesDSPSetPostGain(&engine->jdsp, safePostGain);

    pthread_mutex_unlock(&engine->lock);
}

} // extern "C"
