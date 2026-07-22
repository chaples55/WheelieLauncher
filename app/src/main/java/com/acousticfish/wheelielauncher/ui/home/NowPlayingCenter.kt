package com.acousticfish.wheelielauncher.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.acousticfish.wheelielauncher.data.NowPlayingMeta
import com.acousticfish.wheelielauncher.data.PlaybackProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

@Composable
fun NowPlayingCenter(
    meta: NowPlayingMeta,
    progress: StateFlow<PlaybackProgress>,
    artworkBitmap: Bitmap?,
    diameter: Dp,
    progressStrokeDp: Float,
    showBatteryBar: Boolean,
    showTrackInfo: Boolean,
    onOpenApp: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artKey = meta.artworkBitmapKey ?: meta.artworkUri?.toString() ?: "empty"
    val targetImage = remember(artKey, artworkBitmap) { artworkBitmap?.asImageBitmap() }
    var displayedImage by remember { mutableStateOf<ImageBitmap?>(targetImage) }
    var displayedKey by remember { mutableStateOf(artKey) }
    val fade = remember { Animatable(1f) }
    val batteryFraction = rememberBatteryFraction()
    val strokeDp = progressStrokeDp.dp.coerceIn(2.dp, 14.dp)
    val labelMaxWidth = diameter * 0.82f
    val labelSize = (diameter.value * 0.09f).coerceIn(10f, 16f).sp

    LaunchedEffect(artKey, targetImage) {
        if (artKey == displayedKey && targetImage == displayedImage) {
            fade.snapTo(1f)
            return@LaunchedEffect
        }
        fade.animateTo(0f, tween(150))
        displayedImage = targetImage
        displayedKey = artKey
        fade.snapTo(0f)
        fade.animateTo(1f, tween(200))
    }

    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .clickable(onClick = onOpenApp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C22)),
        ) {
            Box(modifier = Modifier.fillMaxSize().alpha(fade.value)) {
                when {
                    displayedImage != null -> {
                        Image(
                            bitmap = displayedImage!!,
                            contentDescription = meta.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    meta.artworkUri != null -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(meta.artworkUri)
                                .memoryCacheKey(displayedKey)
                                .build(),
                            contentDescription = meta.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A32)))
                    }
                }
            }

            if (showBatteryBar) {
                RingArc(
                    fraction = batteryFraction,
                    startAngle = 210f,
                    sweepAngle = 120f,
                    stroke = strokeDp,
                    fillColor = batteryFillColor(batteryFraction),
                )
            }
            ProgressArc(progress = progress, stroke = strokeDp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = diameter * 0.08f, vertical = diameter * 0.12f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showTrackInfo) {
                MarqueeLabel(
                    text = meta.artist?.takeIf { it.isNotBlank() } ?: " ",
                    maxWidth = labelMaxWidth,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Medium,
                )
            }

            Box(
                modifier = Modifier
                    .padding(vertical = diameter * 0.04f)
                    .size(diameter * 0.28f)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (meta.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (meta.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(diameter * 0.16f),
                )
            }

            if (showTrackInfo) {
                MarqueeLabel(
                    text = meta.title?.takeIf { it.isNotBlank() } ?: " ",
                    maxWidth = labelMaxWidth,
                    fontSize = labelSize,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ProgressArc(
    progress: StateFlow<PlaybackProgress>,
    stroke: Dp,
) {
    val playback by progress.collectAsStateWithLifecycle()
    val fraction = if (playback.durationMs > 0) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    RingArc(
        fraction = fraction,
        startAngle = 30f,
        sweepAngle = 120f,
        stroke = stroke,
        fillColor = Color.White.copy(alpha = 0.95f),
    )
}

@Composable
private fun RingArc(
    fraction: Float,
    startAngle: Float,
    sweepAngle: Float,
    stroke: Dp,
    fillColor: Color,
) {
    val track = Color.White.copy(alpha = 0.25f)
    Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
        val strokePx = stroke.toPx()
        val diameterPx = size.minDimension
        val topLeft = Offset((size.width - diameterPx) / 2f, (size.height - diameterPx) / 2f)
        val arcSize = Size(diameterPx, diameterPx)
        drawArc(
            color = Color.Black.copy(alpha = 0.45f),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft + Offset(0f, strokePx * 0.35f),
            size = arcSize,
            style = Stroke(width = strokePx * 1.35f, cap = StrokeCap.Round),
        )
        drawArc(
            color = track,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
        drawArc(
            color = fillColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle * fraction.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun MarqueeLabel(
    text: String,
    maxWidth: Dp,
    fontSize: TextUnit,
    fontWeight: FontWeight,
) {
    val density = LocalDensity.current
    var textWidthPx by remember(text, fontSize) { mutableFloatStateOf(0f) }
    val maxWidthPx = with(density) { maxWidth.toPx() }
    val needsScroll = textWidthPx > maxWidthPx + 1f
    val offset = remember { Animatable(0f) }

    LaunchedEffect(text, needsScroll, textWidthPx, maxWidthPx) {
        offset.snapTo(0f)
        if (!needsScroll) return@LaunchedEffect
        val travel = textWidthPx - maxWidthPx
        while (true) {
            delay(900)
            offset.animateTo(
                -travel,
                animationSpec = tween(
                    durationMillis = max(2_400, (travel * 12f).toInt()),
                    easing = LinearEasing,
                ),
            )
            delay(900)
            offset.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Center,
            onTextLayout = { textWidthPx = it.size.width.toFloat() },
            modifier = Modifier.graphicsLayer { translationX = offset.value },
            style = TextStyle(
                color = Color.White,
                fontSize = fontSize,
                fontWeight = fontWeight,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.7f),
                    blurRadius = 6f,
                ),
            ),
        )
    }
}

@Composable
private fun rememberBatteryFraction(): Float {
    val context = LocalContext.current
    var fraction by remember { mutableFloatStateOf(readBatteryFraction(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                fraction = readBatteryFraction(intent ?: return)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = context.registerReceiver(receiver, filter)
        if (sticky != null) fraction = readBatteryFraction(sticky)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return fraction
}

private fun readBatteryFraction(context: Context): Float {
    val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return readBatteryFraction(sticky)
}

private fun readBatteryFraction(intent: Intent?): Float {
    if (intent == null) return 0f
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    if (level < 0) return 0f
    return (level.toFloat() / scale.toFloat()).coerceIn(0f, 1f)
}

private fun batteryFillColor(fraction: Float): Color = when {
    fraction <= 0.15f -> Color(0xFFFF5252)
    fraction <= 0.30f -> Color(0xFFFFC107)
    else -> Color.White.copy(alpha = 0.95f)
}
