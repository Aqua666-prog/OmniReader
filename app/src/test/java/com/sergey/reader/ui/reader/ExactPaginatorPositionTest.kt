package com.sergey.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ExactPaginatorPositionTest {
    @Test
    fun pageForPositionUsesFragmentOffsets() {
        val pages = listOf(
            ExactReaderPage(listOf(PageSlice(blockIndex = 3, startOffset = 0, endOffsetExclusive = 100))),
            ExactReaderPage(listOf(PageSlice(blockIndex = 3, startOffset = 100, endOffsetExclusive = 220))),
            ExactReaderPage(listOf(PageSlice(blockIndex = 4, startOffset = 0, endOffsetExclusive = 80)))
        )

        assertEquals(0, ExactPaginator.pageForPosition(pages, 3, 25))
        assertEquals(1, ExactPaginator.pageForPosition(pages, 3, 150))
        assertEquals(2, ExactPaginator.pageForPosition(pages, 4, 10))
    }

    @Test
    fun pageForPositionMapsHiddenBlockToPreviousVisualPage() {
        val pages = listOf(
            ExactReaderPage(listOf(PageSlice(blockIndex = 0))),
            ExactReaderPage(listOf(PageSlice(blockIndex = 2))),
            ExactReaderPage(listOf(PageSlice(blockIndex = 4)))
        )

        // Blocks 1 and 3 can be hidden PDF/DjVu text layers that are not represented visually.
        assertEquals(0, ExactPaginator.pageForPosition(pages, 1, 0))
        assertEquals(1, ExactPaginator.pageForPosition(pages, 3, 0))
    }

    @Test
    fun unboundedSliceAcceptsAnyOffset() {
        val pages = listOf(ExactReaderPage(listOf(PageSlice(blockIndex = 7))))
        assertEquals(0, ExactPaginator.pageForPosition(pages, 7, 50_000))
    }
}
