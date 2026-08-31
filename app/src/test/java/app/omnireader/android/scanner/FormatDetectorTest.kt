package app.omnireader.android.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.omnireader.android.core.model.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FormatDetectorTest {
    private val detector by lazy { FormatDetector(ApplicationProvider.getApplicationContext<Context>().contentResolver) }

    @Test fun recognizesCoreExtensions() {
        assertEquals(FileFormat.EPUB, detector.byExtension("book.epub").format)
        assertEquals(FileFormat.FB2_ZIP, detector.byExtension("novel.fb2.zip").format)
        assertEquals(FileFormat.CBZ, detector.byExtension("manga.cbz").format)
        assertEquals(FileFormat.CBR, detector.byExtension("comic.cbr").format)
        assertEquals(FileFormat.PDF, detector.byExtension("scan.pdf").format)
        assertEquals(FileFormat.DJVU, detector.byExtension("scan.djvu").format)
        assertEquals(FileFormat.DJV, detector.byExtension("scan.djv").format)
        assertEquals(FileFormat.CB7, detector.byExtension("manga.cb7").format)
        assertEquals(FileFormat.CBT, detector.byExtension("manga.cbt").format)
        assertEquals(FileFormat.SEVEN_Z, detector.byExtension("images.7z").format)
        assertEquals(FileFormat.MOBI, detector.byExtension("book.mobi").format)
        assertEquals(FileFormat.AZW3, detector.byExtension("book.azw3").format)
        assertEquals(FileFormat.DOCX, detector.byExtension("document.docx").format)
        assertEquals(FileFormat.ODT, detector.byExtension("document.odt").format)
        assertEquals(FileFormat.RTF, detector.byExtension("document.rtf").format)
        assertEquals(FileFormat.AVIF, detector.byExtension("page.avif").format)
        assertEquals(FileFormat.TIFF, detector.byExtension("scan.tiff").format)
    }
}
