#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#define DR_FLAC_IMPLEMENTATION
#define DR_FLAC_NO_STDIO
#include "dr_flac.h"

int XLITTLE_ENDIAN = 1;

void LiveProgEnable(void *jdsp) {}
void LiveProgDisable(void *jdsp) {}
void LiveProgProcess(void *jdsp, size_t n) {}
void LiveProgConstructor(void *jdsp) {}
void LiveProgDestructor(void *jdsp) {}
int32_t LiveProgStringParser(void *jdsp, const char *str) { return 0; }

int NSEEL_start(void) { return 1; }
void NSEEL_quit(void) {}

static int isPowerOfTwo(int n) {
    return (n > 0) && ((n & (n - 1)) == 0);
}

/**
 * Быстрое преобразование Хартли (Fast Hartley Transform через Radix-2 Cooley-Tukey FFT) за O(N log N).
 * Математически идентично H[k] = sum(x[n] * (cos(2*pi*k*n/N) + sin(2*pi*k*n/N))) = Re(FFT[k]) - Im(FFT[k]).
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
        re[i] = (double)x[i];
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
        double angle = -6.28318530717958647692 / (double)len;
        double wLenRe = cos(angle);
        double wLenIm = sin(angle);

        for (int i = 0; i < n; i += len) {
            double wRe = 1.0;
            double wIm = 0.0;
            for (int k = 0; k < halfLen; ++k) {
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

                double nextWRe = wRe * wLenRe - wIm * wLenIm;
                wIm = wRe * wLenIm + wIm * wLenRe;
                wRe = nextWRe;
            }
        }
    }

    for (int k = 0; k < n; ++k) {
        x[k] = (float)(re[k] - im[k]);
    }

    free(re);
    free(im);
}

/**
 * Высококачественный 4-точечный кубический Эрмитов ресемплер для импульсов Convolver.
 */
void JamesDSPOfflineResampling(float const *in, float *out, size_t lenIn, size_t lenOut, int channels, double src_ratio) {
    if (!in || !out || lenIn == 0 || channels <= 0) {
        return;
    }

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
