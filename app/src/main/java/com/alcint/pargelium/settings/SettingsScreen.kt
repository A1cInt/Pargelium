@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alcint.pargelium.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alcint.pargelium.ContinuousMixSettingsCard
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.R

enum class SettingsRoute {
    MAIN, APPEARANCE, DESIGN, PLAYBACK, INTEGRATION, DATA, ADVANCED, ABOUT
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
    var currentRoute by remember { mutableStateOf(SettingsRoute.MAIN) }
    var isAutoUpdateEnabled by remember { mutableStateOf(PrefsManager.getAutoUpdateEnabled()) }

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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentRoute = SettingsRoute.MAIN }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (route) {
                            SettingsRoute.APPEARANCE -> stringResource(R.string.settings_appearance)
                            SettingsRoute.DESIGN -> stringResource(R.string.settings_design)
                            SettingsRoute.PLAYBACK -> stringResource(R.string.settings_playback)
                            SettingsRoute.INTEGRATION -> stringResource(R.string.settings_integration)
                            SettingsRoute.DATA -> stringResource(R.string.settings_data)
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
                            SettingsMenuEntry(title = stringResource(R.string.settings_design), icon = Icons.Default.Palette, tint = MaterialTheme.colorScheme.secondary) { currentRoute = SettingsRoute.DESIGN }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsMenuEntry(title = stringResource(R.string.settings_playback), icon = painterResource(R.drawable.ic_action_key), tint = MaterialTheme.colorScheme.tertiary) { currentRoute = SettingsRoute.PLAYBACK }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsMenuEntry(title = stringResource(R.string.settings_integration), icon = painterResource(R.drawable.ic_watch_api), tint = MaterialTheme.colorScheme.primary) { currentRoute = SettingsRoute.INTEGRATION }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsMenuEntry(title = stringResource(R.string.settings_data), icon = painterResource(R.drawable.ic_text_delete), tint = MaterialTheme.colorScheme.error) { currentRoute = SettingsRoute.DATA }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsMenuEntry(title = stringResource(R.string.settings_advanced), icon = Icons.Default.SettingsSuggest, tint = MaterialTheme.colorScheme.error) { currentRoute = SettingsRoute.ADVANCED }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsMenuEntry(title = stringResource(R.string.settings_about), icon = Icons.Default.Info, tint = MaterialTheme.colorScheme.secondary) { currentRoute = SettingsRoute.ABOUT }
                        }
                    }
                    SettingsRoute.APPEARANCE -> AppearancePanel(currentThemeMode, onThemeChanged)
                    SettingsRoute.DESIGN -> DesignPanel()
                    SettingsRoute.PLAYBACK -> ContinuousMixSettingsCard(context = context)
                    SettingsRoute.INTEGRATION -> IntegrationPanel(onFossWearChanged)
                    SettingsRoute.DATA -> DataPanel()
                    SettingsRoute.ADVANCED -> onNavigateToAdvanced() // Or an AdvancedPanel if it existed
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