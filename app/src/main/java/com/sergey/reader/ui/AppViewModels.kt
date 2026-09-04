package com.sergey.reader.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sergey.reader.AppContainer
import com.sergey.reader.data.db.AnnotationEntity
import com.sergey.reader.data.db.AnnotationType
import com.sergey.reader.data.db.BookEntity
import com.sergey.reader.data.db.BookReadingProfileEntity
import com.sergey.reader.data.db.BookmarkEntity
import com.sergey.reader.data.db.DictionaryEntryEntity
import com.sergey.reader.data.db.ParagraphEntity
import com.sergey.reader.data.fonts.UserFont
import com.sergey.reader.data.repository.ScanResult
import com.sergey.reader.data.settings.ContextMenuMode
import com.sergey.reader.data.settings.LibraryViewMode
import com.sergey.reader.data.settings.ReaderMode
import com.sergey.reader.data.settings.ReaderSettings
import com.sergey.reader.data.settings.ReaderThemePreset
import com.sergey.reader.model.ReaderBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    val books: StateFlow<List<BookEntity>> = c.books.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<ReaderSettings> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    val customFonts: StateFlow<List<UserFont>> = c.fonts.fonts

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _scanState = MutableStateFlow<String?>(null)
    val scanState = _scanState.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun importDocuments(uris: List<Uri>, copyIntoLibrary: Boolean = false) {
        viewModelScope.launch {
            var ok = 0
            val errors = mutableListOf<String>()
            uris.forEach { uri ->
                val result = c.books.importDocument(uri, copyIntoLibrary = copyIntoLibrary)
                if (result.isSuccess) ok++ else errors += result.exceptionOrNull()?.message.orEmpty()
            }
            _message.value = if (errors.isEmpty()) {
                "Добавлено книг: $ok"
            } else {
                "Добавлено: $ok. Ошибок: ${errors.size}. ${errors.firstOrNull().orEmpty()}"
            }
        }
    }

    fun scanTree(uri: Uri) {
        viewModelScope.launch {
            _scanState.value = "Сканирование…"
            val result: ScanResult = c.books.scanTree(uri) { name -> _scanState.value = "Сканирование: $name" }
            _scanState.value = null
            _message.value = buildString {
                append("Импортировано: ${result.imported}")
                if (result.skipped > 0) append(", пропущено: ${result.skipped}")
                if (result.errors.isNotEmpty()) append(", ошибок: ${result.errors.size}")
            }
        }
    }

    fun importFont(uri: Uri) = viewModelScope.launch {
        c.fonts.importFont(uri)
            .onSuccess { font ->
                c.settings.setFontPath(font.path)
                _message.value = "Шрифт ${font.name} добавлен и выбран"
            }
            .onFailure { _message.value = it.message ?: "Не удалось импортировать шрифт" }
    }

    fun deleteFont(path: String) = viewModelScope.launch {
        val deleted = c.fonts.deleteFont(path)
        if (deleted && settings.value.fontPath == path) c.settings.setFontPath(null)
        _message.value = if (deleted) "Шрифт удалён" else "Не удалось удалить шрифт"
    }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        c.backup.exportTo(uri)
            .onSuccess { _message.value = "Резервная копия создана" }
            .onFailure { _message.value = it.message ?: "Не удалось создать резервную копию" }
    }

    fun stageRestore(uri: Uri) = viewModelScope.launch {
        c.backup.stageRestore(uri)
            .onSuccess { _message.value = "Копия подготовлена. Полностью закройте и заново откройте Reader — восстановление применится при запуске." }
            .onFailure { _message.value = it.message ?: "Не удалось подготовить восстановление" }
    }

    fun toggleFavorite(book: BookEntity) = viewModelScope.launch { c.books.setFavorite(book.id, !book.favorite) }
    fun toggleWant(book: BookEntity) = viewModelScope.launch { c.books.setWantToRead(book.id, !book.wantToRead) }
    fun toggleFinished(book: BookEntity) = viewModelScope.launch { c.books.setFinished(book.id, !book.finished) }
    fun setLibraryView(mode: LibraryViewMode) = viewModelScope.launch { c.settings.setLibraryView(mode) }
    fun setFontSize(value: Float) = viewModelScope.launch { c.settings.setFontSize(value) }
    fun setLineHeight(value: Float) = viewModelScope.launch { c.settings.setLineHeight(value) }
    fun setPadding(value: Float) = viewModelScope.launch { c.settings.setPadding(value) }
    fun setTheme(value: ReaderThemePreset) = viewModelScope.launch { c.settings.setTheme(value) }
    fun setJustify(value: Boolean) = viewModelScope.launch { c.settings.setJustify(value) }
    fun setShowControlsOnTap(value: Boolean) = viewModelScope.launch { c.settings.setShowControlsOnTap(value) }
    fun setTtsEnabled(value: Boolean) = viewModelScope.launch { c.settings.setTtsEnabled(value) }
    fun setFontPath(value: String?) = viewModelScope.launch { c.settings.setFontPath(value) }
    fun setReaderMode(value: ReaderMode) = viewModelScope.launch { c.settings.setReaderMode(value) }
    fun setTranslatorTemplate(value: String) = viewModelScope.launch { c.settings.setTranslatorUrlTemplate(value) }
    fun setDictionaryTemplate(value: String) = viewModelScope.launch { c.settings.setDictionaryUrlTemplate(value) }
    fun setWebSearchTemplate(value: String) = viewModelScope.launch { c.settings.setWebSearchUrlTemplate(value) }
    fun setContextMenuMode(value: ContextMenuMode) = viewModelScope.launch { c.settings.setContextMenuMode(value) }
    fun setTtsRate(value: Float) = viewModelScope.launch { c.settings.setTtsRate(value) }
    fun setTtsPitch(value: Float) = viewModelScope.launch { c.settings.setTtsPitch(value) }
}

