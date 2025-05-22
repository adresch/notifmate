package com.notifmate

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import com.notifmate.adapter.NotificationAdapter
import com.notifmate.helper.CustomUtils
import com.notifmate.helper.OverlayHelper
import com.notifmate.model.NotificationItem
import com.notifmate.service.BluetoothReceiverService
import com.notifmate.service.NotifMateNotificationHandlerService
import com.notifmate.R
import com.notifmate.helper.BillingHelper
import com.notifmate.model.NotifMateActivity

class ActivityReceiver : NotifMateActivity() {

    var logTag : String = "MYDEBUG AR"

    var lastNotifPackage : String = ""
    var lastNotifTitle : String = ""
    var lastNotifText : String = ""
    var lastNotifSource : String = ""

    var lastMusicTitle : String = ""
    var lastMusicArtist : String = ""
    var lastMusicArtBase64 : String = ""
    var lastMusicState : String = ""
    var lastMusicSource : String = ""

    var currentNotiPref : String = ""
    var currentOverlayPref : String = ""
    var currentMusicPref : String = ""

    var lastLat : Double = 0.0
    var lastLon : Double = 0.0
    var lastPlaceName : String = ""

    private var overlayView: View? = null
    private lateinit var overlayHelper: OverlayHelper

    private lateinit var notificationAdapter: NotificationAdapter
    private val notificationList = mutableListOf<NotificationItem>()

    private lateinit var billingHelper: BillingHelper
    private var hasPurchasedMusicFeature = false

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e(logTag, "🌀 onCreate called")

        currentNotiPref = CustomUtils.getCurrentNoti(this)
        currentOverlayPref = CustomUtils.getCurrentOverlay(this)
        currentMusicPref = CustomUtils.getCurrentMusic(this)

        setContentView(R.layout.activity_receiver)

        val recyclerView = findViewById<RecyclerView>(R.id.recNoti)
        notificationAdapter = NotificationAdapter(notificationList)
        recyclerView.adapter = notificationAdapter


        val notifServiceIntent = Intent(this, NotifMateNotificationHandlerService::class.java)
        notifServiceIntent.putExtra("caller_activity", this::class.java.simpleName)
        ContextCompat.startForegroundService(this, notifServiceIntent)

        val bluetoothServiceIntent = Intent(this, BluetoothReceiverService::class.java)
        bluetoothServiceIntent.putExtra("caller_activity", this::class.java.simpleName)
        ContextCompat.startForegroundService(this, bluetoothServiceIntent)

        var filter = IntentFilter().apply {
            addAction("com.notifmate.UPDATE_NOTIFICATION_RECEIVER")
            addAction("com.notifmate.UPDATE_MEDIA_RECEIVER")
            addAction("com.notifmate.UPDATE_MEDIA_STATE_RECEIVER")
            addAction("com.notifmate.COORDINATES_RECEIVED")
        }
        registerReceiver(UpdateReceiverInterface, filter, Context.RECEIVER_EXPORTED)

        val settings = findViewById<ImageView>(R.id.settings)
        settings.setOnClickListener {
            val intent = Intent(this, ActivitySettingsReceiver::class.java)
            startActivity(intent)
        }

        val playPauseButton = findViewById<ImageView>(R.id.play_pause_button)
        playPauseButton.setOnClickListener {
            Log.i(logTag, "PlayPause Button")
        }

        overlayHelper = OverlayHelper(this)

        val buyMusicFeatureButton = findViewById<Button>(R.id.buyMusicFeature)

        // Initialize billing helper
        billingHelper = BillingHelper(this) { purchased ->
            Log.d(logTag, "billing helper $purchased")
            hasPurchasedMusicFeature = purchased
            runOnUiThread {
                updateMusicFeatureUI()
            }
        }

        // Always check for past purchases when the app starts
        billingHelper.startConnection {
            billingHelper.queryPurchases { purchased ->
                Log.e(logTag, purchased.toString())
                hasPurchasedMusicFeature = purchased
                runOnUiThread {
                    updateMusicFeatureUI()
                }
            }
        }


