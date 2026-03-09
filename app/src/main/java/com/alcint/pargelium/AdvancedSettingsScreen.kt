package com.alcint.pargelium

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(onBackClick: () -> Unit) {

    var visualsEnabled by remember { mutableStateOf(PrefsManager.getFeatureVisuals()) }
    var streamingEnabled by remember { mutableStateOf(PrefsManager.getFeatureStreaming()) }
    var eqEnabled by remember { mutableStateOf(PrefsManager.getFeatureEqualizer()) }

    var playlistsFeatureEnabled by remember { mutableStateOf(PrefsManager.getFeaturePlaylists()) }
    var playlistDynamicColors by remember { mutableStateOf(PrefsManager.getPlaylistDynamicColors()) }
    var playlistBanners by remember { mutableStateOf(PrefsManager.getPlaylistBanners()) }
    var playlistCovers by remember { mutableStateOf(PrefsManager.getPlaylistTrackCovers()) }
    var playlistAnimations by remember { mutableStateOf(PrefsManager.getPlaylistAnimations()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Управление модулями", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            item {
                Text(
                    text = "Осторожно: Отключение модулей полностью уберет их из интерфейса и фоновой работы.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                Text(
                    text = "ОСНОВНЫЕ СИСТЕМЫ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )
            }

            item { AdvancedToggleItem("Визуальные эффекты", "Размытие обложек и тяжелые тени.", visualsEnabled) { visualsEnabled = it; PrefsManager.saveFeatureVisuals(it) } }
            item { AdvancedToggleItem("Сетевые функции", "Доступ к стримингу и сети.", streamingEnabled) { streamingEnabled = it; PrefsManager.saveFeatureStreaming(it) } }
            item { AdvancedToggleItem("Эквалайзер и DSP", "Подсистема аудиоэффектов.", eqEnabled) { eqEnabled = it; PrefsManager.saveFeatureEqualizer(it) } }

            // --- БЛОК ПЛЕЙЛИСТОВ ---
            item {
                Text(
                    text = "МОДУЛЬ ПЛЕЙЛИСТОВ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )
            }

            item {
                AdvancedToggleItem(
                    title = "Включить модуль",
                    description = "Отображение раздела плейлистов в нижней панели навигации.",
                    checked = playlistsFeatureEnabled,
                    onCheckedChange = { playlistsFeatureEnabled = it; PrefsManager.saveFeaturePlaylists(it) }
                )
            }

            if (playlistsFeatureEnabled) {
                item {
                    AdvancedToggleItem(
                        title = "Динамические цвета",
                        description = "Цветовая схема на основе обложки плейлиста.",
                        checked = playlistDynamicColors,
                        onCheckedChange = { playlistDynamicColors = it; PrefsManager.savePlaylistDynamicColors(it) }
                    )
                }
                item {
                    AdvancedToggleItem(
                        title = "Баннеры плейлистов",
                        description = "Размытые фоновые обложки.",
                        checked = playlistBanners,
                        onCheckedChange = { playlistBanners = it; PrefsManager.savePlaylistBanners(it) }
                    )
                }
                item {
                    AdvancedToggleItem(
                        title = "Обложки треков",
                        description = "Загрузка миниатюр для каждого трека в списке.",
                        checked = playlistCovers,
                        onCheckedChange = { playlistCovers = it; PrefsManager.savePlaylistTrackCovers(it) }
                    )
                }
                item {
                    AdvancedToggleItem(
                        title = "Анимации переходов",
                        description = "Плавная анимация при открытии плейлиста.",
                        checked = playlistAnimations,
                        onCheckedChange = { playlistAnimations = it; PrefsManager.savePlaylistAnimations(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun AdvancedToggleItem(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, onValueChange = onCheckedChange),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}