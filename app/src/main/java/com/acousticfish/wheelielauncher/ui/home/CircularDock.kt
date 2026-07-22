package com.acousticfish.wheelielauncher.ui.home

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acousticfish.wheelielauncher.R
import com.acousticfish.wheelielauncher.data.DockItem
import com.acousticfish.wheelielauncher.ui.components.CachedAppIcon
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

sealed class DockSlot {
    data object Drawer : DockSlot()
    data class App(val item: DockItem, val appIndex: Int) : DockSlot()
    data object Empty : DockSlot()
}

/** Drag state held outside slot composables so only the dragged slot reads dragX/Y. */
@Stable
private class DockDragState {
    var draggingCn by mutableStateOf<ComponentName?>(null)
    var dragX by mutableFloatStateOf(0f)
    var dragY by mutableFloatStateOf(0f)
    var dragMoved by mutableStateOf(false)
}

@Composable
fun CircularDock(
    dockItems: List<DockItem>,
    slotCount: Int,
    selectedIndex: Int,
    iconSizeDp: Float,
    ringRadiusFraction: Float,
    showLabels: Boolean,
    showDrawerButton: Boolean = true,
    loadIconBitmap: suspend (ComponentName, String?, Int) -> Bitmap?,
    peekIconBitmap: (ComponentName, String?, Int) -> Bitmap? = { _, _, _ -> null },
    resolveLabel: (DockItem) -> String,
    onSelect: (Int) -> Unit,
    onLaunch: (DockSlot) -> Unit,
    onRemove: (ComponentName) -> Unit,
    onReorder: (ComponentName, Int) -> Unit,
    iconPackPackage: String? = null,
    resolveCustomIcon: (ComponentName) -> String? = { null },
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val iconSize = iconSizeDp.dp
    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<ComponentName?>(null) }
    val dragState = remember { DockDragState() }

    val placements = remember(dockItems, slotCount, showDrawerButton) {
        computeFixedPlacements(dockItems, slotCount, showDrawerButton)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                widthPx = it.width
                heightPx = it.height
            },
    ) {
        if (widthPx == 0 || heightPx == 0) return@Box
        val cx = widthPx / 2f
        val cy = heightPx / 2f
        val radius = minOf(cx, cy) * ringRadiusFraction.coerceIn(0.45f, 0.92f)
        val iconPx = with(density) { iconSize.toPx() }
        val touchSlop = viewConfiguration.touchSlop

        placements.forEach { placement ->
            val angleRad = Math.toRadians(placement.angleDegrees.toDouble())
            val homeX = cx + radius * cos(angleRad).toFloat()
            val homeY = cy + radius * sin(angleRad).toFloat()
            DockSlotItem(
                placement = placement,
                homeX = homeX,
                homeY = homeY,
                cx = cx,
                cy = cy,
                iconPx = iconPx,
                iconSize = iconSize,
                touchSlop = touchSlop,
                showLabels = showLabels,
                dragState = dragState,
                placements = placements,
                dockItemCount = dockItems.size,
                menuFor = menuFor,
                onMenuForChange = { menuFor = it },
                loadIconBitmap = loadIconBitmap,
                peekIconBitmap = peekIconBitmap,
                iconPackPackage = iconPackPackage,
                resolveCustomIcon = resolveCustomIcon,
                resolveLabel = resolveLabel,
                onSelect = onSelect,
                onLaunch = onLaunch,
                onRemove = onRemove,
                onReorder = onReorder,
            )
        }
    }
}

