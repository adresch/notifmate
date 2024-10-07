package com.notifmate.helper

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import com.notifmate.model.AudioModel
import com.notifmate.model.AudioStateModel
import com.notifmate.model.NotificationModel
import java.io.IOException
import java.io.ObjectInputStream
import java.util.UUID

class AcceptThread @SuppressLint("MissingPermission") constructor(
    bluetoothAdapter: BluetoothAdapter,
    mContext: Context,
    handler: Handler) : Thread() {
    private val serverSocket: BluetoothServerSocket?
    private val mHandler: Handler
    var mContext: Context

    init {
        var tmp: BluetoothServerSocket? = null
        mHandler = handler
        this.mContext = mContext
        try {
            tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID_INSECURE)
        } catch (e: Exception) {
            Log.e("AcceptThread", "Socket's error ", e)
            e.printStackTrace()
        }
        serverSocket = tmp
    }

    override fun run() {
        var  socket: BluetoothSocket? = null
        while (true) {
            try {
                socket = serverSocket!!.accept()
                Log.d("socket", "socket: ${socket.isConnected}")
                if (socket.isConnected) {
                    Log.d("AcceptThread", "recieverSocket: Connected")
                    val intent = Intent("HOST_CONNECTED")
                    mContext.sendBroadcast(intent)
                }else{
                    Log.e("AcceptThread", "Socket's not connected")
                }

                val tmpIn = socket.inputStream
                val ois = ObjectInputStream(tmpIn)
                while (true) {
                    try {
                        when (val receivedObject = ois.readObject()) {
                            is  ArrayList<*> -> {
                                // Handle String
                                val readMsg = mHandler.obtainMessage(1, receivedObject)
                                // Send the obtained string to the UI activity
                                readMsg.sendToTarget()
                            }

                            is String -> {
                                // Handle String
                                val readMsg = mHandler.obtainMessage(3, receivedObject)
                                // Send the obtained string to the UI activity
                                readMsg.sendToTarget()
                            }
                            is MutableSet<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                val setOfString = receivedObject as MutableSet<String>
                                // Handle MutableSet<String>
                                // Do whatever you want with the set
                                // For example, you can send each element to the UI activity
                                setOfString.forEach { stringElement ->
                                    val readMsg = mHandler.obtainMessage(2, stringElement)
                                    readMsg.sendToTarget()
                                }
                            }

                            is NotificationModel -> {
                                // Handle AudioModel
                                val readMsg = mHandler.obtainMessage(4, receivedObject)
                                readMsg.sendToTarget()
                            }

                            is AudioModel -> {
                                // Handle AudioModel
                                val readMsg = mHandler.obtainMessage(5, receivedObject)
                                readMsg.sendToTarget()
                            }

                            is AudioStateModel -> {
                                // Handle AudioModel
                                val readMsg = mHandler.obtainMessage(6, receivedObject)
                                readMsg.sendToTarget()
                            }

//                            is ArrayList<NotificationModel> -> {
//
//                            }
                            else -> {
                                // Handle unknown type
                                println("Received unknown type: ${receivedObject.javaClass.name}")
                                println("Received unknown type: $receivedObject")
                            }
                        }
                    } catch (e: ClassNotFoundException) {
                        Log.d("MY_DEBUG", "run: ClassNotFoundException:$e")
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                Log.d("MY_DEBUG", "AAAAAA :$e")
                val intent = Intent("HOST_CLOSE")
                mContext.sendBroadcast(intent)
                cancel()
                break
            }
        }
    }

    fun cancel() {
        try {
            serverSocket!!.close()
            val intent = Intent("HOST_CLOSE")
            Log.d("MY_DEBUG", "HOST_CLOSE")
            mContext.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.d("TAG", "cancel: ")
        }
    }


    companion object {
        private const val APP_NAME = "NotiMate"
        private val MY_UUID_INSECURE: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }
}

