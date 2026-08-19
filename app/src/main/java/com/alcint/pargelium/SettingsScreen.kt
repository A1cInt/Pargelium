@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alcint.pargelium

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

enum class SettingsRoute {
    MAIN, APPEARANCE, PLAYBACK, INTEGRATION, DATA, CONTACT, ADVANCED, ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentThemeMode: Int,
    onThemeChanged: (Int) -> Unit,
    onSecureChanged: (Boolean) -> Unit,
    onFossWearChanged: (Boolean) -> Unit,
    onNavigateToAdvanced: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentRoute by remember { mutableStateOf(SettingsRoute.MAIN) }

    var isSecureEnabled by remember { mutableStateOf(PrefsManager.getSecureMode()) }
    var isFossWearEnabled by remember { mutableStateOf(PrefsManager.getFossWearEnabled()) }
    var isAutoDetectHeadphonesEnabled by remember { mutableStateOf(PrefsManager.getAutoDetectHeadphones()) }
    var isTrackInfoBarEnabled by remember { mutableStateOf(PrefsManager.getShowTrackInfoBar()) }
    var isAutoUpdateEnabled by remember { mutableStateOf(PrefsManager.getAutoUpdateEnabled()) }

    var isClearingCache by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    var isAdvancedSettingsEnabled by remember { mutableStateOf(PrefsManager.getAdvancedSettingsEnabled()) }
    var showAdvancedWarningDialog by remember { mutableStateOf(false) }

    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val isSystemLanguage = currentLocales.isEmpty
    val currentLanguageTag = if (!isSystemLanguage) {
        currentLocales[0]?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
    } else {
        Locale.getDefault().toLanguageTag()
    }

    val supportedLanguages = remember(currentLanguageTag, isSystemLanguage) {
        listOf(
            "auto" to context.getString(R.string.lang_system),
            "ru" to context.getString(R.string.lang_ru),
            "en" to context.getString(R.string.lang_en),
            "es" to context.getString(R.string.lang_es),
            "it" to context.getString(R.string.lang_it),
            "ja" to context.getString(R.string.lang_ja),
            "pl" to context.getString(R.string.lang_pl),
            "sv" to context.getString(R.string.lang_sv),
            "de" to context.getString(R.string.lang_de),
            "is" to context.getString(R.string.lang_is),
            "hi" to context.getString(R.string.lang_hi),
            "fr" to context.getString(R.string.lang_fr),
            "el" to context.getString(R.string.lang_el),
            "fi" to context.getString(R.string.lang_fi),
            "kk" to context.getString(R.string.lang_kk),
            "ga" to context.getString(R.string.lang_ga),
            "id" to context.getString(R.string.lang_id),
            "tl" to context.getString(R.string.lang_tl),
            "zh-CN" to context.getString(R.string.lang_zh),
            "zh-TW" to context.getString(R.string.lang_zh_tw),
            "uk" to context.getString(R.string.lang_uk)
        )
    }

    val currentLanguageName = if (isSystemLanguage) {
        stringResource(R.string.lang_system)
    } else {
        supportedLanguages.find { it.first == currentLanguageTag }?.second ?: stringResource(R.string.lang_system)
    }

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

    BackHandler(enabled = currentRoute != SettingsRoute.MAIN) {
        currentRoute = SettingsRoute.MAIN
    }

