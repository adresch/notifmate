package com.notifmate.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.notifmate.ActivityHome
import com.notifmate.AskPermissionActivity
import com.notifmate.R
import com.notifmate.helper.CustomActivity
import com.notifmate.helper.NotificationListener

@SuppressLint("CustomSplashScreen")
class SplashScreenActivity : CustomActivity() {
    var isSender = false
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.values.all { it }
            if (allPermissionsGranted) {
                openNextScene()
            } else {
                val intent = Intent(this, AskPermissionActivity::class.java)
                startActivity(intent)
                finish()
                //openSettingsPermissionScreen()
            }
        }

    private val notificationAccessLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (isNotificationAccessGranted().not()) {
                Toast.makeText(
                    this,
                    "Notification access permission not granted",
                    Toast.LENGTH_SHORT
                ).show()
                val intent = Intent(this, AskPermissionActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                openNextScene()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        // Show splash screen for 2 seconds
        val splashScreenDuration = 2000L
        splashScreenDuration.let { duration ->
            window.decorView.postDelayed({
                checkPermissions()
            }, duration)
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        // Check Bluetooth permissions for Android 12 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.addAll(
                    listOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            }
        }

        // Check for location permission
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.addAll(
                listOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CONTACTS
                )
            )
        }


        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_CALL_LOG)
        }



        if (permissionsToRequest.isNotEmpty()) {
            // Request permissions if needed
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // If all permissions are granted, proceed to next scene
            openNextScene()
        }
    }

    private fun openNextScene() {
        startActivity(Intent(this, ActivityHome::class.java))
        finish()
        // Navigate to the next scene/activity
//        if (isNotificationAccessGranted().not()) {
//            requestNotificationAccessPermission()
//        } else {
//            startActivity(Intent(this, ActivityHome::class.java))
//            finish()
//        }

        /* val nextSceneIntent: Intent = if (isSender){
             Intent(this, SenderActivity::class.java)
         } else{
             Intent(this, ReceiverActivity::class.java)
         }
         startActivity(nextSceneIntent)*/
    }

    private fun isNotificationAccessGranted(): Boolean {
        val listenerServices = NotificationManagerCompat.getEnabledListenerPackages(this)
        return listenerServices.contains(packageName)
    }

    private fun requestNotificationAccessPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        notificationAccessLauncher.launch(intent)
    }

    private fun openSettingsPermissionScreen() {
        // Redirect the user to the app's settings to manually enable permissions
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = android.net.Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
        Toast.makeText(this, "Please grant all required permissions.", Toast.LENGTH_SHORT).show()
    }
}
