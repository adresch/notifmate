package com.notifmate

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.notifmate.ActivityReceiver
import com.notifmate.ActivitySender
import com.notifmate.helper.CustomUtils
import com.google.android.material.card.MaterialCardView
import com.notifmate.R
import com.notifmate.model.NotifMateActivity

class ActivityHome : NotifMateActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val appScreenPreference = CustomUtils.getAppScreenPreference(this)

        if (appScreenPreference == "receiver") {
            val intent = Intent(this, ActivityReceiver::class.java)
            startActivity(intent)
        } else if (appScreenPreference == "sender") {
            val intent = Intent(this, ActivitySender::class.java)
            startActivity(intent)
        }

        val receiverButton = findViewById<MaterialCardView>(R.id.receiverView)
        val senderButton = findViewById<MaterialCardView>(R.id.senderView)

        receiverButton.setOnClickListener {
            Log.i("MYDEBUG", "receiverButton")
            CustomUtils.saveAppScreenPreference(this, "receiver")
            val intent = Intent(this, ActivityReceiver::class.java)
            startActivity(intent)
        }

        senderButton.setOnClickListener {
            Log.i("MYDEBUG", "senderButton")
            CustomUtils.saveAppScreenPreference(this, "sender")
            val intent = Intent(this, ActivitySender::class.java)
            startActivity(intent)
        }
    }
}
