package com.notifmate

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.widget.SearchView
import com.notifmate.adapter.AppAdapter
import com.notifmate.databinding.ActivitySettingSBinding
import com.notifmate.helper.CustomActivity
import com.notifmate.model.AppInfo
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.notifmate.helper.NotificationListener

class ActivitySettingS : CustomActivity() {
    private lateinit var binding: ActivitySettingSBinding
    private  var allApps: List<AppInfo>? = null
    private lateinit var adapter: AppAdapter
    private var selectedApps: MutableSet<String> = mutableSetOf()

    private val notificationListener = NotificationListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingSBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentTheme = getCurrentTheme()

        binding.apply {
            checkButton5.isChecked = currentTheme == "light"
            checkButton6.isChecked = currentTheme == "dark"
            checkButton7.isChecked = currentTheme !in listOf("light", "dark")

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
                        Log.d("MY_DEBUG", "saveThemePreference system")
                    }
                }
            }

            btnClose.setOnClickListener {
                if (selectedApps.isNotEmpty()) {
                    saveAppListPreference(selectedApps)
                    notificationListener.updateSelectedAppList(selectedApps)
                }
                finish()
            }

            binding.lastNotificationApp.text = "Last notification from: " + getAppNameFromPackageName(getLastNotificationPckgName())


            loadApps()
//            allApps = getInstalledApps()
//            adapter = AppAdapter(packageManager, allApps, selectedApps)
//            recApp.adapter = adapter

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
        showLoader(this@ActivitySettingS)
    }

//    private fun getInstalledApps(): List<AppInfo> {
//        if (!::allApps.isInitialized) {
//            allApps = loadInstalledApps()
//        }
//        return allApps
//    }

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

    // Function to check if an app has phone call permissions
    fun hasPhoneCallPermission(packageName: String, pm: PackageManager): Boolean {
        try {
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val permissions = packageInfo.requestedPermissions
            permissions?.let {
                for (permission in it) {
                    if (permission == Manifest.permission.CALL_PHONE) {
                        return true
                    }
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            // Handle exception if package not found
        }
        return false
    }

    // Function to check if an app is a social media app
    fun isSocialMedia(appName: String): Boolean {
        val socialMediaKeywords = listOf("facebook", "twitter", "instagram", "snapchat", "linkedin")
        return socialMediaKeywords.any { keyword -> appName.contains(keyword) }
    }

    // Function to check if an app is a messaging app
    fun isMessaging(appName: String): Boolean {
        val messagingKeywords = listOf("messaging", "message", "whatsapp", "telegram", "messenger")
        return messagingKeywords.any { keyword -> appName.contains(keyword) }
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        // Do nothing to disable the back button press
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
}