package com.chaples55.wheelielauncher.ui.home

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chaples55.wheelielauncher.data.NowPlayingState

@Composable
fun NowPlayingCenter(
    nowPlaying: NowPlayingState,
    artworkBitmap: Bitmap?,
    diameter: Dp,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (nowPlaying.durationMs > 0) {
        (nowPlaying.positionMs.toFloat() / nowPlaying.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val artKey = nowPlaying.artworkBitmapKey ?: nowPlaying.artworkUri?.toString() ?: "empty"

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(0xFF1C1C22)),
        ) {
            AnimatedContent(
                targetState = artKey,
                transitionSpec = {
                    fadeIn(androidx.compose.animation.core.tween(350)) togetherWith
                        fadeOut(androidx.compose.animation.core.tween(350))
                },
                label = "centerArt",
            ) { key ->
                when {
                    artworkBitmap != null && key == artKey -> {
                        Image(
                            bitmap = artworkBitmap.asImageBitmap(),
                            contentDescription = nowPlaying.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    nowPlaying.artworkUri != null -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(nowPlaying.artworkUri)
                                .build(),
                            contentDescription = nowPlaying.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A32)))
                    }
                }
            }

            // Progress along bottom edge of album art
            Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                val stroke = 4.dp.toPx()
                val diameterPx = size.minDimension
                val topLeft = Offset((size.width - diameterPx) / 2f, (size.height - diameterPx) / 2f)
                val arcSize = Size(diameterPx, diameterPx)
                // Bottom arc baseline
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
                    sweepAngle = 120f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
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
                imageVector = if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(diameter * 0.16f),
            )
        }
    }
}
