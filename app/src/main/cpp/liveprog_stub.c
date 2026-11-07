#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <ctype.h>
#include <math.h>

#define DR_FLAC_IMPLEMENTATION
#define DR_FLAC_NO_STDIO
#include "dr_flac.h"

int XLITTLE_ENDIAN = 1;

static int isPowerOfTwo(int n) {
    return (n > 0) && ((n & (n - 1)) == 0);
}

/**
 * Точное преобразование Хартли H[k] = sum_{n=0}^{N-1} x[n] * (cos(2*pi*k*n/N) + sin(2*pi*k*n/N))
 * Так как стандартный прямой FFT считает sum x[n] * (cos(...) - i*sin(...)),
 * то Re(FFT) = sum x[n]*cos(...), а Im(FFT) = -sum x[n]*sin(...).
 * Поэтому cas(theta) = cos(theta) + sin(theta) = Re(FFT) - Im(FFT) при отрицательной экспоненте,
 * либо мы сразу используем положительный угол +2*pi/len и берем Re + Im!
 */
void discreteHartleyTransform(float *x, int n) {
    if (!x || n <= 1) return;

    if (!isPowerOfTwo(n)) {
        float *tmp = (float *)malloc((size_t)n * sizeof(float));
        if (!tmp) return;
        const double twoPiOverN = 6.28318530717958647692 / (double)n;
        for (int k = 0; k < n; ++k) {
            double sum = 0.0;
            for (int i = 0; i < n; ++i) {
                double angle = twoPiOverN * (double)(k * i);
                sum += (double)x[i] * (cos(angle) + sin(angle));
            }
            tmp[k] = (float)sum;
        }
        memcpy(x, tmp, (size_t)n * sizeof(float));
        free(tmp);
        return;
    }

    double *re = (double *)malloc((size_t)n * sizeof(double));
    double *im = (double *)calloc((size_t)n, sizeof(double));
    if (!re || !im) {
        free(re);
        free(im);
        return;
    }

    for (int i = 0; i < n; ++i) {
        re[i] = isfinite(x[i]) ? (double)x[i] : 0.0;
    }

    int j = 0;
    for (int i = 1; i < n; ++i) {
        int bit = n >> 1;
        while (j & bit) {
            j ^= bit;
            bit >>= 1;
        }
        j ^= bit;
        if (i < j) {
            double tr = re[i];
            re[i] = re[j];
            re[j] = tr;
        }
    }

    for (int len = 2; len <= n; len <<= 1) {
        int halfLen = len >> 1;
        double angle = 6.28318530717958647692 / (double)len;
        double wLenRe = cos(angle);
        double wLenIm = sin(angle);

        for (int i = 0; i < n; i += len) {
            for (int k = 0; k < halfLen; ++k) {
                double theta = angle * (double)k;
                double wRe = cos(theta);
                double wIm = sin(theta);

                int evenIdx = i + k;
                int oddIdx = i + k + halfLen;

                double uRe = re[evenIdx];
                double uIm = im[evenIdx];
                double vRe = re[oddIdx] * wRe - im[oddIdx] * wIm;
                double vIm = re[oddIdx] * wIm + im[oddIdx] * wRe;

                re[evenIdx] = uRe + vRe;
                im[evenIdx] = uIm + vIm;
                re[oddIdx] = uRe - vRe;
                im[oddIdx] = uIm - vIm;
            }
        }
    }

    for (int k = 0; k < n; ++k) {
        double val = re[k] + im[k];
        x[k] = isfinite(val) ? (float)val : 0.0f;
    }

    free(re);
    free(im);
}

