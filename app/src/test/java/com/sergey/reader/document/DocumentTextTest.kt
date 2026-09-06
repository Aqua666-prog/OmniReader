package com.sergey.reader.document

import org.junit.Assert.*
import org.junit.Test

class DocumentTextTest {
    private fun page(text: String): DocumentTextPage {
        val glyphs=mutableListOf<DocumentGlyph>()
        var index=0
        while(index<text.length) {
            val end=index+Character.charCount(text.codePointAt(index))
            glyphs+=DocumentGlyph(index,end,DRect(index*10.0,20.0,end*10.0,32.0))
            index=end
        }
        return DocumentTextPage(7,text,glyphs)
    }
    @Test fun copyPreservesBackendUnicodeOrder() {
        val original="Русский שלום ייִדיש العربية ABC 😀"
        val p=page(original)
        assertEquals(original,DocumentSelection(p,0,original.length).text)
        val hits=DocumentSearch.find(p,"שלום")
        assertEquals(1,hits.size)
        assertEquals("שלום",p.text.substring(hits[0].start,hits[0].end))
        assertEquals(7,hits[0].page)
        assertFalse(hits[0].bounds.isEmpty())
    }
    @Test fun selectionHitTestUsesPageCoordinatesAfterZoomAndPan() {
        val p = page("alpha beta")
        assertEquals(listOf("alpha", "beta"), p.words.map { it.text })

        val placement = PagePlacement(
            7,
            DRect(0.0, 800.0, 1200.0, 2400.0),
            DSize(600.0, 800.0),
        )
        val transform = DocumentTransform(12.0, 250.0, 19000.0)
        val original = DPoint(72.0, 25.0)
        val screen = transform.toScreen(placement.toLayout(original))
        val restored = placement.toPage(transform.toDocument(screen))

        assertEquals(original.x, restored.x, 1e-9)
        assertEquals(original.y, restored.y, 1e-9)
        val hit = requireNotNull(p.wordAt(restored))
        assertEquals("beta", hit.text)
        assertEquals("beta", DocumentSelection(p, hit.start, hit.end).text)
    }
    @Test fun unicodeWordHitTestingUsesTextLayerOffsets() {
        val p = page("Русский שלום ייִדיש العربية ABC 😀")
        for (expected in listOf("Русский", "שלום", "ייִדיש", "العربية", "ABC", "😀")) {
            val start = p.text.indexOf(expected)
            assertTrue("Missing fixture token: $expected", start >= 0)
            val glyph = p.glyphs.first { it.start == start }
            assertEquals(expected, p.wordAt(glyph.bounds.center)?.text)
        }
    }

    @Test fun scannedPageDoesNotInventText() {
        val scan=DocumentTextPage(0,"",emptyList())
        assertNull(scan.wordAt(DPoint(10.0,10.0)))
        assertTrue(DocumentSearch.find(scan,"word").isEmpty())
    }
    @Test fun supplementaryCharacterRemainsCompleteInSearchSnippet() {
        val p=page("x".repeat(44)+"😀"+"z".repeat(44)+"needle")
        val snippet=DocumentSearch.find(p,"needle").single().snippet
        assertFalse(snippet.first().isLowSurrogate())
        assertFalse(snippet.last().isHighSurrogate())
    }
}
