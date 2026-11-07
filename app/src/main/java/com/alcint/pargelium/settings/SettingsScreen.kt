@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.alcint.pargelium.settings

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcint.pargelium.ContinuousMixSettingsCard
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.R
import com.alcint.pargelium.liquid.LiquidGlassSwitch
import java.util.Locale

enum class SettingsRoute {
    MAIN, APPEARANCE, DESIGN, PLAYBACK, INTEGRATION, DATA, ABOUT
}

@Composable
fun SettingsScreen(
    currentThemeMode: Int,
    onThemeChanged: (Int) -> Unit,
    onNavBarStyleChanged: (Int) -> Unit = {},
    onSecureChanged: (Boolean) -> Unit,
    onFossWearChanged: (Boolean) -> Unit,
    onNavigateToAdvanced: () -> Unit
) {
    val context = LocalContext.current
    var currentRoute by remember { mutableStateOf(SettingsRoute.MAIN) }
    var isAutoUpdateEnabled by remember { mutableStateOf(PrefsManager.getAutoUpdateEnabled()) }
    var isSecureMode by remember { mutableStateOf(PrefsManager.getSecureMode()) }
    var searchQuery by remember { mutableStateOf("") }

    BackHandler(enabled = currentRoute != SettingsRoute.MAIN || searchQuery.isNotEmpty()) {
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
        } else {
            currentRoute = SettingsRoute.MAIN
        }
    }

    val themeBadgeName = when (currentThemeMode) {
        0 -> stringResource(R.string.theme_auto)
        1 -> stringResource(R.string.theme_light)
        2 -> stringResource(R.string.theme_dark)
        3 -> stringResource(R.string.theme_amoled)
        4 -> stringResource(R.string.theme_opal)
        5 -> stringResource(R.string.theme_pargelia)
        else -> stringResource(R.string.theme_dark)
    }

    val activeLocaleTag = remember {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            appLocales[0]?.language?.uppercase(Locale.ROOT) ?: "SYS"
        } else {
            Locale.getDefault().language.uppercase(Locale.ROOT)
        }
    }

    AnimatedContent(
        targetState = currentRoute,
        transitionSpec = {
            if (targetState != SettingsRoute.MAIN && initialState == SettingsRoute.MAIN) {
                (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { width -> width / 2 } + fadeIn(tween(250)))
                    .togetherWith(slideOutHorizontally(tween(250)) { width -> -width / 4 } + fadeOut(tween(200)))
            } else {
                (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { width -> -width / 4 } + fadeIn(tween(250)))
                    .togetherWith(slideOutHorizontally(tween(250)) { width -> width / 2 } + fadeOut(tween(200)))
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
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SettingsSuggest,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = stringResource(R.string.settings_title),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "${stringResource(R.string.app_name)} • $themeBadgeName • $activeLocaleTag",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = null)
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(22.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = { currentRoute = SettingsRoute.MAIN },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when (route) {
                            SettingsRoute.APPEARANCE -> stringResource(R.string.settings_appearance)
                            SettingsRoute.DESIGN -> stringResource(R.string.settings_design)
                            SettingsRoute.PLAYBACK -> stringResource(R.string.settings_playback)
                            SettingsRoute.INTEGRATION -> stringResource(R.string.settings_integration)
                            SettingsRoute.DATA -> stringResource(R.string.settings_data)
                            SettingsRoute.ABOUT -> stringResource(R.string.settings_about)
                            else -> ""
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        val q = searchQuery.trim().lowercase()

                        if (q.isEmpty()) {
                            // Bento-сетка быстрого доступа 2x2
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ExpressiveBentoTile(
                                        title = stringResource(R.string.settings_appearance),
                                        subtitle = "$themeBadgeName • $activeLocaleTag",
                                        painter = painterResource(R.drawable.ic_dark_theme),
                                        accentColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                        onClick = { currentRoute = SettingsRoute.APPEARANCE }
                                    )
                                    ExpressiveBentoTile(
                                        title = stringResource(R.string.settings_design),
                                        subtitle = stringResource(R.string.settings_nav_style),
                                        icon = Icons.Default.Palette,
                                        accentColor = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.weight(1f),
                                        onClick = { currentRoute = SettingsRoute.DESIGN }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ExpressiveBentoTile(
                                        title = stringResource(R.string.settings_playback),
                                        subtitle = stringResource(R.string.settings_bento_playback_sub),
                                        icon = Icons.Rounded.GraphicEq,
                                        accentColor = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.weight(1f),
                                        onClick = { currentRoute = SettingsRoute.PLAYBACK }
                                    )
                                    ExpressiveBentoTile(
                                        title = stringResource(R.string.settings_integration),
                                        subtitle = stringResource(R.string.integration_wear_api),
                                        painter = painterResource(R.drawable.ic_watch_api),
                                        accentColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                        onClick = { currentRoute = SettingsRoute.INTEGRATION }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        val appearanceTitle = stringResource(R.string.settings_appearance)
                        val designTitle = stringResource(R.string.settings_design)
                        val playbackTitle = stringResource(R.string.settings_playback)
                        val integrationTitle = stringResource(R.string.settings_integration)
                        val privacyTitle = stringResource(R.string.privacy_secure)
                        val dataTitle = stringResource(R.string.settings_data)
                        val advancedTitle = stringResource(R.string.settings_advanced)
                        val aboutTitle = stringResource(R.string.settings_about)

                        SettingsSectionHeader(title = stringResource(R.string.settings_privacy))
                        SettingsGroup {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = privacyTitle,
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = stringResource(R.string.privacy_secure_desc),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                leadingContent = {
                                    IconContainer(
                                        icon = if (isSecureMode) Icons.Rounded.Lock else Icons.Rounded.Security,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                },
                                trailingContent = {
                                    LiquidGlassSwitch(
                                        checked = isSecureMode,
                                        onCheckedChange = { enabled ->
                                            isSecureMode = enabled
                                            PrefsManager.saveSecureMode(enabled)
                                            onSecureChanged(enabled)
                                        }
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsSectionHeader(title = stringResource(R.string.settings_section_system_and_data))

                        SettingsGroup {
                            if (q.isNotEmpty()) {
                                if (appearanceTitle.lowercase().contains(q)) {
                                    SettingsMenuEntry(
                                        title = appearanceTitle,
                                        subtitle = stringResource(R.string.settings_language),
                                        badge = themeBadgeName,
                                        icon = painterResource(R.drawable.ic_dark_theme),
                                        tint = MaterialTheme.colorScheme.primary
                                    ) { currentRoute = SettingsRoute.APPEARANCE }
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                                if (designTitle.lowercase().contains(q)) {
                                    SettingsMenuEntry(
                                        title = designTitle,
                                        subtitle = stringResource(R.string.settings_nav_style),
                                        icon = Icons.Default.Palette,
                                        tint = MaterialTheme.colorScheme.secondary
                                    ) { currentRoute = SettingsRoute.DESIGN }
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                                if (playbackTitle.lowercase().contains(q)) {
                                    SettingsMenuEntry(
                                        title = playbackTitle,
                                        subtitle = stringResource(R.string.settings_bento_playback_sub),
                                        icon = painterResource(R.drawable.ic_action_key),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    ) { currentRoute = SettingsRoute.PLAYBACK }
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                                if (integrationTitle.lowercase().contains(q)) {
                                    SettingsMenuEntry(
                                        title = integrationTitle,
                                        subtitle = stringResource(R.string.integration_auto_detect),
                                        icon = painterResource(R.drawable.ic_watch_api),
                                        tint = MaterialTheme.colorScheme.primary
                                    ) { currentRoute = SettingsRoute.INTEGRATION }
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }

                            if (q.isEmpty() || dataTitle.lowercase().contains(q)) {
                                SettingsMenuEntry(
                                    title = dataTitle,
                                    subtitle = stringResource(R.string.data_clear_cache_desc),
                                    icon = painterResource(R.drawable.ic_text_delete),
                                    tint = MaterialTheme.colorScheme.error
                                ) { currentRoute = SettingsRoute.DATA }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }

                            if (q.isEmpty() || advancedTitle.lowercase().contains(q)) {
                                // ИСПРАВЛЕНО: вызываем onNavigateToAdvanced напрямую в onClick, а не внутри композиции!
                                SettingsMenuEntry(
                                    title = advancedTitle,
                                    subtitle = stringResource(R.string.adv_module_control_desc),
                                    icon = Icons.Default.SettingsSuggest,
                                    tint = MaterialTheme.colorScheme.error
                                ) { onNavigateToAdvanced() }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }

                            if (q.isEmpty() || aboutTitle.lowercase().contains(q)) {
                                SettingsMenuEntry(
                                    title = aboutTitle,
                                    subtitle = stringResource(R.string.about_developer),
                                    badge = "v1.2",
                                    icon = Icons.Default.Info,
                                    tint = MaterialTheme.colorScheme.secondary
                                ) { currentRoute = SettingsRoute.ABOUT }
                            }
                        }
                    }
                    SettingsRoute.APPEARANCE -> AppearancePanel(currentThemeMode, onThemeChanged)
                    SettingsRoute.DESIGN -> DesignPanel(
                        onNavBarStyleChanged = onNavBarStyleChanged,
                        isSecureMode = isSecureMode,
                        onSecureChanged = { enabled ->
                            isSecureMode = enabled
                            PrefsManager.saveSecureMode(enabled)
                            onSecureChanged(enabled)
                        }
                    )
                    SettingsRoute.PLAYBACK -> ContinuousMixSettingsCard(context = context)
                    SettingsRoute.INTEGRATION -> IntegrationPanel(onFossWearChanged)
                    SettingsRoute.DATA -> DataPanel()
                    SettingsRoute.ABOUT -> AboutPanel(
                        isAutoUpdateEnabled = isAutoUpdateEnabled,
                        onAutoUpdateChanged = {
                            isAutoUpdateEnabled = it
                            PrefsManager.saveAutoUpdateEnabled(it)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(280.dp))
            }
        }
    }
}