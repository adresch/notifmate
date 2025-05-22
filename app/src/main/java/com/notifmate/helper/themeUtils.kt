package com.notifmate.helper

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate

object ThemeUtils {
    fun applySavedTheme(context: Context) {
        val savedTheme = CustomUtils.getThemePreference(context)
        Log.d("MYDEBUG", "THEME UTILS Retrieved Theme Preference: $savedTheme") // Debugging

        when (savedTheme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
