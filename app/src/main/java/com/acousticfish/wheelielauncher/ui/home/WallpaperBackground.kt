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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.acousticfish.wheelielauncher.data.WallpaperStore

private sealed class WallpaperContent {
    data class BitmapLayer(val key: String, val bitmap: ImageBitmap) : WallpaperContent()
    data class UriLayer(val key: String, val uri: Uri) : WallpaperContent()
    /** Transparent — system wallpaper shows through the window. */
    data class SystemPassThrough(val key: String) : WallpaperContent()
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
    var defaultBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(artKey, artworkBitmap, artworkBitmapKey, blurredBitmap, isMediaArt) {
        if (isMediaArt && artworkBitmapKey != null && artworkBitmap != null) {
            resolvedBlur = blurredBitmap
                ?: ensureBlurred(artworkBitmapKey, artworkBitmap)
        } else {
            resolvedBlur = null
        }
    }

    // Decode launcher-only custom wallpaper from disk (document-picker path).
    LaunchedEffect(defaultWallpaperUri) {
        defaultBitmap = WallpaperStore.decodeStored(defaultWallpaperUri)?.asImageBitmap()
    }

    val target: WallpaperContent = remember(
        artKey,
        resolvedBlur,
        artworkUri,
        defaultWallpaperUri,
        defaultBitmap,
    ) {
        when {
            artKey.startsWith("media:") && resolvedBlur != null ->
                WallpaperContent.BitmapLayer(artKey, resolvedBlur!!.asImageBitmap())
            artKey.startsWith("media:") && artworkUri != null ->
                WallpaperContent.UriLayer(artKey, artworkUri)
            artKey.startsWith("default:") && defaultBitmap != null ->
                WallpaperContent.BitmapLayer(artKey, defaultBitmap!!)
            artKey.startsWith("default:") && defaultWallpaperUri != null -> {
                // Path still resolving — keep transparent so we don't flash an opaque plate.
                WallpaperContent.SystemPassThrough(artKey)
            }
            else -> WallpaperContent.SystemPassThrough(artKey)
        }
    }

    var displayed by remember { mutableStateOf(target) }
    val fade = remember { Animatable(1f) }
    var uriLoadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        uriLoadFailed = false
        if (target.key() == displayed.key() &&
            target::class == displayed::class &&
            (target !is WallpaperContent.BitmapLayer || displayed !is WallpaperContent.BitmapLayer ||
                target.bitmap === (displayed as WallpaperContent.BitmapLayer).bitmap)
        ) {
            displayed = target
            fade.snapTo(1f)
            return@LaunchedEffect
        }
        fade.animateTo(0f, tween(160))
        displayed = target
        fade.snapTo(0.001f)
        fade.animateTo(1f, tween(220))
    }

    Box(modifier.fillMaxSize()) {
        // No opaque base layer: the system wallpaper is visible through the window.
        Box(Modifier.fillMaxSize().alpha(fade.value)) {
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
                    if (!uriLoadFailed) {
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
                                onState = { state ->
                                    if (state is AsyncImagePainter.State.Error) {
                                        uriLoadFailed = true
                                    }
                                },
                            )
                        }
                    }
                }
                is WallpaperContent.SystemPassThrough -> Unit
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha.coerceIn(0f, 1f))),
        ) {}
    }
}

private fun WallpaperContent.key(): String = when (this) {
    is WallpaperContent.BitmapLayer -> key
    is WallpaperContent.UriLayer -> key
    is WallpaperContent.SystemPassThrough -> key
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
    else -> "system"
}
