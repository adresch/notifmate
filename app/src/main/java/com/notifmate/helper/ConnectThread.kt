package com.notifmate.helper

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import com.notifmate.model.AudioModel
import com.notifmate.model.AudioStateModel
import com.notifmate.model.NotificationModel
import java.io.IOException
import java.io.ObjectOutputStream
import java.util.UUID

class ConnectThread @SuppressLint("MissingPermission") constructor(
    bluetoothAdapter: BluetoothAdapter,
    device: BluetoothDevice,
    mContext: Context,
    handler: Handler
) : Thread() {
    private var socket: BluetoothSocket? = null
    private val device: BluetoothDevice
    private val bluetoothAdapter: BluetoothAdapter
    private val writeLock = Any()
    var mContext: Context
    private var oos: ObjectOutputStream? = null
    private val mHandler: Handler


    init {
        var tmp: BluetoothSocket? = null
        this.bluetoothAdapter = bluetoothAdapter
        mHandler = handler
        this.device = device
        this.mContext = mContext
        try {
            tmp = device.createRfcommSocketToServiceRecord(MY_UUID_INSECURE)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        socket = tmp
    }

    @SuppressLint("MissingPermission")
    override fun run() {
        bluetoothAdapter.cancelDiscovery()
        try {
            socket!!.connect()
            val tmpOut = socket?.outputStream
            oos = ObjectOutputStream(tmpOut)
            if (socket?.isConnected == true) {
                val bleDeviceName = bluetoothAdapter.name
                sendData(bleDeviceName)
                val intent = Intent("CLIENT_CONNECTED")
                mContext.sendBroadcast(intent)
            }else{
                Log.e("socketzx", "not connected")
            }
            // Send message to Handler indicating successful connection
            mHandler.obtainMessage(1).sendToTarget()
        } catch (connectException: IOException) {
            Log.d("socketzx", connectException.message.toString())
            // Unable to connect; close the socket and return
            try {
                socket!!.close()
                val intent = Intent("CLIENT_NOT_CONNECTED")
                mContext.sendBroadcast(intent)
            } catch (closeException: IOException) {
                closeException.printStackTrace()
            }
        }
    }


    fun cancel() {
        try {
            socket!!.close()
            val intent = Intent("CLIENT_CLOSE")
            mContext.sendBroadcast(intent)
        } catch (e: IOException) {
            println(e.message)
        }
    }

    fun sendCommand(data: String?): String {
        if (oos != null) {
            try {
                oos!!.writeObject(data)
                oos!!.flush()
                return "Data sent successfully"
            } catch (e: IOException) {
                e.printStackTrace()
                return "Failed to send data"
            }
        } else {
            return "Failed to send data"
        }
    }


    fun sendData(dd: String?) {
        try {
            oos!!.writeObject(dd)
            oos!!.reset() // Call this to avoid memory leaks
        } catch (e: IOException) {
            e.printStackTrace()
            // remember this
//            val intent = Intent("CLIENT_CLOSE")
//            mContext.sendBroadcast(intent)
        }
    }

    fun sendAppData(data: MutableSet<String>) {
        try {
            Log.d("socketzx", socket?.isConnected.toString())
            if (socket != null && socket?.isConnected == true) {
                oos!!.writeObject(data)
                oos!!.reset() // Call this to avoid memory leaks
            }else{
                Log.d("socketzx", "not connected")
            }

        } catch (e: IOException) {
            e.printStackTrace()
            val intent = Intent("CLIENT_CLOSE")
            mContext.sendBroadcast(intent)
        }

    }


    fun sendData(notiData: List<NotificationModel>): String {
        if (oos == null || socket == null || !socket!!.isConnected) {
            return "Socket is not connected or stream is not initialized"
        }
        try {
            oos!!.writeObject(notiData)
            oos!!.reset() // Call this to avoid memory leaks
        } catch (e: IOException) {
            Log.d("socketError", e.message.toString())
            e.printStackTrace()
            return "error"
        }
        return "success"
    }

    fun sendNotiData(currentNoti: NotificationModel?): String {
        if (oos == null || socket == null || !socket!!.isConnected) {
            return "Socket is not connected or stream is not initialized"
        }
        try {
            oos!!.writeObject(currentNoti)
            oos!!.reset() // Call this to avoid memory leaks
        } catch (e: IOException) {
            Log.d("socketError", e.message.toString())
            e.printStackTrace()
            val intent = Intent("BLE_ERROR")
            mContext.sendBroadcast(intent)
            return "error"
        }
        return "success"
    }

    fun sendAudioData(currentAudio: AudioModel?): String {
        if (oos == null || socket == null || !socket!!.isConnected) {
            return "Socket is not connected or stream is not initialized"
        }

        try {
            Log.d("MY_DEBUG", "sendAudioData")
            oos!!.writeObject(currentAudio)
            oos!!.reset() // Call this to avoid memory leaks
        } catch (e: IOException) {
            Log.d("MY_DEBUG", "AAAAAA" + e.message.toString())
            e.printStackTrace()
            val intent = Intent("BLE_ERROR")
            mContext.sendBroadcast(intent)
            return "error"
        }
        return "success"
    }

    fun sendAudioStateData(currentAudioState: AudioStateModel?): String {
        if (oos == null || socket == null || !socket!!.isConnected) {
            return "Socket is not connected or stream is not initialized"
        }
        try {
            Log.d("MY_DEBUG", "sendAudioData")
            oos!!.writeObject(currentAudioState)
            oos!!.reset() // Call this to avoid memory leaks
        } catch (e: IOException) {
            Log.d("MY_DEBUG", e.message.toString())
            e.printStackTrace()
            val intent = Intent("BLE_ERROR")
            mContext.sendBroadcast(intent)
            return "error"
        }
        return "success"
    }


    fun getConnectedDevice(): BluetoothDevice {
        return device
    }


    companion object {
        private val MY_UUID_INSECURE: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }

}

