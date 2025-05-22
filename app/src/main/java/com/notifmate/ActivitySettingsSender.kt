package com.notifmate

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.notifmate.R
import com.google.android.material.button.MaterialButton
import com.notifmate.helper.CustomUtils
import com.notifmate.model.NotifMateActivity

class ActivitySettingsSender : NotifMateActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_sender)

        sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)

        val radioGroup = findViewById<RadioGroup>(R.id.radio_theme_group)
        val alwaysBright = findViewById<RadioButton>(R.id.check_button5)
        val alwaysDark = findViewById<RadioButton>(R.id.check_button6)
        val followSystem = findViewById<RadioButton>(R.id.check_button7)

        val excludeApps = findViewById<MaterialButton>(R.id.excludeApps)

        val btnClose = findViewById<MaterialButton>(R.id.btnClose)

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

        // Close button
        btnClose.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
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
