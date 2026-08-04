package com.acousticfish.wheelielauncher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
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
import com.acousticfish.wheelielauncher.ui.LauncherRoot
import com.acousticfish.wheelielauncher.ui.LauncherViewModel
import com.acousticfish.wheelielauncher.ui.LauncherViewModelFactory
import com.acousticfish.wheelielauncher.ui.theme.WheelieTheme
import com.acousticfish.wheelielauncher.util.cancelStatusBarAutoHide
import com.acousticfish.wheelielauncher.util.hideStatusBarTransient

class HomeActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels {
        LauncherViewModelFactory((application as WheelieApp).container)
    }

    private var lastStatusBarVisible: Boolean? = null
    private var preferStatusBarVisible: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Let the system wallpaper show through when we aren't drawing a custom/media layer.
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val showStatusBar = state.settings.showStatusBar
            preferStatusBarVisible = showStatusBar
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
        if (!preferStatusBarVisible) {
            cancelStatusBarAutoHide()
            hideStatusBarTransient()
        } else {
            lastStatusBarVisible?.let { applyStatusBarVisibility(it) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !preferStatusBarVisible) {
            cancelStatusBarAutoHide()
            hideStatusBarTransient()
        }
    }

    private fun applyStatusBarVisibility(show: Boolean) {
        cancelStatusBarAutoHide()
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
