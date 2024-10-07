package com.notifmate.model

import androidx.lifecycle.ViewModel
import com.notifmate.helper.AudioPlayState

class NotificationViewModel : ViewModel() {

    private var currentNotification: NotificationModel? = null
    private var currentAudio: AudioModel? = null
    private var currentAudioPlaybackState: AudioStateModel? = null

    fun getCurrentNotification(): NotificationModel? {
        return currentNotification
    }

    fun getCurrentAudio(): AudioModel? {
        return currentAudio
    }

    fun getCurrentAudioPlaybackState(): AudioStateModel? {
        return currentAudioPlaybackState
    }

    fun changeCurrentNotification(notification: NotificationModel ){
        currentNotification = notification
    }

    fun changeCurrentAudio(audio: AudioModel ){
        currentAudio = audio
    }

    fun changeCurrentAudioPlaybackState(playbackState: AudioStateModel ){
        currentAudioPlaybackState = playbackState
    }

    val notificationList: MutableList<NotificationModel> = mutableListOf()
    val audioList: MutableList<AudioPlayState> = mutableListOf()
    private val sentNotificationIds = mutableSetOf<Long>()

    fun addNotification(notification: NotificationModel) {
        clearLists()
        notificationList.add(notification)
        notificationList.add(notification)
        /*
        if(!sentNotificationIds.contains(notification.timestamp)) {
            notification.timestamp?.let { sentNotificationIds.add(it) }
            notificationList.add(notification)
        }
        */
    }

    fun addAudioPlayBackState(audio: AudioPlayState) {
        audioList.add(audio)
    }

    fun clearLists() {
        notificationList.clear()
        audioList.clear()
        sentNotificationIds.clear()
    }

}
