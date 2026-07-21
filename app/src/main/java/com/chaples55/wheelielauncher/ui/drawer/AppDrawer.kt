package com.chaples55.wheelielauncher.ui.drawer

import android.content.ComponentName
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaples55.wheelielauncher.R
import com.chaples55.wheelielauncher.data.LauncherApp
import com.chaples55.wheelielauncher.data.LauncherSettings
import com.chaples55.wheelielauncher.data.key
import com.chaples55.wheelielauncher.ui.components.AppIconImage
import com.chaples55.wheelielauncher.ui.components.rememberResolvedIcon
import kotlin.math.roundToInt

private const val DISMISS_DISTANCE_PX = 140f

@Composable
fun AppDrawer(
    apps: List<LauncherApp>,
    settings: LauncherSettings,
    resolveIcon: suspend (ComponentName, String?) -> Drawable?,
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
    val gridState = rememberLazyGridState()
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var menuApp by remember { mutableStateOf<LauncherApp?>(null) }
    var renameApp by remember { mutableStateOf<LauncherApp?>(null) }
    var renameText by remember { mutableStateOf("") }

    val nestedScroll = remember(onDismiss) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val atTop = !gridState.canScrollBackward
                // While drawer is already pulled down, continue sheet drag from anywhere.
                if (dragOffset > 0f) {
                    val newOffset = (dragOffset + available.y).coerceAtLeast(0f)
                    val consumedY = newOffset - dragOffset
                    dragOffset = newOffset
                    if (dragOffset >= DISMISS_DISTANCE_PX) {
                        onDismiss()
                        dragOffset = 0f
                    }
                    return Offset(0f, consumedY)
                }
                // Start pull-to-dismiss from list content when scrolled to top.
                if (atTop && available.y > 0f && source == NestedScrollSource.UserInput) {
                    dragOffset += available.y
                    if (dragOffset >= DISMISS_DISTANCE_PX) {
                        onDismiss()
                        dragOffset = 0f
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Overscroll at top of list.
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    dragOffset += available.y
                    if (dragOffset >= DISMISS_DISTANCE_PX) {
                        onDismiss()
                        dragOffset = 0f
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Fast swipe down from anywhere closes the drawer.
                if (available.y > 1800f) {
                    onDismiss()
                    dragOffset = 0f
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .background(Color(0xF2101014))
            .statusBarsPadding()
            .nestedScroll(nestedScroll),
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
                                        onDismiss()
                                        dragOffset = 0f
                                    }
                                } else {
                                    dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                                }
                            },
                            onDragEnd = {
                                if (dragOffset >= DISMISS_DISTANCE_PX / 2f) onDismiss()
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

            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.drawerColumns.coerceIn(2, 6)),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(apps, key = { it.componentName.flattenToString() }) { app ->
                    val custom = settings.customizations[app.componentName.key()]
                    val drawable = rememberResolvedIcon(app.componentName to custom?.customIcon) {
                        resolveIcon(app.componentName, custom?.customIcon)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.pointerInput(app) {
                            detectTapGestures(
                                onTap = { onLaunch(app.componentName) },
                                onLongPress = { menuApp = app },
                            )
                        },
                    ) {
                        AppIconImage(
                            drawable = drawable,
                            contentDescription = app.label,
                            size = settings.drawerIconSizeDp.dp,
                        )
                        if (settings.drawerShowLabels) {
                            Text(
                                text = app.label,
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuApp == app,
                        onDismissRequest = { menuApp = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_to_dock)) },
                            onClick = {
                                menuApp = null
                                onAddToDock(app.componentName)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.change_label)) },
                            onClick = {
                                menuApp = null
                                renameApp = app
                                renameText = app.label
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.change_icon)) },
                            onClick = {
                                menuApp = null
                                onChangeIcon(app.componentName)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_info)) },
                            onClick = {
                                menuApp = null
                                onAppInfo(app.packageName)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hide_app)) },
                            onClick = {
                                menuApp = null
                                onHide(app.packageName)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.uninstall)) },
                            onClick = {
                                menuApp = null
                                onUninstall(app.packageName)
                            },
                        )
                    }
                }
            }
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
