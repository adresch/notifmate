package com.notifmate.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import com.notifmate.R

// Define the DataListAdapter class
class DataListAdapter(private val dataList: List<String>) : RecyclerView.Adapter<DataListAdapter.DataViewHolder>() {

    // Define the DataViewHolder class for the ViewHolder
    class DataViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // TextView for displaying each data item
        val dataTextView: TextView = itemView.findViewById(R.id.dataTextView)
    }

    // Create a new ViewHolder instance
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataViewHolder {
        // Inflate the item layout for each data item
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_data, parent, false)
        return DataViewHolder(itemView)
    }

    // Bind data to the ViewHolder
    override fun onBindViewHolder(holder: DataViewHolder, position: Int) {
        val data = dataList[position]
        holder.dataTextView.text = data
    }

    // Return the total number of data items
    override fun getItemCount(): Int = dataList.size
}
