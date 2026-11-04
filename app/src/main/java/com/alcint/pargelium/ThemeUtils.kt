package com.alcint.pargelium

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DominantColorCache = LruCache<String, Color>(256)
private const val COLOR_ANIM_DURATION_MS = 550

@Composable
fun rememberDominantColor(uri: Uri?): Color {
    val context = LocalContext.current
    val cacheKey = remember(uri) { uri?.toString() ?: "" }

    var color by remember(cacheKey) {
        val cached = if (cacheKey.isNotEmpty()) DominantColorCache.get(cacheKey) else null
        mutableStateOf(cached ?: Color.Unspecified)
    }

    LaunchedEffect(cacheKey) {
        if (uri == null || cacheKey.isEmpty()) {
            color = Color.Unspecified
            return@LaunchedEffect
        }

        DominantColorCache.get(cacheKey)?.let { cached ->
            color = cached
            return@LaunchedEffect
        }

        val extractedColor = withContext(Dispatchers.IO) {
            try {
                val appContext = context.applicationContext
                val request = ImageRequest.Builder(appContext)
                    .data(uri)
                    .size(96)
                    .scale(Scale.FILL)
                    .allowHardware(false)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .memoryCacheKey("palette_$cacheKey")
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()

                val result = appContext.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap(width = 96, height = 96, config = Bitmap.Config.RGB_565)
                    val palette = withContext(Dispatchers.Default) {
                        Palette.from(bitmap)
                            .maximumColorCount(16)
                            .clearFilters()
                            .generate()
                    }

                    val bestSwatch = palette.vibrantSwatch
                        ?: palette.lightVibrantSwatch
                        ?: palette.dominantSwatch
                        ?: palette.mutedSwatch
                        ?: palette.swatches.maxByOrNull { it.population }

                    if (bestSwatch != null) {
                        Color(bestSwatch.rgb)
                    } else {
                        Color.Unspecified
                    }
                } else {
                    Color.Unspecified
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Color.Unspecified
            }
        }

        if (extractedColor != Color.Unspecified) {
            DominantColorCache.put(cacheKey, extractedColor)
        }
        color = extractedColor
    }

    return color
}

fun mixColors(foreground: Color, background: Color, ratio: Float): Color {
    val clamped = ratio.coerceIn(0f, 1f)
    val inv = 1f - clamped
    return Color(
        red = (foreground.red * clamped) + (background.red * inv),
        green = (foreground.green * clamped) + (background.green * inv),
        blue = (foreground.blue * clamped) + (background.blue * inv),
        alpha = 1f
    )
}

fun shiftHue(color: Color, hueDeltaDegrees: Float, saturationScale: Float = 1f): Color {
    if (color == Color.Unspecified) return color
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[0] = (hsl[0] + hueDeltaDegrees + 360f) % 360f
    hsl[1] = (hsl[1] * saturationScale).coerceIn(0.25f, 0.95f)
    return Color(ColorUtils.HSLToColor(hsl))
}

fun boostColor(color: Color, isLightMode: Boolean): Color {
    if (color == Color.Unspecified) return color
    val hsl = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    if (hsl[1] < 0.62f) hsl[1] = (hsl[1] + 0.32f).coerceAtMost(0.96f)
    if (isLightMode) {
        hsl[2] = hsl[2].coerceIn(0.42f, 0.52f)
    } else {
        hsl[2] = hsl[2].coerceIn(0.55f, 0.72f)
    }
    return Color(ColorUtils.HSLToColor(hsl))
}