    AnimatedContent(
        targetState = currentRoute,
        transitionSpec = {
            if (targetState != SettingsRoute.MAIN && initialState == SettingsRoute.MAIN) {
                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
            } else {
                (slideInHorizontally { width -> -width / 3 } + fadeIn()).togetherWith(slideOutHorizontally { width -> width } + fadeOut())
            }
        },
        label = "settings_navigation"
    ) { route ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (route == SettingsRoute.MAIN) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SettingsSuggest, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentRoute = SettingsRoute.MAIN }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (route) {
                            SettingsRoute.APPEARANCE -> stringResource(R.string.settings_appearance)
                            SettingsRoute.PLAYBACK -> stringResource(R.string.settings_playback)
                            SettingsRoute.INTEGRATION -> stringResource(R.string.settings_integration)
                            SettingsRoute.DATA -> stringResource(R.string.settings_data)
                            SettingsRoute.CONTACT -> stringResource(R.string.settings_contact)
                            SettingsRoute.ADVANCED -> stringResource(R.string.settings_advanced)
                            SettingsRoute.ABOUT -> stringResource(R.string.settings_about)
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                when (route) {
                    SettingsRoute.MAIN -> {
                        SettingsGroup {
                            SettingsMenuEntry(title = stringResource(R.string.settings_appearance), icon = painterResource(R.drawable.ic_dark_theme), tint = MaterialTheme.colorScheme.primary) { currentRoute = SettingsRoute.APPEARANCE }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsMenuEntry(title = stringResource(R.string.settings_playback), icon = painterResource(R.drawable.ic_action_key), tint = MaterialTheme.colorScheme.secondary) { currentRoute = SettingsRoute.PLAYBACK }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsMenuEntry(title = stringResource(R.string.settings_integration), icon = painterResource(R.drawable.ic_watch_api), tint = MaterialTheme.colorScheme.tertiary) { currentRoute = SettingsRoute.INTEGRATION }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsMenuEntry(title = stringResource(R.string.settings_data), icon = painterResource(R.drawable.ic_text_delete), tint = MaterialTheme.colorScheme.error) { currentRoute = SettingsRoute.DATA }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsMenuEntry(title = stringResource(R.string.settings_contact), icon = painterResource(R.drawable.ic_social), tint = MaterialTheme.colorScheme.primary) { currentRoute = SettingsRoute.CONTACT }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsMenuEntry(title = stringResource(R.string.settings_advanced), icon = Icons.Default.SettingsSuggest, tint = MaterialTheme.colorScheme.error) { currentRoute = SettingsRoute.ADVANCED }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsMenuEntry(title = stringResource(R.string.settings_about), icon = Icons.Default.Info, tint = MaterialTheme.colorScheme.secondary) { currentRoute = SettingsRoute.ABOUT }
                        }
                    }

                    SettingsRoute.APPEARANCE -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)
                        ) {
                            items(themes) { theme ->
                                ThemeCard(
                                    title = theme.second,
                                    iconRes = theme.third,
                                    isSelected = currentThemeMode == theme.first,
                                    onClick = {
                                        if (currentThemeMode != theme.first) {
                                            onThemeChanged(theme.first)
                                        }
                                    }
                                )
                            }
                        }

