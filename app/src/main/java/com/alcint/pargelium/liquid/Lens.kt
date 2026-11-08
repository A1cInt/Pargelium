package com.alcint.pargelium.liquid

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect

fun BackdropEffectScope.lens(
    refractionHeight: Float = 36f,
    refractionAmount: Float = 16f,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0.02f,
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding < refractionAmount) {
        padding = refractionAmount
    }

    val cornerShape = shape as? CornerBasedShape ?: return
    val sizePx = size
    if (sizePx.width <= 0f || sizePx.height <= 0f) return

    val maxRadius = sizePx.minDimension * 0.5f
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val topLeft = (if (isLtr) cornerShape.topStart.toPx(sizePx, this) else cornerShape.topEnd.toPx(sizePx, this)).fastCoerceAtMost(maxRadius)
    val topRight = (if (isLtr) cornerShape.topEnd.toPx(sizePx, this) else cornerShape.topStart.toPx(sizePx, this)).fastCoerceAtMost(maxRadius)
    val bottomRight = (if (isLtr) cornerShape.bottomEnd.toPx(sizePx, this) else cornerShape.bottomStart.toPx(sizePx, this)).fastCoerceAtMost(maxRadius)
    val bottomLeft = (if (isLtr) cornerShape.bottomStart.toPx(sizePx, this) else cornerShape.bottomEnd.toPx(sizePx, this)).fastCoerceAtMost(maxRadius)

    val dispersionEnabled = chromaticAberration > 0.001f
    val shaderString = if (dispersionEnabled) {
        ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER
    } else {
        ROUNDED_RECT_REFRACTION_SHADER
    }
    val key = if (dispersionEnabled) "LiquidGlassLensDispersion" else "LiquidGlassLens"

    val invSf = 1f / downscaleFactor.coerceAtLeast(1).toFloat()
    val scaledSizeW = sizePx.width * invSf
    val scaledSizeH = sizePx.height * invSf
    val scaledPadding = padding * invSf
    val scaledRefractionHeight = refractionHeight * invSf
    val scaledRefractionAmount = refractionAmount * invSf

    runtimeShaderEffect(
        key = key,
        shaderString = shaderString,
        uniformShaderName = "content",
    ) {
        setFloatUniform("size", scaledSizeW, scaledSizeH)
        setFloatUniform("offset", -scaledPadding, -scaledPadding)
        setFloatUniform(
            "cornerRadii",
            topLeft * invSf,
            topRight * invSf,
            bottomRight * invSf,
            bottomLeft * invSf
        )
        setFloatUniform("refractionHeight", scaledRefractionHeight)
        setFloatUniform("refractionAmount", -scaledRefractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (dispersionEnabled) {
            setFloatUniform("chromaticAberration", chromaticAberration)
        }
    }
}

private const val ROUNDED_RECT_SDF = """
float radiusAt(float2 coord, float4 radii) {
    float2 rightRadii = mix(radii.yz, radii.zy, step(0.0, coord.y));
    float2 leftRadii = mix(radii.xw, radii.wx, step(0.0, coord.y));
    return mix(leftRadii.x, rightRadii.x, step(0.0, coord.x));
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float2 clamped = max(cornerCoord, 0.0);
    float lenSq = dot(clamped, clamped);
    float isCorner = step(0.0001, lenSq);
    float2 cornerGrad = clamped * inversesqrt(max(lenSq, 0.0001));
    float gradX = step(cornerCoord.y, cornerCoord.x);
    float2 edgeGrad = float2(gradX, 1.0 - gradX);
    return sign(coord) * mix(edgeGrad, cornerGrad, isCorner);
}

float lensProfile(float normalizedDepth) {
    float t = clamp(1.0 - normalizedDepth, 0.0, 1.0);
    float smoothT = t * t * (3.0 - 2.0 * t);
    return smoothT * (1.0 - sqrt(max(1.0 - t * t, 0.0)));
}
"""

private const val ROUNDED_RECT_REFRACTION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = lensProfile(-sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.25, min(halfSize.x, halfSize.y));
    float2 aspectNormCenter = centeredCoord / max(halfSize, float2(1.0));
    float2 rawGrad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + (depthEffect * 0.35) * aspectNormCenter;
    float2 grad = rawGrad * inversesqrt(max(dot(rawGrad, rawGrad), 0.0001));

    return content.eval(coord + d * grad);
}
"""

private const val ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = lensProfile(-sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.25, min(halfSize.x, halfSize.y));
    float2 aspectNormCenter = centeredCoord / max(halfSize, float2(1.0));
    float2 rawGrad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + (depthEffect * 0.35) * aspectNormCenter;
    float2 grad = rawGrad * inversesqrt(max(dot(rawGrad, rawGrad), 0.0001));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / max(halfSize.x * halfSize.y, 1.0));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    if (dot(dispersedCoord, dispersedCoord) < 0.025) {
        return content.eval(refractedCoord);
    }

    half4 sPlus = content.eval(refractedCoord + dispersedCoord);
    half4 sMid = content.eval(refractedCoord);
    half4 sMinus = content.eval(refractedCoord - dispersedCoord);

    half4 color;
    color.r = sPlus.r * 0.72 + sMid.r * 0.28;
    color.g = sMid.g * 0.64 + (sPlus.g + sMinus.g) * 0.18;
    color.b = sMinus.b * 0.72 + sMid.b * 0.28;
    color.a = (sPlus.a + sMid.a + sMinus.a) * 0.33333334;
    return color;
}
"""