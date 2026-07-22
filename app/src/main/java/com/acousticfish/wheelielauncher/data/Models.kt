package com.acousticfish.wheelielauncher.data

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
    /**
     * Drawer swipe sensitivity multiplier.
     * 1.0 = current default commit distance; 0.25 = less sensitive; 2.0 = more sensitive.
     */
    val swipeSensitivity: Float = 1f,
    /** Show a floating clock in the top-right corner of home. */
    val showClock: Boolean = true,
    /** Show an equalizer shortcut in the top-left corner. */
    val showEqButton: Boolean = false,
    /** Flattened [ComponentName] of the EQ / audio app to launch, or null. */
    val eqAppComponent: String? = null,
    /** Show previous/next media skip buttons in the bottom corners. */
    val showSkipButtons: Boolean = false,
    /** Dark scrim over the fixed home wallpaper while the drawer is open. */
    val drawerBackgroundOpacity: Float = 0.45f,
    /** Now Playing progress / battery arc stroke width in dp. */
    val progressBarThicknessDp: Float = 4f,
    /** Show battery level arc mirrored above the album art. */
    val showBatteryBar: Boolean = true,
    /** Show scrolling artist / title around the play button. */
    val showTrackInfo: Boolean = true,
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
