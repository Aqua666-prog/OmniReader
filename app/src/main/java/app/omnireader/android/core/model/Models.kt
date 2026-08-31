package app.omnireader.android.core.model

enum class ContentType { BOOK, MANGA, COMIC, PDF, DOCUMENT, IMAGE, OTHER }

enum class FileFormat {
    EPUB, FB2, FB2_ZIP, TXT, HTML, XHTML, MARKDOWN, RTF, DOCX, ODT, MOBI, AZW3,
    PDF, DJVU, DJV, CBZ, CBR, CB7, CBT, ZIP, RAR, SEVEN_Z,
    JPG, JPEG, PNG, WEBP, AVIF, GIF, BMP, TIFF, TIF, IMAGE_FOLDER, UNKNOWN
}

enum class ReadStatus { NOT_STARTED, READING, COMPLETED, ON_HOLD, DROPPED }

data class ReadingPosition(
    val chapter: Int? = null,
    val page: Int? = null,
    val offset: Long? = null,
    val progress: Float = 0f,
)

data class SeriesGuess(
    val title: String,
    val series: String? = null,
    val volume: Double? = null,
)
