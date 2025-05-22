package com.notifmate.adapter

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.notifmate.R
import com.notifmate.helper.CustomUtils

class ExcludedAppsAdapter(
    private val context: Context,
    private val appList: List<Pair<String, String>> // (App Name, Package Name)
) : RecyclerView.Adapter<ExcludedAppsAdapter.AppViewHolder>() {

    private val packageManager: PackageManager = context.packageManager
    private val excludedApps = CustomUtils.getExcludedApps(context) // Load from SharedPreferences

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        val appName: TextView = itemView.findViewById(R.id.app_name)
        val appSwitch: Switch = itemView.findViewById(R.id.app_switch)
        val container: View = itemView // Reference to the entire item view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.exclude_app_item, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val (appName, packageName) = appList[position]

        holder.appName.text = appName
        holder.appSwitch.setOnCheckedChangeListener(null) // Reset listener to prevent unwanted calls
        holder.appSwitch.isChecked = excludedApps.contains(packageName)

        // Load app icon
        try {
            val appIconDrawable = packageManager.getApplicationIcon(packageName)
            holder.appIcon.setImageDrawable(appIconDrawable)
        } catch (e: PackageManager.NameNotFoundException) {
            holder.appIcon.setImageResource(R.drawable.placeholder) // Fallback icon
        }

        // Update background color based on switch state
        updateItemBackground(holder)

        // Handle switch toggle
        holder.appSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                excludedApps.add(packageName)
            } else {
                excludedApps.remove(packageName)
            }
            saveExcludedApps()
            updateItemBackground(holder) // Update background color based on the new state
        }

        // Handle item click to toggle switch
        holder.container.setOnClickListener {
            holder.appSwitch.toggle() // Toggle switch programmatically
        }
    }

    override fun getItemCount(): Int = appList.size

    private fun saveExcludedApps() {
        CustomUtils.saveExcludedApps(context, excludedApps) // Save to SharedPreferences
    }

    // Function to update background color based on switch state
    private fun updateItemBackground(holder: AppViewHolder) {
        val selectedColor = ContextCompat.getColor(context, R.color.light_gray) // Change to your desired color
        val defaultColor = Color.TRANSPARENT // Default background color

        if (holder.appSwitch.isChecked) {
            holder.container.setBackgroundColor(selectedColor)
        } else {
            holder.container.setBackgroundColor(defaultColor)
        }
    }
}
