package com.notifmate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.notifmate.databinding.ActivityAskPermissionBinding
import com.notifmate.helper.NotificationListener
import com.notifmate.viewmodel.AskPermissionViewmodel
import kotlinx.coroutines.launch

class AskPermissionActivity : AppCompatActivity() {
    private val TAG = "AskPermissionActivity12"
    private lateinit var binding: ActivityAskPermissionBinding
    private val permissionsMap = mutableMapOf<String, Boolean>()
    private lateinit var viewmodel: AskPermissionViewmodel

    private var currentPermission = ""
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
//            val updatedPermissionsMap = permissionsMap.toMutableMap() // Create a copy of permissionsMap
            permissions.forEach {
                if (permissionsMap.keys.contains(it.key)) {
                    permissionsMap[it.key] = it.value
                }
            }
            checkandOpenActivity()
        }

    private val notificationAccessLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(
                TAG,
                "isNotificationAccessGranted::${result.data}  ${isNotificationAccessGranted()}"
            )
            if (isNotificationAccessGranted()) {
                permissionsMap["notification"] = true
                checkandOpenActivity()
            } else {
                permissionsMap["notification"] = false
                Toast.makeText(
                    this,
                    "Notification access permission not granted",
                    Toast.LENGTH_SHORT
                ).show()
            }


        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAskPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewmodel = ViewModelProvider(this)[AskPermissionViewmodel::class.java]
        clickEvent()
        addPermissionToAsk()
        Log.e(TAG, "onCreate: $permissionsMap")
//        lifecycleScope.launch {
//            viewmodel.permissionsMap.collect { permissionsMap ->
//                Log.d(TAG, "permissionsMap: $permissionsMap")
//            if (permissionsMap.size == 3 && !permissionsMap.containsValue(false)) {
//                Log.d(TAG, "permissionsMap all permission granted: $permissionsMap")
//                // All permissions are granted
//                // Your code to handle the case when all permissions are granted goes here
//            }
//        }
//
//        }


//        Log.d(TAG, "permissionsMap: $permissionsMap")
//        if (permissionsMap.containsValue(false).not()) {
//            Toast.makeText(this, "all permission granted", Toast.LENGTH_SHORT).show()
//            // All permissions granted, navigate to the next activity
//            //navigateToNextActivity()
//        } else {
//            Toast.makeText(this, "permission is necessary", Toast.LENGTH_SHORT).show()
//        }

    }

    private fun addPermissionToAsk() {
        permissionsMap.clear()
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
                permissionsMap[Manifest.permission.BLUETOOTH_CONNECT] = false
                permissionsMap[Manifest.permission.BLUETOOTH_SCAN] = false
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsMap[Manifest.permission.ACCESS_FINE_LOCATION] = false
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            permissionsMap[Manifest.permission.READ_PHONE_STATE] = false
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsMap[Manifest.permission.READ_CALL_LOG] = false
        }

        if (isNotificationAccessGranted().not()) {
            permissionsMap["notification"] = false
        }

        Log.e(TAG, "addPermissionToAsk: $permissionsMap")
    }

    private fun clickEvent() {
        val permissionsToRequest = mutableListOf<String>()
        binding.notificationPermission.setOnClickListener {
            if (isNotificationAccessGranted().not()) {
                requestNotificationAccessPermission()
            } else {
                checkandOpenActivity()
                Toast.makeText(this, "Notification access permission  granted", Toast.LENGTH_SHORT)
                    .show()
            }
        }


        binding.requestBluetoothPermission.setOnClickListener {
            currentPermission = "bluetooth"
            if (permissionsToRequest.isNotEmpty()) {
                permissionsToRequest.clear()
            }

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

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            if (permissionsToRequest.isNotEmpty()) {
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            } else {
                Toast.makeText(this, "bluetooth Permission granted", Toast.LENGTH_SHORT).show()
            }
        }

        binding.phoneCallAccess.setOnClickListener {
            currentPermission = "phonecall"
            if (permissionsToRequest.isNotEmpty()) {
                permissionsToRequest.clear()
            }
            // request phone call permission
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
            }

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_CALL_LOG
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_CALL_LOG)
            }

            if (permissionsToRequest.isNotEmpty()) {
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            } else {
                Toast.makeText(this, "phone call access permission granted", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    }

    private fun checkandOpenActivity() {
        if (permissionsMap.containsValue(false).not()) {
            Toast.makeText(this, "all permission granted", Toast.LENGTH_SHORT).show()
            // All permissions granted, navigate to the next activity
            //navigateToNextActivity()
            val intent = Intent(this@AskPermissionActivity, ActivityHome::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun requestNotificationAccessPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        notificationAccessLauncher.launch(intent)
    }

    private fun isNotificationAccessGranted(): Boolean {
        val listenerServices = NotificationManagerCompat.getEnabledListenerPackages(this)
        return listenerServices.contains(packageName)
    }
}