package com.notifmate.utils

import com.notifmate.model.NotificationViewModel

class DataManager {
    var deviceName:String = ""
    var selectedApps: MutableSet<String> = mutableSetOf()
    var notificationViewModel: NotificationViewModel = NotificationViewModel()

    companion object {
        var dataManager: DataManager? = null
        val instanse: DataManager
            get() = dataManager ?: DataManager().also {
                dataManager = it
            }
    }


}