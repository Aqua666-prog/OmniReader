package com.sergey.reader.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["uri"], unique = true)]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val title: String,
    val authors: String = "",
    val series: String? = null,
    val seriesIndex: Double? = null,
    val collection: String? = null,
    val format: String,
    val sizeBytes: Long = 0,
    val language: String? = null,
    val annotation: String? = null,
    val coverPath: String? = null,
    val folderLabel: String? = null,
    val favorite: Boolean = false,
    val wantToRead: Boolean = false,
    val finished: Boolean = false,
    val progress: Float = 0f,
    val positionBlock: Int = 0,
    val positionOffset: Int = 0,
    val totalBlocks: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null,
    val wordCount: Long = 0
)

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val orderIndex: Int,
    val title: String,
    val sourceRef: String? = null
)

@Entity(
    tableName = "paragraphs",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId"), Index(value = ["bookId", "chapterIndex", "paragraphIndex"], unique = true)]
)
data class ParagraphEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val text: String,
    val kind: String = "PARAGRAPH",
    val resourcePath: String? = null
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val blockIndex: Int,
    val label: String? = null,
    val colorHex: Long = 0xFF008AA0,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class AnnotationType { HIGHLIGHT, QUOTE, NOTE }

@Entity(
    tableName = "annotations",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId"), Index(value = ["bookId", "blockIndex"])]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val blockIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val selectedText: String,
    val type: String,
    val colorHex: Long = 0x66FFF59D,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "dictionary_entries",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("normalizedTerm"), Index("bookId")]
)
data class DictionaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val term: String,
    val normalizedTerm: String,
    val definition: String? = null,
    val translation: String? = null,
    val contextText: String? = null,
    val bookId: Long? = null,
    val blockIndex: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)


@Entity(
    tableName = "book_reading_profiles",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class BookReadingProfileEntity(
    @PrimaryKey val bookId: Long,
    val fontSizeSp: Float,
    val lineHeight: Float,
    val horizontalPaddingDp: Float,
    val theme: String,
    val justify: Boolean,
    val fontPath: String? = null,
    val readerMode: String = "VERTICAL",
    val updatedAt: Long = System.currentTimeMillis()
)
