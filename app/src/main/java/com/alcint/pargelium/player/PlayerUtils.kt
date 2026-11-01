package com.alcint.pargelium.player

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.*
import com.alcint.pargelium.AudioDeviceHelper
import com.alcint.pargelium.R

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun parseDurationToMs(durationStr: String): Long {
    try {
        val parts = durationStr.trim().split(":")
        if (parts.size == 2) {
            val m = parts[0].toLong()
            val s = parts[1].toLong()
            return (m * 60 + s) * 1000
        } else if (parts.size == 3) {
            val h = parts[0].toLong()
            val m = parts[1].toLong()
            val s = parts[2].toLong()
            return (h * 3600 + m * 60 + s) * 1000
        }
    } catch (_: Exception) { }
    return -1L
}

data class AudioOutputState(val name: String, val iconResId: Int)

@Composable
fun rememberAudioOutputState(context: android.content.Context): AudioOutputState {
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val helper = remember { AudioDeviceHelper(context) }

    var state by remember { mutableStateOf(AudioOutputState("Speaker", R.drawable.ic_play_mobile)) }

    DisposableEffect(audioManager) {
        val updateState = {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val active = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER }
                ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
                ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?: outputs.firstOrNull()

            if (active != null) {
                val name = active.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Speaker"
                state = AudioOutputState(name, helper.getDeviceIcon(active))
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