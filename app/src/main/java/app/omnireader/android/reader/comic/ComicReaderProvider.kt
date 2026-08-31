package app.omnireader.android.reader.comic

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.omnireader.android.core.cache.SafFileCache
import app.omnireader.android.core.model.FileFormat
import app.omnireader.android.core.util.NaturalSort
import app.omnireader.android.data.db.LibraryItemEntity
import app.omnireader.android.reader.ComicReaderSession
import app.omnireader.android.reader.ReaderProvider
import app.omnireader.android.reader.ReaderSession
import app.omnireader.android.scanner.FormatDetector
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

class ComicReaderProvider(
    private val context: Context,
    private val cache: SafFileCache,
) : ReaderProvider {
    override val id = "comic"
    override fun supports(format: FileFormat) = format in setOf(
        FileFormat.CBZ, FileFormat.CBR, FileFormat.CB7, FileFormat.CBT,
        FileFormat.ZIP, FileFormat.RAR, FileFormat.SEVEN_Z, FileFormat.IMAGE_FOLDER,
    )

    override suspend fun open(item: LibraryItemEntity): ReaderSession = withContext(Dispatchers.IO) {
        if (item.format == FileFormat.IMAGE_FOLDER) {
            return@withContext ImageFolderSession(item, context.contentResolver, Uri.parse(item.uri))
        }
        val staged = cache.stage(Uri.parse(item.uri), item.fileName, "${item.lastModified}:${item.fileSize}")
        when (item.format) {
            FileFormat.CBR, FileFormat.RAR -> RarSession(item, Archive(staged))
            FileFormat.CB7, FileFormat.SEVEN_Z -> SevenZSession(item, staged)
            FileFormat.CBT -> TarSession(item, staged)
            else -> ZipSession(item, ZipFile(staged))
        }
    }

    private class ZipSession(
        override val item: LibraryItemEntity,
        private val zip: ZipFile,
    ) : ComicReaderSession {
        override val pageNames = zip.entries().asSequence()
            .filter { !it.isDirectory && FormatDetector.isImageName(it.name) }
            .map { it.name }
            .sortedWith(NaturalSort.comparator)
            .toList()
        override val pageCount get() = pageNames.size
        override suspend fun page(index: Int): ByteArray = withContext(Dispatchers.IO) {
            val entry = zip.getEntry(pageNames[index]) ?: error("Страница отсутствует")
            zip.getInputStream(entry).use { it.readBytes() }
        }
        override fun close() = zip.close()
    }

    private class RarSession(
        override val item: LibraryItemEntity,
        private val archive: Archive,
    ) : ComicReaderSession {
        private val mutex = Mutex()
        private val headers: Map<String, FileHeader> = archive.fileHeaders
            .filter { !it.isDirectory && FormatDetector.isImageName(it.fileName) }
            .associateBy { it.fileName }
        override val pageNames = headers.keys.sortedWith(NaturalSort.comparator)
        override val pageCount get() = pageNames.size
        override suspend fun page(index: Int): ByteArray = withContext(Dispatchers.IO) {
            val header = headers[pageNames[index]] ?: error("Страница отсутствует")
            mutex.withLock {
                ByteArrayOutputStream().use { out ->
                    archive.extractFile(header, out)
                    out.toByteArray()
                }
            }
        }
        override fun close() = archive.close()
    }

    @Suppress("DEPRECATION")
    private class SevenZSession(
        override val item: LibraryItemEntity,
        staged: File,
    ) : ComicReaderSession {
        private val archive = SevenZFile(staged)
        private val mutex = Mutex()
        private val entries = archive.entries.asSequence()
            .filter { !it.isDirectory && FormatDetector.isImageName(it.name) }
            .associateBy { it.name }
        override val pageNames = entries.keys.sortedWith(NaturalSort.comparator)
        override val pageCount get() = pageNames.size

        override suspend fun page(index: Int): ByteArray = withContext(Dispatchers.IO) {
            val entry = entries[pageNames[index]] ?: error("Страница отсутствует")
            mutex.withLock {
                archive.getInputStream(entry).use { input -> input.readBytes() }
            }
        }

        override fun close() = archive.close()
    }

    private class TarSession(
        override val item: LibraryItemEntity,
        private val staged: File,
    ) : ComicReaderSession {
        override val pageNames: List<String> = scanNames(staged)
        override val pageCount get() = pageNames.size

        override suspend fun page(index: Int): ByteArray = withContext(Dispatchers.IO) {
            val wanted = pageNames[index]
            TarArchiveInputStream(BufferedInputStream(FileInputStream(staged))).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    if (!entry.isDirectory && entry.name == wanted) return@withContext tar.readBytes()
                }
            }
            error("Страница отсутствует")
        }

        override fun close() = Unit

        companion object {
            private fun scanNames(file: File): List<String> {
                val names = mutableListOf<String>()
                TarArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        if (!entry.isDirectory && FormatDetector.isImageName(entry.name)) names += entry.name
                    }
                }
                return names.sortedWith(NaturalSort.comparator)
            }
        }
    }

    private class ImageFolderSession(
        override val item: LibraryItemEntity,
        private val resolver: ContentResolver,
        directoryUri: Uri,
    ) : ComicReaderSession {
        private val pages: List<Pair<String, Uri>> = listImages(resolver, directoryUri)
        override val pageNames: List<String> = pages.map { it.first }
        override val pageCount get() = pages.size
        override suspend fun page(index: Int): ByteArray = withContext(Dispatchers.IO) {
            resolver.openInputStream(pages[index].second)?.use { it.readBytes() }
                ?: error("Изображение недоступно через SAF")
        }
        override fun close() = Unit

        companion object {
            private fun listImages(resolver: ContentResolver, directoryUri: Uri): List<Pair<String, Uri>> {
                val docId = DocumentsContract.getDocumentId(directoryUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, docId)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
                val out = mutableListOf<Pair<String, Uri>>()
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val mime = cursor.getString(mimeCol)
                        val name = cursor.getString(nameCol) ?: continue
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR || !FormatDetector.isImageName(name)) continue
                        val child = DocumentsContract.buildDocumentUriUsingTree(directoryUri, cursor.getString(idCol))
                        out += name to child
                    }
                }
                return out.sortedWith { a, b -> NaturalSort.comparator.compare(a.first, b.first) }
            }
        }
    }
}
