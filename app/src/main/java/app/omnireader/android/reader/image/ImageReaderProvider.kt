package app.omnireader.android.reader.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import app.omnireader.android.core.cache.SafFileCache
import app.omnireader.android.core.model.FileFormat
import app.omnireader.android.data.db.LibraryItemEntity
import app.omnireader.android.reader.PagedBitmapReaderSession
import app.omnireader.android.reader.ReaderProvider
import app.omnireader.android.reader.ReaderSession
import com.t8rin.tiff_coder.TiffCoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class ImageReaderProvider(
    private val context: Context,
    private val cache: SafFileCache,
) : ReaderProvider {
    private val imageFormats = setOf(
        FileFormat.JPG, FileFormat.JPEG, FileFormat.PNG, FileFormat.WEBP,
        FileFormat.AVIF, FileFormat.GIF, FileFormat.BMP, FileFormat.TIFF, FileFormat.TIF,
    )

    override val id = "images"
    override fun supports(format: FileFormat) = format in imageFormats

    override suspend fun open(item: LibraryItemEntity): ReaderSession = withContext(Dispatchers.IO) {
        if (item.format in setOf(FileFormat.TIFF, FileFormat.TIF)) {
            val staged = cache.stage(Uri.parse(item.uri), item.fileName, "${item.lastModified}:${item.fileSize}")
            val count = TiffCoder.pageCount(staged)
            if (count <= 0) error("TIFF повреждён или не содержит страниц")
            TiffSession(item, staged, count)
        } else {
            ImageSession(item, context, Uri.parse(item.uri))
        }
    }

    private class ImageSession(
        override val item: LibraryItemEntity,
        private val context: Context,
        private val uri: Uri,
    ) : PagedBitmapReaderSession {
        override val pageCount = 1
        override suspend fun render(index: Int, targetWidth: Int): Bitmap = withContext(Dispatchers.IO) {
            require(index == 0)
            decodePlatform(context, uri, targetWidth)
                ?: error("${item.format}: декодер изображения недоступен на этой версии Android или файл повреждён")
        }
        override fun close() = Unit
    }

    private class TiffSession(
        override val item: LibraryItemEntity,
        private val file: File,
        override val pageCount: Int,
    ) : PagedBitmapReaderSession {
        private val mutex = Mutex()
        override suspend fun render(index: Int, targetWidth: Int): Bitmap = withContext(Dispatchers.IO) {
            require(index in 0 until pageCount)
            mutex.withLock {
                val decoded = TiffCoder.decode(file, index) ?: error("TIFF: не удалось декодировать страницу ${index + 1}")
                scaleDown(decoded, targetWidth)
            }
        }
        override fun close() = Unit
    }

    companion object {
        private fun decodePlatform(context: Context, uri: Uri, targetWidth: Int): Bitmap? {
            return if (Build.VERSION.SDK_INT >= 28) {
                runCatching {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val width = info.size.width
                        if (targetWidth > 0 && width > targetWidth) {
                            val height = (info.size.height * (targetWidth.toFloat() / width)).toInt().coerceAtLeast(1)
                            decoder.setTargetSize(targetWidth, height)
                        }
                    }
                }.getOrNull()
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0) return null
                var sample = 1
                while (targetWidth > 0 && bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        }

        private fun scaleDown(bitmap: Bitmap, targetWidth: Int): Bitmap {
            if (targetWidth <= 0 || bitmap.width <= targetWidth) return bitmap
            val height = (bitmap.height * (targetWidth.toFloat() / bitmap.width)).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(bitmap, targetWidth, height, true).also { if (it !== bitmap) bitmap.recycle() }
        }
    }
}
