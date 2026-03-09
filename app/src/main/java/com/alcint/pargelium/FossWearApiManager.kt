package com.alcint.pargelium

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

val MY_APP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

data class FossPacket(val type: String, val payload: String)

data class PlayerStateData(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val position: Long,
    val duration: Long,
    val primaryColor: Int
)

class FossWearApiManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCommandReceived: (String) -> Unit
) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var connectedSocket: BluetoothSocket? = null
    private val gson = Gson()

    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    init {
        startServer()
    }

    @SuppressLint("MissingPermission")
    fun startServer() {
        // 1. Проверяем настройку в PrefsManager
        if (!PrefsManager.getFossWearEnabled()) {
            Log.d("FossWearApi", "Сервис отключен в настройках. Останавливаемся.")
            release() // Убеждаемся, что всё закрыто
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter?.isEnabled!!) return

        if (Build.VERSION.SDK_INT >= 31) {
            val hasConnect = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!hasConnect) {
                Log.e("FossWearApi", "Нет прав BLUETOOTH_CONNECT")
                return
            }
        }

        if (serverSocket != null) return

        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("PargeliumWatchService", MY_APP_UUID)
                Log.d("FossWearApi", "Bluetooth сервер запущен, жду подключения...")

                val socket = serverSocket?.accept()

                if (socket != null) {
                    manageConnectedSocket(socket)
                    try { serverSocket?.close() } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                Log.e("FossWearApi", "Ошибка сервера: ${e.message}")
            }
        }
    }

    fun restart() {
        release()
        startServer()
    }

    private fun manageConnectedSocket(socket: BluetoothSocket) {
        connectedSocket = socket
        outputStream = socket.outputStream
        inputStream = socket.inputStream
        Log.d("FossWearApi", "Устройство подключено: ${socket.remoteDevice.name}")

        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            while (isActive) {
                try {
                    val bytes = inputStream?.read(buffer) ?: break
                    if (bytes == -1) break
                    val message = String(buffer, 0, bytes)
                    handleIncomingMessage(message)
                } catch (e: IOException) {
                    break
                }
            }
            try { connectedSocket?.close() } catch (e: Exception) {}
            startServer()
        }
    }

    private fun handleIncomingMessage(json: String) {
        try {
            if (json.contains("type")) {
                val packet = gson.fromJson(json, FossPacket::class.java)
                if (packet.type == "CMD") {
                    scope.launch(Dispatchers.Main) { onCommandReceived(packet.payload) }
                }
            }
        } catch (e: Exception) { }
    }

    private fun sendPacket(type: String, dataObj: Any) {
        if (!PrefsManager.getFossWearEnabled() || outputStream == null) return

        scope.launch(Dispatchers.IO) {
            try {
                val payload = gson.toJson(dataObj)
                val packet = FossPacket(type, payload)
                val jsonString = gson.toJson(packet)
                outputStream?.write(jsonString.toByteArray())
                outputStream?.flush()
            } catch (e: Exception) { }
        }
    }

    fun updatePlaybackState(
        title: String,
        artist: String,
        isPlaying: Boolean,
        pos: Long,
        dur: Long,
        color: Int
    ) {
        if (!PrefsManager.getFossWearEnabled()) return

        val state = PlayerStateData(title, artist, isPlaying, pos, dur, color)
        sendPacket("STATE", state)
    }

    fun syncTheme(mode: Int) {
        if (!PrefsManager.getFossWearEnabled()) return
        sendPacket("THEME", mapOf("theme_mode" to mode))
    }

    fun syncLibrary(albums: List<AlbumModel>) {
        if (!PrefsManager.getFossWearEnabled()) return

        val simpleList = albums.map {
            mapOf("id" to it.id, "title" to it.title, "count" to it.tracks.size)
        }
        sendPacket("LIBRARY", simpleList)
    }

    fun release() {
        try {
            serverSocket?.close()
            connectedSocket?.close()
            serverSocket = null
            connectedSocket = null
            outputStream = null
            inputStream = null
        } catch (e: Exception) {}
    }
}