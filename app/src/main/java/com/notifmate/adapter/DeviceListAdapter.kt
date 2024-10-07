package com.notifmate.adapter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.notifmate.R

class DeviceListAdapter(
    private val devices: List<BluetoothDevice>,
    private val onConnectClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceNameTextView: TextView = itemView.findViewById(R.id.tvDeviceName)
        val connectButton: Button = itemView.findViewById(R.id.btnConnect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(itemView)
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.deviceNameTextView.text = device.name ?: "Unknown device"

        // Configure the Connect button click event
        holder.connectButton.setOnClickListener {
            // Call the callback function to connect to the device

            onConnectClick(device)
        }
    }

    override fun getItemCount(): Int = devices.size
}
