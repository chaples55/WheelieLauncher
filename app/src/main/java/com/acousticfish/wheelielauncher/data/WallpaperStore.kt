package com.acousticfish.wheelielauncher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persists the user's fallback wallpaper in app-private storage.
 * Settings store an absolute filesystem path (not a content URI) so loads stay reliable.
 */
object WallpaperStore {
    private const val FILE_PREFIX = "user_wallpaper_"
    private const val FILE_SUFFIX = ".jpg"
    private const val MAX_EDGE = 2160

    fun resolveFile(storedPath: String?): File? {
        if (storedPath.isNullOrBlank()) return null
        val file = when {
            storedPath.startsWith("/") -> File(storedPath)
            storedPath.startsWith("file:") -> File(android.net.Uri.parse(storedPath).path ?: return null)
            else -> File(storedPath)
        }
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)) {
                file.delete()
            }
        }
    }

    /** Copy a picked image into app storage; returns absolute path. */
    suspend fun saveFromUri(context: Context, uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return@withContext null
            writeBitmap(context, decoded)
        }.getOrNull()
    }

    /**
     * Snapshot the current system wallpaper into app storage.
     * Retries — the system picker often returns before the new wallpaper is readable.
     * Returns absolute path.
     */
    suspend fun captureSystemWallpaper(context: Context): String? {
        var lastPath: String? = null
        repeat(6) { attempt ->
            if (attempt > 0) delay(300L * attempt)
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val wm = WallpaperManager.getInstance(context)
                    val bitmap = systemWallpaperBitmap(wm) ?: return@runCatching null
                    writeBitmap(context, bitmap)
                }.getOrNull()
            }
            if (path != null) {
                // Prefer a capture that differs from the previous attempt once the system updates.
                if (lastPath != null && !filesLikelySame(lastPath!!, path)) {
                    File(lastPath!!).delete()
                    return path
                }
                lastPath = path
            }
        }
        return lastPath
    }

    suspend fun decodeStored(storedPath: String?): Bitmap? = withContext(Dispatchers.IO) {
        val file = resolveFile(storedPath) ?: return@withContext null
        BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun writeBitmap(context: Context, bitmap: Bitmap): String {
        val scaled = scaleDown(bitmap, MAX_EDGE)
        val out = File(context.filesDir, "$FILE_PREFIX${System.currentTimeMillis()}$FILE_SUFFIX")
        out.outputStream().buffered().use { stream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        }
        if (scaled !== bitmap) scaled.recycle()
        pruneOldFiles(context, keep = out)
        return out.absolutePath
    }

    private fun filesLikelySame(a: String, b: String): Boolean {
        val fa = File(a)
        val fb = File(b)
        if (!fa.isFile || !fb.isFile) return false
        if (fa.length() != fb.length()) return false
        // Cheap content peek — enough to detect a wallpaper swap.
        return fa.inputStream().use { ia ->
            fb.inputStream().use { ib ->
                val ba = ByteArray(64)
                val bb = ByteArray(64)
                val na = ia.read(ba)
                val nb = ib.read(bb)
                na == nb && ba.contentEquals(bb)
            }
        }
    }

    private fun pruneOldFiles(context: Context, keep: File) {
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(FILE_PREFIX) &&
                file.name.endsWith(FILE_SUFFIX) &&
                file.absolutePath != keep.absolutePath
            ) {
                file.delete()
            }
        }
    }

    private fun systemWallpaperBitmap(wm: WallpaperManager): Bitmap? {
        // Prefer the wallpaper file — most accurate right after the system picker commits.
        if (Build.VERSION.SDK_INT >= 24) {
            runCatching {
                wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
                    BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                }
            }.getOrNull()?.let { return it }
        }

        val drawable = runCatching {
            if (Build.VERSION.SDK_INT >= 24) {
                wm.getDrawable(WallpaperManager.FLAG_SYSTEM)
            } else {
                null
            }
        }.getOrNull() ?: wm.drawable ?: return null

        if (drawable is BitmapDrawable) {
            val src = drawable.bitmap
            if (src != null && !src.isRecycled) {
                return src.copy(src.config ?: Bitmap.Config.ARGB_8888, false)
            }
        }
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        if (w <= 1 && h <= 1) return null
        val bmp = createBitmap(w, h)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }

    private fun scaleDown(source: Bitmap, maxEdge: Int): Bitmap {
        val edge = maxOf(source.width, source.height)
        if (edge <= maxEdge) return source
        val scale = maxEdge.toFloat() / edge
        val nw = (source.width * scale).toInt().coerceAtLeast(1)
        val nh = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, nw, nh, true)
    }
}
