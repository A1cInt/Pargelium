#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <math.h>
#include <pthread.h>
#include <android/log.h>

extern "C" {
#include "jdsp_header.h"
}

#define TAG "JamesDSP-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define MAX_BLOCK_SIZE 4096

struct NativeJdspEngine {
    JamesDSPLib jdsp;
    pthread_mutex_t lock;
    float sampleRate;
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

    // Если другой поток прямо сейчас пересчитывает тяжелый FIR/Convolver,
    // не блокируем аудио-нить ExoPlayer во избежание щелчков:
    if (pthread_mutex_trylock(&engine->lock) != 0) {
        if (fabsf(preAmpMultiplier - 1.0f) > 0.001f) {
            jfloat *buf = (jfloat *)env->GetPrimitiveArrayCritical(buffer, nullptr);
            if (buf) {
                int totalSamples = numFrames * 2;
                for (int i = 0; i < totalSamples; ++i) {
                    buf[i] *= preAmpMultiplier;
                }
                env->ReleasePrimitiveArrayCritical(buffer, buf, 0);
            }
        }
        return;
    }

    jfloat *buf = (jfloat *)env->GetPrimitiveArrayCritical(buffer, nullptr);
    if (!buf) {
        pthread_mutex_unlock(&engine->lock);
        return;
    }

    JamesDSPLib *jdsp = &engine->jdsp;
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
        BassBoostSetParam(&engine->jdsp, clampedDb);
        BassBoostEnable(&engine->jdsp);
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

            CompressorSetParam(&engine->jdsp, safeTc, safeGran, safeTf);
            CompressorSetGain(&engine->jdsp, freqs, dGains, 1);
            CompressorEnable(&engine->jdsp, 1);
        }
    } else {
        CompressorDisable(&engine->jdsp);
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
            int safeOpMode = filterMode < 0 ? 0 : (filterMode > 6 ? 6 : filterMode);

            MultimodalEqualizerAxisInterpolation(&engine->jdsp, safeInterp, safeOpMode, stdFreqs, dGains);
            MultimodalEqualizerEnable(&engine->jdsp, 1);
        }
    } else {
        MultimodalEqualizerDisable(&engine->jdsp);
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
                ArbitraryResponseEqualizerStringParser(&engine->jdsp, mutableCopy);
                ArbitraryResponseEqualizerEnable(&engine->jdsp, 1);
                free(mutableCopy);
            }
        } else {
            ArbitraryResponseEqualizerDisable(&engine->jdsp);
        }
        if (rawStr) env->ReleaseStringUTFChars(graphicEqString, rawStr);
    } else {
        ArbitraryResponseEqualizerDisable(&engine->jdsp);
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
        // В VacuumTube.c функция VacuumTubeSetGain делит входной аргумент на 100.0 (db2mag(dbGain / 100.0))
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
        float normalized = (float)width / 100.0f;
        if (normalized < 0.0f) normalized = 0.0f;
        if (normalized > 1.0f) normalized = 1.0f;
        StereoEnhancementDisable(&engine->jdsp);
        StereoEnhancementSetParam(&engine->jdsp, normalized);
        StereoEnhancementEnable(&engine->jdsp);
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
        CrossfeedDisable(&engine->jdsp);
        CrossfeedChangeMode(&engine->jdsp, safeMode);
        CrossfeedEnable(&engine->jdsp, 1);
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
    if (enabled && impulseSamples != nullptr && frameCount > 0 && (channels == 1 || channels == 2)) {
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
