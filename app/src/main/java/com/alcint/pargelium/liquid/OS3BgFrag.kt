package com.alcint.pargelium.liquid

const val OS3_BG_FRAG = """
    uniform vec2 uResolution;
    uniform float uAnimTime;
    uniform vec4 uBound;
    uniform float uTranslateY;
    uniform vec3 uPoints[4];
    uniform vec2 uPointsAnim[4];
    uniform vec4 uColors[4];
    uniform float uAlphaMulti;
    uniform float uNoiseScale;
    uniform float uPointRadiusMulti;
    uniform float uSaturateOffset;
    uniform float uLightOffset;

    half3 rgb2hsv(half3 c) {
        half4 K = half4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
        half4 p = mix(half4(c.bg, K.wz), half4(c.gb, K.xy), step(c.b, c.g));
        half4 q = mix(half4(p.xyw, c.r), half4(c.r, p.yzx), step(p.x, c.r));
        half d = q.x - min(q.w, q.y);
        half e = 1.0e-4;
        return half3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
    }

    half3 hsv2rgb(half3 c) {
        half4 K = half4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
        half3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
        return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
    }

    float hash(vec2 p) {
        vec3 p3 = fract(vec3(p.xyx) * 0.13);
        p3 += dot(p3, p3.yzx + 3.333);
        return fract((p3.x + p3.y) * p3.z);
    }

    float perlin(vec2 x) {
        vec2 i = floor(x);
        vec2 f = fract(x);

        float a = hash(i);
        float b = hash(i + vec2(1.0, 0.0));
        float c = hash(i + vec2(0.0, 1.0));
        float d = hash(i + vec2(1.0, 1.0));

        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    half gradientNoise(in vec2 uv) {
        return half(fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715)))));
    }

    half4 main(vec2 fragCoord) {
        vec2 vUv = fragCoord / max(uResolution, vec2(1.0));
        vUv.y = 1.0 - vUv.y;
        vec2 uv = vUv;
        uv.y -= uTranslateY;
        uv.xy -= uBound.xy;
        uv.xy /= max(uBound.zw, vec2(0.0001));

        half4 color = half4(0.0);
        half noiseValue = half(perlin(vUv * uNoiseScale + vec2(-uAnimTime, -uAnimTime)));

        for (int i = 0; i < 4; i++) {
            half4 pointColor = half4(uColors[i]);
            pointColor.rgb *= pointColor.a;
            vec2 point = uPointsAnim[i];
            float rad = uPoints[i].z * uPointRadiusMulti;

            float d = distance(uv, point);
            half pct = half(smoothstep(rad, 0.0, d));
            color.rgb = mix(color.rgb, pointColor.rgb, pct);
            color.a = mix(color.a, pointColor.a, pct);
        }

        half oppositeNoise = smoothstep(half(0.0), half(1.0), noiseValue);
        color.rgb /= max(color.a, half(0.0001));
        half3 hsv = rgb2hsv(color.rgb);
        hsv.y = mix(hsv.y, half(0.0), oppositeNoise * half(uSaturateOffset));
        color.rgb = hsv2rgb(hsv);
        color.rgb += oppositeNoise * half(uLightOffset);

        color.a = clamp(color.a, half(0.0), half(1.0)) * half(uAlphaMulti);
        color += (half(10.0 / 255.0) * gradientNoise(fragCoord.xy)) - half(5.0 / 255.0);
        return half4(color.rgb * color.a, color.a);
    }
"""