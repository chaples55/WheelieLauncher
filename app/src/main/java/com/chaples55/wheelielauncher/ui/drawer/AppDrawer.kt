package com.chaples55.wheelielauncher.ui.drawer

import android.content.ComponentName
import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaples55.wheelielauncher.R
import com.chaples55.wheelielauncher.data.AppCustomization
import com.chaples55.wheelielauncher.data.LauncherApp
import com.chaples55.wheelielauncher.data.key
import kotlinx.coroutines.Dispatchers
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
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            initialOffsetY = { full -> full },
        ) + fadeIn(animationSpec = tween(220)),
        exit = slideOutVertically(
            animationSpec = tween(240, easing = FastOutSlowInEasing),
            targetOffsetY = { full -> full },
        ) + fadeOut(animationSpec = tween(180)),
        modifier = modifier,
    ) {
        // Reset search each time the drawer is shown.
        var resetKey by remember { mutableStateOf(0) }
        LaunchedEffect(visible) {
            if (visible) resetKey++
        }
        key(resetKey) {
            AppDrawer(
                apps = apps,
                drawerColumns = drawerColumns,
                drawerIconSizeDp = drawerIconSizeDp,
                drawerShowLabels = drawerShowLabels,
                drawerShowSearch = drawerShowSearch,
                customizations = customizations,
                loadIconBitmap = loadIconBitmap,
                peekIconBitmap = peekIconBitmap,
                onDismiss = onDismiss,
                onOpenSettings = onOpenSettings,
                onLaunch = onLaunch,
                onAddToDock = onAddToDock,
                onHide = onHide,
                onUninstall = onUninstall,
                onAppInfo = onAppInfo,
                onChangeLabel = onChangeLabel,
                onChangeIcon = onChangeIcon,
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
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var query by remember { mutableStateOf("") }
    var renameApp by remember { mutableStateOf<LauncherApp?>(null) }
    var renameText by remember { mutableStateOf("") }
    val onDismissUpdated = rememberUpdatedState(onDismiss)
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
    val iconPx = with(density) { drawerIconSizeDp.dp.roundToPx().coerceIn(72, 256) }
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
        )
    }

    LaunchedEffect(onLaunch, onAddToDock, onChangeIcon, onAppInfo, onHide, onUninstall) {
        adapter.onLaunch = onLaunch
        adapter.onAddToDock = onAddToDock
        adapter.onChangeIcon = onChangeIcon
        adapter.onAppInfo = onAppInfo
        adapter.onHide = onHide
        adapter.onUninstall = onUninstall
    }
    LaunchedEffect(peekIconBitmap, loadIconBitmap) {
        adapter.updateIconLoaders(peekIconBitmap, loadIconBitmap)
    }
    LaunchedEffect(shownApps) {
        adapter.submitList(shownApps)
    }
    LaunchedEffect(iconPx, cellHeightPx, drawerShowLabels, customIcons, displayLabels) {
        adapter.config = DrawerBindConfig(
            iconSizePx = iconPx,
            cellHeightPx = cellHeightPx,
            showLabels = drawerShowLabels,
            customIcons = customIcons,
            displayLabels = displayLabels,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { translationY = dragOffset }
            .background(Color(0xF2101014))
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 0) {
                                    dragOffset += dragAmount
                                    if (dragOffset >= DISMISS_DISTANCE_PX) {
                                        onDismissUpdated.value()
                                        dragOffset = 0f
                                    }
                                } else {
                                    dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                                }
                            },
                            onDragEnd = {
                                if (dragOffset >= DISMISS_DISTANCE_PX / 2f) {
                                    onDismissUpdated.value()
                                }
                                dragOffset = 0f
                            },
                            onDragCancel = { dragOffset = 0f },
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.app_drawer),
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = Color.White,
                    )
                }
            }

            if (drawerShowSearch) {
                DrawerSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            AndroidView(
                factory = { context ->
                    RecyclerView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        layoutManager = GridLayoutManager(context, columns)
                        setHasFixedSize(true)
                        itemAnimator = null
                        setItemViewCacheSize(columns * 6)
                        recycledViewPool.setMaxRecycledViews(0, columns * 8)
                        overScrollMode = RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS
                        this.adapter = adapter
                        setPadding(12, 8, 12, 24)
                        clipToPadding = false
                    }
                },
                update = { rv ->
                    val lm = rv.layoutManager as? GridLayoutManager
                    if (lm != null && lm.spanCount != columns) {
                        lm.spanCount = columns
                    }
                    if (rv.adapter !== adapter) {
                        rv.adapter = adapter
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }

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

@Composable
private fun DrawerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color(0xFF1C1C22), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { inner ->
                Box(modifier = Modifier.padding(horizontal = 10.dp)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_apps),
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier.weight(1f),
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Clear",
                    tint = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}
