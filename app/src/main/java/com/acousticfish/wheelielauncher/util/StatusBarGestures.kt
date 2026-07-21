package com.acousticfish.wheelielauncher.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.AlarmClock
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.acousticfish.wheelielauncher.R

private const val STATUS_BAR_AUTO_HIDE_MS = 2_800L

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

fun Activity.hideStatusBarTransient() {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.statusBars())
}

/**
 * Temporarily reveal the status bar (immersive / hidden-bar mode).
 * Re-hides automatically after a short delay so peeks don't stick.
 */
fun Activity.peekStatusBar() {
    val decor = window.decorView
    val controller = WindowInsetsControllerCompat(window, decor)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.show(WindowInsetsCompat.Type.statusBars())

    val existing = decor.getTag(R.id.tag_status_bar_hide) as? Runnable
    if (existing != null) decor.removeCallbacks(existing)
    val hide = Runnable {
        hideStatusBarTransient()
        decor.setTag(R.id.tag_status_bar_hide, null)
    }
    decor.setTag(R.id.tag_status_bar_hide, hide)
    decor.postDelayed(hide, STATUS_BAR_AUTO_HIDE_MS)
}

fun Activity.cancelStatusBarAutoHide() {
    val decor = window.decorView
    val existing = decor.getTag(R.id.tag_status_bar_hide) as? Runnable ?: return
    decor.removeCallbacks(existing)
    decor.setTag(R.id.tag_status_bar_hide, null)
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
        activity?.cancelStatusBarAutoHide()
        expandNotificationsPanel(context)
    }
}

/** Opens the system Clock / Alarms app. */
fun openClockApp(context: Context) {
    val attempts = listOf(
        Intent(AlarmClock.ACTION_SHOW_ALARMS),
        Intent(AlarmClock.ACTION_SHOW_TIMERS),
    )
    for (intent in attempts) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return
        }
    }
    val packages = listOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.samsung.android.app.clockpackage",
        "com.oneplus.deskclock",
    )
    for (pkg in packages) {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return
    }
}
