package com.notifmate.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.notifmate.ActivitySender
import com.notifmate.R
import com.notifmate.helper.CustomUtils
import com.notifmate.model.CordinateMessage
import com.notifmate.model.MediaMessage
import com.notifmate.model.MediaState
import com.notifmate.model.NotifMessage
import com.notifmate.utils.BluetoothSender
import org.json.JSONObject
import java.io.OutputStream
import java.util.UUID

class NotifMateNotificationHandlerService : Service() {
    
    var logTag = "MYDEBUG NNHS"

    var lastNotifPackage : String = ""
    var lastNotifTitle : String = ""
    var lastNotifText : String = ""
    var lastNotifSource : String = ""

    var lastMusicTitle : String = "Unknown Title"
    var lastMusicArtist : String = "Unknown Artist"
    var lastMusicArtBase64 : String = ""
    var lastMusicState : String = "Unknown State"
    var lastMusicSource : String = ""

    var receiverAddress : String = ""
    var activitySource : String = ""

    var lastLat : Double = 0.0
    var lastLon : Double = 0.0
    var lastPlaceName : String = ""

    private lateinit var excludedApps: MutableSet<String>

    companion object {
        private const val CHANNEL_ID = "ForegroundServiceChannel"
    }

    private val gson = Gson()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.e(logTag, "NotifMate Sender service Created")

        // Register global BroadcastReceiver
        var filter = IntentFilter().apply {
            addAction("com.notifmate.NOTIFICATION_RECEIVED")
            addAction("com.notifmate.MEDIA_UPDATED")
            addAction("com.notifmate.MEDIA_STATE_CHANGED")
            addAction("com.notifmate.COORDINATES_SHARED")
        }
        registerReceiver(notificationReceiver, filter, Context.RECEIVER_EXPORTED)

        requestMediaInfo()

        updateReceiverAddress()

        // Register BroadcastReceiver to listen for media info requests
        filter = IntentFilter().apply {
            addAction("com.notifmate.UPDATE_RECEIVER_ADDRESS")
            addAction("com.notifmate.UPDATE_EXCLUDED_APP_LIST")
        }
        registerReceiver(updateSenderServiceInfos, filter, Context.RECEIVER_EXPORTED)

        excludedApps = CustomUtils.getExcludedApps(applicationContext)

