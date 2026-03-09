package com.alcint.pargelium

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

val APP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

data class Packet(val type: String, val payload: String)
data class PlayerState(
    val title: String = "Нет соединения",
    val artist: String = "Жду телефон...",
    val isPlaying: Boolean = false,
    val duration: Long = 1L,
    val position: Long = 0L,
    val primaryColor: Int = android.graphics.Color.DKGRAY
)
data class LibraryItem(val id: Long, val title: String, val count: Int)

class WearClient {
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState = _playerState.asStateFlow()

    private val _library = MutableStateFlow<List<LibraryItem>>(emptyList())
    val library = _library.asStateFlow()

    private val gson = Gson()
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("MissingPermission")
    fun connect(context: Context) {
        scope.launch {
            // Проверка прав
            if (Build.VERSION.SDK_INT >= 31) {
                val hasConnect = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                if (!hasConnect) {
                    Log.e("WearClient", "Нет прав BLUETOOTH_CONNECT")
                    return@launch
                }
            }

            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) return@launch

            val devices = adapter.bondedDevices
            if (devices.isNullOrEmpty()) {
                Log.e("WearClient", "Нет сопряженных устройств")
                return@launch
            }

            Log.d("WearClient", "Поиск сервера на ${devices.size} устройствах...")

            for (device in devices) {
                try {
                    val tmpSocket = device.createRfcommSocketToServiceRecord(APP_UUID)
                    tmpSocket.connect()

                    socket = tmpSocket
                    outputStream = socket?.outputStream
                    Log.d("WearClient", "Успешно подключено к ${device.name}")

                    listenForData(tmpSocket.inputStream)
                    break
                } catch (e: Exception) {
                    try { socket?.close() } catch (closeException: Exception) {}
                }
            }
        }
    }

    private suspend fun listenForData(inputStream: InputStream) {
        val buffer = ByteArray(4096)
        while (true) {
            try {
                val bytes = inputStream.read(buffer)
                if (bytes == -1) break
                val json = String(buffer, 0, bytes)
                try {
                    if (json.contains("type")) {
                        val packet = gson.fromJson(json, Packet::class.java)
                        if (packet.type == "STATE") {
                            val state = gson.fromJson(packet.payload, PlayerState::class.java)
                            _playerState.value = state
                        } else if (packet.type == "LIBRARY") {
                            val list = gson.fromJson(packet.payload, Array<LibraryItem>::class.java).toList()
                            _library.value = list
                        }
                    }
                } catch (e: Exception) { }
            } catch (e: Exception) { break }
        }
    }

    fun sendCommand(cmd: String) {
        scope.launch(Dispatchers.IO) {
            try {
                if (outputStream == null) return@launch
                val packet = Packet("CMD", cmd)
                outputStream?.write(gson.toJson(packet).toByteArray())
            } catch (e: Exception) { }
        }
    }
}