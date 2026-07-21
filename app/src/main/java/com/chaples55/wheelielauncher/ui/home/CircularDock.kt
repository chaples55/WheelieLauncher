package com.chaples55.wheelielauncher.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaples55.wheelielauncher.R
import com.chaples55.wheelielauncher.data.DockItem
import com.chaples55.wheelielauncher.ui.components.CachedAppIcon
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

@Composable
fun CircularDock(
    dockItems: List<DockItem>,
    slotCount: Int,
    selectedIndex: Int,
    iconSizeDp: Float,
    ringRadiusFraction: Float,
    showLabels: Boolean,
    loadIconBitmap: suspend (ComponentName, String?, Int) -> Bitmap?,
    peekIconBitmap: (ComponentName, String?, Int) -> Bitmap? = { _, _, _ -> null },
    resolveLabel: (DockItem) -> String,
    onSelect: (Int) -> Unit,
    onLaunch: (DockSlot) -> Unit,
    onRemove: (ComponentName) -> Unit,
    onReorder: (ComponentName, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val iconSize = iconSizeDp.dp
    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<ComponentName?>(null) }
    var draggingCn by remember { mutableStateOf<ComponentName?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }

    val placements = remember(dockItems, slotCount) {
        computeFixedPlacements(dockItems, slotCount)
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
            val appSlot = placement.slot as? DockSlot.App
            val isDraggingThis = appSlot != null && appSlot.item.componentName == draggingCn
            val x = if (isDraggingThis) dragX else homeX
            val y = if (isDraggingThis) dragY else homeY
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
                                        draggingCn = appSlot.item.componentName
                                        dragX = homeX
                                        dragY = homeY
                                        dragMoved = false
                                        menuFor = null
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragX += dragAmount.x
                                        dragY += dragAmount.y
                                        if (hypot((dragX - homeX).toDouble(), (dragY - homeY).toDouble()) > touchSlop) {
                                            dragMoved = true
                                        }
                                    },
                                    onDragEnd = {
                                        if (!dragMoved) {
                                            menuFor = appSlot.item.componentName
                                        } else {
                                            val angle = angleFromAtan(
                                                atan2(dragY - cy, dragX - cx) * (180f / PI.toFloat()),
                                            )
                                            val target = nearestAppSlotIndex(angle, placements, dockItems.size)
                                            if (target != null) {
                                                onReorder(appSlot.item.componentName, target)
                                            }
                                        }
                                        draggingCn = null
                                        dragMoved = false
                                    },
                                    onDragCancel = {
                                        draggingCn = null
                                        dragMoved = false
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
                                imageVector = Icons.Filled.Apps,
                                contentDescription = stringResource(R.string.app_drawer),
                                tint = Color.White,
                                modifier = Modifier.size(iconSize),
                            )
                            if (showLabels) {
                                Label(stringResource(R.string.app_drawer), iconSize)
                            }
                        }
                        is DockSlot.App -> {
                        CachedAppIcon(
                            componentName = slot.item.componentName,
                            customIcon = slot.item.customIcon,
                            contentDescription = resolveLabel(slot.item),
                            size = iconSize,
                            loadBitmap = loadIconBitmap,
                            peekBitmap = peekIconBitmap,
                        )
                            if (showLabels) {
                                Label(resolveLabel(slot.item), iconSize)
                            }
                        }
                        DockSlot.Empty -> Box(modifier = Modifier.size(iconSize))
                    }
                }

                if (appSlot != null && menuFor == appSlot.item.componentName) {
                    DropdownMenu(expanded = true, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remove_from_dock)) },
                            onClick = {
                                menuFor = null
                                onRemove(appSlot.item.componentName)
                            },
                        )
                    }
                }
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

/** Fixed ring positions. Drawer always at bottom (90°). Icons do not rotate. */
private fun computeFixedPlacements(dockItems: List<DockItem>, slotCount: Int): List<Placement> {
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
        is DockSlot.Empty -> (best.slotIndex - 1).coerceIn(0, appCount - 1)
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
