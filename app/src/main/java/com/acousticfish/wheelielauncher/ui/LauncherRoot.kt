package com.acousticfish.wheelielauncher.ui

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acousticfish.wheelielauncher.R
import com.acousticfish.wheelielauncher.WheelieApp
import com.acousticfish.wheelielauncher.data.AppCustomization
import com.acousticfish.wheelielauncher.data.DockItem
import com.acousticfish.wheelielauncher.data.key
import com.acousticfish.wheelielauncher.ui.drawer.AppDrawerHost
import com.acousticfish.wheelielauncher.ui.drawer.DrawerProgressController
import com.acousticfish.wheelielauncher.ui.home.CircularDock
import com.acousticfish.wheelielauncher.ui.home.DockSlot
import com.acousticfish.wheelielauncher.ui.home.HomeChromeOverlays
import com.acousticfish.wheelielauncher.ui.home.NowPlayingCenter
import com.acousticfish.wheelielauncher.ui.home.WallpaperBackground
import com.acousticfish.wheelielauncher.ui.home.homeBackgroundLongPress
import com.acousticfish.wheelielauncher.ui.home.homeVerticalGestures
import com.acousticfish.wheelielauncher.ui.home.wallpaperArtKey
import com.acousticfish.wheelielauncher.ui.icons.IconPickerScreen
import com.acousticfish.wheelielauncher.ui.settings.HiddenAppsScreen
import com.acousticfish.wheelielauncher.ui.settings.SettingsScreen
import com.acousticfish.wheelielauncher.util.handleHomeSwipeDown
import com.acousticfish.wheelielauncher.util.openClockApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun LauncherRoot(viewModel: LauncherViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val nowPlayingMeta by viewModel.nowPlayingMeta.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val app = context.applicationContext as WheelieApp
    var iconPickerFor by remember { mutableStateOf<ComponentName?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var packageLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val drawerProgress = remember { DrawerProgressController(scope) }
    val drawerP = drawerProgress.progress.value

    val swipeUpEnabled = state.settings.swipeUpToOpenDrawer
    val showDrawerButton = !(swipeUpEnabled && state.settings.hideDrawerButton)
    val overlaysBlockingHome =
        state.settingsOpen || iconPickerFor != null || showHidden || state.showOnboardingHome || state.showOnboardingMedia

    LaunchedEffect(density) {
        viewModel.setIconDensity(density)
    }

    LaunchedEffect(state.settings.swipeSensitivity) {
        drawerProgress.updateSwipeSensitivity(state.settings.swipeSensitivity)
    }

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

    val slotCount = LauncherViewModel.slotCountFor(state.dockItems.size, includeDrawer = showDrawerButton)
    val artKey = remember(
        nowPlayingMeta.hasSession,
        nowPlayingMeta.artworkBitmapKey,
        nowPlayingMeta.artworkUri,
        state.settings.defaultWallpaperUri,
    ) {
        wallpaperArtKey(
            hasSession = nowPlayingMeta.hasSession,
            artworkBitmapKey = nowPlayingMeta.artworkBitmapKey,
            artworkUri = nowPlayingMeta.artworkUri,
            defaultWallpaperUri = state.settings.defaultWallpaperUri,
        )
    }
    val artworkBitmap = remember(artKey) { viewModel.artworkBitmap() }
    val blurredBitmap = remember(artKey) {
        viewModel.blurredWallpaperBitmap(nowPlayingMeta.artworkBitmapKey)
    }

    val loadIconBitmap: suspend (ComponentName, String?, Int) -> Bitmap? = remember(viewModel) {
        { cn, custom, px -> viewModel.cachedIconBitmap(cn, custom, px) }
    }
    val peekIconBitmap: (ComponentName, String?, Int) -> Bitmap? = remember(viewModel) {
        { cn, custom, px -> viewModel.peekIconBitmap(cn, custom, px) }
    }
    val onSelectDock = remember(viewModel) { { index: Int -> viewModel.setSelectedDockIndex(index) } }
    val onLaunchDock = remember(viewModel, drawerProgress) {
        { slot: DockSlot ->
            when (slot) {
                DockSlot.Drawer -> {
                    viewModel.openDrawer()
                    drawerProgress.animateOpen()
                }
                is DockSlot.App -> viewModel.launch(slot.item.componentName)
                DockSlot.Empty -> Unit
            }
        }
    }
    val onRemoveDock = remember(viewModel) { { cn: ComponentName -> viewModel.removeFromDock(cn) } }
    val onReorderDock = remember(viewModel) {
        { cn: ComponentName, index: Int -> viewModel.moveDockItem(cn, index) }
    }
    val onOpenNowPlaying = remember(viewModel) { { viewModel.openNowPlayingApp() } }
    val onPlayPause = remember(viewModel) { { viewModel.togglePlayPause() } }
    val onSkipPrevious = remember(viewModel) { { viewModel.skipToPrevious() } }
    val onSkipNext = remember(viewModel) { { viewModel.skipToNext() } }
    val onEqClick = remember(viewModel) { { viewModel.launchEqApp() } }
    val ensureBlurred: suspend (String, Bitmap) -> Bitmap? = remember(viewModel) {
        { key, bmp -> viewModel.ensureBlurredWallpaper(key, bmp) }
    }
    val labelsByComponent = state.appLabelsByComponent
    val customizations = state.settings.customizations
    val resolveLabel = remember(labelsByComponent, customizations) {
        { item: DockItem ->
            item.customLabel
                ?: customizations[item.componentName.key()]?.customLabel
                ?: labelsByComponent[item.componentName.key()]
                ?: item.componentName.packageName
        }
    }

    val homeScale = DrawerProgressController.homeScale(drawerP)
    val homeAlpha = DrawerProgressController.homeAlpha(drawerP)
    val scrimAlpha = DrawerProgressController.scrimAlpha(drawerP)

    var homeDragAccum by remember { mutableFloatStateOf(0f) }
    var homeDragProgress by remember { mutableFloatStateOf(0f) }
    var homeMenuAt by remember { mutableStateOf<Offset?>(null) }
    val showStatusBar = state.settings.showStatusBar
    val homeGesturesEnabled = !overlaysBlockingHome
    val notificationAction = rememberUpdatedState {
        handleHomeSwipeDown(
            context = context,
            statusBarPreferredVisible = showStatusBar,
        )
    }
    // Gestures live on the home layer (under the drawer) so an open drawer keeps pull-to-dismiss.
    val homeGestureModifier = Modifier
        .homeVerticalGestures(
            enabled = homeGesturesEnabled,
            swipeUpToOpenDrawer = swipeUpEnabled,
            isDrawerClosed = { drawerProgress.progress.value <= 0.001f },
            onNotificationSwipeDown = { notificationAction.value() },
            onDrawerDragStart = {
                homeDragAccum = 0f
                homeDragProgress = drawerProgress.progress.value
            },
            onDrawerDrag = { _, dragAmount ->
                homeDragAccum += -dragAmount
                val h = drawerProgress.panelHeightPx.coerceAtLeast(1f)
                homeDragProgress = (homeDragProgress + (-dragAmount) / h).coerceIn(0f, 1f)
                drawerProgress.dragTo(homeDragProgress)
            },
            onDrawerDragEnd = {
                val opening = homeDragAccum > 0f || homeDragProgress < 0.5f
                drawerProgress.settleFromGesture(
                    atProgress = homeDragProgress,
                    velocityYpxPerMs = 0f,
                    wasOpening = opening,
                )
                homeDragAccum = 0f
            },
            onDrawerDragCancel = {
                drawerProgress.settleFromGesture(
                    atProgress = homeDragProgress,
                    velocityYpxPerMs = 0f,
                    wasOpening = homeDragProgress < 0.5f,
                )
                homeDragAccum = 0f
            },
        )
        .homeBackgroundLongPress(enabled = homeGesturesEnabled) { pos ->
            homeMenuAt = pos
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                drawerProgress.updatePanelHeight(size.height.toFloat())
            },
    ) {
        WallpaperBackground(
            artKey = artKey,
            isMediaArt = nowPlayingMeta.hasSession && artKey.startsWith("media:"),
            artworkBitmapKey = nowPlayingMeta.artworkBitmapKey,
            artworkBitmap = artworkBitmap,
            blurredBitmap = blurredBitmap,
            artworkUri = nowPlayingMeta.artworkUri,
            defaultWallpaperUri = state.settings.defaultWallpaperUri,
            ensureBlurred = ensureBlurred,
        )

        if (showStatusBar) {
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
                .then(if (showStatusBar) Modifier.statusBarsPadding() else Modifier)
                .graphicsLayer {
                    scaleX = homeScale
                    scaleY = homeScale
                    alpha = homeAlpha.coerceAtLeast(0.001f)
                }
                .then(homeGestureModifier),
            contentAlignment = Alignment.Center,
        ) {
            CircularDock(
                dockItems = state.dockItems,
                slotCount = slotCount.coerceAtLeast(1),
                selectedIndex = state.selectedDockIndex.coerceIn(0, (slotCount - 1).coerceAtLeast(0)),
                iconSizeDp = state.settings.dockIconSizeDp,
                ringRadiusFraction = state.settings.dockRingRadiusFraction,
                showLabels = state.settings.dockShowLabels,
                showDrawerButton = showDrawerButton,
                loadIconBitmap = loadIconBitmap,
                peekIconBitmap = peekIconBitmap,
                resolveLabel = resolveLabel,
                onSelect = onSelectDock,
                onLaunch = onLaunchDock,
                onRemove = onRemoveDock,
                onReorder = onReorderDock,
            )

            NowPlayingCenter(
                meta = nowPlayingMeta,
                progress = viewModel.playbackProgress,
                artworkBitmap = artworkBitmap,
                diameter = state.settings.nowPlayingSizeDp.dp,
                onOpenApp = onOpenNowPlaying,
                onPlayPause = onPlayPause,
            )

            HomeChromeOverlays(
                showClock = state.settings.showClock,
                showEqButton = state.settings.showEqButton,
                showSkipButtons = state.settings.showSkipButtons,
                onEqClick = onEqClick,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onClockClick = { openClockApp(context) },
            )

            homeMenuAt?.let { anchor ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(anchor.x.roundToInt(), anchor.y.roundToInt()) },
                ) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { homeMenuAt = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.launcher_settings)) },
                            onClick = {
                                homeMenuAt = null
                                viewModel.openSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_drawer)) },
                            onClick = {
                                homeMenuAt = null
                                viewModel.openDrawer()
                                drawerProgress.animateOpen()
                            },
                        )
                    }
                }
            }
        }

        if (scrimAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha)),
            )
        }

        AppDrawerHost(
            visible = state.drawerOpen && !overlaysBlockingHome,
            progressController = drawerProgress,
            apps = state.apps,
            drawerColumns = state.settings.drawerColumns,
            drawerIconSizeDp = state.settings.drawerIconSizeDp,
            drawerShowLabels = state.settings.drawerShowLabels,
            drawerShowSearch = state.settings.drawerShowSearch,
            drawerBackgroundOpacity = state.settings.drawerBackgroundOpacity,
            artKey = artKey,
            isMediaArt = nowPlayingMeta.hasSession && artKey.startsWith("media:"),
            artworkBitmapKey = nowPlayingMeta.artworkBitmapKey,
            artworkBitmap = artworkBitmap,
            blurredBitmap = blurredBitmap,
            artworkUri = nowPlayingMeta.artworkUri,
            defaultWallpaperUri = state.settings.defaultWallpaperUri,
            ensureBlurred = ensureBlurred,
            customizations = state.settings.customizations,
            loadIconBitmap = loadIconBitmap,
            peekIconBitmap = peekIconBitmap,
            onDismiss = {
                viewModel.closeDrawer()
                drawerProgress.animateClose()
            },
            onOpenSettings = { viewModel.openSettings() },
            onLaunch = {
                viewModel.closeDrawer()
                drawerProgress.animateClose()
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
            onProgressSettledClosed = { viewModel.closeDrawer() },
            onProgressSettledOpen = { viewModel.openDrawer() },
        )

        if (state.settingsOpen && iconPickerFor == null && !showHidden) {
            SettingsScreen(
                settings = state.settings,
                iconPacks = state.iconPacks,
                apps = state.apps,
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
