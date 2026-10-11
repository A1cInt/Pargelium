#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>
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

void discreteHartleyTransform(float *x, int n) {
    if (n <= 1) return;
    float *tmp = (float *)malloc(n * sizeof(float));
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

    for (int i = 0; i < n; ++i) {
        x[i] = tmp[i];
    }
    free(tmp);
}

void JamesDSPOfflineResampling(float const *in, float *out, size_t lenIn, size_t lenOut, int channels, double src_ratio) {
    if (!in || !out || lenIn == 0 || lenOut == 0 || channels <= 0) {
        return;
    }

    if (lenIn == lenOut) {
        size_t total = lenIn * (size_t)channels;
        for (size_t i = 0; i < total; ++i) {
            out[i] = in[i];
        }
        return;
    }

    double step = (double)lenIn / (double)lenOut;
    for (size_t i = 0; i < lenOut; ++i) {
        double srcPos = (double)i * step;
        size_t idx0 = (size_t)srcPos;
        size_t idx1 = idx0 + 1;
        float frac = (float)(srcPos - (double)idx0);

        if (idx1 >= lenIn) {
            idx1 = lenIn - 1;
        }

        for (int ch = 0; ch < channels; ++ch) {
            float s0 = in[idx0 * (size_t)channels + (size_t)ch];
            float s1 = in[idx1 * (size_t)channels + (size_t)ch];
            out[i * (size_t)channels + (size_t)ch] = s0 + frac * (s1 - s0);
        }
    }
}