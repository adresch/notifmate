package com.notifmate.utils

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import java.util.Date

object Constant {
    fun getCallerNameFromContact(context: Context?, phoneNumber: String?): String? {
        var callerName: String? = null
        try {
            context?.let {
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"
                val selectionArgs = arrayOf(phoneNumber)
                val cursor: Cursor? = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            callerName = it.getString(displayNameIndex)
                        } else {
                            Log.e("getCallerNameFromContact", "Column index for display name is invalid")
                        }
                    } else {
                        Log.e("getCallerNameFromContact", "No matching contact found for phone number: $phoneNumber")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("getCallerNameFromContact", "Exception: ${e.message}")
            e.printStackTrace()
        }
        return callerName
    }


    fun calculateDuration(start: Date?, end: Date?): String {
        if (start != null && end != null) {
            val differenceInMillis = end.time - start.time
            val seconds = differenceInMillis / 1000

            return if (seconds < 60) {
                "$seconds seconds"
            } else {
                val minutes = seconds / 60
                "$minutes minutes"
            }
        }
        return ""
    }
}