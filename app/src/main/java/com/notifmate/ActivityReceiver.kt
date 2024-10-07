package com.notifmate

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.notifmate.adapter.ReceiverNotificationAdapter
import com.notifmate.databinding.ActivityRecieverBinding
import com.notifmate.helper.AudioPlayState
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
import com.notifmate.viewmodel.ActivityReceiverViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class ActivityReceiver : CustomActivity(),
    ReceiverNotificationAdapter.OnNotificationDeleteListener, PhoneCallListener {
    private lateinit var adapter: ReceiverNotificationAdapter
    private lateinit var binding: ActivityRecieverBinding
    var selectedApps: MutableSet<String> = mutableSetOf()
    private lateinit var viewmodel: ActivityReceiverViewmodel
    private var audioList: ArrayList<AudioPlayState> = arrayListOf()

    private lateinit var phoneCallReceiver: PhoneCallReceiver
    private lateinit var notificationListenerIntent: Intent
    private lateinit var musicController: MusicController
    private lateinit var notificationViewModel: NotificationViewModel

    private var overlayView: View? = null


    var receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "HOST_CLOSE" -> {
                    saveDeviceNamePreference("")
                    openHomeActivity()
                }

                "Device_Name" -> {
                    saveDeviceNamePreference(intent.getStringExtra("deviceName").toString())
                }

                "HEARTBEAT" -> {
                    if (isAppInBackground(applicationContext)) {
                        // If the app is in the background, do nothing
                        Log.d("HEARTBEAT", "App is in the background, do nothing")
                    } else {
                        // App is in the foreground, check the current activity
                        if (!isCurrentActivity(applicationContext, ActivityReceiver::class.java)) {
                            // If the current activity is not ActivityReceiver, move to ActivityReceiver
                            val receiverIntent =
                                Intent(applicationContext, ActivityReceiver::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            applicationContext?.startActivity(receiverIntent)
                        }
                    }
                }

                "Data_Receiver" -> {
                    Toast.makeText(context, intent.getStringExtra("hh"), Toast.LENGTH_SHORT).show()
                }

                "App_List" -> {
                    selectedApps.add(intent.getStringExtra("appList").toString())
                    // save app list in shared pref
                    saveAppListPreference(selectedApps)
                }

                "NOTI_DATA" -> {
                    val notiData: List<NotificationModel> =
                        intent.getSerializableExtra("notiData") as List<NotificationModel>
                    audioList.clear()
                    Log.d("NOTI_DATA_ONRECIEVE", "onReceive: $notiData")
                    for (noTiData in notiData) {
                        viewmodel.addNotiData(noTiData)
                        break;
                    }

                    //                notificationViewModel.add(notiData)
                }

                "NOTI_DATA_NEW" -> {
                    val preference = getNotiPreference()
                    if (preference == "remote" || preference == "remote_local") {
                        val notiData: NotificationModel =
                            intent.getSerializableExtra("notiData") as NotificationModel
                        Log.e(TAG, "ACT_RECIEVER NOTI_DATA_NEW: $notiData")
                        viewmodel.changeNoti(notiData)
                        showOverlay(notiData)
                    }
                }

                "AUDIO_DATA" -> {
                    if (getMusicPreference() == "remote") {
                        val audioData: AudioModel =
                            intent.getSerializableExtra("audioData") as AudioModel
                        viewmodel.changeAudio(audioData)
                    }
                }

                "AUDIO_STATE" -> {
                    if (getMusicPreference() == "remote") {
                        val audioState: AudioStateModel =
                            intent.getSerializableExtra("audioState") as AudioStateModel
                        viewmodel.changeAudioState(audioState)
                    }
                }
            }
        }
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

    private fun openHomeActivity() {
        startActivity(Intent(this@ActivityReceiver, ActivityHome::class.java))
        finishAffinity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecieverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewmodel = ViewModelProvider(this)[ActivityReceiverViewmodel::class.java]
        setBluetoothStateChangeReciever()
        // musicController = MusicController(this)

        val filter = IntentFilter()
        filter.addAction("HOST_CLOSE")
        filter.addAction("Device_Name")
        filter.addAction("App_List")
        filter.addAction("NOTI_DATA_NEW")
        filter.addAction("AUDIO_DATA")
        filter.addAction("AUDIO_STATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        adapter = ReceiverNotificationAdapter(this)

        binding.apply {
            recNoti.adapter = adapter

            settings.setOnClickListener {
                startActivity(Intent(this@ActivityReceiver, ActivitySetting::class.java))
            }

            playerLayout.playPauseButton.setOnClickListener {

            }

            playerLayout.nextButton.setOnClickListener {

            }
        }

        observeNotiDataChanges()
        observeAudioChanges()
        observeAudioStateChanges()

        phoneCallReceiver = PhoneCallReceiver()
        musicController = MusicController(this)
        musicController.initializeMediaSessionManager()

        phoneCallReceiver.setPhoneCallListener(this)

        if (isNotificationAccessGranted().not()) {
            requestNotificationAccessPermission()
        } else {
            selectedApps = getAppListPreference()
            startNotificationListenerService()
        }

        registerIncomingCallReceiver()

        notificationViewModel = ViewModelProvider(this)[NotificationViewModel::class.java]

        DataManager.instanse.notificationViewModel = notificationViewModel;

        lifecycleScope.launch {
            NotificationListener.notificationLiveData.collect { notification ->
                val currentNotif = notificationViewModel.getCurrentNotification()

                val preference = getNotiPreference()
                if (preference == "local" || preference == "remote_local") {
                    if (currentNotif == null || (currentNotif.title != notification.title || currentNotif.msg != notification.msg || currentNotif.packageName != notification.packageName)) {
                        Log.d(
                            "MY_DEBUG",
                            "ACT_RECIEVER NotificationListener CUrrent new: $notification"
                        )
                        notificationViewModel.changeCurrentNotification(notification)
                        viewmodel.changeNoti(notification)
                        showOverlay(notification)
                    }
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

                if (getMusicPreference() == "local") {
                    val musicArtBase64 = bitmapToBase64(musicArt)
                    val newAudio = AudioModel(
                        songName = songName ?: "",
                        artistName = artistName ?: "",
                        musicArtBase64 = musicArtBase64
                    )

                    val currentAudio = notificationViewModel.getCurrentAudio()

                    if (currentAudio == null || newAudio.songName != currentAudio?.songName || newAudio.artistName != currentAudio?.artistName || newAudio.musicArtBase64 != currentAudio?.musicArtBase64) {
                        Log.e(
                            "MY_DEBUG",
                            "newAudio ${newAudio?.songName ?: "Unknown"} ${newAudio?.artistName ?: "Unknown"} "
                        )
                        notificationViewModel.changeCurrentAudio(newAudio)
                        Log.d("MY_DEBUG", "ACT_RECIEVER ACTION_WRITE_AUDIO")
                        viewmodel.changeAudio(newAudio)

                    }

                    val newPlayBackSate = AudioStateModel(
                        playBackState = playbackState
                    )
                    Log.d("MY_DEBUG", "ACT_RECIEVER ACTION_WRITE_PLAYBACK_STATE")
                    notificationViewModel.changeCurrentAudioPlaybackState(newPlayBackSate)
                    viewmodel.changeAudioState(newPlayBackSate)

                }
            }
        }

    }

    private fun observeNotiDataChanges() {
        lifecycleScope.launch {
            viewmodel.notificationModelNew.flowWithLifecycle(
                lifecycle = lifecycle,
                minActiveState = Lifecycle.State.STARTED
            ).collect {
                Log.d("observeNotiDataChanges", "observeNotiDataChanges: NOTI $it")
                if (it.title.isNotBlank() && !it.title.contains(".mp3")) {
                    adapter.submitNotiData(it)
                    binding.recNoti.scrollToPosition(0)
                }
            }
        }
    }

    private fun observeAudioChanges() {
        lifecycleScope.launch(Dispatchers.Main) {
            viewmodel.audioModelNew.collect {
                Log.d("MY_DEBUG", "observeAudioChanges:")
                val nightModeFlags =
                    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                var musicArtBase64 =
                    "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"
                if (it.musicArtBase64 != null && it.musicArtBase64 != "") {
                    musicArtBase64 = it.musicArtBase64
                }
                binding.playerLayout.apply {
                    musicName.text = it.songName
                    artistName.text = it.artistName
                    musicArt.setImageBitmap(base64ToBitmap(musicArtBase64))
                }
            }
        }
    }

    private fun observeAudioStateChanges() {
        lifecycleScope.launch(Dispatchers.Main) {
            viewmodel.audioStateNew.collect {
                Log.d("MY_DEBUG", "observeAudioStateChanges: ${it.playBackState}")
                val nightModeFlags =
                    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                binding.playerLayout.apply {
                    when (it.playBackState) {
                        PlaybackState.STATE_PLAYING -> {
                            when (nightModeFlags) {
                                Configuration.UI_MODE_NIGHT_YES -> {
                                    // Night mode is active
                                    playPauseButton.setImageResource(R.drawable.icons8_pause_button_96___white)
                                }

                                Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                                    // Night mode is not active, or we can't determine the mode
                                    playPauseButton.setImageResource(R.drawable.icons8_pause_button_96___)
                                }
                            }
                        }

                        PlaybackState.STATE_PAUSED -> {
                            playPauseButton.setImageResource(R.drawable.ic_play)
                        }

                        else -> {
                            playPauseButton.setImageResource(R.drawable.ic_play)
                        }
                    }
                }
            }
        }
    }

    private fun showOverlay(currentNoti: NotificationModel) {
        Log.d("MY_DEBUG", "ACT_RECIEVER SHOW OVERLAY IN")
        var currentOverlay = getCurrentOverlay()

        var currentAppContext =
            if (isAppInBackground(applicationContext)) "background" else "foreground"

        if (currentOverlay == "always" || (currentOverlay == "background" && currentAppContext == "background")) {
            Log.d("MY_DEBUG", "ACT_RECIEVER SHOW OVERLAY AFTERCONDITIOn")
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            // Inflate the custom layout if it's not already displayed
            if (overlayView == null) {
                overlayView = LayoutInflater.from(this).inflate(R.layout.overlay, null)
                windowManager.addView(overlayView, params)
                val closeButton = overlayView?.findViewById<Button>(R.id.overlay_delete_button)
                closeButton?.setOnClickListener {
                    removeOverlay()
                }
            }

            // Update the text of the TextView
            val overlay_title_text = overlayView?.findViewById<TextView>(R.id.overlay_header_text)
            val overlay_header_text = overlayView?.findViewById<TextView>(R.id.overlay_title_text)
            val overlay_description_text =
                overlayView?.findViewById<TextView>(R.id.overlay_description_text)

            overlay_header_text?.text = currentNoti.title
            overlay_title_text?.text = currentNoti.packageName
            overlay_description_text?.text = currentNoti.msg

            // Schedule the overlay to be removed after 10 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                removeOverlay()
            }, 10000)  // 10 seconds
        }
    }


    private fun removeOverlay() {
        overlayView?.let {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(it)
            overlayView = null  // Ensure to clear the reference to allow garbage collection
        }
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

    private fun isCurrentActivity(context: Context?, activityClass: Class<*>): Boolean {
        val activityManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val taskInfo = activityManager.getRunningTasks(1)
        if (taskInfo.isNotEmpty()) {
            val topActivity = taskInfo[0].topActivity
            val topActivityClass = topActivity?.className
            return topActivityClass == activityClass.name
        }
        return false
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

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        // Do nothing to disable the back button press
    }

    override fun onNotificationDelete(position: Int) {
        adapter.removeNotification(position)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(
            "deleteNotificationList",
            ArrayList(adapter.deleteNotificationList)
        )
        outState.putSerializable("NotificationList", ArrayList(adapter.notificationList))
        super.onSaveInstanceState(outState)
    }


    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val deleteList =
            savedInstanceState.getSerializable("deleteNotificationList") as? ArrayList<NotificationModel>
        val notificationList =
            savedInstanceState.getSerializable("NotificationList") as? ArrayList<NotificationModel>
        if (notificationList != null) {
            adapter.updateNotificationLiST(notificationList)
            Log.e("TAG", "onRestoreInstanceState: $notificationList")
        }

        if (deleteList != null) {
            Log.e("TAG", "onRestoreInstanceState: $deleteList")
            adapter.updateDeleteList(deleteList)
        }
    }

    private fun registerIncomingCallReceiver() {
        Log.d("MY_DEBUG", "ACT_RECIEVER registerIncomingCallReceiver: ")
        val incomingCallReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.takeIf { it.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED }?.run {
                    val state = getStringExtra(TelephonyManager.EXTRA_STATE)
                    if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                        val incomingNumber = getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        val callerName = getCallerNameFromContact(context, incomingNumber)

                        if (incomingNumber != null && incomingNumber != "") {
                            incomingNumber?.let {
                                val notificationModel = NotificationModel(
                                    callerName.orEmpty(),
                                    it,
                                    "Call"
                                )
                                val preference = getNotiPreference()
                                if (preference == "local" || preference == "remote_local") {
                                    Log.d(
                                        "MY_DEBUG",
                                        "ACT_RECIEVER TelephonyManager: $incomingNumber $callerName"
                                    )
                                    viewmodel.changeNoti(notificationModel)
                                    showOverlay(notificationModel)
                                }
                            }
                        }
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

    override fun onIncomingCallStarted(ctx: Context?, number: String?, start: Date?) {
        Log.w("MY_DEBUG", "ACT_RECIEVER onIncomingCallStarted")
    }

    override fun onOutgoingCallStarted(ctx: Context?, number: String?, start: Date?) {
        Log.w("MY_DEBUG", "ACT_RECIEVER onOutgoingCallStarted")
    }

    override fun onIncomingCallEnded(ctx: Context?, number: String?, start: Date?, end: Date?) {
        Log.w("MY_DEBUG", "ACT_RECIEVER onIncomingCallEnded")
    }

    override fun onOutgoingCallEnded(ctx: Context?, number: String?, start: Date?, end: Date?) {
        Log.w("MY_DEBUG", "ACT_RECIEVER onOutgoingCallEnded")
    }

    override fun onMissedCall(ctx: Context?, number: String?, start: Date?) {
        Log.w("MY_DEBUG", "ACT_RECIEVER onMissedCall")
    }

    fun setBluetoothStateChangeReciever() {
        val bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            Log.d("BluetoothStateReceiver", "Bluetooth is turned off")
                            // Handle the Bluetooth being turned off
                            // Close the socket if necessary
                        }

                        BluetoothAdapter.STATE_TURNING_OFF -> {
                            Log.d("BluetoothStateReceiver", "Bluetooth is turning off")
                        }

                        BluetoothAdapter.STATE_ON -> {
                            Log.d("BluetoothStateReceiver", "Bluetooth is turned on")
                        }

                        BluetoothAdapter.STATE_TURNING_ON -> {
                            Log.d("BluetoothStateReceiver", "Bluetooth is turning on")
                        }
                    }
                }
            }
        }

// Register the receiver in your activity or service
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(bluetoothStateReceiver, filter)
        }


    }

}