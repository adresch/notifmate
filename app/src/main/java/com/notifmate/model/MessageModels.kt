package com.notifmate.model

data class NotifMessage(
    val type: String = "NOTIF",
    val packageName: String,
    val title: String,
    val text: String
)

data class MediaMessage(
    val type: String = "MEDIA",
    val title: String,
    val artist: String,
    val state: String,
    val base64Image: String
)

data class MediaState(
    val type: String = "MEDIA_STATE",
    val state: String,
)

data class CordinateMessage(
    val type: String = "COORDS",
    val lat: Double,
    val lon: Double,
    val name: String
)