void JamesDSPOfflineResampling(float const *in, float *out, size_t lenIn, size_t lenOut, int channels, double src_ratio) {
    if (!in || !out || lenIn == 0 || channels <= 0) return;

    size_t actualLenOut = lenOut;
    if (actualLenOut == 0 && src_ratio > 0.0) {
        actualLenOut = (size_t)((double)lenIn * src_ratio);
    }
    if (actualLenOut == 0) return;

    if (lenIn == actualLenOut) {
        memcpy(out, in, lenIn * (size_t)channels * sizeof(float));
        return;
    }

    double step = (double)(lenIn - 1) / (double)(actualLenOut > 1 ? actualLenOut - 1 : 1);
    for (size_t i = 0; i < actualLenOut; ++i) {
        double srcPos = (double)i * step;
        size_t idx1 = (size_t)srcPos;
        float t = (float)(srcPos - (double)idx1);

        size_t idx0 = (idx1 > 0) ? (idx1 - 1) : 0;
        size_t idx2 = (idx1 + 1 < lenIn) ? (idx1 + 1) : (lenIn - 1);
        size_t idx3 = (idx1 + 2 < lenIn) ? (idx1 + 2) : (lenIn - 1);

        for (int ch = 0; ch < channels; ++ch) {
            float y0 = in[idx0 * (size_t)channels + (size_t)ch];
            float y1 = in[idx1 * (size_t)channels + (size_t)ch];
            float y2 = in[idx2 * (size_t)channels + (size_t)ch];
            float y3 = in[idx3 * (size_t)channels + (size_t)ch];

            float c0 = y1;
            float c1 = 0.5f * (y2 - y0);
            float c2 = y0 - 2.5f * y1 + 2.0f * y2 - 0.5f * y3;
            float c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2);

            out[i * (size_t)channels + (size_t)ch] = ((c3 * t + c2) * t + c1) * t + c0;
        }
    }
}

#define FDN_DELAY_LEN 8192

typedef struct {
    int enabled;
    int activeMode;
    float params[8];
    float sampleRate;

    float fdnLines[8][FDN_DELAY_LEN];
    int fdnWriteIdx[8];
    float fdnLpState[8];

    double b0, b1, b2, a1, a2;
    double x1L, x2L, y1L, y2L;
    double x1R, x2R, y1R, y2R;
    int subFlipL, subFlipR;
    float prevSubInL, prevSubInR;
    float subEnvL, subEnvR;

    float fastEnvL, slowEnvL;
    float fastEnvR, slowEnvR;
    float holdSampleL, holdSampleR;
    int holdCounter;
} PargeliumLiveProgState;

static PargeliumLiveProgState gLiveProg = {
    .enabled = 0,
    .activeMode = 0,
    .params = { 0.35f, 0.72f, 0.45f, 1.0f, 80.0f, 0.707f, 0.0f, 0.0f },
    .sampleRate = 48000.0f,
    .b0 = 1.0, .b1 = 0.0, .b2 = 0.0, .a1 = 0.0, .a2 = 0.0
};

static const int kFdnLengths[8] = { 1087, 1283, 1511, 1789, 2081, 2411, 2797, 3203 };

static void updateLiveProgBiquad(float sr, float freqHz, float qVal, int isLowPass) {
    if (sr < 8000.0f) sr = 48000.0f;
    double f = (double)freqHz;
    if (!isfinite(f) || f < 15.0) f = 55.0;
    if (f > sr * 0.45) f = sr * 0.45;
    double q = (double)qVal;
    if (!isfinite(q) || q < 0.25) q = 0.707;
    if (q > 8.0) q = 8.0;

    double w0 = 2.0 * 3.14159265358979323846 * f / (double)sr;
    double cosw0 = cos(w0);
    double sinw0 = sin(w0);
    double alpha = sinw0 / (2.0 * q);
    double a0 = 1.0 + alpha;

    if (isLowPass) {
        gLiveProg.b0 = ((1.0 - cosw0) * 0.5) / a0;
        gLiveProg.b1 = (1.0 - cosw0) / a0;
        gLiveProg.b2 = ((1.0 - cosw0) * 0.5) / a0;
    } else {
        gLiveProg.b0 = ((1.0 + cosw0) * 0.5) / a0;
        gLiveProg.b1 = -(1.0 + cosw0) / a0;
        gLiveProg.b2 = ((1.0 + cosw0) * 0.5) / a0;
    }
    gLiveProg.a1 = (-2.0 * cosw0) / a0;
    gLiveProg.a2 = (1.0 - alpha) / a0;
}

