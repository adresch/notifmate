package com.notifmate.helper

import android.content.Context
import java.util.Date

interface PhoneCallListener {
    fun onIncomingCallStarted(ctx: Context?, number: String?, start: Date?)
    fun onOutgoingCallStarted(ctx: Context?, number: String?, start: Date?)
    fun onIncomingCallEnded(ctx: Context?, number: String?, start: Date?, end: Date?)
    fun onOutgoingCallEnded(ctx: Context?,number: String?, start: Date?, end: Date?)
    fun onMissedCall(ctx: Context?,number: String?, start: Date?)
}