@Composable
private fun DockSlotItem(
    placement: Placement,
    homeX: Float,
    homeY: Float,
    cx: Float,
    cy: Float,
    iconPx: Float,
    iconSize: androidx.compose.ui.unit.Dp,
    touchSlop: Float,
    showLabels: Boolean,
    dragState: DockDragState,
    placements: List<Placement>,
    dockItemCount: Int,
    menuFor: ComponentName?,
    onMenuForChange: (ComponentName?) -> Unit,
    loadIconBitmap: suspend (ComponentName, String?, Int) -> Bitmap?,
    peekIconBitmap: (ComponentName, String?, Int) -> Bitmap?,
    iconPackPackage: String?,
    resolveCustomIcon: (ComponentName) -> String?,
    resolveLabel: (DockItem) -> String,
    onSelect: (Int) -> Unit,
    onLaunch: (DockSlot) -> Unit,
    onRemove: (ComponentName) -> Unit,
    onReorder: (ComponentName, Int) -> Unit,
) {
    val appSlot = placement.slot as? DockSlot.App
    val cn = appSlot?.item?.componentName
    // Non-dragged slots only observe draggingCn (start/end), not per-frame dragX/Y.
    val isDraggingThis = cn != null && dragState.draggingCn == cn
    val x = if (isDraggingThis) dragState.dragX else homeX
    val y = if (isDraggingThis) dragState.dragY else homeY
    val dragMoved = if (isDraggingThis) dragState.dragMoved else false
    val offsetX = (x - iconPx / 2f).roundToInt()
    val offsetY = (y - iconPx / 2f).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX, offsetY) }
            .graphicsLayer {
                alpha = if (isDraggingThis && dragMoved) 0.85f else 1f
            }
            .pointerInput(placement.slot, placement.slotIndex) {
                detectTapGestures(
                    onTap = {
                        onSelect(placement.slotIndex)
                        onLaunch(placement.slot)
                    },
                )
            }
            .then(
                if (appSlot != null) {
                    Modifier.pointerInput(appSlot.item.componentName, placements, homeX, homeY, cx, cy) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                onSelect(placement.slotIndex)
                                dragState.draggingCn = appSlot.item.componentName
                                dragState.dragX = homeX
                                dragState.dragY = homeY
                                dragState.dragMoved = false
                                onMenuForChange(null)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragState.dragX += dragAmount.x
                                dragState.dragY += dragAmount.y
                                if (hypot(
                                        (dragState.dragX - homeX).toDouble(),
                                        (dragState.dragY - homeY).toDouble(),
                                    ) > touchSlop
                                ) {
                                    dragState.dragMoved = true
                                }
                            },
                            onDragEnd = {
                                if (!dragState.dragMoved) {
                                    onMenuForChange(appSlot.item.componentName)
                                } else {
                                    val angle = angleFromAtan(
                                        atan2(dragState.dragY - cy, dragState.dragX - cx) * (180f / PI.toFloat()),
                                    )
                                    val target = nearestAppSlotIndex(angle, placements, dockItemCount)
                                    if (target != null) {
                                        onReorder(appSlot.item.componentName, target)
                                    }
                                }
                                dragState.draggingCn = null
                                dragState.dragMoved = false
                            },
                            onDragCancel = {
                                dragState.draggingCn = null
                                dragState.dragMoved = false
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (val slot = placement.slot) {
                DockSlot.Drawer -> {
                    Icon(
                        painter = painterResource(R.drawable.drawer_icon),
                        contentDescription = stringResource(R.string.app_drawer),
                        tint = Color.White,
                        modifier = Modifier.size(iconSize),
                    )
                    if (showLabels) {
                        Label(stringResource(R.string.app_drawer), iconSize)
                    }
                }
                is DockSlot.App -> {
                    val customIcon = resolveCustomIcon(slot.item.componentName)
                        ?: slot.item.customIcon
                    CachedAppIcon(
                        componentName = slot.item.componentName,
                        customIcon = customIcon,
                        contentDescription = resolveLabel(slot.item),
                        size = iconSize,
                        loadBitmap = loadIconBitmap,
                        peekBitmap = peekIconBitmap,
                        iconPackPackage = iconPackPackage,
                    )
                    if (showLabels) {
                        Label(resolveLabel(slot.item), iconSize)
                    }
                }
                DockSlot.Empty -> Box(modifier = Modifier.size(iconSize))
            }
        }

        if (appSlot != null && menuFor == appSlot.item.componentName) {
            DropdownMenu(expanded = true, onDismissRequest = { onMenuForChange(null) }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_from_dock)) },
                    onClick = {
                        onMenuForChange(null)
                        onRemove(appSlot.item.componentName)
                    },
                )
            }
        }
    }
}

@Composable
private fun Label(text: String, iconSize: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = iconSize * 1.6f),
        textAlign = TextAlign.Center,
    )
}

private data class Placement(
    val slotIndex: Int,
    val angleDegrees: Float,
    val slot: DockSlot,
)

/** Fixed ring positions. When [includeDrawer] is true, drawer sits at bottom (90°). */
private fun computeFixedPlacements(
    dockItems: List<DockItem>,
    slotCount: Int,
    includeDrawer: Boolean,
): List<Placement> {
    if (!includeDrawer) {
        if (dockItems.isEmpty()) return emptyList()
        val n = dockItems.size
        val step = 360f / n
        return List(n) { index ->
            Placement(
                slotIndex = index,
                angleDegrees = 90f + index * step,
                slot = DockSlot.App(dockItems[index], index),
            )
        }
    }
    val count = slotCount.coerceAtLeast(1)
    val step = 360f / count
    return List(count) { index ->
        val angle = 90f + index * step
        val slot = when {
            index == 0 -> DockSlot.Drawer
            index - 1 < dockItems.size -> DockSlot.App(dockItems[index - 1], index - 1)
            else -> DockSlot.Empty
        }
        Placement(index, angle, slot)
    }
}

private fun nearestAppSlotIndex(
    angleDegrees: Float,
    placements: List<Placement>,
    appCount: Int,
): Int? {
    if (appCount <= 0) return null
    val best = placements
        .filter { it.slot is DockSlot.App || it.slot is DockSlot.Empty }
        .minByOrNull { angularDistance(it.angleDegrees, angleDegrees) }
        ?: return null
    return when (val slot = best.slot) {
        is DockSlot.App -> slot.appIndex
        is DockSlot.Empty -> {
            // Empty slots exist only when the drawer button is shown.
            val appSlotIndex = placements.indexOfFirst { it.slot is DockSlot.Empty }.let {
                (best.slotIndex - 1).coerceIn(0, appCount - 1)
            }
            appSlotIndex
        }
        else -> null
    }
}

private fun angleFromAtan(atanDegrees: Float): Float {
    var a = atanDegrees
    if (a < 0) a += 360f
    return a
}

private fun angularDistance(a: Float, b: Float): Float {
    val diff = ((a - b) % 360f + 540f) % 360f - 180f
    return kotlin.math.abs(diff)
}
