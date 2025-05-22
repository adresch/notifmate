package com.notifmate.model

data class NotificationItem(
    val appName: String,
    val title: String,
    val text: String,
    var isExpanded: Boolean = false // Track visibility per item
)