@Composable
fun rememberPargeliumScheme(seedColor: Color, themeMode: Int, isSystemDark: Boolean): ColorScheme {
    val actualMode = if (themeMode == 0) (if (isSystemDark) 2 else 1) else themeMode
    val targetSeed = if (seedColor != Color.Unspecified && seedColor != Color.Transparent) {
        seedColor
    } else {
        Color(0xFFD0BCFF)
    }

    val animatedSeed by animateColorAsState(
        targetValue = targetSeed,
        animationSpec = tween(durationMillis = COLOR_ANIM_DURATION_MS, easing = FastOutSlowInEasing),
        label = "dynamicThemeSeed"
    )

    return remember(animatedSeed, actualMode) {
        val isLight = actualMode == 1
        val boosted = boostColor(animatedSeed, isLightMode = isLight)
        val secondarySeed = shiftHue(boosted, hueDeltaDegrees = 22f, saturationScale = 0.75f)
        val tertiarySeed = shiftHue(boosted, hueDeltaDegrees = 54f, saturationScale = 0.85f)

        fun makePalette(
            base: Color,
            bgRatio: Float,
            surfRatio: Float,
            contRatio: Float,
            highRatio: Float,
            variantRatio: Float
        ): List<Color> {
            return listOf(
                mixColors(boosted, base, bgRatio),
                mixColors(boosted, base, surfRatio),
                mixColors(boosted, base, contRatio),
                mixColors(boosted, base, highRatio),
                mixColors(boosted, base, variantRatio)
            )
        }

        val p: List<Color>
        val primaryColor: Color
        val onPrimaryColor: Color
        val primaryContainerColor: Color
        val onPrimaryContainerColor: Color

        val secondaryColor: Color
        val onSecondaryColor: Color
        val secondaryContainerColor: Color
        val onSecondaryContainerColor: Color

        val tertiaryColor: Color
        val onTertiaryColor: Color
        val tertiaryContainerColor: Color
        val onTertiaryContainerColor: Color

        when (actualMode) {
            1 -> {
                // Светлая тема (Light Expressive)
                p = makePalette(Color(0xFFFAFAFC), 0.03f, 0.07f, 0.12f, 0.17f, 0.25f)
                primaryColor = mixColors(boosted, Color.Black, 0.78f)
                onPrimaryColor = Color.White
                primaryContainerColor = mixColors(boosted, Color.White, 0.32f)
                onPrimaryContainerColor = mixColors(boosted, Color.Black, 0.45f)

                secondaryColor = mixColors(secondarySeed, Color.Black, 0.72f)
                onSecondaryColor = Color.White
                secondaryContainerColor = mixColors(secondarySeed, Color.White, 0.28f)
                onSecondaryContainerColor = mixColors(secondarySeed, Color.Black, 0.42f)

                tertiaryColor = mixColors(tertiarySeed, Color.Black, 0.76f)
                onTertiaryColor = Color.White
                tertiaryContainerColor = mixColors(tertiarySeed, Color.White, 0.30f)
                onTertiaryContainerColor = mixColors(tertiarySeed, Color.Black, 0.45f)
            }
            3 -> {
                // Чистый AMOLED (Pitch Black)
                p = makePalette(Color.Black, 0.00f, 0.04f, 0.09f, 0.14f, 0.20f)
                primaryColor = mixColors(boosted, Color.White, 0.82f)
                onPrimaryColor = Color(0xFF0E0E11)
                primaryContainerColor = mixColors(boosted, Color.Black, 0.34f)
                onPrimaryContainerColor = mixColors(boosted, Color.White, 0.92f)

                secondaryColor = mixColors(secondarySeed, Color.White, 0.78f)
                onSecondaryColor = Color(0xFF0E0E11)
                secondaryContainerColor = mixColors(secondarySeed, Color.Black, 0.28f)
                onSecondaryContainerColor = mixColors(secondarySeed, Color.White, 0.90f)

                tertiaryColor = mixColors(tertiarySeed, Color.White, 0.80f)
                onTertiaryColor = Color(0xFF0E0E11)
                tertiaryContainerColor = mixColors(tertiarySeed, Color.Black, 0.32f)
                onTertiaryContainerColor = mixColors(tertiarySeed, Color.White, 0.92f)
            }
            4 -> {
                // Глубокий индиго / Полночь (Deep Midnight)
                p = makePalette(Color(0xFF0F1016), 0.08f, 0.13f, 0.18f, 0.24f, 0.32f)
                primaryColor = mixColors(boosted, Color.White, 0.82f)
                onPrimaryColor = Color(0xFF101014)
                primaryContainerColor = mixColors(boosted, Color.Black, 0.40f)
                onPrimaryContainerColor = mixColors(boosted, Color.White, 0.92f)

                secondaryColor = mixColors(secondarySeed, Color.White, 0.78f)
                onSecondaryColor = Color(0xFF101014)
                secondaryContainerColor = mixColors(secondarySeed, Color.Black, 0.34f)
                onSecondaryContainerColor = mixColors(secondarySeed, Color.White, 0.90f)

                tertiaryColor = mixColors(tertiarySeed, Color.White, 0.80f)
                onTertiaryColor = Color(0xFF101014)
                tertiaryContainerColor = mixColors(tertiarySeed, Color.Black, 0.38f)
                onTertiaryContainerColor = mixColors(tertiarySeed, Color.White, 0.92f)
            }
            5 -> {
                // Насыщенная цветная тема (Vivid Colorful)
                val baseColorful = mixColors(boosted, Color.Black, 0.28f)
                p = makePalette(baseColorful, 0.14f, 0.24f, 0.34f, 0.44f, 0.54f)
                primaryColor = Color.White
                onPrimaryColor = mixColors(boosted, Color.Black, 0.55f)
                primaryContainerColor = mixColors(boosted, Color.Black, 0.48f)
                onPrimaryContainerColor = Color.White

                secondaryColor = mixColors(secondarySeed, Color.White, 0.85f)
                onSecondaryColor = Color.Black
                secondaryContainerColor = mixColors(secondarySeed, Color.Black, 0.42f)
                onSecondaryContainerColor = Color.White

                tertiaryColor = mixColors(tertiarySeed, Color.White, 0.85f)
                onTertiaryColor = Color.Black
                tertiaryContainerColor = mixColors(tertiarySeed, Color.Black, 0.45f)
                onTertiaryContainerColor = Color.White
            }
            else -> {
                // Стандартная темная тема (Material Dark Expressive)
                p = makePalette(Color(0xFF121215), 0.05f, 0.09f, 0.14f, 0.20f, 0.28f)
                primaryColor = mixColors(boosted, Color.White, 0.82f)
                onPrimaryColor = Color(0xFF121215)
                primaryContainerColor = mixColors(boosted, Color.Black, 0.36f)
                onPrimaryContainerColor = mixColors(boosted, Color.White, 0.92f)

                secondaryColor = mixColors(secondarySeed, Color.White, 0.78f)
                onSecondaryColor = Color(0xFF121215)
                secondaryContainerColor = mixColors(secondarySeed, Color.Black, 0.30f)
                onSecondaryContainerColor = mixColors(secondarySeed, Color.White, 0.90f)

                tertiaryColor = mixColors(tertiarySeed, Color.White, 0.80f)
                onTertiaryColor = Color(0xFF121215)
                tertiaryContainerColor = mixColors(tertiarySeed, Color.Black, 0.34f)
                onTertiaryContainerColor = mixColors(tertiarySeed, Color.White, 0.92f)
            }
        }

        if (isLight) {
            lightColorScheme(
                primary = primaryColor,
                onPrimary = onPrimaryColor,
                primaryContainer = primaryContainerColor,
                onPrimaryContainer = onPrimaryContainerColor,
                secondary = secondaryColor,
                onSecondary = onSecondaryColor,
                secondaryContainer = secondaryContainerColor,
                onSecondaryContainer = onSecondaryContainerColor,
                tertiary = tertiaryColor,
                onTertiary = onTertiaryColor,
                tertiaryContainer = tertiaryContainerColor,
                onTertiaryContainer = onTertiaryContainerColor,
                background = p[0],
                onBackground = Color(0xFF18181B),
                surface = p[1],
                onSurface = Color(0xFF18181B),
                surfaceVariant = p[4],
                onSurfaceVariant = Color(0xFF46464F),
                outline = mixColors(boosted, Color(0xFF75757F), 0.25f),
                outlineVariant = mixColors(boosted, Color(0xFFC8C8D0), 0.22f),
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = p[0],
                surfaceContainer = p[1],
                surfaceContainerHigh = p[2],
                surfaceContainerHighest = p[3]
            )
        } else {
            darkColorScheme(
                primary = primaryColor,
                onPrimary = onPrimaryColor,
                primaryContainer = primaryContainerColor,
                onPrimaryContainer = onPrimaryContainerColor,
                secondary = secondaryColor,
                onSecondary = onSecondaryColor,
                secondaryContainer = secondaryContainerColor,
                onSecondaryContainer = onSecondaryContainerColor,
                tertiary = tertiaryColor,
                onTertiary = onTertiaryColor,
                tertiaryContainer = tertiaryContainerColor,
                onTertiaryContainer = onTertiaryContainerColor,
                background = p[0],
                onBackground = if (actualMode == 5) Color.White.copy(alpha = 0.96f) else Color(0xFFECECF0),
                surface = p[1],
                onSurface = if (actualMode == 5) Color.White else Color(0xFFECECF0),
                surfaceVariant = p[4],
                onSurfaceVariant = if (actualMode == 5) Color.White.copy(alpha = 0.82f) else Color(0xFFCACAD2),
                outline = mixColors(boosted, Color(0xFF8E8E98), 0.28f),
                outlineVariant = mixColors(boosted, Color(0xFF42424A), 0.30f),
                surfaceContainerLowest = mixColors(p[0], Color.Black, 0.55f),
                surfaceContainerLow = p[0],
                surfaceContainer = p[1],
                surfaceContainerHigh = p[2],
                surfaceContainerHighest = p[3]
            )
        }
    }
}