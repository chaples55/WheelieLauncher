package com.chaples55.wheelielauncher.ui.drawer

import android.content.ComponentName
import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DISMISS_DISTANCE_PX = 140f

@Composable
fun AppDrawerHost(
    visible: Boolean,
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
    modifier: Modifier = Modifier,
) {
    // Keep the Views drawer mounted after first open so reopen is instant.
    var mounted by remember { mutableStateOf(false) }
    var panelHeightPx by remember { mutableFloatStateOf(0f) }
    val slide = remember { Animatable(0f) }
    var pullPx by remember { mutableFloatStateOf(0f) }
    var pendingFlyIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val onDismissUpdated = rememberUpdatedState(onDismiss)

    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            pendingFlyIn = true
        }
    }

    LaunchedEffect(visible, panelHeightPx, pendingFlyIn) {
        if (panelHeightPx <= 0f || !mounted) return@LaunchedEffect
        if (visible) {
            pullPx = 0f
            if (pendingFlyIn) {
                slide.snapTo(panelHeightPx)
                pendingFlyIn = false
            }
            slide.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        } else {
            pullPx = 0f
            pendingFlyIn = false
            slide.animateTo(panelHeightPx, tween(240, easing = FastOutSlowInEasing))
        }
    }

    if (!mounted) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                val h = size.height.toFloat()
                if (h > 0f && kotlin.math.abs(panelHeightPx - h) > 1f) {
                    val wasUnset = panelHeightPx == 0f
                    panelHeightPx = h
                    if (wasUnset && !visible) {
                        scope.launch { slide.snapTo(h) }
                    }
                }
            }
            .graphicsLayer {
                translationY = slide.value + pullPx
            },
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
            onDismiss = { onDismissUpdated.value() },
            onOpenSettings = onOpenSettings,
            onLaunch = onLaunch,
            onAddToDock = onAddToDock,
            onHide = onHide,
            onUninstall = onUninstall,
            onAppInfo = onAppInfo,
            onChangeLabel = onChangeLabel,
            onChangeIcon = onChangeIcon,
            onPullChanged = { pullPx = it },
            onPullEnd = { amount, _ ->
                if (amount >= DISMISS_DISTANCE_PX / 2f) {
                    onDismissUpdated.value()
                } else {
                    pullPx = 0f
                }
            },
            resetPullToken = visible,
        )
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
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
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
                if (amount > 0f && amount < DISMISS_DISTANCE_PX / 2f) {
                    rv.resetPull()
                }
                onPullEndState.value(amount, velocity)
            }
            if (!resetPullToken) {
                rv.resetPull()
            }
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
