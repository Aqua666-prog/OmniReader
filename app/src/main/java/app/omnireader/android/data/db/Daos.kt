package app.omnireader.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.omnireader.android.core.model.ContentType
import app.omnireader.android.core.model.ReadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceFolderDao {
    @Query("SELECT * FROM source_folders ORDER BY addedAt") fun observeAll(): Flow<List<SourceFolderEntity>>
    @Query("SELECT * FROM source_folders ORDER BY addedAt") suspend fun getAll(): List<SourceFolderEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(folder: SourceFolderEntity)
    @Query("DELETE FROM source_folders WHERE uri = :uri") suspend fun delete(uri: String)
    @Query("UPDATE source_folders SET lastScanAt = :time, isAvailable = :available WHERE uri = :uri")
    suspend fun updateScanState(uri: String, time: Long, available: Boolean)
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_items ORDER BY COALESCE(lastOpenedAt, 0) DESC, title COLLATE NOCASE")
    fun observeAll(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items WHERE id = :id LIMIT 1") fun observeById(id: Long): Flow<LibraryItemEntity?>
    @Query("SELECT * FROM library_items WHERE id = :id LIMIT 1") suspend fun getById(id: Long): LibraryItemEntity?
    @Query("SELECT * FROM library_items WHERE uri = :uri LIMIT 1") suspend fun getByUri(uri: String): LibraryItemEntity?
    @Query("SELECT * FROM library_items WHERE contentFingerprint = :fingerprint AND sourceFolderUri = :sourceUri AND (lastSeenScanToken IS NULL OR lastSeenScanToken != :scanToken) LIMIT 1")
    suspend fun findStaleByFingerprint(fingerprint: String, sourceUri: String, scanToken: String): LibraryItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: LibraryItemEntity): Long
    @Update suspend fun update(item: LibraryItemEntity)

    @Query("UPDATE library_items SET isPresent = 0 WHERE sourceFolderUri = :sourceUri AND (lastSeenScanToken IS NULL OR lastSeenScanToken != :scanToken)")
    suspend fun markUnseenMissing(sourceUri: String, scanToken: String)

    @Query("UPDATE library_items SET isPresent = 0 WHERE sourceFolderUri = :sourceUri")
    suspend fun markSourceDetached(sourceUri: String)

    @Query("DELETE FROM library_items WHERE sourceFolderUri = :sourceUri AND isPresent = 0 AND progress = 0 AND favorite = 0")
    suspend fun purgeNeverReadMissing(sourceUri: String)

    @Query("UPDATE library_items SET currentChapter=:chapter, currentPage=:page, positionOffset=:offset, progress=:progress, lastOpenedAt=:openedAt, readStatus=:status WHERE id=:id")
    suspend fun updateProgress(id: Long, chapter: Int?, page: Int?, offset: Long?, progress: Float, openedAt: Long, status: ReadStatus)

    @Query("UPDATE library_items SET readStatus = :status WHERE id = :id") suspend fun setStatus(id: Long, status: ReadStatus)

    @Query("UPDATE library_items SET title=:title, author=:author, series=:series, volumeNumber=:volume, description=:description, coverCachePath=:coverPath, contentType=:contentType, userEditedMetadata=1 WHERE id=:id")
    suspend fun editMetadata(id: Long, title: String, author: String?, series: String?, volume: Double?, description: String?, coverPath: String?, contentType: ContentType)

    @Transaction
    suspend fun upsertScanned(candidate: LibraryItemEntity): Long {
        val existing = getByUri(candidate.uri)
        if (existing != null) {
            val merged = candidate.copy(
                id = existing.id,
                title = if (existing.userEditedMetadata) existing.title else candidate.title,
                author = if (existing.userEditedMetadata) existing.author else candidate.author,
                series = if (existing.userEditedMetadata) existing.series else candidate.series,
                volumeNumber = if (existing.userEditedMetadata) existing.volumeNumber else candidate.volumeNumber,
                description = if (existing.userEditedMetadata) existing.description else candidate.description,
                coverCachePath = existing.coverCachePath ?: candidate.coverCachePath,
                addedAt = existing.addedAt,
                lastOpenedAt = existing.lastOpenedAt,
                currentChapter = existing.currentChapter,
                currentPage = existing.currentPage,
                positionOffset = existing.positionOffset,
                progress = existing.progress,
                readStatus = existing.readStatus,
                favorite = existing.favorite,
                userEncodingOverride = existing.userEncodingOverride,
                userEditedMetadata = existing.userEditedMetadata,
            )
            update(merged)
            return existing.id
        }

        val scanToken = requireNotNull(candidate.lastSeenScanToken) { "Scanned items require a scan token" }
        val moved = findStaleByFingerprint(candidate.contentFingerprint, candidate.sourceFolderUri, scanToken)
        if (moved != null) {
            update(candidate.copy(
                id = moved.id,
                addedAt = moved.addedAt,
                lastOpenedAt = moved.lastOpenedAt,
                currentChapter = moved.currentChapter,
                currentPage = moved.currentPage,
                positionOffset = moved.positionOffset,
                progress = moved.progress,
                readStatus = moved.readStatus,
                favorite = moved.favorite,
                title = if (moved.userEditedMetadata) moved.title else candidate.title,
                author = if (moved.userEditedMetadata) moved.author else candidate.author,
                series = if (moved.userEditedMetadata) moved.series else candidate.series,
                volumeNumber = if (moved.userEditedMetadata) moved.volumeNumber else candidate.volumeNumber,
                description = if (moved.userEditedMetadata) moved.description else candidate.description,
                coverCachePath = moved.coverCachePath ?: candidate.coverCachePath,
                userEditedMetadata = moved.userEditedMetadata,
                userEncodingOverride = moved.userEncodingOverride,
            ))
            return moved.id
        }
        return insert(candidate)
    }
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE libraryItemId = :itemId ORDER BY createdAt DESC") fun observeForItem(itemId: Long): Flow<List<BookmarkEntity>>
    @Insert suspend fun insert(bookmark: BookmarkEntity): Long
    @Query("DELETE FROM bookmarks WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE libraryItemId = :itemId ORDER BY createdAt DESC") fun observeForItem(itemId: Long): Flow<List<NoteEntity>>
    @Insert suspend fun insert(note: NoteEntity): Long
    @Update suspend fun update(note: NoteEntity)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE") fun observeAll(): Flow<List<CollectionEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(collection: CollectionEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addItem(ref: CollectionItemCrossRef)
    @Query("DELETE FROM collection_items WHERE collectionId=:collectionId AND libraryItemId=:itemId") suspend fun removeItem(collectionId: Long, itemId: Long)
}
