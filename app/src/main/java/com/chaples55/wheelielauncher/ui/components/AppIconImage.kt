package com.chaples55.wheelielauncher.ui.components

import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap

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
        drawable?.toBitmap(px, px)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
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

fun Drawable.asBitmapDrawable(): BitmapDrawable? =
    this as? BitmapDrawable
