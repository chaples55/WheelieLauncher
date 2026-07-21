package com.chaples55.wheelielauncher.ui.drawer

import android.content.ComponentName
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.chaples55.wheelielauncher.R
import com.chaples55.wheelielauncher.data.AppCustomization
import com.chaples55.wheelielauncher.data.LauncherApp
import com.chaples55.wheelielauncher.data.key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * @param progressController shared Murine-style progress (0 closed … 1 open)
 * @param visible intent from ViewModel; animated via [progressController]
 */
@Composable
fun AppDrawerHost(
    visible: Boolean,
    progressController: DrawerProgressController,
    apps: List<LauncherApp>,
    drawerColumns: Int,
    drawerIconSizeDp: Float,
    drawerShowLabels: Boolean,
    drawerShowSearch: Boolean,
    customizations: Map<String, AppCustomization>,
    loadIconBitmap: suspend (ComponentName, String?, Int) -> Bitmap?,
    peekIconBitmap: (ComponentName, String?, Int) -> Bitmap?,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (ComponentName) -> Unit,
    onAddToDock: (ComponentName) -> Unit,
    onHide: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    onChangeLabel: (ComponentName, String?) -> Unit,
    onChangeIcon: (ComponentName) -> Unit,
    onProgressSettledClosed: () -> Unit,
    onProgressSettledOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mounted by remember { mutableStateOf(false) }
    val progress = progressController.progress.value
    val onDismissUpdated = rememberUpdatedState(onDismiss)

    // Mount as soon as we start opening so the first frame can track the finger.
    LaunchedEffect(visible, progress) {
        if (visible || progress > 0.001f) mounted = true
    }

    // Notify VM when settle completes (keeps drawerOpen in sync with animation).
    LaunchedEffect(progressController) {
        var lastBucket = -1
        snapshotFlow { progressController.progress.value }
            .distinctUntilChanged()
            .collect { p ->
                val bucket = when {
                    p <= 0.001f -> 0
                    p >= 0.999f -> 1
                    else -> 2
                }
                if (bucket != lastBucket) {
                    lastBucket = bucket
                    when (bucket) {
                        0 -> onProgressSettledClosed()
                        1 -> onProgressSettledOpen()
                    }
                }
            }
    }

    if (!mounted) return

    val height = progressController.panelHeightPx
    val translation = if (height > 0f) drawerTranslationY(progress, height) else 0f
    val contentAlpha = DrawerProgressController.drawerContentAlpha(progress)
    val interactive = progress > 0.02f

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                progressController.updatePanelHeight(size.height.toFloat())
            }
            .graphicsLayer {
                translationY = translation
                alpha = if (progress <= 0.001f) 0f else 1f
            },
    ) {
        // Scrim is drawn under the sheet content by LauncherRoot; sheet itself fades in.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha.coerceAtLeast(0.001f) },
        ) {
            AppDrawer(
                apps = apps,
                drawerColumns = drawerColumns,
                drawerIconSizeDp = drawerIconSizeDp,
                drawerShowLabels = drawerShowLabels,
                drawerShowSearch = drawerShowSearch,
                customizations = customizations,
                loadIconBitmap = loadIconBitmap,
                peekIconBitmap = peekIconBitmap,
                touchEnabled = interactive,
                onDismiss = { onDismissUpdated.value() },
                onOpenSettings = onOpenSettings,
                onLaunch = onLaunch,
                onAddToDock = onAddToDock,
                onHide = onHide,
                onUninstall = onUninstall,
                onAppInfo = onAppInfo,
                onChangeLabel = onChangeLabel,
                onChangeIcon = onChangeIcon,
                onPullChanged = { pullPx ->
                    val h = progressController.panelHeightPx.coerceAtLeast(1f)
                    progressController.dragTo(1f - (pullPx / h))
                },
                onPullEnd = { pullPx, velocityY ->
                    val h = progressController.panelHeightPx.coerceAtLeast(1f)
                    val at = (1f - pullPx / h).coerceIn(0f, 1f)
                    progressController.settleFromGesture(
                        atProgress = at,
                        velocityYpxPerMs = velocityY,
                        wasOpening = false,
                    )
                },
                resetPullToken = progress > 0.95f,
            )
        }
    }
}

