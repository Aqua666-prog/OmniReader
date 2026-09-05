package com.sergey.reader.document

import org.junit.Assert.*
import org.junit.Test

class DocumentGeometryTest {
    private val pages = listOf(DSize(600.0,800.0),DSize(800.0,600.0))
    @Test fun continuousUsesEntireWidthAndOriginalAspect() {
        val l=DocumentLayout.create(pages,1080.0,4.0,DocumentMode.CONTINUOUS,0)
        assertEquals(1080.0,l.pages[0].bounds.width,0.0)
        assertEquals(1440.0,l.pages[0].bounds.height,0.0)
        assertEquals(4.0,l.pages[1].bounds.top-l.pages[0].bounds.bottom,0.0)
        assertEquals(810.0,l.pages[1].bounds.height,0.0)
    }
    @Test fun pageModeHasNoNeighbourAndSpreadFitsBoth() {
        assertEquals(listOf(1),DocumentLayout.create(pages,1000.0,4.0,DocumentMode.PAGE,1).pages.map{it.index})
        val spread=DocumentLayout.create(pages,1000.0,4.0,DocumentMode.SPREAD,1)
        assertEquals(2,spread.pages.size)
        assertEquals(1000.0,spread.pages.last().bounds.right,1e-8)
        assertEquals(spread.pages[0].bounds.height,spread.pages[1].bounds.height,1e-8)
    }
    @Test fun zoomKeepsPointUnderFingersAndClamps() {
        val original=DocumentTransform(2.0,420.0,1600.0)
        val focus=DPoint(310.0,520.0)
        val document=original.toDocument(focus)
        for (zoom in listOf(.001,1.0,7.3,15.0,1000.0)) {
            val next=original.zoomAt(focus,zoom,1.0,15.0)
            assertTrue(next.zoom in 1.0..15.0)
            assertEquals(document.x,next.toDocument(focus).x,1e-8)
            assertEquals(document.y,next.toDocument(focus).y,1e-8)
        }
    }
    @Test fun panNeverExposesBeyondLargeDocumentAndCentersSmallPage() {
        val large=DocumentTransform(10.0,-900.0,1e12).constrained(DSize(600.0,800.0),DSize(1080.0,1920.0))
        assertEquals(0.0,large.scrollX,0.0)
        assertEquals(6080.0,large.scrollY,0.0)
        val small=DocumentTransform().constrained(DSize(600.0,800.0),DSize(1080.0,1920.0))
        assertEquals(-240.0,small.scrollX,0.0)
        assertEquals(-560.0,small.scrollY,0.0)
    }
    @Test fun pageCoordinatesRoundTripAtFifteenTimes() {
        val l=DocumentLayout.create(pages,1080.0,4.0,DocumentMode.CONTINUOUS,0)
        val t=DocumentTransform(15.0,4000.0,23000.0)
        val word=DPoint(234.25,198.75)
        val restored=l.pages[1].toPage(t.toDocument(t.toScreen(l.pages[1].toLayout(word))))
        assertEquals(word.x,restored.x,1e-8);assertEquals(word.y,restored.y,1e-8)
    }
    @Test fun progressUsesPagesAndRestorationRejectsInvalidValues() {
        assertEquals(27f/842,DocumentTransform.progress(26,842),1e-7f)
        assertEquals(1f,DocumentTransform.progress(841,842),0f)
        assertEquals(0f,DocumentTransform.progress(0,0),0f)
        val a=DocumentTransform.sanitize(DocumentAnchor(900,Double.NaN,-3.0,1000.0),842,15.0)
        assertEquals(DocumentAnchor(841,.5,0.0,15.0),a)
        val valid=DocumentAnchor(26,.72,.33,11.5)
        assertEquals(valid,DocumentTransform.sanitize(valid,842,15.0))
    }
    @Test fun restoredAnchorSurvivesPortraitToLandscape() {
        val a=DocumentAnchor(1,.7,.6,4.0)
        for(width in listOf(1080.0,1920.0)) {
            val p=DocumentLayout.create(pages,width,4.0,DocumentMode.CONTINUOUS,1).pages[1]
            val target=p.toLayout(DPoint(a.x*p.pageSize.width,a.y*p.pageSize.height))
            val t=DocumentTransform(a.zoom,target.x*a.zoom-500,target.y*a.zoom-350)
            val restored=p.toPage(t.toDocument(DPoint(500.0,350.0)))
            assertEquals(a.x,restored.x/p.pageSize.width,1e-8)
            assertEquals(a.y,restored.y/p.pageSize.height,1e-8)
        }
    }
    @Test fun wholePageFitRespectsBothAxes() {
        assertEquals(.5,DocumentTransform.fitPage(DSize(600.0,1600.0),DSize(600.0,800.0)),0.0)
    }
    @Test fun twoThousandPagesGenerateOnlyViewportTiles() {
        val l=DocumentLayout.create(List(2001){DSize(600.0,800.0)},1080.0,4.0,DocumentMode.CONTINUOUS,0)
        val p=l.pages[1500]
        val t=DocumentTransform(15.0,5000.0,p.bounds.top*15+5000)
        val tiles=DocumentTiles.visible(p,t,DSize(1080.0,1920.0))
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.size<=20)
        assertTrue(tiles.all {it.width in 1..512 && it.height in 1..512 && it.fullWidth>=16200})
        assertTrue(DocumentTiles.visible(l.pages[0],t,DSize(1080.0,1920.0)).isEmpty())
        assertTrue(tiles.sumOf{it.width.toLong()*it.height*4}<20L*1024*1024)
    }
}
