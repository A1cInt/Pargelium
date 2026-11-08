@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.alcint.pargelium.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.alcint.pargelium.R
import java.util.Locale

@Immutable
data class OnboardingLanguage(
    val tag: String,
    val flagEmoji: String,
    val nativeName: String,
    val englishName: String,
    val titleRes: Int
)

@Immutable
data class OnboardingThemeOption(
    val id: Int,
    val titleRes: Int,
    val descRes: Int,
    val iconRes: Int,
    val swatches: List<Color>
)

@Composable
fun rememberPargeliumExpressiveColorScheme(themeMode: Int): ColorScheme {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val hasDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    return remember(context, themeMode, systemDark, hasDynamicColor) {
        when (themeMode) {
            0 -> {
                if (hasDynamicColor) {
                    if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    if (systemDark) darkColorScheme() else lightColorScheme()
                }
            }
            1 -> {
                if (hasDynamicColor) dynamicLightColorScheme(context) else lightColorScheme()
            }
            2 -> {
                if (hasDynamicColor) dynamicDarkColorScheme(context) else darkColorScheme()
            }
            3 -> {
                val base = if (hasDynamicColor) dynamicDarkColorScheme(context) else darkColorScheme()
                base.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color(0xFF0A0A0D),
                    surfaceContainer = Color(0xFF101014),
                    surfaceContainerHigh = Color(0xFF16161C),
                    surfaceContainerHighest = Color(0xFF1F1F26)
                )
            }
            4 -> {
                darkColorScheme(
                    primary = Color(0xFF64FFDA),
                    onPrimary = Color(0xFF00382B),
                    primaryContainer = Color(0xFF005140),
                    onPrimaryContainer = Color(0xFF82F8D5),
                    secondary = Color(0xFF80CBC4),
                    tertiary = Color(0xFF80DEEA),
                    background = Color(0xFF070C10),
                    surface = Color(0xFF070C10),
                    surfaceContainerLow = Color(0xFF0D151B),
                    surfaceContainer = Color(0xFF111C24),
                    surfaceContainerHigh = Color(0xFF172630),
                    surfaceContainerHighest = Color(0xFF1E303C)
                )
            }
            5 -> {
                darkColorScheme(
                    primary = Color(0xFFFF5252),
                    onPrimary = Color(0xFF3E0006),
                    primaryContainer = Color(0xFF68000F),
                    onPrimaryContainer = Color(0xFFFFDAD7),
                    secondary = Color(0xFFFF8A80),
                    tertiary = Color(0xFFFFD180),
                    background = Color(0xFF0E0607),
                    surface = Color(0xFF0E0607),
                    surfaceContainerLow = Color(0xFF170B0D),
                    surfaceContainer = Color(0xFF200F12),
                    surfaceContainerHigh = Color(0xFF2B1519),
                    surfaceContainerHighest = Color(0xFF361B20)
                )
            }
            else -> if (systemDark) darkColorScheme() else lightColorScheme()
        }
    }
}

@Composable
fun isPargeliumThemeLight(themeMode: Int): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        0 -> !systemDark
        1 -> true
        else -> false
    }
}

