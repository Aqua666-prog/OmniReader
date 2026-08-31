package app.omnireader.android.reader

import android.graphics.Bitmap
import app.omnireader.android.core.model.FileFormat
import app.omnireader.android.data.db.LibraryItemEntity
import java.io.Closeable

interface ReaderProvider {
    val id: String
    fun supports(format: FileFormat): Boolean
    suspend fun open(item: LibraryItemEntity): ReaderSession
}

sealed interface ReaderSession : Closeable {
    val item: LibraryItemEntity
}

data class TextChapter(val title: String, val text: String)

sealed interface TextBlock {
    data class Paragraph(val text: String) : TextBlock
    data class Heading(val text: String, val level: Int = 1) : TextBlock
    data class Image(val source: String, val alt: String? = null) : TextBlock
}

class TextReaderSession(
    override val item: LibraryItemEntity,
    val chapters: List<TextChapter>,
    private val chapterBlocks: List<List<TextBlock>> = chapters.map { chapter ->
        if (chapter.text.isBlank()) emptyList() else listOf(TextBlock.Paragraph(chapter.text))
    },
    private val assetLoader: suspend (String) -> ByteArray? = { null },
) : ReaderSession {
    fun blocks(index: Int): List<TextBlock> = chapterBlocks.getOrNull(index).orEmpty()

    suspend fun loadAsset(source: String): ByteArray? = assetLoader(source)

    override fun close() = Unit
}

interface ComicReaderSession : ReaderSession {
    val pageCount: Int
    val pageNames: List<String>
    suspend fun page(index: Int): ByteArray
}

interface PagedBitmapReaderSession : ReaderSession {
    val pageCount: Int
    suspend fun render(index: Int, targetWidth: Int): Bitmap
}

interface PdfReaderSession : PagedBitmapReaderSession

class UnsupportedReaderException(message: String) : IllegalStateException(message)
