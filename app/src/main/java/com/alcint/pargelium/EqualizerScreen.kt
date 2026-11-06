@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.alcint.pargelium

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcint.pargelium.audio.JamesDspController
import com.alcint.pargelium.liquid.LiquidGlassSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.abs

@Composable
fun EqualizerScreen(seedColor: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val accentColor = if (seedColor != Color.Unspecified && seedColor != Color.Transparent) {
        seedColor
    } else {
        MaterialTheme.colorScheme.primary
    }

    var isEnabled by remember { mutableStateOf(PrefsManager.getEnabled()) }
    var autoHeadroomEnabled by remember { mutableStateOf(JamesDspController.isAutoHeadroomEnabled) }

    // 1. AutoEQ & Arbitrary Response EQ (GraphicEQ)
    var autoEqEnabled by remember { mutableStateOf(PrefsManager.getAutoEqEnabled()) }
    var currentProfile by remember { mutableStateOf(PrefsManager.getCurrentAutoEqProfile()) }
    var customGraphicEqStr by remember { mutableStateOf(PrefsManager.getCustomGraphicEqString()) }
    var showGraphicEqEditor by remember { mutableStateOf(false) }
    var showAutoEqSheet by remember { mutableStateOf(false) }
    var autoEqDatabaseList by remember { mutableStateOf<List<AutoEqProfile>>(emptyList()) }
    var isAutoEqListLoading by remember { mutableStateOf(true) }
    var autoEqErrorText by remember { mutableStateOf<String?>(null) }

    // 2. Multimodal Equalizer (15-Band ISO & 10-Band)
    var userEqEnabled by remember { mutableStateOf(PrefsManager.getUserEqEnabled()) }
    var is15BandMode by remember { mutableStateOf(PrefsManager.getEq15BandMode()) }
    var eqFilterMode by remember { mutableIntStateOf(PrefsManager.getEqFilterMode()) }
    var eqInterpMode by remember { mutableIntStateOf(PrefsManager.getEqInterpolationMode()) }

    val userEqGains10 = remember {
        val saved = PrefsManager.getUserEqGains()
        mutableStateListOf(*FloatArray(10) { idx -> saved.getOrElse(idx) { 0f } }.toTypedArray())
    }
    val userEqGains15 = remember {
        val saved = PrefsManager.getUserEqGains15()
        mutableStateListOf(*FloatArray(15) { idx -> saved.getOrElse(idx) { 0f } }.toTypedArray())
    }
    val eqLabels10 = remember { listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k") }
    val eqLabels15 = remember {
        listOf("25", "40", "63", "100", "160", "250", "400", "630", "1k", "1.6k", "2.5k", "4k", "6.3k", "10k", "16k")
    }
    val eqFilterModeLabels = remember {
        listOf(
            "FIR Min Phase",
            "FIR Linear Phase",
            "IIR 4th Order",
            "IIR 6th Order",
            "IIR 8th Order",
            "IIR 10th Order",
            "IIR 12th Order"
        )
    }
    var activePresetName by remember { mutableStateOf<String?>(null) }

    // 3. Dynamic Range Compander (7 bands)
    var companderEnabled by remember { mutableStateOf(PrefsManager.getCompanderEnabled()) }
    var companderTc by remember { mutableFloatStateOf(PrefsManager.getCompanderTimeConstant()) }
    var companderGranularity by remember { mutableIntStateOf(PrefsManager.getCompanderGranularity()) }
    var companderTfRes by remember { mutableIntStateOf(PrefsManager.getCompanderTfResolution()) }
    val companderGains = remember {
        val saved = PrefsManager.getCompanderGains()
        mutableStateListOf(*FloatArray(7) { idx -> saved.getOrElse(idx) { 0f } }.toTypedArray())
    }
    val companderLabels = remember { listOf("95", "200", "400", "800", "1.6k", "3.4k", "7.5k") }
    val companderTfLabels = remember { listOf("Uniform", "Low Latency", "High Res", "Extreme") }

    // 4. Dynamic Bass Boost
    var bassEnabled by remember { mutableStateOf(PrefsManager.getBassEnabled()) }
    var bass by remember { mutableFloatStateOf(PrefsManager.getBass().toFloat()) }

    // 5. Reverberation (Progenitor 2 - все 12 пресетов JamesDSP)
    var roomEnabled by remember { mutableStateOf(PrefsManager.getRoom()) }
    var reverbMode by remember { mutableIntStateOf(PrefsManager.getReverbMode()) }
    val reverbModesList = remember {
        listOf(
            "Small Hall", "Medium Hall", "Large Hall",
            "Small Room", "Medium Room", "Large Room",
            "Medium ER1", "Medium ER2", "Plate High",
            "Plate Low", "Long Reverb 1", "Long Reverb 2"
        )
    }

    // 6. Vacuum Tube (Analog Modeling)
    var tubeEnabled by remember { mutableStateOf(PrefsManager.getTubeEnabled()) }
    var tubeDrive by remember { mutableFloatStateOf(PrefsManager.getTubeDrive()) }

    // 7. Stereo Widener
    var stereoWideEnabled by remember { mutableStateOf(PrefsManager.getStereoWideEnabled()) }
    var stereoWidth by remember { mutableFloatStateOf(PrefsManager.getStereoWidth()) }

    // 8. BS2B & HRTF Crossfeed (все 6 режимов из libjamesdsp)
    var bs2bEnabled by remember { mutableStateOf(PrefsManager.getBs2bEnabled()) }
    var bs2bMode by remember { mutableIntStateOf(PrefsManager.getBs2bMode()) }
    val bs2bModesList = remember {
        listOf(
            "BS2B Chu Moy (700Hz / 4.5dB)",
            "BS2B Jan Meier (650Hz / 9.5dB)",
            "BS2B Default (700Hz / 6.0dB)",
            "HRTF Crossfeed Weak",
            "HRTF Crossfeed Medium",
            "HRTF Crossfeed Strong"
        )
    }

    // 9. ViPER-DDC (.vdc)
    var ddcEnabled by remember { mutableStateOf(PrefsManager.getDdcEnabled()) }
    var selectedVdcId by remember { mutableStateOf(PrefsManager.getDdcPresetId()) }
    var hasCustomVdc by remember { mutableStateOf(!PrefsManager.getCustomVdcContent().isNullOrBlank()) }

    // 10. Convolver (1D Impulse Response + IR Conditioning)
    var convolverEnabled by remember { mutableStateOf(PrefsManager.getConvolverEnabled()) }
    var convolverPresetId by remember { mutableIntStateOf(PrefsManager.getConvolverPresetId()) }
    var convolverWetMix by remember { mutableFloatStateOf(PrefsManager.getConvolverWetMix()) }
    var convolverPreDelay by remember { mutableFloatStateOf(PrefsManager.getConvolverPreDelayMs()) }
    var convolverTailTrim by remember { mutableFloatStateOf(PrefsManager.getConvolverTailTrim()) }
    var convolverInvPhase by remember { mutableStateOf(PrefsManager.getConvolverInvertPhase()) }
    var customIrName by remember { mutableStateOf(JamesDspController.customImpulseName) }
    var irWaveform by remember { mutableStateOf(JamesDspController.currentIrWaveformPreview) }

    // 11. EEL2 Liveprog (Programmable DSP Engine)
    var liveprogEnabled by remember { mutableStateOf(PrefsManager.getLiveprogEnabled()) }
    var liveprogScriptId by remember { mutableIntStateOf(PrefsManager.getLiveprogScriptId()) }
    val liveprogParams = remember {
        val saved = PrefsManager.getLiveprogParams()
        mutableStateListOf(*FloatArray(8) { idx -> saved.getOrElse(idx) { 0f } }.toTypedArray())
    }
    var showEelSourceEditor by remember { mutableStateOf(false) }
    var customEelCodeText by remember {
        mutableStateOf(
            PrefsManager.getCustomEelCode()
                ?: JamesDspController.builtInEelScripts.first().eelSource
        )
    }

    // 12. Master Limiter & PostGain
    var limThreshold by remember { mutableFloatStateOf(PrefsManager.getLimThreshold()) }
    var limRelease by remember { mutableFloatStateOf(PrefsManager.getLimRelease()) }
    var postGain by remember { mutableFloatStateOf(PrefsManager.getPostGain()) }

    // Сохраненные пользовательские полные профили JamesDSP
    var savedSnapshots by remember { mutableStateOf(PrefsManager.getSavedDspSnapshots()) }
    var showSaveSnapshotDialog by remember { mutableStateOf(false) }
    var snapshotNameInput by remember { mutableStateOf("") }

    val vdcFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val content = try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    }
                } catch (_: Exception) { null }

                withContext(Dispatchers.Main) {
                    if (!content.isNullOrBlank()) {
                        JamesDspController.setCustomVdcContent(content)
                        JamesDspController.setDdcEnabled(true)
                        ddcEnabled = true
                        hasCustomVdc = true
                        Toast.makeText(context, "ViPER-DDC (.vdc) loaded", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val eelFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val code = try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    }
                } catch (_: Exception) { null }

                withContext(Dispatchers.Main) {
                    if (!code.isNullOrBlank()) {
                        customEelCodeText = code
                        JamesDspController.setLiveprogParams(liveprogParams.toFloatArray(), code)
                        JamesDspController.setLiveprogEnabled(true)
                        liveprogEnabled = true
                        showEelSourceEditor = true
                        Toast.makeText(context, "EEL2 Liveprog script loaded", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val irWavPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                var fileName: String? = null
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) fileName = c.getString(idx)
                        }
                    }
                } catch (_: Exception) {}

                val ok = JamesDspController.loadImpulseFromUri(context, uri, fileName)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        convolverEnabled = true
                        customIrName = JamesDspController.customImpulseName
                        irWaveform = JamesDspController.currentIrWaveformPreview
                        Toast.makeText(context, "Impulse Response loaded: ${customIrName ?: "WAV"}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Unsupported IR format (use 16/24/32-bit WAV)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        JamesDspController.syncFromPrefs()
        irWaveform = JamesDspController.currentIrWaveformPreview
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
        autoEqEnabled, userEqEnabled, companderEnabled, bassEnabled, roomEnabled,
        stereoWideEnabled, bs2bEnabled, tubeEnabled, ddcEnabled, convolverEnabled, liveprogEnabled
    ) {
        listOf(
            autoEqEnabled, userEqEnabled, companderEnabled, bassEnabled, roomEnabled,
            stereoWideEnabled, bs2bEnabled, tubeEnabled, ddcEnabled, convolverEnabled, liveprogEnabled
        ).count { it }
    }

    if (showSaveSnapshotDialog) {
        AlertDialog(
            onDismissRequest = { showSaveSnapshotDialog = false },
            title = { Text("Save JamesDSP Profile", fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = snapshotNameInput,
                    onValueChange = { snapshotNameInput = it },
                    label = { Text("Profile Name (e.g. AirPods Pro Bass)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = snapshotNameInput.trim().ifEmpty { "Preset ${savedSnapshots.size + 1}" }
                        val snap = JamesDspFullSnapshot(
                            name = name,
                            eq15Enabled = userEqEnabled,
                            eqFilterMode = eqFilterMode,
                            eqInterpMode = eqInterpMode,
                            eqGains15 = userEqGains15.toList(),
                            companderEnabled = companderEnabled,
                            companderTc = companderTc,
                            companderGranularity = companderGranularity,
                            companderTfRes = companderTfRes,
                            companderGains = companderGains.toList(),
                            bassEnabled = bassEnabled,
                            bassGain = bass.toInt(),
                            tubeEnabled = tubeEnabled,
                            tubeDrive = tubeDrive,
                            ddcEnabled = ddcEnabled,
                            ddcPresetId = selectedVdcId,
                            convolverEnabled = convolverEnabled,
                            convolverPresetId = convolverPresetId,
                            convolverWetMix = convolverWetMix,
                            convolverPreDelay = convolverPreDelay,
                            liveprogEnabled = liveprogEnabled,
                            liveprogScriptId = liveprogScriptId,
                            liveprogParams = liveprogParams.toList(),
                            reverbEnabled = roomEnabled,
                            reverbPreset = reverbMode,
                            stereoWideEnabled = stereoWideEnabled,
                            stereoWidth = stereoWidth,
                            bs2bEnabled = bs2bEnabled,
                            bs2bMode = bs2bMode,
                            limThreshold = limThreshold,
                            limRelease = limRelease,
                            postGain = postGain
                        )
                        val updated = savedSnapshots.filterNot { it.name.equals(name, true) } + snap
                        savedSnapshots = updated
                        PrefsManager.saveDspSnapshots(updated)
                        snapshotNameInput = ""
                        showSaveSnapshotDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveSnapshotDialog = false }) {
                    Text(stringResource(id = R.string.album_action_close))
                }
            }
        )
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
                            text = if (isEnabled) "JamesDSP • $activeModulesCount/11 ON" else "BYPASS",
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

            // Полоса полных сохраненных профилей JamesDSP
            item(key = "dsp_snapshots_bar") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        AssistChip(
                            onClick = { showSaveSnapshotDialog = true },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Save DSP Profile", fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    items(savedSnapshots, key = { it.name }) { snap ->
                        InputChip(
                            selected = false,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                userEqEnabled = snap.eq15Enabled
                                eqFilterMode = snap.eqFilterMode
                                eqInterpMode = snap.eqInterpMode
                                is15BandMode = true
                                for (i in 0 until 15) userEqGains15[i] = snap.eqGains15.getOrElse(i) { 0f }
                                PrefsManager.saveUserEqEnabled(userEqEnabled)
                                PrefsManager.saveUserEqGains15(userEqGains15.toFloatArray())
                                JamesDspController.setEq15BandMode(true)
                                JamesDspController.setEqFilterMode(eqFilterMode)
                                JamesDspController.setEqInterpolationMode(eqInterpMode)
                                JamesDspController.setEqualizerEnabled(userEqEnabled)
                                JamesDspController.setEqualizerGains15(userEqGains15.toFloatArray())

                                companderEnabled = snap.companderEnabled
                                companderTc = snap.companderTc
                                companderGranularity = snap.companderGranularity
                                companderTfRes = snap.companderTfRes
                                for (i in 0 until 7) companderGains[i] = snap.companderGains.getOrElse(i) { 0f }
                                JamesDspController.setCompanderEnabled(companderEnabled)
                                JamesDspController.setCompanderParams(
                                    companderTc, companderGranularity, companderTfRes, companderGains.toFloatArray()
                                )

                                bassEnabled = snap.bassEnabled
                                bass = snap.bassGain.toFloat()
                                PrefsManager.saveBassEnabled(bassEnabled)
                                PrefsManager.saveBass(snap.bassGain)
                                JamesDspController.setBassEnabled(bassEnabled)
                                JamesDspController.setBassGainFloat(bass)

                                tubeEnabled = snap.tubeEnabled
                                tubeDrive = snap.tubeDrive
                                PrefsManager.saveTubeEnabled(tubeEnabled)
                                PrefsManager.saveTubeDrive(tubeDrive)
                                JamesDspController.setTubeEnabled(tubeEnabled)
                                JamesDspController.setTubeDrive(tubeDrive)

                                ddcEnabled = snap.ddcEnabled
                                selectedVdcId = snap.ddcPresetId
                                JamesDspController.setDdcPreset(selectedVdcId)
                                JamesDspController.setDdcEnabled(ddcEnabled)

                                convolverEnabled = snap.convolverEnabled
                                convolverPresetId = snap.convolverPresetId
                                convolverWetMix = snap.convolverWetMix
                                convolverPreDelay = snap.convolverPreDelay
                                JamesDspController.setConvolverPreset(convolverPresetId)
                                JamesDspController.setConvolverConditioning(
                                    convolverWetMix, convolverPreDelay, convolverTailTrim, convolverInvPhase
                                )
                                JamesDspController.setConvolverEnabled(convolverEnabled)

                                liveprogEnabled = snap.liveprogEnabled
                                liveprogScriptId = snap.liveprogScriptId
                                for (i in 0 until 8) liveprogParams[i] = snap.liveprogParams.getOrElse(i) { 0f }
                                JamesDspController.setLiveprogScript(liveprogScriptId)
                                JamesDspController.setLiveprogParams(liveprogParams.toFloatArray())
                                JamesDspController.setLiveprogEnabled(liveprogEnabled)

                                roomEnabled = snap.reverbEnabled
                                reverbMode = snap.reverbPreset
                                PrefsManager.saveRoom(roomEnabled)
                                PrefsManager.saveReverbMode(reverbMode)
                                JamesDspController.setReverbEnabled(roomEnabled)
                                JamesDspController.setReverbPreset(reverbMode.toShort())

                                stereoWideEnabled = snap.stereoWideEnabled
                                stereoWidth = snap.stereoWidth
                                PrefsManager.saveStereoWideEnabled(stereoWideEnabled)
                                PrefsManager.saveStereoWidth(stereoWidth)
                                JamesDspController.setStereoWideEnabled(stereoWideEnabled)
                                JamesDspController.setStereoWidth(stereoWidth.toInt().toShort())

                                bs2bEnabled = snap.bs2bEnabled
                                bs2bMode = snap.bs2bMode
                                PrefsManager.saveBs2bEnabled(bs2bEnabled)
                                PrefsManager.saveBs2bMode(bs2bMode)
                                JamesDspController.setBs2bEnabled(bs2bEnabled)
                                JamesDspController.setBs2bMode(bs2bMode.toShort())

                                limThreshold = snap.limThreshold
                                limRelease = snap.limRelease
                                postGain = snap.postGain
                                PrefsManager.saveLimThreshold(limThreshold)
                                PrefsManager.saveLimRelease(limRelease)
                                PrefsManager.savePostGain(postGain)
                                JamesDspController.setLimiterParams(limThreshold, limRelease, postGain)
                            },
                            label = { Text(snap.name, fontWeight = FontWeight.SemiBold) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            val next = savedSnapshots.filterNot { it.name == snap.name }
                                            savedSnapshots = next
                                            PrefsManager.saveDspSnapshots(next)
                                        }
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // 1. ЭКВАЛАЙЗЕРЫ (Arbitrary Response EQ + Multimodal 15-Band / 10-Band EQ)
            item(key = "dsp_eq_section") {
                SectionHeaderTitle(
                    icon = Icons.Rounded.Equalizer,
                    title = stringResource(id = R.string.eq_section_title),
                    accentColor = accentColor
                )

                val autoEqDesc = buildString {
                    if (customGraphicEqStr.isNotBlank()) {
                        append("Custom GraphicEQ Curve Active")
                    } else {
                        append(currentProfile?.name ?: stringResource(id = R.string.click_to_select_model))
                    }
                    if (connectedDeviceName != null) {
                        append("\n🎧 ${stringResource(id = R.string.connected_device_fmt, connectedDeviceName!!)}")
                    }
                }

                EffectCardWithSettings(
                    title = stringResource(id = R.string.autoeq_title),
                    desc = autoEqDesc,
                    badge = if (autoEqEnabled) "Arbitrary FIR" else null,
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { showAutoEqSheet = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
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

                            OutlinedButton(
                                onClick = { showGraphicEqEditor = !showGraphicEqEditor },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("GraphicEQ", fontWeight = FontWeight.Bold)
                            }
                        }

                        AnimatedVisibility(visible = showGraphicEqEditor) {
                            OutlinedTextField(
                                value = customGraphicEqStr,
                                onValueChange = {
                                    customGraphicEqStr = it
                                    JamesDspController.setCustomGraphicEqString(it)
                                },
                                label = { Text("GraphicEQ: 20 0.0; 63 3.5; 1000 -1.2...") },
                                singleLine = false,
                                maxLines = 3,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                EffectCardWithSettings(
                    title = if (is15BandMode) "15-Band Multimodal Equalizer" else stringResource(id = R.string.ten_band_eq_title),
                    desc = "${eqFilterModeLabels.getOrElse(eqFilterMode) { "FIR" }} • ${if (eqInterpMode == 0) "PCHIP" else "Makima Spline"}",
                    badge = activePresetName ?: if (is15BandMode) "15 ISO" else "10 Band",
                    checked = userEqEnabled,
                    accentColor = accentColor,
                    onChecked = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        userEqEnabled = it
                        PrefsManager.saveUserEqEnabled(it)
                        JamesDspController.setEqualizerEnabled(it)
                    }
                ) {
                    val activeGainsList = if (is15BandMode) userEqGains15 else userEqGains10
                    val activeLabels = if (is15BandMode) eqLabels15 else eqLabels10

                    EqFrequencyResponseGraph(
                        userGains = activeGainsList,
                        autoEqGains = if (autoEqEnabled) currentProfile?.gains else null,
                        accentColor = accentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(114.dp)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = is15BandMode,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val next = !is15BandMode
                                    if (next && userEqGains15.all { it == 0f }) {
                                        val interp = JamesDspController.interpolate10To15Bands(userEqGains10.toFloatArray())
                                        for (i in 0 until 15) userEqGains15[i] = interp[i]
                                    }
                                    is15BandMode = next
                                    JamesDspController.setEq15BandMode(next)
                                },
                                label = { Text(if (is15BandMode) "15 ISO Bands" else "10 Bands", fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        item {
                            FilterChip(
                                selected = eqInterpMode == 1,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    eqInterpMode = if (eqInterpMode == 0) 1 else 0
                                    JamesDspController.setEqInterpolationMode(eqInterpMode)
                                },
                                label = { Text(if (eqInterpMode == 0) "Spline: PCHIP" else "Spline: Makima") },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        itemsIndexed(eqFilterModeLabels) { idx, modeName ->
                            FilterChip(
                                selected = eqFilterMode == idx,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    eqFilterMode = idx
                                    JamesDspController.setEqFilterMode(idx)
                                },
                                label = { Text(modeName) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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
                                        userEqGains10[i] = preset.gains10.getOrElse(i) { 0f }
                                    }
                                    val arr10 = userEqGains10.toFloatArray()
                                    val arr15 = JamesDspController.interpolate10To15Bands(arr10)
                                    for (i in 0..14) userEqGains15[i] = arr15[i]
                                    PrefsManager.saveUserEqGains(arr10)
                                    PrefsManager.saveUserEqGains15(arr15)
                                    JamesDspController.setEqualizerGains(if (is15BandMode) arr15 else arr10)
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
                        val bandCount = activeGainsList.size
                        for (i in 0 until bandCount) {
                            val gainVal = activeGainsList[i]
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 5.dp)
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
                                        .width(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Slider(
                                        value = gainVal,
                                        onValueChange = { newValue ->
                                            activePresetName = null
                                            if (is15BandMode) {
                                                userEqGains15[i] = newValue
                                                JamesDspController.setEqualizerGains15(userEqGains15.toFloatArray())
                                            } else {
                                                userEqGains10[i] = newValue
                                                JamesDspController.setEqualizerGains(userEqGains10.toFloatArray())
                                            }
                                        },
                                        onValueChangeFinished = {
                                            if (is15BandMode) {
                                                val array15 = userEqGains15.toFloatArray()
                                                PrefsManager.saveUserEqGains15(array15)
                                                JamesDspController.setEqualizerGains15(array15)
                                            } else {
                                                val array10 = userEqGains10.toFloatArray()
                                                PrefsManager.saveUserEqGains(array10)
                                                JamesDspController.setEqualizerGains(array10)
                                            }
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
                                    text = activeLabels[i],
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
                            for (i in 0..9) userEqGains10[i] = 0f
                            for (i in 0..14) userEqGains15[i] = 0f
                            val zero10 = FloatArray(10) { 0f }
                            val zero15 = FloatArray(15) { 0f }
                            PrefsManager.saveUserEqGains(zero10)
                            PrefsManager.saveUserEqGains15(zero15)
                            JamesDspController.setEqualizerGains(zero15)
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

            // 2. DYNAMIC RANGE COMPANDER (7-Band Time-Frequency Compander)
            item(key = "dsp_compander_section") {
                SectionHeaderTitle(
                    icon = Icons.Rounded.Compress,
                    title = "Dynamic Range Compander",
                    accentColor = accentColor
                )

                EffectCardWithSettings(
                    title = "Multiband Compander (7-Band)",
                    desc = "Time-frequency dynamic range compression & micro-detail booster",
                    badge = if (companderEnabled) companderTfLabels.getOrElse(companderTfRes) { "Active" } else null,
                    checked = companderEnabled,
                    accentColor = accentColor,
                    onChecked = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        companderEnabled = it
                        JamesDspController.setCompanderEnabled(it)
                    }
                ) {
                    EqFrequencyResponseGraph(
                        userGains = companderGains,
                        autoEqGains = null,
                        accentColor = accentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    SettingSlider(
                        name = "Time Constant (Release Speed)",
                        value = companderTc,
                        range = 0.05f..1.0f,
                        unit = " s",
                        showDecimal = true,
                        accentColor = accentColor,
                        onValueChange = {
                            companderTc = it
                            JamesDspController.setCompanderParams(
                                companderTc, companderGranularity, companderTfRes, companderGains.toFloatArray()
                            )
                        },
                        onValueChangeFinished = {
                            PrefsManager.saveCompanderTimeConstant(companderTc)
                        }
                    )

                    SettingSlider(
                        name = "Granularity",
                        value = companderGranularity.toFloat(),
                        range = 1f..4f,
                        unit = "x",
                        accentColor = accentColor,
                        onValueChange = {
                            companderGranularity = it.toInt().coerceIn(1, 4)
                            JamesDspController.setCompanderParams(
                                companderTc, companderGranularity, companderTfRes, companderGains.toFloatArray()
                            )
                        },
                        onValueChangeFinished = {
                            PrefsManager.saveCompanderGranularity(companderGranularity)
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        companderTfLabels.forEachIndexed { idx, label ->
                            FilterChip(
                                selected = companderTfRes == idx,
                                onClick = {
                                    companderTfRes = idx
                                    PrefsManager.saveCompanderTfResolution(idx)
                                    JamesDspController.setCompanderParams(
                                        companderTc, companderGranularity, companderTfRes, companderGains.toFloatArray()
                                    )
                                },
                                label = { Text(label) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (i in 0 until 7) {
                            val gVal = companderGains[i]
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%+.1f", gVal),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (abs(gVal) > 0.2f) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .height(136.dp)
                                        .width(38.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Slider(
                                        value = gVal,
                                        onValueChange = { newVal ->
                                            companderGains[i] = newVal
                                            JamesDspController.setCompanderParams(
                                                companderTc, companderGranularity, companderTfRes, companderGains.toFloatArray()
                                            )
                                        },
                                        onValueChangeFinished = {
                                            PrefsManager.saveCompanderGains(companderGains.toFloatArray())
                                        },
                                        valueRange = -12f..12f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentColor,
                                            activeTrackColor = accentColor
                                        ),
                                        modifier = Modifier
                                            .requiredWidth(136.dp)
                                            .graphicsLayer {
                                                rotationZ = -90f
                                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                                            }
                                    )
                                }
                                Text(
                                    text = companderLabels[i],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            // 3. EEL2 LIVEPROG (PROGRAMMABLE DSP ENGINE)
            item(key = "dsp_liveprog_section") {
                SectionHeaderTitle(
                    icon = Icons.Rounded.Code,
                    title = "EEL2 Liveprog Programmable DSP",
                    accentColor = accentColor
                )

                val currentEelScript = JamesDspController.builtInEelScripts.getOrElse(liveprogScriptId) {
                    JamesDspController.builtInEelScripts.first()
                }

                EffectCardWithSettings(
                    title = "Liveprog Script Engine (.eel)",
                    desc = "${currentEelScript.title} — ${currentEelScript.description}",
                    badge = if (liveprogEnabled) currentEelScript.fileName else null,
                    checked = liveprogEnabled,
                    accentColor = accentColor,
                    onChecked = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        liveprogEnabled = it
                        JamesDspController.setLiveprogEnabled(it)
                    }
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(JamesDspController.builtInEelScripts, key = { it.id }) { script ->
                            FilterChip(
                                selected = liveprogScriptId == script.id,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    liveprogScriptId = script.id
                                    customEelCodeText = script.eelSource
                                    script.parameters.forEach { p ->
                                        liveprogParams[p.index] = p.defaultVal
                                    }
                                    JamesDspController.setLiveprogScript(script.id)
                                },
                                label = { Text(script.fileName, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    currentEelScript.parameters.forEach { paramDef ->
                        val pVal = liveprogParams.getOrElse(paramDef.index) { paramDef.defaultVal }
                        SettingSlider(
                            name = paramDef.name,
                            value = pVal,
                            range = paramDef.min..paramDef.max,
                            unit = paramDef.unit,
                            showDecimal = (paramDef.max - paramDef.min) <= 20f,
                            accentColor = accentColor,
                            onValueChange = { newVal ->
                                liveprogParams[paramDef.index] = newVal
                                JamesDspController.setLiveprogParams(liveprogParams.toFloatArray())
                            },
                            onValueChangeFinished = {
                                PrefsManager.saveLiveprogParams(liveprogParams.toFloatArray())
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { eelFilePicker.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import .eel", fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = { showEelSourceEditor = !showEelSourceEditor },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (showEelSourceEditor) "Hide Code" else "EEL2 Editor", fontWeight = FontWeight.Bold)
                        }
                    }

                    AnimatedVisibility(visible = showEelSourceEditor) {
                        OutlinedTextField(
                            value = customEelCodeText,
                            onValueChange = {
                                customEelCodeText = it
                                JamesDspController.setLiveprogParams(liveprogParams.toFloatArray(), it)
                            },
                            label = { Text("EEL2 Script (@init / @sample)") },
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            minLines = 4,
                            maxLines = 8,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 4. DYNAMIC BASS & ANALOG TUBE
            item(key = "dsp_bass_and_tube_section") {
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
                        range = 0f..12f,
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

            // 5. VIPER-DDC & CONVOLVER (IMPULSE RESPONSE ENGINE)
            item(key = "dsp_ddc_and_convolver_section") {
                SectionHeaderTitle(
                    icon = Icons.Rounded.Memory,
                    title = "Convolver & ViPER-DDC",
                    accentColor = accentColor
                )

                val activeVdcTitle = if (hasCustomVdc) {
                    "Custom .vdc File"
                } else {
                    JamesDspController.builtInVdcPresets.find { it.id == selectedVdcId }?.name ?: "DT770 Pro"
                }

                EffectCardWithSettings(
                    title = "ViPER-DDC (IIR Cascade)",
                    desc = "Precision Second-Order Sections headphone correction ($activeVdcTitle)",
                    badge = if (ddcEnabled) "SOS IIR" else null,
                    checked = ddcEnabled,
                    accentColor = accentColor,
                    onChecked = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        ddcEnabled = it
                        JamesDspController.setDdcEnabled(it)
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        JamesDspController.builtInVdcPresets.forEach { preset ->
                            val isSelected = !hasCustomVdc && selectedVdcId == preset.id
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedVdcId = preset.id
                                    hasCustomVdc = false
                                    JamesDspController.setDdcPreset(preset.id)
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(preset.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(preset.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = accentColor)
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { vdcFilePicker.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (hasCustomVdc) "Change Custom .vdc File" else "Import .vdc File from Storage", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                val activeIrTitle = customIrName
                    ?: JamesDspController.builtInImpulsePresets.find { it.id == convolverPresetId }?.name
                    ?: "Studio Impulse"

                EffectCardWithSettings(
                    title = "Convolver (Partitioned FFT IR Engine)",
                    desc = "Impulse response convolution ($activeIrTitle)",
                    badge = if (convolverEnabled) "${convolverWetMix.toInt()}% Wet" else null,
                    checked = convolverEnabled,
                    accentColor = accentColor,
                    onChecked = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        convolverEnabled = it
                        JamesDspController.setConvolverEnabled(it)
                        irWaveform = JamesDspController.currentIrWaveformPreview
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // График формы импульса (IR Waveform Visualizer)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            val count = irWaveform.size.coerceAtLeast(1)
                            val gap = 2.dp.toPx()
                            val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
                            val midY = size.height / 2f

                            for (i in 0 until count) {
                                val amp = irWaveform[i].coerceIn(0.04f, 1f)
                                val h = amp * size.height * 0.9f
                                val x = i * (barW + gap)
                                drawLine(
                                    color = accentColor.copy(alpha = 0.35f + 0.65f * amp),
                                    start = Offset(x + barW / 2f, midY - h / 2f),
                                    end = Offset(x + barW / 2f, midY + h / 2f),
                                    strokeWidth = barW,
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        SettingSlider(
                            name = "IR Wet Mix",
                            value = convolverWetMix,
                            range = 0f..100f,
                            unit = "%",
                            accentColor = accentColor,
                            onValueChange = {
                                convolverWetMix = it
                                JamesDspController.setConvolverConditioning(
                                    convolverWetMix, convolverPreDelay, convolverTailTrim, convolverInvPhase
                                )
                                irWaveform = JamesDspController.currentIrWaveformPreview
                            },
                            onValueChangeFinished = {}
                        )

                        SettingSlider(
                            name = "IR Pre-Delay",
                            value = convolverPreDelay,
                            range = 0f..80f,
                            unit = " ms",
                            accentColor = accentColor,
                            onValueChange = {
                                convolverPreDelay = it
                                JamesDspController.setConvolverConditioning(
                                    convolverWetMix, convolverPreDelay, convolverTailTrim, convolverInvPhase
                                )
                                irWaveform = JamesDspController.currentIrWaveformPreview
                            },
                            onValueChangeFinished = {}
                        )

                        SettingSlider(
                            name = "IR Tail Length (Advise Trim)",
                            value = convolverTailTrim,
                            range = 15f..100f,
                            unit = "%",
                            accentColor = accentColor,
                            onValueChange = {
                                convolverTailTrim = it
                                JamesDspController.setConvolverConditioning(
                                    convolverWetMix, convolverPreDelay, convolverTailTrim, convolverInvPhase
                                )
                                irWaveform = JamesDspController.currentIrWaveformPreview
                            },
                            onValueChangeFinished = {}
                        )

                        FilterChip(
                            selected = convolverInvPhase,
                            onClick = {
                                convolverInvPhase = !convolverInvPhase
                                JamesDspController.setConvolverConditioning(
                                    convolverWetMix, convolverPreDelay, convolverTailTrim, convolverInvPhase
                                )
                            },
                            label = { Text("Invert IR Phase (180°)") },
                            shape = RoundedCornerShape(12.dp)
                        )

                        JamesDspController.builtInImpulsePresets.forEach { irPreset ->
                            val isSelected = customIrName == null && convolverPresetId == irPreset.id
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    convolverPresetId = irPreset.id
                                    customIrName = null
                                    JamesDspController.setConvolverPreset(irPreset.id)
                                    irWaveform = JamesDspController.currentIrWaveformPreview
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(irPreset.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(irPreset.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = accentColor)
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { irWavPicker.launch(arrayOf("audio/*", "*/*")) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (customIrName != null) "Loaded: $customIrName" else "Load Custom .wav / .irs Impulse",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 6. ПРОСТРАНСТВО (Progenitor 2 Reverb, Stereo Widener, BS2B / HRTF Crossfeed)
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
                    badge = if (bs2bEnabled) bs2bModesList.getOrNull(bs2bMode)?.substringBefore(" (") else null,
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

            // 7. MASTER LIMITER & POST-GAIN
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
                            range = -24f..-0.05f,
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
                            range = 5f..500f,
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
                            range = -15f..15f,
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
                            text = "JamesDSP 64-bit Engine • ${JamesDspController.currentSampleRate} Hz",
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
        String.format(Locale.US, "%.2f%s", value, unit)
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
                        label = { Text(connectedDeviceName) },
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
