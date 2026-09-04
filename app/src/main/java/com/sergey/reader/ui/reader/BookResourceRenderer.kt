package com.sergey.reader.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.github.axet.djvulibre.DjvuLibre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

object BookResourceRenderer {
    private val bitmapCache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    suspend fun loadImage(path: String): Bitmap? = withContext(Dispatchers.IO) {
        val key = "img:$path"
        synchronized(bitmapCache) { bitmapCache.get(key) }?.let { return@withContext it }
        val bitmap = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return@withContext null
        synchronized(bitmapCache) { bitmapCache.put(key, bitmap) }
        bitmap
    }

    suspend fun renderPdfPage(context: Context, uri: Uri, pageIndex: Int, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val width = targetWidthPx.coerceIn(320, 2200)
        val key = "pdf:${uri}:$pageIndex:$width"
        synchronized(bitmapCache) { bitmapCache.get(key) }?.let { return@withContext it }

        val bitmap = runCatching {
            val pfd = if (uri.scheme == "file") {
                uri.path?.let { ParcelFileDescriptor.open(File(it), ParcelFileDescriptor.MODE_READ_ONLY) }
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")
            } ?: return@runCatching null
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@runCatching null
                    renderer.openPage(pageIndex).use { page ->
                        val height = (width * page.height.toFloat() / page.width.toFloat()).roundToInt().coerceAtLeast(1)
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { out ->
                            out.eraseColor(android.graphics.Color.WHITE)
                            page.render(out, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }.getOrNull() ?: return@withContext null

        synchronized(bitmapCache) { bitmapCache.put(key, bitmap) }
        bitmap
    }

    suspend fun renderDjvuPage(context: Context, uri: Uri, pageIndex: Int, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val width = targetWidthPx.coerceIn(320, 2200)
        val key = "djvu:${uri}:$pageIndex:$width"
        synchronized(bitmapCache) { bitmapCache.get(key) }?.let { return@withContext it }

        val bitmap = runCatching {
            val pfd = if (uri.scheme == "file") {
                uri.path?.let { ParcelFileDescriptor.open(File(it), ParcelFileDescriptor.MODE_READ_ONLY) }
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")
            } ?: return@runCatching null
            pfd.use { descriptor ->
                val doc = DjvuLibre(descriptor.fileDescriptor)
                try {
                    val pageCount = doc.pagesCount
                    if (pageIndex !in 0 until pageCount) return@runCatching null
                    val info = doc.getPageInfo(pageIndex)
                    if (info.width <= 0 || info.height <= 0) return@runCatching null
                    val height = (width * info.height.toFloat() / info.width.toFloat())
                        .roundToInt().coerceIn(1, 6000)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { out ->
                        out.eraseColor(android.graphics.Color.WHITE)
                        doc.renderPage(
                            out,
                            pageIndex,
                            0, 0, info.width, info.height,
                            0, 0, width, height
                        )
                    }
                } finally {
                    runCatching { doc.close() }
                }
            }
        }.getOrNull() ?: return@withContext null

        synchronized(bitmapCache) { bitmapCache.put(key, bitmap) }
        bitmap
    }

    fun fileExists(path: String?): Boolean = !path.isNullOrBlank() && File(path).isFile
}
