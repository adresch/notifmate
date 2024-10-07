package com.notifmate.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.notifmate.R
import com.notifmate.adapter.DataListAdapter
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.ObjectInputStream
import java.util.UUID

class ReceiverActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var statusTextView: TextView
    private lateinit var dataRecyclerView: RecyclerView
    private lateinit var dataListAdapter: DataListAdapter
    private val receivedDataList: MutableList<String> = mutableListOf()

    private var serverSocket: BluetoothServerSocket? = null
    private var connectionSocket: BluetoothSocket? = null

    // Declare an ActivityResultLauncher for discoverability
    private val discoverableActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_CANCELED) {
                // Discoverable mode was canceled
                Toast.makeText(this, "Discoverable mode not enabled.", Toast.LENGTH_SHORT).show()
            } else {
                // Discoverable mode enabled
                val duration = result.resultCode
                statusTextView.text = "Device is discoverable for $duration seconds."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiver)

        // Initialize Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Initialize views
        statusTextView = findViewById(R.id.statusTextView)
        dataRecyclerView = findViewById(R.id.dataRecyclerView)

        // Initialize RecyclerView and adapter for data
        dataRecyclerView.layoutManager = LinearLayoutManager(this)
        dataListAdapter = DataListAdapter(receivedDataList)
        dataRecyclerView.adapter = dataListAdapter

        // Check if Bluetooth is supported on this device
        if (bluetoothAdapter == null) {
            // Bluetooth is not supported on this device
            Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG)
                .show()
            finish()
        }

        // Call the function to enable discoverable mode
        enableDiscoverableMode()

        // Start Bluetooth server to listen for incoming connections
        startBluetoothServer()
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

    @SuppressLint("MissingPermission")
    private fun startBluetoothServer() {
        Thread {
            try {
                // Create a Bluetooth server socket using UUID
                val uuid = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("Receiver", uuid)

                // Listen for incoming connections
                statusTextView.text = "Waiting for a connection..."
                connectionSocket = serverSocket?.accept() // This is a blocking call

                // Once a connection is established
                runOnUiThread {
                    statusTextView.text = "Connected to sender device!"
                }

                // Start receiving data
                startReceivingData()

            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    statusTextView.text = "Failed to start server or accept connection."
                }
            }
        }.start()
    }

    private fun startReceivingData() {
        Thread {
            try {
                // Ensure connectionSocket is not null
                if (connectionSocket == null) {
                    runOnUiThread {
                        statusTextView.text = "Connection socket is null. Failed to receive data."
                    }
                    return@Thread
                }

                // Create an input stream to receive data from the sender device
                val inputStream = connectionSocket!!.inputStream

                // Wrap the input stream with ObjectInputStream
                val objectInputStream = ObjectInputStream(inputStream)

                // Start receiving data
                while (true) {
                    // Read an object from the input stream
                    val receivedObject = objectInputStream.readObject()

                    // Check if the received object is of expected type (String in this case)
                    if (receivedObject is String) {
                        val receivedData = receivedObject as String

                        // Add the received data to the list
                        receivedDataList.add(receivedData)

                        // Notify the adapter to update the RecyclerView
                        runOnUiThread { dataListAdapter.notifyDataSetChanged() }
                    } else {
                        // Handle unexpected object type
                        runOnUiThread {
                            statusTextView.text = "Received unexpected object type: ${receivedObject?.javaClass?.simpleName}"
                        }
                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    statusTextView.text = "Failed to receive data from sender device: ${e.message}"
                }
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
                runOnUiThread {
                    statusTextView.text = "Failed to read object from input stream: ${e.message}"
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources when the activity is destroyed
        serverSocket?.close()
        connectionSocket?.close()
    }

    companion object {
        const val REQUEST_CODE_DISCOVERABLE = 1
    }
}

