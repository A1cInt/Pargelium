#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
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
}

#define TAG "JamesDSP-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

static int gInitCount = 0;

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

    float targetRate = (float)sampleRate;
    if (targetRate > 48000.0f) targetRate = 48000.0f;
    if (targetRate < 44100.0f) targetRate = 44100.0f;

    JamesDSPInit(jdsp, 4096, targetRate);

    LOGD("JamesDSP engine initialized for sample rate %d (active: %.0f Hz)", sampleRate, targetRate);
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
LOGD("JamesDSP engine released");
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeProcess(JNIEnv *env, jobject thiz, jlong handle, jfloatArray buffer, jint numFrames) {
if (handle == 0 || numFrames <= 0) return;
JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);

jfloat *buf = env->GetFloatArrayElements(buffer, nullptr);
if (!buf) return;

int framesToProcess = numFrames > 4096 ? 4096 : numFrames;

for (int i = 0; i < framesToProcess; ++i) {
jdsp->tmpBuffer[0][i] = buf[i * 2];
jdsp->tmpBuffer[1][i] = buf[i * 2 + 1];
}

JamesDSPProcess(jdsp, (size_t)framesToProcess);

for (int i = 0; i < framesToProcess; ++i) {
buf[i * 2] = jdsp->tmpBuffer[0][i];
buf[i * 2 + 1] = jdsp->tmpBuffer[1][i];
}

env->ReleaseFloatArrayElements(buffer, buf, 0);
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetBass(JNIEnv *env, jobject thiz, jlong handle, jboolean enabled, jint gainDb) {
if (handle == 0) return;
JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);
if (enabled) {
BassBoostEnable(jdsp);
BassBoostSetParam(jdsp, (float)gainDb / 10.0f);
} else {
BassBoostDisable(jdsp);
}
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetEqualizer(JNIEnv *env, jobject thiz, jlong handle, jboolean enabled, jfloatArray gains) {
if (handle == 0) return;
JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);
MultimodalEqualizerEnable(jdsp, enabled ? 1 : 0);

if (enabled && gains != nullptr) {
jsize len = env->GetArrayLength(gains);
jfloat *g = env->GetFloatArrayElements(gains, nullptr);
double stdFreqs[15] = {25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000};
double dGains[15];
for (int i = 0; i < 15; ++i) {
dGains[i] = (i < len) ? (double)g[i] : 0.0;
}
MultimodalEqualizerAxisInterpolation(jdsp, 0, 3, stdFreqs, dGains);
env->ReleaseFloatArrayElements(gains, g, 0);
}
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetTube(JNIEnv *env, jobject thiz, jlong handle, jboolean enabled, jint drive) {
if (handle == 0) return;
JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);
if (enabled) {
VacuumTubeEnable(jdsp);
VacuumTubeSetGain(jdsp, (double)drive / 100.0);
} else {
VacuumTubeDisable(jdsp);
}
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetReverb(JNIEnv *env, jobject thiz, jlong handle, jboolean enabled, jint preset) {
if (handle == 0) return;
JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);
if (enabled) {
ReverbEnable(jdsp);
Reverb_SetParam(jdsp, preset);
} else {
ReverbDisable(jdsp);
}
}

JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetStereoWide(JNIEnv *env, jobject thiz, jlong handle, jboolean enabled, jint width) {
if (handle == 0) return;
JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);
if (enabled) {
StereoEnhancementEnable(jdsp);
StereoEnhancementSetParam(jdsp, (float)width / 100.0f);
} else {
StereoEnhancementDisable(jdsp);
}
}

// 6. Crossfeed (BS2B)
JNIEXPORT void JNICALL
Java_com_alcint_pargelium_audio_JamesDspController_nativeSetCrossfeed(JNIEnv *env, jobject thiz, jlong handle, jboolean enabled, jint mode) {
if (handle == 0) return;
JamesDSPLib *jdsp = reinterpret_cast<JamesDSPLib *>(handle);
CrossfeedEnable(jdsp, enabled ? (char)mode : (char)0);
}

} // extern "C"