package com.sergey.reader.data.parser

import android.content.Context
import android.net.Uri
import com.github.axet.djvulibre.DjvuLibre
import com.sergey.reader.model.ParsedBook
import com.sergey.reader.model.ParsedChapter
import com.sergey.reader.model.ParsedElement
import com.sergey.reader.util.TextUtil
import java.io.File

/**
 * Native DjVu parser backed by DjVuLibre.
 *
 * Pages remain fixed-layout raster pages. When the DjVu contains an embedded text
 * layer, a hidden DJVU_TEXT block is stored immediately after its visual page so
 * search, TTS, quotes/notes and the "Text of page" sheet can use it.
 */
class DjvuBookParser(private val context: Context) : BookParser {
    override suspend fun parse(uri: Uri, displayName: String): ParsedBook {
        val pfd = if (uri.scheme == "file") {
            uri.path?.let { path ->
                android.os.ParcelFileDescriptor.open(File(path), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            }
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")
        } ?: throw IllegalArgumentException("Не удалось открыть DjVu")

        return pfd.use { descriptor ->
            val doc = runCatching { DjvuLibre(descriptor.fileDescriptor) }
                .getOrElse { throw IllegalArgumentException("Не удалось прочитать DjVu: ${it.message ?: "ошибка декодера"}", it) }
            try {
                val pageCount = runCatching { doc.pagesCount }.getOrDefault(0)
                require(pageCount > 0) { "В DjVu не найдено страниц" }

                val elements = buildList {
                    for (page in 0 until pageCount) {
                        add(
                            ParsedElement(
                                kind = ParsedElement.Kind.DJVU_PAGE,
                                resourcePath = page.toString()
                            )
                        )
                        extractPageText(doc, page)?.takeIf { it.isNotBlank() }?.let { text ->
                            add(
                                ParsedElement(
                                    kind = ParsedElement.Kind.DJVU_TEXT,
                                    text = text,
                                    resourcePath = page.toString()
                                )
                            )
                        }
                    }
                }

                val metadataTitle = runCatching { doc.getMeta(DjvuLibre.META_TITLE) }
                    .getOrNull()?.trim()?.takeIf { it.isNotBlank() }
                val metadataAuthor = runCatching { doc.getMeta(DjvuLibre.META_AUTHOR) }
                    .getOrNull()?.trim()?.takeIf { it.isNotBlank() }.orEmpty()

                ParsedBook(
                    title = metadataTitle ?: TextUtil.fileTitle(displayName),
                    authors = metadataAuthor,
                    chapters = listOf(
                        ParsedChapter(
                            title = "Документ",
                            elements = elements
                        )
                    )
                )
            } finally {
                runCatching { doc.close() }
            }
        }
    }

    private fun extractPageText(doc: DjvuLibre, page: Int): String? {
        val candidates = intArrayOf(
            DjvuLibre.ZONE_PARAGRAPH,
            DjvuLibre.ZONE_LINE,
            DjvuLibre.ZONE_WORD
        )
        for (zone in candidates) {
            val text = runCatching { doc.getText(page, zone) }.getOrNull() ?: continue
            val parts = text.text.orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (parts.isEmpty()) continue
            val separator = when (zone) {
                DjvuLibre.ZONE_PARAGRAPH -> "\n\n"
                DjvuLibre.ZONE_LINE -> "\n"
                else -> " "
            }
            return parts.joinToString(separator)
        }
        return null
    }
}
