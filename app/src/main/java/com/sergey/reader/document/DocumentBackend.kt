package com.sergey.reader.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.math.sqrt

/** Synchronous native API. Only [DocumentSession] may invoke it, off Main under its lock. */
interface DocumentBackend : Closeable {
    val sizes: List<DSize>
    fun render(tile: TileKey): Bitmap
    fun text(page: Int): DocumentTextPage
}

internal fun openDescriptor(context: Context, uri: Uri): ParcelFileDescriptor =
    if (uri.scheme == "file") {
        ParcelFileDescriptor.open(File(requireNotNull(uri.path)), ParcelFileDescriptor.MODE_READ_ONLY)
    } else {
        context.contentResolver.openFileDescriptor(uri, "r") ?: error("Нет доступа к документу")
    }

class DocumentSession internal constructor(
    private val backend: DocumentBackend,
    private val ocr: OcrEngine,
    private val store: DocumentTextStore,
) {
    val sizes: List<DSize> = backend.sizes

    /** Serializes native backend calls (PDFium/DjVu handles are not used concurrently). */
    private val backendMutex = Mutex()
    /** Prevents duplicate OCR for the same/adjacent concurrent text requests and coordinates close. */
    private val textMutex = Mutex()
    private var closed = false
    private val textCache = object : LinkedHashMap<Int, DocumentTextPage>(8, .75f, true) {}

    private suspend fun <T> use(block: (DocumentBackend) -> T): T = withContext(Dispatchers.IO) {
        backendMutex.withLock {
            ensureActive()
            check(!closed) { "Документ закрыт" }
            block(backend)
        }
    }

    suspend fun render(tile: TileKey): Bitmap {
        var owned: Bitmap? = null
        try {
            use { owned = it.render(tile) }
            currentCoroutineContext().ensureActive()
            return requireNotNull(owned).also { owned = null }
        } finally {
            owned?.recycle()
        }
    }

    /**
     * Native text is always preferred. OCR is used only for pages whose native text layer is blank;
     * recognized empty pages are cached as such so they are not repeatedly OCRed.
     */
    suspend fun text(page: Int): DocumentTextPage = textMutex.withLock {
        require(page in sizes.indices) { "Некорректный номер страницы" }

        check(!closed) { "Документ закрыт" }
        textCache[page]?.let { return@withLock it }

        val native = use { source -> source.text(page) }
        val result = if (native.text.isNotBlank() || native.recognized) {
            native
        } else {
            withContext(Dispatchers.IO) { store.read(page) } ?: recognize(page)
        }

        // Pre-segment on a worker, without holding the native backend lock and blocking rendering.
        withContext(Dispatchers.Default) { result.words }
        check(!closed) { "Документ закрыт" }
        textCache[page] = result
        trimTextCache()
        result
    }

    private fun trimTextCache() {
        while (
            textCache.size > 6 ||
            textCache.values.sumOf { it.text.length.toLong() * 2L + it.glyphs.size * 64L } > 4_000_000L
        ) {
            val first = textCache.keys.firstOrNull() ?: break
            textCache.remove(first)
        }
    }

    private suspend fun recognize(page: Int): DocumentTextPage {
        val size = sizes[page]
        val area = (size.width * size.height).coerceAtLeast(1.0)
        val scale = minOf(
            2400.0 / maxOf(size.width, size.height).coerceAtLeast(1.0),
            sqrt(3_000_000.0 / area),
        ).coerceAtLeast(0.01)
        val width = (size.width * scale).toInt().coerceAtLeast(1)
        val height = (size.height * scale).toInt().coerceAtLeast(1)

        var ownedImage: Bitmap? = null
        try {
            try {
                withContext(Dispatchers.Default) {
                    val created = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // Publish ownership before the cancellable dispatcher hop resumes the caller.
                    ownedImage = created
                }
            } catch (error: OutOfMemoryError) {
                throw IllegalStateException("Недостаточно памяти для OCR этой страницы.", error)
            }
            val image = requireNotNull(ownedImage)
            val canvas = withContext(Dispatchers.Default) {
                image.eraseColor(android.graphics.Color.WHITE)
                Canvas(image)
            }

            for (y in 0 until height step OCR_TILE) {
                for (x in 0 until width step OCR_TILE) {
                    currentCoroutineContext().ensureActive()
                    val bitmap = render(
                        TileKey(
                            page = page,
                            fullWidth = width,
                            fullHeight = height,
                            x = x,
                            y = y,
                            width = minOf(OCR_TILE, width - x),
                            height = minOf(OCR_TILE, height - y),
                        )
                    )
                    try {
                        withContext(Dispatchers.Default) {
                            canvas.drawBitmap(bitmap, x.toFloat(), y.toFloat(), null)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }

            val result = try {
                ocr.recognize(page, size, image)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: OutOfMemoryError) {
                throw IllegalStateException("Недостаточно памяти для OCR этой страницы.", error)
            } catch (error: LinkageError) {
                throw IllegalStateException("Не удалось загрузить OCR-движок в этой сборке приложения.", error)
            }
            currentCoroutineContext().ensureActive()
            withContext(Dispatchers.IO) {
                try {
                    store.write(result)
                } catch (_: IOException) {
                    // Cache is optional; recognition remains usable for the current session.
                }
            }
            return result
        } finally {
            ownedImage?.recycle()
        }
    }

    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        // Wait for/cancelled text work to relinquish OCR bitmaps before closing the backend.
        textMutex.withLock {
            backendMutex.withLock {
                if (!closed) {
                    closed = true
                    textCache.clear()
                    backend.close()
                }
            }
        }
    }

    companion object {
        private const val OCR_TILE = 512

        suspend fun open(context: Context, uri: Uri, format: String): DocumentSession {
            var owned: DocumentBackend? = null
            try {
                withContext(Dispatchers.IO) {
                    owned = if (format.equals("PDF", true)) {
                        PdfDocumentBackend(context, uri)
                    } else {
                        DjvuDocumentBackend(context, uri)
                    }
                    require(owned!!.sizes.isNotEmpty()) { "В документе нет страниц" }
                }
                currentCoroutineContext().ensureActive()
                return DocumentSession(
                    backend = requireNotNull(owned),
                    ocr = TesseractOcrEngine(context),
                    store = DocumentTextStore(context, uri),
                ).also { owned = null }
            } finally {
                owned?.let { backend ->
                    withContext(NonCancellable + Dispatchers.IO) { backend.close() }
                }
            }
        }
    }
}
