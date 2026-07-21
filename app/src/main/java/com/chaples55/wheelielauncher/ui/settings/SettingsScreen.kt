package com.chaples55.wheelielauncher.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.chaples55.wheelielauncher.data.LauncherSettings
import com.chaples55.wheelielauncher.data.SettingsRepository
import com.chaples55.wheelielauncher.icons.IconPackInfo

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    iconPacks: List<IconPackInfo>,
    onUpdate: (suspend (SettingsRepository) -> Unit) -> Unit,
    onBack: () -> Unit,
    onManageHidden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val host = remember(onUpdate) {
        SettingsPanelView.Host { block -> onUpdate(block) }
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
                panel.bindCallbacks(
                    host = host,
                    onBack = onBack,
                    onManageHidden = onManageHidden,
                    onPickWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                )
                panel.bind(settings, iconPacks)
            }
        },
        update = { panel ->
            panel.bindCallbacks(
                host = host,
                onBack = onBack,
                onManageHidden = onManageHidden,
                onPickWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
            )
            panel.bind(settings, iconPacks)
        },
        modifier = modifier.fillMaxSize(),
    )
}
