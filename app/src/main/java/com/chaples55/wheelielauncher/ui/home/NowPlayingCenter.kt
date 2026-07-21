package com.chaples55.wheelielauncher.ui.home

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chaples55.wheelielauncher.data.NowPlayingMeta
import com.chaples55.wheelielauncher.data.PlaybackProgress
import kotlinx.coroutines.flow.StateFlow

@Composable
fun NowPlayingCenter(
    meta: NowPlayingMeta,
    progress: StateFlow<PlaybackProgress>,
    artworkBitmap: Bitmap?,
    diameter: Dp,
    onOpenApp: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artKey = meta.artworkBitmapKey ?: meta.artworkUri?.toString() ?: "empty"
    val targetImage = remember(artKey, artworkBitmap) { artworkBitmap?.asImageBitmap() }
    var displayedImage by remember { mutableStateOf<ImageBitmap?>(targetImage) }
    var displayedKey by remember { mutableStateOf(artKey) }
    val fade = remember { Animatable(1f) }

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

            ProgressArc(progress = progress)
        }

        Box(
            modifier = Modifier
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
    }
}

@Composable
private fun ProgressArc(progress: StateFlow<PlaybackProgress>) {
    val playback by progress.collectAsStateWithLifecycle()
    val fraction = if (playback.durationMs > 0) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
        val stroke = 4.dp.toPx()
        val diameterPx = size.minDimension
        val topLeft = Offset((size.width - diameterPx) / 2f, (size.height - diameterPx) / 2f)
        val arcSize = Size(diameterPx, diameterPx)
        drawArc(
            color = Color.White.copy(alpha = 0.25f),
            startAngle = 30f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = Color.White.copy(alpha = 0.9f),
            startAngle = 30f,
            sweepAngle = 120f * fraction,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
