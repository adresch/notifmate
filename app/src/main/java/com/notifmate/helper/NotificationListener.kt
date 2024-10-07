package com.notifmate.helper

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.notifmate.model.NotificationModel
import kotlinx.coroutines.flow.MutableSharedFlow

class NotificationListener : NotificationListenerService() {
    private val TAG = "NotificationListener"

    object SharedState {
        val selectedAppsShared: MutableSet<String> = mutableSetOf()
    }

    companion object {
        val notificationLiveData =
            MutableSharedFlow<NotificationModel>(replay = 0, extraBufferCapacity = 64)

        fun getAllMusicApps(context: Context): List<String> {
            val musicApps = mutableListOf<String>()
            val pm: PackageManager = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_APP_MUSIC)
            val apps = pm.queryIntentActivities(intent, 0)

            apps.forEach { appInfo ->
                val appName = appInfo.loadLabel(pm).toString()
                musicApps.add(appName)
            }

            return musicApps
        }
    }

    fun updateSelectedAppList(appList: MutableSet<String>){
        SharedState.selectedAppsShared.clear()
        SharedState.selectedAppsShared.addAll(appList)
    }

    private var selectedApps: MutableSet<String> = mutableSetOf()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra("selectedApps")) {
            val newSelectedApps = intent.getStringArrayExtra("selectedApps")?.toSet() ?: emptySet()
            SharedState.selectedAppsShared.clear()
            SharedState.selectedAppsShared.addAll(newSelectedApps)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val packageName = sbn.packageName
        if (SharedState.selectedAppsShared.contains(packageName)) {
            getNotification(sbn)
        }else{
            Log.d("MY_DEBUG", "PCKGNAME: NOT_IN packageName: $packageName")
            val extras = sbn.notification.extras
            val notificationTitle = extras.getCharSequence("android.title")?.toString() ?: ""
            Log.d("MY_DEBUG", "PCKGNAME: NOT_IN notificationTitle: $notificationTitle")
        }
        CustomActivity.saveLastNotificationPckgName(packageName, applicationContext)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }

    private fun getNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val notificationTitle = extras.getCharSequence("android.title")?.toString() ?: ""
        val notificationText = extras.getCharSequence("android.text")?.toString() ?: ""
        val msg = "$notificationTitle   $notificationText      ${sbn.packageName}"
        Log.d(TAG, msg)

        val notificationModel =
            NotificationModel(
                notificationTitle,
                notificationText,
                getAppName(sbn.packageName)
            )

        if (!getAllMusicApps(this).contains(getAppName(sbn.packageName))) {
            if (notificationTitle != "Call" && notificationText != "Call" && notificationTitle.isNotBlank() && notificationText.isNotBlank() && notificationModel.packageName != "Phone") {
                notificationLiveData.tryEmit(notificationModel)
            }
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // Return package name if app name not found
        }
    }
}





