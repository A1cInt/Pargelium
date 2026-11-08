package com.alcint.pargelium

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
import androidx.core.content.ContextCompat
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
import kotlin.math.abs

private val DominantColorCache = LruCache<String, Color>(256)
private const val COLOR_ANIM_DURATION_MS = 420

enum class MonetColorSource(val id: Int, val titleRes: Int, val descRes: Int) {
    TRACK_COVER(0, R.string.monet_source_track, R.string.monet_source_track_desc),
    DEVICE_WALLPAPER(1, R.string.monet_source_wallpaper, R.string.monet_source_wallpaper_desc),
    CUSTOM_COLOR(2, R.string.monet_source_custom, R.string.monet_source_custom_desc);

    companion object {
        fun fromId(id: Int): MonetColorSource = entries.firstOrNull { it.id == id } ?: TRACK_COVER
    }
}

enum class MonetPaletteStyle(val id: Int, val titleRes: Int) {
    TONAL_SPOT(0, R.string.monet_style_tonal_spot),
    VIBRANT(1, R.string.monet_style_vibrant),
    EXPRESSIVE(2, R.string.monet_style_expressive),
    CONTENT(3, R.string.monet_style_content),
    NEUTRAL(4, R.string.monet_style_neutral),
    MONOCHROME(5, R.string.monet_style_monochrome);

    companion object {
        fun fromId(id: Int): MonetPaletteStyle = entries.firstOrNull { it.id == id } ?: TONAL_SPOT
    }
}

@Stable
object MonetConfigState {
    var colorSource by mutableIntStateOf(0)
        private set
    var customColorArgb by mutableIntStateOf(0xFFD0BCFF.toInt())
        private set
    var paletteStyle by mutableIntStateOf(0)
        private set
    var contrastLevel by mutableFloatStateOf(0f)
        private set
    private var initialized = false

    fun ensureInitialized() {
        if (!initialized) {
            runCatching {
                colorSource = PrefsManager.getMonetColorSource()
                customColorArgb = PrefsManager.getMonetCustomColor()
                paletteStyle = PrefsManager.getMonetPaletteStyle()
                contrastLevel = PrefsManager.getMonetContrastLevel()
                initialized = true
            }
        }
    }

    fun updateSource(source: Int) {
        colorSource = source
        PrefsManager.saveMonetColorSource(source)
    }

    fun updateCustomColor(argb: Int) {
        customColorArgb = argb
        PrefsManager.saveMonetCustomColor(argb)
    }

    fun updatePaletteStyle(style: Int) {
        paletteStyle = style
        PrefsManager.saveMonetPaletteStyle(style)
    }

    fun updateContrastLevel(contrast: Float) {
        contrastLevel = contrast.coerceIn(-0.3f, 0.8f)
        PrefsManager.saveMonetContrastLevel(contrastLevel)
    }
}

fun extractWallpaperSeedColor(context: Context): Color {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            val sysArgb = ContextCompat.getColor(context, android.R.color.system_accent1_400)
            if (sysArgb != 0) return Color(sysArgb)
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            val wpColors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            val primary = wpColors?.primaryColor?.toArgb()
            if (primary != null && primary != 0) return Color(primary)
        }
    }
    return Color(0xFFD0BCFF)
}

@Composable
fun rememberEffectiveSeedColor(trackArtUri: Uri?): Color {
    val context = LocalContext.current
    LaunchedEffect(Unit) { MonetConfigState.ensureInitialized() }

    val source = MonetColorSource.fromId(MonetConfigState.colorSource)
    val trackSeed = rememberDominantColor(if (source == MonetColorSource.TRACK_COVER) trackArtUri else null)
    val wallpaperSeed = remember(context, source) {
        if (source == MonetColorSource.DEVICE_WALLPAPER) extractWallpaperSeedColor(context) else Color.Unspecified
    }

    return when (source) {
        MonetColorSource.TRACK_COVER -> if (trackSeed != Color.Unspecified) trackSeed else Color(0xFFD0BCFF)
        MonetColorSource.DEVICE_WALLPAPER -> if (wallpaperSeed != Color.Unspecified) wallpaperSeed else Color(0xFFD0BCFF)
        MonetColorSource.CUSTOM_COLOR -> Color(MonetConfigState.customColorArgb)
    }
}

