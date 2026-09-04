package com.sergey.reader.model

data class ParsedBook(
    val title: String,
    val authors: String = "",
    val series: String? = null,
    val seriesIndex: Double? = null,
    val language: String? = null,
    val annotation: String? = null,
    val coverBytes: ByteArray? = null,
    val chapters: List<ParsedChapter> = emptyList()
)

data class ParsedChapter(
    val title: String,
    val paragraphs: List<String> = emptyList(),
    val sourceRef: String? = null,
    val elements: List<ParsedElement> = paragraphs.map { ParsedElement(ParsedElement.Kind.PARAGRAPH, text = it) }
)

data class ParsedElement(
    val kind: Kind,
    val text: String = "",
    val resourcePath: String? = null
) {
    enum class Kind { PARAGRAPH, IMAGE, FOOTNOTE, PDF_PAGE, PDF_TEXT }
}

data class ReaderBlock(
    val kind: Kind,
    val text: String,
    val chapterIndex: Int,
    val paragraphIndex: Int = -1,
    val resourcePath: String? = null
) {
    enum class Kind { CHAPTER, PARAGRAPH, IMAGE, FOOTNOTE, PDF_PAGE, PDF_TEXT }

    val isSpeakable: Boolean
        get() = kind == Kind.PARAGRAPH || kind == Kind.FOOTNOTE || kind == Kind.PDF_TEXT || kind == Kind.CHAPTER
}

data class ReaderPage(
    val startBlock: Int,
    val endBlockExclusive: Int
) {
    init {
        require(startBlock >= 0)
        require(endBlockExclusive > startBlock)
    }
}

enum class LibrarySection(val label: String) {
    CURRENT("Читаю сейчас"),
    ALL("Все книги и документы"),
    FAVORITES("Избранное"),
    WANT_TO_READ("Хочу прочитать"),
    FINISHED("Прочитанные"),
    AUTHORS("Авторы"),
    SERIES("Серии"),
    COLLECTIONS("Коллекции"),
    FORMATS("Форматы"),
    FOLDERS("Папки")
}
