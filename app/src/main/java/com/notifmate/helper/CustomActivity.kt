package com.notifmate.helper

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.preference.PreferenceManager
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.notifmate.R
import com.notifmate.model.AudioModel
import com.notifmate.model.AudioStateModel
import com.notifmate.model.NotificationModel
import com.notifmate.utils.DataManager
import java.io.ByteArrayOutputStream


open class CustomActivity : AppCompatActivity() {
    lateinit var savedInstance: Bundle
    lateinit var THIS: CustomActivity
    var connectThread: ConnectThread? = null
    var acceptThread: AcceptThread? = null
    val TAG = "NOTIFMATE_TAG"
    lateinit var progressDialog: AlertDialog


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        THIS = this
        if (savedInstanceState != null) {
            savedInstance = savedInstanceState
        }
        applyTheme()
        Log.d("MY_DEBUG", "CUSTOM_ACT ON_CREATE")
    }

    fun resetThread() {
        if (connectThread != null && connectThread!!.isAlive) {
            connectThread = null
        }
        if (acceptThread?.isAlive == true) {
            acceptThread = null
        }

    }

    fun showLoader(context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.setView(R.layout.loader)
        progressDialog = builder.create()
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        progressDialog.show()
    }

    fun stopLoader() {
        try {
            if (progressDialog.isShowing) {
                progressDialog.cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopLoader: ", e)
        }
    }


    // Function to apply the theme based on the saved preference
    private fun applyTheme() {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val selectedTheme = preferences.getString("theme", "system")
        Log.d("MY_DEBUG", "applyTheme applyTheme")
        when (selectedTheme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun getCurrentTheme(): String {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val selectedTheme = preferences.getString("theme", "system")
        return selectedTheme ?: "system"
    }

    fun getCurrentMusic(): String {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val selectedMusic = preferences.getString("music", "remote")
        return selectedMusic ?: "remote"
    }

    fun getCurrentNoti(): String {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val selectedMusic = preferences.getString("noti", "remote_local")
        return selectedMusic ?: "remote_local"
    }

    fun getCurrentOverlay(): String {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val selectedOverlay = preferences.getString("overlay", "none")
        return selectedOverlay ?: "none"
    }

    fun saveThemePreference(theme: String) {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putString("theme", theme)
        editor.apply()
        applyTheme()
        Log.d("MY_DEBUG", "CUSTOM_ACT saveThemePreference")
    }

    fun saveMusicPreference(music: String) {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putString("music", music)
        editor.apply()
    }

    fun saveNotiPreference(noti: String) {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putString("noti", noti)
        editor.apply()
    }

    fun saveOverlayPreference(overlay: String) {
        Log.e("MY_DEBUG", "SAVE_OVERLAY_PREFERENCE")
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putString("overlay", overlay)
        editor.apply()
    }

    fun getMusicPreference(): String? {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        return preferences.getString("music", "remote")  // Returns null if no value is found
    }

    fun getNotiPreference(): String? {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        return preferences.getString("noti", "remote_local")  // Returns null if no value is found
    }

    fun getOverlayPreference(): String? {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        return preferences.getString("overlay", "none")  // Returns null if no value is found
    }

    fun getDeviceNamePreference(): String {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val deviceName = preferences.getString("deviceName", "")
        return deviceName ?: ""
    }

    fun saveAppScreenPreference(appScreen: String) {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putString("appScreen", appScreen)
        editor.apply()
    }

    fun saveLastConnectedDevice(device: BluetoothDevice?) {
        val sharedPreferences = getSharedPreferences("NotifMatePrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        if (device == null) {
            editor.remove("lastConnectedDeviceAddress")
        } else {
            editor.putString("lastConnectedDeviceAddress", device.address)
        }
        editor.apply()
    }

    fun getLastConnectedDeviceAddress(): String? {
        val sharedPreferences = getSharedPreferences("NotifMatePrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("lastConnectedDeviceAddress", null)
    }

    fun saveDeviceNamePreference(deviceName: String) {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putString("deviceName", deviceName)
        editor.apply()
    }

    fun getAppListPreference(): MutableSet<String> {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val appListString = preferences.getString("appList", null)
        return appListString?.split(",")?.toMutableSet() ?: mutableSetOf()
    }

    fun saveAppListPreference(appList: MutableSet<String>) {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val editor = preferences.edit()
        val appListString = appList.joinToString(",") // Convert the set to a comma-separated string
        editor.putString("appList", appListString)
        editor.apply()
    }

    companion object {
        fun saveLastNotificationPckgName(LastNotificationPckgName: String, context: Context) {
            val preferences = context.getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
            val editor = preferences.edit()
            editor.putString("LastNotificationPckgName", LastNotificationPckgName)
            editor.apply()
        }
    }

    fun getLastNotificationPckgName(): String {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val LastNotificationPckgName = preferences.getString("LastNotificationPckgName", "")
        return LastNotificationPckgName ?: ""
    }

    fun connectDevice(bluetoothAdapter: BluetoothAdapter, device: BluetoothDevice) {
        val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    1 -> {}
                }
            }
        }

        if (connectThread != null) {
            connectThread = null
        }

        connectThread = ConnectThread(bluetoothAdapter, device, this, mHandler)
        connectThread?.start()

        val inf = IntentFilter()
        inf.addAction("ACTION_WRITE_OBJ")
        inf.addAction("ACTION_CLOSE")
        inf.addAction("ACTION_WRITE_APP")
        inf.addAction("ACTION_WRITE_NOTI")
        inf.addAction("ACTION_WRITE_NOTI_NEW")
        inf.addAction("ACTION_WRITE_AUDIO")
        inf.addAction("ACTION_WRITE_PLAYBACK_STATE")
        inf.addAction("ACTION_WRITE_")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(connectBrod, inf, RECEIVER_EXPORTED)
        } else {
            registerReceiver(connectBrod, inf)
        }
    }

    fun connectReciverDevice(bluetoothAdapter: BluetoothAdapter) {
        Log.d("MY_DEBUG", "connectReciverDevice REGISTER:")
        val mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    1 -> {
                        val notiData: ArrayList<NotificationModel> =
                            msg.obj as ArrayList<NotificationModel>
                        val intent = Intent("NOTI_DATA")
                        //Log.d("MY_DEBUG", "connectReciverDevice: $notiData")
                        intent.putExtra("notiData", notiData)
                        val overlayPref = getOverlayPreference()
                        Log.d("MY_DEBUG", "overlayPref: $overlayPref")

                        sendBroadcast(intent)

//                        val musicData: ArrayList<AudioPlayState> =
//                            msg.obj as ArrayList<AudioPlayState>
//                        val intent1 = Intent("MUSIC_DATA")
//                        intent1.putExtra("musicData", musicData)
//                        sendBroadcast(intent1)

                    }

                    2 -> {
                        val receivedStringElement: String = msg.obj as String
                        // Handle MutableSet<String> element here
                        // For example, you can send it to the UI activity
                        val intent = Intent("App_List")
                        intent.putExtra("appList", receivedStringElement)
                        sendBroadcast(intent)
                    }

                    3 -> {
                        val receivedString: String = msg.obj as String
                        val intent = Intent("Device_Name")
                        intent.putExtra("deviceName", receivedString)
                        sendBroadcast(intent)
                    }

                    4 -> {
                        val notiData: NotificationModel = msg.obj as NotificationModel
                        Log.w(TAG, "recieve noti data :$notiData")
                        val intent = Intent("NOTI_DATA_NEW")
                        intent.putExtra("notiData", notiData)
                        sendBroadcast(intent)
                    }

                    5 -> {
                        val audioData: AudioModel = msg.obj as AudioModel
                        Log.w("MY_DEBUG", "CUSTOM_ACT connectReciverDevice 5 audioData")
                        val intent = Intent("AUDIO_DATA")
                        intent.putExtra("audioData", audioData)
                        sendBroadcast(intent)
                    }

                    6 -> {
                        val audioState: AudioStateModel = msg.obj as AudioStateModel
                        Log.w("MY_DEBUG", "CUSTOM_ACT connectReciverDevice 6 audioState")
                        val intent = Intent("AUDIO_STATE")
                        intent.putExtra("audioState", audioState)
                        sendBroadcast(intent)
                    }

                }
            }
        }

        if (acceptThread != null) {
            acceptThread = null
        }

        acceptThread = AcceptThread(bluetoothAdapter, this, mHandler)
        acceptThread?.start()

        val inf = IntentFilter()
        inf.addAction("ACTION_CLOSE_PORT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(AcceptBrod, inf, RECEIVER_EXPORTED)
        } else {
            registerReceiver(AcceptBrod, inf)
        }
    }


    fun getConnectedDevice(): BluetoothDevice? {
        return try {
            connectThread!!.getConnectedDevice()
        } catch (e: Exception) {
            null
        }
    }

    fun bitmapToBase64(bitmap: Bitmap?, quality: Int = 70): String {
        if (bitmap == null) {
            return ""
        }
        val byteArrayOutputStream = ByteArrayOutputStream()
        // Compress the bitmap to JPEG format with the specified quality
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            null
        }
    }

    private val connectBrod: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "ACTION_WRITE_OBJ" -> {
                    try {
                        // val model: ArrayList<OrderItemData>? = intent.getSerializableExtra(Constants.EXTRA_DATA) as ArrayList<OrderItemData>?
                        val data: String = intent.getStringExtra("Data") as String

                        val response = connectThread?.sendData(data)

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                "ACTION_CLOSE" -> {
                    connectThread?.cancel()
                }

                "ACTION_WRITE_APP" -> {
                    //connectThread?.sendAppData(getAppListPreference())
                }

                "ACTION_WRITE_NOTI" -> {
                    //   val notiData = ArrayList<NotificationModel>()
                    Log.e("MY_DEBUG", "onReceive ACTION_WRITE_NOTI:")
//                    notiData.addAll(DataManager.instanse.notificationViewModel.notificationList)
//                    Log.e(TAG, "onReceive: $notiData" )
                    val response =
                        connectThread?.sendData(DataManager.instanse.notificationViewModel.notificationList.toList())
                    Log.e("MY_DEBUG", "onReceive ACTION_WRITE_NOTI: $response")
                    if (response == "error") {
                        //sendBroadcast(Intent("ACTION_CLOSE"))
                    }
                    //DataManager.instanse.notificationViewModel.clearLists()
                }

                "ACTION_WRITE_NOTI_NEW" -> {
                    Log.w("MY_DEBUG", "CUSTOM_ACTIVITY  ACTION_WRITE_NOTI_NEW:")
                    val response =
                        connectThread?.sendNotiData(DataManager.instanse.notificationViewModel.getCurrentNotification())
                }

                "ACTION_WRITE_AUDIO" -> {
                    Log.w("MY_DEBUG", "CUSTOM_ACT ACTION_WRITE_AUDIO: ")
                    val response =
                        connectThread?.sendAudioData(DataManager.instanse.notificationViewModel.getCurrentAudio())
                    if (response == "success") {

                    }
                }

                "ACTION_WRITE_PLAYBACK_STATE" -> {
                    Log.w("MY_DEBUG", "CUSTOM_ACT ACTION_WRITE_PLAYBACK_STATE:")
                    val response =
                        connectThread?.sendAudioStateData(DataManager.instanse.notificationViewModel.getCurrentAudioPlaybackState())
                }
            }

        }
    }


    private val AcceptBrod: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "ACTION_CLOSE_PORT") {
                Log.e("MY_DEBUG", " acceptThread?.cancel()")
                acceptThread?.cancel()
            }

        }
    }

}
