package com.notifmate.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.notifmate.ActivityReceiver
import com.notifmate.ActivitySender
import com.notifmate.R
import com.notifmate.helper.CustomUtils
import com.notifmate.model.CordinateMessage
import com.notifmate.model.MediaMessage
import com.notifmate.model.MediaState
import com.notifmate.model.NotifMessage
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class BluetoothReceiverService : Service() {

    var logTag = "MYDEBUG BRS"

    var activitySource : String = ""

    private val SERVICE_NAME = "BluetoothService"
    private val SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SPP UUID
    private var serverSocket: BluetoothServerSocket? = null

    var lastMusicTitle : String = "Unknown Title"
    var lastMusicArtist : String = "Unknown Artist"
    var lastMusicArtBase64 : String = ""
    var lastMusicState : String = "Unknown State"

    var lastNotificationPackageName : String = ""
    var lastNotificationTitle : String = ""
    var lastNotificationText : String = ""

    var lastLat : Double = 0.0
    var lastLon : Double = 0.0
    var lastPlaceName : String = ""

    private val gson = Gson()

    private var serverThread: Thread? = null
    private val clientThreads = mutableListOf<Thread>()


    companion object {
        private const val CHANNEL_ID = "ForegroundServiceChannel"
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        Log.e(logTag, "NotifMate Receiver service Created")

        Log.d(logTag, "Starting Bluetooth receiver thread")
        startBluetoothServer()
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startBluetoothServer() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Log.e(logTag, "Bluetooth not supported on this device")
            return
        }

        serverThread = Thread {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                Log.d(logTag, "🔵 Waiting for Bluetooth connections...")

                while (!Thread.currentThread().isInterrupted) {
                    val socket: BluetoothSocket? = try {
                        serverSocket?.accept()
                    } catch (e: IOException) {
                        if (Thread.currentThread().isInterrupted) {
                            Log.d(logTag, "🔌 Server thread interrupted, stopping accept loop")
                        } else {
                            Log.e(logTag, "❌ Error while accepting socket: ${e.message}")
                        }
                        break
                    }

                    if (socket != null) {
                        Log.d(logTag, "✅ Client connected!")
                        val clientThread = Thread {
                            handleClient(socket)
                        }
                        clientThreads.add(clientThread)
                        clientThread.start()
                    }
                }
            } catch (e: Exception) {
                Log.e(logTag, "❌ Error in Bluetooth server: ${e.message}")
                e.printStackTrace()
            } finally {
                try {
                    serverSocket?.close()
                    Log.d(logTag, "🛑 Server socket closed.")
                } catch (e: IOException) {
                    Log.e(logTag, "⚠️ Error closing server socket: ${e.message}")
                }
            }
        }

        serverThread?.start()
    }

    private fun handleClient(socket: BluetoothSocket) {
        try {
            val inputStream = socket.inputStream
            val buffer = ByteArray(4096)
            val stringBuilder = StringBuilder()

            while (socket.isConnected) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) {
                    // Normal disconnect
                    Log.i(logTag, "Client disconnected normally (read returned -1)")
                    break
                }

                val chunk = String(buffer, 0, bytesRead)
                stringBuilder.append(chunk)

                if (chunk.contains("\n")) {
                    val messages = stringBuilder.toString().split("\n")
                    for (i in 0 until messages.size - 1) {
                        val msg = messages[i].trim()
                        if (msg.isNotEmpty()) {
                            processReceivedData(msg)
                        }
                    }
                    stringBuilder.clear()
                    stringBuilder.append(messages.last())
                }
            }

        } catch (e: IOException) {
            //Log.e(logTag, "Unexpected IO error while reading from socket", e)
        } finally {
            try {
                socket.close()
                Log.d(logTag, "Socket closed.")
            } catch (e: IOException) {
                Log.e(logTag, "Error closing socket", e)
            }
        }
    }



    private fun processReceivedData(message: String) {
        Log.e(logTag, "processReceivedData",)
        try {
            val json = gson.fromJson(message, Map::class.java)
            val type = json["type"] as? String ?: return

            when (type) {
                "NOTIF" -> {
                    val notif = gson.fromJson(message, NotifMessage::class.java)

                    if (lastNotificationPackageName != notif.packageName || lastNotificationTitle != notif.title || lastNotificationText != notif.text) {
                        lastNotificationPackageName = notif.packageName
                        lastNotificationTitle = notif.title
                        lastNotificationText = notif.text

                        val intent = Intent("com.notifmate.NOTIFICATION_RECEIVED").apply {
                            putExtra("packageName", notif.packageName)
                            putExtra("notificationTitle", notif.title)
                            putExtra("notificationText", notif.text)
                            putExtra("source", "remote")
                        }
                        sendBroadcast(intent)
                    }
                }

                "MEDIA" -> {
                    val media = gson.fromJson(message, MediaMessage::class.java)

                    if (lastMusicTitle != media.title || lastMusicArtist != media.artist || lastMusicState != media.state || lastMusicArtBase64 != media.base64Image) {
                        lastMusicTitle = media.title
                        lastMusicArtist = media.artist
                        lastMusicState = media.state
                        lastMusicArtBase64 = media.base64Image

                        Log.d(logTag, "Received Media: Title: ${media.title}, Artist: ${media.artist}, State: ${media.state}")

                        val intent = Intent("com.notifmate.MEDIA_UPDATED").apply {
                            putExtra("title", media.title)
                            putExtra("artist", media.artist)
                            putExtra("artBitmap", media.base64Image)
                            putExtra("state", media.state)
                            putExtra("source", "remote")
                        }
                        sendBroadcast(intent)
                    }
                }
                "MEDIA_STATE" -> {
                    val media = gson.fromJson(message, MediaState::class.java)

                    if (lastMusicState != media.state) {
                        lastMusicState = media.state

                        Log.d(logTag, "Received State: ${media.state}")

                        val intent = Intent("com.notifmate.MEDIA_STATE_CHANGED").apply {
                            putExtra("state", media.state)
                            putExtra("source", "remote")
                        }
                        sendBroadcast(intent)
                    }
                }
                "COORDS" -> {
                    val coords = gson.fromJson(message, CordinateMessage::class.java)

                    Log.d(logTag, "Received Coordinates: lat:${coords.lat} - lon:${coords.lon} - name:${coords.name}")

                    lastLat = coords.lat
                    lastLon = coords.lon
                    lastPlaceName = coords.name

                    val intent = Intent("com.notifmate.COORDINATES_RECEIVED").apply {
                        putExtra("lat", coords.lat)
                        putExtra("lon", coords.lon)
                        putExtra("name", coords.name)
                    }
                    sendBroadcast(intent)

                }

                else -> Log.e(logTag, "Unknown message type in JSON: $type")
            }

        } catch (e: Exception) {
            Log.e(logTag, "Failed to parse JSON message: $message", e)
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, ActivityReceiver::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotifMate Receiver is running")
            .setSmallIcon(R.drawable.splash_orange) // Use a built-in icon
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        val callerActivity = intent?.getStringExtra("caller_activity")
        activitySource = callerActivity.toString()
        Log.d(logTag, "Started by: $callerActivity")

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e(logTag, "⚠️ Error closing server socket on destroy", e)
        }

        serverThread?.interrupt()
        clientThreads.forEach { it.interrupt() }

        Log.d(logTag, "💥 Bluetooth server and client threads interrupted and cleaned up")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }
}