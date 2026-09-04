package com.sergey.reader.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.sergey.reader.model.ReaderBlock

/** A block fragment that fits on one physical reflowable page. */
data class PageSlice(
    val blockIndex: Int,
    val startOffset: Int = 0,
    val endOffsetExclusive: Int = Int.MAX_VALUE
)

data class ExactReaderPage(val slices: List<PageSlice>) {
    val startBlock: Int get() = slices.firstOrNull()?.blockIndex ?: 0
    val endBlockInclusive: Int get() = slices.lastOrNull()?.blockIndex ?: startBlock
}

data class PaginationStyles(
    val paragraph: TextStyle,
    val footnote: TextStyle,
    val chapter: TextStyle,
    val paragraphSpacingPx: Int,
    val footnoteSpacingPx: Int,
    val chapterSpacingPx: Int
)

/**
 * Typography-aware pagination driven by Compose's own TextMeasurer.
 *
 * Unlike the old character-budget heuristic, page boundaries come from the exact
 * line layout produced for the active font, font size, line height, width and
 * alignment. Long paragraphs are split only at measured line ends, while the
 * offsets keep selections/highlights anchored to the original source block.
 */
object ExactPaginator {
    fun paginate(
        blocks: List<ReaderBlock>,
        measurer: TextMeasurer,
        widthPx: Int,
        heightPx: Int,
        styles: PaginationStyles
    ): List<ExactReaderPage> {
        if (blocks.isEmpty() || widthPx <= 0 || heightPx <= 0) return emptyList()

        val pages = mutableListOf<ExactReaderPage>()
        val current = mutableListOf<PageSlice>()
        var remaining = heightPx

        fun flush() {
            if (current.isNotEmpty()) {
                pages += ExactReaderPage(current.toList())
                current.clear()
            }
            remaining = heightPx
        }

        blocks.forEachIndexed { blockIndex, block ->
            when (block.kind) {
                ReaderBlock.Kind.PDF_TEXT, ReaderBlock.Kind.DJVU_TEXT -> Unit // searchable/TTS layers; never occupy visual space
                ReaderBlock.Kind.IMAGE, ReaderBlock.Kind.PDF_PAGE, ReaderBlock.Kind.DJVU_PAGE -> {
                    flush()
                    pages += ExactReaderPage(listOf(PageSlice(blockIndex)))
                    remaining = heightPx
                }
                else -> {
                    val text = block.text
                    if (text.isBlank()) return@forEachIndexed
                    var start = 0
                    while (start < text.length) {
                        val baseStyle = when (block.kind) {
                            ReaderBlock.Kind.CHAPTER -> styles.chapter
                            ReaderBlock.Kind.FOOTNOTE -> styles.footnote
                            else -> styles.paragraph
                        }
                        val style = if (start > 0 && block.kind == ReaderBlock.Kind.PARAGRAPH) {
                            baseStyle.copy(textIndent = TextIndent(firstLine = 0.sp))
                        } else baseStyle
                        val spacing = when (block.kind) {
                            ReaderBlock.Kind.CHAPTER -> styles.chapterSpacingPx
                            ReaderBlock.Kind.FOOTNOTE -> styles.footnoteSpacingPx
                            else -> styles.paragraphSpacingPx
                        }

                        val rest = text.substring(start)
                        val layout = measurer.measure(
                            text = AnnotatedString(rest),
                            style = style,
                            constraints = Constraints(maxWidth = widthPx)
                        )
                        val fullHeight = layout.size.height + spacing

                        if (fullHeight <= remaining) {
                            current += PageSlice(blockIndex, start, text.length)
                            remaining -= fullHeight
                            start = text.length
                            continue
                        }

                        // Keep ordinary chapter headings intact when they fit on a fresh page.
                        if (block.kind == ReaderBlock.Kind.CHAPTER && start == 0 && fullHeight <= heightPx && current.isNotEmpty()) {
                            flush()
                            continue
                        }

                        // If even one line does not fit in the remainder, begin a clean page.
                        var lastFittingLine = -1
                        val availableForText = (remaining - spacing.coerceAtMost(remaining / 3)).coerceAtLeast(1)
                        for (line in 0 until layout.lineCount) {
                            if (layout.getLineBottom(line) <= availableForText) lastFittingLine = line else break
                        }

                        if (lastFittingLine < 0 && current.isNotEmpty()) {
                            flush()
                            continue
                        }

                        // A single extremely large line still has to make forward progress.
                        val forcedLine = if (lastFittingLine >= 0) lastFittingLine else 0
                        var endInRest = layout.getLineEnd(forcedLine, visibleEnd = true)
                        if (endInRest <= 0) endInRest = minOf(rest.length, 1)
                        var absoluteEnd = (start + endInRest).coerceIn(start + 1, text.length)

                        // Keep trailing spaces out of the next page without changing visible text.
                        while (absoluteEnd < text.length && text[absoluteEnd].isWhitespace()) absoluteEnd++
                        current += PageSlice(blockIndex, start, absoluteEnd)
                        start = absoluteEnd
                        flush()
                    }
                }
            }
        }
        flush()
        return pages
    }

    fun pageForBlock(pages: List<ExactReaderPage>, blockIndex: Int): Int = pageForPosition(pages, blockIndex, 0)

    fun pageForPosition(pages: List<ExactReaderPage>, blockIndex: Int, offset: Int): Int {
        if (pages.isEmpty()) return 0
        val safeOffset = offset.coerceAtLeast(0)
        val exact = pages.indexOfFirst { page ->
            page.slices.any { slice ->
                slice.blockIndex == blockIndex &&
                    (slice.endOffsetExclusive == Int.MAX_VALUE || safeOffset in slice.startOffset until slice.endOffsetExclusive)
            }
        }
        if (exact >= 0) return exact
        val firstForBlock = pages.indexOfFirst { page -> page.slices.any { it.blockIndex == blockIndex } }
        if (firstForBlock >= 0) return firstForBlock
        // Hidden fixed-layout text blocks belong to the preceding rendered PDF/DjVu page.
        val previous = pages.indexOfLast { it.endBlockInclusive < blockIndex }
        return previous.coerceAtLeast(0)
    }
}
