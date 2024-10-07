package com.notifmate.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.notifmate.R
import com.notifmate.adapter.DeviceListAdapter
import com.notifmate.helper.CustomActivity
import java.io.IOException
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID

class SenderActivity : CustomActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var editTextData: EditText
    private lateinit var btnSendData: Button
    private lateinit var deviceListAdapter: DeviceListAdapter
    private var discoveredDevices: MutableList<BluetoothDevice> = mutableListOf()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var oos: ObjectOutputStream? = null
    private val uuid = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66") // UUID for the service
    private var discoverDevicesReceiver: BroadcastReceiver? = null

    // Define rescan interval (e.g., 30 seconds)
    private val rescanIntervalMillis: Long = 30000
    private val handler = Handler(Looper.getMainLooper())

    // Create a Runnable for periodic rescan
    private val rescanRunnable = object : Runnable {
        override fun run() {
            // Perform rescan and update the list of discovered devices
            rescanDevices()
            // Schedule the next rescan
            handler.postDelayed(this, rescanIntervalMillis)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender)

        // Initialize Bluetooth adapter
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Check if Bluetooth is supported on this device
        if (bluetoothAdapter == null) {
            handleBluetoothNotSupported()
            return
        }

        // Initialize RecyclerView and adapter
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        deviceListAdapter = DeviceListAdapter(discoveredDevices, ::connectToDevice)
        recyclerView.adapter = deviceListAdapter

        editTextData = findViewById(R.id.editTextData)
        btnSendData = findViewById(R.id.btnSendData)



        btnSendData.setOnClickListener {
            val dataToSend = editTextData.text.toString()
            sendDatas(dataToSend)
        }

        // Check permissions and request if necessary
        checkPermissionsAndDiscoverDevices()
    }

    private fun handleBluetoothNotSupported() {
        Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG).show()
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

        if (permissionsToRequest.isNotEmpty()) {
            // Request all necessary permissions
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Permissions already granted, start discovering devices
            discoverDevices()
        }
    }

    // Method to update the visibility of the views based on connection status
    private fun updateViewVisibility(isConnected: Boolean) {
        if (isConnected) {
            // Device is connected, show EditText and Send button, hide RecyclerView
            editTextData.visibility = View.VISIBLE
            btnSendData.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            // No device is connected, hide EditText and Send button, show RecyclerView
            editTextData.visibility = View.GONE
            btnSendData.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    // Request permissions
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.values.all { it }
            if (allPermissionsGranted) {
                // Permissions granted, start discovering devices
                discoverDevices()
            } else {
                // Handle permissions denied case
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

        // Create a BroadcastReceiver to handle discovered devices
        discoverDevicesReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        // Filter devices with non-null names and avoid duplicates
                        if (it.name != null && !discoveredDevices.contains(it)) {
                            // Add device to the list and notify the adapter
                            discoveredDevices.add(it)
                            deviceListAdapter.notifyItemInserted(discoveredDevices.size - 1)
                        }
                    }
                }
            }
        }

        // Register the BroadcastReceiver for Bluetooth device discovery
        registerReceiver(discoverDevicesReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        // Start Bluetooth device discovery
        try {
            bluetoothAdapter.startDiscovery()
        } catch (e: SecurityException) {
            showPermissionError("Permission denied for starting Bluetooth discovery.")
        }
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

        // Remove devices from the list and notify the adapter
        for (device in devicesToRemove) {
            val index = discoveredDevices.indexOf(device)
            if (index != -1) {
                discoveredDevices.removeAt(index)
                deviceListAdapter.notifyItemRemoved(index)
            }
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasBluetoothConnectPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            val hasBluetoothScanPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            return hasBluetoothConnectPermission && hasBluetoothScanPermission
        } else {
            val hasLocationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            return hasLocationPermission
        }
    }

    // Connect to a selected device
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        // Stop discovery to prevent interference
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        try {
            // Establish connection
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket?.connect()

            // Initialize output stream for sending data
            outputStream = bluetoothSocket?.outputStream
            oos = ObjectOutputStream(outputStream)
            // Display a success message or update UI
            runOnUiThread {
                updateViewVisibility(true)
                sendDatas("dataToSend")
                Toast.makeText(this, "Connected to: ${device.name}", Toast.LENGTH_SHORT).show()
            }

        } catch (e: IOException) {
            // Handle connection error
            runOnUiThread {
                Toast.makeText(this, "Failed to connect to: ${device.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Send data through Bluetooth socket
    private fun sendDatas(data: String) {
        try {
            // Convert the data string to bytes
            val dataBytes = data.toByteArray(Charset.defaultCharset())

            // Ensure outputStream is not null
            if (oos == null) {
                runOnUiThread {
                    Toast.makeText(this, "Output stream is null. Failed to send data", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // Write data to the output stream
          /*  outputStream?.write(dataBytes)
            outputStream?.flush()*/ // Flush the output stream to ensure immediate transmission

            oos!!.writeObject(data)
            oos!!.flush()

            // Notify the user about the successful data transmission
            runOnUiThread {
                Toast.makeText(this, "Data sent successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            // Handle data sending errors
            runOnUiThread {
                Toast.makeText(this, "Failed to send data", Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onResume() {
        super.onResume()
        // Start periodic rescans when the activity is resumed
        handler.postDelayed(rescanRunnable, rescanIntervalMillis)
    }

    override fun onPause() {
        super.onPause()
        // Stop periodic rescans when the activity is paused
        handler.removeCallbacks(rescanRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the BroadcastReceiver
        discoverDevicesReceiver?.let {
            unregisterReceiver(it)
        }
        // Close the Bluetooth socket if open
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            // Handle error closing socket
        }
    }
}
