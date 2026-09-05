package com.sergey.reader.document

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * Offline Tesseract OCR. A single initialized native API is reused process-wide so repeated page
 * searches do not reload five language models for every page. The instance is recycled after a
 * short idle timeout or on memory pressure.
 */
class TesseractOcrEngine(context: Context) : OcrEngine {
    private val appContext = context.applicationContext

    override suspend fun recognize(pageIndex: Int, pageSize: DSize, image: Bitmap): DocumentTextPage =
        gate.withLock {
            withContext(Dispatchers.IO) {
                ensureActive()
                val base = ensureModels(appContext)
                cleanup?.cancel()
                activeContext = currentCoroutineContext()
                val api = shared ?: createApi(base).also { shared = it }
                try {
                    api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                    api.setImage(image)
                    // Unlike getUTF8Text(), hOCR recognition is interruptible by TessBaseAPI.stop().
                    api.getHOCRText(pageIndex)
                    ensureActive()

                    val result = api.resultIterator
                        ?: return@withContext DocumentTextPage(pageIndex, "", emptyList(), recognized = true)
                    try {
                        val text = StringBuilder()
                        val glyphs = mutableListOf<DocumentGlyph>()
                        val level = TessBaseAPI.PageIteratorLevel.RIL_WORD
                        result.begin()
                        var firstWord = true
                        do {
                            ensureActive()
                            val value = result.getUTF8Text(level).orEmpty().trimEnd()
                            if (value.isNotEmpty()) {
                                if (!firstWord) {
                                    text.append(
                                        if (result.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)) '\n'
                                        else ' '
                                    )
                                }
                                val start = text.length
                                text.append(value)
                                val box = result.getBoundingRect(level)
                                if (box != null && image.width > 0 && image.height > 0) {
                                    val left = box.left.toDouble() / image.width * pageSize.width
                                    val top = box.top.toDouble() / image.height * pageSize.height
                                    val right = box.right.toDouble() / image.width * pageSize.width
                                    val bottom = box.bottom.toDouble() / image.height * pageSize.height
                                    if (right > left && bottom > top) {
                                        glyphs += DocumentGlyph(start, text.length, DRect(left, top, right, bottom))
                                    }
                                }
                                firstWord = false
                            }
                        } while (result.next(level))
                        DocumentTextPage(pageIndex, text.toString(), glyphs, recognized = true)
                    } finally {
                        result.delete()
                    }
                } finally {
                    val cancelled = activeContext?.isActive == false
                    if (cancelled) {
                        runCatching { api.recycle() }
                        if (shared === api) shared = null
                    } else {
                        // Free image/page results but keep initialized language models hot.
                        runCatching { api.clear() }
                    }
                    activeContext = null
                    cleanup = cleanupScope.launch {
                        delay(IDLE_RECYCLE_MS)
                        gate.withLock {
                            shared?.let { runCatching { it.recycle() } }
                            shared = null
                        }
                    }
                }
            }
        }

    private fun createApi(base: File): TessBaseAPI {
        val candidate = TessBaseAPI { _ ->
            if (activeContext?.isActive == false) shared?.stop()
        }
        try {
            check(candidate.init(base.absolutePath, languages.joinToString("+"), TessBaseAPI.OEM_LSTM_ONLY)) {
                "Не удалось загрузить модели OCR"
            }
            return candidate
        } catch (error: Throwable) {
            runCatching { candidate.recycle() }
            throw error
        }
    }

    private fun ensureModels(context: Context): File {
        val base = File(context.filesDir, "ocr-fast-v1")
        val directory = File(base, "tessdata").apply { mkdirs() }
        for (language in languages) {
            val file = File(directory, "$language.traineddata")
            if (file.isFile && file.length() > 0L) continue
            val temp = File(directory, "$language.part")
            try {
                context.assets.open("tessdata/$language.traineddata").use { input ->
                    temp.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                if (file.exists() && !file.delete()) error("Не удалось обновить язык OCR")
                check(temp.renameTo(file)) { "Не удалось установить язык OCR" }
            } finally {
                temp.delete()
            }
        }
        return base
    }

    companion object {
        private const val IDLE_RECYCLE_MS = 30_000L
        private val gate = Mutex()
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var shared: TessBaseAPI? = null
        private var activeContext: CoroutineContext? = null
        private var cleanup: Job? = null

        val languages = listOf("eng", "rus", "heb", "yid", "ara")

        fun trimMemory() {
            cleanupScope.launch {
                gate.withLock {
                    cleanup?.cancel()
                    cleanup = null
                    shared?.let { runCatching { it.recycle() } }
                    shared = null
                    activeContext = null
                }
            }
        }
    }
}
