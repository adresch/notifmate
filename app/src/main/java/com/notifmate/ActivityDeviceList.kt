package com.notifmate

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.notifmate.adapter.DeviceListAdapter
import com.notifmate.databinding.ActivityDeviceListBinding
import com.notifmate.helper.CustomActivity
import com.notifmate.utils.DataManager

class ActivityDeviceList : CustomActivity() {
    private lateinit var adapter: DeviceListAdapter
    private lateinit var binding: ActivityDeviceListBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var discoveredDevices: MutableList<BluetoothDevice> = mutableListOf()
    private var discoverDevicesReceiver: BroadcastReceiver? = null

    // Define rescan interval (e.g., 30 seconds)
    private val rescanIntervalMillis: Long = 30000
    private val handler = Handler(Looper.getMainLooper())
    private var bleConnectionState = 0

    // Create a Runnable for periodic rescan
    private val rescanRunnable = object : Runnable {
        override fun run() {
            // Perform rescan and update the list of discovered devices
            rescanDevices()
            // Schedule the next rescan
            handler.postDelayed(this, rescanIntervalMillis)
        }
    }

    private val autoConnectIntervalMillis: Long = 10000 // 10 seconds
    private val autoConnectRunnable = object : Runnable {
        override fun run() {
            attemptAutoConnect()
            // Schedule the next auto-connect attempt
            handler.postDelayed(this, autoConnectIntervalMillis)
        }
    }

    @SuppressLint("MissingPermission")
    private fun attemptAutoConnect() {
        if (bluetoothAdapter.isEnabled.not()) {
            Log.e("ActivityDeviceList", "bluetoothAdapter.isEnabled so returning ")
            return
        }

        val lastDeviceAddress = getLastConnectedDeviceAddress()
        Log.d("MY_DEBUG", "attemptAutoConnect $lastDeviceAddress")
        if (!lastDeviceAddress.isNullOrEmpty()) {
            val lastDevice = bluetoothAdapter.getRemoteDevice(lastDeviceAddress)
            if (lastDevice != null && !isDeviceConnected(lastDevice)) {
                connectToDevice(lastDevice)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val connectedDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
        return connectedDevices.contains(device)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        saveAppScreenPreference("ActivityDeviceList")

        // Initialize Bluetooth adapter
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        // Check if Bluetooth is supported on this device
        if (bluetoothAdapter == null) {
            handleBluetoothNotSupported()
            return
        } else {
            if (bluetoothAdapter.isEnabled.not()) {
                // Bluetooth is not enabled, enable it
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                bluetoothEnableLauncher.launch(enableBtIntent)
            } else {
                // Bluetooth is already enabled
                // Proceed with your application logic
            }
        }

        adapter = DeviceListAdapter(discoveredDevices, ::connectToDevice)

        binding.apply {
            setting.setOnClickListener {
                startActivity(Intent(this@ActivityDeviceList, ActivitySettingS::class.java))
            }

            recyclerView.adapter = adapter
            btnCancel.setOnClickListener {
                finish()
            }

            btnScan.setOnClickListener {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU){
                    // Android version is below 13, perform device discovery
                    if (bluetoothAdapter.isEnabled) {
                        btnCancel.visibility = View.VISIBLE
                        btnScan.visibility = View.GONE
                    }
                }
                scan()
            }
        }

        // Check permissions and request if necessary
        checkPermissionsAndDiscoverDevices()

        scan()
        // Start the auto-connect loop
        attemptAutoConnect()
        handler.postDelayed(autoConnectRunnable, autoConnectIntervalMillis)


    }

    private fun scan(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android version is 13 or higher, show paired devices
            showPairedDevices()
        } else {
            // Android version is below 13, perform device discovery
            if (bluetoothAdapter.isEnabled) {
                discoverDevices()
                handler.postDelayed(rescanRunnable, rescanIntervalMillis)
            } else {
                showSnackbar("Enable Bluetooth")
            }
        }
    }

    // Function to show paired devices
    @SuppressLint("MissingPermission")
    private fun showPairedDevices() {
        // Get the list of paired devices
        val pairedDevices = bluetoothAdapter.bondedDevices
        // Clear the existing discovered devices list
        discoveredDevices.clear()
        // Add paired devices to the list
        discoveredDevices.addAll(pairedDevices)
        // Notify the adapter
        adapter.notifyDataSetChanged()
        startBroadcast()
    }

    // Check and request necessary permissions
    private fun checkPermissionsAndDiscoverDevices() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For devices running Android 12 (API level 31) or higher
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

