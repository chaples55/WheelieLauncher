package com.acousticfish.wheelielauncher.ui.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Drawer sheet progress:
 * - 0 = closed (home)
 * - 1 = open (drawer fully up)
 * Finger tracks progress directly; settle uses a short symmetric distance threshold.
 */
@Stable
class DrawerProgressController(
    private val scope: CoroutineScope,
) {
    val progress = Animatable(0f)
    var panelHeightPx by mutableFloatStateOf(0f)
        private set
    /** Fraction of panel height required to commit; derived from swipe sensitivity. */
    var commitDistance by mutableFloatStateOf(BASE_COMMIT_DISTANCE)
        private set

    private var settleJob: Job? = null

    fun updatePanelHeight(height: Float) {
        if (height <= 0f) return
        panelHeightPx = height
    }

    fun updateSwipeSensitivity(sensitivity: Float) {
        val s = sensitivity.coerceIn(0.25f, 2f)
        // Higher sensitivity → shorter swipe to commit.
        commitDistance = (BASE_COMMIT_DISTANCE / s).coerceIn(0.05f, 0.8f)
    }

    fun snapProgress(value: Float) {
        settleJob?.cancel()
        settleJob = scope.launch { progress.snapTo(value.coerceIn(0f, 1f)) }
    }

    fun animateOpen() {
        settleJob?.cancel()
        settleJob = scope.launch {
            progress.animateTo(1f, tween(OPEN_DURATION_MS, easing = ATOMIC_EASING))
        }
    }

    fun animateClose() {
        settleJob?.cancel()
        settleJob = scope.launch {
            progress.animateTo(0f, tween(CLOSE_DURATION_MS, easing = ATOMIC_EASING))
        }
    }

    /** While the user is dragging, set progress immediately (no tween). */
    fun dragTo(value: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            progress.snapTo(value.coerceIn(0f, 1f))
        }
    }

    /**
     * Settle after a gesture.
     * @param atProgress finger position to settle from (avoids racing async [dragTo])
     * @param velocityYpxPerMs positive = finger moving down
     * @param wasOpening true if the gesture was toward open
     */
    fun settleFromGesture(
        atProgress: Float,
        velocityYpxPerMs: Float,
        wasOpening: Boolean,
    ) {
        settleJob?.cancel()
        val p = atProgress.coerceIn(0f, 1f)
        val commit = commitDistance
        val fling = abs(velocityYpxPerMs) > FLING_VELOCITY_PX_MS
        val target = when {
            fling && velocityYpxPerMs < 0f -> 1f
            fling && velocityYpxPerMs > 0f -> 0f
            wasOpening -> if (p >= commit) 1f else 0f
            else -> if ((1f - p) >= commit) 0f else 1f
        }
        val distance = abs(target - p)
        val duration = settleDurationMs(velocityYpxPerMs, distance)
        settleJob = scope.launch {
            progress.snapTo(p)
            if (distance < 0.001f) return@launch
            progress.animateTo(
                target,
                tween(
                    durationMillis = duration,
                    easing = if (fling) LinearEasing else ATOMIC_EASING,
                ),
            )
        }
    }

    companion object {
        /** Midpoint commit distance at sensitivity 1.0 (fraction of panel height). */
        const val BASE_COMMIT_DISTANCE = 0.2f
        const val OPEN_DURATION_MS = 560
        const val CLOSE_DURATION_MS = 300
        const val FLING_VELOCITY_PX_MS = 0.8f
        const val WORKSPACE_SCALE_OPEN = 0.97f
        val ATOMIC_EASING = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

        private fun settleDurationMs(velocityYpxPerMs: Float, progressNeeded: Float): Int {
            val v = max(2f, abs(velocityYpxPerMs) * 0.5f)
            val needed = max(0.15f, progressNeeded)
            return max(100, (1200f / v * needed).toInt()).coerceAtMost(900)
        }

        /** Visual early-effect uses the base midpoint so home fade stays consistent. */
        fun earlyProgress(p: Float): Float = (p / BASE_COMMIT_DISTANCE).coerceIn(0f, 1f)

        fun homeScale(p: Float): Float {
            val t = earlyProgress(p)
            return 1f + (WORKSPACE_SCALE_OPEN - 1f) * t
        }

        fun homeAlpha(p: Float): Float = 1f - earlyProgress(p)

        /** Dim over the fixed home wallpaper while the drawer opens. */
        fun scrimAlpha(p: Float, maxAlpha: Float = 0.55f): Float {
            val max = maxAlpha.coerceIn(0f, 1f)
            val start = 0.06f
            val end = BASE_COMMIT_DISTANCE
            return when {
                p <= start || max <= 0f -> 0f
                p >= end -> max
                else -> ((p - start) / (end - start)) * max
            }
        }

        fun drawerContentAlpha(p: Float): Float = when {
            p <= 0.02f -> 0f
            p >= BASE_COMMIT_DISTANCE -> 1f
            else -> (p / BASE_COMMIT_DISTANCE).coerceIn(0f, 1f)
        }
    }
}

fun drawerTranslationY(progress: Float, heightPx: Float): Float =
    (1f - progress.coerceIn(0f, 1f)) * heightPx