                        SettingsGroup {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_track_info), fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(stringResource(R.string.settings_track_info_desc)) },
                                leadingContent = {
                                    IconContainer(icon = Icons.Default.Info, color = MaterialTheme.colorScheme.secondary)
                                },
                                trailingContent = {
                                    Switch(checked = isTrackInfoBarEnabled, onCheckedChange = null)
                                },
                                modifier = Modifier.toggleable(
                                    value = isTrackInfoBarEnabled,
                                    onValueChange = { newValue ->
                                        isTrackInfoBarEnabled = newValue
                                        PrefsManager.saveShowTrackInfoBar(newValue)
                                    }
                                ),
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_language), fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(currentLanguageName) },
                                leadingContent = {
                                    IconContainer(painter = painterResource(id = R.drawable.ic_worldwide), color = MaterialTheme.colorScheme.tertiary)
                                },
                                modifier = Modifier.clickable { showLanguageSheet = true },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }

                    SettingsRoute.PLAYBACK -> {
                        ContinuousMixSettingsCard(context = context)
                    }

                    SettingsRoute.INTEGRATION -> {
                        SettingsGroup {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.integration_auto_detect), fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(stringResource(R.string.integration_auto_detect_desc)) },
                                leadingContent = {
                                    IconContainer(painter = painterResource(id = R.drawable.ic_earphones), color = MaterialTheme.colorScheme.primary)
                                },
                                trailingContent = {
                                    Switch(checked = isAutoDetectHeadphonesEnabled, onCheckedChange = null)
                                },
                                modifier = Modifier.toggleable(
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
                                headlineContent = { Text(stringResource(R.string.integration_wear_api), fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(stringResource(R.string.integration_wear_api_desc)) },
                                leadingContent = {
                                    IconContainer(painter = painterResource(id = R.drawable.ic_watch_api), color = MaterialTheme.colorScheme.primary)
                                },
                                trailingContent = {
                                    Switch(checked = isFossWearEnabled, onCheckedChange = null)
                                },
                                modifier = Modifier.toggleable(
                                    value = isFossWearEnabled,
                                    onValueChange = { newValue ->
                                        isFossWearEnabled = newValue
                                        PrefsManager.saveFossWearEnabled(newValue)
                                        onFossWearChanged(newValue)
                                    }
                                ),
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                            ListItem(
                                headlineContent = { Text(stringResource(R.string.privacy_secure), fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(stringResource(R.string.privacy_secure_desc)) },
                                leadingContent = {
                                    IconContainer(painter = painterResource(id = R.drawable.ic_mobile_def), color = MaterialTheme.colorScheme.secondary)
                                },
                                trailingContent = {
                                    Switch(checked = isSecureEnabled, onCheckedChange = null)
                                },
                                modifier = Modifier.toggleable(
                                    value = isSecureEnabled,
                                    onValueChange = { newValue ->
                                        isSecureEnabled = newValue
                                        PrefsManager.saveSecureMode(newValue)
                                        onSecureChanged(newValue)

                                        var ctx = context
                                        while (ctx is android.content.ContextWrapper) {
                                            if (ctx is android.app.Activity) break
                                            ctx = ctx.baseContext
                                        }

                                        (ctx as? android.app.Activity)?.window?.let { window ->
                                            if (newValue) {
                                                window.setFlags(
                                                    android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                                    android.view.WindowManager.LayoutParams.FLAG_SECURE
                                                )
                                            } else {
                                                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                                            }
                                            window.attributes = window.attributes
                                        }
                                    }
                                ),
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }

                    SettingsRoute.DATA -> {
                        SettingsGroup {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.data_clear_cache), fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(stringResource(R.string.data_clear_cache_desc)) },
                                leadingContent = {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                                        if (isClearingCache) {
                                            Box(modifier = Modifier.scale(0.5f)) {
                                                MorphingPillIndicator()
                                            }
                                        } else {
                                            IconContainer(painter = painterResource(id = R.drawable.ic_text_delete), color = MaterialTheme.colorScheme.error)
                                        }
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
                                leadingContent = {
                                    IconContainer(painter = painterResource(id = R.drawable.ic_sound_mop), color = MaterialTheme.colorScheme.error)
                                },
                                modifier = Modifier.clickable {
                                    PrefsManager.saveCurrentAutoEqProfile(null)
                                    PrefsManager.saveAutoEqEnabled(false)
                                    Toast.makeText(context, context.getString(R.string.msg_autoeq_reset), Toast.LENGTH_SHORT).show()
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }

                    SettingsRoute.CONTACT -> {
                        SettingsGroup {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.contact_pm), fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(stringResource(R.string.contact_pm_desc)) },
                                leadingContent = {
                                    IconContainer(painter = painterResource(id = R.drawable.ic_magic_handshake), color = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/alcint"))
                                    context.startActivity(intent)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                            ListItem(
                                headlineContent = { Text(stringResource(R.string.contact_channel), fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(stringResource(R.string.contact_channel_desc)) },
                                leadingContent = {
                                    IconContainer(painter = painterResource(id = R.drawable.ic_social), color = MaterialTheme.colorScheme.tertiary)
                                },
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/AlcReleases"))
                                    context.startActivity(intent)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }

                    SettingsRoute.ADVANCED -> {
                        SettingsGroup {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.adv_dev_mode), fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(stringResource(R.string.adv_dev_mode_desc)) },
                                leadingContent = {
                                    IconContainer(Icons.Default.SettingsSuggest, color = MaterialTheme.colorScheme.error)
                                },
                                trailingContent = {
                                    Switch(checked = isAdvancedSettingsEnabled, onCheckedChange = null)
                                },
                                modifier = Modifier.toggleable(
                                    value = isAdvancedSettingsEnabled,
                                    onValueChange = { newValue ->
                                        if (newValue) {
                                            showAdvancedWarningDialog = true
                                        } else {
                                            isAdvancedSettingsEnabled = false
                                            PrefsManager.saveAdvancedSettingsEnabled(false)
                                        }
                                    }
                                ),
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            AnimatedVisibility(visible = isAdvancedSettingsEnabled) {
                                Column {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.adv_module_control), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                                        supportingContent = { Text(stringResource(R.string.adv_module_control_desc)) },
                                        modifier = Modifier.clickable { onNavigateToAdvanced() },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                    }

                    SettingsRoute.ABOUT -> {
                        var easterEggClickCount by remember { mutableStateOf(0) }
                        var lastClickTime by remember { mutableStateOf(0L) }

                        SettingsGroup {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.about_version), fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(stringResource(R.string.about_version_desc)) },
                                leadingContent = {
                                    IconContainer(icon = Icons.Default.Info, color = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier.clickable(
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
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                            ListItem(
                                headlineContent = { Text(stringResource(R.string.update_auto_check), fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(stringResource(R.string.update_auto_check_desc)) },
                                leadingContent = {
                                    IconContainer(icon = Icons.Default.Sync, color = MaterialTheme.colorScheme.secondary)
                                },
                                trailingContent = {
                                    Switch(checked = isAutoUpdateEnabled, onCheckedChange = null)
                                },
                                modifier = Modifier.toggleable(
                                    value = isAutoUpdateEnabled,
                                    onValueChange = { newValue ->
                                        isAutoUpdateEnabled = newValue
                                        PrefsManager.saveAutoUpdateEnabled(newValue)
                                    }
                                ),
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                            ListItem(
                                headlineContent = { Text(stringResource(R.string.update_check_now), fontWeight = FontWeight.Bold) },
                                leadingContent = {
                                    IconContainer(icon = Icons.Default.SystemUpdate, color = MaterialTheme.colorScheme.tertiary)
                                },
                                modifier = Modifier.clickable {
                                    UpdateManager.checkForUpdates(context, isManual = true)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(280.dp))
            }
        }
    }

    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = stringResource(R.string.choose_language),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                LazyColumn {
                    items(supportedLanguages) { (code, name) ->
                        val isSelected = if (code == "auto") isSystemLanguage else (!isSystemLanguage && currentLanguageTag == code)
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingContent = {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable {
                                if (code == "auto") {
                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                                } else {
                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                                }
                                showLanguageSheet = false
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }

    if (showAdvancedWarningDialog) {
        AlertDialog(
            onDismissRequest = { showAdvancedWarningDialog = false },
            icon = { Icon(Icons.Default.SettingsSuggest, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.adv_warning_title)) },
            text = { Text(stringResource(R.string.adv_warning_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isAdvancedSettingsEnabled = true
                        PrefsManager.saveAdvancedSettingsEnabled(true)
                        showAdvancedWarningDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_understand), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdvancedWarningDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsMenuEntry(
    title: String,
    icon: Painter,
    tint: Color,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
        leadingContent = { IconContainer(painter = icon, color = tint) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SettingsMenuEntry(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
        leadingContent = { IconContainer(icon = icon, color = tint) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun IconContainer(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun IconContainer(painter: Painter, color: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(painter, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ThemeCard(title: String, iconRes: Int, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .width(104.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(painterResource(id = iconRes), contentDescription = null, tint = contentColor, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}