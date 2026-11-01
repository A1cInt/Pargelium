@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.alcint.pargelium.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.alcint.pargelium.EasterEggActivity
import com.alcint.pargelium.LyricsManager
import com.alcint.pargelium.MorphingPillIndicator
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.R
import com.alcint.pargelium.UpdateManager
import com.alcint.pargelium.liquid.LiquidGlassSwitch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AppearancePanel(
    currentThemeMode: Int,
    onThemeChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    var isTrackInfoBarEnabled by remember { mutableStateOf(PrefsManager.getShowTrackInfoBar()) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val isSystemLanguage = currentLocales.isEmpty
    val currentLanguageTag = if (!isSystemLanguage) currentLocales[0]?.toLanguageTag() ?: Locale.getDefault().toLanguageTag() else Locale.getDefault().toLanguageTag()

    val supportedLanguages = remember(currentLanguageTag, isSystemLanguage) {
        listOf(
            "auto" to context.getString(R.string.lang_system),
            "ru" to context.getString(R.string.lang_ru),
            "en" to context.getString(R.string.lang_en)
        )
    }

    val currentLanguageName = if (isSystemLanguage) stringResource(R.string.lang_system) else supportedLanguages.find { it.first == currentLanguageTag }?.second ?: stringResource(R.string.lang_system)

    val themes = remember {
        listOf(
            Triple(0, context.getString(R.string.theme_auto), R.drawable.ic_auto_theme),
            Triple(1, context.getString(R.string.theme_light), R.drawable.ic_light_theme),
            Triple(2, context.getString(R.string.theme_dark), R.drawable.ic_dark_theme),
            Triple(3, context.getString(R.string.theme_amoled), R.drawable.ic_amoled_theme),
            Triple(4, context.getString(R.string.theme_opal), R.drawable.ic_opal_theme),
            Triple(5, context.getString(R.string.theme_pargelia), R.drawable.ic_pargelia_theme)
        )
    }

    Column {
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
            items(themes) { theme ->
                ThemeCard(
                    title = theme.second, iconRes = theme.third, isSelected = currentThemeMode == theme.first,
                    onClick = { if (currentThemeMode != theme.first) onThemeChanged(theme.first) }
                )
            }
        }

        SettingsGroup {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_track_info), fontWeight = FontWeight.Bold) },
                supportingContent = { Text(stringResource(R.string.settings_track_info_desc)) },
                leadingContent = { IconContainer(icon = Icons.Default.Info, color = MaterialTheme.colorScheme.secondary) },
                trailingContent = { LiquidGlassSwitch(checked = isTrackInfoBarEnabled, onCheckedChange = { newValue -> isTrackInfoBarEnabled = newValue; PrefsManager.saveShowTrackInfoBar(newValue) }) },
                modifier = Modifier.toggleable(value = isTrackInfoBarEnabled, onValueChange = { newValue -> isTrackInfoBarEnabled = newValue; PrefsManager.saveShowTrackInfoBar(newValue) }),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language), fontWeight = FontWeight.Bold) },
                supportingContent = { Text(currentLanguageName) },
                leadingContent = { IconContainer(painter = painterResource(id = R.drawable.ic_worldwide), color = MaterialTheme.colorScheme.tertiary) },
                modifier = Modifier.clickable { showLanguageSheet = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }

    if (showLanguageSheet) {
        ModalBottomSheet(onDismissRequest = { showLanguageSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(text = stringResource(R.string.choose_language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                LazyColumn {
                    items(supportedLanguages) { (code, name) ->
                        val isSelected = if (code == "auto") isSystemLanguage else (!isSystemLanguage && currentLanguageTag == code)
                        ListItem(
                            headlineContent = { Text(text = name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                            trailingContent = { if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                if (code == "auto") AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                                else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                                showLanguageSheet = false
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
fun DesignPanel() {
    SettingsGroup {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_design), fontWeight = FontWeight.Bold) },
            supportingContent = { Text("Здесь будут располагаться параметры кастомизации интерфейса") },
            leadingContent = { IconContainer(icon = Icons.Default.Palette, color = MaterialTheme.colorScheme.primary) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun IntegrationPanel(onFossWearChanged: (Boolean) -> Unit) {
    var isAutoDetectHeadphonesEnabled by remember { mutableStateOf(PrefsManager.getAutoDetectHeadphones()) }
    var isFossWearEnabled by remember { mutableStateOf(PrefsManager.getFossWearEnabled()) }

    SettingsGroup {
        ListItem(
            headlineContent = { Text(stringResource(R.string.integration_auto_detect), fontWeight = FontWeight.Bold) },
            supportingContent = { Text(stringResource(R.string.integration_auto_detect_desc)) },
            leadingContent = { IconContainer(painter = painterResource(id = R.drawable.ic_earphones), color = MaterialTheme.colorScheme.primary) },
            trailingContent = { LiquidGlassSwitch(checked = isAutoDetectHeadphonesEnabled, onCheckedChange = { newValue -> isAutoDetectHeadphonesEnabled = newValue; PrefsManager.saveAutoDetectHeadphones(newValue) }) },
            modifier = Modifier.toggleable(value = isAutoDetectHeadphonesEnabled, onValueChange = { newValue -> isAutoDetectHeadphonesEnabled = newValue; PrefsManager.saveAutoDetectHeadphones(newValue) }),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.integration_wear_api), fontWeight = FontWeight.Bold) },
            supportingContent = { Text(stringResource(R.string.integration_wear_api_desc)) },
            leadingContent = { IconContainer(painter = painterResource(id = R.drawable.ic_watch_api), color = MaterialTheme.colorScheme.primary) },
            trailingContent = { LiquidGlassSwitch(checked = isFossWearEnabled, onCheckedChange = { newValue -> isFossWearEnabled = newValue; PrefsManager.saveFossWearEnabled(newValue); onFossWearChanged(newValue) }) },
            modifier = Modifier.toggleable(value = isFossWearEnabled, onValueChange = { newValue -> isFossWearEnabled = newValue; PrefsManager.saveFossWearEnabled(newValue); onFossWearChanged(newValue) }),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun DataPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isClearingCache by remember { mutableStateOf(false) }

    SettingsGroup {
        ListItem(
            headlineContent = { Text(stringResource(R.string.data_clear_cache), fontWeight = FontWeight.Bold) },
            supportingContent = { Text(stringResource(R.string.data_clear_cache_desc)) },
            leadingContent = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                    if (isClearingCache) Box(modifier = Modifier.scale(0.5f)) { MorphingPillIndicator() }
                    else IconContainer(painter = painterResource(id = R.drawable.ic_text_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            modifier = Modifier.clickable(enabled = !isClearingCache) {
                scope.launch {
                    isClearingCache = true
                    delay(800)
                    val count = LyricsManager.clearCache(context)
                    isClearingCache = false
                    Toast.makeText(context, context.getString(R.string.msg_cache_cleared, count), Toast.LENGTH_SHORT).show()
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.data_reset_autoeq), fontWeight = FontWeight.Bold) },
            supportingContent = { Text(stringResource(R.string.data_reset_autoeq_desc)) },
            leadingContent = { IconContainer(painter = painterResource(id = R.drawable.ic_sound_mop), color = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickable {
                PrefsManager.saveCurrentAutoEqProfile(null)
                PrefsManager.saveAutoEqEnabled(false)
                Toast.makeText(context, context.getString(R.string.msg_autoeq_reset), Toast.LENGTH_SHORT).show()
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun AboutPanel(isAutoUpdateEnabled: Boolean, onAutoUpdateChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    var easterEggClickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = "https://github.com/A1cInt.png", contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(100.dp).clip(CircleShape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 400) {
                    easterEggClickCount++
                    if (easterEggClickCount >= 5) {
                        easterEggClickCount = 0
                        context.startActivity(Intent(context, EasterEggActivity::class.java))
                    }
                } else easterEggClickCount = 1
                lastClickTime = currentTime
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(text = stringResource(R.string.about_developer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Spacer(modifier = Modifier.height(24.dp))

    SettingsGroup {
        ListItem(
            headlineContent = { Text(stringResource(R.string.contact_pm), fontWeight = FontWeight.Bold) },
            supportingContent = { Text(stringResource(R.string.contact_pm_desc)) },
            leadingContent = { IconContainer(painter = painterResource(id = R.drawable.ic_icon_telegram), color = Color(0xFF2AABEE)) },
            modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/alcint"))) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.contact_channel), fontWeight = FontWeight.Bold) },
            supportingContent = { Text(stringResource(R.string.contact_channel_desc)) },
            leadingContent = { IconContainer(painter = painterResource(id = R.drawable.ic_icon_telegram), color = Color(0xFF2AABEE)) },
            modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/AlcReleases"))) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsGroup {
        ListItem(
            headlineContent = { Text(stringResource(R.string.about_version), fontWeight = FontWeight.Bold) },
            supportingContent = { Text(stringResource(R.string.about_version_desc)) },
            leadingContent = { IconContainer(icon = Icons.Default.Info, color = MaterialTheme.colorScheme.primary) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.update_auto_check), fontWeight = FontWeight.Bold) },
            supportingContent = { Text(stringResource(R.string.update_auto_check_desc)) },
            leadingContent = { IconContainer(icon = Icons.Default.Sync, color = MaterialTheme.colorScheme.secondary) },
            trailingContent = { LiquidGlassSwitch(checked = isAutoUpdateEnabled, onCheckedChange = onAutoUpdateChanged) },
            modifier = Modifier.toggleable(value = isAutoUpdateEnabled, onValueChange = onAutoUpdateChanged),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.update_check_now), fontWeight = FontWeight.Bold) },
            leadingContent = { IconContainer(icon = Icons.Default.SystemUpdate, color = MaterialTheme.colorScheme.tertiary) },
            modifier = Modifier.clickable { UpdateManager.checkForUpdates(context, isManual = true) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}