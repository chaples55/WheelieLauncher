package com.acousticfish.wheelielauncher.ui.settings

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.acousticfish.wheelielauncher.data.LauncherApp
import com.acousticfish.wheelielauncher.data.LauncherSettings
import com.acousticfish.wheelielauncher.data.SettingsRepository
import com.acousticfish.wheelielauncher.icons.IconPackInfo

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
    val host = remember(onUpdate) {
        SettingsPanelView.Host { block -> onUpdate(block) }
    }
    var panelRef by remember { mutableStateOf<SettingsPanelView?>(null) }

    BackHandler {
        if (panelRef?.handleBack() != true) onBack()
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            onUpdate { it.setWallpaperUri(uri.toString()) }
        }
    }

    AndroidView(
        factory = { ctx ->
            SettingsPanelView(ctx).also { panel ->
                panelRef = panel
                panel.bindCallbacks(
                    host = host,
                    onBack = onBack,
                    onManageHidden = onManageHidden,
                    onPickWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
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
                onPickWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
            )
            panel.bind(settings, iconPacks, apps)
        },
        modifier = modifier.fillMaxSize(),
    )
}
