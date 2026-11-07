@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.alcint.pargelium.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import coil.compose.AsyncImage
import com.alcint.pargelium.AudioRepository
import com.alcint.pargelium.EasterEggActivity
import com.alcint.pargelium.LyricsManager
import com.alcint.pargelium.MorphingPillIndicator
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.R
import com.alcint.pargelium.UpdateManager
import com.alcint.pargelium.audio.JamesDspController
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

@Composable
fun AppearancePanel(
    currentThemeMode: Int,
    onThemeChanged: (Int) -> Unit
) {
    val context = LocalContext.current
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

    // Все 27 языковых локалей из strings.xml
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
                .padding(top = 4.dp, bottom = 20.dp)
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