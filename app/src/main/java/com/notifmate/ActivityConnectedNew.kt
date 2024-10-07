package com.notifmate

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.notifmate.databinding.ActivityConnectedNewBinding
import com.notifmate.helper.CustomActivity
import com.notifmate.helper.MusicController
import com.notifmate.helper.NotificationListener
import com.notifmate.helper.PhoneCallListener
import com.notifmate.helper.PhoneCallReceiver
import com.notifmate.model.AudioModel
import com.notifmate.model.AudioStateModel
import com.notifmate.model.NotificationModel
import com.notifmate.model.NotificationViewModel
import com.notifmate.utils.Constant.getCallerNameFromContact
import com.notifmate.utils.DataManager
import kotlinx.coroutines.launch
import java.util.Date

class ActivityConnectedNew : CustomActivity(), PhoneCallListener {
    var selectedApps: MutableSet<String> = mutableSetOf()
    private lateinit var notificationListenerIntent: Intent
    private lateinit var binding: ActivityConnectedNewBinding
    private lateinit var phoneCallReceiver: PhoneCallReceiver
    private lateinit var musicController: MusicController
    private lateinit var notificationViewModel: NotificationViewModel
    private var bluetoothDevice: BluetoothDevice? = null

    private var receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "CLIENT_CLOSE") {
                Log.e("ERROR CLIENT CLOSE", "CLIENT CLOSE")
                startHome()
               // onDestroy()

            }else if (intent?.action == "BLE_ERROR") {
                Log.e("MY_DEBUG", "BLE_ERROR")
                startHome()
               // onDestroy()
            }

            else if (intent?.action ==  BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                Log.e(TAG, "ACTION_ACL_DISCONNECTED")
//                startHome()
                sendBroadcast(Intent("ACTION_CLOSE"))
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MY_DEBUG", "ONDESTROY: ")
       // sendBroadcast(Intent("ACTION_CLOSE"))
        stopService(notificationListenerIntent)
        //NotificationListener.notificationLiveData.removeObservers(this)
    }

    private val notificationAccessLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                if (isNotificationAccessGranted().not()) {
                    Toast.makeText(
                        this,
                        "Notification access permission not granted",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    selectedApps = getAppListPreference()
                    notificationListenerIntent = Intent(this, NotificationListener::class.java)
                    notificationListenerIntent.putExtra("selectedApps", selectedApps.toTypedArray())
                    startNotificationListenerService()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectedNewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        phoneCallReceiver = PhoneCallReceiver()
        musicController = MusicController(this)
        musicController.initializeMediaSessionManager()

        phoneCallReceiver.setPhoneCallListener(this)
        registerPhoneReceiver()

        if (isNotificationAccessGranted().not()) {
            requestNotificationAccessPermission()
        } else {
            selectedApps = getAppListPreference()
            startNotificationListenerService()
        }

        registerIncomingCallReceiver()
        Log.d("MY_DEBUG", "ACT_CONECTED_NEW registerIncomingCallReceiver: ")

        clickEvent()

        notificationViewModel = ViewModelProvider(this)[NotificationViewModel::class.java]

        DataManager.instanse.notificationViewModel = notificationViewModel;

        initView()

        lifecycleScope.launch {
            NotificationListener.notificationLiveData.collect { notification ->
                Log.d("MY_DEBUG", "ACT_CONECTED_NEW NotificationListener: $notification")
                val currentNotif = notificationViewModel.getCurrentNotification()

                Log.d("MY_DEBUG", "ACT_CONECTED_NEW NotificationListenerCUrrent: $currentNotif")

                if (currentNotif == null || (currentNotif.title != notification.title || currentNotif.msg != notification.msg || currentNotif.packageName != notification.packageName)) {
                    notificationViewModel.changeCurrentNotification(notification)
                    Log.d("MY_DEBUG", "ACT_CONECTED_NEW ACTION_WRITE_NOTI_NEW")
                    sendBroadcast(Intent("ACTION_WRITE_NOTI_NEW"))
                }
            }
        }

        lifecycleScope.launch {
            musicController.combineLiveData().collect { combinedData ->
                val songName = combinedData.songName
                val artistName = combinedData.artistName
                val musicArt = combinedData.musicArt
                val playbackState: Int =
                    combinedData.playbackState?.state ?: PlaybackState.STATE_NONE

                val musicArtBase64 = bitmapToBase64(musicArt)
                val newAudio = AudioModel(
                    songName = songName ?: "",
                    artistName = artistName ?: "",
                    musicArtBase64 = musicArtBase64
                )

                val currentAudio = notificationViewModel.getCurrentAudio()

                if (currentAudio == null || newAudio.songName != currentAudio?.songName || newAudio.artistName != currentAudio?.artistName || newAudio.musicArtBase64 != currentAudio?.musicArtBase64){
                    Log.e("MY_DEBUG", "newAudio ${newAudio?.songName ?: "Unknown"} ${newAudio?.artistName ?: "Unknown"} ")
                    notificationViewModel.changeCurrentAudio(newAudio)
                    Log.d("MY_DEBUG", "ACT_CONECTED_NEW ACTION_WRITE_AUDIO")
                    sendBroadcast(Intent("ACTION_WRITE_AUDIO"))
                }

                val newPlayBackSate = AudioStateModel(
                    playBackState = playbackState
                )
                notificationViewModel.changeCurrentAudioPlaybackState(newPlayBackSate)
                Log.d("MY_DEBUG", "ACT_CONECTED_NEW ACTION_WRITE_PLAYBACK_STATE")
                sendBroadcast(Intent("ACTION_WRITE_PLAYBACK_STATE"))
            }
        }
    }

    private fun registerPhoneReceiver() {
        val filter = IntentFilter()
        filter.addAction("android.intent.action.PHONE_STATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(phoneCallReceiver, filter,RECEIVER_EXPORTED)
        } else {
            registerReceiver(phoneCallReceiver, filter)
        }
    }

    private fun startHome() {
        startActivity(Intent(this@ActivityConnectedNew, ActivityHome::class.java))
        finishAffinity()
    }
    @SuppressLint("MissingPermission")
    private fun initView(){
        try {
            if (intent.hasExtra("bluetoothDevice")) {
                bluetoothDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("bluetoothDevice", BluetoothDevice::class.java)
                } else {
                    intent.getParcelableExtra("bluetoothDevice")!!
                }
            }
        } catch (e: Exception) {
            Log.e("MY_DEBUG", "$e")
        }

        val filter = IntentFilter()
        filter.addAction("CLIENT_CLOSE")
        filter.addAction("BLE_ERROR")
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        binding.apply {
            txtDeviceNmeNew.text = bluetoothDevice?.name ?: ""
            btnDisconnectNew.setOnClickListener {
                Log.e("btnDisconnectNew", "socket close")
                sendBroadcast(Intent("ACTION_CLOSE"))
                saveLastConnectedDevice(null)
            }

            settingConnectedNew.setOnClickListener {
                startActivity(Intent(this@ActivityConnectedNew, ActivitySettingS::class.java))
            }
        }
    }

    private fun clickEvent() {
        binding.apply {
            settingConnectedNew.setOnClickListener {
                startActivity(Intent(this@ActivityConnectedNew, ActivitySettingS::class.java).apply {
                    putExtra("fromScreen", "ActivityConnectedNew")
                })
            }
        }
    }

    private fun registerIncomingCallReceiver() {
        Log.d("MY_DEBUG", "ACT_CONECTED_NEW registerIncomingCallReceiver: ")
        val incomingCallReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.takeIf { it.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED }?.run {
                    val state = getStringExtra(TelephonyManager.EXTRA_STATE)
                    if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                        val incomingNumber: String? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        } else {
                            // You might not be able to get the incoming number directly due to restrictions.
                            // Consider alternative ways to get the incoming number, such as using a third-party service
                            // or ensuring your app is the default dialer.
                          null
                        }

                     //   val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        val callerName = getCallerNameFromContact(context, incomingNumber)
                        if (incomingNumber != null && incomingNumber != ""){

                        }

                        Log.d("MY_DEBUG", "TelephonyManager: $incomingNumber $callerName")
                        incomingNumber?.let {

                        }

                        val notificationModel = NotificationModel(
                            callerName?:"Unknown",
                            incomingNumber?:"Restricted Number",
                            "Call"
                        )

                        notificationViewModel.changeCurrentNotification(notificationModel)
                        sendBroadcast(Intent("ACTION_WRITE_NOTI_NEW"))
                    }
                }
            }
        }
        val intentFilter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(incomingCallReceiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(incomingCallReceiver, intentFilter)
        }
    }

    private fun startNotificationListenerService() {
        notificationListenerIntent = Intent(this, NotificationListener::class.java)
        notificationListenerIntent.putExtra("selectedApps", selectedApps.toTypedArray())
        startService(notificationListenerIntent)
    }

    private fun isNotificationAccessGranted(): Boolean {
        val listenerServices = NotificationManagerCompat.getEnabledListenerPackages(this)
        return listenerServices.contains(packageName)
    }

    private fun requestNotificationAccessPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        notificationAccessLauncher.launch(intent)
    }

    override fun onIncomingCallStarted(ctx: Context?, number: String?, start: Date?) {
        Log.w("CALL_DEBUG", " onIncomingCallStarted")
    }

    override fun onOutgoingCallStarted(ctx: Context?, number: String?, start: Date?) {
        Log.w("CALL_DEBUG", " onOutgoingCallStarted")
    }

    override fun onIncomingCallEnded(ctx: Context?, number: String?, start: Date?, end: Date?) {
        Log.w("CALL_DEBUG", "onIncomingCallEnded")
    }

    override fun onOutgoingCallEnded(ctx: Context?, number: String?, start: Date?, end: Date?) {
        Log.w("CALL_DEBUG", "onOutgoingCallEnded")
    }

    override fun onMissedCall(ctx: Context?, number: String?, start: Date?) {
        Log.w("CALL_DEBUG", "onMissedCall")
    }

}