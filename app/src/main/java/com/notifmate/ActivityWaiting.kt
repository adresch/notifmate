package com.notifmate

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.notifmate.databinding.ActivityWaitingBinding
import com.notifmate.helper.CustomActivity

class ActivityWaiting : CustomActivity() {
    private lateinit var binding: ActivityWaitingBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var receiver: BroadcastReceiver? = null
    private var bluetoothManager: BluetoothManager? = null


    // Declare an ActivityResultLauncher for discoverability
    private val discoverableActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_CANCELED) {
                // Discoverable mode was canceled
                binding.txtTitle.text = resources.getString(R.string.discoverable_mode_not_enabled)
            } else {
                // Discoverable mode enabled
                binding.txtTitle.text = resources.getString(R.string.waiting_for_device_connection)
                val duration = result.resultCode
                "Device is discoverable for $duration seconds."
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWaitingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bluetoothManager = getSystemService(BluetoothManager::class.java)

        saveAppScreenPreference("ActivityWaiting")

        binding.apply {
            txtTitle.text = resources.getString(R.string.enable_discoverable_mode)
            txtTitle.setOnClickListener {
                // Call the function to enable discoverable mode
                enableDiscoverableMode()
            }

            btnClose.setOnClickListener {
                saveAppScreenPreference("")
                finish()
            }
            btnDevice.setOnClickListener {
                //saveMusicPreference("local")
                //saveNotiPreference("local")
                //openNextScreen()
                val intent = Intent(this@ActivityWaiting, ActivityReceiver::class.java)
                intent.putExtra("reciverScreen","ActivityReceiver")
                startActivity(intent)
                finishAffinity()
            }
        }

        discoverableDevice()
    }

    private fun discoverableDevice() {
        // Initialize Bluetooth adapter
        bluetoothAdapter = bluetoothManager?.adapter!!
        if (bluetoothAdapter == null) {
            // Bluetooth is not supported on this device
            Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG)
                .show()
            finish()
        }

        connectReciverDevice(bluetoothAdapter)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.e("MY_DEBUG", "onReceive:REGISTER HOST_CONNECTED")
                if (intent?.action == "HOST_CONNECTED") {
                    Log.e(TAG, "onReceive:host cnnected go to next reciver screen ")
                    openNextScreen()
                }
            }
        }

        val filter = IntentFilter("HOST_CONNECTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun openNextScreen() {
        //saveMusicPreference("remote")
        //saveNotiPreference("remote_local")
        startActivity(Intent(this, ActivityReceiver::class.java))
    }


    private fun enableDiscoverableMode() {
        // Define the duration (in seconds) for which the device should be discoverable
        val discoverableDurationSeconds = 300 // 5 minutes

        // Create the intent for the BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE action
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoverableDurationSeconds)
        }

        // Start the discoverable activity and register for the result
        discoverableActivityResultLauncher.launch(intent)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        // Do nothing to disable the back button press
    }
}