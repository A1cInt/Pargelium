package com.alcint.pargelium

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(seedColor: Color) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(PrefsManager.getEnabled()) }

    // AutoEQ
    var autoEqEnabled by remember { mutableStateOf(PrefsManager.getAutoEqEnabled()) }
    var currentProfile by remember { mutableStateOf(PrefsManager.getCurrentAutoEqProfile()) }
    var showAutoEqSheet by remember { mutableStateOf(false) }

    var connectedDeviceName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        while (true) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val btDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
            }
            val newDeviceName = btDevice?.productName?.toString()?.takeIf { it.isNotBlank() }
            if (connectedDeviceName != newDeviceName) {
                connectedDeviceName = newDeviceName
            }
            delay(2500)
        }
    }

    // 10-Band EQ
    var userEqEnabled by remember { mutableStateOf(PrefsManager.getUserEqEnabled()) }
    var userEqGains by remember { mutableStateOf(PrefsManager.getUserEqGains().toList()) }
    val eqLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    // Бас
    var bassEnabled by remember { mutableStateOf(PrefsManager.getBassEnabled()) }
    var bass by remember { mutableFloatStateOf(PrefsManager.getBass().toFloat()) }
    var bassFreq by remember { mutableFloatStateOf(PrefsManager.getBassFreq().toFloat()) }

    // Эффекты
    var roomEnabled by remember { mutableStateOf(PrefsManager.getRoom()) }
    var reverbMode by remember { mutableIntStateOf(PrefsManager.getReverbMode()) }
    var reverbSize by remember { mutableFloatStateOf(PrefsManager.getReverbSize().toFloat()) }
    var reverbDamp by remember { mutableFloatStateOf(PrefsManager.getReverbDamp().toFloat()) }
    var reverbMix by remember { mutableFloatStateOf(PrefsManager.getReverbMix().toFloat()) }
    val reverbModesList = stringArrayResource(id = R.array.reverb_modes).toList()

    var haasEnabled by remember { mutableStateOf(PrefsManager.getHaas()) }
    var haasDelay by remember { mutableFloatStateOf(PrefsManager.getHaasDelay().toFloat()) }

    var spatializer by remember { mutableStateOf(PrefsManager.getSpatializer()) }
    var spatialWidth by remember { mutableFloatStateOf(PrefsManager.getSpatialWidth().toFloat()) }

    var tube by remember { mutableStateOf(PrefsManager.getTube()) }
    var mp3 by remember { mutableStateOf(PrefsManager.getMp3Restorer()) }
    var exciterIntensity by remember { mutableFloatStateOf(PrefsManager.getExciterIntensity().toFloat()) }
    var crossfeed by remember { mutableStateOf(PrefsManager.getCrossfeed()) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(id = R.string.sound_effects_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Switch(checked = isEnabled, onCheckedChange = { isEnabled = it; PrefsManager.saveEnabled(it) })
        }

        LazyColumn(
            modifier = Modifier.weight(1f).alpha(if (isEnabled) 1f else 0.5f),
            userScrollEnabled = isEnabled,
            contentPadding = PaddingValues(bottom = 250.dp)
        ) {

            item {
                Text(stringResource(id = R.string.eq_section_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                val autoEqDesc = buildString {
                    append(currentProfile?.name ?: stringResource(id = R.string.click_to_select_model))
                    if (connectedDeviceName != null) append("\n🎧 ${stringResource(id = R.string.connected_device_fmt, connectedDeviceName!!)}")
                }
                EffectCardWithSettings(title = stringResource(id = R.string.autoeq_title), desc = autoEqDesc, checked = autoEqEnabled, onChecked = { autoEqEnabled = it; PrefsManager.saveAutoEqEnabled(it) }, onClick = { showAutoEqSheet = true }) {}

                EffectCardWithSettings(title = stringResource(id = R.string.ten_band_eq_title), desc = stringResource(id = R.string.ten_band_eq_desc), checked = userEqEnabled, onChecked = { userEqEnabled = it; PrefsManager.saveUserEqEnabled(it) }) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (i in 0..9) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("${if (userEqGains[i] > 0) "+" else ""}${userEqGains[i].toInt()} dB", style = MaterialTheme.typography.labelSmall)
                                Box(modifier = Modifier.height(150.dp).width(40.dp), contentAlignment = Alignment.Center) {
                                    Slider(
                                        value = userEqGains[i],
                                        onValueChange = {
                                            val newGains = userEqGains.toMutableList()
                                            newGains[i] = it
                                            userEqGains = newGains
                                            PrefsManager.saveUserEqGains(newGains.toFloatArray())
                                        },
                                        valueRange = -15f..15f,
                                        modifier = Modifier.requiredWidth(150.dp).graphicsLayer { rotationZ = -90f; transformOrigin = TransformOrigin(0.5f, 0.5f) }
                                    )
                                }
                                Text(eqLabels[i], style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Button(onClick = {
                        val reset = List(10) { 0f }
                        userEqGains = reset
                        PrefsManager.saveUserEqGains(reset.toFloatArray())
                    }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp)) {
                        Text(stringResource(id = R.string.reset_to_zero))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                Text(stringResource(id = R.string.bass_management_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                EffectCardWithSettings(
                    title = stringResource(id = R.string.deep_bass_title),
                    desc = stringResource(id = R.string.deep_bass_desc),
                    checked = bassEnabled,
                    onChecked = { bassEnabled = it; PrefsManager.saveBassEnabled(it) }
                ) {
                    SettingSlider(stringResource(id = R.string.setting_power), bass, unit = "%") { bass = it; PrefsManager.saveBass(it.toInt()) }
                    SettingSlider(stringResource(id = R.string.setting_frequency), bassFreq, 30f..150f, " ${stringResource(id = R.string.unit_hz)}") { bassFreq = it; PrefsManager.saveBassFreq(it.toInt()) }
                }

                Spacer(Modifier.height(24.dp))
                Text(stringResource(id = R.string.space_and_effects_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }

            item {
                EffectCardWithSettings(title = stringResource(id = R.string.reverb_title), desc = stringResource(id = R.string.reverb_desc), checked = roomEnabled, onChecked = { roomEnabled = it; PrefsManager.saveRoom(it) }) {

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        reverbModesList.forEachIndexed { index, name ->
                            FilterChip(
                                selected = reverbMode == index,
                                onClick = {
                                    reverbMode = index
                                    PrefsManager.saveReverbMode(index)
                                    if (index != 0) {
                                        when (index) {
                                            1 -> { reverbSize = 25f; reverbDamp = 80f; reverbMix = 25f }
                                            2 -> { reverbSize = 50f; reverbDamp = 50f; reverbMix = 35f }
                                            3 -> { reverbSize = 80f; reverbDamp = 30f; reverbMix = 50f }
                                            4 -> { reverbSize = 95f; reverbDamp = 10f; reverbMix = 70f }
                                            5 -> { reverbSize = 100f; reverbDamp = 0f; reverbMix = 85f }
                                        }
                                        PrefsManager.saveReverbSize(reverbSize.toInt())
                                        PrefsManager.saveReverbDamp(reverbDamp.toInt())
                                        PrefsManager.saveReverbMix(reverbMix.toInt())
                                    }
                                },
                                label = { Text(name) }
                            )
                        }
                    }

                    SettingSlider(stringResource(id = R.string.setting_room_size), reverbSize, unit = "%") {
                        reverbSize = it; PrefsManager.saveReverbSize(it.toInt())
                        reverbMode = 0; PrefsManager.saveReverbMode(0)
                    }
                    SettingSlider(stringResource(id = R.string.setting_wall_damp), reverbDamp, unit = "%") {
                        reverbDamp = it; PrefsManager.saveReverbDamp(it.toInt())
                        reverbMode = 0; PrefsManager.saveReverbMode(0)
                    }
                    SettingSlider(stringResource(id = R.string.setting_mix_level), reverbMix, unit = "%") {
                        reverbMix = it; PrefsManager.saveReverbMix(it.toInt())
                        reverbMode = 0; PrefsManager.saveReverbMode(0)
                    }
                }

                EffectCardWithSettings(title = stringResource(id = R.string.spatializer_title), desc = stringResource(id = R.string.spatializer_desc), checked = spatializer, onChecked = { spatializer = it; PrefsManager.saveSpatializer(it) }) {
                    SettingSlider(stringResource(id = R.string.setting_stereo_width), spatialWidth, unit = "%") { spatialWidth = it; PrefsManager.saveSpatialWidth(it.toInt()) }
                }

                EffectCardWithSettings(title = stringResource(id = R.string.haas_title), desc = stringResource(id = R.string.haas_desc), checked = haasEnabled, onChecked = { haasEnabled = it; PrefsManager.saveHaas(it) }) {
                    SettingSlider(stringResource(id = R.string.setting_right_delay), haasDelay, 1f..20f, " ${stringResource(id = R.string.unit_ms)}") { haasDelay = it; PrefsManager.saveHaasDelay(it.toInt()) }
                }

                EffectCardWithSettings(title = stringResource(id = R.string.exciter_title), desc = stringResource(id = R.string.exciter_desc), checked = mp3, onChecked = { mp3 = it; PrefsManager.saveMp3Restorer(it) }) {
                    SettingSlider(stringResource(id = R.string.setting_intensity), exciterIntensity, unit = "%") { exciterIntensity = it; PrefsManager.saveExciterIntensity(it.toInt()) }
                }

                EffectCardWithSettings(stringResource(id = R.string.tube_amp_title), stringResource(id = R.string.tube_amp_desc), tube, { tube = it; PrefsManager.saveTube(it) }) {}
                EffectCardWithSettings(stringResource(id = R.string.crossfeed_title), stringResource(id = R.string.crossfeed_desc), crossfeed, { crossfeed = it; PrefsManager.saveCrossfeed(it) }) {}
            }
        }
    }

    if (showAutoEqSheet) {
        AutoEqSelectionSheet(currentProfileName = currentProfile?.name, onDismiss = { showAutoEqSheet = false }, onProfileSelected = {
            currentProfile = PrefsManager.getCurrentAutoEqProfile()
            autoEqEnabled = true
            showAutoEqSheet = false
        })
    }
}

@Composable
fun SettingSlider(name: String, value: Float, range: ClosedFloatingPointRange<Float> = 0f..100f, unit: String = "%", onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text("${value.toInt()}$unit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
fun EffectCardWithSettings(title: String, desc: String, checked: Boolean, onChecked: (Boolean) -> Unit, onClick: (() -> Unit)? = null, settingsContent: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { if (onClick != null) onClick() else onChecked(!checked) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                Switch(checked = checked, onCheckedChange = onChecked)
            }
            AnimatedVisibility(visible = checked, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    settingsContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoEqSelectionSheet(currentProfileName: String?, onDismiss: () -> Unit, onProfileSelected: () -> Unit) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var isListLoading by remember { mutableStateOf(true) }
    var isPresetDownloading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var databaseList by remember { mutableStateOf<List<AutoEqProfile>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            isListLoading = true
            errorText = null
            databaseList = withContext(Dispatchers.IO) { AutoEqApi.getDatabaseIndex() }
        } catch (e: Exception) { errorText = e.message } finally { isListLoading = false }
    }

    val filtered = databaseList.filter { it.name.contains(searchQuery, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxHeight(0.85f).padding(bottom = 16.dp)) {
            if (isPresetDownloading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(id = R.string.select_headphone_model), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text(stringResource(id = R.string.search_hint)) }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(24.dp), singleLine = true, enabled = !isListLoading && !isPresetDownloading
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isListLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else if (errorText != null) Text(errorText!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp))
                else if (filtered.isEmpty()) Text(stringResource(id = R.string.search_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
                else {
                    LazyColumn {
                        items(filtered) { profile ->
                            val isSelected = profile.name == currentProfileName
                            ListItem(
                                headlineContent = { Text(profile.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                leadingContent = { Icon(Icons.Default.Headphones, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                                trailingContent = { if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable(enabled = !isPresetDownloading) {
                                    scope.launch {
                                        try {
                                            isPresetDownloading = true
                                            errorText = null
                                            val fullProfileWithGains = withContext(Dispatchers.IO) {
                                                val downloadedGains = AutoEqApi.downloadPresetGains(profile)
                                                profile.copy(gains = downloadedGains)
                                            }
                                            PrefsManager.saveCurrentAutoEqProfile(fullProfileWithGains)
                                            PrefsManager.saveEqGains(fullProfileWithGains.gains)
                                            PrefsManager.saveAutoEqEnabled(true)
                                            onProfileSelected()
                                        } catch (e: Exception) { errorText = e.message } finally { isPresetDownloading = false }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}