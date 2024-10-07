package com.notifmate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.notifmate.databinding.ActivityHomeBinding
import com.notifmate.helper.CustomActivity

class ActivityHome : AppCompatActivity() {

    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var binding: ActivityHomeBinding

    private val notificationAccessLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (isNotificationAccessGranted()) {
                Toast.makeText(this, "Notification access permission granted", Toast.LENGTH_SHORT).show()
                checkAndRequestOverlayPermission()
            } else {
                Toast.makeText(this, "Notification access permission not granted", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize overlay permission launcher
        overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

        // Request notification access if not granted
        if (isNotificationAccessGranted().not()) {
            requestNotificationAccessPermission()
        } else {
            checkAndRequestOverlayPermission()
        }

        // Call getAppScreenPreference
        val appScreenPreference = getAppScreenPreference()

        if (appScreenPreference == "ActivityWaiting"){
            if (isNotificationAccessGranted()) {
                val intent = Intent(this@ActivityHome, ActivityWaiting::class.java)
                startActivity(intent)
            }
        }else if (appScreenPreference == "ActivityDeviceList"){
            if (isNotificationAccessGranted()) {
                val intent = Intent(this@ActivityHome, ActivityDeviceList::class.java)
                startActivity(intent)
            }
        }

        binding.apply {
            receiverView.setOnClickListener {
                if (isNotificationAccessGranted()) {
                    val intent = Intent(this@ActivityHome, ActivityWaiting::class.java)
                    startActivity(intent)
                } else {
                    requestNotificationAccessPermission()
                }
            }

            senderView.setOnClickListener {
                if (isNotificationAccessGranted()) {
                    val intent = Intent(this@ActivityHome, ActivityDeviceList::class.java)
                    startActivity(intent)
                } else {
                    requestNotificationAccessPermission()
                }
            }
        }
    }

    private fun getAppScreenPreference(): String {
        val preferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
        val appScreen = preferences.getString("appScreen", "")
        return appScreen ?: ""
    }

    private fun checkAndRequestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun requestNotificationAccessPermission() {
        Toast.makeText(
            this,
            "Notification access is required in order to continue",
            Toast.LENGTH_SHORT
        ).show()
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        notificationAccessLauncher.launch(intent)
    }

    private fun isNotificationAccessGranted(): Boolean {
        val listenerServices = NotificationManagerCompat.getEnabledListenerPackages(this)
        return listenerServices.contains(packageName)
    }
}