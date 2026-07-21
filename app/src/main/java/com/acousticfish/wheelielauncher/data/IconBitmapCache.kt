package com.acousticfish.wheelielauncher.data

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Process-wide icon bitmap cache so the drawer / dock do not re-decode icons on every open.
 * Stores software ARGB bitmaps for reliable ImageView / Compose binding.
 */
class IconBitmapCache {
    private val maxKb = ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt().coerceIn(2048, 16384)
    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val mutex = Mutex()

    fun get(key: String): Bitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, bitmap: Bitmap) {
        synchronized(cache) { cache.put(key, bitmap) }
    }

    suspend fun getOrLoad(
        key: String,
        sizePx: Int,
        loader: suspend () -> Drawable?,
    ): Bitmap? = withContext(Dispatchers.Default) {
        get(key)?.let { return@withContext it }
        mutex.withLock {
            get(key)?.let { return@withLock it }
            val drawable = loader() ?: return@withLock null
            val bmp = drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            // Tip the GPU without converting to HARDWARE (safer for ImageView recycling).
            bmp.prepareToDraw()
            put(key, bmp)
            bmp
        }
    }

    suspend fun preload(
        keysAndLoaders: List<Pair<String, suspend () -> Drawable?>>,
        sizePx: Int,
    ) = withContext(Dispatchers.Default) {
        for ((key, loader) in keysAndLoaders) {
            if (get(key) == null) {
                runCatching { getOrLoad(key, sizePx, loader) }
            }
        }
    }

    companion object {
        fun key(componentName: android.content.ComponentName, customIcon: String?, sizePx: Int): String =
            "${componentName.flattenToString()}|${customIcon.orEmpty()}|$sizePx"
    }
}
