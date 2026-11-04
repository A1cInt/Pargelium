@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.alcint.pargelium

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SpatialAudioOff
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcint.pargelium.audio.JamesDspController
import com.alcint.pargelium.liquid.LiquidGlassSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

@Composable
fun EqualizerScreen(seedColor: Color) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val accentColor = if (seedColor != Color.Unspecified && seedColor != Color.Transparent) {
        seedColor
    } else {
        MaterialTheme.colorScheme.primary
    }

    var isEnabled by remember { mutableStateOf(PrefsManager.getEnabled()) }
    var autoHeadroomEnabled by remember { mutableStateOf(JamesDspController.isAutoHeadroomEnabled) }

    // AutoEQ
    var autoEqEnabled by remember { mutableStateOf(PrefsManager.getAutoEqEnabled()) }
    var currentProfile by remember { mutableStateOf(PrefsManager.getCurrentAutoEqProfile()) }
    var showAutoEqSheet by remember { mutableStateOf(false) }
    var autoEqDatabaseList by remember { mutableStateOf<List<AutoEqProfile>>(emptyList()) }
    var isAutoEqListLoading by remember { mutableStateOf(true) }
    var autoEqErrorText by remember { mutableStateOf<String?>(null) }

    var userEqEnabled by remember { mutableStateOf(PrefsManager.getUserEqEnabled()) }
    val userEqGains = remember {
        val saved = PrefsManager.getUserEqGains()
        val padded = FloatArray(10) { idx -> saved.getOrElse(idx) { 0f } }
        mutableStateListOf(*padded.toTypedArray())
    }
    val eqLabels = remember { listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k") }
    var activePresetName by remember { mutableStateOf<String?>(null) }

    // Dynamic Bass
    var bassEnabled by remember { mutableStateOf(PrefsManager.getBassEnabled()) }
    var bass by remember { mutableFloatStateOf(PrefsManager.getBass().toFloat()) }

    // Reverb
    var roomEnabled by remember { mutableStateOf(PrefsManager.getRoom()) }
    var reverbMode by remember { mutableIntStateOf(PrefsManager.getReverbMode()) }
    val reverbModesList = stringArrayResource(id = R.array.reverb_modes).toList()

    // Vacuum Tube
    var tubeEnabled by remember { mutableStateOf(PrefsManager.getTubeEnabled()) }
    var tubeDrive by remember { mutableFloatStateOf(PrefsManager.getTubeDrive()) }

    // Stereo Widener
    var stereoWideEnabled by remember { mutableStateOf(PrefsManager.getStereoWideEnabled()) }
    var stereoWidth by remember { mutableFloatStateOf(PrefsManager.getStereoWidth()) }

    // BS2B Crossfeed
    var bs2bEnabled by remember { mutableStateOf(PrefsManager.getBs2bEnabled()) }
    var bs2bMode by remember { mutableIntStateOf(PrefsManager.getBs2bMode()) }
    val bs2bModesList = stringArrayResource(id = R.array.bs2b_modes).toList()

    // Master Limiter
    var limThreshold by remember { mutableFloatStateOf(PrefsManager.getLimThreshold()) }
    var limRelease by remember { mutableFloatStateOf(PrefsManager.getLimRelease()) }
    var postGain by remember { mutableFloatStateOf(PrefsManager.getPostGain()) }

    LaunchedEffect(Unit) {
        JamesDspController.syncFromPrefs()
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
                        it.type == AudioDeviceInfo.TYPE_BLE_BROADCAST ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
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

    val activeModulesCount = remember(
        autoEqEnabled, userEqEnabled, bassEnabled, roomEnabled, stereoWideEnabled, bs2bEnabled, tubeEnabled
    ) {
        listOf(autoEqEnabled, userEqEnabled, bassEnabled, roomEnabled, stereoWideEnabled, bs2bEnabled, tubeEnabled).count { it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.sound_effects_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isEnabled) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (isEnabled) "JamesDSP • $activeModulesCount ON" else "BYPASS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                if (connectedDeviceName != null) {
                    Text(
                        text = "🎧 $connectedDeviceName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LiquidGlassSwitch(
                checked = isEnabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isEnabled = it
                    PrefsManager.saveEnabled(it)
                    JamesDspController.setMasterEnabled(it)
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .alpha(if (isEnabled) 1f else 0.48f),
            userScrollEnabled = isEnabled,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 250.dp)
        ) {
            item(key = "dsp_signal_telemetry") {
                DspSignalTelemetryDeck(
                    isEnabled = isEnabled,
                    autoHeadroomEnabled = autoHeadroomEnabled,
                    accentColor = accentColor,
                    onToggleAutoHeadroom = { enabled ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        autoHeadroomEnabled = enabled
                        JamesDspController.setAutoHeadroom(enabled)
                    }
                )
            }

            item(key = "dsp_eq_section") {
                SectionHeaderTitle(
                    icon = Icons.Rounded.Equalizer,
                    title = stringResource(id = R.string.eq_section_title),
                    accentColor = accentColor
                )

                val autoEqDesc = buildString {
                    append(currentProfile?.name ?: stringResource(id = R.string.click_to_select_model))
                    if (connectedDeviceName != null) {
                        append("\n🎧 ${stringResource(id = R.string.connected_device_fmt, connectedDeviceName!!)}")
                    }
                }

                EffectCardWithSettings(
                    title = stringResource(id = R.string.autoeq_title),
                    desc = autoEqDesc,
                    badge = if (autoEqEnabled && currentProfile != null) "FIR/IIR" else null,
                    checked = autoEqEnabled,
                    accentColor = accentColor,
                    onChecked = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        autoEqEnabled = it
                        PrefsManager.saveAutoEqEnabled(it)
                        if (currentProfile != null && it) {
                            JamesDspController.applyAutoEq(currentProfile!!.gains)
                        } else {
                            JamesDspController.setAutoEqEnabled(it)
                        }
                    },
                    onClick = { showAutoEqSheet = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { showAutoEqSheet = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = currentProfile?.name ?: stringResource(id = R.string.select_headphone_model),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                EffectCardWithSettings(
                    title = stringResource(id = R.string.ten_band_eq_title),
                    desc = stringResource(id = R.string.ten_band_eq_desc),
                    badge = activePresetName,
                    checked = userEqEnabled,
                    accentColor = accentColor,
                    onChecked = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        userEqEnabled = it
                        PrefsManager.saveUserEqEnabled(it)
                        JamesDspController.setEqualizerEnabled(it)
                    }
                ) {
                    EqFrequencyResponseGraph(
                        userGains = userEqGains,
                        autoEqGains = if (autoEqEnabled) currentProfile?.gains else null,
                        accentColor = accentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(PresetManagerList.presets, key = { it.name }) { preset ->
                            val isSelected = activePresetName == preset.name
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    activePresetName = preset.name
                                    for (i in 0..9) {
                                        userEqGains[i] = preset.gains10.getOrElse(i) { 0f }
                                    }
                                    val arr = userEqGains.toFloatArray()
                                    PrefsManager.saveUserEqGains(arr)
                                    JamesDspController.setEqualizerGains(arr)
                                },
                                label = { Text(preset.name, fontWeight = FontWeight.SemiBold) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (i in 0..9) {
                            val gainVal = userEqGains[i]
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%+.1f", gainVal),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (abs(gainVal) > 0.2f) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .height(156.dp)
                                        .width(38.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Slider(
                                        value = gainVal,
                                        onValueChange = { newValue ->
                                            activePresetName = null
                                            userEqGains[i] = newValue
                                            JamesDspController.setEqualizerGains(userEqGains.toFloatArray())
                                        },
                                        onValueChangeFinished = {
                                            val array = userEqGains.toFloatArray()
                                            PrefsManager.saveUserEqGains(array)
                                            JamesDspController.setEqualizerGains(array)
                                        },
                                        valueRange = -15f..15f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentColor,
                                            activeTrackColor = accentColor
                                        ),
                                        modifier = Modifier
                                            .requiredWidth(156.dp)
                                            .graphicsLayer {
                                                rotationZ = -90f
                                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                                            }
                                    )
                                }
                                Text(
                                    text = eqLabels[i],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            activePresetName = "Flat"
                            for (i in 0..9) userEqGains[i] = 0f
                            val zeroArray = FloatArray(10) { 0f }
                            PrefsManager.saveUserEqGains(zeroArray)
                            JamesDspController.setEqualizerGains(zeroArray)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(id = R.string.reset_to_zero), fontWeight = FontWeight.Bold)
                    }
                }
            }

            item(key = "dsp_bass_section") {
                SectionHeaderTitle(
                    icon = Icons.Rounded.Speaker,
                    title = stringResource(id = R.string.bass_management_title),
                    accentColor = accentColor
                )

                EffectCardWithSettings(
                    title = stringResource(id = R.string.dynamic_bass_title),
                    desc = stringResource(id = R.string.dynamic_bass_desc),
                    badge = if (bassEnabled) "+${bass.toInt()} dB" else null,
                    checked = bassEnabled,
                    accentColor = accentColor,
                    onChecked = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        accentColor = accentColor,
                        onValueChange = {
                            bass = it
                            JamesDspController.setBassGainFloat(it)
                        },
                        onValueChangeFinished = {
                            PrefsManager.saveBass(bass.toInt())
                            JamesDspController.setBassGain(bass.toInt().toShort())
                        }
                    )
                }
            }

            item(key = "dsp_space_section") {
                SectionHeaderTitle(
                    icon = Icons.Rounded.SpatialAudioOff,
                    title = stringResource(id = R.string.space_and_effects_title),
                    accentColor = accentColor
                )

                EffectCardWithSettings(
                    title = stringResource(id = R.string.spatial_reverb_title),
                    desc = stringResource(id = R.string.spatial_reverb_desc),
                    badge = if (roomEnabled) reverbModesList.getOrNull(reverbMode) else null,
                    checked = roomEnabled,
                    accentColor = accentColor,
                    onChecked = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        roomEnabled = it
                        PrefsManager.saveRoom(it)
                        JamesDspController.setReverbEnabled(it)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        reverbModesList.forEachIndexed { index, name ->
                            FilterChip(
                                selected = reverbMode == index,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    reverbMode = index
                                    PrefsManager.saveReverbMode(index)
                                    JamesDspController.setReverbPreset(index.toShort())
                                },
                                label = { Text(name) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                EffectCardWithSettings(
                    title = stringResource(id = R.string.stereo_widener_title),
                    desc = stringResource(id = R.string.stereo_widener_desc),
                    badge = if (stereoWideEnabled) "${stereoWidth.toInt()}%" else null,
                    checked = stereoWideEnabled,
                    accentColor = accentColor,
                    onChecked = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        accentColor = accentColor,
                        onValueChange = {
                            stereoWidth = it
                            JamesDspController.setStereoWidth(it.toInt().toShort())
                        },
                        onValueChangeFinished = {
                            PrefsManager.saveStereoWidth(stereoWidth)
                            JamesDspController.setStereoWidth(stereoWidth.toInt().toShort())
                        }
                    )
                }

                EffectCardWithSettings(
                    title = stringResource(id = R.string.bs2b_title),
                    desc = stringResource(id = R.string.bs2b_desc),
                    badge = if (bs2bEnabled) bs2bModesList.getOrNull(bs2bMode) else null,
                    checked = bs2bEnabled,
                    accentColor = accentColor,
                    onChecked = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        bs2bEnabled = it
                        PrefsManager.saveBs2bEnabled(it)
                        JamesDspController.setBs2bEnabled(it)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bs2bModesList.forEachIndexed { index, name ->
                            FilterChip(
                                selected = bs2bMode == index,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    bs2bMode = index
                                    PrefsManager.saveBs2bMode(index)
                                    JamesDspController.setBs2bMode(index.toShort())
                                },
                                label = { Text(name) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            item(key = "dsp_tube_section") {
                SectionHeaderTitle(
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(id = R.string.section_analog_color),
                    accentColor = accentColor
                )

                EffectCardWithSettings(
                    title = stringResource(id = R.string.tube_title),
                    desc = stringResource(id = R.string.tube_desc),
                    badge = if (tubeEnabled) String.format(Locale.US, "%.1f dB", tubeDrive) else null,
                    checked = tubeEnabled,
                    accentColor = accentColor,
                    onChecked = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tubeEnabled = it
                        PrefsManager.saveTubeEnabled(it)
                        JamesDspController.setTubeEnabled(it)
                    }
                ) {
                    SettingSlider(
                        name = stringResource(id = R.string.setting_tube_drive),
                        value = tubeDrive,
                        range = 0f..10f,
                        unit = " dB",
                        showDecimal = true,
                        accentColor = accentColor,
                        onValueChange = {
                            tubeDrive = it
                            JamesDspController.setTubeDrive(it)
                        },
                        onValueChangeFinished = {
                            PrefsManager.saveTubeDrive(tubeDrive)
                            JamesDspController.setTubeDrive(tubeDrive)
                        }
                    )
                }
            }

            item(key = "dsp_limiter_section") {
                SectionHeaderTitle(
                    icon = Icons.Rounded.Tune,
                    title = stringResource(id = R.string.section_master_dynamics),
                    accentColor = accentColor
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.limiter_title),
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(id = R.string.limiter_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            TextButton(
                                onClick = {
                                    limThreshold = -0.5f
                                    limRelease = 60f
                                    postGain = 0f
                                    PrefsManager.saveLimThreshold(limThreshold)
                                    PrefsManager.saveLimRelease(limRelease)
                                    PrefsManager.savePostGain(postGain)
                                    JamesDspController.setLimiterParams(limThreshold, limRelease, postGain)
                                }
                            ) {
                                Text(stringResource(id = R.string.reset_to_zero), fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingSlider(
                            name = stringResource(id = R.string.setting_limiter_threshold),
                            value = limThreshold,
                            range = -12f..0f,
                            unit = " ${stringResource(id = R.string.unit_db)}",
                            showDecimal = true,
                            accentColor = accentColor,
                            onValueChange = {
                                limThreshold = it
                                JamesDspController.setLimiterParams(limThreshold, limRelease, postGain)
                            },
                            onValueChangeFinished = {
                                PrefsManager.saveLimThreshold(limThreshold)
                                JamesDspController.setLimiterParams(limThreshold, limRelease, postGain)
                            }
                        )

                        SettingSlider(
                            name = stringResource(id = R.string.setting_limiter_release),
                            value = limRelease,
                            range = 10f..300f,
                            unit = " ${stringResource(id = R.string.unit_ms)}",
                            accentColor = accentColor,
                            onValueChange = {
                                limRelease = it
                                JamesDspController.setLimiterParams(limThreshold, limRelease, postGain)
                            },
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
                            showDecimal = true,
                            accentColor = accentColor,
                            onValueChange = {
                                postGain = it
                                JamesDspController.setLimiterParams(limThreshold, limRelease, postGain)
                            },
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
            connectedDeviceName = connectedDeviceName,
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
private fun DspSignalTelemetryDeck(
    isEnabled: Boolean,
    autoHeadroomEnabled: Boolean,
    accentColor: Color,
    onToggleAutoHeadroom: (Boolean) -> Unit
) {
    var liveLoudness by remember { mutableFloatStateOf(0f) }
    var livePeakDb by remember { mutableFloatStateOf(-60f) }
    var isSoftClipping by remember { mutableStateOf(false) }
    var headroomDb by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isEnabled) {
        while (isActive && isEnabled) {
            liveLoudness = globalAudioLoudness.coerceIn(0f, 1f)
            livePeakDb = globalAudioPeakDb
            isSoftClipping = globalSoftClipActive
            headroomDb = JamesDspController.currentHeadroomDb
            delay(80L)
        }
        if (!isEnabled) {
            liveLoudness = 0f
            livePeakDb = -60f
            isSoftClipping = false
        }
    }

    val animatedMeter by animateFloatAsState(
        targetValue = liveLoudness,
        animationSpec = tween(90),
        label = "vuMeter"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isSoftClipping) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = accentColor
                    )
                    Column {
                        Text(
                            text = "DSP Signal & Headroom Guard",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (autoHeadroomEnabled) {
                                String.format(Locale.US, "Auto Preamp: %.1f dB • Peak: %.1f dBFS", headroomDb, livePeakDb)
                            } else {
                                String.format(Locale.US, "Preamp: 0.0 dB (Manual) • Peak: %.1f dBFS", livePeakDb)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilterChip(
                    selected = autoHeadroomEnabled,
                    onClick = { onToggleAutoHeadroom(!autoHeadroomEnabled) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Anti-Clip",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    shape = RoundedCornerShape(50)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                val barCount = 28
                val gap = 3.dp.toPx()
                val segWidth = (size.width - gap * (barCount - 1)) / barCount
                val activeSegments = (animatedMeter * barCount).toInt()

                for (i in 0 until barCount) {
                    val x = i * (segWidth + gap)
                    val segFraction = i.toFloat() / barCount
                    val segColor = when {
                        segFraction > 0.86f -> Color(0xFFFF5252)
                        segFraction > 0.68f -> Color(0xFFFFD740)
                        else -> accentColor
                    }
                    val alpha = if (i < activeSegments) 1f else 0.16f
                    drawRoundRect(
                        color = segColor.copy(alpha = alpha),
                        topLeft = Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(segWidth, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
private fun EqFrequencyResponseGraph(
    userGains: List<Float>,
    autoEqGains: FloatArray?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .border(1.dp, gridColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        drawLine(color = gridColor, start = Offset(0f, midY), end = Offset(w, midY), strokeWidth = 1.dp.toPx())
        drawLine(color = gridColor.copy(alpha = 0.08f), start = Offset(0f, h * 0.25f), end = Offset(w, h * 0.25f), strokeWidth = 1.dp.toPx())
        drawLine(color = gridColor.copy(alpha = 0.08f), start = Offset(0f, h * 0.75f), end = Offset(w, h * 0.75f), strokeWidth = 1.dp.toPx())

        if (userGains.isEmpty()) return@Canvas

        if (autoEqGains != null && autoEqGains.isNotEmpty()) {
            val autoPath = Path()
            val count = autoEqGains.size
            for (i in 0 until count) {
                val x = (i.toFloat() / (count - 1).coerceAtLeast(1)) * w
                val y = (midY - (autoEqGains[i].coerceIn(-15f, 15f) / 15f) * (midY * 0.88f)).coerceIn(0f, h)
                if (i == 0) autoPath.moveTo(x, y) else autoPath.lineTo(x, y)
            }
            drawPath(
                path = autoPath,
                color = secondaryColor.copy(alpha = 0.5f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        val points = userGains.mapIndexed { idx, gain ->
            val x = (idx.toFloat() / (userGains.size - 1).coerceAtLeast(1)) * w
            val y = (midY - (gain.coerceIn(-15f, 15f) / 15f) * (midY * 0.88f)).coerceIn(0f, h)
            Offset(x, y)
        }

        val curvePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val ctrlX = (p0.x + p1.x) / 2f
                cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(curvePath)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accentColor.copy(alpha = 0.32f), Color.Transparent)
            )
        )

        drawPath(
            path = curvePath,
            color = accentColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        points.forEach { pt ->
            drawCircle(color = accentColor, radius = 3.5.dp.toPx(), center = pt)
        }
    }
}

@Composable
private fun SectionHeaderTitle(
    icon: ImageVector,
    title: String,
    accentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun SettingSlider(
    name: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    unit: String = "%",
    showDecimal: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val formattedValue = if (showDecimal) {
        String.format(Locale.US, "%.1f%s", value, unit)
    } else {
        "${value.toInt()}$unit"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = formattedValue,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            )
        )
    }
}

@Composable
fun EffectCardWithSettings(
    title: String,
    desc: String,
    badge: String? = null,
    checked: Boolean,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onChecked: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
    settingsContent: @Composable ColumnScope.() -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(250),
        label = "cardColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                width = if (checked) 1.dp else 0.dp,
                color = if (checked) accentColor.copy(alpha = 0.45f) else Color.Transparent,
                shape = RoundedCornerShape(22.dp)
            )
            .clip(RoundedCornerShape(22.dp))
            .clickable { if (onClick != null) onClick() else onChecked(!checked) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!badge.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accentColor.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                LiquidGlassSwitch(
                    checked = checked,
                    onCheckedChange = onChecked
                )
            }
            AnimatedVisibility(
                visible = checked,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    settingsContent()
                }
            }
        }
    }
}

@Composable
fun AutoEqSelectionSheet(
    currentProfileName: String?,
    connectedDeviceName: String? = null,
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

    val filtered by remember(databaseList, searchQuery, connectedDeviceName) {
        derivedStateOf {
            val base = if (searchQuery.isBlank()) {
                databaseList
            } else {
                databaseList.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
            val dev = connectedDeviceName?.trim()
            if (!dev.isNullOrEmpty() && searchQuery.isBlank()) {
                val matching = base.filter { it.name.contains(dev, ignoreCase = true) }
                val rest = base.filterNot { it.name.contains(dev, ignoreCase = true) }
                matching + rest
            } else {
                base
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .padding(bottom = 16.dp)
        ) {
            if (isPresetDownloading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = stringResource(id = R.string.select_headphone_model),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (!connectedDeviceName.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = searchQuery.equals(connectedDeviceName, ignoreCase = true),
                        onClick = {
                            searchQuery = if (searchQuery.equals(connectedDeviceName, ignoreCase = true)) "" else connectedDeviceName
                        },
                        leadingIcon = { Icon(Icons.Default.Headphones, null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Найти для $connectedDeviceName") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                enabled = !isListLoading && !isPresetDownloading
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isListLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (errorText != null || downloadError != null) {
                    Text(
                        text = errorText ?: downloadError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp)
                    )
                } else if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.search_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn {
                        itemsIndexed(
                            items = filtered,
                            key = { index, item -> "${index}_${item.name}" },
                            contentType = { _, _ -> "autoeq_item" }
                        ) { _, profile ->
                            val isSelected = profile.name == currentProfileName
                            val isConnectedMatch = !connectedDeviceName.isNullOrBlank() &&
                                    profile.name.contains(connectedDeviceName, ignoreCase = true)

                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = profile.name,
                                        fontWeight = if (isSelected || isConnectedMatch) FontWeight.ExtraBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                supportingContent = if (isConnectedMatch) {
                                    {
                                        Text(
                                            text = "Совпадает с подключенным устройством",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else null,
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.Headphones,
                                        contentDescription = null,
                                        tint = if (isSelected || isConnectedMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = !isPresetDownloading) {
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