package com.notifmate.utils

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.notifmate.helper.CustomUtils.truncate
import java.io.OutputStream
import java.util.LinkedList
import java.util.UUID

object BluetoothSender {
    private val messageQueue = LinkedList<String>()
    private var isSending = false
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val logTag = "BluetoothSender"

    private var previousMessage = ""

    private var receiverAddress: String? = null

    fun setReceiver(address: String) {
        receiverAddress = address
    }

    fun send(message: String, context: Context) {
        synchronized(messageQueue) {
            messageQueue.add(message)
            if (!isSending) {
                isSending = true
                Thread {
                    processQueue(context)  // ← Launches processing
                }.start()
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun processQueue(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (receiverAddress.isNullOrEmpty() || !BluetoothAdapter.checkBluetoothAddress(receiverAddress)) {
            Log.e(logTag, "Invalid Bluetooth Address")
            isSending = false
            return
        }

        try {
            val device = bluetoothAdapter.getRemoteDevice(receiverAddress)
            socket = device.createRfcommSocketToServiceRecord(uuid)
            socket?.connect()
            outputStream = socket?.outputStream

            processLoop()

        } catch (e: Exception) {
            Log.e(logTag, "Error sending Bluetooth message", e)
            isSending = false
        } finally {
            try {
                outputStream?.close()
                socket?.close()
            } catch (e: Exception) {
                Log.e(logTag, "Error closing Bluetooth resources", e)
            }
        }
    }

    // Extracted loop to avoid inline lambda issue with `break`
    private fun processLoop() {
        while (true) {
            val message: String? = synchronized(messageQueue) {
                messageQueue.poll()
            }

            if (message == null) {
                isSending = false
                return  // ✅ No more break — safely exits the loop
            }

            try {
                if (message != previousMessage){
                    outputStream?.write(message.toByteArray())
                    outputStream?.flush()
                    Log.d(logTag, "Sent: ${message.truncate(100)}")
                    previousMessage = message
                }else{
                    Log.d(logTag, "Message not sent, same as previous message")
                }
            } catch (e: Exception) {
                Log.e(logTag, "Error writing message", e)
                clearQueue()
            }

            Thread.sleep(500)
        }
    }

    fun clearQueue() {
        synchronized(messageQueue) {
            messageQueue.clear()
        }
        Log.d(logTag, "Message queue cleared")
    }
}
