package com.chaples55.wheelielauncher.ui

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaples55.wheelielauncher.R
import com.chaples55.wheelielauncher.WheelieApp
import com.chaples55.wheelielauncher.data.AppCustomization
import com.chaples55.wheelielauncher.data.key
import com.chaples55.wheelielauncher.ui.drawer.AppDrawer
import com.chaples55.wheelielauncher.ui.home.CircularDock
import com.chaples55.wheelielauncher.ui.home.DockSlot
import com.chaples55.wheelielauncher.ui.home.NowPlayingCenter
import com.chaples55.wheelielauncher.ui.home.WallpaperBackground
import com.chaples55.wheelielauncher.ui.home.wallpaperArtKey
import com.chaples55.wheelielauncher.ui.icons.IconPickerScreen
import com.chaples55.wheelielauncher.ui.settings.HiddenAppsScreen
import com.chaples55.wheelielauncher.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LauncherRoot(viewModel: LauncherViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as WheelieApp
    var iconPickerFor by remember { mutableStateOf<ComponentName?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var packageLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(state.settings.hiddenPackages) {
        packageLabels = withContext(Dispatchers.IO) {
            state.settings.hiddenPackages.associateWith { pkg ->
                try {
                    val ai = context.packageManager.getApplicationInfo(pkg, 0)
                    context.packageManager.getApplicationLabel(ai).toString()
                } catch (_: Exception) {
                    pkg
                }
            }
        }
    }

    val slotCount = LauncherViewModel.slotCountFor(state.dockItems.size)
    val np = state.nowPlaying
    val artKey = remember(np.hasSession, np.artworkBitmapKey, np.artworkUri, state.settings.defaultWallpaperUri) {
        wallpaperArtKey(
            hasSession = np.hasSession,
            artworkBitmapKey = np.artworkBitmapKey,
            artworkUri = np.artworkUri,
            defaultWallpaperUri = state.settings.defaultWallpaperUri,
        )
    }
    val artworkBitmap = remember(artKey) { viewModel.artworkBitmap() }

    Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackground(
            artKey = artKey,
            isMediaArt = np.hasSession && artKey.startsWith("media:"),
            artworkBitmap = artworkBitmap,
            artworkUri = np.artworkUri,
            defaultWallpaperUri = state.settings.defaultWallpaperUri,
        )

        if (state.settings.showStatusBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Color.Black.copy(alpha = state.settings.statusBarScrimOpacity)),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (state.settings.showStatusBar) Modifier.statusBarsPadding() else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            CircularDock(
                dockItems = state.dockItems,
                slotCount = slotCount,
                selectedIndex = state.selectedDockIndex.coerceIn(0, (slotCount - 1).coerceAtLeast(0)),
                iconSizeDp = state.settings.dockIconSizeDp,
                ringRadiusFraction = state.settings.dockRingRadiusFraction,
                showLabels = state.settings.dockShowLabels,
                resolveIcon = { cn, custom -> viewModel.resolveIcon(cn, custom) },
                resolveLabel = { item ->
                    item.customLabel
                        ?: state.settings.customizations[item.componentName.key()]?.customLabel
                        ?: state.apps.find { it.componentName == item.componentName }?.label
                        ?: item.componentName.packageName
                },
                onSelect = { viewModel.setSelectedDockIndex(it) },
                onLaunch = { slot ->
                    when (slot) {
                        DockSlot.Drawer -> viewModel.openDrawer()
                        is DockSlot.App -> viewModel.launch(slot.item.componentName)
                        DockSlot.Empty -> Unit
                    }
                },
                onRemove = { viewModel.removeFromDock(it) },
                onReorder = { cn, index -> viewModel.moveDockItem(cn, index) },
            )

            NowPlayingCenter(
                nowPlaying = np,
                artworkBitmap = artworkBitmap,
                diameter = state.settings.nowPlayingSizeDp.dp,
                onPlayPause = { viewModel.togglePlayPause() },
            )
        }

        if (state.drawerOpen && !state.settingsOpen && iconPickerFor == null && !showHidden) {
            AppDrawer(
                apps = state.apps,
                settings = state.settings,
                resolveIcon = { cn, custom -> viewModel.resolveIcon(cn, custom) },
                onDismiss = { viewModel.closeDrawer() },
                onOpenSettings = { viewModel.openSettings() },
                onLaunch = {
                    viewModel.closeDrawer()
                    viewModel.launch(it)
                },
                onAddToDock = { cn ->
                    viewModel.addToDock(cn) {
                        Toast.makeText(context, context.getString(R.string.dock_full), Toast.LENGTH_SHORT).show()
                    }
                },
                onHide = { viewModel.hideApp(it) },
                onUninstall = { viewModel.uninstall(it) },
                onAppInfo = { viewModel.openAppInfo(it) },
                onChangeLabel = { cn, label ->
                    val existing = state.settings.customizations[cn.key()]
                    viewModel.setCustomization(
                        cn,
                        AppCustomization(customLabel = label, customIcon = existing?.customIcon),
                    )
                },
                onChangeIcon = { iconPickerFor = it },
            )
        }

        if (state.settingsOpen && iconPickerFor == null && !showHidden) {
            SettingsScreen(
                settings = state.settings,
                iconPacks = state.iconPacks,
                onUpdate = { block -> viewModel.updateSettings(block) },
                onBack = { viewModel.closeSettings() },
                onManageHidden = { showHidden = true },
            )
        }

        if (showHidden) {
            HiddenAppsScreen(
                hiddenPackages = state.settings.hiddenPackages,
                packageLabels = packageLabels,
                onUnhide = { viewModel.unhideApp(it) },
                onBack = { showHidden = false },
            )
        }

        iconPickerFor?.let { cn ->
            IconPickerScreen(
                componentName = cn,
                iconPacks = state.iconPacks,
                selectedPack = state.settings.iconPackPackage,
                loadDrawables = { pack, query ->
                    app.container.iconPackRepository.searchPackDrawables(pack, query)
                },
                loadDrawable = { pack, name ->
                    app.container.iconPackRepository.loadPackDrawable(pack, name)
                },
                onPick = { token ->
                    val existing = state.settings.customizations[cn.key()]
                    viewModel.setCustomization(
                        cn,
                        AppCustomization(customLabel = existing?.customLabel, customIcon = token),
                    )
                    iconPickerFor = null
                },
                onBack = { iconPickerFor = null },
            )
        }

        if (state.showOnboardingHome) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissOnboardingHome() },
                title = { Text(stringResource(R.string.onboarding_home_title)) },
                text = { Text(stringResource(R.string.onboarding_home_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_HOME_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            viewModel.dismissOnboardingHome()
                        },
                    ) { Text(stringResource(R.string.open_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissOnboardingHome() }) {
                        Text(stringResource(R.string.skip))
                    }
                },
            )
        } else if (state.showOnboardingMedia) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissOnboardingMedia() },
                title = { Text(stringResource(R.string.onboarding_media_title)) },
                text = { Text(stringResource(R.string.onboarding_media_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            viewModel.dismissOnboardingMedia()
                        },
                    ) { Text(stringResource(R.string.open_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissOnboardingMedia() }) {
                        Text(stringResource(R.string.skip))
                    }
                },
            )
        }
    }
}