        // Always request ACCESS_FINE_LOCATION if not already granted
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Additionally, check for ACCESS_COARSE_LOCATION along with ACCESS_FINE_LOCATION
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            // Request all necessary permissions
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Request permissions
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.values.all { it }
            if (allPermissionsGranted) {
                // Permissions granted, start discovering devices
            } else {
                // Handle permissions denied case
            }
        }

    // Initialize ActivityResultLauncher for enabling Bluetooth
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Bluetooth was successfully enabled
            // Proceed with your application logic
        } else {
            // User denied enabling Bluetooth or an error occurred
            showSnackbar("Enable Bluetooth")
        }
    }

    // Start discovering devices
    private fun discoverDevices() {
        // Check if Bluetooth permissions are granted
        if (!checkBluetoothPermissions()) {
            // Permissions not granted, handle this case
            showPermissionError("Missing Bluetooth permissions. Please grant permissions to use this feature.")
            return
        }

        // Check if Bluetooth discovery is ongoing and cancel it if necessary
        try {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            showPermissionError("Permission denied for canceling Bluetooth discovery.")
            return
        }

        startBroadcast()
        // Start Bluetooth device discovery
        try {
            bluetoothAdapter.startDiscovery()
        } catch (e: SecurityException) {
            showPermissionError("Permission denied for starting Bluetooth discovery.")
        }
    }


    private fun  startBroadcast() {
        // Create a BroadcastReceiver to handle discovered devices
        discoverDevicesReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        Log.d("bleDevice", device?.name.toString())
                        device?.let {
                            // Filter devices with non-null names and avoid duplicates
                            if (it.name != null && !discoveredDevices.contains(it)) {
                                // Add device to the list and notify the adapter
                                discoveredDevices.add(it)
                                adapter.notifyItemInserted(discoveredDevices.size - 1)
                            }
                        }
                    }

                    "CLIENT_CONNECTED" -> {
                        openNextScreen()
                        Toast.makeText(
                            context,
                            "Connected to: ${getConnectedDevice()?.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    "CLIENT_NOT_CONNECTED" -> {
                        Toast.makeText(
                            context,
                            "Failed to connect to: ${getConnectedDevice()?.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    BluetoothAdapter.ACTION_STATE_CHANGED ->{
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        Log.e("BluetoothAdapterState", state.toString())
                        when (state) {
                            BluetoothAdapter.STATE_ON -> {
                                bleConnectionState = BluetoothAdapter.STATE_ON
                                // Bluetooth has been turned on, attempt to reconnect
                                attemptAutoConnect()

                            }

                            BluetoothAdapter.STATE_OFF -> {
//                                if(bleConnectionState!=0&&)
                                // Handle Bluetooth turned off if needed
                               sendBroadcast(Intent("ACTION_CLOSE"))
//                                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
//                                if (bluetoothAdapter.isDiscovering) {
//                                    bluetoothAdapter.cancelDiscovery()
//                                    Log.d("BluetoothAdapterState", "Device discovery stopped as Bluetooth is off.")
//                                }
                            }
                        }
                    }
                }
            }
        }

        // Register the BroadcastReceiver for Bluetooth device discovery
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction("CLIENT_CONNECTED")
        filter.addAction("CLIENT_NOT_CONNECTED")
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(discoverDevicesReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(discoverDevicesReceiver, filter)
        }
    }

    private fun openNextScreen() {
        //val intent = Intent(this, ActivityConnected::class.java)
        handler.removeCallbacks(rescanRunnable)
        handler.removeCallbacks(autoConnectRunnable)
        val intent = Intent(this, ActivityConnectedNew::class.java)
        intent.putExtra("bluetoothDevice", getConnectedDevice())
        startActivity(intent)
    }

    private fun showPermissionError(message: String) {
        // Display an error message to the user
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Rescan for devices and update the list
    @SuppressLint("MissingPermission")
    private fun rescanDevices() {
        // Start or continue device discovery
        try {
            if (!bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.startDiscovery()
            }
        } catch (e: SecurityException) {
            showPermissionError("Permission denied for starting Bluetooth discovery.")
            return
        }

        // Periodically check the devices in the list and remove any that are no longer available
        val currentDeviceAddresses = discoveredDevices.map { it.address }

        // Create a list of devices to remove (those no longer available)
        val devicesToRemove = mutableListOf<BluetoothDevice>()

        for (device in discoveredDevices) {
            if (device.address !in currentDeviceAddresses) {
                devicesToRemove.add(device)
            }
        }
        Log.e("devicesToRemove", devicesToRemove.toString())

        // Remove devices from the list and notify the adapter
        for (device in devicesToRemove) {
            val index = discoveredDevices.indexOf(device)
            if (index != -1) {
                discoveredDevices.removeAt(index)
                adapter.notifyItemRemoved(index)
            }
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasBluetoothConnectPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            val hasBluetoothScanPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            val hasFineLocationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            hasBluetoothConnectPermission && hasBluetoothScanPermission &&
                    hasFineLocationPermission && hasCoarseLocationPermission
        } else {
            // For versions prior to Android 12
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun handleBluetoothNotSupported() {
        Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG).show()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (getAppListPreference().isEmpty()) {
            showSnackbar("Select the apps for notification")
        }

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        saveLastConnectedDevice(device)
        connectDevice(bluetoothAdapter, device)
    }

    private fun showSnackbar(msg: String) {
        Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT)
            .show()
    }

    override fun onPause() {
        super.onPause()
        // Stop periodic rescans when the activity is paused
        Log.d("MY_DEBUG", "onPause: Removing auto-connect callback")
        handler.removeCallbacks(rescanRunnable)
        handler.removeCallbacks(autoConnectRunnable)
    }

    override fun onStop() {
        super.onStop()
        Log.d("MY_DEBUG", "onStop: Removing auto-connect callback")
        // Stop auto-connect loop when the activity is no longer visible
        handler.removeCallbacks(rescanRunnable)
        handler.removeCallbacks(autoConnectRunnable)
    }


    override fun onDestroy() {
        super.onDestroy()
        // Unregister the BroadcastReceiver
        Log.d("MY_DEBUG", "onDestroy: Removing auto-connect callback")
        handler.removeCallbacks(rescanRunnable)
        handler.removeCallbacks(autoConnectRunnable)
        discoverDevicesReceiver?.let {
            unregisterReceiver(it)
        }
    }


}