object OnboardingCatalog {
    val languages = listOf(
        OnboardingLanguage("ru", "🇷🇺", "Русский", "Russian", R.string.lang_ru),
        OnboardingLanguage("en", "🇺🇸", "English", "English", R.string.lang_en),
        OnboardingLanguage("uk", "🇺🇦", "Українська", "Ukrainian", R.string.lang_uk),
        OnboardingLanguage("kk", "🇰🇿", "Қазақша", "Kazakh", R.string.lang_kk),
        OnboardingLanguage("de", "🇩🇪", "Deutsch", "German", R.string.lang_de),
        OnboardingLanguage("fr", "🇫🇷", "Français", "French", R.string.lang_fr),
        OnboardingLanguage("es", "🇪🇸", "Español", "Spanish", R.string.lang_es),
        OnboardingLanguage("it", "🇮🇹", "Italiano", "Italian", R.string.lang_it),
        OnboardingLanguage("pt", "🇵🇹", "Português", "Portuguese", R.string.lang_pt),
        OnboardingLanguage("pl", "🇵🇱", "Polski", "Polish", R.string.lang_pl),
        OnboardingLanguage("tr", "🇹🇷", "Türkçe", "Turkish", R.string.lang_tr),
        OnboardingLanguage("sv", "🇸🇪", "Svenska", "Swedish", R.string.lang_sv),
        OnboardingLanguage("fi", "🇫🇮", "Suomi", "Finnish", R.string.lang_fi),
        OnboardingLanguage("is", "🇮🇸", "Íslenska", "Icelandic", R.string.lang_is),
        OnboardingLanguage("fo", "🇫🇴", "Føroyskt", "Faroese", R.string.lang_fo),
        OnboardingLanguage("el", "🇬🇷", "Ελληνικά", "Greek", R.string.lang_el),
        OnboardingLanguage("ga", "🇮🇪", "Gaeilge", "Irish", R.string.lang_ga),
        OnboardingLanguage("cy", "🏴󠁧󠁢󠁷󠁬󠁳󠁿", "Cymraeg", "Welsh", R.string.lang_cy),
        OnboardingLanguage("rm", "🇨🇭", "Rumantsch", "Romansh", R.string.lang_rm),
        OnboardingLanguage("ka", "🇬🇪", "ქართული", "Georgian", R.string.lang_ka),
        OnboardingLanguage("hi", "🇮🇳", "हिन्दी", "Hindi", R.string.lang_hi),
        OnboardingLanguage("id", "🇮🇩", "Bahasa Indonesia", "Indonesian", R.string.lang_id),
        OnboardingLanguage("tl", "🇵🇭", "Tagalog", "Filipino", R.string.lang_tl),
        OnboardingLanguage("ja", "🇯🇵", "日本語", "Japanese", R.string.lang_ja),
        OnboardingLanguage("ko", "🇰🇷", "한국어", "Korean", R.string.lang_ko),
        OnboardingLanguage("zh", "🇨🇳", "简体中文", "Simplified Chinese", R.string.lang_zh),
        OnboardingLanguage("zh-TW", "🇹🇼", "繁體中文", "Traditional Chinese", R.string.lang_zh_tw)
    )

    val themes = listOf(
        OnboardingThemeOption(
            id = 0,
            titleRes = R.string.theme_auto,
            descRes = R.string.theme_auto_desc,
            iconRes = R.drawable.ic_auto_theme,
            swatches = listOf(Color(0xFFD0BCFF), Color(0xFF1E1E24), Color(0xFFF5F5F7))
        ),
        OnboardingThemeOption(
            id = 1,
            titleRes = R.string.theme_light,
            descRes = R.string.theme_light_desc,
            iconRes = R.drawable.ic_light_theme,
            swatches = listOf(Color(0xFF6750A4), Color(0xFFFDF8FD), Color(0xFFEADDFF))
        ),
        OnboardingThemeOption(
            id = 2,
            titleRes = R.string.theme_dark,
            descRes = R.string.theme_dark_desc,
            iconRes = R.drawable.ic_dark_theme,
            swatches = listOf(Color(0xFFD0BCFF), Color(0xFF141218), Color(0xFF332D41))
        ),
        OnboardingThemeOption(
            id = 3,
            titleRes = R.string.theme_amoled,
            descRes = R.string.theme_amoled_desc,
            iconRes = R.drawable.ic_amoled_theme,
            swatches = listOf(Color(0xFFD0BCFF), Color(0xFF000000), Color(0xFF121212))
        ),
        OnboardingThemeOption(
            id = 4,
            titleRes = R.string.theme_opal,
            descRes = R.string.theme_opal_desc,
            iconRes = R.drawable.ic_opal_theme,
            swatches = listOf(Color(0xFF64FFDA), Color(0xFF070C10), Color(0xFF111C24))
        ),
        OnboardingThemeOption(
            id = 5,
            titleRes = R.string.theme_pargelia,
            descRes = R.string.app_name,
            iconRes = R.drawable.ic_pargelia_theme,
            swatches = listOf(Color(0xFFFF5252), Color(0xFF0E0607), Color(0xFF200F12))
        )
    )

    fun detectDeviceLanguage(): OnboardingLanguage {
        val sysLocale = Locale.getDefault()
        val fullTag = sysLocale.toLanguageTag().lowercase(Locale.ROOT)
        val langCode = sysLocale.language.lowercase(Locale.ROOT)

        if (langCode == "zh") {
            return if (fullTag.contains("tw") || fullTag.contains("hk") || fullTag.contains("hant")) {
                languages.first { it.tag == "zh-TW" }
            } else {
                languages.first { it.tag == "zh" }
            }
        }
        return languages.find { it.tag.equals(langCode, ignoreCase = true) }
            ?: languages.first { it.tag == "en" }
    }

    fun isAllFilesGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun isMediaPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}