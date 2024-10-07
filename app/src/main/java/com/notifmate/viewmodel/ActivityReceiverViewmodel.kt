package com.notifmate.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.notifmate.helper.AudioPlayState
import com.notifmate.model.AudioModel
import com.notifmate.model.AudioStateModel
import com.notifmate.model.NotificationModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ActivityReceiverViewmodel : ViewModel() {
    private val _notificationModel: MutableStateFlow<NotificationModel> =
        MutableStateFlow(NotificationModel("", "", ""))
    val notificationModel: StateFlow<NotificationModel> = _notificationModel.asStateFlow()

    val _audioModel: MutableStateFlow<AudioPlayState> = MutableStateFlow(AudioPlayState("", "", 0, ""))
    val audioModel: StateFlow<AudioPlayState> = _audioModel.asStateFlow()

    val notificationModelNew: MutableStateFlow<NotificationModel> =
        MutableStateFlow(NotificationModel("", "", ""))

    val audioModelNew: MutableStateFlow<AudioModel> =
        MutableStateFlow(AudioModel("", "", ""))

    val audioStateNew: MutableStateFlow<AudioStateModel> =
        MutableStateFlow(AudioStateModel(0))

    fun changeNoti(notification: NotificationModel) {
        if (notification.title != notificationModelNew.value.title || notification.msg != notificationModelNew.value.msg) {
            notificationModelNew.value = notification
            Log.d("MY_DEBUG", "Changed notification: ${notification.packageName}")
        } else {
            Log.d("MY_DEBUG", "Notification from banned package ignored: ${notification.packageName}")
        }
    }

    fun changeAudio(audio: AudioModel){
        if (audioModelNew.value.songName != audio.songName || audioModelNew.value.artistName != audio.artistName || audioModelNew.value.musicArtBase64 != audio.musicArtBase64){
            audioModelNew.value = audio
        }
    }

    fun changeAudioState(audioState: AudioStateModel){

        if (audioStateNew.value.playBackState != audioState.playBackState){
            audioStateNew.value = audioState
        }
    }

    fun addNotiData(notificationModel: NotificationModel) {
        _notificationModel.update {
            it.copy(
                title = notificationModel.title,
                msg = notificationModel.msg,
                packageName = notificationModel.packageName

            )
        }
    }
}