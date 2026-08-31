package app.omnireader.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.omnireader.android.core.model.ContentType
import app.omnireader.android.core.model.FileFormat
import app.omnireader.android.core.model.ReadStatus

@Entity(tableName = "source_folders")
data class SourceFolderEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastScanAt: Long? = null,
    val isAvailable: Boolean = true,
)

@Entity(
    tableName = "library_items",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["sourceFolderUri"]),
        Index(value = ["title"]),
        Index(value = ["author"]),
        Index(value = ["series"]),
        Index(value = ["lastOpenedAt"]),
        Index(value = ["contentFingerprint"]),
    ],
)
data class LibraryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val fileName: String,
    val format: FileFormat,
    val mimeType: String?,
    val contentType: ContentType,
    val title: String,
    val author: String? = null,
    val series: String? = null,
    val volumeNumber: Double? = null,
    val description: String? = null,
    val coverCachePath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null,
    val fileSize: Long = 0,
    val lastModified: Long = 0,
    val currentChapter: Int? = null,
    val currentPage: Int? = null,
    val positionOffset: Long? = null,
    val progress: Float = 0f,
    val pageCount: Int? = null,
    val chapterCount: Int? = null,
    val readStatus: ReadStatus = ReadStatus.NOT_STARTED,
    val favorite: Boolean = false,
    val sourceFolderUri: String,
    val contentFingerprint: String,
    val lastSeenScanToken: String? = null,
    val isPresent: Boolean = true,
    val detectedEncoding: String? = null,
    val userEncodingOverride: String? = null,
    val userEditedMetadata: Boolean = false,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(
        entity = LibraryItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["libraryItemId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("libraryItemId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val libraryItemId: Long,
    val chapter: Int? = null,
    val page: Int? = null,
    val positionOffset: Long? = null,
    val title: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = LibraryItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["libraryItemId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("libraryItemId")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val libraryItemId: Long,
    val quote: String? = null,
    val comment: String,
    val chapter: Int? = null,
    val page: Int? = null,
    val positionOffset: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "collections", indices = [Index(value = ["name"], unique = true)])
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "collection_items",
    primaryKeys = ["collectionId", "libraryItemId"],
    foreignKeys = [
        ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LibraryItemEntity::class, parentColumns = ["id"], childColumns = ["libraryItemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("libraryItemId")],
)
data class CollectionItemCrossRef(val collectionId: Long, val libraryItemId: Long)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "item_tags",
    primaryKeys = ["tagId", "libraryItemId"],
    foreignKeys = [
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LibraryItemEntity::class, parentColumns = ["id"], childColumns = ["libraryItemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("libraryItemId")],
)
data class ItemTagCrossRef(val tagId: Long, val libraryItemId: Long)

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(entity = LibraryItemEntity::class, parentColumns = ["id"], childColumns = ["libraryItemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("libraryItemId"), Index("startedAt")],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val libraryItemId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val startProgress: Float,
    val endProgress: Float,
    val pagesAdvanced: Int = 0,
)
