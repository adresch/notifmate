package com.notifmate.model

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize

open class NotifMateActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Firebase
        Firebase.initialize(this)

        // Optional: Enable Crashlytics collection in debug builds
        Firebase.crashlytics.setCrashlyticsCollectionEnabled(true)

        // Set global uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("GlobalCrashHandler", "Uncaught exception in thread ${thread.name}", throwable)

            // Report to Firebase
            Firebase.crashlytics.recordException(throwable)

            // Let the system handle the crash (important!)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
    }
}