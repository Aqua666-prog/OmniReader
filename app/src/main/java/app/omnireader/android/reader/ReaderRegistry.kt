package app.omnireader.android.reader

import android.content.Context
import app.omnireader.android.core.cache.SafFileCache
import app.omnireader.android.data.db.LibraryItemEntity
import app.omnireader.android.data.repository.LibraryRepository
import app.omnireader.android.reader.comic.ComicReaderProvider
import app.omnireader.android.reader.djvu.DjvuReaderProvider
import app.omnireader.android.reader.image.ImageReaderProvider
import app.omnireader.android.reader.pdf.PdfReaderProvider
import app.omnireader.android.reader.text.DocumentTextReaderProvider
import app.omnireader.android.reader.text.KindleReaderProvider
import app.omnireader.android.reader.text.TextReaderProvider

class ReaderRegistry(
    context: Context,
    private val repository: LibraryRepository,
    cache: SafFileCache,
) {
    private val providers: List<ReaderProvider> = listOf(
        TextReaderProvider(context, cache),
        DocumentTextReaderProvider(context, cache),
        KindleReaderProvider(context, cache),
        ComicReaderProvider(context, cache),
        PdfReaderProvider(context, cache),
        DjvuReaderProvider(cache),
        ImageReaderProvider(context, cache),
    )

    fun providerFor(item: LibraryItemEntity): ReaderProvider? = providers.firstOrNull { it.supports(item.format) }

    suspend fun open(item: LibraryItemEntity): ReaderSession = providerFor(item)?.open(item)
        ?: throw UnsupportedReaderException("Для ${item.format} ReaderProvider пока не включён")
}
