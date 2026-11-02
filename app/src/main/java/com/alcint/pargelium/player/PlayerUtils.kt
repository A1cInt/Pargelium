package com.alcint.pargelium.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.*
import com.alcint.pargelium.AudioDeviceHelper
import com.alcint.pargelium.R

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

@Immutable
data class AudioOutputState(val name: String, val iconResId: Int)

@Composable
fun rememberAudioOutputState(context: Context): AudioOutputState {
    val appContext = remember(context) { context.applicationContext }
    val audioManager = remember(appContext) {
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val helper = remember(appContext) { AudioDeviceHelper(appContext) }

    var state by remember { mutableStateOf(AudioOutputState("Speaker", R.drawable.ic_play_mobile)) }

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
                val newState = AudioOutputState(name, icon)
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