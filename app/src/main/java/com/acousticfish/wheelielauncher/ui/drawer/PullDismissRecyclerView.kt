package com.acousticfish.wheelielauncher.ui.drawer

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * RecyclerView with optional pull-to-dismiss when already scrolled to the top.
 *
 * Pull is intentionally hard to trigger (large slop) so normal scrolling and
 * long-press menus are not cancelled by touch jitter.
 */
class PullDismissRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : RecyclerView(context, attrs) {
    var pullOffsetPx: Float = 0f
        private set
    var onPullChanged: ((Float) -> Unit)? = null
    var onPullEnd: ((Float, Float) -> Unit)? = null

    private var trackingPull = false
    private var lastRawY = 0f
    private var downRawX = 0f
    private var downRawY = 0f
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    /** Require a clear downward drag before stealing the gesture from scroll / long-press. */
    private val pullSlop = touchSlop * 2f

    init {
        isNestedScrollingEnabled = false
    }

    fun resetPull() {
        pullOffsetPx = 0f
        trackingPull = false
        recycleVelocityTracker()
    }

    private fun ensureVelocityTracker() {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun finishPull(e: MotionEvent) {
        velocityTracker?.computeCurrentVelocity(1000)
        val vyPxPerMs = (velocityTracker?.yVelocity ?: 0f) / 1000f
        onPullEnd?.invoke(pullOffsetPx, vyPxPerMs)
        trackingPull = false
        pullOffsetPx = 0f
        lastRawY = e.rawY
        recycleVelocityTracker()
        parent?.requestDisallowInterceptTouchEvent(false)
        // Do not forward UP/CANCEL to RecyclerView — it never owned this gesture.
        stopNestedScroll()
        stopScroll()
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = e.rawX
                downRawY = e.rawY
                lastRawY = e.rawY
                trackingPull = false
                ensureVelocityTracker()
                velocityTracker?.clear()
                velocityTracker?.addMovement(e)
                super.onInterceptTouchEvent(e)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                ensureVelocityTracker()
                velocityTracker?.addMovement(e)
                if (trackingPull) return true
                val totalDy = e.rawY - downRawY
                val totalDx = e.rawX - downRawX
                if (!canScrollVertically(-1) &&
                    totalDy > touchSlop &&
                    totalDy > abs(totalDx)
                ) {
                    trackingPull = true
                    lastRawY = e.rawY
                    pullOffsetPx = totalDy
                    onPullChanged?.invoke(pullOffsetPx)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (trackingPull) {
                    finishPull(e)
                    return true
                }
                recycleVelocityTracker()
            }
        }
        return super.onInterceptTouchEvent(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        ensureVelocityTracker()
        velocityTracker?.addMovement(e)
        if (trackingPull) {
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - lastRawY
                    lastRawY = e.rawY
                    pullOffsetPx = (pullOffsetPx + dy).coerceAtLeast(0f)
                    onPullChanged?.invoke(pullOffsetPx)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    finishPull(e)
                    return true
                }
            }
            return true
        }
        return super.onTouchEvent(e)
    }
}
