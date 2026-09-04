package com.sergey.reader.util

import com.sergey.reader.model.ReaderBlock
import com.sergey.reader.model.ReaderPage

/**
 * Lightweight pagination heuristic for reflowable books.
 *
 * Real EPUB pagination depends on the exact Compose text layout. This class deliberately
 * uses a deterministic character budget so page boundaries stay stable while the same
 * typography/settings are active. Oversized paragraphs remain a single scrollable page;
 * PDF/image blocks always receive their own page.
 */
object PageChunker {
    fun chunk(blocks: List<ReaderBlock>, charBudget: Int): List<ReaderPage> {
        if (blocks.isEmpty()) return emptyList()
        val budget = charBudget.coerceAtLeast(240)
        val pages = mutableListOf<ReaderPage>()
        var start = 0
        var cost = 0

        fun flush(endExclusive: Int) {
            if (endExclusive > start) pages += ReaderPage(start, endExclusive)
            start = endExclusive
            cost = 0
        }

        blocks.forEachIndexed { index, block ->
            val isolated = block.kind == ReaderBlock.Kind.IMAGE || block.kind == ReaderBlock.Kind.PDF_PAGE || block.kind == ReaderBlock.Kind.DJVU_PAGE
            if (isolated) {
                flush(index)
                pages += ReaderPage(index, index + 1)
                start = index + 1
                cost = 0
                return@forEachIndexed
            }

            val blockCost = when (block.kind) {
                ReaderBlock.Kind.CHAPTER -> (block.text.length * 2 + 160).coerceAtLeast(220)
                ReaderBlock.Kind.FOOTNOTE -> (block.text.length * 1.15f).toInt() + 80
                ReaderBlock.Kind.PARAGRAPH, ReaderBlock.Kind.LINK -> block.text.length + 55
                else -> 0
            }

            if (index > start && cost + blockCost > budget) flush(index)
            cost += blockCost
        }
        flush(blocks.size)
        return pages
    }

    fun pageForBlock(pages: List<ReaderPage>, blockIndex: Int): Int {
        if (pages.isEmpty()) return 0
        val hit = pages.indexOfFirst { blockIndex in it.startBlock until it.endBlockExclusive }
        return if (hit >= 0) hit else pages.lastIndex
    }
}
