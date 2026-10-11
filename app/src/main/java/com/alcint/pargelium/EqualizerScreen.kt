package com.alcint.pargelium

import android.content.Context
import android.media.AudioDeviceCallback
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
import com.alcint.pargelium.audio.JamesDspController
import com.alcint.pargelium.liquid.LiquidGlassSwitch
import kotlinx.coroutines.Dispatchers
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
    var autoEqDatabaseList by remember { mutableStateOf<List<AutoEqProfile>>(emptyList()) }
    var isAutoEqListLoading by remember { mutableStateOf(true) }
    var autoEqErrorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            isAutoEqListLoading = true
            autoEqErrorText = null
            autoEqDatabaseList = withContext(Dispatchers.IO) { AutoEqApi.getDatabaseIndex() }
        } catch (e: Exception) {
            autoEqErrorText = e.message
        } finally {
            isAutoEqListLoading = false
        }
    }

    var connectedDeviceName by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val updateDevice = {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val btDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
            }
            connectedDeviceName = btDevice?.productName?.toString()?.takeIf { it.isNotBlank() }
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = updateDevice()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = updateDevice()
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        updateDevice()
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    var userEqEnabled by remember { mutableStateOf(PrefsManager.getUserEqEnabled()) }
    val userEqGains = remember { mutableStateListOf(*PrefsManager.getUserEqGains().toTypedArray()) }
    val eqLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    // Dynamic Bass
    var bassEnabled by remember { mutableStateOf(PrefsManager.getBassEnabled()) }
    var bass by remember { mutableFloatStateOf(PrefsManager.getBass().toFloat()) }

    // Reverb
    var roomEnabled by remember { mutableStateOf(PrefsManager.getRoom()) }
    var reverbMode by remember { mutableIntStateOf(PrefsManager.getReverbMode()) }
    val reverbModesList = stringArrayResource(id = R.array.reverb_modes).toList()

    // Vacuum Tube (1206 / 150)
    var tubeEnabled by remember { mutableStateOf(PrefsManager.getTubeEnabled()) }
    var tubeDrive by remember { mutableFloatStateOf(PrefsManager.getTubeDrive()) }

    // Stereo Widener (1204 / 137)
    var stereoWideEnabled by remember { mutableStateOf(PrefsManager.getStereoWideEnabled()) }
    var stereoWidth by remember { mutableFloatStateOf(PrefsManager.getStereoWidth()) }

    // BS2B Crossfeed (1208 / 188)
    var bs2bEnabled by remember { mutableStateOf(PrefsManager.getBs2bEnabled()) }
    var bs2bMode by remember { mutableIntStateOf(PrefsManager.getBs2bMode()) }
    val bs2bModesList = stringArrayResource(id = R.array.bs2b_modes).toList()

    // Master Limiter (1500)
    var limThreshold by remember { mutableFloatStateOf(PrefsManager.getLimThreshold()) }
    var limRelease by remember { mutableFloatStateOf(PrefsManager.getLimRelease()) }
    var postGain by remember { mutableFloatStateOf(PrefsManager.getPostGain()) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(id = R.string.sound_effects_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            LiquidGlassSwitch(
                checked = isEnabled,
                onCheckedChange = {
                    isEnabled = it
                    PrefsManager.saveEnabled(it)
                    JamesDspController.setMasterEnabled(it)
                }
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).alpha(if (isEnabled) 1f else 0.5f),
            userScrollEnabled = isEnabled,
            contentPadding = PaddingValues(bottom = 250.dp)
        ) {

            item {
                Text(
                    stringResource(id = R.string.eq_section_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val autoEqDesc = buildString {
                    append(currentProfile?.name ?: stringResource(id = R.string.click_to_select_model))
                    if (connectedDeviceName != null) append("\n\uD83C\uDFA7 ${stringResource(id = R.string.connected_device_fmt, connectedDeviceName!!)}")
                }
                EffectCardWithSettings(
                    title = stringResource(id = R.string.autoeq_title),
                    desc = autoEqDesc,
                    checked = autoEqEnabled,
                    onChecked = {
                        autoEqEnabled = it
                        PrefsManager.saveAutoEqEnabled(it)
                        if (currentProfile != null && it) {
                            JamesDspController.applyAutoEq(currentProfile!!.gains)
                        } else {
                            JamesDspController.setAutoEqEnabled(it)
                        }
                    },
                    onClick = { showAutoEqSheet = true }
                ) {}

                EffectCardWithSettings(
                    title = stringResource(id = R.string.ten_band_eq_title),
                    desc = stringResource(id = R.string.ten_band_eq_desc),
                    checked = userEqEnabled,
                    onChecked = {
                        userEqEnabled = it
                        PrefsManager.saveUserEqEnabled(it)
                        JamesDspController.setEqualizerEnabled(it)
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (i in 0..9) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    "${if (userEqGains[i] > 0) "+" else ""}${userEqGains[i].toInt()} ${stringResource(id = R.string.unit_db)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Box(
                                    modifier = Modifier.height(150.dp).width(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Slider(
                                        value = userEqGains[i],
                                        onValueChange = { userEqGains[i] = it },
                                        onValueChangeFinished = {
                                            val array = userEqGains.toFloatArray()
                                            PrefsManager.saveUserEqGains(array)
                                            JamesDspController.setEqualizerGains(array)
                                        },
                                        valueRange = -15f..15f,
                                        modifier = Modifier.requiredWidth(150.dp).graphicsLayer {
                                            rotationZ = -90f
                                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                                        }
                                    )
                                }
                                Text(eqLabels[i], style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Button(
                        onClick = {
                            for (i in 0..9) userEqGains[i] = 0f
                            val zeroArray = FloatArray(10) { 0f }
                            PrefsManager.saveUserEqGains(zeroArray)
                            JamesDspController.setEqualizerGains(zeroArray)
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp)
                    ) {
                        Text(stringResource(id = R.string.reset_to_zero))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                Text(
                    stringResource(id = R.string.bass_management_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                EffectCardWithSettings(
                    title = stringResource(id = R.string.dynamic_bass_title),
                    desc = stringResource(id = R.string.dynamic_bass_desc),
                    checked = bassEnabled,
                    onChecked = {
                        bassEnabled = it
                        PrefsManager.saveBassEnabled(it)
                        JamesDspController.setBassEnabled(it)
                    }
                ) {
                    SettingSlider(
                        name = stringResource(id = R.string.setting_power),
                        value = bass,
                        range = 0f..15f,
                        unit = " ${stringResource(id = R.string.unit_db)}",
                        onValueChange = { bass = it },
                        onValueChangeFinished = {
                            PrefsManager.saveBass(bass.toInt())
                            JamesDspController.setBassGain(bass.toInt().toShort())
                        }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                Text(
                    stringResource(id = R.string.space_and_effects_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Stereo Reverb
                EffectCardWithSettings(
                    title = stringResource(id = R.string.spatial_reverb_title),
                    desc = stringResource(id = R.string.spatial_reverb_desc),
                    checked = roomEnabled,
                    onChecked = {
                        roomEnabled = it
                        PrefsManager.saveRoom(it)
                        JamesDspController.setReverbEnabled(it)
                    }
                ) {
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
                                    JamesDspController.setReverbPreset(index.toShort())
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                }

                // Stereo Widener
                EffectCardWithSettings(
                    title = stringResource(id = R.string.stereo_widener_title),
                    desc = stringResource(id = R.string.stereo_widener_desc),
                    checked = stereoWideEnabled,
                    onChecked = {
                        stereoWideEnabled = it
                        PrefsManager.saveStereoWideEnabled(it)
                        JamesDspController.setStereoWideEnabled(it)
                    }
                ) {
                    SettingSlider(
                        name = stringResource(id = R.string.setting_stereo_width),
                        value = stereoWidth,
                        range = 0f..100f,
                        unit = "%",
                        onValueChange = { stereoWidth = it },
                        onValueChangeFinished = {
                            PrefsManager.saveStereoWidth(stereoWidth)
                            JamesDspController.setStereoWidth(stereoWidth.toInt().toShort())
                        }
                    )
                }

                // BS2B Crossfeed
                EffectCardWithSettings(
                    title = stringResource(id = R.string.bs2b_title),
                    desc = stringResource(id = R.string.bs2b_desc),
                    checked = bs2bEnabled,
                    onChecked = {
                        bs2bEnabled = it
                        PrefsManager.saveBs2bEnabled(it)
                        JamesDspController.setBs2bEnabled(it)
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bs2bModesList.forEachIndexed { index, name ->
                            FilterChip(
                                selected = bs2bMode == index,
                                onClick = {
                                    bs2bMode = index
                                    PrefsManager.saveBs2bMode(index)
                                    JamesDspController.setBs2bMode(index.toShort())
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                Text(
                    stringResource(id = R.string.section_analog_color),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                EffectCardWithSettings(
                    title = stringResource(id = R.string.tube_title),
                    desc = stringResource(id = R.string.tube_desc),
                    checked = tubeEnabled,
                    onChecked = {
                        tubeEnabled = it
                        PrefsManager.saveTubeEnabled(it)
                        JamesDspController.setTubeEnabled(it)
                    }
                ) {
                    SettingSlider(
                        name = stringResource(id = R.string.setting_tube_drive),
                        value = tubeDrive,
                        range = 0f..10f,
                        unit = "",
                        onValueChange = { tubeDrive = it },
                        onValueChangeFinished = {
                            PrefsManager.saveTubeDrive(tubeDrive)
                            JamesDspController.setTubeDrive(tubeDrive)
                        }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                Text(
                    stringResource(id = R.string.section_master_dynamics),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(id = R.string.limiter_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(id = R.string.limiter_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        SettingSlider(
                            name = stringResource(id = R.string.setting_limiter_threshold),
                            value = limThreshold,
                            range = -12f..0f,
                            unit = " ${stringResource(id = R.string.unit_db)}",
                            onValueChange = { limThreshold = it },
                            onValueChangeFinished = {
                                PrefsManager.saveLimThreshold(limThreshold)
                                JamesDspController.setLimiterParams(limThreshold, limRelease, postGain)
                            }
                        )

                        SettingSlider(
                            name = stringResource(id = R.string.setting_limiter_release),
                            value = limRelease,
                            range = 20f..300f,
                            unit = " ${stringResource(id = R.string.unit_ms)}",
                            onValueChange = { limRelease = it },
                            onValueChangeFinished = {
                                PrefsManager.saveLimRelease(limRelease)
                                JamesDspController.setLimiterParams(limThreshold, limRelease, postGain)
                            }
                        )

                        SettingSlider(
                            name = stringResource(id = R.string.setting_postgain),
                            value = postGain,
                            range = -12f..12f,
                            unit = " ${stringResource(id = R.string.unit_db)}",
                            onValueChange = { postGain = it },
                            onValueChangeFinished = {
                                PrefsManager.savePostGain(postGain)
                                JamesDspController.setLimiterParams(limThreshold, limRelease, postGain)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAutoEqSheet) {
        AutoEqSelectionSheet(
            currentProfileName = currentProfile?.name,
            databaseList = autoEqDatabaseList,
            isListLoading = isAutoEqListLoading,
            errorText = autoEqErrorText,
            onDismiss = { showAutoEqSheet = false },
            onProfileSelected = {
                currentProfile = PrefsManager.getCurrentAutoEqProfile()
                autoEqEnabled = true
                showAutoEqSheet = false
            }
        )
    }
}

@Composable
fun SettingSlider(
    name: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    unit: String = "%",
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text("${value.toInt()}$unit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished, valueRange = range)
    }
}

@Composable
fun EffectCardWithSettings(
    title: String,
    desc: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
    settingsContent: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { if (onClick != null) onClick() else onChecked(!checked) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                LiquidGlassSwitch(
                    checked = checked,
                    onCheckedChange = onChecked
                )
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
fun AutoEqSelectionSheet(
    currentProfileName: String?,
    databaseList: List<AutoEqProfile>,
    isListLoading: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onProfileSelected: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var isPresetDownloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val filtered = databaseList.filter { it.name.contains(searchQuery, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxHeight(0.85f).padding(bottom = 16.dp)) {
            if (isPresetDownloading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(id = R.string.select_headphone_model),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(id = R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                enabled = !isListLoading && !isPresetDownloading
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isListLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (errorText != null || downloadError != null) {
                    Text(
                        errorText ?: downloadError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                } else if (filtered.isEmpty()) {
                    Text(
                        stringResource(id = R.string.search_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn {
                        items(filtered) { profile ->
                            val isSelected = profile.name == currentProfileName
                            ListItem(
                                headlineContent = {
                                    Text(
                                        profile.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Headphones,
                                        null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.clickable(enabled = !isPresetDownloading) {
                                    scope.launch {
                                        try {
                                            isPresetDownloading = true
                                            downloadError = null
                                            val fullProfileWithGains = withContext(Dispatchers.IO) {
                                                val downloadedGains = AutoEqApi.downloadPresetGains(profile)
                                                profile.copy(gains = downloadedGains)
                                            }
                                            PrefsManager.saveCurrentAutoEqProfile(fullProfileWithGains)
                                            PrefsManager.saveEqGains(fullProfileWithGains.gains)
                                            PrefsManager.saveAutoEqEnabled(true)
                                            JamesDspController.applyAutoEq(fullProfileWithGains.gains)
                                            onProfileSelected()
                                        } catch (e: Exception) {
                                            downloadError = e.message
                                        } finally {
                                            isPresetDownloading = false
                                        }
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