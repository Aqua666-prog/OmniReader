package app.omnireader.android.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.omnireader.android.core.model.ContentType
import app.omnireader.android.core.model.FileFormat
import app.omnireader.android.core.model.ReadStatus
import app.omnireader.android.data.db.AppDatabase
import app.omnireader.android.data.db.LibraryItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryDaoScannerMergeTest {
    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test fun rescanPreservesProgressAndMovedFileKeepsIdentity() = runTest {
        val dao = db.libraryDao()
        val first = sample("content://old/book.epub", "fp", "scan-1")
        val id = dao.upsertScanned(first)
        dao.updateProgress(id, 2, null, 1234, 0.42f, 1000, ReadStatus.READING)

        val movedId = dao.upsertScanned(sample("content://new/renamed.epub", "fp", "scan-2"))
        dao.markUnseenMissing("content://root", "scan-2")
        val restored = dao.getById(movedId)!!
        assertEquals(id, movedId)
        assertEquals(0.42f, restored.progress, 0.0001f)
        assertEquals(2, restored.currentChapter)
        assertEquals(ReadStatus.READING, restored.readStatus)
        assertEquals("content://new/renamed.epub", restored.uri)
    }

    private fun sample(uri: String, fingerprint: String, scanToken: String) = LibraryItemEntity(
        uri = uri,
        fileName = "book.epub",
        format = FileFormat.EPUB,
        mimeType = "application/epub+zip",
        contentType = ContentType.BOOK,
        title = "Book",
        sourceFolderUri = "content://root",
        contentFingerprint = fingerprint,
        lastSeenScanToken = scanToken,
    )
}