void JDSP_LiveProgSetConfig(int enabled, int mode, const float *params, int paramCount, float sampleRate) {
    gLiveProg.enabled = enabled;
    gLiveProg.activeMode = mode;
    if (sampleRate >= 8000.0f) {
        gLiveProg.sampleRate = sampleRate;
    }
    if (params && paramCount > 0) {
        int count = paramCount > 8 ? 8 : paramCount;
        for (int i = 0; i < count; ++i) {
            gLiveProg.params[i] = isfinite(params[i]) ? params[i] : 0.0f;
        }
    }

    if (mode == 1) {
        updateLiveProgBiquad(gLiveProg.sampleRate, gLiveProg.params[0], gLiveProg.params[1], 0);
    } else if (mode == 2) {
        updateLiveProgBiquad(gLiveProg.sampleRate, gLiveProg.params[0], 0.85f, 1);
    } else if (mode == 3) {
        updateLiveProgBiquad(gLiveProg.sampleRate, gLiveProg.params[0], 0.707f, 0);
    }
}

void JDSP_LiveProgProcessInterleaved(float *buffer, int numFrames, float sampleRate) {
    if (!gLiveProg.enabled || !buffer || numFrames <= 0) return;

    int mode = gLiveProg.activeMode;

    if (mode == 0) {
        float wetMix = gLiveProg.params[0];
        float decay = gLiveProg.params[1];
        float damp = gLiveProg.params[2];
        float roomScale = gLiveProg.params[3];

        if (wetMix < 0.0f || wetMix > 1.0f) wetMix = 0.35f;
        if (decay < 0.1f || decay > 0.96f) decay = 0.72f;
        if (damp < 0.05f || damp > 0.9f) damp = 0.42f;
        if (roomScale < 0.35f || roomScale > 1.8f) roomScale = 1.0f;

        float dryGain = 1.0f - wetMix * 0.35f;
        float normFactor = 0.35355339f * decay;

        for (int f = 0; f < numFrames; ++f) {
            float inL = buffer[f * 2];
            float inR = buffer[f * 2 + 1];

            float rd[8];
            for (int c = 0; c < 8; ++c) {
                int delaySamples = (int)((float)kFdnLengths[c] * roomScale);
                if (delaySamples < 64) delaySamples = 64;
                if (delaySamples >= FDN_DELAY_LEN - 2) delaySamples = FDN_DELAY_LEN - 2;

                int rIdx = gLiveProg.fdnWriteIdx[c] - delaySamples;
                if (rIdx < 0) rIdx += FDN_DELAY_LEN;

                float raw = gLiveProg.fdnLines[c][rIdx];
                if (!isfinite(raw)) raw = 0.0f;
                gLiveProg.fdnLpState[c] = raw * (1.0f - damp) + gLiveProg.fdnLpState[c] * damp;
                rd[c] = gLiveProg.fdnLpState[c];
            }

            float s0 = rd[0] + rd[1], s1 = rd[0] - rd[1];
            float s2 = rd[2] + rd[3], s3 = rd[2] - rd[3];
            float s4 = rd[4] + rd[5], s5 = rd[4] - rd[5];
            float s6 = rd[6] + rd[7], s7 = rd[6] - rd[7];

            float t0 = s0 + s2, t1 = s1 + s3, t2 = s0 - s2, t3 = s1 - s3;
            float t4 = s4 + s6, t5 = s5 + s7, t6 = s4 - s6, t7 = s5 - s7;

            float h[8];
            h[0] = (t0 + t4) * normFactor;
            h[1] = (t1 + t5) * normFactor;
            h[2] = (t2 + t6) * normFactor;
            h[3] = (t3 + t7) * normFactor;
            h[4] = (t0 - t4) * normFactor;
            h[5] = (t1 - t5) * normFactor;
            h[6] = (t2 - t6) * normFactor;
            h[7] = (t3 - t7) * normFactor;

            float excL = inL * 0.25f;
            float excR = inR * 0.25f;

            for (int c = 0; c < 8; ++c) {
                float inject = (c & 1) ? excR : excL;
                int wIdx = gLiveProg.fdnWriteIdx[c];
                gLiveProg.fdnLines[c][wIdx] = h[c] + inject;
                wIdx++;
                if (wIdx >= FDN_DELAY_LEN) wIdx = 0;
                gLiveProg.fdnWriteIdx[c] = wIdx;
            }

            float wetL = (rd[0] + rd[2] + rd[4] + rd[6]) * 0.5f;
            float wetR = (rd[1] + rd[3] + rd[5] + rd[7]) * 0.5f;

            buffer[f * 2] = inL * dryGain + wetL * wetMix;
            buffer[f * 2 + 1] = inR * dryGain + wetR * wetMix;
        }
    } else if (mode == 1) {
        float gainLinear = powf(10.0f, gLiveProg.params[2] / 20.0f);
        if (!isfinite(gainLinear) || gainLinear < 0.1f || gainLinear > 4.0f) gainLinear = 1.0f;

        for (int f = 0; f < numFrames; ++f) {
            double inL = (double)buffer[f * 2];
            double inR = (double)buffer[f * 2 + 1];

            double outL = gLiveProg.b0 * inL + gLiveProg.b1 * gLiveProg.x1L + gLiveProg.b2 * gLiveProg.x2L
                          - gLiveProg.a1 * gLiveProg.y1L - gLiveProg.a2 * gLiveProg.y2L;
            double outR = gLiveProg.b0 * inR + gLiveProg.b1 * gLiveProg.x1R + gLiveProg.b2 * gLiveProg.x2R
                          - gLiveProg.a1 * gLiveProg.y1R - gLiveProg.a2 * gLiveProg.y2R;

            if (!isfinite(outL)) outL = inL;
            if (!isfinite(outR)) outR = inR;

            gLiveProg.x2L = gLiveProg.x1L; gLiveProg.x1L = inL;
            gLiveProg.y2L = gLiveProg.y1L; gLiveProg.y1L = outL;

            gLiveProg.x2R = gLiveProg.x1R; gLiveProg.x1R = inR;
            gLiveProg.y2R = gLiveProg.y1R; gLiveProg.y1R = outR;

            buffer[f * 2] = (float)outL * gainLinear;
            buffer[f * 2 + 1] = (float)outR * gainLinear;
        }
    } else if (mode == 2) {
        float subMix = gLiveProg.params[1];
        float punch = gLiveProg.params[2];
        if (subMix < 0.0f || subMix > 1.0f) subMix = 0.45f;

        for (int f = 0; f < numFrames; ++f) {
            double inL = (double)buffer[f * 2];
            double inR = (double)buffer[f * 2 + 1];

            float lowL = (float)(gLiveProg.b0 * inL + gLiveProg.b1 * gLiveProg.x1L + gLiveProg.b2 * gLiveProg.x2L
                                 - gLiveProg.a1 * gLiveProg.y1L - gLiveProg.a2 * gLiveProg.y2L);
            float lowR = (float)(gLiveProg.b0 * inR + gLiveProg.b1 * gLiveProg.x1R + gLiveProg.b2 * gLiveProg.x2R
                                 - gLiveProg.a1 * gLiveProg.y1R - gLiveProg.a2 * gLiveProg.y2R);

            if (!isfinite(lowL)) lowL = 0.0f;
            if (!isfinite(lowR)) lowR = 0.0f;

            gLiveProg.x2L = gLiveProg.x1L; gLiveProg.x1L = inL;
            gLiveProg.y2L = gLiveProg.y1L; gLiveProg.y1L = lowL;
            gLiveProg.x2R = gLiveProg.x1R; gLiveProg.x1R = inR;
            gLiveProg.y2R = gLiveProg.y1R; gLiveProg.y1R = lowR;

            if (gLiveProg.prevSubInL <= 0.0f && lowL > 0.0f) gLiveProg.subFlipL = -gLiveProg.subFlipL;
            if (gLiveProg.prevSubInR <= 0.0f && lowR > 0.0f) gLiveProg.subFlipR = -gLiveProg.subFlipR;
            if (gLiveProg.subFlipL == 0) gLiveProg.subFlipL = 1;
            if (gLiveProg.subFlipR == 0) gLiveProg.subFlipR = 1;
            gLiveProg.prevSubInL = lowL;
            gLiveProg.prevSubInR = lowR;

            float subL = fabsf(lowL) * (float)gLiveProg.subFlipL * (1.0f + punch * 0.5f);
            float subR = fabsf(lowR) * (float)gLiveProg.subFlipR * (1.0f + punch * 0.5f);

            gLiveProg.subEnvL = gLiveProg.subEnvL * 0.96f + subL * 0.04f;
            gLiveProg.subEnvR = gLiveProg.subEnvR * 0.96f + subR * 0.04f;

            buffer[f * 2] = (float)inL + gLiveProg.subEnvL * subMix * 0.65f;
            buffer[f * 2 + 1] = (float)inR + gLiveProg.subEnvR * subMix * 0.65f;
        }
    } else if (mode == 3) {
        float drive = 1.0f + gLiveProg.params[1] * 4.0f;
        float mix = gLiveProg.params[2];
        for (int f = 0; f < numFrames; ++f) {
            double inL = (double)buffer[f * 2];
            double inR = (double)buffer[f * 2 + 1];

            double highL = gLiveProg.b0 * inL + gLiveProg.b1 * gLiveProg.x1L + gLiveProg.b2 * gLiveProg.x2L
                           - gLiveProg.a1 * gLiveProg.y1L - gLiveProg.a2 * gLiveProg.y2L;
            double highR = gLiveProg.b0 * inR + gLiveProg.b1 * gLiveProg.x1R + gLiveProg.b2 * gLiveProg.x2R
                           - gLiveProg.a1 * gLiveProg.y1R - gLiveProg.a2 * gLiveProg.y2R;

            if (!isfinite(highL)) highL = 0.0;
            if (!isfinite(highR)) highR = 0.0;

            gLiveProg.x2L = gLiveProg.x1L; gLiveProg.x1L = inL;
            gLiveProg.y2L = gLiveProg.y1L; gLiveProg.y1L = highL;
            gLiveProg.x2R = gLiveProg.x1R; gLiveProg.x1R = inR;
            gLiveProg.y2R = gLiveProg.y1R; gLiveProg.y1R = highR;

            float harmL = tanhf((float)highL * drive);
            float harmR = tanhf((float)highR * drive);

            buffer[f * 2] = (float)inL + harmL * mix * 0.35f;
            buffer[f * 2 + 1] = (float)inR + harmR * mix * 0.35f;
        }
    } else if (mode == 4) {
        float attackGain = gLiveProg.params[0];
        float sustainGain = gLiveProg.params[1];

        for (int f = 0; f < numFrames; ++f) {
            float inL = buffer[f * 2];
            float inR = buffer[f * 2 + 1];
            float absL = fabsf(inL);
            float absR = fabsf(inR);

            gLiveProg.fastEnvL = absL > gLiveProg.fastEnvL
                                 ? (gLiveProg.fastEnvL * 0.85f + absL * 0.15f)
                                 : (gLiveProg.fastEnvL * 0.995f);
            gLiveProg.slowEnvL = gLiveProg.slowEnvL * 0.9992f + absL * 0.0008f;

            gLiveProg.fastEnvR = absR > gLiveProg.fastEnvR
                                 ? (gLiveProg.fastEnvR * 0.85f + absR * 0.15f)
                                 : (gLiveProg.fastEnvR * 0.995f);
            gLiveProg.slowEnvR = gLiveProg.slowEnvR * 0.9992f + absR * 0.0008f;

            float diffL = gLiveProg.fastEnvL - gLiveProg.slowEnvL;
            float diffR = gLiveProg.fastEnvR - gLiveProg.slowEnvR;

            float gainL = 1.0f + (diffL > 0.0f ? diffL * attackGain * 2.5f : -diffL * sustainGain * 1.8f);
            float gainR = 1.0f + (diffR > 0.0f ? diffR * attackGain * 2.5f : -diffR * sustainGain * 1.8f);

            if (gainL < 0.2f) gainL = 0.2f;
            if (gainL > 2.2f) gainL = 2.2f;
            if (gainR < 0.2f) gainR = 0.2f;
            if (gainR > 2.2f) gainR = 2.2f;

            buffer[f * 2] = inL * gainL;
            buffer[f * 2 + 1] = inR * gainR;
        }
    } else if (mode == 5) {
        float bits = gLiveProg.params[0];
        int downsample = (int)gLiveProg.params[1];
        float wet = gLiveProg.params[2];
        if (bits < 4.0f || bits > 16.0f) bits = 12.0f;
        if (downsample < 1 || downsample > 24) downsample = 2;
        if (wet < 0.0f || wet > 1.0f) wet = 0.5f;

        float steps = powf(2.0f, bits - 1.0f);
        float invSteps = 1.0f / steps;

        for (int f = 0; f < numFrames; ++f) {
            float inL = buffer[f * 2];
            float inR = buffer[f * 2 + 1];

            if (gLiveProg.holdCounter <= 0) {
                gLiveProg.holdSampleL = roundf(inL * steps) * invSteps;
                gLiveProg.holdSampleR = roundf(inR * steps) * invSteps;
                gLiveProg.holdCounter = downsample;
            }
            gLiveProg.holdCounter--;

            buffer[f * 2] = inL * (1.0f - wet) + gLiveProg.holdSampleL * wet;
            buffer[f * 2 + 1] = inR * (1.0f - wet) + gLiveProg.holdSampleR * wet;
        }
    }
}