@Composable
fun AppDrawer(
    apps: List<LauncherApp>,
    drawerColumns: Int,
    drawerIconSizeDp: Float,
    drawerShowLabels: Boolean,
    drawerShowSearch: Boolean,
    customizations: Map<String, AppCustomization>,
    loadIconBitmap: suspend (ComponentName, String?, Int) -> Bitmap?,
    peekIconBitmap: (ComponentName, String?, Int) -> Bitmap? = { _, _, _ -> null },
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (ComponentName) -> Unit,
    onAddToDock: (ComponentName) -> Unit,
    onHide: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    onChangeLabel: (ComponentName, String?) -> Unit,
    onChangeIcon: (ComponentName) -> Unit,
    onPullChanged: (Float) -> Unit,
    onPullEnd: (Float, Float) -> Unit,
    resetPullToken: Boolean,
    touchEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = touchEnabled, onBack = onDismiss)
    var renameApp by remember { mutableStateOf<LauncherApp?>(null) }
    var renameText by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val appsUpdated = rememberUpdatedState(apps)
    val labelsUpdated = rememberUpdatedState(
        apps.associate { app ->
            val key = app.componentName.key()
            key to (customizations[key]?.customLabel ?: app.label)
        },
    )
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val columns = drawerColumns.coerceIn(2, 6)
    // Use real dp→px so the settings slider visibly changes icon size.
    val iconPx = with(density) { drawerIconSizeDp.dp.toPx().toInt().coerceAtLeast(1) }
    val labelExtraPx = if (drawerShowLabels) {
        with(density) { 36.dp.roundToPx() }
    } else {
        with(density) { 12.dp.roundToPx() }
    }
    val cellHeightPx = iconPx + labelExtraPx + with(density) { 12.dp.roundToPx() }
    val displayLabels = labelsUpdated.value
    val customIcons = remember(customizations) {
        customizations.mapValues { it.value.customIcon }
    }

    var shownApps by remember { mutableStateOf(apps) }
    LaunchedEffect(apps, query, displayLabels) {
        val q = query.trim()
        shownApps = if (q.isEmpty()) {
            apps
        } else {
            withContext(Dispatchers.Default) {
                apps.filter { app ->
                    val label = displayLabels[app.componentName.key()] ?: app.label
                    label.contains(q, ignoreCase = true) ||
                        app.packageName.contains(q, ignoreCase = true)
                }
            }
        }
    }

    val adapter = remember {
        DrawerAppsAdapter(
            scope = scope,
            peekIcon = peekIconBitmap,
            loadIcon = loadIconBitmap,
            onLaunch = onLaunch,
            onAddToDock = onAddToDock,
            onChangeLabel = { cn ->
                val list = appsUpdated.value
                val labels = labelsUpdated.value
                val app = list.find { it.componentName == cn } ?: return@DrawerAppsAdapter
                renameApp = app
                renameText = labels[cn.key()] ?: app.label
            },
            onChangeIcon = onChangeIcon,
            onAppInfo = onAppInfo,
            onHide = onHide,
            onUninstall = onUninstall,
            onOpenSettings = onOpenSettings,
            onQueryChanged = { q -> query = q },
        )
    }

    // Clear search when drawer opens without remounting the RV.
    LaunchedEffect(resetPullToken) {
        if (resetPullToken) {
            query = ""
            adapter.setQueryExternal("")
        }
    }

    val onPullChangedState = rememberUpdatedState(onPullChanged)
    val onPullEndState = rememberUpdatedState(onPullEnd)
    val touchEnabledState = rememberUpdatedState(touchEnabled)

    LaunchedEffect(onLaunch, onAddToDock, onChangeIcon, onAppInfo, onHide, onUninstall, onOpenSettings) {
        adapter.onLaunch = onLaunch
        adapter.onAddToDock = onAddToDock
        adapter.onChangeIcon = onChangeIcon
        adapter.onAppInfo = onAppInfo
        adapter.onHide = onHide
        adapter.onUninstall = onUninstall
        adapter.onOpenSettings = onOpenSettings
        adapter.onQueryChanged = { q -> query = q }
    }
    LaunchedEffect(peekIconBitmap, loadIconBitmap) {
        adapter.updateIconLoaders(peekIconBitmap, loadIconBitmap)
    }
    LaunchedEffect(shownApps, drawerShowSearch) {
        adapter.submitList(buildDrawerRows(shownApps, drawerShowSearch))
    }
    LaunchedEffect(iconPx, cellHeightPx, drawerShowLabels, drawerShowSearch, columns, customIcons, displayLabels) {
        adapter.config = DrawerBindConfig(
            iconSizePx = iconPx,
            cellHeightPx = cellHeightPx,
            showLabels = drawerShowLabels,
            showSearch = drawerShowSearch,
            columns = columns,
            customIcons = customIcons,
            displayLabels = displayLabels,
        )
    }
    LaunchedEffect(query) {
        adapter.setQueryExternal(query)
    }

    AndroidView(
        factory = { context ->
            PullDismissRecyclerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor("#F2101014".toColorInt())
                layoutManager = GridLayoutManager(context, columns).also { lm ->
                    lm.spanSizeLookup = adapter.spanSizeLookup(columns)
                }
                setHasFixedSize(false)
                itemAnimator = null
                setItemViewCacheSize(columns * 8)
                recycledViewPool.setMaxRecycledViews(TYPE_APP_CACHE, columns * 10)
                this.adapter = adapter
                clipToPadding = false
                setPadding(0, 0, 0, (24 * resources.displayMetrics.density).toInt())
                this.onPullChanged = { onPullChangedState.value(it) }
                this.onPullEnd = { amount, velocity -> onPullEndState.value(amount, velocity) }
                ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                    val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                    v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
                    insets
                }
            }
        },
        update = { rv ->
            val lm = rv.layoutManager as GridLayoutManager
            if (lm.spanCount != columns) {
                lm.spanCount = columns
                lm.spanSizeLookup = adapter.spanSizeLookup(columns)
            }
            if (rv.adapter !== adapter) rv.adapter = adapter
            rv.onPullChanged = { onPullChangedState.value(it) }
            rv.onPullEnd = { amount, velocity ->
                onPullEndState.value(amount, velocity)
                rv.resetPull()
            }
            // GONE while closed so the mounted RV cannot steal home-screen swipes.
            val enable = touchEnabledState.value
            rv.visibility = if (enable) View.VISIBLE else View.GONE
            rv.isEnabled = enable
        },
        modifier = modifier.fillMaxSize(),
    )

    renameApp?.let { app ->
        AlertDialog(
            onDismissRequest = { renameApp = null },
            title = { Text(stringResource(R.string.change_label)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChangeLabel(app.componentName, renameText.trim().ifEmpty { null })
                        renameApp = null
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { renameApp = null }) { Text("Cancel") }
            },
        )
    }
}

private const val TYPE_APP_CACHE = 2
