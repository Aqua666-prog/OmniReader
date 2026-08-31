package app.omnireader.android.reader.djvu

import android.graphics.Bitmap
import android.net.Uri
import app.omnireader.android.core.cache.SafFileCache
import app.omnireader.android.core.model.FileFormat
import app.omnireader.android.data.db.LibraryItemEntity
import app.omnireader.android.reader.PagedBitmapReaderSession
import app.omnireader.android.reader.ReaderProvider
import app.omnireader.android.reader.ReaderSession
import com.t8rin.djvu_coder.DJVUDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

class DjvuReaderProvider(private val cache: SafFileCache) : ReaderProvider {
    override val id = "djvu"
    override fun supports(format: FileFormat) = format in setOf(FileFormat.DJVU, FileFormat.DJV)

    override suspend fun open(item: LibraryItemEntity): ReaderSession = withContext(Dispatchers.IO) {
        val staged = cache.stage(Uri.parse(item.uri), item.fileName, "${item.lastModified}:${item.fileSize}")
        DjvuSession(item, staged)
    }

    private class DjvuSession(
        override val item: LibraryItemEntity,
        file: File,
    ) : PagedBitmapReaderSession {
        private val decoder = DJVUDecoder(file)
        private val mutex = Mutex()
        override val pageCount: Int = DjvuSupport.countPages(file, decoder)

        override suspend fun render(index: Int, targetWidth: Int): Bitmap = withContext(Dispatchers.IO) {
            require(index in 0 until pageCount) { "DJVU: некорректный номер страницы" }
            mutex.withLock {
                val decoded = decoder.decode(index, 180) ?: error("DJVU: не удалось декодировать страницу ${index + 1}")
                if (targetWidth <= 0 || decoded.width <= targetWidth) return@withLock decoded
                val height = (decoded.height * (targetWidth.toFloat() / decoded.width)).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetWidth, height, true).also { if (it !== decoded) decoded.recycle() }
            }
        }

        override fun close() = Unit
    }
}

object DjvuSupport {
    fun countPages(file: File, decoder: DJVUDecoder? = null): Int {
        val structural = runCatching { countForms(file) }.getOrDefault(0)
        if (structural > 0) return structural
        val d = decoder ?: DJVUDecoder(file)
        return probeCount(d)
    }

    private fun probeCount(decoder: DJVUDecoder): Int {
        fun exists(page: Int): Boolean = runCatching {
            decoder.decode(page, 1200)?.let { bitmap -> bitmap.recycle(); true } ?: false
        }.getOrDefault(false)
        if (!exists(0)) return 0

        var lastValid = 0
        var firstInvalid = 1
        while (firstInvalid < 16384 && exists(firstInvalid)) {
            lastValid = firstInvalid
            firstInvalid = (firstInvalid * 2).coerceAtMost(16384)
            if (firstInvalid == lastValid) break
        }
        var lo = lastValid + 1
        var hi = firstInvalid - 1
        var maxValid = lastValid
        while (lo <= hi) {
            val mid = lo + (hi - lo) / 2
            if (exists(mid)) { maxValid = mid; lo = mid + 1 } else hi = mid - 1
        }
        return maxValid + 1
    }

    private fun countForms(file: File): Int = RandomAccessFile(file, "r").use { raf ->
        var start = 0L
        if (raf.length() >= 4) {
            raf.seek(0)
            if (readAscii(raf, 4) == "AT&T") start = 4
        }
        countChunk(raf, start, raf.length(), 0)
    }

    private fun countChunk(raf: RandomAccessFile, position: Long, hardEnd: Long, depth: Int): Int {
        if (depth > 32 || position < 0 || position + 8 > hardEnd) return 0
        raf.seek(position)
        val id = readAscii(raf, 4)
        val size = raf.readInt().toLong() and 0xffff_ffffL
        val dataStart = position + 8
        val dataEnd = (dataStart + size).coerceAtMost(hardEnd)
        if (dataEnd < dataStart || id != "FORM" || dataStart + 4 > dataEnd) return 0
        raf.seek(dataStart)
        val type = readAscii(raf, 4)
        if (type == "DJVU") return 1

        var count = 0
        var child = dataStart + 4
        while (child + 8 <= dataEnd) {
            raf.seek(child)
            val childId = readAscii(raf, 4)
            val childSize = raf.readInt().toLong() and 0xffff_ffffL
            if (childSize > dataEnd - child - 8) break
            if (childId == "FORM") count += countChunk(raf, child, dataEnd, depth + 1)
            child += 8 + childSize + (childSize and 1L)
        }
        return count
    }

    private fun readAscii(raf: RandomAccessFile, count: Int): String {
        val bytes = ByteArray(count)
        raf.readFully(bytes)
        return bytes.toString(Charsets.US_ASCII)
    }
}
