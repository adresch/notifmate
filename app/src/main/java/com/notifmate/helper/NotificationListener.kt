package com.notifmate.helper

import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.notifmate.helper.CustomUtils.truncate
import java.io.ByteArrayOutputStream

class NotificationListener : NotificationListenerService() {

    private val logTag = "MYDEBUG NLS"

    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeMediaController: MediaController? = null

    private var lastPlaybackState: Int = -1

    private var currentNotifPackageName: String = ""
    private var currentNotifTitle: String = ""
    private var currentNotifText: String = ""

    companion object {
        const val ACTION_REQUEST_MEDIA_INFO = "com.notifmate.REQUEST_MEDIA_INFO"
        const val ACTION_MEDIA_UPDATED = "com.notifmate.MEDIA_UPDATED"
        const val ACTION_MEDIA_STATE_CHANGED = "com.notifmate.MEDIA_STATE_CHANGED"
        const val ACTION_NOTIFICATION_RECEIVED = "com.notifmate.NOTIFICATION_RECEIVED"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(logTag, "NotificationListener Created")
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        initMediaSessionManager(true)

        val filter = IntentFilter(ACTION_REQUEST_MEDIA_INFO)
        registerReceiver(requestMediaInfoReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun initMediaSessionManager(forceUpdate: Boolean = false) {
        val sessions = mediaSessionManager.getActiveSessions(ComponentName(this, NotificationListener::class.java))

        if (sessions.isEmpty()) {
            Log.w(logTag, "No active media sessions found")
            return
        }

        val prioritizedSession = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.first()

        if (!forceUpdate && activeMediaController?.packageName == prioritizedSession.packageName) return

        activeMediaController?.unregisterCallback(mediaCallback)
        activeMediaController = prioritizedSession
        activeMediaController?.registerCallback(mediaCallback)

        updateCurrentMediaState(prioritizedSession.metadata, prioritizedSession.playbackState)
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateCurrentMediaState(metadata, activeMediaController?.playbackState)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateCurrentMediaState(activeMediaController?.metadata, state)
        }
    }

    private fun updateCurrentMediaState(metadata: MediaMetadata?, state: PlaybackState?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
        val artBase64 = bitmapToBase64(metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART))
        val playbackState = when (state?.state) {
            PlaybackState.STATE_PLAYING -> "Playing"
            PlaybackState.STATE_PAUSED -> "Paused"
            PlaybackState.STATE_STOPPED -> "Stopped"
            else -> "Unknown"
        }

        val playbackStateCode = state?.state ?: -1

        if (title != "Unknown Title" && artist != "Unknown Artist"){
            sendMediaUpdate(title, artist, artBase64, playbackState)
        }

        if (playbackStateCode != lastPlaybackState) {
            lastPlaybackState = playbackStateCode
            sendMediaStateChanged(playbackState)
        }

        Log.d(logTag, "Media Update -> $title - $artist ($playbackState)")
    }

    private fun sendMediaUpdate(title: String, artist: String, artBase64: String, state: String) {
        val intent = Intent(ACTION_MEDIA_UPDATED).apply {
            putExtra("title", title)
            putExtra("artist", artist)
            putExtra("artBitmap", artBase64)
            putExtra("state", state)
            putExtra("source", "local")
        }
        Log.d(logTag, "SEND Media Update -> $title - $artist")
        sendBroadcast(intent)
    }

    private fun sendMediaStateChanged(state: String) {
        val intent = Intent(ACTION_MEDIA_STATE_CHANGED).apply {
            putExtra("state", state)
            putExtra("source", "local")
        }
        sendBroadcast(intent)
    }

    private val requestMediaInfoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                Log.d(logTag, "Received REQUEST_MEDIA_INFO broadcast, sending current media info.")
                activeMediaController?.let {
                    updateCurrentMediaState(it.metadata, it.playbackState)
                }
            } catch (e: Exception) {
                Log.e(logTag, "Failed to send media info update: ${e.message}")
            }
        }
    }

    fun bitmapToBase64(bitmap: Bitmap?, quality: Int = 70): String {
        if (bitmap == null) return ""
        return try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(logTag, "Bitmap encoding failed: ${e.message}")
            ""
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            if (isSystemApp(it.packageName)) {
                Log.d(logTag, "Ignoring system notification from ${it.packageName}")
                return
            }

            currentNotifPackageName = it.packageName
            val extras = it.notification.extras
            currentNotifTitle = extras.getCharSequence("android.title")?.toString() ?: ""
            currentNotifText = extras.getCharSequence("android.text")?.toString() ?: ""
            currentNotifText = currentNotifText.truncate(250)

            val packageName = it.packageName
            val intent = Intent(ACTION_NOTIFICATION_RECEIVED).apply {
                putExtra("packageName", packageName)
                putExtra("notificationTitle", currentNotifTitle)
                putExtra("notificationText", currentNotifText)
                putExtra("source", "local")
            }

            if (!getAllMusicApps(this).contains(packageName)) {
                if (currentNotifTitle.isNotBlank() && currentNotifText.isNotBlank()) {
                    sendBroadcast(intent)
                }
            } else {
                initMediaSessionManager()
            }
        }
    }

    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val isSystemFlagged = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isSystemPath = appInfo.sourceDir.startsWith("/system/")
            isSystemFlagged || isSystemPath
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private var cachedMusicApps: List<String>? = null

    fun getAllMusicApps(context: Context): List<String> {
        if (cachedMusicApps != null) return cachedMusicApps!!
        val musicApps = mutableListOf<String>()
        val pm: PackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
        }
        val apps = pm.queryIntentActivities(intent, 0)
        apps.forEach { appInfo ->
            musicApps.add(appInfo.activityInfo.packageName)
        }
        Log.i(logTag, "Music app packages: $musicApps")
        cachedMusicApps = musicApps
        return musicApps
    }

    override fun onDestroy() {
        super.onDestroy()
        activeMediaController?.unregisterCallback(mediaCallback)
        activeMediaController = null
        unregisterReceiver(requestMediaInfoReceiver)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(logTag, "NotificationListener connected")
        initMediaSessionManager(true)
    }
}
