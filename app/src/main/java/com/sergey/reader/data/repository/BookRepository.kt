package com.sergey.reader.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.sergey.reader.data.db.AnnotationEntity
import com.sergey.reader.data.db.AnnotationType
import com.sergey.reader.data.db.BookEntity
import com.sergey.reader.data.db.BookReadingProfileEntity
import com.sergey.reader.data.db.DictionaryEntryEntity
import com.sergey.reader.data.db.ChapterEntity
import com.sergey.reader.data.db.ParagraphEntity
import com.sergey.reader.data.db.ReaderDatabase
import com.sergey.reader.data.parser.BookParserFactory
import com.sergey.reader.model.ParsedElement
import com.sergey.reader.model.ReaderBlock
import com.sergey.reader.util.TextUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class BookRepository(
    private val context: Context,
    private val db: ReaderDatabase
) {
    private val bookDao = db.bookDao()
    private val contentDao = db.contentDao()
    private val bookmarkDao = db.bookmarkDao()
    private val annotationDao = db.annotationDao()
    private val dictionaryDao = db.dictionaryDao()
    private val readingProfileDao = db.readingProfileDao()
    private val parserFactory = BookParserFactory(context)

    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeAll()

    suspend fun getBook(id: Long): BookEntity? = bookDao.getById(id)

    suspend fun importDocument(uri: Uri, folderLabel: String? = null, copyIntoLibrary: Boolean = false): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val originalDisplayName = parserFactory.displayName(uri)
            require(parserFactory.isSupported(originalDisplayName)) {
                "Формат пока не поддерживается: $originalDisplayName"
            }

            val sourceUri = if (copyIntoLibrary) copyToPrivateStorage(uri, originalDisplayName) else uri
            if (!copyIntoLibrary) persistReadPermission(uri)

            val displayName = parserFactory.displayName(sourceUri).ifBlank { originalDisplayName }
            val parsed = parserFactory.parserFor(displayName).parse(sourceUri, displayName)
            val size = parserFactory.size(sourceUri).takeIf { it > 0 } ?: parserFactory.size(uri)
            val format = parserFactory.format(displayName).uppercase()
            val coverPath = parsed.coverBytes?.let { saveCover(sourceUri, it) }
            val elements = parsed.chapters.flatMap { chapter ->
                chapter.elements.ifEmpty { chapter.paragraphs.map { ParsedElement(ParsedElement.Kind.PARAGRAPH, text = it) } }
            }
            val wordCount = elements
                .filter { it.kind == ParsedElement.Kind.PARAGRAPH || it.kind == ParsedElement.Kind.FOOTNOTE || it.kind == ParsedElement.Kind.PDF_TEXT }
                .sumOf { TextUtil.wordCount(it.text).toLong() }
            val totalBlocks = parsed.chapters.size + elements.size

            db.withTransaction {
                val existing = bookDao.getByUri(sourceUri.toString())
                    ?: if (sourceUri != uri) bookDao.getByUri(uri.toString()) else null

                val candidate = BookEntity(
                    id = existing?.id ?: 0,
                    uri = sourceUri.toString(),
                    displayName = displayName,
                    title = parsed.title.ifBlank { TextUtil.fileTitle(displayName) },
                    authors = parsed.authors,
                    series = parsed.series,
                    seriesIndex = parsed.seriesIndex,
                    collection = existing?.collection,
                    format = format,
                    sizeBytes = size,
                    language = parsed.language,
                    annotation = parsed.annotation,
                    coverPath = coverPath ?: existing?.coverPath,
                    folderLabel = folderLabel ?: existing?.folderLabel ?: if (copyIntoLibrary) "Внутренняя библиотека" else "Импорт",
                    favorite = existing?.favorite ?: false,
                    wantToRead = existing?.wantToRead ?: false,
                    finished = existing?.finished ?: false,
                    progress = existing?.progress ?: 0f,
                    positionBlock = (existing?.positionBlock ?: 0).coerceAtMost((totalBlocks - 1).coerceAtLeast(0)),
                    totalBlocks = totalBlocks,
                    addedAt = existing?.addedAt ?: System.currentTimeMillis(),
                    lastOpenedAt = existing?.lastOpenedAt,
                    wordCount = wordCount
                )

                val bookId = if (existing == null) bookDao.insert(candidate) else {
                    bookDao.update(candidate)
                    candidate.id
                }

                contentDao.deleteParagraphs(bookId)
                contentDao.deleteChapters(bookId)
                contentDao.insertChapters(parsed.chapters.mapIndexed { chapterIndex, chapter ->
                    ChapterEntity(
                        bookId = bookId,
                        orderIndex = chapterIndex,
                        title = chapter.title.ifBlank { "Глава ${chapterIndex + 1}" },
                        sourceRef = chapter.sourceRef
                    )
                })
                val rows = parsed.chapters.flatMapIndexed { chapterIndex, chapter ->
                    val chapterElements = chapter.elements.ifEmpty {
                        chapter.paragraphs.map { ParsedElement(ParsedElement.Kind.PARAGRAPH, text = it) }
                    }
                    chapterElements.mapIndexed { paragraphIndex, element ->
                        ParagraphEntity(
                            bookId = bookId,
                            chapterIndex = chapterIndex,
                            paragraphIndex = paragraphIndex,
                            text = element.text,
                            kind = element.kind.name,
                            resourcePath = element.resourcePath
                        )
                    }
                }
                rows.chunked(500).forEach { contentDao.insertParagraphs(it) }
                bookId
            }
        }
    }

    suspend fun scanTree(treeUri: Uri, onProgress: suspend (String) -> Unit = {}): ScanResult = withContext(Dispatchers.IO) {
        persistTreePermission(treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext ScanResult(0, 0, listOf("Не удалось открыть папку"))
        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        suspend fun walk(node: DocumentFile, relative: String) {
            for (child in node.listFiles()) {
                if (child.isDirectory) {
                    walk(child, if (relative.isBlank()) child.name.orEmpty() else "$relative/${child.name.orEmpty()}")
                } else if (child.isFile) {
                    val name = child.name ?: continue
                    if (!parserFactory.isSupported(name)) {
                        skipped++
                        continue
                    }
                    onProgress(name)
                    val result = importDocument(child.uri, relative.ifBlank { root.name ?: "Папка" }, copyIntoLibrary = false)
                    if (result.isSuccess) imported++ else errors += "$name: ${result.exceptionOrNull()?.message ?: "ошибка"}"
                }
            }
        }
        walk(root, "")
        ScanResult(imported, skipped, errors)
    }

    suspend fun loadBlocks(bookId: Long): List<ReaderBlock> = withContext(Dispatchers.IO) {
        val chapters = contentDao.chapters(bookId)
        val paragraphs = contentDao.paragraphs(bookId).groupBy { it.chapterIndex }
        buildList {
            chapters.forEach { chapter ->
                add(ReaderBlock(ReaderBlock.Kind.CHAPTER, chapter.title, chapter.orderIndex))
                paragraphs[chapter.orderIndex].orEmpty().forEach { p ->
                    val kind = when (runCatching { ParsedElement.Kind.valueOf(p.kind) }.getOrNull()) {
                        ParsedElement.Kind.IMAGE -> ReaderBlock.Kind.IMAGE
                        ParsedElement.Kind.FOOTNOTE -> ReaderBlock.Kind.FOOTNOTE
                        ParsedElement.Kind.PDF_PAGE -> ReaderBlock.Kind.PDF_PAGE
                        ParsedElement.Kind.PDF_TEXT -> ReaderBlock.Kind.PDF_TEXT
                        else -> ReaderBlock.Kind.PARAGRAPH
                    }
                    add(ReaderBlock(kind, p.text, p.chapterIndex, p.paragraphIndex, p.resourcePath))
                }
            }
        }
    }

    suspend fun searchBook(bookId: Long, query: String): List<ParagraphEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else contentDao.search(bookId, query.trim())
    }

    suspend fun updateProgress(bookId: Long, blockIndex: Int, totalBlocks: Int, positionOffset: Int = 0) = withContext(Dispatchers.IO) {
        val progress = if (totalBlocks <= 1) 0f else blockIndex.toFloat() / (totalBlocks - 1).toFloat()
        bookDao.updateProgress(
            bookId = bookId,
            progress = progress.coerceIn(0f, 1f),
            positionBlock = blockIndex,
            positionOffset = positionOffset.coerceAtLeast(0),
            totalBlocks = totalBlocks,
            lastOpenedAt = System.currentTimeMillis()
        )
    }

    suspend fun updateBook(book: BookEntity) = withContext(Dispatchers.IO) { bookDao.update(book) }
    suspend fun setFavorite(bookId: Long, value: Boolean) = withContext(Dispatchers.IO) { bookDao.setFavorite(bookId, value) }
    suspend fun setWantToRead(bookId: Long, value: Boolean) = withContext(Dispatchers.IO) { bookDao.setWantToRead(bookId, value) }
    suspend fun setFinished(bookId: Long, value: Boolean) = withContext(Dispatchers.IO) { bookDao.setFinished(bookId, value) }
    suspend fun deleteFromLibrary(bookId: Long) = withContext(Dispatchers.IO) { bookDao.deleteById(bookId) }

    suspend fun addOrRemoveBookmark(bookId: Long, blockIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val existing = bookmarkDao.atPosition(bookId, blockIndex)
        if (existing != null) {
            bookmarkDao.delete(existing.id)
            false
        } else {
            bookmarkDao.insert(com.sergey.reader.data.db.BookmarkEntity(bookId = bookId, blockIndex = blockIndex))
            true
        }
    }

    fun observeBookmarks(bookId: Long) = bookmarkDao.observeForBook(bookId)

    fun observeAllBookmarks() = bookmarkDao.observeAll()

    suspend fun deleteBookmark(id: Long) = withContext(Dispatchers.IO) { bookmarkDao.delete(id) }

    fun observeAnnotations(bookId: Long) = annotationDao.observeForBook(bookId)

    fun observeAllAnnotations() = annotationDao.observeAll()

    fun observeDictionary() = dictionaryDao.observeAll()

    suspend fun addAnnotation(
        bookId: Long,
        blockIndex: Int,
        startOffset: Int,
        endOffset: Int,
        selectedText: String,
        type: AnnotationType,
        colorHex: Long,
        note: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val clean = selectedText.trim()
        require(clean.isNotBlank()) { "Нельзя сохранить пустой фрагмент" }
        val start = startOffset.coerceAtLeast(0)
        val end = endOffset.coerceAtLeast(start)
        annotationDao.insert(
            AnnotationEntity(
                bookId = bookId,
                blockIndex = blockIndex,
                startOffset = start,
                endOffset = end,
                selectedText = clean,
                type = type.name,
                colorHex = colorHex,
                note = note?.trim()?.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun deleteAnnotation(id: Long) = withContext(Dispatchers.IO) { annotationDao.delete(id) }

    suspend fun addDictionaryEntry(
        term: String,
        contextText: String?,
        bookId: Long?,
        blockIndex: Int?
    ): Long = withContext(Dispatchers.IO) {
        val clean = term.trim().replace(Regex("\\s+"), " ")
        require(clean.isNotBlank()) { "Пустой термин" }
        val normalized = clean.lowercase()
        val existing = dictionaryDao.findNormalized(normalized)
        if (existing != null) {
            dictionaryDao.update(
                existing.copy(
                    term = clean,
                    contextText = contextText?.trim()?.takeIf { it.isNotBlank() } ?: existing.contextText,
                    bookId = bookId ?: existing.bookId,
                    blockIndex = blockIndex ?: existing.blockIndex,
                    updatedAt = System.currentTimeMillis()
                )
            )
            existing.id
        } else {
            dictionaryDao.insert(
                DictionaryEntryEntity(
                    term = clean,
                    normalizedTerm = normalized,
                    contextText = contextText?.trim()?.takeIf { it.isNotBlank() },
                    bookId = bookId,
                    blockIndex = blockIndex
                )
            )
        }
    }

    suspend fun updateDictionaryEntry(entry: DictionaryEntryEntity) = withContext(Dispatchers.IO) {
        dictionaryDao.update(
            entry.copy(
                term = entry.term.trim(),
                normalizedTerm = entry.term.trim().lowercase(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteDictionaryEntry(id: Long) = withContext(Dispatchers.IO) { dictionaryDao.delete(id) }

    fun observeReadingProfile(bookId: Long) = readingProfileDao.observeForBook(bookId)

    suspend fun getReadingProfile(bookId: Long): BookReadingProfileEntity? = withContext(Dispatchers.IO) {
        readingProfileDao.getForBook(bookId)
    }

    suspend fun saveReadingProfile(profile: BookReadingProfileEntity) = withContext(Dispatchers.IO) {
        readingProfileDao.upsert(profile.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteReadingProfile(bookId: Long) = withContext(Dispatchers.IO) {
        readingProfileDao.deleteForBook(bookId)
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun persistTreePermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private fun copyToPrivateStorage(uri: Uri, displayName: String): Uri {
        val dir = File(context.filesDir, "books").apply { mkdirs() }
        val safeName = displayName.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
        var target = File(dir, safeName)
        var i = 2
        while (target.exists()) {
            val stem = safeName.substringBeforeLast('.', safeName)
            val ext = safeName.substringAfterLast('.', "")
            target = File(dir, if (ext.isBlank()) "$stem ($i)" else "$stem ($i).$ext")
            i++
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Не удалось прочитать файл")
        return target.toUri()
    }

    private fun saveCover(uri: Uri, bytes: ByteArray): String {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = File(dir, "$digest.img")
        file.writeBytes(bytes)
        return file.absolutePath
    }
}

data class ScanResult(
    val imported: Int,
    val skipped: Int,
    val errors: List<String>
)
