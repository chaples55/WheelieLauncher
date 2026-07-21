package com.chaples55.wheelielauncher.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.chaples55.wheelielauncher.data.ArtworkCache
import com.chaples55.wheelielauncher.data.NowPlayingMeta
import com.chaples55.wheelielauncher.data.NowPlayingState
import com.chaples55.wheelielauncher.data.PlaybackProgress
import com.chaples55.wheelielauncher.data.toMeta
import com.chaples55.wheelielauncher.data.toProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MediaSessionRepository(
    private val context: Context,
    private val artworkCache: ArtworkCache,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(NowPlayingState())
    /** Full snapshot; prefer [nowPlayingMeta] / [progress] in UI to avoid progress-driven recomposition. */
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private val _meta = MutableStateFlow(NowPlayingMeta())
    /** Art / title / session — does not emit on progress-only ticks. */
    val nowPlayingMeta: StateFlow<NowPlayingMeta> = _meta.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private var controller: MediaController? = null
    private var listening = false
    private var lastArtKey: String? = null

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
                mainHandler.postDelayed(this, 2000L)
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
            clearAll()
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
            clearAll()
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun openSessionApp() {
        val pkg = controller?.packageName ?: _meta.value.sourcePackage ?: return
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }

    fun currentArtworkBitmap(): Bitmap? {
        val key = _meta.value.artworkBitmapKey ?: return null
        return artworkCache.getArt(key)
    }

    private fun clearAll() {
        lastArtKey = null
        _state.value = NowPlayingState()
        _meta.value = NowPlayingMeta()
        _progress.value = PlaybackProgress()
        mainHandler.removeCallbacks(progressTicker)
    }

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
            clearAll()
            return
        }
        val metadata = c.metadata
        val playback = c.playbackState
        val isPlaying = playback?.state == PlaybackState.STATE_PLAYING
        val position = playback?.position ?: 0L
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        if (updatePositionOnly) {
            // Progress-only: do not touch meta / full state used by wallpaper + dock.
            val next = PlaybackProgress(positionMs = position, durationMs = duration, isPlaying = isPlaying)
            if (_progress.value != next) {
                _progress.value = next
            }
            // Keep legacy state in sync for any leftover readers, but home UI should use meta/progress.
            val current = _state.value
            if (current.hasSession) {
                _state.value = current.copy(positionMs = position, isPlaying = isPlaying, durationMs = duration)
            }
            mainHandler.removeCallbacks(progressTicker)
            if (isPlaying) {
                mainHandler.postDelayed(progressTicker, 2000L)
            }
            return
        }

        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val artUri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artKey = listOfNotNull(c.packageName, title, artUri, bitmap?.byteCount?.toString())
            .joinToString("|")
            .ifBlank { null }

        if (artKey != null && bitmap != null && artKey != lastArtKey) {
            artworkCache.putArt(artKey, bitmap)
            lastArtKey = artKey
            scope.launch(Dispatchers.Default) {
                artworkCache.getOrCreateBlurred(artKey, bitmap)
            }
        } else if (artKey != null) {
            lastArtKey = artKey
        }

        val full = NowPlayingState(
            title = title,
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            artworkUri = artUri?.let { Uri.parse(it) },
            artworkBitmapKey = artKey,
            isPlaying = isPlaying,
            positionMs = position,
            durationMs = duration,
            hasSession = true,
            sourcePackage = c.packageName,
        )
        _state.value = full
        val nextMeta = full.toMeta()
        if (_meta.value != nextMeta) {
            _meta.value = nextMeta
        }
        _progress.value = full.toProgress()

        mainHandler.removeCallbacks(progressTicker)
        if (isPlaying) {
            mainHandler.post(progressTicker)
        }
    }

    private fun mediaSessionManager(): MediaSessionManager? =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
}
