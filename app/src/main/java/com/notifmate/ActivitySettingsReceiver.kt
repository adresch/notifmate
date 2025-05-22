package com.notifmate

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.notifmate.adapter.ExcludedAppsAdapter
import com.notifmate.helper.CustomUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.notifmate.R
import com.notifmate.model.NotifMateActivity

class ActivitySettingsReceiver : NotifMateActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_receiver)


        sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)

        val currentNoti = CustomUtils.getCurrentNoti(this)
        val currentOverlay = CustomUtils.getCurrentOverlay(this)
        val currentMusic = CustomUtils.getCurrentMusic(this)

        val radioGroup = findViewById<RadioGroup>(R.id.radio_theme_group)
        val alwaysBright = findViewById<RadioButton>(R.id.check_button5)
        val alwaysDark = findViewById<RadioButton>(R.id.check_button6)
        val followSystem = findViewById<RadioButton>(R.id.check_button7)
        val btnClose = findViewById<MaterialButton>(R.id.btnClose)

        val excludeApps = findViewById<MaterialCardView>(R.id.excludeAppsSettingsSender)

        // Load saved theme preference
        when (sharedPreferences.getString("theme", "system")) {
            "light" -> alwaysBright.isChecked = true
            "dark" -> alwaysDark.isChecked = true
            "system" -> followSystem.isChecked = true
        }

        // Handle theme selection
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.check_button5 -> setThemePreference("light", AppCompatDelegate.MODE_NIGHT_NO)
                R.id.check_button6 -> setThemePreference("dark", AppCompatDelegate.MODE_NIGHT_YES)
                R.id.check_button7 -> setThemePreference("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        val remote_local = findViewById<RadioButton>(R.id.check_button_notif_remote_local)
        val remote = findViewById<RadioButton>(R.id.check_button_notif_remote_only)
        val local = findViewById<RadioButton>(R.id.check_button_notif_local_only)

        if (currentNoti == "local"){
            local.isChecked = true
        }else if (currentNoti == "remote_local"){
            remote_local.isChecked = true
        }else{
            remote.isChecked = true
        }
        val radioNotifGroup = findViewById<RadioGroup>(R.id.radio_notif_source_group)
        radioNotifGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.check_button_notif_remote_local -> CustomUtils.saveNotiPreference(this, "remote_local")
                R.id.check_button_notif_remote_only -> CustomUtils.saveNotiPreference(this, "remote")
                R.id.check_button_notif_local_only -> CustomUtils.saveNotiPreference(this, "local")
            }
        }

        val overlay_always = findViewById<RadioButton>(R.id.check_button_overlay_always)
        val overlay_never = findViewById<RadioButton>(R.id.check_button_overlay_never)
        val overlay_background = findViewById<RadioButton>(R.id.check_button_overlay_background)

        if (currentOverlay == "always"){
            overlay_always.isChecked = true
        }else if (currentOverlay == "background"){
            overlay_background.isChecked = true
        }else{
            overlay_never.isChecked = true
        }

        val radioOverlayGroup = findViewById<RadioGroup>(R.id.radio_overlay_group)
        radioOverlayGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.check_button_overlay_always -> CustomUtils.saveOverlayPreference(this, "always")
                R.id.check_button_overlay_never -> CustomUtils.saveOverlayPreference(this, "never")
                R.id.check_button_overlay_background -> CustomUtils.saveOverlayPreference(this, "background")
            }
        }

        val music_remote = findViewById<RadioButton>(R.id.check_button_music_remote)
        val music_local = findViewById<RadioButton>(R.id.check_button_music_local)

        if (currentMusic == "local"){
            music_local.isChecked = true
        }else{
            music_remote.isChecked = true
        }

        val radioMusicGroup = findViewById<RadioGroup>(R.id.radio_music_group)
        radioMusicGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.check_button_music_remote -> CustomUtils.saveMusicPreference(this, "remote")
                R.id.check_button_music_local -> CustomUtils.saveMusicPreference(this, "local")
            }
        }

        // Close button
        btnClose.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        excludeApps.setOnClickListener {
            Log.i("MYDEBUG", "excludeApps clicked")
            showExcludeAppsDialog()
        }
    }

    private fun showExcludeAppsDialog() {
        val apps = CustomUtils.getInstalledApps(this) // Fetch installed apps
        val dialogView = layoutInflater.inflate(R.layout.exclude_apps_dialog, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.apps_recycler_view)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ExcludedAppsAdapter(this, apps) // ✅ No extra argument needed

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.excludeApps))
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .create()

        dialog.setOnDismissListener {
            val intent = Intent("com.notifmate.UPDATE_EXCLUDED_APP_LIST")
            sendBroadcast(intent)
        }

        dialog.show()
    }

    private fun setThemePreference(theme: String, mode: Int) {
        val currentTheme = sharedPreferences.getString("theme", "system")

        if (currentTheme != theme) { // Restart only if theme changed
            sharedPreferences.edit().putString("theme", theme).apply()
            AppCompatDelegate.setDefaultNightMode(mode)

            // Restart the activity properly to avoid infinite loops
            val intent = Intent(this, ActivitySettingsSender::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            overridePendingTransition(0, 0) // No animation
        }
    }
}
