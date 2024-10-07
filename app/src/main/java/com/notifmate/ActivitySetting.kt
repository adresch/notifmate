package com.notifmate

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.appcompat.widget.SearchView
import com.notifmate.adapter.AppAdapter
import com.notifmate.databinding.ActivitySettingBinding
import com.notifmate.helper.CustomActivity
import com.notifmate.helper.NotificationListener
import com.notifmate.model.AppInfo
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActivitySetting : CustomActivity() {
    private lateinit var binding: ActivitySettingBinding
    private var deviceName = ""
    private var intentData = ""
    private var selectedApps: MutableSet<String> = mutableSetOf()
    private  var allApps: List<AppInfo>? = null
    private lateinit var adapter: AppAdapter

    private lateinit var connectedDeviceLayout: LinearLayout
    private var originalHeight: Int = 0 // Height in pixels
    private var rootViewHeight: Int = 0 // Root view height in pixels


    private val notificationListener = NotificationListener()

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()

        connectedDeviceLayout = findViewById(R.id.connectedDeviceLayout)
        // Convert dp to pixels
        originalHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 229f, resources.displayMetrics).toInt()

        val rootView = window.decorView.findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            private var isKeyboardVisible = false

            override fun onGlobalLayout() {
                val rect = Rect()
                rootView.getWindowVisibleDisplayFrame(rect)
                val visibleHeight = rect.height()

                if (rootViewHeight == 0) {
                    rootViewHeight = rootView.height
                }

                val heightDifference = rootViewHeight - visibleHeight

                // If the height difference is significant (assuming 100 pixels as a threshold), the keyboard is visible
                val isKeyboardNowVisible = heightDifference > 100

                if (isKeyboardNowVisible != isKeyboardVisible) {
                    isKeyboardVisible = isKeyboardNowVisible
                    if (isKeyboardVisible) {
                        // Keyboard is visible
                        val layoutParams = connectedDeviceLayout.layoutParams
                        layoutParams.height = 0
                        connectedDeviceLayout.layoutParams = layoutParams
                    } else {
                        // Keyboard is hidden
                        val layoutParams = connectedDeviceLayout.layoutParams
                        layoutParams.height = originalHeight
                        connectedDeviceLayout.layoutParams = layoutParams
                    }
                }
            }
        })


        val currentTheme = getCurrentTheme()
        val currentMusic = getCurrentMusic()
        val currentNoti = getCurrentNoti()
        val currentOverlay = getCurrentOverlay()
        try {
            //   deviceName = DataManager.instanse.deviceName
            // get device name from shared pref
            deviceName = getDeviceNamePreference()
            //Log.d("TAG", "deviceName: " + DataManager.instanse.deviceName )
            Log.d(TAG, "deviceName: $deviceName")

        } catch (_: Exception) {
            deviceName = "Local Device"
        }
        binding.apply {
            remoteLocalCheckbox.isChecked = currentNoti == "remote_local"
            remoteOnlyCheckbox.isChecked = currentNoti == "remote"
            localOnlyCheckbox.isChecked = currentNoti == "local"
            noneCheckbox.isChecked = currentNoti !in listOf("remote_local", "remote", "local")

            checkButton1.isChecked = currentMusic == "remote"
            checkButton2.isChecked = currentMusic == "local"
            checkButton3.isChecked = currentMusic !in listOf("remote", "local")

            checkButton5.isChecked = currentTheme == "light"
            checkButton6.isChecked = currentTheme == "dark"
            checkButton7.isChecked = currentTheme !in listOf("light", "dark")

            checkButton8.isChecked = currentOverlay == "always"
            checkButton9.isChecked = currentOverlay == "background"
            checkButton10.isChecked = currentOverlay !in listOf("always", "background")


            binding.radioGroupNoti.setOnCheckedChangeListener { group, checkedId ->
                when (checkedId) {
                    R.id.remote_local_checkbox -> {
                        saveNotiPreference("remote_local")
                    }

                    R.id.remote_only_checkbox -> {
                        saveNotiPreference("remote")
                    }

                    R.id.local_only_checkbox -> {
                        saveNotiPreference("local")
                    }

                    R.id.none_checkbox -> {
                        saveNotiPreference("none")
                    }
                }
            }
            binding.radioGroupMusic.setOnCheckedChangeListener { group, checkedId ->
                when (checkedId) {
                    R.id.check_button1 -> {
                        saveMusicPreference("remote")
                    }

                    R.id.check_button2 -> {
                        saveMusicPreference("local")
                    }

                    R.id.check_button3 -> {
                        saveMusicPreference("none")
                    }
                }
            }

            binding.radioThemeGroup.setOnCheckedChangeListener { group, checkedId ->
                when (checkedId) {
                    R.id.check_button5 -> {
                        saveThemePreference("light")
                    }

                    R.id.check_button6 -> {
                        saveThemePreference("dark")
                    }

                    R.id.check_button7 -> {
                        saveThemePreference("system")
                    }
                }
            }

            binding.radioOverlayGroup.setOnCheckedChangeListener { group, checkedId ->
                when (checkedId) {
                    R.id.check_button8 -> {
                        saveOverlayPreference("always")
                    }

                    R.id.check_button9 -> {
                        saveOverlayPreference("background")
                    }

                    R.id.check_button10 -> {
                        saveOverlayPreference("none")
                    }
                }
            }

            btnClose.setOnClickListener { finish() }

            btnDisconnect.setOnClickListener {
                sendBroadcast(Intent("ACTION_CLOSE_PORT"))
            }

            btnClose.setOnClickListener {
                saveAppListPreference(selectedApps)
                notificationListener.updateSelectedAppList(selectedApps)
                finish()
            }

            loadApps()

            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    val filteredApps = if (newText.isNullOrEmpty()) {
                        allApps
                    } else {
                        allApps?.filter { it.appName.contains(newText, ignoreCase = true) }
                    }

                    if (filteredApps != null) {
                        adapter.filterList(filteredApps)
                    }
                    return true
                }
            })

            binding.lastNotificationApp.text = "Last notification from: " + getAppNameFromPackageName(getLastNotificationPckgName())

            if (deviceName.isEmpty()) {
                //btnDisconnect.visibility = View.GONE
                txtDeviceName.text = resources.getString(R.string.not_connected_to_any_device)
            } else {
                btnDisconnect.visibility = View.VISIBLE
                txtDeviceName.text = resources.getString(R.string.connected_to) + " $deviceName"
            }

        }

    }

    private fun getAppNameFromPackageName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            appName
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // Fallback to package name if app name is not found
        }
    }

    override fun onStart() {
        super.onStart()
        showLoader(this@ActivitySetting)
    }

    // Function to load the list of installed apps
    private fun loadInstalledApps(): List<AppInfo> {
        val pm: PackageManager = packageManager
        val apps = mutableListOf<AppInfo>()
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (appInfo in packages) {
            apps.add(AppInfo(appInfo.loadLabel(pm).toString(), appInfo.packageName, false))
        }
        return apps.sortedBy { it.appName } // Sort the list alphabetically by appName
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun loadApps() {
        GlobalScope.launch(Dispatchers.IO) {
            val apps = loadInstalledApps()
            selectedApps = getAppListPreference()
            withContext(Dispatchers.Main) {
                allApps = apps
                adapter =
                    AppAdapter(packageManager, apps, selectedApps) // Set adapter with loaded apps
                binding.recApp.adapter = adapter // Set adapter to RecyclerView
                if(apps.isNotEmpty()){
                    stopLoader()
                }
            }

        }
    }

    private fun initViews() {
        if (intent.hasExtra("fromScreen")) {
            val fromScreen = intent.getStringExtra("fromScreen")

            if (fromScreen != null && fromScreen == "ActivityReceiverNew") {
                intentData = "ActivityReceiverNew"
//                binding.connectedDeviceLayout.visibility = View.GONE
                //binding.btnDisconnect.visibility = View.GONE
                binding.txtDeviceName.visibility = View.GONE
                binding.remoteLocalCheckbox.isClickable = false
                binding.remoteOnlyCheckbox.isClickable = false
                binding.noneCheckbox.isClickable = false
                binding.checkButton1.isClickable = false
                binding.checkButton3.isClickable = false

            }

        }
    }


    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        // Do nothing to disable the back button press
//        if(intenData.isNotEmpty()){
//            super.onBackPressed()
//        }
    }
}