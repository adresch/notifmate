package com.notifmate

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.notifmate.helper.CustomUtils
import com.notifmate.helper.NotificationListener
import com.notifmate.helper.ThemeUtils
import android.os.Handler
import android.os.Looper
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.initialize
import com.notifmate.model.NotifMateActivity

class MainActivity : NotifMateActivity() {

    private val bluetoothPermissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )

    private val handler = Handler(Looper.getMainLooper())

    private val requestBluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val allGranted = result.values.all { it }
            if (allGranted) {
                Toast.makeText(this, "Bluetooth permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth permissions denied!", Toast.LENGTH_SHORT).show()
            }
            checkPermissionsAndProceed()
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied!", Toast.LENGTH_SHORT).show()
            }
            waitForUserAction(::requestBluetoothPermissions)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applySavedTheme(this)
        CustomUtils.initializePreferences(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase
        Firebase.initialize(this)

        // Optional: Enable Crashlytics collection in debug builds
        Firebase.crashlytics.setCrashlyticsCollectionEnabled(true)

        val proceedButton = findViewById<Button>(R.id.proceed_button)
        proceedButton.setOnClickListener {
            startPermissionFlow()
        }

        checkPermissionsAndProceed() // If everything is granted, proceed automatically
    }

    private fun startPermissionFlow() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else {
            waitForUserAction(::requestNotificationAccess)
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
        startActivity(intent)
        Toast.makeText(this, "Enable overlay permission for this app", Toast.LENGTH_LONG).show()

        // Wait for the user to grant overlay permission before moving to the next step
        waitForUserAction(::requestNotificationAccess) { Settings.canDrawOverlays(this) }
    }

    private fun requestNotificationAccess() {
        if (!isNotificationListenerEnabled()) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Enable notification access for this app", Toast.LENGTH_LONG).show()
        }

        waitForUserAction(::requestNotificationPermissions) { isNotificationListenerEnabled() }
    }

    private fun requestNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            waitForUserAction(::requestBluetoothPermissions)
        }
    }

    private fun requestBluetoothPermissions() {
        val allGranted = bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) {
            requestBluetoothPermissionLauncher.launch(bluetoothPermissions)
        } else {
            checkPermissionsAndProceed()
        }
    }

    private fun checkPermissionsAndProceed() {
        if (isAllPermissionsGranted()) {
            proceedToHome()
        }
    }

    private fun isAllPermissionsGranted(): Boolean {
        val bluetoothGranted = bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        val notificationPermissionGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else true
        val notificationAccessGranted = isNotificationListenerEnabled()
        val overlayGranted = Settings.canDrawOverlays(this)

        return bluetoothGranted && notificationPermissionGranted && notificationAccessGranted && overlayGranted
    }

    private fun proceedToHome() {
        Log.d("MYDEBUG", "All permissions granted, proceeding to home")
        val intent = Intent(this, ActivityHome::class.java)
        startActivity(intent)
        finish()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(this, NotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn.flattenToString()) == true
    }

    /**
     * Helper function that waits for the user to grant a permission before continuing.
     */
    private fun waitForUserAction(nextStep: () -> Unit, condition: (() -> Boolean)? = null) {
        handler.postDelayed(object : Runnable {
            override fun run() {
                // If no condition is provided, continue immediately
                if (condition == null || condition()) {
                    nextStep()
                  } else {
                    handler.postDelayed(this, 1000) // Check again after 1 second
                }
            }
        }, 1000)
    }
}
