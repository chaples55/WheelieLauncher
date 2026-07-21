package com.chaples55.wheelielauncher.ui.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun WallpaperBackground(
    artKey: String,
    isMediaArt: Boolean,
    artworkBitmapKey: String?,
    artworkBitmap: Bitmap?,
    blurredBitmap: Bitmap?,
    artworkUri: Uri?,
    defaultWallpaperUri: String?,
    ensureBlurred: suspend (String, Bitmap) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    var wallpaperBmp by remember(artKey) { mutableStateOf(blurredBitmap) }

    LaunchedEffect(artKey, artworkBitmap) {
        if (isMediaArt && artworkBitmapKey != null && artworkBitmap != null) {
            wallpaperBmp = blurredBitmap
                ?: ensureBlurred(artworkBitmapKey, artworkBitmap)
        } else {
            wallpaperBmp = null
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D0D0F))) {
        AnimatedContent(
            targetState = artKey,
            transitionSpec = {
                fadeIn(animationSpec = tween(350)) togetherWith
                    fadeOut(animationSpec = tween(350))
            },
            label = "wallpaper",
        ) { key ->
            when {
                key.startsWith("media:") && wallpaperBmp != null -> {
                    Image(
                        bitmap = wallpaperBmp!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                key.startsWith("media:") && artworkUri != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artworkUri)
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                key.startsWith("default:") && defaultWallpaperUri != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(Uri.parse(defaultWallpaperUri))
                            .crossfade(false)
                            .memoryCacheKey(defaultWallpaperUri)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF1A1A1E),
                                        Color(0xFF0D0D0F),
                                        Color(0xFF121218),
                                    ),
                                ),
                            ),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        )
    }
}

fun wallpaperArtKey(
    hasSession: Boolean,
    artworkBitmapKey: String?,
    artworkUri: Uri?,
    defaultWallpaperUri: String?,
): String = when {
    hasSession && !artworkBitmapKey.isNullOrBlank() -> "media:$artworkBitmapKey"
    hasSession && artworkUri != null -> "media:$artworkUri"
    !defaultWallpaperUri.isNullOrBlank() -> "default:$defaultWallpaperUri"
    else -> "builtin"
}
