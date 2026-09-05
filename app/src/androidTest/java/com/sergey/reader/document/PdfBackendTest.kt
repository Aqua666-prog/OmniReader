package com.sergey.reader.document

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real PDFium/JNI tests. Fixtures are generated locally; no network or OCR. */
@RunWith(AndroidJUnit4::class)
class PdfBackendTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(count: Int, scanned: Boolean=false): File {
        val file=File.createTempFile("viewer-", ".pdf",context.cacheDir)
        PdfDocument().use { pdf ->
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=18f}
            repeat(count) { index ->
                val page=pdf.startPage(PdfDocument.PageInfo.Builder(600,800,index+1).create())
                if(scanned) {
                    val image=Bitmap.createBitmap(300,100,Bitmap.Config.ARGB_8888)
                    image.eraseColor(Color.WHITE)
                    android.graphics.Canvas(image).drawText("IMAGE ONLY",10f,50f,paint)
                    page.canvas.drawBitmap(image,40f,40f,null)
                    image.recycle()
                } else {
                    page.canvas.drawText("Hello PDF ${index+1}",40f,80f,paint)
                    page.canvas.drawText("Русский שלום ייִדיש العربية",40f,120f,paint)
                    page.canvas.drawText("Left column",40f,200f,paint)
                    page.canvas.drawText("Right column",330f,200f,paint)
                }
                pdf.finishPage(page)
            }
            file.outputStream().use{pdf.writeTo(it)}
        }
        return file
    }
    @Test fun glyphCoordinatesMatchSharpRegionRender() {
        val file=fixture(1)
        try { PdfDocumentBackend(context,Uri.fromFile(file)).use { backend ->
            val text=backend.text(0)
            assertTrue(text.text.contains("Hello PDF"))
            assertTrue(text.text.contains("Русский"))
            assertTrue(text.text.any{it in '\u0590'..'\u05ff'})
            val word=text.words.first{it.text=="Hello"}
            assertTrue(word.bounds.left in 35.0..50.0)
            assertTrue(word.bounds.top in 55.0..85.0)
            val z=15
            val x=(word.bounds.left*z).toInt().coerceAtLeast(0)
            val y=(word.bounds.top*z).toInt().coerceAtLeast(0)
            val tile=backend.render(TileKey(0,600*z,800*z,x,y,512,512))
            try {
                assertEquals(512,tile.width)
                assertTrue(tile.allocationByteCount<=512*512*4)
                val pixels=IntArray(512*512);tile.getPixels(pixels,0,512,0,0,512,512)
                assertTrue("Text glyph must overlap its document-space box",pixels.count{Color.red(it)<100}>50)
                assertTrue("Region must also include white page background",pixels.count{Color.red(it)>245}>50)
            } finally {tile.recycle()}
        }} finally {file.delete()}
    }
    @Test fun scanRendersWithoutPretendingItHasText() {
        val file=fixture(1,true)
        try {PdfDocumentBackend(context,Uri.fromFile(file)).use { backend ->
            assertTrue(backend.text(0).text.isBlank())
            val tile=backend.render(TileKey(0,600,800,0,0,512,512))
            try {assertEquals(512,tile.width)} finally {tile.recycle()}
        }} finally {file.delete()}
    }
    @Test fun sixHundredPagesCanSeekDirectlyToLastPage() {
        val file=fixture(600)
        try {PdfDocumentBackend(context,Uri.fromFile(file)).use { backend ->
            assertEquals(600,backend.sizes.size)
            assertTrue(backend.text(599).text.contains("Hello PDF 600"))
            val tile=backend.render(TileKey(599,600,800,0,0,512,512))
            tile.recycle()
        }} finally {file.delete()}
    }
}
