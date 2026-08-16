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
import kotlinx.coroutines.channels.Channel
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
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
    private val externalScope: CoroutineScope,
    private val onCommandReceived: (String) -> Unit
) {
    private val TAG = "FossWearApi"

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private val gson = Gson()
    private var internalJob = SupervisorJob(externalScope.coroutineContext[Job])
    private val internalScope = CoroutineScope(Dispatchers.IO + internalJob)

    private var serverSocket: BluetoothServerSocket? = null
    private var connectedSocket: BluetoothSocket? = null
    private var writer: PrintWriter? = null
    private val sendChannel = Channel<String>(Channel.UNLIMITED)

    init {
        startSendLoop()
        startServer()
    }

    private fun isApiEnabled() = PrefsManager.getFossWearEnabled()

    @SuppressLint("MissingPermission")
    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun startServer() {
        if (!isApiEnabled()) return
        cleanupActiveConnection()

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w(TAG, "Bluetooth не готов")
            return
        }

        if (!checkPermissions()) {
            Log.e(TAG, "Нет прав BLUETOOTH_CONNECT")
            return
        }

        internalScope.launch {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("PargeliumWatchService", MY_APP_UUID)
                Log.d(TAG, "Жду подключения на RFCOMM...")

                val socket = serverSocket?.accept()
                serverSocket?.close()
                serverSocket = null

                if (socket != null && isActive) {
                    manageConnectedSocket(socket)
                } else {
                    socket?.close()
                }
            } catch (e: IOException) {
                if (isActive) Log.e(TAG, "Ошибка accept: ${e.message}")
            }
        }
    }

    private fun startSendLoop() {
        internalScope.launch {
            for (message in sendChannel) {
                val currentWriter = writer
                if (currentWriter != null && isActive) {
                    try {
                        currentWriter.println(message)
                        if (currentWriter.checkError()) {
                            Log.e(TAG, "Ошибка записи в PrintWriter (checkError)")
                            handleConnectionLost()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка отправки: ${e.message}")
                        handleConnectionLost()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun manageConnectedSocket(socket: BluetoothSocket) {
        connectedSocket = socket
        try {
            writer = PrintWriter(socket.outputStream, true)
            Log.d(TAG, "Устройство подключено: ${socket.remoteDevice.name}")
        } catch (e: IOException) {
            Log.e(TAG, "Не удалось получить стримы сокета", e)
            handleConnectionLost()
            return
        }

        internalScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) continue
                    handleIncomingMessage(line)
                }
            } catch (e: IOException) {
                Log.d(TAG, "Соединение разорвано при чтении: ${e.message}")
            } finally {
                handleConnectionLost()
            }
        }
    }

    private fun handleIncomingMessage(json: String) {
        internalScope.launch(Dispatchers.Default) {
            try {
                if (json.startsWith("{") && json.contains("\"type\"")) {
                    val packet = gson.fromJson(json, FossPacket::class.java)
                    if (packet.type == "CMD" && isActive) {
                        withContext(Dispatchers.Main) {
                            if (isActive) onCommandReceived(packet.payload)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка парсинга входящего JSON", e)
            }
        }
    }

    private fun sendPacket(type: String, dataObj: Any) {
        // Проверяем статус джоба явно, так как мы не внутри корутины
        if (!isApiEnabled() || connectedSocket == null || !internalJob.isActive) return

        internalScope.launch(Dispatchers.Default) {
            try {
                val payload = gson.toJson(dataObj)
                val packet = FossPacket(type, payload)
                val jsonString = gson.toJson(packet)
                sendChannel.send(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка подготовки пакета", e)
            }
        }
    }

    private fun handleConnectionLost() {
        // Проверяем статус джоба явно, так как мы не внутри корутины
        if (!internalJob.isActive) return
        Log.d(TAG, "Обработка потери соединения, перезапуск сервера...")
        cleanupActiveConnection()
        startServer()
    }

    fun updatePlaybackState(title: String, artist: String, isPlaying: Boolean, pos: Long, dur: Long, color: Int) {
        val state = PlayerStateData(title, artist, isPlaying, pos, dur, color)
        sendPacket("STATE", state)
    }

    fun syncTheme(mode: Int) {
        sendPacket("THEME", mapOf("theme_mode" to mode))
    }

    fun syncLibrary(albums: List<AlbumModel>) {
        val simpleList = albums.map {
            mapOf("id" to it.id, "title" to it.title, "count" to it.tracks.size)
        }
        sendPacket("LIBRARY", simpleList)
    }

    private fun cleanupActiveConnection() {
        try {
            writer?.close()
            connectedSocket?.close()
        } catch (e: Exception) {}
        writer = null
        connectedSocket = null
    }

    fun release() {
        Log.d(TAG, "Релиз менеджера")
        internalJob.cancel()
        sendChannel.close()
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        cleanupActiveConnection()
        serverSocket = null
    }
}