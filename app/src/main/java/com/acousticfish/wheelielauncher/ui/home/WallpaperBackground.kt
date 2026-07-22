package com.acousticfish.wheelielauncher.ui.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

private sealed class WallpaperContent {
    data class BitmapLayer(val key: String, val bitmap: ImageBitmap) : WallpaperContent()
    data class UriLayer(val key: String, val uri: Uri) : WallpaperContent()
    data class Gradient(val key: String) : WallpaperContent()
}

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
    scrimAlpha: Float = 0.35f,
    modifier: Modifier = Modifier,
) {
    var resolvedBlur by remember { mutableStateOf(blurredBitmap) }
    LaunchedEffect(artKey, artworkBitmap) {
        if (isMediaArt && artworkBitmapKey != null && artworkBitmap != null) {
            resolvedBlur = blurredBitmap
                ?: ensureBlurred(artworkBitmapKey, artworkBitmap)
        } else {
            resolvedBlur = null
        }
    }

    val target: WallpaperContent = remember(artKey, resolvedBlur, artworkUri, defaultWallpaperUri) {
        when {
            artKey.startsWith("media:") && resolvedBlur != null ->
                WallpaperContent.BitmapLayer(artKey, resolvedBlur!!.asImageBitmap())
            artKey.startsWith("media:") && artworkUri != null ->
                WallpaperContent.UriLayer(artKey, artworkUri)
            artKey.startsWith("default:") && defaultWallpaperUri != null ->
                WallpaperContent.UriLayer(artKey, Uri.parse(defaultWallpaperUri))
            else -> WallpaperContent.Gradient(artKey)
        }
    }

    var displayed by remember { mutableStateOf(target) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target.key() == displayed.key()) {
            displayed = target
            fade.snapTo(1f)
            return@LaunchedEffect
        }
        fade.animateTo(0f, tween(180))
        displayed = target
        fade.snapTo(0f)
        fade.animateTo(1f, tween(220))
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D0D0F))) {
        Box(modifier = Modifier.fillMaxSize().alpha(fade.value)) {
            when (val content = displayed) {
                is WallpaperContent.BitmapLayer -> {
                    Image(
                        bitmap = content.bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is WallpaperContent.UriLayer -> {
                    key(content.key) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(content.uri)
                                .crossfade(false)
                                .memoryCacheKey(content.key)
                                .diskCacheKey(content.key)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                is WallpaperContent.Gradient -> {
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
                .background(Color.Black.copy(alpha = scrimAlpha.coerceIn(0f, 1f))),
        )
    }
}

private fun WallpaperContent.key(): String = when (this) {
    is WallpaperContent.BitmapLayer -> key
    is WallpaperContent.UriLayer -> key
    is WallpaperContent.Gradient -> key
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
