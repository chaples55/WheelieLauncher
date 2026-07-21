package com.chaples55.wheelielauncher

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaples55.wheelielauncher.ui.LauncherRoot
import com.chaples55.wheelielauncher.ui.LauncherViewModel
import com.chaples55.wheelielauncher.ui.LauncherViewModelFactory
import com.chaples55.wheelielauncher.ui.theme.WheelieTheme

class HomeActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels {
        LauncherViewModelFactory((application as WheelieApp).container)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var playPauseDownAt = 0L
    private var playPauseLongFired = false
    private var playPauseLongRunnable: Runnable? = null
    private var lastStatusBarVisible: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val showStatusBar = state.settings.showStatusBar
            SideEffect {
                if (lastStatusBarVisible != showStatusBar) {
                    lastStatusBarVisible = showStatusBar
                    applyStatusBarVisibility(showStatusBar)
                }
            }
            WheelieTheme {
                LauncherRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_HOME)
        ) {
            viewModel.onHomePressed()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshMedia()
        viewModel.refreshApps()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val state = viewModel.uiState.value
        if (state.drawerOpen || state.settingsOpen) {
            return super.dispatchKeyEvent(event)
        }

        if (state.settings.interceptVolumeAsWheel) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        val slotCount = LauncherViewModel.slotCountFor(state.dockItems.size)
                        val delta = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1
                        viewModel.stepSelection(delta, slotCount)
                    }
                    return true
                }
            }
        }

        if (state.settings.interceptPlayPause) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                -> {
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> {
                            if (event.repeatCount == 0) {
                                playPauseDownAt = System.currentTimeMillis()
                                playPauseLongFired = false
                                playPauseLongRunnable?.let { handler.removeCallbacks(it) }
                                val longPress = Runnable {
                                    if (!playPauseLongFired && playPauseDownAt > 0) {
                                        playPauseLongFired = true
                                        viewModel.togglePlayPause()
                                    }
                                }
                                playPauseLongRunnable = longPress
                                handler.postDelayed(longPress, 450L)
                            }
                            return true
                        }
                        KeyEvent.ACTION_UP -> {
                            playPauseLongRunnable?.let { handler.removeCallbacks(it) }
                            playPauseLongRunnable = null
                            if (!playPauseLongFired) {
                                val slotCount = LauncherViewModel.slotCountFor(state.dockItems.size)
                                val selected = state.selectedDockIndex.coerceIn(0, (slotCount - 1).coerceAtLeast(0))
                                viewModel.activateSelected(state.dockItems, selected)
                            }
                            playPauseDownAt = 0L
                            playPauseLongFired = false
                            return true
                        }
                    }
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun applyStatusBarVisibility(show: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (show) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
