package com.chaples55.wheelielauncher.data

import android.content.ComponentName
import android.net.Uri

data class LauncherApp(
    val componentName: ComponentName,
    val label: String,
    val packageName: String = componentName.packageName,
)

data class DockItem(
    val componentName: ComponentName,
    val customLabel: String? = null,
    /** Pack drawable name, content URI, or null for default/pack-resolved icon. */
    val customIcon: String? = null,
)

data class AppCustomization(
    val customLabel: String? = null,
    val customIcon: String? = null,
)

data class LauncherSettings(
    val dockShowLabels: Boolean = false,
    val drawerShowLabels: Boolean = true,
    val dockIconSizeDp: Float = 48f,
    val drawerIconSizeDp: Float = 48f,
    val drawerColumns: Int = 4,
    /** When true, show a search field at the top of the app drawer. */
    val drawerShowSearch: Boolean = true,
    val showStatusBar: Boolean = true,
    val statusBarScrimOpacity: Float = 0.4f,
    val defaultWallpaperUri: String? = null,
    val iconPackPackage: String? = null,
    val hiddenPackages: Set<String> = emptySet(),
    val customizations: Map<String, AppCustomization> = emptyMap(),
    val dockSeeded: Boolean = false,
    val onboardingHomeDone: Boolean = false,
    val onboardingMediaDone: Boolean = false,
    /** Diameter of the center Now Playing widget in dp. */
    val nowPlayingSizeDp: Float = 120f,
    /** Ring radius as a fraction of the shorter screen half (0.45–0.92). */
    val dockRingRadiusFraction: Float = 0.78f,
    /** Swipe up on the home screen opens the app drawer (Murine-style). */
    val swipeUpToOpenDrawer: Boolean = false,
    /** Hide the dock drawer button; only applies when [swipeUpToOpenDrawer] is true. */
    val hideDrawerButton: Boolean = false,
)

data class NowPlayingState(
    val title: String? = null,
    val artist: String? = null,
    val artworkUri: Uri? = null,
    val artworkBitmapKey: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasSession: Boolean = false,
    /** Package of the app owning the active media session. */
    val sourcePackage: String? = null,
)

/** Metadata / artwork that should invalidate wallpaper + center art (not progress ticks). */
data class NowPlayingMeta(
    val title: String? = null,
    val artist: String? = null,
    val artworkUri: Uri? = null,
    val artworkBitmapKey: String? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val hasSession: Boolean = false,
    val sourcePackage: String? = null,
)

data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
)

fun NowPlayingState.toMeta() = NowPlayingMeta(
    title = title,
    artist = artist,
    artworkUri = artworkUri,
    artworkBitmapKey = artworkBitmapKey,
    isPlaying = isPlaying,
    durationMs = durationMs,
    hasSession = hasSession,
    sourcePackage = sourcePackage,
)

fun NowPlayingState.toProgress() = PlaybackProgress(
    positionMs = positionMs,
    durationMs = durationMs,
    isPlaying = isPlaying,
)

fun ComponentName.key(): String = flattenToString()
