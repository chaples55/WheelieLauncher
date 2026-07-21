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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Wallpaper keyed only by art identity — progress ticks must not change [artKey].
 */
@Composable
fun WallpaperBackground(
    artKey: String,
    isMediaArt: Boolean,
    artworkBitmap: Bitmap?,
    artworkUri: Uri?,
    defaultWallpaperUri: String?,
    modifier: Modifier = Modifier,
) {
    val stableBitmap = remember(artKey) { artworkBitmap }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D0D0F))) {
        AnimatedContent(
            targetState = artKey,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith
                    fadeOut(animationSpec = tween(400))
            },
            label = "wallpaper",
        ) { key ->
            WallpaperLayer(
                key = key,
                isMediaArt = isMediaArt && key.startsWith("media:"),
                bitmap = stableBitmap.takeIf { isMediaArt && key.startsWith("media:") },
                uri = when {
                    key.startsWith("media:") -> artworkUri
                    key.startsWith("default:") -> defaultWallpaperUri?.let(Uri::parse)
                    else -> null
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun WallpaperLayer(
    key: String,
    isMediaArt: Boolean,
    bitmap: Bitmap?,
    uri: Uri?,
) {
    val context = LocalContext.current
    when {
        isMediaArt && bitmap != null -> {
            // Soft blur; avoid recomposing this layer unless [key] changes.
            Image(
                bitmap = remember(key) { bitmap.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(28.dp),
            )
        }
        uri != null -> {
            AsyncImage(
                model = remember(key) {
                    ImageRequest.Builder(context)
                        .data(uri)
                        .crossfade(false)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isMediaArt) Modifier.blur(28.dp) else Modifier),
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