class ReaderViewModel(private val c: AppContainer, private val bookId: Long) : ViewModel() {
    private val _book = MutableStateFlow<BookEntity?>(null)
    val book = _book.asStateFlow()

    private val _blocks = MutableStateFlow<List<ReaderBlock>>(emptyList())
    val blocks = _blocks.asStateFlow()

    private val globalSettings: StateFlow<ReaderSettings> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    val readingProfile: StateFlow<BookReadingProfileEntity?> = c.books.observeReadingProfile(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val perBookProfileEnabled: StateFlow<Boolean> = readingProfile
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val settings: StateFlow<ReaderSettings> = combine(globalSettings, readingProfile) { global, profile ->
        profile?.let { applyProfile(global, it) } ?: global
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    val customFonts: StateFlow<List<UserFont>> = c.fonts.fonts

    val bookmarks = c.books.observeBookmarks(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val annotations = c.books.observeAnnotations(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchResults = MutableStateFlow<List<ParagraphEntity>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        viewModelScope.launch {
            _book.value = c.books.getBook(bookId)
            _blocks.value = c.books.loadBlocks(bookId)
        }
    }

    fun clearMessage() { _message.value = null }

    fun savePosition(index: Int, offset: Int = 0) {
        val total = _blocks.value.size
        if (total == 0) return
        viewModelScope.launch {
            c.books.updateProgress(
                bookId = bookId,
                blockIndex = index.coerceIn(0, total - 1),
                totalBlocks = total,
                positionOffset = offset.coerceAtLeast(0)
            )
            _book.value = c.books.getBook(bookId)
        }
    }

    fun toggleBookmark(blockIndex: Int) = viewModelScope.launch { c.books.addOrRemoveBookmark(bookId, blockIndex) }

    fun search(query: String) = viewModelScope.launch {
        _searchResults.value = c.books.searchBook(bookId, query)
    }

    fun clearSearch() { _searchResults.value = emptyList() }

    fun saveHighlight(selection: TextSelection, colorHex: Long) = saveAnnotation(selection, AnnotationType.HIGHLIGHT, colorHex)
    fun saveQuote(selection: TextSelection, colorHex: Long = 0x66FFF59D) = saveAnnotation(selection, AnnotationType.QUOTE, colorHex)

    fun saveNote(selection: TextSelection, note: String, colorHex: Long = 0x6679C7FF) {
        if (note.isBlank()) return
        viewModelScope.launch {
            runCatching {
                c.books.addAnnotation(
                    bookId = bookId,
                    blockIndex = selection.blockIndex,
                    startOffset = selection.startOffset,
                    endOffset = selection.endOffset,
                    selectedText = selection.text,
                    type = AnnotationType.NOTE,
                    colorHex = colorHex,
                    note = note
                )
            }.onSuccess { _message.value = "Заметка сохранена" }
                .onFailure { _message.value = it.message ?: "Не удалось сохранить заметку" }
        }
    }

    fun addToDictionary(selection: TextSelection) {
        viewModelScope.launch {
            runCatching {
                val contextText = _blocks.value.getOrNull(selection.blockIndex)?.text
                c.books.addDictionaryEntry(
                    term = selection.text,
                    contextText = contextText,
                    bookId = bookId,
                    blockIndex = selection.blockIndex
                )
            }.onSuccess { _message.value = "Добавлено в словарь" }
                .onFailure { _message.value = it.message ?: "Не удалось добавить слово" }
        }
    }

    fun deleteAnnotation(id: Long) = viewModelScope.launch { c.books.deleteAnnotation(id) }

    private fun saveAnnotation(selection: TextSelection, type: AnnotationType, colorHex: Long) {
        viewModelScope.launch {
            runCatching {
                c.books.addAnnotation(
                    bookId = bookId,
                    blockIndex = selection.blockIndex,
                    startOffset = selection.startOffset,
                    endOffset = selection.endOffset,
                    selectedText = selection.text,
                    type = type,
                    colorHex = colorHex
                )
            }.onSuccess {
                _message.value = if (type == AnnotationType.QUOTE) "Цитата сохранена" else "Выделение сохранено"
            }.onFailure { _message.value = it.message ?: "Не удалось сохранить" }
        }
    }

    fun enablePerBookProfile(enabled: Boolean) = viewModelScope.launch {
        if (enabled) {
            val current = settings.value
            c.books.saveReadingProfile(
                BookReadingProfileEntity(
                    bookId = bookId,
                    fontSizeSp = current.fontSizeSp,
                    lineHeight = current.lineHeight,
                    horizontalPaddingDp = current.horizontalPaddingDp,
                    theme = current.theme.name,
                    justify = current.justify,
                    fontPath = current.fontPath,
                    readerMode = current.readerMode.name
                )
            )
            _message.value = "Настройки этой книги отделены от общих"
        } else {
            c.books.deleteReadingProfile(bookId)
            _message.value = "Книга снова использует общие настройки"
        }
    }

    fun setFontSize(value: Float) = updateReadingSetting(
        profileChange = { it.copy(fontSizeSp = value.coerceIn(14f, 42f)) },
        globalChange = { c.settings.setFontSize(value) }
    )

    fun setLineHeight(value: Float) = updateReadingSetting(
        profileChange = { it.copy(lineHeight = value.coerceIn(1f, 2f)) },
        globalChange = { c.settings.setLineHeight(value) }
    )

    fun setPadding(value: Float) = updateReadingSetting(
        profileChange = { it.copy(horizontalPaddingDp = value.coerceIn(8f, 52f)) },
        globalChange = { c.settings.setPadding(value) }
    )

    fun setTheme(value: ReaderThemePreset) = updateReadingSetting(
        profileChange = { it.copy(theme = value.name) },
        globalChange = { c.settings.setTheme(value) }
    )

    fun setJustify(value: Boolean) = updateReadingSetting(
        profileChange = { it.copy(justify = value) },
        globalChange = { c.settings.setJustify(value) }
    )

    fun setFontPath(value: String?) = updateReadingSetting(
        profileChange = { it.copy(fontPath = value) },
        globalChange = { c.settings.setFontPath(value) }
    )

    fun setReaderMode(value: ReaderMode) = updateReadingSetting(
        profileChange = { it.copy(readerMode = value.name) },
        globalChange = { c.settings.setReaderMode(value) }
    )

    fun setShowControlsOnTap(value: Boolean) = viewModelScope.launch {
        c.settings.setShowControlsOnTap(value)
    }

    fun setTtsEnabled(value: Boolean) = viewModelScope.launch {
        c.settings.setTtsEnabled(value)
    }

    private fun updateReadingSetting(
        profileChange: (BookReadingProfileEntity) -> BookReadingProfileEntity,
        globalChange: suspend () -> Unit
    ) {
        viewModelScope.launch {
            val profile = c.books.getReadingProfile(bookId)
            if (profile != null) c.books.saveReadingProfile(profileChange(profile)) else globalChange()
        }
    }

    private fun applyProfile(global: ReaderSettings, profile: BookReadingProfileEntity): ReaderSettings = global.copy(
        fontSizeSp = profile.fontSizeSp,
        lineHeight = profile.lineHeight,
        horizontalPaddingDp = profile.horizontalPaddingDp,
        theme = runCatching { ReaderThemePreset.valueOf(profile.theme) }.getOrDefault(global.theme),
        justify = profile.justify,
        fontPath = profile.fontPath,
        readerMode = runCatching { ReaderMode.valueOf(profile.readerMode) }.getOrDefault(global.readerMode)
    )
}

data class TextSelection(
    val blockIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val text: String
)

class ResearchViewModel(private val c: AppContainer) : ViewModel() {
    val books: StateFlow<List<BookEntity>> = c.books.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val annotations: StateFlow<List<AnnotationEntity>> = c.books.observeAllAnnotations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarks: StateFlow<List<BookmarkEntity>> = c.books.observeAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dictionary: StateFlow<List<DictionaryEntryEntity>> = c.books.observeDictionary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteAnnotation(id: Long) = viewModelScope.launch { c.books.deleteAnnotation(id) }
    fun deleteBookmark(id: Long) = viewModelScope.launch { c.books.deleteBookmark(id) }
    fun deleteDictionary(id: Long) = viewModelScope.launch { c.books.deleteDictionaryEntry(id) }
    fun updateDictionary(entry: DictionaryEntryEntity) = viewModelScope.launch { c.books.updateDictionaryEntry(entry) }
}

class DetailsViewModel(private val c: AppContainer, private val bookId: Long) : ViewModel() {
    private val _book = MutableStateFlow<BookEntity?>(null)
    val book = _book.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch { _book.value = c.books.getBook(bookId) }

    fun save(book: BookEntity) = viewModelScope.launch {
        c.books.updateBook(book)
        _book.value = c.books.getBook(bookId)
    }

    fun toggleFavorite() = viewModelScope.launch {
        _book.value?.let { c.books.setFavorite(it.id, !it.favorite) }
        refresh()
    }

    fun toggleWant() = viewModelScope.launch {
        _book.value?.let { c.books.setWantToRead(it.id, !it.wantToRead) }
        refresh()
    }

    fun toggleFinished() = viewModelScope.launch {
        _book.value?.let { c.books.setFinished(it.id, !it.finished) }
        refresh()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        c.books.deleteFromLibrary(bookId)
        onDone()
    }
}

@Suppress("UNCHECKED_CAST")
class AppViewModelFactory(
    private val container: AppContainer,
    private val kind: Kind,
    private val bookId: Long = 0L
) : ViewModelProvider.Factory {
    enum class Kind { LIBRARY, READER, DETAILS, RESEARCH }

    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (kind) {
        Kind.LIBRARY -> LibraryViewModel(container)
        Kind.READER -> ReaderViewModel(container, bookId)
        Kind.DETAILS -> DetailsViewModel(container, bookId)
        Kind.RESEARCH -> ResearchViewModel(container)
    } as T
}
