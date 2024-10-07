package com.notifmate.adapter

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.notifmate.R
import com.notifmate.model.NotificationModel


class ReceiverNotificationAdapter(private val onDeleteListener: OnNotificationDeleteListener) :
    RecyclerView.Adapter<ReceiverNotificationAdapter.ViewHOlder>() {
    var notificationList = mutableListOf<NotificationModel>()
    private var selectedPosition: Int = RecyclerView.NO_POSITION
    private var selectedItemPosition = RecyclerView.NO_POSITION
    var deleteNotificationList: ArrayList<NotificationModel> = arrayListOf()

    fun updateDeleteList(newList: List<NotificationModel>) {
        deleteNotificationList.clear()
        deleteNotificationList.addAll(newList)
        notifyDataSetChanged()
    }

    fun updateNotificationLiST(newList: List<NotificationModel>) {
        notificationList.clear()
        notificationList.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ReceiverNotificationAdapter.ViewHOlder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.receiver_item, parent, false)
        return ViewHOlder(itemView)
    }

    inner class ViewHOlder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val header: TextView = itemView.findViewById(R.id.header_text)
        private val title: TextView = itemView.findViewById(R.id.title_text)
        private val description: TextView = itemView.findViewById(R.id.description_text)
        val itemCard: View = itemView.findViewById(R.id.item_card_layout)
        val deleteBtn: Button = itemView.findViewById(R.id.delete_button)

        fun bind(item: NotificationModel, position: Int) {
            header.text = item.packageName
            title.text = item.title
            description.text = item.msg
            deleteBtn.visibility = View.GONE
            if (selectedItemPosition == position) {
                itemCard.setBackgroundResource(R.drawable.selected_card_background)
                deleteBtn.visibility = View.VISIBLE
            } else {
                itemCard.setBackgroundResource(R.drawable.card_background)
                deleteBtn.visibility = View.GONE
            }
        }

        init {
            // Set initial background for the CardView
            itemCard.setOnClickListener {
                // Toggle delete button visibility
//                deleteBtn.visibility = if (deleteBtn.visibility == View.VISIBLE) {
//                    View.GONE
//                } else {
//                    View.VISIBLE
//                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: ReceiverNotificationAdapter.ViewHOlder, position: Int) {
        holder.bind(notificationList[position], position)


        holder.deleteBtn.setOnClickListener {
            onDeleteListener.onNotificationDelete(selectedItemPosition)
        }



        holder.itemView.setOnClickListener {
            val previousSelectedItemPosition = selectedItemPosition
            Log.e("TAG", "previousSelectedItemPosition: $previousSelectedItemPosition")
            selectedItemPosition = holder.adapterPosition
            notifyItemChanged(previousSelectedItemPosition)
            notifyItemChanged(selectedItemPosition)
            //notifyDataSetChanged()
        }

        Log.e("TAG", "selectedItemPosition: $selectedItemPosition")
    }

    override fun getItemCount(): Int {
        return if (notificationList.size > 0) {
            notificationList.size
        } else {
            0
        }
    }
    private var previousNoti = ""
    @SuppressLint("NotifyDataSetChanged")
    fun submitNotiData(it: NotificationModel) {
        if (previousNoti != it.toString()) {
            notificationList.add(0, it)
            var test = it.toString()
            previousNoti = it.toString()
            if (selectedItemPosition >= 0) {
                selectedItemPosition++
            }
            notifyItemInserted(0)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearData() {
        if (notificationList.size > 0) {
            notificationList.clear()
            notifyDataSetChanged()
        }
    }

    fun removeNotification(position: Int) {
        if (position != -1) {
            val removeElememnt = notificationList.removeAt(selectedItemPosition)
            deleteNotificationList.add(removeElememnt)
            notifyItemRemoved(selectedItemPosition)
            selectedItemPosition = -1
        }
    }

    interface OnNotificationDeleteListener {
        fun onNotificationDelete(position: Int)
    }

}

