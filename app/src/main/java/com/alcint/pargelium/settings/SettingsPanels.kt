@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)
package com.alcint.pargelium.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.os.LocaleListCompat
import coil.compose.AsyncImage
import com.alcint.pargelium.AudioRepository
import com.alcint.pargelium.EasterEggActivity
import com.alcint.pargelium.LyricsManager
import com.alcint.pargelium.MonetColorSource
import com.alcint.pargelium.MonetConfigState
import com.alcint.pargelium.MonetPaletteStyle
import com.alcint.pargelium.MorphingPillIndicator
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.R
import com.alcint.pargelium.UpdateManager
import com.alcint.pargelium.audio.JamesDspController
import com.alcint.pargelium.boostColorForVariant
import com.alcint.pargelium.liquid.LiquidGlassSwitch
import com.alcint.pargelium.player.OnlineCanvasManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Immutable
private data class LanguageOption(
    val tag: String,
    val titleRes: Int,
    val nativeSubtitle: String
)

@Immutable
private data class ThemeOptionItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val previewColors: List<Color>
)

private val PRESET_MONET_COLORS = listOf(
    Color(0xFFD0BCFF),
    Color(0xFFFF2A54),
    Color(0xFF00E5FF),
    Color(0xFF64FFDA),
    Color(0xFFFF6D00),
    Color(0xFF7C4DFF),
    Color(0xFFFFD600),
    Color(0xFF00E676),
    Color(0xFFFF4081),
    Color(0xFF536DFE)
)

