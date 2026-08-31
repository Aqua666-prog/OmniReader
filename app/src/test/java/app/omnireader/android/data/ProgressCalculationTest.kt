package app.omnireader.android.data

import app.omnireader.android.core.model.ReadStatus
import app.omnireader.android.data.repository.LibraryRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressCalculationTest {
    @Test fun firstProgressMovesToReading() {
        assertEquals(ReadStatus.READING, LibraryRepository.statusForProgress(ReadStatus.NOT_STARTED, 0.1f))
    }

    @Test fun nearEndMarksCompleted() {
        assertEquals(ReadStatus.COMPLETED, LibraryRepository.statusForProgress(ReadStatus.READING, 0.999f))
    }

    @Test fun explicitDroppedStatusIsPreservedBeforeEnd() {
        assertEquals(ReadStatus.DROPPED, LibraryRepository.statusForProgress(ReadStatus.DROPPED, 0.4f))
    }
}
