package com.alcint.pargelium

import android.net.Uri
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberDominantColor(uri: Uri?): Color {
    val context = LocalContext.current
    var color by remember { mutableStateOf(Color.Unspecified) }

    LaunchedEffect(uri) {
        if (uri == null) {
            color = Color.Unspecified
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(128)
                .allowHardware(false)
                .memoryCacheKey(uri.toString())
                .build()

            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val palette = Palette.from((result.drawable).toBitmap()).generate()

                var extracted = palette.getVibrantColor(android.graphics.Color.TRANSPARENT)
                if (extracted == android.graphics.Color.TRANSPARENT) {
                    extracted = palette.getDominantColor(android.graphics.Color.TRANSPARENT)
                }
                if (extracted == android.graphics.Color.TRANSPARENT) {
                    extracted = palette.getMutedColor(android.graphics.Color.TRANSPARENT)
                }

                color = if (extracted != android.graphics.Color.TRANSPARENT) Color(extracted) else Color.Unspecified
            } else {
                color = Color.Unspecified
            }
        }
    }
    return color
}

@Composable
fun getAlbumGradient(seedColor: Color, isDark: Boolean): Brush {
    val baseColor = if (seedColor != Color.Unspecified) seedColor else MaterialTheme.colorScheme.surface
    val endColor = MaterialTheme.colorScheme.background

    val startColor = if (seedColor != Color.Unspecified) baseColor.copy(alpha = 0.45f) else Color.Transparent

    return Brush.verticalGradient(
        colors = listOf(
            startColor,
            endColor.copy(alpha = 0.8f),
            endColor
        )
    )
}

fun mixColors(foreground: Color, background: Color, ratio: Float): Color {
    val r = (foreground.red * ratio) + (background.red * (1f - ratio))
    val g = (foreground.green * ratio) + (background.green * (1f - ratio))
    val b = (foreground.blue * ratio) + (background.blue * (1f - ratio))
    return Color(red = r, green = g, blue = b, alpha = 1f)
}

fun boostColor(color: Color, isLightMode: Boolean): Color {
    if (color == Color.Unspecified) return color
    val hsl = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    if (hsl[1] < 0.6f) hsl[1] = (hsl[1] + 0.3f).coerceAtMost(1.0f)
    if (isLightMode) {
        if (hsl[2] > 0.6f) hsl[2] = 0.5f
        else if (hsl[2] < 0.4f) hsl[2] = 0.45f
    } else {
        if (hsl[2] < 0.35f) hsl[2] = (hsl[2] + 0.20f).coerceAtMost(0.7f)
    }
    return Color(ColorUtils.HSLToColor(hsl))
}

@Composable
fun rememberPargeliumScheme(seedColor: Color, themeMode: Int, isSystemDark: Boolean): ColorScheme {
    // 0: Auto, 1: Light, 2: Dark, 3: AMOLED, 4: Opal, 5: Pargelium
    val actualMode = if (themeMode == 0) (if (isSystemDark) 2 else 1) else themeMode

    return remember(seedColor, actualMode) {
        val rawSeed = if (seedColor != Color.Unspecified) seedColor else Color(0xFFD0BCFF)
        val boosted = boostColor(rawSeed, isLightMode = (actualMode == 1))

        fun makePalette(base: Color, bgRatio: Float, surfRatio: Float, contRatio: Float, highRatio: Float, variantRatio: Float): List<Color> {
            return listOf(
                mixColors(boosted, base, bgRatio),       // 0: Background
                mixColors(boosted, base, surfRatio),     // 1: Surface
                mixColors(boosted, base, contRatio),     // 2: Container
                mixColors(boosted, base, highRatio),     // 3: Container High
                mixColors(boosted, base, variantRatio)   // 4: Variant
            )
        }

        val p: List<Color>
        val primaryColor: Color
        val onPrimaryColor: Color
        val primaryContainerColor: Color
        val onPrimaryContainerColor: Color

        when (actualMode) {
            1 -> {
                // Светлая тема (береги глаза)
                p = makePalette(Color(0xFFFAFAFA), 0.03f, 0.08f, 0.12f, 0.18f, 0.28f)
                primaryColor = mixColors(boosted, Color.Black, 0.8f)
                onPrimaryColor = Color.White
                primaryContainerColor = mixColors(boosted, Color.White, 0.4f)
                onPrimaryContainerColor = mixColors(boosted, Color.Black, 0.9f)
            }
            3 -> {
                // AMOLED, это круто
                p = makePalette(Color.Black, 0.00f, 0.03f, 0.08f, 0.12f, 0.18f)
                primaryColor = mixColors(boosted, Color.White, 0.8f)
                onPrimaryColor = Color.Black
                primaryContainerColor = mixColors(boosted, Color.Black, 0.3f)
                onPrimaryContainerColor = mixColors(boosted, Color.White, 0.9f)
            }
            4 -> {
                // Опал
                p = makePalette(Color(0xFF101014), 0.10f, 0.14f, 0.18f, 0.24f, 0.32f)
                primaryColor = mixColors(boosted, Color.White, 0.8f)
                onPrimaryColor = Color.Black
                primaryContainerColor = mixColors(boosted, Color.Black, 0.4f)
                onPrimaryContainerColor = mixColors(boosted, Color.White, 0.9f)
            }
            5 -> {
                // Паргелия
                val baseColorful = mixColors(boosted, Color.Black, 0.3f)
                p = makePalette(baseColorful, 0.15f, 0.25f, 0.35f, 0.45f, 0.55f)
                primaryColor = Color.White
                onPrimaryColor = mixColors(boosted, Color.Black, 0.6f)
                primaryContainerColor = mixColors(boosted, Color.Black, 0.5f)
                onPrimaryContainerColor = Color.White
            }
            else -> {
                // Тёмная тема
                p = makePalette(Color(0xFF141414), 0.05f, 0.09f, 0.14f, 0.19f, 0.28f)
                primaryColor = mixColors(boosted, Color.White, 0.8f)
                onPrimaryColor = Color.Black
                primaryContainerColor = mixColors(boosted, Color.Black, 0.35f)
                onPrimaryContainerColor = mixColors(boosted, Color.White, 0.9f)
            }
        }

        if (actualMode == 1) {
            lightColorScheme(
                primary = primaryColor,
                onPrimary = onPrimaryColor,
                primaryContainer = primaryContainerColor,
                onPrimaryContainer = onPrimaryContainerColor,
                background = p[0],
                onBackground = Color(0xFF1A1A1A),
                surface = p[1],
                onSurface = Color(0xFF1A1A1A),
                surfaceVariant = p[4],
                onSurfaceVariant = Color(0xFF4A4A4A),
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
                background = p[0],
                onBackground = if (actualMode == 5) Color.White.copy(alpha = 0.95f) else Color(0xFFEBEBEB),
                surface = p[1],
                onSurface = if (actualMode == 5) Color.White else Color(0xFFEBEBEB),
                surfaceVariant = p[4],
                onSurfaceVariant = if (actualMode == 5) Color.White.copy(alpha = 0.8f) else Color(0xFFD0D0D0),
                surfaceContainerLowest = mixColors(p[0], Color.Black, 0.5f),
                surfaceContainerLow = p[0],
                surfaceContainer = p[1],
                surfaceContainerHigh = p[2],
                surfaceContainerHighest = p[3]
            )
        }
    }
}