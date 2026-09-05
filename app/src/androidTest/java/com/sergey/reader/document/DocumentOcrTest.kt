package com.sergey.reader.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DocumentOcrTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private class FakeBackend(private val realText: String): DocumentBackend {
        override val sizes=listOf(DSize(600.0,800.0))
        var renders=0
        var closed=false
        override fun text(page: Int)=DocumentTextPage(page,realText,emptyList())
        override fun render(tile: TileKey): Bitmap {renders++;return Bitmap.createBitmap(tile.width,tile.height,Bitmap.Config.ARGB_8888)}
        override fun close(){closed=true}
    }
    @Test fun genuinePdfTextNeverTriggersOcrOrRasterization() = runBlocking {
        val backend=FakeBackend("Русский שלום")
        var calls=0
        val engine=object: OcrEngine {
            override suspend fun recognize(pageIndex: Int,pageSize: DSize,image: Bitmap): DocumentTextPage {calls++;error("OCR must not run")}
        }
        val file=File.createTempFile("native-text-",".pdf",context.cacheDir)
        val session=DocumentSession(backend,engine,DocumentTextStore(context,Uri.fromFile(file)))
        try {assertEquals("Русский שלום",session.text(0).text);assertEquals(0,calls);assertEquals(0,backend.renders)}
        finally {session.close();file.delete()}
        assertTrue(backend.closed)
    }
    @Test fun scannedPageUsesOcrOnceAndPersistsDocumentCoordinates() = runBlocking {
        val file=File.createTempFile("scan-cache-",".pdf",context.cacheDir)
        var calls=0
        val engine=object: OcrEngine {
            override suspend fun recognize(pageIndex: Int,pageSize: DSize,image: Bitmap): DocumentTextPage {
                calls++;assertTrue(image.width.toLong()*image.height<=3_000_000)
                return DocumentTextPage(pageIndex,"OCR word",listOf(DocumentGlyph(0,8,DRect(40.0,60.0,120.0,80.0))),true)
            }
        }
        suspend fun openAndRead(): DocumentTextPage {
            val session=DocumentSession(FakeBackend(""),engine,DocumentTextStore(context,Uri.fromFile(file)))
            try {return session.text(0)}finally{session.close()}
        }
        try {
            assertEquals("OCR word",openAndRead().text)
            val restored=openAndRead()
            assertEquals(1,calls)
            assertEquals(DRect(40.0,60.0,120.0,80.0),restored.glyphs.single().bounds)
            assertTrue(restored.recognized)
        } finally {file.delete()}
    }
    @Test fun realOfflineEngineRecognizesPrintedTextAndMapsBoxes() = runBlocking {
        val bitmap=Bitmap.createBitmap(1600,400,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=90f;typeface=android.graphics.Typeface.DEFAULT_BOLD}
        Canvas(bitmap).drawText("HELLO DOCUMENT",80f,220f,paint)
        try {
            val text=TesseractOcrEngine(context).recognize(0,DSize(800.0,200.0),bitmap)
            assertTrue(text.text.uppercase().contains("HELLO"))
            assertTrue(text.recognized)
            val word=text.words.first{it.text.uppercase()=="HELLO"}
            assertTrue(word.bounds.left in 30.0..65.0)
            assertTrue(word.bounds.top in 45.0..115.0)
            assertTrue(word.bounds.right<=800.0 && word.bounds.bottom<=200.0)
        } finally {bitmap.recycle()}
    }
    @Test fun cancelledOcrDoesNotPublishPartialPage() = runBlocking {
        val entered=CompletableDeferred<Unit>()
        val engine=object: OcrEngine {
            override suspend fun recognize(pageIndex: Int,pageSize: DSize,image: Bitmap): DocumentTextPage {entered.complete(Unit);awaitCancellation()}
        }
        val file=File.createTempFile("cancel-scan-",".pdf",context.cacheDir)
        val store=DocumentTextStore(context,Uri.fromFile(file))
        val session=DocumentSession(FakeBackend(""),engine,store)
        try {
            val job=launch {session.text(0)}
            withTimeout(15000){entered.await()}
            job.cancelAndJoin()
            assertNull(store.read(0))
        } finally {session.close();file.delete()}
    }
}