void LiveProgEnable(void *jdsp) {
    (void)jdsp;
    gLiveProg.enabled = 1;
}

void LiveProgDisable(void *jdsp) {
    (void)jdsp;
    gLiveProg.enabled = 0;
}

void LiveProgProcess(void *jdsp, size_t n) {
    (void)jdsp;
    (void)n;
}

void LiveProgConstructor(void *jdsp) {
    (void)jdsp;
    memset(gLiveProg.fdnLines, 0, sizeof(gLiveProg.fdnLines));
    memset(gLiveProg.fdnWriteIdx, 0, sizeof(gLiveProg.fdnWriteIdx));
    memset(gLiveProg.fdnLpState, 0, sizeof(gLiveProg.fdnLpState));
}

void LiveProgDestructor(void *jdsp) {
    (void)jdsp;
}

int32_t LiveProgStringParser(void *jdsp, const char *str) {
    (void)jdsp;
    if (!str) return 0;
    if (strstr(str, "Hadamard") || strstr(str, "hadam")) {
        gLiveProg.activeMode = 0;
    } else if (strstr(str, "Highpass") || strstr(str, "hpfloat") || strstr(str, "biquad")) {
        gLiveProg.activeMode = 1;
    } else if (strstr(str, "Subharmonic") || strstr(str, "subbass")) {
        gLiveProg.activeMode = 2;
    } else if (strstr(str, "Exciter") || strstr(str, "Clarity")) {
        gLiveProg.activeMode = 3;
    } else if (strstr(str, "Transient")) {
        gLiveProg.activeMode = 4;
    } else if (strstr(str, "Bitcrusher") || strstr(str, "Downsample")) {
        gLiveProg.activeMode = 5;
    }
    return 1;
}

int NSEEL_start(void) { return 1; }
void NSEEL_quit(void) {}