        buyMusicFeatureButton.setOnClickListener {
            billingHelper.launchPurchase(this)
        }
        val wasRestarted = savedInstanceState != null
        Log.e(logTag, "🌀 onCreate called. Was restarted? $wasRestarted")

        updateMusicFeatureUI()

    }

    private fun updateMusicFeatureUI() {
        val buyMusicFeatureButton = findViewById<Button>(R.id.buyMusicFeature)
        val musicPlayerLayout = findViewById<View>(R.id.player_layout)

        if (!isInstalledFromPlayStore() || hasPurchasedMusicFeature) {
        //if (hasPurchasedMusicFeature) {
            Log.e(logTag, "SHOW MUSIC")
            buyMusicFeatureButton.visibility = View.GONE
            musicPlayerLayout.visibility = View.VISIBLE
        } else {
            buyMusicFeatureButton.visibility = View.VISIBLE
            musicPlayerLayout.visibility = View.GONE
        }
        Log.d(logTag, "buyMusicFeatureButton: ${buyMusicFeatureButton.visibility}, musicPlayerLayout: ${musicPlayerLayout.visibility}")

    }



    private val UpdateReceiverInterface = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(context: Context?, intent: Intent?) {
            //Log.i(logTag, "Received in ActivityReceiver: ${intent?.action}")

            when (intent?.action) {
                "com.notifmate.UPDATE_NOTIFICATION_RECEIVER" -> {

                    val packageName = intent.getStringExtra("packageName") ?: getString(R.string.unknown_app)
                    val notificationTitle = intent.getStringExtra("notificationTitle") ?: ""
                    val notificationText = intent.getStringExtra("notificationText") ?: ""
                    val source = intent.getStringExtra("source") ?: getString(R.string.unknown_source)

                    if ((currentNotiPref == "remote" && source == "remote") || (currentNotiPref == "local" && source == "local") || currentNotiPref == "remote_local"){
                        if (packageName != lastNotifPackage || notificationTitle != lastNotifTitle || notificationText != lastNotifText){
                            lastNotifPackage = packageName
                            lastNotifTitle = notificationTitle
                            lastNotifText = notificationText
                            Log.d(logTag, "Notification: $packageName - $notificationTitle - $notificationText - $source")
                            val newNotification = NotificationItem(lastNotifPackage, lastNotifTitle, lastNotifText)

                            val isAppBackground = isAppInBackground(context)
                            if ((currentOverlayPref == "background" && isAppBackground) || currentOverlayPref == "always"){
                                //showOverlay(newNotification)
                                overlayHelper.showOverlay(newNotification)
                            }

                            Handler(Looper.getMainLooper()).post {
                                notificationAdapter.addItem(newNotification, findViewById(R.id.recNoti))
                            }

                        }
                    }

                }
                "com.notifmate.UPDATE_MEDIA_RECEIVER" -> {

                    val title = intent.getStringExtra("title") ?: getString(R.string.unknown_title)
                    val artist = intent.getStringExtra("artist") ?: getString(R.string.unknown_artist)
                    val artBitmap = intent.getStringExtra("artBitmap") ?: ""
                    val state = intent.getStringExtra("state") ?: getString(R.string.unknown_state)
                    val source = intent.getStringExtra("source") ?: getString(R.string.unknown_source)

                    if (title != lastMusicTitle || artist != lastMusicArtist || artBitmap != lastMusicArtBase64 || state != lastMusicState){
                        lastMusicTitle = title
                        lastMusicArtist = artist
                        lastMusicArtBase64 = artBitmap
                        lastMusicState = state
                        lastMusicSource = source
                        Log.d(logTag, "Media Updated: $title - $artist - $state - $source")

                        if (currentMusicPref == source){
                            updateMusic()
                        }
                    }

                }"com.notifmate.UPDATE_MEDIA_STATE_RECEIVER" -> {
                    val state = intent.getStringExtra("state") ?: getString(R.string.unknown_state)
                    val source = intent.getStringExtra("source") ?: getString(R.string.unknown_source)

                    if (state != lastMusicState){
                        lastMusicState = state
                        lastMusicSource = source
                        Log.d(logTag, "Media State Updated: $state - $source")

                        if (currentMusicPref == source){
                            updateMusic()
                        }
                    }
                }
                "com.notifmate.COORDINATES_RECEIVED" -> {
                    val lat = intent.getDoubleExtra("lat", 0.0)
                    val lon = intent.getDoubleExtra("lon", 0.0)
                    val name = intent.getStringExtra("name") ?: ""

                    if (lat != 0.0 && lon != 0.0) {
                        lastLat = lat
                        lastLon = lon
                        lastPlaceName = name

                        Log.d(logTag, "COORDINATES_RECEIVED -> $lat $lon $name")

                        openInNavigationApp(lat, lon, name)
                    }
                }
            }
        }
    }

    private fun openInNavigationApp(lat: Double, lon: Double, name: String?) {
        val label = name?.takeIf { it.isNotBlank() } ?: "Custom Pin"
        val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            setPackage(null) // Let the system pick any available map app
        }

        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            Toast.makeText(this, "No map app available to open location", Toast.LENGTH_SHORT).show()
        }
    }


    private fun updateMusic() {
        val musicName = findViewById<TextView>(R.id.musicName) ?: return
        val artistName = findViewById<TextView>(R.id.artistName) ?: return
        val musicArt = findViewById<ImageView>(R.id.musicArt) ?: return
        val playPauseButton = findViewById<ImageView>(R.id.play_pause_button) ?: return
        val typedValue = TypedValue()

        if (lastMusicState == "Paused") {
            theme.resolveAttribute(R.attr.playButton, typedValue, true)
        } else {
            theme.resolveAttribute(R.attr.pauseButton, typedValue, true)
        }
        playPauseButton.setImageResource(typedValue.resourceId)
        musicName.text = lastMusicTitle
        artistName.text = lastMusicArtist
        musicArt.setImageBitmap(CustomUtils.base64ToBitmap(lastMusicArtBase64))

    }

    private fun isAppInBackground(context: Context?): Boolean {
        val activityManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return true
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                appProcess.processName == context.packageName
            ) {
                return false
            }
        }
        return true
    }

    private fun isInstalledFromPlayStore(): Boolean {
        return try {
            val packageManager = packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+ (API 30)
                val installerInfo = packageManager.getInstallSourceInfo(packageName)
                installerInfo?.installingPackageName == "com.android.vending"
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName) == "com.android.vending"
            }
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun onResume() {
        super.onResume()
        currentNotiPref = CustomUtils.getCurrentNoti(this)
        currentOverlayPref = CustomUtils.getCurrentOverlay(this)
        currentMusicPref = CustomUtils.getCurrentMusic(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(logTag, "💥 onDestroy called")
        unregisterReceiver(UpdateReceiverInterface)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(logTag, "🔄 onConfigurationChanged: $newConfig")
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("lastNotifPackage", lastNotifPackage)
        outState.putString("lastNotifTitle", lastNotifTitle)
        outState.putString("lastNotifText", lastNotifText)
        outState.putString("lastNotifSource", lastNotifSource)
        outState.putString("lastMusicTitle", lastMusicTitle)
        outState.putString("lastMusicArtist", lastMusicArtist)
        outState.putString("lastMusicArtBase64", lastMusicArtBase64)
        outState.putString("lastMusicState", lastMusicState)
        outState.putString("lastMusicSource", lastMusicSource)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        lastNotifTitle = savedInstanceState.getString("lastNotifTitle") ?: ""
        lastNotifText = savedInstanceState.getString("lastNotifText") ?: ""
        lastMusicTitle = savedInstanceState.getString("lastMusicTitle") ?: ""
        lastMusicArtist = savedInstanceState.getString("lastMusicTitle") ?: ""
        lastMusicArtBase64= savedInstanceState.getString("lastMusicTitle") ?: ""
        lastMusicState = savedInstanceState.getString("lastMusicTitle") ?: ""
        if (lastMusicTitle != ""){
            updateMusic()
        }
    }



}
