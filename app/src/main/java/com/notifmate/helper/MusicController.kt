package com.notifmate.helper

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.util.Base64
import android.util.Log
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.io.ByteArrayInputStream
import java.io.Serializable
import java.util.Timer
import java.util.TimerTask

data class AudioPlayState(
    val title: String = "",
    val artist: String = "",
    val playbackState: Int,
    val musicArtBase64: String = "",
) : Serializable

class MusicController(private val context: Context) {
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaController: MediaController? = null
    private var callback: MediaControllerCallback? = null
    private val activeSessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener {
            mediaSessionManager?.let { it1 -> registerActiveMediaControllerCallback(it1) }
        }

    private val mediaControllerCallback: MediaController.Callback =
        object : MediaController.Callback() {
            @SuppressLint("SwitchIntDef")
            override fun onPlaybackStateChanged(playbackState: PlaybackState?) {
                Log.e("MY_DEBUG", "LOCAL_MUSIC_PLAYBACK_STATE_CHANGE")
                Log.w("MY_DEBUG", "Playback state changed: ${playbackState?.state}")
                playbackStateFlow.value = playbackState

            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                metadata?.let { metadata ->
                    try {
                        val songName = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        val artBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        Log.e("MY_DEBUG", "LOCAL_MUSIC_ONMETADATA_CHANGE")

                        songNameFlow.value = songName
                        artistNameFlow.value = artist
                        musicArtFlow.value = artBitmap
                    } catch (e: RuntimeException) {
                        Log.e("TAG", "An error occurred reading the media metadata: $e")
                    }
                }
            }
        }

    private val songNameFlow = MutableStateFlow<String?>(null)
    private val artistNameFlow = MutableStateFlow<String?>(null)
    private val musicArtFlow = MutableStateFlow<Bitmap?>(null)
    private val playbackStateFlow = MutableStateFlow<PlaybackState?>(null)


    private fun getActiveMediaController(mediaSessionManager: MediaSessionManager): MediaController? {
        return try {
            val mediaControllers = mediaSessionManager.getActiveSessions(
                ComponentName(
                    context,
                    NotificationListener::class.java
                )
            )
            mediaControllers.firstOrNull()
        } catch (e: SecurityException) {
            null
        }
    }


    private fun registerActiveMediaControllerCallback(mediaSessionManager: MediaSessionManager) {
        getActiveMediaController(mediaSessionManager)?.let { mediaController ->
            registerCallback(mediaController)

            mediaController.metadata?.let { metadata ->
                mediaControllerCallback.onMetadataChanged(metadata)
            }
        }
    }

    private fun registerCallback(mediaController: MediaController) {
        unregisterCallback(this.mediaController)

        Log.i("TAG", "Registering callback for ${mediaController.packageName}")

        this.mediaController = mediaController
        mediaControllerCallback.let { mediaControllerCallback ->
            mediaController.registerCallback(mediaControllerCallback)
        }
    }

    private fun unregisterCallback(mediaController: MediaController?) {
        Log.i("TAG", "Unregistering callback for ${mediaController?.packageName}")
        mediaControllerCallback.let { mediaControllerCallback ->
            mediaController?.unregisterCallback(mediaControllerCallback)
        }
    }

    fun combineLiveData(): Flow<CombinedData> = combine(
        songNameFlow,
        artistNameFlow,
        playbackStateFlow,
        musicArtFlow
    ) { songName, artistName, playbackState, musicArt ->
        CombinedData(songName, artistName, playbackState, musicArt)
    }

    data class CombinedData(
        val songName: String?,
        val artistName: String?,
        val playbackState: PlaybackState?,
        val musicArt: Bitmap?
    )


    companion object {
        val musicDetailLiveData = MutableLiveData<AudioPlayState>()
    }

    fun initializeMediaSessionManager() {
        try {
            mediaSessionManager =
                context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val mediaSessions = mediaSessionManager?.getActiveSessions(
                ComponentName(
                    context,
                    NotificationListener::class.java
                )
            )

            registerActiveMediaControllerCallback(mediaSessionManager!!)

            addSessionStateChangeListener()

        } catch (e: Exception) {
            Log.e("initializeMediaSessionManager", "$e")
        }

    }


    private fun addSessionStateChangeListener() {
        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                activeSessionsChangedListener,
                ComponentName(context, NotificationListener::class.java)
            )
            Log.i("TAG", "Successfully added session change listener")
        } catch (e: SecurityException) {
            Log.e("TAG", "Failed to add session change listener :$e")
        }
    }

    fun play() {
        mediaController?.transportControls?.play()
    }

    fun pause() {
        mediaController?.transportControls?.pause()
    }

    fun next() {
        mediaController?.transportControls?.skipToNext()
    }

    fun previous() {
        mediaController?.transportControls?.skipToPrevious()
    }


    fun setCallback(callback: MediaControllerCallback) {
        this.callback = callback
    }


    fun removeCallback() {
        this.callback = null
    }


    interface MediaControllerCallback {
        fun onSongChanged(title: String?, artist: String?)
        fun onPlaybackStateChanged(state: Int)
    }

    private inner class MediaControllerCallback11 : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            // Handle metadata changes here
            val songName = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            Log.w("onMetadataChanged", "Song name changed: $songName")
            Log.w("onMetadataChanged", "Artist changed: $artist")

            songNameFlow.value = songName
            artistNameFlow.value = artist

        }

        @SuppressLint("SwitchIntDef")
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            Log.d("onPlaybackStateChanged", "Playback state changed: ${state?.state}")
            playbackStateFlow.value = state
            // Handle playback state changes here
            when (state?.state) {
                PlaybackState.STATE_PLAYING -> {
                    // Media playback has resumed
                    Log.d("onPlaybackStateChanged", "Media playback resumed")
                    // You may want to retrieve metadata again here if needed
                }

                PlaybackState.STATE_PAUSED -> {
                    // Media playback has been paused
                    Log.d("onPlaybackStateChanged", "Media playback paused")
                    // You may want to clear metadata or update UI accordingly
                }

                PlaybackState.STATE_NONE -> {
                    Log.d("onPlaybackStateChanged", "Media playback paused")
                    // You may want to clear metadata or update UI accordingly
                }

                PlaybackState.STATE_STOPPED -> {
                    Log.e("onPlaybackStateChanged", "Media playback stopped")

                }
            }
        }

        override fun onSessionEvent(event: String, extras: Bundle?) {
            super.onSessionEvent(event, extras)
            Log.e("onSessionEvent", "event: $event")
        }
    }

}




