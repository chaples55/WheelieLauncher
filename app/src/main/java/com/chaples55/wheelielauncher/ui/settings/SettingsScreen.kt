package com.chaples55.wheelielauncher.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaples55.wheelielauncher.R
import com.chaples55.wheelielauncher.data.LauncherSettings
import com.chaples55.wheelielauncher.data.SettingsRepository
import com.chaples55.wheelielauncher.icons.IconPackInfo

@OptIn(ExperimentalMaterial3Api::class)
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
    var pickIconPack by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Dock")
            SwitchRow("Show dock labels", settings.dockShowLabels) {
                onUpdate { repo -> repo.setDockShowLabels(it) }
            }
            SliderRow(
                label = "Dock icon size",
                value = settings.dockIconSizeDp,
                range = 36f..72f,
            ) { onUpdate { repo -> repo.setDockIconSize(it) } }
            SliderRow(
                label = "Icon ring size (${(settings.dockRingRadiusFraction * 100).toInt()}%)",
                value = settings.dockRingRadiusFraction,
                range = 0.45f..0.92f,
            ) { onUpdate { repo -> repo.setDockRingRadius(it) } }
            SliderRow(
                label = "Now Playing size",
                value = settings.nowPlayingSizeDp,
                range = 72f..220f,
            ) { onUpdate { repo -> repo.setNowPlayingSize(it) } }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("Drawer")
            SwitchRow("Show drawer labels", settings.drawerShowLabels) {
                onUpdate { repo -> repo.setDrawerShowLabels(it) }
            }
            SliderRow(
                label = "Drawer columns (${settings.drawerColumns})",
                value = settings.drawerColumns.toFloat(),
                range = 2f..6f,
                steps = 3,
            ) { onUpdate { repo -> repo.setDrawerColumns(it.toInt()) } }
            SliderRow(
                label = "Drawer icon size",
                value = settings.drawerIconSizeDp,
                range = 36f..72f,
            ) { onUpdate { repo -> repo.setDrawerIconSize(it) } }
            ListItem(
                headlineContent = { Text(stringResource(R.string.hidden_apps)) },
                modifier = Modifier.clickable(onClick = onManageHidden),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("Appearance")
            SwitchRow("Show status bar", settings.showStatusBar) {
                onUpdate { repo -> repo.setShowStatusBar(it) }
            }
            SliderRow(
                label = "Status bar scrim (${(settings.statusBarScrimOpacity * 100).toInt()}%)",
                value = settings.statusBarScrimOpacity,
                range = 0f..1f,
            ) { onUpdate { repo -> repo.setStatusBarScrim(it) } }
            ListItem(
                headlineContent = { Text(stringResource(R.string.default_wallpaper)) },
                supportingContent = {
                    Text(
                        if (settings.defaultWallpaperUri != null) "Custom image set"
                        else "Built-in default",
                    )
                },
                modifier = Modifier.clickable {
                    wallpaperPicker.launch(arrayOf("image/*"))
                },
            )
            if (settings.defaultWallpaperUri != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.clear_wallpaper)) },
                    modifier = Modifier.clickable {
                        onUpdate { it.setWallpaperUri(null) }
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("Icons")
            ListItem(
                headlineContent = { Text("Icon pack") },
                supportingContent = {
                    val label = iconPacks.find { it.packageName == settings.iconPackPackage }?.label
                        ?: if (settings.iconPackPackage == null) "System default" else settings.iconPackPackage
                    Text(label ?: "System default")
                },
                modifier = Modifier.clickable { pickIconPack = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("System")
            ListItem(
                headlineContent = { Text(stringResource(R.string.set_as_home)) },
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.enable_notification_access)) },
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }
    }

    if (pickIconPack) {
        AlertDialog(
            onDismissRequest = { pickIconPack = false },
            title = { Text("Icon pack") },
            text = {
                Column {
                    TextButton(onClick = {
                        onUpdate { it.setIconPack(null) }
                        pickIconPack = false
                    }) { Text("System default") }
                    iconPacks.forEach { pack ->
                        TextButton(onClick = {
                            onUpdate { it.setIconPack(pack.packageName) }
                            pickIconPack = false
                        }) { Text(pack.label) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickIconPack = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChecked)
        },
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
