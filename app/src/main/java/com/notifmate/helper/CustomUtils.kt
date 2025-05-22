package com.notifmate.helper

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AlertDialog
import java.io.ByteArrayOutputStream
import androidx.core.content.edit
import com.notifmate.R
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CustomUtils {

    lateinit var progressDialog: AlertDialog

    private const val PREF_NAME = "my_preferences"
    private const val EXCLUDED_APPS_KEY = "excluded_apps"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getCurrentMusic(context: Context): String {
        return getPreferences(context).getString("music", "remote") ?: "remote"
    }

    fun getCurrentNoti(context: Context): String {
        return getPreferences(context).getString("noti", "remote") ?: "remote"
    }

    fun getCurrentOverlay(context: Context): String {
        return getPreferences(context).getString("overlay", "never") ?: "never"
    }

    fun saveMusicPreference(context: Context, music: String) {
        getPreferences(context).edit { putString("music", music) }
    }

    fun saveNotiPreference(context: Context, noti: String) {
        getPreferences(context).edit { putString("noti", noti) }
    }

    fun saveOverlayPreference(context: Context, overlay: String) {
        Log.e("MY_DEBUG", "SAVE_OVERLAY_PREFERENCE")
        getPreferences(context).edit { putString("overlay", overlay) }
    }

    fun saveThemePreference(context: Context, theme: String) {
        Log.d("MYDEBUG", "Saving Theme Preference: $theme")
        getPreferences(context).edit { putString("theme", theme) }
    }


    fun getThemePreference(context: Context): String {
        val sharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val savedTheme = sharedPreferences.getString("theme", "system") ?: "system"
        return savedTheme
    }

    fun saveAppScreenPreference(context: Context, appScreen: String) {
        getPreferences(context).edit { putString("appScreen", appScreen) }
    }

    fun getAppScreenPreference(context: Context): String {
        return getPreferences(context).getString("appScreen", "") ?: ""
    }

    fun saveReceiverNamePreference(context: Context, receiverName: String) {
        getPreferences(context).edit { putString("receiverName", receiverName) }
    }

    fun getReceiverNamePreference(context: Context): String {
        return getPreferences(context).getString("receiverName", "") ?: ""
    }

    fun saveReceiverAddressPreference(context: Context, receiverAddress: String) {
        getPreferences(context).edit { putString("receiverAddress", receiverAddress) }
    }

    fun getReceiverAddressPreference(context: Context): String {
        return getPreferences(context).getString("receiverAddress", "") ?: ""
    }

    fun bitmapToBase64(bitmap: Bitmap?, quality: Int = 70): String {
        if (bitmap == null) return ""
        val byteArrayOutputStream = ByteArrayOutputStream()
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

    /**
     * Get list of installed apps (excluding system apps)
     */
    fun getInstalledApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val apps = mutableListOf<Pair<String, String>>()

        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (packageInfo in packages) {
            val appName = pm.getApplicationLabel(packageInfo).toString()
            val packageName = packageInfo.packageName

            // Exclude system apps properly
            if ((packageInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                (packageInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            ) {
                apps.add(Pair(appName, packageName))
            }
        }

        return apps.sortedBy { it.first } // Sort alphabetically
    }

    /**
     * Save the list of excluded apps to SharedPreferences
     */
    fun saveExcludedApps(context: Context, excludedApps: Set<String>) {
        getPreferences(context).edit {
            putStringSet(EXCLUDED_APPS_KEY, excludedApps)
        }
        Log.d("MYDEBUG", "Excluded Apps Saved: $excludedApps")
    }

    /**
     * Retrieve the list of excluded apps from SharedPreferences
     */
    fun getExcludedApps(context: Context): MutableSet<String> {
        val excludedApps = getPreferences(context).getStringSet(EXCLUDED_APPS_KEY, mutableSetOf()) ?: mutableSetOf()
        Log.d("MYDEBUG", "Excluded Apps Retrieved: $excludedApps")
        return excludedApps.toMutableSet()
    }

    fun initializePreferences(context: Context) {
        val existingExcludedApps = getExcludedApps(context)

        // If no excluded apps exist (first-time install), add only the current app
        if (existingExcludedApps.isEmpty()) {
            val packageName = context.packageName // Get current app package name
            val excludedApps = mutableSetOf(packageName)

            saveExcludedApps(context, excludedApps)
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
            Log.e("MYDEBUG CustomUtils", "stopLoader: ", e)
        }
    }

    fun String.truncate(maxLength: Int = 50): String {
        return if (this.length > maxLength) {
            this.take(maxLength - 3) + "..."
        } else {
            this
        }
    }

    fun saveCrashToFile(context: Context, throwable: Throwable) {
        try {
            val crashDir = File(context.filesDir, "crash_logs")
            if (!crashDir.exists()) crashDir.mkdirs()

            val crashFile = File(crashDir, "crash_${System.currentTimeMillis()}.log")

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            crashFile.writeText(sw.toString())

            Log.d("GlobalCrashHandler", "Crash log saved: ${crashFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("GlobalCrashHandler", "Failed to save crash log", e)
        }
    }

}
