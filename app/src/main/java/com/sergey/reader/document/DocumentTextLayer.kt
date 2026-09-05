package com.sergey.reader.document

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.text.BreakIterator
import java.util.Locale

/** UTF-16 offsets address exactly [DocumentTextPage.text], in backend order, without RTL rewriting. */
data class DocumentGlyph(val start: Int, val end: Int, val bounds: DRect)
data class PdfTextWord(val text: String, val bounds: DRect, val pageIndex: Int, val start: Int, val end: Int)

data class DocumentTextPage(
    val pageIndex: Int,
    val text: String,
    val glyphs: List<DocumentGlyph>,
    /** True when the page was produced by OCR rather than the document's native text layer. */
    val recognized: Boolean = false,
) {
    val words: List<PdfTextWord> by lazy {
        val iterator = BreakIterator.getWordInstance(Locale.ROOT).apply { setText(text) }
        val out = mutableListOf<PdfTextWord>()
        var glyphIndex = 0
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            if (text.substring(start, end).any { !it.isWhitespace() }) {
                while (glyphIndex < glyphs.size && glyphs[glyphIndex].end <= start) glyphIndex++
                var i = glyphIndex
                var rect: DRect? = null
                while (i < glyphs.size && glyphs[i].start < end) {
                    rect = rect?.union(glyphs[i].bounds) ?: glyphs[i].bounds
                    i++
                }
                rect?.let { out += PdfTextWord(text.substring(start, end), it, pageIndex, start, end) }
            }
            start = end
            end = iterator.next()
        }
        out
    }

    /** Glyphs are emitted in text order, so find the first intersecting glyph in O(log n). */
    fun bounds(start: Int, end: Int): List<DRect> {
        if (end <= start || glyphs.isEmpty()) return emptyList()
        var low = 0
        var high = glyphs.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (glyphs[mid].end <= start) low = mid + 1 else high = mid
        }
        val out = mutableListOf<DRect>()
        while (low < glyphs.size && glyphs[low].start < end) {
            out += glyphs[low].bounds
            low++
        }
        return out
    }

    fun wordAt(p: DPoint): PdfTextWord? {
        val glyph = glyphs.firstOrNull { it.bounds.contains(p) } ?: return null
        return words.firstOrNull { it.end > glyph.start && it.start < glyph.end }
    }

    fun nearestWord(p: DPoint) = wordAt(p) ?: words.minByOrNull {
        val c = it.bounds.center
        (c.x - p.x) * (c.x - p.x) + (c.y - p.y) * (c.y - p.y)
    }
}

data class DocumentSelection(
    val page: DocumentTextPage,
    val start: Int,
    val end: Int,
    val following: List<DocumentSelection> = emptyList(),
) {
    /** Ordered page fragments. The first fragment never recursively contains [following]. */
    val parts: List<DocumentSelection>
        get() = listOf(if (following.isEmpty()) this else copy(following = emptyList())) + following

    val text: String
        get() = parts.joinToString("\n") { part ->
            val from = part.start.coerceIn(0, part.page.text.length)
            val to = part.end.coerceIn(from, part.page.text.length)
            part.page.text.substring(from, to)
        }

    val bounds by lazy { page.bounds(start, end) }
}

/** Endpoints are UTF-16 positions in backend order; rectangles never determine RTL order. */
data class DocumentEndpoint(val page: Int, val offset: Int) : Comparable<DocumentEndpoint> {
    override fun compareTo(other: DocumentEndpoint): Int =
        compareValuesBy(this, other, { it.page }, { it.offset })
}

object DocumentSelectionRange {
    /** Keeps Clipboard/Intent payloads comfortably below Android Binder's transaction ceiling. */
    const val MAX_CHARACTERS = 200_000
    private const val MAX_GEOMETRY_BYTES = 16_000_000L

    suspend fun load(
        a: DocumentEndpoint,
        b: DocumentEndpoint,
        read: suspend (Int) -> DocumentTextPage,
    ): DocumentSelection {
        val start = minOf(a, b)
        val end = maxOf(a, b)
        require(start.page >= 0) { "Некорректная страница выделения" }

        val parts = mutableListOf<DocumentSelection>()
        var characters = 0
        var geometry = 0L
        for (index in start.page..end.page) {
            currentCoroutineContext().ensureActive()
            val page = read(index)
            val from = if (index == start.page) start.offset.coerceIn(0, page.text.length) else 0
            val to = if (index == end.page) end.offset.coerceIn(from, page.text.length) else page.text.length
            characters += (to - from) + if (parts.isEmpty()) 0 else 1 // newline inserted by DocumentSelection.text
            val selectedBounds = page.bounds(from, to)
            geometry += selectedBounds.size * 64L
            require(characters <= MAX_CHARACTERS && geometry <= MAX_GEOMETRY_BYTES) {
                "Выделение слишком велико для буфера обмена. Выберите меньший диапазон."
            }
            parts += DocumentSelection(page, from, to)
        }
        require(parts.isNotEmpty()) { "Пустой диапазон выделения" }
        return parts.first().copy(following = parts.drop(1))
    }
}

data class DocumentSearchHit(
    val page: Int,
    val start: Int,
    val end: Int,
    val snippet: String,
    val bounds: List<DRect>,
)

object DocumentSearch {
    fun hit(page: DocumentTextPage, index: Int, length: Int): DocumentSearchHit {
        var a = (index - 45).coerceAtLeast(0)
        var b = (index + length + 75).coerceAtMost(page.text.length)
        if (a > 0 && page.text[a].isLowSurrogate()) a--
        if (b < page.text.length && b > 0 && page.text[b - 1].isHighSurrogate()) b++
        return DocumentSearchHit(
            page.pageIndex,
            index,
            index + length,
            page.text.substring(a, b),
            page.bounds(index, index + length),
        )
    }

    /** Bounded list for the search sheet. Adjacent navigation is unbounded and streaming. */
    fun find(page: DocumentTextPage, query: String, limit: Int = 500): List<DocumentSearchHit> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val out = mutableListOf<DocumentSearchHit>()
        var from = 0
        while (from < page.text.length && out.size < limit) {
            val index = page.text.indexOf(query, from, ignoreCase = true)
            if (index < 0) break
            out += hit(page, index, query.length)
            from = index + query.length.coerceAtLeast(1)
        }
        return out
    }
}

interface OcrEngine {
    suspend fun recognize(pageIndex: Int, pageSize: DSize, image: android.graphics.Bitmap): DocumentTextPage
}
