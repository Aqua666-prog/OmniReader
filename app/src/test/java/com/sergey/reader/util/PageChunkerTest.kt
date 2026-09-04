package com.sergey.reader.util

import com.sergey.reader.model.ReaderBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageChunkerTest {
    @Test
    fun imageGetsOwnPage() {
        val blocks = listOf(
            ReaderBlock(ReaderBlock.Kind.PARAGRAPH, "a".repeat(200), 0, 0),
            ReaderBlock(ReaderBlock.Kind.IMAGE, "", 0, 1, "/tmp/a.jpg"),
            ReaderBlock(ReaderBlock.Kind.PARAGRAPH, "b".repeat(200), 0, 2)
        )
        val pages = PageChunker.chunk(blocks, 1000)
        assertEquals(3, pages.size)
        assertEquals(1, pages[1].startBlock)
        assertEquals(2, pages[1].endBlockExclusive)
    }

    @Test
    fun findsPageForBlock() {
        val blocks = (0 until 10).map { ReaderBlock(ReaderBlock.Kind.PARAGRAPH, "x".repeat(300), 0, it) }
        val pages = PageChunker.chunk(blocks, 900)
        val page = PageChunker.pageForBlock(pages, 7)
        assertTrue(7 in pages[page].startBlock until pages[page].endBlockExclusive)
    }

    @Test
    fun djvuPageGetsOwnPageAndLinkHasTextCost() {
        val blocks = listOf(
            ReaderBlock(ReaderBlock.Kind.LINK, "Глава 2".repeat(20), 0, 0, "chapter:1"),
            ReaderBlock(ReaderBlock.Kind.PARAGRAPH, "x".repeat(300), 0, 1),
            ReaderBlock(ReaderBlock.Kind.DJVU_PAGE, "", 1, 0, "0"),
            ReaderBlock(ReaderBlock.Kind.DJVU_TEXT, "hidden", 1, 1, "0")
        )
        val pages = PageChunker.chunk(blocks, 300)
        assertTrue(pages.any { it.startBlock == 2 && it.endBlockExclusive == 3 })
        assertTrue(pages.size >= 3)
    }
}
