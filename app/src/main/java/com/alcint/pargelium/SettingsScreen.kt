@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alcint.pargelium

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SettingsSuggest
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

    var isSecureEnabled by remember { mutableStateOf(PrefsManager.getSecureMode()) }
    var isFossWearEnabled by remember { mutableStateOf(PrefsManager.getFossWearEnabled()) }
    var isAutoDetectHeadphonesEnabled by remember { mutableStateOf(PrefsManager.getAutoDetectHeadphones()) }

    var isClearingCache by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    var isAdvancedSettingsEnabled by remember { mutableStateOf(PrefsManager.getAdvancedSettingsEnabled()) }
    var showAdvancedWarningDialog by remember { mutableStateOf(false) }

    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentLanguageCode = if (!currentLocales.isEmpty) currentLocales[0]?.language else Locale.getDefault().language

    val supportedLanguages = listOf(
        "ru" to stringResource(R.string.lang_ru),
        "en" to stringResource(R.string.lang_en),
        "es" to stringResource(R.string.lang_es),
        "it" to stringResource(R.string.lang_it),
        "ja" to stringResource(R.string.lang_ja),
        "pl" to stringResource(R.string.lang_pl),
        "sv" to stringResource(R.string.lang_sv),
        "de" to stringResource(R.string.lang_de),
        "is" to stringResource(R.string.lang_is),
        "hi" to stringResource(R.string.lang_hi),
        "fr" to stringResource(R.string.lang_fr),
        "el" to stringResource(R.string.lang_el),
        "fi" to stringResource(R.string.lang_fi),
        "kk" to stringResource(R.string.lang_kk),
        "ga" to stringResource(R.string.lang_ga)
    )

    val currentLanguageName = supportedLanguages.find { it.first == currentLanguageCode }?.second ?: stringResource(R.string.lang_ru)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
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

        // Внешка
        Text(
            text = stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )

        val themes = listOf(
            Triple(0, stringResource(R.string.theme_auto), R.drawable.ic_auto_theme),
            Triple(1, stringResource(R.string.theme_light), R.drawable.ic_light_theme),
            Triple(2, stringResource(R.string.theme_dark), R.drawable.ic_dark_theme),
            Triple(3, stringResource(R.string.theme_amoled), R.drawable.ic_amoled_theme),
            Triple(4, stringResource(R.string.theme_opal), R.drawable.ic_opal_theme),
            Triple(5, stringResource(R.string.theme_pargelia), R.drawable.ic_pargelia_theme)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
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

        // Основные настройки
        SettingsGroup {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language), fontWeight = FontWeight.Bold) },
                supportingContent = { Text(currentLanguageName) },
                leadingContent = {
                    IconContainer(painter = painterResource(id = R.drawable.ic_worldwide), color = MaterialTheme.colorScheme.tertiary)
                },
                modifier = Modifier.clickable { showLanguageSheet = true },
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
                    }
                ),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }

        // Жалкий Continuous Mix в моём жалком исполнении, который я всё равно не смог нормально реализовать, но он есть, и его можно включить
        Text(
            text = "Воспроизведение",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 8.dp)
        )
        ContinuousMixSettingsCard(context = context)

        // Интеграции
        Text(
            text = stringResource(R.string.settings_integration),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 8.dp)
        )
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
        }

        // Данные
        Text(
            text = stringResource(R.string.settings_data),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 8.dp)
        )
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

        // Связь со мной
        Text(
            text = stringResource(R.string.settings_contact),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 8.dp)
        )
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
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/comalcint"))
                    context.startActivity(intent)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }

        // Продвинутые настройки
        Text(
            text = "Продвинутые настройки",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 8.dp)
        )
        SettingsGroup {
            ListItem(
                headlineContent = { Text("Режим разработчика", fontWeight = FontWeight.Bold) },
                supportingContent = { Text("Открытие доступа к системным переключателям плеера") },
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
                        headlineContent = { Text("Управление модулями", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text("Включение и отключение компонентов приложения") },
                        modifier = Modifier.clickable { onNavigateToAdvanced() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(280.dp))
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
                        val isSelected = currentLanguageCode == code
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
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
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
            title = { Text("Внимание") },
            text = { Text("Эти настройки предназначены для глубокой кастомизации. Отключение системных компонентов (стриминг, визуализация, эквалайзер) может привести к неожиданному поведению приложения или его нестабильности.\n\nВы уверены, что хотите продолжить?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        isAdvancedSettingsEnabled = true
                        PrefsManager.saveAdvancedSettingsEnabled(true)
                        showAdvancedWarningDialog = false
                    }
                ) {
                    Text("Я понимаю", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdvancedWarningDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
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