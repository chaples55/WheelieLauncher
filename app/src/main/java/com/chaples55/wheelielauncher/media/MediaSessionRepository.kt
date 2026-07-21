package com.chaples55.wheelielauncher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.chaples55.wheelielauncher.data.NowPlayingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MediaSessionRepository(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    @Volatile
    private var artworkBitmap: Bitmap? = null

    private var controller: MediaController? = null
    private var listening = false

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        bindBestSession(sessions)
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controller?.unregisterCallback(this)
            controller = null
            publish()
            refreshSessions()
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            val c = controller
            if (c != null && c.playbackState?.state == PlaybackState.STATE_PLAYING) {
                publish(updatePositionOnly = true)
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    fun start() {
        if (listening) return
        listening = true
        val manager = mediaSessionManager() ?: return
        val listenerComponent = ComponentName(context, MediaNotificationListener::class.java)
        try {
            manager.addOnActiveSessionsChangedListener(sessionListener, listenerComponent, mainHandler)
            bindBestSession(manager.getActiveSessions(listenerComponent))
        } catch (_: SecurityException) {
            _state.value = NowPlayingState()
        }
    }

    fun stop() {
        listening = false
        mainHandler.removeCallbacks(progressTicker)
        try {
            mediaSessionManager()?.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (_: Exception) {
        }
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    fun refreshSessions() {
        val manager = mediaSessionManager() ?: return
        val listenerComponent = ComponentName(context, MediaNotificationListener::class.java)
        try {
            bindBestSession(manager.getActiveSessions(listenerComponent))
        } catch (_: SecurityException) {
            _state.value = NowPlayingState()
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun currentArtworkBitmap(): Bitmap? = artworkBitmap

    private fun bindBestSession(sessions: List<MediaController>?) {
        val best = sessions
            ?.sortedByDescending { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?.firstOrNull()
        if (best?.sessionToken == controller?.sessionToken) {
            publish()
            return
        }
        controller?.unregisterCallback(controllerCallback)
        controller = best
        best?.registerCallback(controllerCallback, mainHandler)
        publish()
    }

    private fun publish(updatePositionOnly: Boolean = false) {
        val c = controller
        if (c == null) {
            artworkBitmap = null
            _state.value = NowPlayingState()
            mainHandler.removeCallbacks(progressTicker)
            return
        }
        val metadata = c.metadata
        val playback = c.playbackState
        val isPlaying = playback?.state == PlaybackState.STATE_PLAYING
        val position = playback?.position ?: 0L

        if (updatePositionOnly) {
            val current = _state.value
            if (current.hasSession && current.positionMs != position) {
                _state.value = current.copy(positionMs = position, isPlaying = isPlaying)
            }
            mainHandler.removeCallbacks(progressTicker)
            if (isPlaying) {
                mainHandler.postDelayed(progressTicker, 1000L)
            }
            return
        }

        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val artUri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
        artworkBitmap = bitmap
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        // Stable key: avoid bitmap.generationId which can change and flicker wallpaper.
        val artKey = listOfNotNull(title, artUri, bitmap?.byteCount?.toString())
            .joinToString("|")
            .ifBlank { null }
        _state.value = NowPlayingState(
            title = title,
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            artworkUri = artUri?.let { Uri.parse(it) },
            artworkBitmapKey = artKey,
            isPlaying = isPlaying,
            positionMs = position,
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            hasSession = true,
        )
        mainHandler.removeCallbacks(progressTicker)
        if (isPlaying) {
            mainHandler.post(progressTicker)
        }
    }

    private fun mediaSessionManager(): MediaSessionManager? =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
}
