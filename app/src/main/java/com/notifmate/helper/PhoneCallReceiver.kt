package com.notifmate.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import java.util.Date
import java.util.Objects

class PhoneCallReceiver  : BroadcastReceiver() {

    private var listener: PhoneCallListener? = null

    fun setPhoneCallListener(listener: PhoneCallListener) {
        this.listener = listener
    }


    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.NEW_OUTGOING_CALL") {
            savedNumber =
                Objects.requireNonNull(intent.extras)
                    ?.getString("android.intent.extra.PHONE_NUMBER")
            Log.d("PhonecallReceiver", "Outgoing call: $savedNumber")
        } else {
            val extras = intent.extras ?: return
            val stateStr = extras.getString(TelephonyManager.EXTRA_STATE)
            val number: String? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            } else {
                // You might not be able to get the incoming number directly due to restrictions.
                // Consider alternative ways to get the incoming number, such as using a third-party service
                // or ensuring your app is the default dialer.
                ""
            }

            Log.d("PhonecallReceiver", "onReceive: $stateStr")
            var state = 0
            if (stateStr != null) {
                when (stateStr) {
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        state = TelephonyManager.CALL_STATE_IDLE
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        state = TelephonyManager.CALL_STATE_OFFHOOK
                    }
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        state = TelephonyManager.CALL_STATE_RINGING
                    }
                }

                Log.d("PhonecallReceiver", "Call state: $state, Incoming number: $number")
                Log.d("PhonecallReceiver", "Call state: $state, Incoming number: $number")
                onCallStateChanged(context, state, number)
//                if (number != null) {
//
//                } else {
//                    //  Log.d("PhonecallReceiver", "number null: $state")
//                }

            }
        }
    }

    private fun onCallStateChanged(context: Context?, state: Int, number: String?) {
        if (lastState == state) {
            //No change, debounce extras
            return
        }
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d("CALL_STATE_RINGING", "RINGING : $number")
                isIncoming = true
                callStartTime = Date()
                savedNumber = number
                listener?.onIncomingCallStarted(context, number, callStartTime)
            }

            TelephonyManager.CALL_STATE_OFFHOOK ->             //Transition of ringing->offhook are pickups of incoming calls.  Nothing done on them
                if (lastState != TelephonyManager.CALL_STATE_RINGING) {
                    isIncoming = false
                    callStartTime = Date()
                    listener?.onOutgoingCallStarted(context, savedNumber, callStartTime)
                }

            TelephonyManager.CALL_STATE_IDLE ->             //Went to idle-  this is the end of a call.  What type depends on previous state(s)
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    //Ring but no pickup-  a miss
                    listener?.onMissedCall(context, savedNumber, callStartTime)
                } else if (isIncoming) {
                    listener?.onIncomingCallEnded(context, savedNumber, callStartTime, Date())
                } else {
                    listener?.onOutgoingCallEnded(context, savedNumber, callStartTime, Date())
                }
        }

        lastState = state
    }

    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var callStartTime: Date? = null
        private var isIncoming = false
        private var savedNumber: String? =
            null //because the passed incoming is only valid in ringing
    }

}