@Composable
fun AppearancePanel(
    currentThemeMode: Int,
    onThemeChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) { MonetConfigState.ensureInitialized() }

    var isTrackInfoBarEnabled by remember { mutableStateOf(PrefsManager.getShowTrackInfoBar()) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var langSearchQuery by remember { mutableStateOf("") }

    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val isSystemLanguage = currentLocales.isEmpty
    val activeTag = if (!isSystemLanguage) {
        currentLocales[0]?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
    } else {
        "auto"
    }

    val allLanguages = remember {
        listOf(
            LanguageOption("auto", R.string.lang_system, "System Default"),
            LanguageOption("ru", R.string.lang_ru, "Русский (Russian)"),
            LanguageOption("en", R.string.lang_en, "English"),
            LanguageOption("uk", R.string.lang_uk, "Українська (Ukrainian)"),
            LanguageOption("kk", R.string.lang_kk, "Қазақ (Kazakh)"),
            LanguageOption("de", R.string.lang_de, "Deutsch (German)"),
            LanguageOption("fr", R.string.lang_fr, "Français (French)"),
            LanguageOption("es", R.string.lang_es, "Español (Spanish)"),
            LanguageOption("it", R.string.lang_it, "Italiano (Italian)"),
            LanguageOption("pt", R.string.lang_pt, "Português (Portuguese)"),
            LanguageOption("pl", R.string.lang_pl, "Polski (Polish)"),
            LanguageOption("tr", R.string.lang_tr, "Türkçe (Turkish)"),
            LanguageOption("sv", R.string.lang_sv, "Svenska (Swedish)"),
            LanguageOption("fi", R.string.lang_fi, "Suomi (Finnish)"),
            LanguageOption("is", R.string.lang_is, "Íslenska (Icelandic)"),
            LanguageOption("fo", R.string.lang_fo, "Føroyskt (Faroese)"),
            LanguageOption("el", R.string.lang_el, "Ελληνικά (Greek)"),
            LanguageOption("ga", R.string.lang_ga, "Gaeilge (Irish)"),
            LanguageOption("cy", R.string.lang_cy, "Cymraeg (Welsh)"),
            LanguageOption("rm", R.string.lang_rm, "Rumantsch (Romansh)"),
            LanguageOption("ka", R.string.lang_ka, "ქართული (Georgian)"),
            LanguageOption("hi", R.string.lang_hi, "हिन्दी (Hindi)"),
            LanguageOption("id", R.string.lang_id, "Bahasa Indonesia"),
            LanguageOption("tl", R.string.lang_tl, "Tagalog (Filipino)"),
            LanguageOption("ja", R.string.lang_ja, "日本語 (Japanese)"),
            LanguageOption("ko", R.string.lang_ko, "한국어 (Korean)"),
            LanguageOption("zh", R.string.lang_zh, "简体中文 (Simplified Chinese)"),
            LanguageOption("zh-TW", R.string.lang_zh_tw, "繁體中文 (Traditional Chinese)")
        )
    }

    fun isLangMatched(optionTag: String, currentTag: String): Boolean {
        if (optionTag == "auto") return isSystemLanguage
        if (isSystemLanguage) return false
        val normOpt = optionTag.lowercase(Locale.ROOT)
        val normCur = currentTag.lowercase(Locale.ROOT)
        if (normOpt == "zh-tw") {
            return normCur.contains("tw") || normCur.contains("hk") || normCur.contains("hant")
        }
        if (normOpt == "zh") {
            return normCur.startsWith("zh") && !normCur.contains("tw") && !normCur.contains("hant")
        }
        return normCur == normOpt || normCur.startsWith("$normOpt-")
    }

    val currentLanguageOption = allLanguages.find { isLangMatched(it.tag, activeTag) } ?: allLanguages.first()
    val currentLanguageName = stringResource(id = currentLanguageOption.titleRes)

    val themes = listOf(
        ThemeOptionItem(
            id = 0,
            title = stringResource(R.string.theme_auto),
            subtitle = stringResource(R.string.theme_auto_desc),
            iconRes = R.drawable.ic_auto_theme,
            previewColors = listOf(Color(0xFFD0BCFF), Color(0xFF1E1E24), Color(0xFFF5F5F7))
        ),
        ThemeOptionItem(
            id = 1,
            title = stringResource(R.string.theme_light),
            subtitle = stringResource(R.string.theme_light_desc),
            iconRes = R.drawable.ic_light_theme,
            previewColors = listOf(Color(0xFF6750A4), Color(0xFFFDF8FD), Color(0xFFEADDFF))
        ),
        ThemeOptionItem(
            id = 2,
            title = stringResource(R.string.theme_dark),
            subtitle = stringResource(R.string.theme_dark_desc),
            iconRes = R.drawable.ic_dark_theme,
            previewColors = listOf(Color(0xFFD0BCFF), Color(0xFF141218), Color(0xFF332D41))
        ),
        ThemeOptionItem(
            id = 3,
            title = stringResource(R.string.theme_amoled),
            subtitle = stringResource(R.string.theme_amoled_desc),
            iconRes = R.drawable.ic_amoled_theme,
            previewColors = listOf(Color(0xFFD0BCFF), Color(0xFF000000), Color(0xFF121212))
        ),
        ThemeOptionItem(
            id = 4,
            title = stringResource(R.string.theme_opal),
            subtitle = stringResource(R.string.theme_opal_desc),
            iconRes = R.drawable.ic_opal_theme,
            previewColors = listOf(Color(0xFF64FFDA), Color(0xFF080E12), Color(0xFF102027))
        ),
        ThemeOptionItem(
            id = 5,
            title = stringResource(R.string.theme_pargelia),
            subtitle = stringResource(R.string.app_name),
            iconRes = R.drawable.ic_pargelia_theme,
            previewColors = listOf(Color(0xFFFF5252), Color(0xFF14080A), Color(0xFF2D1216))
        )
    )

    Column {
        SettingsSectionHeader(title = stringResource(R.string.settings_appearance))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 18.dp)
        ) {
            items(themes, key = { it.id }) { theme ->
                ThemeCard(
                    title = theme.title,
                    subtitle = theme.subtitle,
                    iconRes = theme.iconRes,
                    previewColors = theme.previewColors,
                    isSelected = currentThemeMode == theme.id,
                    onClick = { if (currentThemeMode != theme.id) onThemeChanged(theme.id) }
                )
            }
        }

        SettingsSectionHeader(title = stringResource(R.string.monet_section_title))

        MonetEngineSettingsCard()

        Spacer(modifier = Modifier.height(16.dp))

        SettingsGroup {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.settings_language),
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                supportingContent = {
                    Text("$currentLanguageName • ${currentLanguageOption.nativeSubtitle}")
                },
                leadingContent = {
                    IconContainer(
                        painter = painterResource(id = R.drawable.ic_worldwide),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                },
                trailingContent = {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "${allLanguages.size - 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { showLanguageSheet = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.settings_track_info),
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                supportingContent = {
                    Text(stringResource(R.string.settings_track_info_desc))
                },
                leadingContent = {
                    IconContainer(icon = Icons.Default.Info, color = MaterialTheme.colorScheme.secondary)
                },
                trailingContent = {
                    LiquidGlassSwitch(
                        checked = isTrackInfoBarEnabled,
                        onCheckedChange = { newValue ->
                            isTrackInfoBarEnabled = newValue
                            PrefsManager.saveShowTrackInfoBar(newValue)
                        }
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .toggleable(
                        value = isTrackInfoBarEnabled,
                        onValueChange = { newValue ->
                            isTrackInfoBarEnabled = newValue
                            PrefsManager.saveShowTrackInfoBar(newValue)
                        }
                    ),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }

    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showLanguageSheet = false
                langSearchQuery = ""
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            val filteredLangs = remember(langSearchQuery) {
                if (langSearchQuery.isBlank()) {
                    allLanguages
                } else {
                    val q = langSearchQuery.trim().lowercase()
                    allLanguages.filter {
                        it.nativeSubtitle.lowercase().contains(q) ||
                                it.tag.lowercase().contains(q) ||
                                context.getString(it.titleRes).lowercase().contains(q)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight(0.85f)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.choose_language),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                OutlinedTextField(
                    value = langSearchQuery,
                    onValueChange = { langSearchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (langSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { langSearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredLangs, key = { it.tag }) { lang ->
                        val isSelected = isLangMatched(lang.tag, activeTag)
                        val localizedName = stringResource(id = lang.titleRes)

                        ListItem(
                            headlineContent = {
                                Text(
                                    text = localizedName,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "${lang.nativeSubtitle} (${lang.tag.uppercase(Locale.ROOT)})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            trailingContent = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (lang.tag == "auto") {
                                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                                    } else {
                                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
                                    }
                                    showLanguageSheet = false
                                    langSearchQuery = ""
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonetEngineSettingsCard() {
    val haptic = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme
    val currentPrimary = MaterialTheme.colorScheme.primary

    val activeSource = MonetColorSource.fromId(MonetConfigState.colorSource)
    val activeStyle = MonetPaletteStyle.fromId(MonetConfigState.paletteStyle)
    val customArgb = MonetConfigState.customColorArgb
    val customColor = Color(customArgb)
    val contrastLevel = MonetConfigState.contrastLevel

    val currentHsl = remember(customArgb) {
        FloatArray(3).also { ColorUtils.colorToHSL(customArgb, it) }
    }
    var hueSlider by remember(customArgb) { mutableFloatStateOf(currentHsl[0]) }
    var satSlider by remember(customArgb) { mutableFloatStateOf(currentHsl[1].coerceIn(0.2f, 1f)) }

    SettingsGroup {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MonetColorSource.entries.forEach { source ->
                    val isSelected = activeSource == source
                    val weight by animateFloatAsState(
                        targetValue = if (isSelected) 1.25f else 0.92f,
                        animationSpec = motionScheme.defaultSpatialSpec(),
                        label = "monetSourceWeight"
                    )
                    val corner by animateDpAsState(
                        targetValue = if (isSelected) 22.dp else 16.dp,
                        animationSpec = motionScheme.fastSpatialSpec(),
                        label = "monetSourceCorner"
                    )
                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        animationSpec = motionScheme.defaultEffectsSpec(),
                        label = "monetSourceBg"
                    )
                    val contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    val sourceIcon = when (source) {
                        MonetColorSource.TRACK_COVER -> Icons.Default.Album
                        MonetColorSource.DEVICE_WALLPAPER -> Icons.Default.Wallpaper
                        MonetColorSource.CUSTOM_COLOR -> Icons.Default.Colorize
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            MonetConfigState.updateSource(source.id)
                        },
                        shape = RoundedCornerShape(corner),
                        color = containerColor,
                        modifier = Modifier
                            .weight(weight)
                            .height(82.dp)
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) currentPrimary else Color.Transparent,
                                shape = RoundedCornerShape(corner)
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Icon(
                                imageVector = sourceIcon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = stringResource(id = source.titleRes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(id = activeSource.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            AnimatedVisibility(
                visible = activeSource == MonetColorSource.CUSTOM_COLOR,
                enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(motionScheme.defaultSpatialSpec()) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.monet_custom_color_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    currentPrimary,
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.tertiary,
                                    MaterialTheme.colorScheme.primaryContainer
                                ).forEach { swatch ->
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(swatch)
                                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                    )
                                }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(PRESET_MONET_COLORS, key = { it.toArgb() }) { preset ->
                                val isPresetSelected = customColor.toArgb() == preset.toArgb()
                                val itemCorner by animateDpAsState(
                                    targetValue = if (isPresetSelected) 12.dp else 20.dp,
                                    animationSpec = motionScheme.fastSpatialSpec(),
                                    label = "swatchCorner"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(itemCorner))
                                        .background(preset)
                                        .border(
                                            width = if (isPresetSelected) 2.5.dp else 1.dp,
                                            color = if (isPresetSelected) Color.White else Color.White.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(itemCorner)
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            MonetConfigState.updateCustomColor(preset.toArgb())
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPresetSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(id = R.string.monet_hue_slider_label, hueSlider.toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = hueSlider,
                                onValueChange = { newHue ->
                                    hueSlider = newHue
                                    val newArgb = ColorUtils.HSLToColor(floatArrayOf(newHue, satSlider, 0.62f))
                                    MonetConfigState.updateCustomColor(newArgb)
                                },
                                valueRange = 0f..360f
                            )

                            Text(
                                text = stringResource(id = R.string.monet_chroma_slider_label, (satSlider * 100).toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = satSlider,
                                onValueChange = { newSat ->
                                    satSlider = newSat
                                    val newArgb = ColorUtils.HSLToColor(floatArrayOf(hueSlider, newSat, 0.62f))
                                    MonetConfigState.updateCustomColor(newArgb)
                                },
                                valueRange = 0.2f..1.0f
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = currentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.monet_palette_style_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(MonetPaletteStyle.entries, key = { it.id }) { styleItem ->
                        val isSelected = activeStyle == styleItem
                        val interaction = remember { MutableInteractionSource() }
                        val isPressed by interaction.collectIsPressedAsState()
                        val corner by animateDpAsState(
                            targetValue = when {
                                isPressed -> 12.dp
                                isSelected -> 16.dp
                                else -> 24.dp
                            },
                            animationSpec = motionScheme.fastSpatialSpec(),
                            label = "styleChipCorner"
                        )
                        val previewTriad = remember(currentPrimary, styleItem) {
                            boostColorForVariant(
                                color = currentPrimary,
                                isLightMode = false,
                                style = styleItem,
                                contrastLevel = 0f
                            )
                        }

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                MonetConfigState.updatePaletteStyle(styleItem.id)
                            },
                            interactionSource = interaction,
                            shape = RoundedCornerShape(corner),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) currentPrimary else Color.Transparent,
                                shape = RoundedCornerShape(corner)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                                    listOf(previewTriad.first, previewTriad.second, previewTriad.third).forEach { dotColor ->
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                                .border(0.8.dp, Color.Black.copy(alpha = 0.4f), CircleShape)
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(id = styleItem.titleRes),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Contrast,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.monet_contrast_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%+.0f%%", contrastLevel * 100f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = currentPrimary
                    )
                }

                Slider(
                    value = contrastLevel,
                    onValueChange = { MonetConfigState.updateContrastLevel(it) },
                    valueRange = -0.3f..0.8f,
                    steps = 10
                )
            }
        }
    }
}

@Composable
fun DesignPanel(
    onNavBarStyleChanged: (Int) -> Unit = {},
    isSecureMode: Boolean = false,
    onSecureChanged: (Boolean) -> Unit = {}
) {
    var navBarStyle by remember { mutableIntStateOf(PrefsManager.getNavBarStyle()) }
    var playerBtnStyle by remember { mutableIntStateOf(PrefsManager.getPlayerButtonStyle()) }

    val navStyles = listOf(
        Triple(0, stringResource(R.string.nav_style_liquid), stringResource(R.string.design_style_liquid_desc)),
        Triple(1, stringResource(R.string.nav_style_material), stringResource(R.string.design_style_expressive_desc)),
        Triple(2, stringResource(R.string.nav_style_pill), stringResource(R.string.design_style_pill_desc))
    )

    val playerBtnStyles = listOf(
        Triple(0, stringResource(R.string.nav_style_liquid), stringResource(R.string.design_btn_liquid_desc)),
        Triple(1, stringResource(R.string.nav_style_material), stringResource(R.string.design_btn_expressive_desc)),
        Triple(2, stringResource(R.string.design_btn_classic_title), stringResource(R.string.design_btn_classic_desc))
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSectionHeader(title = stringResource(R.string.settings_nav_style))

        SettingsGroup {
            navStyles.forEachIndexed { idx, (code, title, desc) ->
                val isSelected = navBarStyle == code
                ListItem(
                    headlineContent = {
                        Text(
                            text = title,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    supportingContent = { Text(desc) },
                    leadingContent = {
                        IconContainer(
                            icon = Icons.Default.Palette,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                navBarStyle = code
                                PrefsManager.saveNavBarStyle(code)
                                onNavBarStyleChanged(code)
                            }
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            navBarStyle = code
                            PrefsManager.saveNavBarStyle(code)
                            onNavBarStyleChanged(code)
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                if (idx < navStyles.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        SettingsSectionHeader(title = stringResource(R.string.settings_player_btn_style))

        SettingsGroup {
            playerBtnStyles.forEachIndexed { idx, (code, title, desc) ->
                val isSelected = playerBtnStyle == code
                ListItem(
                    headlineContent = {
                        Text(
                            text = title,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    supportingContent = { Text(desc) },
                    leadingContent = {
                        IconContainer(
                            icon = Icons.Default.PlayArrow,
                            color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                playerBtnStyle = code
                                PrefsManager.savePlayerButtonStyle(code)
                            }
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            playerBtnStyle = code
                            PrefsManager.savePlayerButtonStyle(code)
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                if (idx < playerBtnStyles.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        SettingsSectionHeader(title = stringResource(R.string.settings_privacy))

        SettingsGroup {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.privacy_secure), fontWeight = FontWeight.ExtraBold)
                },
                supportingContent = {
                    Text(stringResource(R.string.privacy_secure_desc))
                },
                leadingContent = {
                    IconContainer(icon = Icons.Rounded.Lock, color = MaterialTheme.colorScheme.tertiary)
                },
                trailingContent = {
                    LiquidGlassSwitch(
                        checked = isSecureMode,
                        onCheckedChange = onSecureChanged
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .toggleable(value = isSecureMode, onValueChange = onSecureChanged),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
fun IntegrationPanel(onFossWearChanged: (Boolean) -> Unit) {
    var isAutoDetectHeadphonesEnabled by remember { mutableStateOf(PrefsManager.getAutoDetectHeadphones()) }
    var isFossWearEnabled by remember { mutableStateOf(PrefsManager.getFossWearEnabled()) }

    SettingsGroup {
        ListItem(
            headlineContent = { Text(stringResource(R.string.integration_auto_detect), fontWeight = FontWeight.ExtraBold) },
            supportingContent = { Text(stringResource(R.string.integration_auto_detect_desc)) },
            leadingContent = { IconContainer(painter = painterResource(id = R.drawable.ic_earphones), color = MaterialTheme.colorScheme.primary) },
            trailingContent = {
                LiquidGlassSwitch(
                    checked = isAutoDetectHeadphonesEnabled,
                    onCheckedChange = { newValue ->
                        isAutoDetectHeadphonesEnabled = newValue
                        PrefsManager.saveAutoDetectHeadphones(newValue)
                    }
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .toggleable(
                    value = isAutoDetectHeadphonesEnabled,
                    onValueChange = { newValue ->
                        isAutoDetectHeadphonesEnabled = newValue
                        PrefsManager.saveAutoDetectHeadphones(newValue)
                    }
                ),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.integration_wear_api), fontWeight = FontWeight.ExtraBold) },
            supportingContent = { Text(stringResource(R.string.integration_wear_api_desc)) },
            leadingContent = { IconContainer(painter = painterResource(id = R.drawable.ic_watch_api), color = MaterialTheme.colorScheme.primary) },
            trailingContent = {
                LiquidGlassSwitch(
                    checked = isFossWearEnabled,
                    onCheckedChange = { newValue ->
                        isFossWearEnabled = newValue
                        PrefsManager.saveFossWearEnabled(newValue)
                        onFossWearChanged(newValue)
                    }
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .toggleable(
                    value = isFossWearEnabled,
                    onValueChange = { newValue ->
                        isFossWearEnabled = newValue
                        PrefsManager.saveFossWearEnabled(newValue)
                        onFossWearChanged(newValue)
                    }
                ),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun DataPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isClearingCache by remember { mutableStateOf(false) }
    var cacheRefreshTick by remember { mutableIntStateOf(0) }
    var totalCacheBytes by remember { mutableLongStateOf(0L) }
    var canvasCacheBytes by remember { mutableLongStateOf(0L) }
    var artworksCacheBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cacheRefreshTick) {
        withContext(Dispatchers.IO) {
            fun folderSize(dir: File?): Long {
                if (dir == null || !dir.exists()) return 0L
                return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
            val canvasVideos = folderSize(File(context.filesDir, "spotify_canvas_videos")) +
                    folderSize(File(context.cacheDir, "canvas_video_cache"))
            val remoteArt = folderSize(context.getDir("album_remote_artworks", Context.MODE_PRIVATE)) +
                    folderSize(context.getDir("artist_remote_assets", Context.MODE_PRIVATE))
            val otherCache = folderSize(context.cacheDir)

            canvasCacheBytes = canvasVideos
            artworksCacheBytes = remoteArt
            totalCacheBytes = (canvasVideos + remoteArt + otherCache).coerceAtLeast(0L)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    IconContainer(icon = Icons.Rounded.Storage, color = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = stringResource(R.string.data_storage_overview_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Canvas: ${Formatter.formatShortFileSize(context, canvasCacheBytes)} • Art: ${Formatter.formatShortFileSize(context, artworksCacheBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = Formatter.formatShortFileSize(context, totalCacheBytes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        SettingsGroup {
            ListItem(
                headlineContent = { Text(stringResource(R.string.data_clear_cache), fontWeight = FontWeight.ExtraBold) },
                supportingContent = { Text(stringResource(R.string.data_clear_cache_desc)) },
                leadingContent = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                        if (isClearingCache) {
                            Box(modifier = Modifier.scale(0.5f)) { MorphingPillIndicator() }
                        } else {
                            IconContainer(painter = painterResource(id = R.drawable.ic_text_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = !isClearingCache) {
                        scope.launch {
                            isClearingCache = true
                            delay(500)
                            val count = LyricsManager.clearCache(context)
                            isClearingCache = false
                            cacheRefreshTick++
                            Toast.makeText(context, context.getString(R.string.msg_cache_cleared, count), Toast.LENGTH_SHORT).show()
                        }
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text(stringResource(R.string.data_clear_canvas_cache), fontWeight = FontWeight.ExtraBold) },
                supportingContent = { Text(stringResource(R.string.data_clear_canvas_cache_desc)) },
                leadingContent = {
                    IconContainer(icon = Icons.Rounded.Movie, color = MaterialTheme.colorScheme.secondary)
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        scope.launch(Dispatchers.IO) {
                            OnlineCanvasManager.clearCache(context)
                            withContext(Dispatchers.Main) {
                                cacheRefreshTick++
                                Toast.makeText(context, context.getString(R.string.msg_canvas_cache_cleared), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text(stringResource(R.string.data_clear_artwork_cache), fontWeight = FontWeight.ExtraBold) },
                supportingContent = { Text(stringResource(R.string.data_clear_artwork_cache_desc)) },
                leadingContent = {
                    IconContainer(icon = Icons.Rounded.DeleteSweep, color = MaterialTheme.colorScheme.tertiary)
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                context.getDir("album_remote_artworks", Context.MODE_PRIVATE).deleteRecursively()
                                context.getDir("artist_remote_assets", Context.MODE_PRIVATE).deleteRecursively()
                                AudioRepository.clearCache()
                            }
                            withContext(Dispatchers.Main) {
                                cacheRefreshTick++
                                Toast.makeText(context, context.getString(R.string.msg_artwork_cache_cleared), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text(stringResource(R.string.data_reset_autoeq), fontWeight = FontWeight.ExtraBold) },
                supportingContent = { Text(stringResource(R.string.data_reset_autoeq_desc)) },
                leadingContent = { IconContainer(painter = painterResource(id = R.drawable.ic_sound_mop), color = MaterialTheme.colorScheme.error) },
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        PrefsManager.saveCurrentAutoEqProfile(null)
                        PrefsManager.saveAutoEqEnabled(false)
                        JamesDspController.setAutoEqEnabled(false)
                        Toast.makeText(context, context.getString(R.string.msg_autoeq_reset), Toast.LENGTH_SHORT).show()
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
fun AboutPanel(isAutoUpdateEnabled: Boolean, onAutoUpdateChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    var easterEggClickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = "https://github.com/A1cInt.png",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(32.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 400) {
                            easterEggClickCount++
                            if (easterEggClickCount >= 5) {
                                easterEggClickCount = 0
                                context.startActivity(Intent(context, EasterEggActivity::class.java))
                            }
                        } else {
                            easterEggClickCount = 1
                        }
                        lastClickTime = currentTime
                    }
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.about_developer),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.about_version_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsSectionHeader(title = stringResource(R.string.settings_contact))

    SettingsGroup {
        ListItem(
            headlineContent = { Text(stringResource(R.string.contact_pm), fontWeight = FontWeight.ExtraBold) },
            supportingContent = { Text(stringResource(R.string.contact_pm_desc)) },
            leadingContent = { IconContainer(painter = painterResource(id = R.drawable.ic_icon_telegram), color = Color(0xFF2AABEE)) },
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/alcint"))) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.contact_channel), fontWeight = FontWeight.ExtraBold) },
            supportingContent = { Text(stringResource(R.string.contact_channel_desc)) },
            leadingContent = { IconContainer(painter = painterResource(id = R.drawable.ic_icon_telegram), color = Color(0xFF2AABEE)) },
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/AlcReleases"))) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.contact_group), fontWeight = FontWeight.ExtraBold) },
            supportingContent = { Text(stringResource(R.string.contact_group_desc)) },
            leadingContent = { IconContainer(painter = painterResource(id = R.drawable.ic_icon_telegram), color = Color(0xFF2AABEE)) },
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/AlcIntGroup"))) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.contact_github), fontWeight = FontWeight.ExtraBold) },
            supportingContent = { Text(stringResource(R.string.contact_github_desc)) },
            leadingContent = { IconContainer(icon = Icons.Rounded.Code, color = MaterialTheme.colorScheme.primary) },
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/A1cInt"))) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.contact_donationalerts), fontWeight = FontWeight.ExtraBold) },
            supportingContent = { Text(stringResource(R.string.contact_donationalerts_desc)) },
            leadingContent = { IconContainer(icon = Icons.Rounded.Favorite, color = Color(0xFFFF6D00)) },
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.donationalerts.com/r/alcint"))) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        val sessionAddr = stringResource(R.string.contact_session_desc)
        val copiedMsg = stringResource(R.string.msg_copied)
        ListItem(
            headlineContent = { Text(stringResource(R.string.contact_session), fontWeight = FontWeight.ExtraBold) },
            supportingContent = { Text(sessionAddr, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = { IconContainer(icon = Icons.Rounded.ContentCopy, color = MaterialTheme.colorScheme.tertiary) },
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cb?.setPrimaryClip(ClipData.newPlainText("Session ID", sessionAddr))
                    Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsGroup {
        ListItem(
            headlineContent = { Text(stringResource(R.string.update_auto_check), fontWeight = FontWeight.ExtraBold) },
            supportingContent = { Text(stringResource(R.string.update_auto_check_desc)) },
            leadingContent = { IconContainer(icon = Icons.Default.Sync, color = MaterialTheme.colorScheme.secondary) },
            trailingContent = { LiquidGlassSwitch(checked = isAutoUpdateEnabled, onCheckedChange = onAutoUpdateChanged) },
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .toggleable(value = isAutoUpdateEnabled, onValueChange = onAutoUpdateChanged),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.update_check_now), fontWeight = FontWeight.ExtraBold) },
            leadingContent = { IconContainer(icon = Icons.Default.SystemUpdate, color = MaterialTheme.colorScheme.tertiary) },
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable { UpdateManager.checkForUpdates(context, isManual = true) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}