        BluetoothSender.clearQueue()

    }

    private val updateSenderServiceInfos = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i(logTag, "Received in updateSenderServiceInfos: ${intent?.action}")
            when (intent?.action) {
                "com.notifmate.UPDATE_RECEIVER_ADDRESS" -> {
                    updateReceiverAddress()
                }
                "com.notifmate.UPDATE_EXCLUDED_APP_LIST" -> {
                    updateExcludedAppList()
                }
            }
        }
    }

    private fun updateExcludedAppList(){
        excludedApps = CustomUtils.getExcludedApps(applicationContext)
    }

    private fun updateReceiverAddress(){
        receiverAddress = CustomUtils.getReceiverAddressPreference(this)
        BluetoothSender.setReceiver(receiverAddress)
        Log.i(logTag, "updateReceiverAddress: $receiverAddress")
    }

    private fun requestMediaInfo() {
        val intent = Intent("com.notifmate.REQUEST_MEDIA_INFO")
        sendBroadcast(intent)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    // Global BroadcastReceiver to handle notifications and media updates
    private val notificationReceiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(context: Context?, intent: Intent?) {
            //Log.i(logTag, "Received in NNHS: ${intent?.action}")

            when (intent?.action) {
                "com.notifmate.NOTIFICATION_RECEIVED" -> {
                    val packageName = intent.getStringExtra("packageName") ?: "Unknown App"
                    var transformedPackageName = getAppName(packageName)
                    val notificationTitle = intent.getStringExtra("notificationTitle") ?: "No Content"
                    val notificationText = intent.getStringExtra("notificationText") ?: "No Content"
                    val notificationSource = intent.getStringExtra("source") ?: ""

                    if (!excludedApps.contains(packageName) &&(transformedPackageName != lastNotifPackage || notificationTitle != lastNotifTitle || notificationText != lastNotifText)){

                        if ((transformedPackageName == "WhatsApp" && notificationText.endsWith("nouveaux messages")) ||
                            (transformedPackageName == "Messenger" && notificationText == "Bulles de discussion activées")){
                            Log.i(logTag, "Notification Ignored: $packageName - $notificationTitle - $notificationText $notificationSource")
                        }else{
                            lastNotifPackage = transformedPackageName
                            lastNotifTitle = notificationTitle
                            lastNotifText = notificationText
                            lastNotifSource = notificationSource
                            Log.i(logTag, "Notification: $packageName - $notificationTitle - $notificationText $notificationSource")
                            sendNotificationData()
                        }
                    }

                }
                "com.notifmate.MEDIA_UPDATED" -> {
                    val title = intent.getStringExtra("title") ?: "Unknown Title"
                    val artist = intent.getStringExtra("artist") ?: "Unknown Album"
                    val artBitmap = intent.getStringExtra("artBitmap") ?: ""
                    val state = intent.getStringExtra("state") ?: "Unknown State"
                    val source = intent.getStringExtra("source") ?: ""

                    Log.i(logTag, "Media update rewceived in NNHS")

                    if (title != lastMusicTitle || artist != lastMusicArtist || artBitmap != lastMusicArtBase64 || state != lastMusicState){
                        lastMusicTitle = title
                        lastMusicArtist = artist
                        lastMusicArtBase64 = artBitmap
                        lastMusicState = state
                        lastMusicSource = source
                        Log.i(logTag, "Media Updated: $title - $artist - $state - $source")
                        sendMediaData()
                    }

                }
                "com.notifmate.MEDIA_STATE_CHANGED" -> {
                    val state = intent.getStringExtra("state") ?: "Unknown State"
                    val source = intent.getStringExtra("source") ?: ""
                    if (state != lastMusicState){
                        lastMusicState = state
                        lastMusicSource = source
                        sendMediaStateData()
                    }
                }
                "com.notifmate.COORDINATES_SHARED" -> {
                    val lat = intent.getDoubleExtra("lat", 0.0)
                    val lon = intent.getDoubleExtra("lon", 0.0)
                    val name = intent.getStringExtra("name") ?: ""
                    if (lat != 0.0 && lon != 0.0){
                        lastLat = lat
                        lastLon = lon
                        lastPlaceName = name
                        Log.d(logTag, "COORDINATES_SHARED -> $lat $lon $name")
                        sendCoordinatesDataBLE()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, ActivitySender::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotifMate Sender is running")
            .setSmallIcon(R.drawable.splash_orange) // Use a built-in icon
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        val callerActivity = intent?.getStringExtra("caller_activity")
        activitySource = callerActivity.toString()
        Log.d(logTag, "Started by: $callerActivity")

        return START_STICKY
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendNotificationData(){
        if (activitySource == "ActivitySender"){
            sendNotificationBLE()
        }else{
            sendNotificationBroadcast()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMediaData(){
        if (activitySource == "ActivitySender"){
            sendMediaBLE()
        }else{
            sendMediaBroadcast()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMediaStateData(){
        if (activitySource == "ActivitySender"){
            sendMediaStateBLE()
        }else{
            sendMediaStateBroadcast()
        }
    }

    private fun sendNotificationBroadcast(){
        val intent = Intent("com.notifmate.UPDATE_NOTIFICATION_RECEIVER")
        intent.putExtra("packageName", lastNotifPackage)
        intent.putExtra("notificationTitle", lastNotifTitle)
        intent.putExtra("notificationText", lastNotifText)
        intent.putExtra("source", lastNotifSource)
        sendBroadcast(intent)
        //Log.d(logTag, "sendNotificationBroadcast: $intent")
    }

    private fun sendMediaBroadcast(){
        val intent = Intent("com.notifmate.UPDATE_MEDIA_RECEIVER")
        intent.putExtra("title", lastMusicTitle)
        intent.putExtra("artist", lastMusicArtist)
        intent.putExtra("artBitmap", lastMusicArtBase64)
        intent.putExtra("state", lastMusicState)
        intent.putExtra("source", lastMusicSource)
        sendBroadcast(intent)
        Log.d(logTag, "sendMediaBroadcast: $intent")
    }
    private fun sendMediaStateBroadcast(){
        val intent = Intent("com.notifmate.UPDATE_MEDIA_STATE_RECEIVER")
        intent.putExtra("state", lastMusicState)
        intent.putExtra("source", lastMusicSource)
        sendBroadcast(intent)
        Log.d(logTag, "sendMediaStateBroadcast: $intent")
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendNotificationBLE() {
        val notif = NotifMessage(
            packageName = lastNotifPackage,
            title = lastNotifTitle,
            text = lastNotifText
        )
        val json = gson.toJson(notif) + "\n"
        //sendBluetoothData(json)
        BluetoothSender.send(json, this)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendMediaBLE() {
        val media = MediaMessage(
            title = lastMusicTitle,
            artist = lastMusicArtist,
            state = lastMusicState,
            base64Image = lastMusicArtBase64
        )
        val json = gson.toJson(media) + "\n"
        Log.i(logTag, "sendMediaBLE - ${media.title} - ${media.artist}")
        //sendBluetoothData(json)
        BluetoothSender.send(json, this)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendMediaStateBLE() {
        val media = MediaState(
            state = lastMusicState,
        )
        val json = gson.toJson(media) + "\n"
        Log.i(logTag, "sendMediaStateBLE - ${media.state}")
        //sendBluetoothData(json)
        BluetoothSender.send(json, this)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendCoordinatesDataBLE() {
        val coordinates = CordinateMessage(
            lat = lastLat,
            lon = lastLon,
            name = lastPlaceName,
        )
        val json = gson.toJson(coordinates) + "\n"
        Log.i(logTag, "sendCoordinatesDataBLE - $json")
        //sendBluetoothData(json)
        BluetoothSender.send(json, this)
    }



    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendBluetoothData(message: String) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (receiverAddress.isNullOrEmpty() || !BluetoothAdapter.checkBluetoothAddress(receiverAddress)) {
            Log.e(logTag, "Invalid Bluetooth Address: $receiverAddress")
            return
        }

        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(receiverAddress)
        val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard UUID for Bluetooth SPP

        try {
            val socket: BluetoothSocket? = device?.createRfcommSocketToServiceRecord(uuid)
            socket?.connect()
            val outputStream: OutputStream? = socket?.outputStream
            outputStream?.write(message.toByteArray())
            outputStream?.flush()

            //Log.d(logTag, "Bluetooth Data sent: $message")

            Thread.sleep(200) // Small delay to ensure message is fully read
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateSenderServiceInfos) // Unregister global receiver
        unregisterReceiver(notificationReceiver) // Unregister global receiver
        Log.d(logTag, "Global BroadcastReceiver Unregistered")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}