package com.alcint.pargelium.player

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.*
import com.alcint.pargelium.AudioDeviceHelper
import com.alcint.pargelium.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return buildString(8) {
        if (hours > 0L) {
            append(hours)
            append(':')
        }
        if (minutes < 10L) append('0')
        append(minutes)
        append(':')
        if (seconds < 10L) append('0')
        append(seconds)
    }
}

fun parseDurationToMs(durationStr: String): Long {
    val trimmed = durationStr.trim()
    if (trimmed.isEmpty()) return -1L

    val parts = trimmed.split(':')
    return try {
        when (parts.size) {
            2 -> {
                val m = parts[0].toLong()
                val s = parts[1].toLong()
                (m * 60L + s) * 1000L
            }
            3 -> {
                val h = parts[0].toLong()
                val m = parts[1].toLong()
                val s = parts[2].toLong()
                (h * 3600L + m * 60L + s) * 1000L
            }
            else -> -1L
        }
    } catch (_: NumberFormatException) {
        -1L
    }
}

suspend fun isVideoUriAsync(context: Context, uri: Uri): Boolean {
    val str = uri.toString().lowercase()
    if (
        str.endsWith(".mp4") ||
        str.endsWith(".webm") ||
        str.endsWith(".m3u8") ||
        str.endsWith(".mov") ||
        str.contains(".mp4?") ||
        str.contains(".m3u8?") ||
        str.contains("video") ||
        str.contains("canvaz")
    ) {
        return true
    }
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.getType(uri)?.startsWith("video") == true
        } catch (_: Exception) {
            false
        }
    }
}

fun openSystemAudioOutputSwitcher(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val panelIntent = Intent("com.android.settings.panel.action.MEDIA_OUTPUT").apply {
                putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(panelIntent)
            return
        }
    } catch (_: Exception) {}

    try {
        val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(btIntent)
    } catch (_: Exception) {}
}

@Immutable
data class AudioOutputState(
    val name: String,
    val iconResId: Int,
    val typeLabel: String = "Direct"
)

@Composable
fun rememberAudioOutputState(context: Context): AudioOutputState {
    val appContext = remember(context) { context.applicationContext }
    val audioManager = remember(appContext) {
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val helper = remember(appContext) { AudioDeviceHelper(appContext) }

    var state by remember { mutableStateOf(AudioOutputState("Speaker", R.drawable.ic_play_mobile, "Speaker")) }

    DisposableEffect(audioManager) {
        val updateState = {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            var btDevice: AudioDeviceInfo? = null
            var wiredDevice: AudioDeviceInfo? = null
            var speakerDevice: AudioDeviceInfo? = null

            for (device in outputs) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                        if (btDevice == null) btDevice = device
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE -> {
                        if (wiredDevice == null) wiredDevice = device
                    }
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                        if (speakerDevice == null) speakerDevice = device
                    }
                }
            }

            val active = btDevice ?: wiredDevice ?: speakerDevice ?: outputs.firstOrNull()
            if (active != null) {
                val name = active.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Speaker"
                val icon = helper.getDeviceIcon(active)
                val typeLabel = when {
                    btDevice != null -> "Bluetooth A2DP / BLE"
                    wiredDevice != null -> "Wired / USB DAC"
                    else -> "Internal Speaker"
                }
                val newState = AudioOutputState(name, icon, typeLabel)
                if (state != newState) {
                    state = newState
                }
            }
        }

        updateState()

        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = updateState()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = updateState()
        }
        audioManager.registerAudioDeviceCallback(callback, null)

        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
    return state
}