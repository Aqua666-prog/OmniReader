package app.omnireader.android.data.repository

import app.omnireader.android.core.model.ReadStatus
import app.omnireader.android.data.db.LibraryDao
import app.omnireader.android.data.db.LibraryItemEntity
import app.omnireader.android.data.db.SourceFolderDao
import app.omnireader.android.data.db.SourceFolderEntity
import kotlinx.coroutines.flow.Flow

class LibraryRepository(
    private val libraryDao: LibraryDao,
    private val sourceFolderDao: SourceFolderDao,
) {
    val items: Flow<List<LibraryItemEntity>> = libraryDao.observeAll()
    val folders: Flow<List<SourceFolderEntity>> = sourceFolderDao.observeAll()

    suspend fun addFolder(folder: SourceFolderEntity) = sourceFolderDao.upsert(folder)
    suspend fun removeFolder(uri: String) {
        libraryDao.markSourceDetached(uri)
        sourceFolderDao.delete(uri)
    }
    suspend fun getFolders(): List<SourceFolderEntity> = sourceFolderDao.getAll()
    suspend fun getItem(id: Long): LibraryItemEntity? = libraryDao.getById(id)
    fun observeItem(id: Long): Flow<LibraryItemEntity?> = libraryDao.observeById(id)
    suspend fun upsertScanned(item: LibraryItemEntity): Long = libraryDao.upsertScanned(item)
    suspend fun finalizeSuccessfulScan(sourceUri: String, scanToken: String) {
        libraryDao.markUnseenMissing(sourceUri, scanToken)
        sourceFolderDao.updateScanState(sourceUri, System.currentTimeMillis(), true)
    }
    suspend fun updateSourceAvailability(sourceUri: String, available: Boolean) =
        sourceFolderDao.updateScanState(sourceUri, System.currentTimeMillis(), available)

    suspend fun saveProgress(itemId: Long, chapter: Int?, page: Int?, offset: Long?, progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        val current = libraryDao.getById(itemId) ?: return
        val status = statusForProgress(current.readStatus, clamped)
        libraryDao.updateProgress(itemId, chapter, page, offset, clamped, System.currentTimeMillis(), status)
    }

    companion object {
        fun statusForProgress(current: ReadStatus, progress: Float): ReadStatus = when {
            progress >= 0.995f -> ReadStatus.COMPLETED
            progress > 0f && current == ReadStatus.NOT_STARTED -> ReadStatus.READING
            else -> current
        }
    }
}
