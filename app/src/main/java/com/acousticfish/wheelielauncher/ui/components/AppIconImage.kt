package com.acousticfish.wheelielauncher.ui.components

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.acousticfish.wheelielauncher.data.IconBitmapCache

@Composable
fun CachedAppIcon(
    componentName: ComponentName,
    customIcon: String?,
    contentDescription: String?,
    size: Dp,
    loadBitmap: suspend (ComponentName, String?, Int) -> Bitmap?,
    peekBitmap: (ComponentName, String?, Int) -> Bitmap? = { _, _, _ -> null },
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx().coerceAtLeast(1) }
    val cacheKey = remember(componentName, customIcon, px) {
        IconBitmapCache.key(componentName, customIcon, px)
    }
    var image by remember(cacheKey) {
        mutableStateOf<ImageBitmap?>(
            peekBitmap(componentName, customIcon, px)?.asImageBitmap(),
        )
    }
    LaunchedEffect(cacheKey) {
        // Skip coroutine work when preload/peek already filled the cell.
        if (image != null) return@LaunchedEffect
        val bmp = loadBitmap(componentName, customIcon, px) ?: return@LaunchedEffect
        image = bmp.asImageBitmap()
    }
    if (image != null) {
        Image(
            bitmap = image!!,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        // Reserve layout space so the grid does not jump as icons resolve.
        androidx.compose.foundation.layout.Box(modifier = modifier.size(size))
    }
}

@Composable
fun AppIconImage(
    drawable: Drawable?,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx().coerceAtLeast(1) }
    val bitmap = remember(drawable, px) {
        drawable?.let {
            android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = android.graphics.Canvas(bmp)
                it.setBounds(0, 0, px, px)
                it.draw(canvas)
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun rememberResolvedIcon(
    key: Any?,
    resolver: suspend () -> Drawable?,
): Drawable? {
    var drawable by remember(key) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(key) {
        drawable = resolver()
    }
    return drawable
}
