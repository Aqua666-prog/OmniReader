package com.sergey.reader.document

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sergey.reader.data.db.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentRoomTest {
    @Test fun documentStateSurvivesDatabaseReopenWithoutChangingTextPosition() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="document-state-test.db"
        context.deleteDatabase(name)
        fun open()=Room.databaseBuilder(context,ReaderDatabase::class.java,name).build()
        var db=open()
        try {
            val id=db.bookDao().insert(BookEntity(uri="file:///fixture.pdf",displayName="fixture.pdf",title="Fixture",format="PDF",positionBlock=42,positionOffset=17))
            val saved=DocumentPositionEntity(id,26,.72,.33,12.5,123L)
            db.documentDao().savePosition(saved)
            db.bookDao().updateDocumentProgress(id,27f/842,123L)
            db.close();db=open()
            assertEquals(saved,db.documentDao().position(id))
            db.openHelper.readableDatabase.query("SELECT positionBlock,positionOffset FROM books WHERE id=$id").use { c ->
                assertTrue(c.moveToFirst());assertEquals(42,c.getInt(0));assertEquals(17,c.getInt(1))
            }
            db.openHelper.writableDatabase.execSQL("DELETE FROM books WHERE id=$id")
            assertNull(db.documentDao().position(id))
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun multiPageAnnotationPersistsAndDecodesEveryPage() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,ReaderDatabase::class.java).build()
        try {
            val bookId=db.bookDao().insert(BookEntity(uri="file:///multi.pdf",displayName="multi.pdf",title="Multi",format="PDF"))
            fun textPage(index:Int,text:String)=DocumentTextPage(
                index,text,listOf(DocumentGlyph(0,text.length,DRect(10.0,20.0,90.0,40.0)))
            )
            val first=textPage(2,"alpha")
            val second=textPage(3,"beta")
            val selection=DocumentSelection(first,0,5,listOf(DocumentSelection(second,0,4)))
            val annotationId=DocumentPersistence(db).annotate(bookId,selection,"note")
            val mark=db.documentDao().marks(bookId).first().single()
            assertEquals(annotationId,mark.annotationId)
            assertEquals(2,mark.pageIndex)
            val decoded=DocumentPersistence.decodeMarks(listOf(mark))
            assertEquals(setOf(2,3),decoded.keys)
            assertEquals(DRect(10.0,20.0,90.0,40.0),decoded.getValue(2).single().first)
            assertEquals(DRect(10.0,20.0,90.0,40.0),decoded.getValue(3).single().first)
        } finally { db.close() }
    }

}
