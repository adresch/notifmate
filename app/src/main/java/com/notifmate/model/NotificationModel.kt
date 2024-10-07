package com.notifmate.model

import android.graphics.Bitmap
import java.io.Serializable

data class NotificationModel(
    val title: String,
    val msg: String,
    val packageName: String
) :
    Serializable

data class AudioModel(
    val songName: String? = null,
    val artistName: String? = null,
    val musicArtBase64: String? = null
) :
    Serializable

data class AudioStateModel(
    val playBackState: Int? = null
):
    Serializable