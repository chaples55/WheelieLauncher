package com.acousticfish.wheelielauncher.ui.settings

import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.acousticfish.wheelielauncher.data.LauncherApp
import com.acousticfish.wheelielauncher.data.LauncherSettings
import com.acousticfish.wheelielauncher.data.SettingsRepository
import com.acousticfish.wheelielauncher.data.WallpaperStore
import com.acousticfish.wheelielauncher.icons.IconPackInfo
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    iconPacks: List<IconPackInfo>,
    apps: List<LauncherApp>,
    onUpdate: (suspend (SettingsRepository) -> Unit) -> Unit,
    onBack: () -> Unit,
    onManageHidden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val host = remember(onUpdate) {
        SettingsPanelView.Host { block -> onUpdate(block) }
    }
    var panelRef by remember { mutableStateOf<SettingsPanelView?>(null) }

    BackHandler {
        if (panelRef?.handleBack() != true) onBack()
    }

    /** Drop any launcher-only override so the live system wallpaper shows through. */
    fun useSystemWallpaper() {
        onUpdate { repo ->
            WallpaperStore.clear(context)
            repo.setWallpaperUri(null)
        }
    }

    val setWallpaperLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // System picker frames + commits the wallpaper. We cannot reliably read it back
        // (Android blocks WallpaperManager for 3P apps), so we show it through the window.
        useSystemWallpaper()
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
        scope.launch {
            // Prefer crop+set so framing matches the system wallpaper we show through the window.
            val cropIntent = runCatching {
                WallpaperManager.getInstance(context).getCropAndSetWallpaperIntent(uri)
            }.getOrNull()
            if (cropIntent != null) {
                try {
                    setWallpaperLauncher.launch(cropIntent)
                    return@launch
                } catch (_: Exception) {
                }
            }
            // No cropper available — do not store an app-only copy; keep using system wallpaper.
            useSystemWallpaper()
        }
    }

    fun pickWallpaper() {
        val setIntent = Intent(Intent.ACTION_SET_WALLPAPER)
        if (setIntent.resolveActivity(context.packageManager) != null) {
            try {
                setWallpaperLauncher.launch(setIntent)
                return
            } catch (_: Exception) {
            }
        }
        documentPicker.launch(arrayOf("image/*"))
    }

    AndroidView(
        factory = { ctx ->
            SettingsPanelView(ctx).also { panel ->
                panelRef = panel
                panel.bindCallbacks(
                    host = host,
                    onBack = onBack,
                    onManageHidden = onManageHidden,
                    onPickWallpaper = { pickWallpaper() },
                )
                panel.bind(settings, iconPacks, apps)
            }
        },
        update = { panel ->
            panelRef = panel
            panel.bindCallbacks(
                host = host,
                onBack = onBack,
                onManageHidden = onManageHidden,
                onPickWallpaper = { pickWallpaper() },
            )
            panel.bind(settings, iconPacks, apps)
        },
        modifier = modifier.fillMaxSize(),
    )
}
