package com.alcint.pargelium

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioDeviceInfo

class AudioDeviceHelper(private val context: Context) {

    private val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val nameKeywordsMap = listOf(
        listOf("quest", "pico", "vive", "index", "vr") to R.drawable.ic_play_vr,
        listOf("airpods", "buds", "earbuds", "freebuds", "dots", "tws", "tune", "wf-") to R.drawable.ic_play_earbuds,
        listOf("wh-", "major", "soundcore life", "anc", "quietcomfort", "headphones") to R.drawable.ic_play_headphone,
        listOf("flip", "charge", "boombox", "go ", "soundlink", "speaker", "pill", "megaboom") to R.drawable.ic_play_speaker,
        listOf("car", "bmw", "audi", "toyota", "vw", "hyundai", "kia", "media nav", "sync", "bt_car") to R.drawable.ic_play_car
    )

    private val macAddressOverrides = mapOf<String, Int>(
        // "AA:BB:CC:DD:EE:FF" to R.drawable.ic_play_earbuds
    )

    @SuppressLint("MissingPermission")
    fun getDeviceIcon(deviceInfo: AudioDeviceInfo): Int {
        return when (deviceInfo.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                if (isTablet()) R.drawable.ic_play_tablet else R.drawable.ic_play_mobile
            }
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> R.drawable.ic_play_headphone
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> R.drawable.ic_play_headset
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> R.drawable.ic_play_usb
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC -> R.drawable.ic_play_tv
            AudioDeviceInfo.TYPE_HEARING_AID -> R.drawable.ic_play_hearing_aid
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> resolveBluetoothDeviceIcon(deviceInfo)
            else -> R.drawable.ic_play_mobile
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveBluetoothDeviceIcon(deviceInfo: AudioDeviceInfo): Int {
        val address = deviceInfo.address
        if (address != null && macAddressOverrides.containsKey(address)) {
            return macAddressOverrides.getValue(address)
        }

        val btDevice = try {
            bluetoothAdapter?.bondedDevices?.firstOrNull { it.address == address }
        } catch (e: SecurityException) {
            null
        }

        val deviceName = btDevice?.name ?: deviceInfo.productName?.toString() ?: ""

        if (deviceName.isNotBlank()) {
            val lowerName = deviceName.lowercase()
            for ((keywords, icon) in nameKeywordsMap) {
                if (keywords.any { lowerName.contains(it) }) {
                    return icon
                }
            }
        }

        val btClass = btDevice?.bluetoothClass?.deviceClass

        return when (btClass) {
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> R.drawable.ic_play_headphone
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> R.drawable.ic_play_earbuds
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> R.drawable.ic_play_car
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX,
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> R.drawable.ic_play_speaker
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> R.drawable.ic_play_headset
            else -> R.drawable.ic_play_bletooth
        }
    }

    private fun isTablet(): Boolean {
        return (context.resources.configuration.screenLayout and
                Configuration.SCREENLAYOUT_SIZE_MASK) >=
                Configuration.SCREENLAYOUT_SIZE_LARGE
    }
}