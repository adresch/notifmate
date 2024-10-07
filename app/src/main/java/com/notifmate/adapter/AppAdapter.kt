package com.notifmate.adapter

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.notifmate.model.AppInfo
import com.notifmate.R

class AppAdapter(
    private val packageManager: PackageManager,
    private var apps: List<AppInfo>,
    private val selectedApps: MutableSet<String>
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appInfo = apps[position]
        holder.checkBox.text = appInfo.appName
        holder.checkBox.isChecked = selectedApps.contains(appInfo.packageName)

        // Load app icon
        try {
            val appIcon = packageManager.getApplicationIcon(appInfo.packageName)
            holder.appIcon.setImageDrawable(appIcon)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }


        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedApps.add(appInfo.packageName)
            } else {
                selectedApps.remove(appInfo.packageName)
            }
        }
        holder.setIsRecyclable(false)
    }

    override fun getItemCount(): Int {
        return apps.size
    }

    fun filterList(filteredApps: List<AppInfo>) {
        apps = filteredApps
        notifyDataSetChanged()
    }
}


