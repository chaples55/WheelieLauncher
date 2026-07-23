package com.acousticfish.wheelielauncher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Caches album art and pre-blurred wallpaper bitmaps keyed by stable art identity.
 */
class ArtworkCache {
    private val maxKb = ((Runtime.getRuntime().maxMemory() / 1024) / 10).toInt().coerceIn(2048, 12288)
    private val artCache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val blurCache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val mutex = Mutex()

    fun getArt(key: String): Bitmap? = synchronized(artCache) { artCache.get(key) }

    fun putArt(key: String, bitmap: Bitmap) {
        val copy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) ?: return
        synchronized(artCache) { artCache.put(key, copy) }
    }

    fun getBlurred(key: String): Bitmap? = synchronized(blurCache) { blurCache.get(key) }

    suspend fun getOrCreateBlurred(
        key: String,
        source: Bitmap,
        maxEdge: Int = 720,
    ): Bitmap = withContext(Dispatchers.Default) {
        getBlurred(key)?.let { return@withContext it }
        mutex.withLock {
            getBlurred(key)?.let { return@withLock it }
            val scaled = scaleDown(source, maxEdge)
            val blurred = softBlur(scaled)
            synchronized(blurCache) { blurCache.put(key, blurred) }
            blurred
        }
    }

    private fun scaleDown(source: Bitmap, maxEdge: Int): Bitmap {
        val w = source.width
        val h = source.height
        val edge = maxOf(w, h)
        if (edge <= maxEdge) return source
        val scale = maxEdge.toFloat() / edge
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, nw, nh, true)
    }

    /** Cheap soft blur via downscale/upscale — good enough for wallpaper and cheap on CPU. */
    private fun softBlur(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val smallW = (w / 6).coerceAtLeast(1)
        val smallH = (h / 6).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)
        val mid = Bitmap.createScaledBitmap(small, w, h, true)
        canvas.drawBitmap(mid, 0f, 0f, paint)
        canvas.drawColor(0x33000000)
        if (small !== bitmap) small.recycle()
        if (mid !== bitmap) mid.recycle()
        return output
    }
}
