package com.sergey.reader.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File
import com.sergey.reader.model.ParsedBook
import com.sergey.reader.model.ParsedChapter
import com.sergey.reader.model.ParsedElement
import com.sergey.reader.util.TextUtil
import java.io.ByteArrayOutputStream

class PdfParser(private val context: Context) : BookParser {
    override suspend fun parse(uri: Uri, displayName: String): ParsedBook {
        val pfd = if (uri.scheme == "file") {
            uri.path?.let { ParcelFileDescriptor.open(File(it), ParcelFileDescriptor.MODE_READ_ONLY) }
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")
        } ?: return ParsedBook(TextUtil.fileTitle(displayName))
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val pages = buildList {
                    for (pageIndex in 0 until renderer.pageCount) {
                        add(
                            ParsedElement(
                                kind = ParsedElement.Kind.PDF_PAGE,
                                text = "Страница ${pageIndex + 1}",
                                resourcePath = pageIndex.toString()
                            )
                        )
                        extractPageText(renderer, pageIndex)?.takeIf { it.isNotBlank() }?.let { text ->
                            add(
                                ParsedElement(
                                    kind = ParsedElement.Kind.PDF_TEXT,
                                    text = text,
                                    resourcePath = pageIndex.toString()
                                )
                            )
                        }
                    }
                }
                val cover = if (renderer.pageCount > 0) renderCover(renderer) else null
                return ParsedBook(
                    title = TextUtil.fileTitle(displayName),
                    coverBytes = cover,
                    chapters = listOf(
                        ParsedChapter(
                            title = "Документ",
                            paragraphs = emptyList(),
                            sourceRef = "pdf",
                            elements = pages
                        )
                    )
                )
            }
        }
    }

    private fun extractPageText(renderer: PdfRenderer, pageIndex: Int): String? {
        if (Build.VERSION.SDK_INT < 35) return null
        return runCatching {
            renderer.openPage(pageIndex).use { page ->
                page.textContents
                    .asSequence()
                    .map { it.text.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .replace(Regex("[\t ]+"), " ")
                    .replace(Regex("\n{3,}"), "\n\n")
                    .trim()
            }
        }.getOrNull()
    }

    private fun renderCover(renderer: PdfRenderer): ByteArray? = runCatching {
        renderer.openPage(0).use { page ->
            val width = 480
            val height = (width * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out)
                bitmap.recycle()
                out.toByteArray()
            }
        }
    }.getOrNull()
}
