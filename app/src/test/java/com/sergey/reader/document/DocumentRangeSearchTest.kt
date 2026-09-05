package com.sergey.reader.document

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DocumentRangeSearchTest {
    private fun page(index: Int,text: String)=DocumentTextPage(index,text,listOf(DocumentGlyph(0,text.length,DRect(0.0,0.0,100.0,20.0))))
    @Test fun rangeCrossesPagesInBackendOrderAndReverseDragMatches() = runBlocking {
        val pages=listOf(page(0,"abc שלום"),page(1,"Русский 😀"),page(2,"العربية xyz"))
        val a=DocumentEndpoint(0,4);val b=DocumentEndpoint(2,7)
        val selected=DocumentSelectionRange.load(a,b){pages[it]}
        assertEquals("שלום\nРусский 😀\nالعربية",selected.text)
        assertEquals(listOf(0,1,2),selected.parts.map{it.page.pageIndex})
        assertEquals(selected,DocumentSelectionRange.load(b,a){pages[it]})
        assertTrue(selected.parts.all{it.bounds.isNotEmpty()})
    }
    @Test fun oversizedClipboardSelectionFailsExplicitly() = runBlocking {
        try {
            DocumentSelectionRange.load(DocumentEndpoint(0,0),DocumentEndpoint(1,150000)){page(it,"x".repeat(150000))}
            fail("Must reject oversized Clipboard transaction")
        } catch(expected: IllegalArgumentException){assertTrue(expected.message!!.contains("буфера"))}
    }
    @Test fun nextPreviousWrapAcrossTwoThousandPagesWithoutRendering() = runBlocking {
        var reads=0
        val read: suspend(Int)->DocumentTextPage={i->reads++;page(i,if(i==0||i==2000) "needle" else "other")}
        val first=DocumentSearch.hit(read(0),0,6)
        val last=DocumentSearchNavigator.adjacent(2001,"needle",first,-1,read)!!
        assertEquals(2000,last.page)
        assertTrue(reads<=3)
        assertEquals(0,DocumentSearchNavigator.adjacent(2001,"needle",last,1,read)!!.page)
    }
    @Test fun navigationContinuesBeyondFiveHundredMatchesOnOnePage() = runBlocking {
        val p=page(0,"x ".repeat(1500))
        val current=DocumentSearch.hit(p,1200,1)
        assertEquals(1202,DocumentSearchNavigator.adjacent(1,"x",current,1,read={p})!!.start)
        assertEquals(1198,DocumentSearchNavigator.adjacent(1,"x",current,-1,read={p})!!.start)
    }
    @Test fun rangeCancellationDoesNotContinueThroughDocument() = runBlocking {
        var reads=0
        val work=launch {
            DocumentSelectionRange.load(DocumentEndpoint(0,0),DocumentEndpoint(2000,1)) {
                reads++;yield();page(it,"x")
            }
        }
        yield();work.cancelAndJoin()
        assertTrue(reads<2001)
    }
    @Test fun navigationSkipsUnreadablePagesButDoesNotHidePermissionLoss() = runBlocking {
        val current=DocumentSearch.hit(page(0,"needle"),0,6)
        var unreadable=0
        val hit=DocumentSearchNavigator.adjacent(4,"needle",current,1,read={ index ->
            when(index){
                1 -> error("broken page")
                2 -> page(2,"needle")
                else -> page(index,"other")
            }
        },onUnreadable={unreadable++})
        assertEquals(2,hit?.page)
        assertEquals(1,unreadable)
        try {
            DocumentSearchNavigator.adjacent(2,"needle",current,1,read={ throw SecurityException() })
            fail("SecurityException must not be swallowed")
        } catch(_: SecurityException) { }
    }

    @Test fun errorsDistinguishPermissionsEncryptionAndEmptyDocuments() {
        assertTrue(DocumentErrors.message(SecurityException()).contains("доступа"))
        assertTrue(DocumentErrors.message(IllegalArgumentException("password required")).contains("паролем"))
        assertTrue(DocumentErrors.message(IllegalArgumentException("В документе нет страниц")).contains("нет страниц"))
        assertTrue(DocumentErrors.message(java.io.FileNotFoundException()).contains("удалён"))
    }
    @Test fun navigationDoesNotReturnTheSameOnlyHitAfterWrap() = runBlocking {
        val p=page(0,"needle only")
        val current=DocumentSearch.hit(p,0,6)
        assertNull(DocumentSearchNavigator.adjacent(1,"needle",current,1,read={p}))
        assertNull(DocumentSearchNavigator.adjacent(1,"needle",current,-1,read={p}))
    }

    @Test fun clipboardLimitIncludesCrossPageSeparators() = runBlocking {
        try {
            DocumentSelectionRange.load(DocumentEndpoint(0,0),DocumentEndpoint(1,100000)) {
                page(it,"x".repeat(100000))
            }
            fail("Inserted page separator must count toward the Clipboard limit")
        } catch(expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("буфера"))
        }
    }

}