@Composable
fun rememberDominantColor(uri: Uri?): Color {
    val context = LocalContext.current
    val cacheKey = remember(uri) { uri?.toString() ?: "" }

    var color by remember {
        val cached = if (cacheKey.isNotEmpty()) DominantColorCache.get(cacheKey) else null
        mutableStateOf(cached ?: Color.Unspecified)
    }

    LaunchedEffect(cacheKey) {
        if (uri == null || cacheKey.isEmpty()) {
            return@LaunchedEffect
        }

        DominantColorCache.get(cacheKey)?.let { cached ->
            if (color != cached) color = cached
            return@LaunchedEffect
        }

        val extractedColor = withContext(Dispatchers.IO) {
            try {
                val appContext = context.applicationContext
                val request = ImageRequest.Builder(appContext)
                    .data(uri)
                    .size(64)
                    .scale(Scale.FILL)
                    .allowHardware(false)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .memoryCacheKey("palette_$cacheKey")
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()

                val result = appContext.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap(width = 64, height = 64, config = Bitmap.Config.RGB_565)
                    val palette = Palette.from(bitmap)
                        .maximumColorCount(16)
                        .clearFilters()
                        .generate()

                    val bestSwatch = palette.SwatchesScored()
                        ?: palette.vibrantSwatch
                        ?: palette.lightVibrantSwatch
                        ?: palette.dominantSwatch
                        ?: palette.mutedSwatch

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
            color = extractedColor
        }
    }

    return color
}

private fun Palette.SwatchesScored(): Palette.Swatch? {
    if (swatches.isEmpty()) return null
    val maxPop = swatches.maxOfOrNull { it.population }?.coerceAtLeast(1) ?: 1
    return swatches.maxByOrNull { swatch ->
        val hsl = swatch.hsl
        val hue = hsl[0]
        val sat = hsl[1]
        val lum = hsl[2]
        val popScore = (swatch.population.toFloat() / maxPop) * 0.35f
        val chromaScore = sat * 0.50f
        val lumPenalty = (1f - abs(lum - 0.52f) * 1.6f).coerceIn(0f, 1f) * 0.15f
        val dislikePenalty = if (hue in 75f..112f && sat > 0.35f && lum < 0.45f) -0.35f else 0f
        popScore + chromaScore + lumPenalty + dislikePenalty
    }
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
    hsl[1] = (hsl[1] * saturationScale).coerceIn(0f, 0.98f)
    return Color(ColorUtils.HSLToColor(hsl))
}

fun boostColorForVariant(
    color: Color,
    isLightMode: Boolean,
    style: MonetPaletteStyle,
    contrastLevel: Float
): Triple<Color, Color, Color> {
    val base = if (color != Color.Unspecified && color != Color.Transparent) color else Color(0xFFD0BCFF)
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(base.toArgb(), hsl)

    when (style) {
        MonetPaletteStyle.TONAL_SPOT -> {
            hsl[1] = hsl[1].coerceIn(0.48f, 0.82f)
        }
        MonetPaletteStyle.VIBRANT -> {
            hsl[1] = (hsl[1] + 0.35f).coerceIn(0.78f, 0.98f)
        }
        MonetPaletteStyle.EXPRESSIVE -> {
            hsl[0] = (hsl[0] + 18f) % 360f
            hsl[1] = (hsl[1] + 0.28f).coerceIn(0.68f, 0.95f)
        }
        MonetPaletteStyle.CONTENT -> {
            hsl[1] = hsl[1].coerceIn(0.32f, 0.92f)
        }
        MonetPaletteStyle.NEUTRAL -> {
            hsl[1] = (hsl[1] * 0.28f).coerceIn(0.08f, 0.24f)
        }
        MonetPaletteStyle.MONOCHROME -> {
            hsl[1] = 0f
        }
    }

    val contrastShift = contrastLevel * 0.12f
    hsl[2] = if (isLightMode) {
        (hsl[2].coerceIn(0.38f, 0.50f) - contrastShift).coerceIn(0.24f, 0.55f)
    } else {
        (hsl[2].coerceIn(0.58f, 0.74f) + contrastShift).coerceIn(0.52f, 0.88f)
    }

    val primarySeed = Color(ColorUtils.HSLToColor(hsl))

    val secondarySeed = when (style) {
        MonetPaletteStyle.TONAL_SPOT -> shiftHue(primarySeed, 14f, 0.65f)
        MonetPaletteStyle.VIBRANT -> shiftHue(primarySeed, 28f, 0.88f)
        MonetPaletteStyle.EXPRESSIVE -> shiftHue(primarySeed, 48f, 0.82f)
        MonetPaletteStyle.CONTENT -> shiftHue(primarySeed, 8f, 0.75f)
        MonetPaletteStyle.NEUTRAL -> shiftHue(primarySeed, 6f, 0.45f)
        MonetPaletteStyle.MONOCHROME -> primarySeed
    }

    val tertiarySeed = when (style) {
        MonetPaletteStyle.TONAL_SPOT -> shiftHue(primarySeed, 52f, 0.78f)
        MonetPaletteStyle.VIBRANT -> shiftHue(primarySeed, 64f, 0.94f)
        MonetPaletteStyle.EXPRESSIVE -> shiftHue(primarySeed, 115f, 0.90f)
        MonetPaletteStyle.CONTENT -> shiftHue(primarySeed, 32f, 0.84f)
        MonetPaletteStyle.NEUTRAL -> shiftHue(primarySeed, 24f, 0.50f)
        MonetPaletteStyle.MONOCHROME -> primarySeed
    }

    return Triple(primarySeed, secondarySeed, tertiarySeed)
}

@Composable
fun rememberPargeliumScheme(
    seedColor: Color,
    themeMode: Int,
    isSystemDark: Boolean
): ColorScheme {
    LaunchedEffect(Unit) { MonetConfigState.ensureInitialized() }

    val actualMode = if (themeMode == 0) (if (isSystemDark) 2 else 1) else themeMode
    val isLight = actualMode == 1
    val style = MonetPaletteStyle.fromId(MonetConfigState.paletteStyle)
    val contrast = MonetConfigState.contrastLevel

    val (targetBoosted, targetSecondary, targetTertiary) = remember(seedColor, isLight, style, contrast) {
        boostColorForVariant(
            color = seedColor,
            isLightMode = isLight,
            style = style,
            contrastLevel = contrast
        )
    }

    val animSpec = remember { tween<Color>(durationMillis = COLOR_ANIM_DURATION_MS, easing = FastOutSlowInEasing) }
    val boosted by animateColorAsState(targetValue = targetBoosted, animationSpec = animSpec, label = "primarySeed")
    val secondarySeed by animateColorAsState(targetValue = targetSecondary, animationSpec = animSpec, label = "secSeed")
    val tertiarySeed by animateColorAsState(targetValue = targetTertiary, animationSpec = animSpec, label = "tertSeed")

    return remember(boosted, secondarySeed, tertiarySeed, actualMode, style, contrast) {
        val surfaceTintMultiplier = when (style) {
            MonetPaletteStyle.VIBRANT -> 1.32f
            MonetPaletteStyle.EXPRESSIVE -> 1.22f
            MonetPaletteStyle.NEUTRAL -> 0.45f
            MonetPaletteStyle.MONOCHROME -> 0.0f
            else -> 1.0f
        }
        val contrastFactor = (1f + contrast * 0.35f).coerceIn(0.75f, 1.45f)

        fun makePalette(
            base: Color,
            bgRatio: Float,
            surfRatio: Float,
            contRatio: Float,
            highRatio: Float,
            variantRatio: Float
        ): List<Color> {
            val m = surfaceTintMultiplier
            return listOf(
                mixColors(boosted, base, (bgRatio * m).coerceIn(0f, 0.65f)),
                mixColors(boosted, base, (surfRatio * m).coerceIn(0f, 0.70f)),
                mixColors(boosted, base, (contRatio * m).coerceIn(0f, 0.75f)),
                mixColors(boosted, base, (highRatio * m).coerceIn(0f, 0.80f)),
                mixColors(boosted, base, (variantRatio * m).coerceIn(0f, 0.85f))
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
                p = makePalette(Color(0xFFFAFAFC), 0.03f, 0.07f, 0.12f, 0.17f, 0.25f)
                primaryColor = mixColors(boosted, Color.Black, (0.78f / contrastFactor).coerceIn(0.55f, 0.90f))
                onPrimaryColor = Color.White
                primaryContainerColor = mixColors(boosted, Color.White, 0.34f * contrastFactor)
                onPrimaryContainerColor = mixColors(boosted, Color.Black, 0.38f)

                secondaryColor = mixColors(secondarySeed, Color.Black, 0.72f)
                onSecondaryColor = Color.White
                secondaryContainerColor = mixColors(secondarySeed, Color.White, 0.28f * contrastFactor)
                onSecondaryContainerColor = mixColors(secondarySeed, Color.Black, 0.40f)

                tertiaryColor = mixColors(tertiarySeed, Color.Black, 0.76f)
                onTertiaryColor = Color.White
                tertiaryContainerColor = mixColors(tertiarySeed, Color.White, 0.30f * contrastFactor)
                onTertiaryContainerColor = mixColors(tertiarySeed, Color.Black, 0.42f)
            }
            3 -> {
                p = makePalette(Color.Black, 0.00f, 0.03f, 0.08f, 0.13f, 0.19f)
                primaryColor = mixColors(boosted, Color.White, (0.82f * contrastFactor).coerceIn(0.65f, 0.96f))
                onPrimaryColor = Color(0xFF0A0A0D)
                primaryContainerColor = mixColors(boosted, Color.Black, (0.36f * contrastFactor).coerceIn(0.22f, 0.58f))
                onPrimaryContainerColor = mixColors(boosted, Color.White, 0.92f)

                secondaryColor = mixColors(secondarySeed, Color.White, 0.78f)
                onSecondaryColor = Color(0xFF0A0A0D)
                secondaryContainerColor = mixColors(secondarySeed, Color.Black, 0.28f * contrastFactor)
                onSecondaryContainerColor = mixColors(secondarySeed, Color.White, 0.90f)

                tertiaryColor = mixColors(tertiarySeed, Color.White, 0.80f)
                onTertiaryColor = Color(0xFF0A0A0D)
                tertiaryContainerColor = mixColors(tertiarySeed, Color.Black, 0.32f * contrastFactor)
                onTertiaryContainerColor = mixColors(tertiarySeed, Color.White, 0.92f)
            }
            4 -> {
                p = makePalette(Color(0xFF0F1016), 0.08f, 0.13f, 0.18f, 0.24f, 0.32f)
                primaryColor = mixColors(boosted, Color.White, (0.82f * contrastFactor).coerceIn(0.65f, 0.96f))
                onPrimaryColor = Color(0xFF101014)
                primaryContainerColor = mixColors(boosted, Color.Black, 0.40f * contrastFactor)
                onPrimaryContainerColor = mixColors(boosted, Color.White, 0.92f)

                secondaryColor = mixColors(secondarySeed, Color.White, 0.78f)
                onSecondaryColor = Color(0xFF101014)
                secondaryContainerColor = mixColors(secondarySeed, Color.Black, 0.34f * contrastFactor)
                onSecondaryContainerColor = mixColors(secondarySeed, Color.White, 0.90f)

                tertiaryColor = mixColors(tertiarySeed, Color.White, 0.80f)
                onTertiaryColor = Color(0xFF101014)
                tertiaryContainerColor = mixColors(tertiarySeed, Color.Black, 0.38f * contrastFactor)
                onTertiaryContainerColor = mixColors(tertiarySeed, Color.White, 0.92f)
            }
            5 -> {
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
                p = makePalette(Color(0xFF121215), 0.05f, 0.09f, 0.14f, 0.20f, 0.28f)
                primaryColor = mixColors(boosted, Color.White, (0.82f * contrastFactor).coerceIn(0.65f, 0.96f))
                onPrimaryColor = Color(0xFF121215)
                primaryContainerColor = mixColors(boosted, Color.Black, 0.36f * contrastFactor)
                onPrimaryContainerColor = mixColors(boosted, Color.White, 0.92f)

                secondaryColor = mixColors(secondarySeed, Color.White, 0.78f)
                onSecondaryColor = Color(0xFF121215)
                secondaryContainerColor = mixColors(secondarySeed, Color.Black, 0.30f * contrastFactor)
                onSecondaryContainerColor = mixColors(secondarySeed, Color.White, 0.90f)

                tertiaryColor = mixColors(tertiarySeed, Color.White, 0.80f)
                onTertiaryColor = Color(0xFF121215)
                tertiaryContainerColor = mixColors(tertiarySeed, Color.Black, 0.34f * contrastFactor)
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
                surfaceContainerLowest = if (actualMode == 3) Color.Black else mixColors(p[0], Color.Black, 0.55f),
                surfaceContainerLow = p[0],
                surfaceContainer = p[1],
                surfaceContainerHigh = p[2],
                surfaceContainerHighest = p[3]
            )
        }
    }
}