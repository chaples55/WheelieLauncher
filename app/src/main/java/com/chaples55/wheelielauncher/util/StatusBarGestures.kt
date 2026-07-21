package com.chaples55.wheelielauncher.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Opens the system notification shade (Murine/Launcher3 approach via hidden API).
 * Requires [android.Manifest.permission.EXPAND_STATUS_BAR].
 */
fun expandNotificationsPanel(context: Context) {
    try {
        val service = context.getSystemService("statusbar") ?: return
        service.javaClass.getMethod("expandNotificationsPanel").invoke(service)
    } catch (_: Exception) {
        // OEM / API restrictions — fail silently like Murine.
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Activity.isStatusBarVisible(): Boolean {
    val insets = ViewCompat.getRootWindowInsets(window.decorView) ?: return false
    return insets.isVisible(WindowInsetsCompat.Type.statusBars())
}

/** Temporarily reveal the status bar (immersive / hidden-bar mode). */
fun Activity.peekStatusBar() {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.show(WindowInsetsCompat.Type.statusBars())
}

/**
 * Swipe-down on home:
 * - Status bar preferred visible → expand shade
 * - Status bar hidden and currently not visible → peek bar
 * - Status bar hidden but already visible (after peek) → expand shade
 */
fun handleHomeSwipeDown(context: Context, statusBarPreferredVisible: Boolean) {
    val activity = context.findActivity()
    if (!statusBarPreferredVisible && activity != null && !activity.isStatusBarVisible()) {
        activity.peekStatusBar()
    } else {
        expandNotificationsPanel(context)
    }
}
