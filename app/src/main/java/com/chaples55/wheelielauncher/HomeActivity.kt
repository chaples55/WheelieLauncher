package com.chaples55.wheelielauncher

import android.content.Intent
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
import com.chaples55.wheelielauncher.ui.LauncherRoot
import com.chaples55.wheelielauncher.ui.LauncherViewModel
import com.chaples55.wheelielauncher.ui.LauncherViewModelFactory
import com.chaples55.wheelielauncher.ui.theme.WheelieTheme

class HomeActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels {
        LauncherViewModelFactory((application as WheelieApp).container)
    }

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
        // Re-apply so a temporary peek does not stick after leaving the shade / app.
        lastStatusBarVisible?.let { applyStatusBarVisibility(it) }
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
