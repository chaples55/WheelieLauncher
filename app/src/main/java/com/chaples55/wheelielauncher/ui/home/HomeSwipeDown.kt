package com.chaples55.wheelielauncher.ui.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Home vertical gestures (single detector, Main pass — sits under the app drawer layer):
 * - Swipe down while drawer closed → notifications / status-bar peek
 * - Swipe up (optional) → open app drawer with finger-tracked progress
 * - Drag while drawer partially open → continue drawer progress
 */
fun Modifier.homeVerticalGestures(
    enabled: Boolean,
    swipeUpToOpenDrawer: Boolean,
    isDrawerClosed: () -> Boolean,
    onNotificationSwipeDown: () -> Unit,
    onDrawerDragStart: () -> Unit,
    onDrawerDrag: (PointerInputChange, Float) -> Unit,
    onDrawerDragEnd: () -> Unit,
    onDrawerDragCancel: () -> Unit,
): Modifier = composed {
    val notif = rememberUpdatedState(onNotificationSwipeDown)
    val dragStart = rememberUpdatedState(onDrawerDragStart)
    val drag = rememberUpdatedState(onDrawerDrag)
    val dragEnd = rememberUpdatedState(onDrawerDragEnd)
    val dragCancel = rememberUpdatedState(onDrawerDragCancel)
    val closed = rememberUpdatedState(isDrawerClosed)

    if (!enabled) return@composed this

    pointerInput(swipeUpToOpenDrawer) {
        var mode = HomeGestureMode.Undecided
        detectVerticalDragGestures(
            onDragStart = {
                mode = HomeGestureMode.Undecided
            },
            onVerticalDrag = { change, dragAmount ->
                // dragAmount > 0 → finger moved down
                when (mode) {
                    HomeGestureMode.Undecided -> {
                        when {
                            dragAmount > 0f && closed.value() -> {
                                mode = HomeGestureMode.Notifications
                                change.consume()
                                notif.value()
                            }
                            swipeUpToOpenDrawer && (dragAmount < 0f || !closed.value()) -> {
                                mode = HomeGestureMode.Drawer
                                change.consume()
                                dragStart.value()
                                drag.value(change, dragAmount)
                            }
                            else -> Unit
                        }
                    }
                    HomeGestureMode.Notifications -> change.consume()
                    HomeGestureMode.Drawer -> {
                        change.consume()
                        drag.value(change, dragAmount)
                    }
                }
            },
            onDragEnd = {
                if (mode == HomeGestureMode.Drawer) dragEnd.value()
                mode = HomeGestureMode.Undecided
            },
            onDragCancel = {
                if (mode == HomeGestureMode.Drawer) dragCancel.value()
                mode = HomeGestureMode.Undecided
            },
        )
    }
}

/** Long-press empty home background for the launcher context menu. */
fun Modifier.homeBackgroundLongPress(
    enabled: Boolean,
    onLongPress: (Offset) -> Unit,
): Modifier = composed {
    val latest = rememberUpdatedState(onLongPress)
    if (!enabled) return@composed this
    pointerInput(Unit) {
        detectTapGestures(
            onLongPress = { latest.value(it) },
        )
    }
}

private enum class HomeGestureMode { Undecided, Notifications, Drawer }
