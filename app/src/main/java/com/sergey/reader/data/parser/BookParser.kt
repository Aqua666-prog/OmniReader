package com.sergey.reader.data.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sergey.reader.model.ParsedBook
import com.sergey.reader.util.TextUtil
import java.io.File
import java.util.Locale

interface BookParser {
    suspend fun parse(uri: Uri, displayName: String): ParsedBook
}

class BookParserFactory(private val context: Context) {
    private val resolver = context.contentResolver

    fun displayName(uri: Uri): String {
        if (uri.scheme == "file") return uri.path?.let(::File)?.name ?: (uri.lastPathSegment ?: "Книга")
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "Книга"
    }

    fun size(uri: Uri): Long {
        if (uri.scheme == "file") return uri.path?.let(::File)?.length() ?: 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) return cursor.getLong(index)
            }
        }
        return 0L
    }

    fun format(displayName: String): String {
        val lower = displayName.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".fb2.zip") -> "fb2.zip"
            lower.endsWith(".fb2.gz") -> "fb2.gz"
            lower.endsWith(".markdown") -> "markdown"
            else -> lower.substringAfterLast('.', "")
        }
    }

    fun parserFor(displayName: String): BookParser = when (format(displayName)) {
        "epub" -> EpubParser(context)
        "fb2" -> Fb2Parser(resolver)
        "fb2.zip" -> ArchiveBookParser(context, ArchiveKind.FB2_ZIP)
        "fb2.gz" -> ArchiveBookParser(context, ArchiveKind.FB2_GZ)
        "txt" -> TxtParser(resolver)
        "pdf" -> PdfParser(context)
        "html", "htm", "xhtml" -> HtmlBookParser(context)
        "md", "markdown" -> MarkdownBookParser(context)
        "rtf" -> RtfBookParser(context)
        "docx" -> OfficeBookParser(context, OfficeKind.DOCX)
        "odt" -> OfficeBookParser(context, OfficeKind.ODT)
        "mobi", "azw", "azw3" -> MobiBookParser(context)
        "zip" -> ArchiveBookParser(context, ArchiveKind.ZIP)
        "cbz" -> ArchiveBookParser(context, ArchiveKind.CBZ)
        "cbr" -> ArchiveBookParser(context, ArchiveKind.CBR)
        "cb7" -> ArchiveBookParser(context, ArchiveKind.CB7)
        "chm" -> ChmBookParser(context)
        "djvu", "djv" -> DjvuBookParser(context)
        else -> PlainFallbackParser(resolver)
    }

    fun isSupported(displayName: String): Boolean = format(displayName) in setOf(
        "epub", "fb2", "fb2.zip", "fb2.gz", "txt", "pdf",
        "html", "htm", "xhtml", "md", "markdown", "rtf", "docx", "odt",
        "mobi", "azw", "azw3", "zip", "cbz", "cbr", "cb7", "chm",
        "djvu", "djv"
    )
}

class PlainFallbackParser(private val resolver: android.content.ContentResolver) : BookParser {
    override suspend fun parse(uri: Uri, displayName: String): ParsedBook {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        val text = TextUtil.decode(bytes)
        return ParsedBook(
            title = TextUtil.fileTitle(displayName),
            chapters = listOf(
                com.sergey.reader.model.ParsedChapter(
                    "Текст",
                    text.split(Regex("\\n\\s*\\n")).map(TextUtil::normalizeParagraph).filter { it.isNotBlank() }
                )
            )
        )
    }
}
