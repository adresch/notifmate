package com.notifmate.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.notifmate.R
import com.notifmate.model.NotificationItem
import com.google.android.material.button.MaterialButton

class NotificationAdapter(
    private var notificationList: MutableList<NotificationItem>
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerText: TextView = itemView.findViewById(R.id.header_text)
        val titleText: TextView = itemView.findViewById(R.id.title_text)
        val descriptionText: TextView = itemView.findViewById(R.id.description_text)
        val deleteButton: MaterialButton = itemView.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.receiver_item, parent, false)
        return NotificationViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val currentItem = notificationList[position]

        holder.headerText.text = currentItem.appName
        holder.titleText.text = currentItem.title
        holder.descriptionText.text = currentItem.text

        // Show or hide delete button based on `isExpanded`
        holder.deleteButton.visibility = if (currentItem.isExpanded) View.VISIBLE else View.INVISIBLE

        holder.itemView.setOnClickListener {
            if (holder.adapterPosition != RecyclerView.NO_POSITION &&
                holder.adapterPosition < notificationList.size) {
                toggleItemExpansion(holder.adapterPosition)
            }
        }

        holder.deleteButton.setOnClickListener {
            if (holder.adapterPosition != RecyclerView.NO_POSITION &&
                holder.adapterPosition < notificationList.size) {
                removeItem(holder.adapterPosition)
            }
        }
    }

    override fun getItemCount(): Int = notificationList.size

    private fun toggleItemExpansion(position: Int) {
        if (position < 0 || position >= notificationList.size) return

        // Collapse others
        notificationList.forEachIndexed { index, item ->
            if (index != position && item.isExpanded) {
                item.isExpanded = false
                notifyItemChanged(index)
            }
        }

        // Toggle this one
        notificationList[position].isExpanded = !notificationList[position].isExpanded
        notifyItemChanged(position)
    }

    fun removeItem(position: Int) {
        notificationList.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, notificationList.size) // Fix item positions
    }

    fun addItem(notification: NotificationItem, recyclerView: RecyclerView) {
        notificationList.forEach { it.isExpanded = false }
        notificationList.add(0, notification)
        notifyItemInserted(0)
        recyclerView.scrollToPosition(0)
    }
}

