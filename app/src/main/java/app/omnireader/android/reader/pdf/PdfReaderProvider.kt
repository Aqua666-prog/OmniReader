package app.omnireader.android.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.omnireader.android.core.cache.SafFileCache
import app.omnireader.android.core.model.FileFormat
import app.omnireader.android.data.db.LibraryItemEntity
import app.omnireader.android.reader.PdfReaderSession
import app.omnireader.android.reader.ReaderProvider
import app.omnireader.android.reader.ReaderSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PdfReaderProvider(
    private val context: Context,
    private val cache: SafFileCache,
) : ReaderProvider {
    override val id = "pdf"
    override fun supports(format: FileFormat) = format == FileFormat.PDF

    override suspend fun open(item: LibraryItemEntity): ReaderSession = withContext(Dispatchers.IO) {
        val uri = Uri.parse(item.uri)
        val direct = context.contentResolver.openFileDescriptor(uri, "r")
        if (direct != null) {
            val renderer = try {
                PdfRenderer(direct)
            } catch (_: IllegalArgumentException) {
                // Some SAF providers expose non-seekable descriptors; stage only that file in cache.
                try { direct.close() } catch (_: Throwable) { Unit }
                null
            } catch (t: Throwable) {
                try { direct.close() } catch (_: Throwable) { Unit }
                throw t
            }
            if (renderer != null) return@withContext Session(item, renderer)
        }
        val staged = cache.stage(uri, item.fileName, "${item.lastModified}:${item.fileSize}")
        val pfd = ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY)
        Session(item, PdfRenderer(pfd))
    }

    private class Session(
        override val item: LibraryItemEntity,
        private val renderer: PdfRenderer,
    ) : PdfReaderSession {
        private val mutex = Mutex()
        override val pageCount: Int get() = renderer.pageCount
        override suspend fun render(index: Int, targetWidth: Int): Bitmap = withContext(Dispatchers.IO) {
            mutex.withLock {
                renderer.openPage(index).use { page ->
                    val width = targetWidth.coerceIn(320, 2200)
                    val height = (width.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
        override fun close() = renderer.close()
    }
}
