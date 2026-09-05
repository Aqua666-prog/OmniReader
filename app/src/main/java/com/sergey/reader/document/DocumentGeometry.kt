package com.sergey.reader.document

import kotlin.math.*

data class DPoint(val x: Double, val y: Double)
data class DSize(val width: Double, val height: Double) {
    init { require(width.isFinite() && height.isFinite() && width > 0 && height > 0) }
}
data class DRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width get() = right - left
    val height get() = bottom - top
    val center get() = DPoint((left + right) / 2, (top + bottom) / 2)
    fun contains(p: DPoint) = p.x in left..right && p.y in top..bottom
    fun intersects(other: DRect) = left < other.right && right > other.left && top < other.bottom && bottom > other.top
    fun union(other: DRect) = DRect(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))
}
enum class DocumentMode { CONTINUOUS, PAGE, SPREAD }
enum class DocumentOpeningScale { WIDTH, PAGE, LAST }
data class DocumentOptions(
    val mode: DocumentMode = DocumentMode.CONTINUOUS,
    val openingScale: DocumentOpeningScale = DocumentOpeningScale.WIDTH,
    val maxZoom: Double = 15.0,
    val doubleTapZoom: Boolean = true,
    val tapControls: Boolean = true,
    val saveZoom: Boolean = true,
    val highQuality: Boolean = true
)
data class DocumentAnchor(val page: Int = 0, val x: Double = .5, val y: Double = 0.0, val zoom: Double = 1.0)
data class PagePlacement(val index: Int, val bounds: DRect, val pageSize: DSize) {
    fun toLayout(p: DPoint) = DPoint(bounds.left + p.x / pageSize.width * bounds.width, bounds.top + p.y / pageSize.height * bounds.height)
    fun toPage(p: DPoint) = DPoint((p.x - bounds.left) / bounds.width * pageSize.width, (p.y - bounds.top) / bounds.height * pageSize.height)
}
data class DocumentLayout(val pages: List<PagePlacement>, val size: DSize) {
    fun at(p: DPoint): PagePlacement? = pages.firstOrNull { it.bounds.contains(p) }
    fun nearest(p: DPoint): PagePlacement = at(p) ?: pages.minBy { abs(it.bounds.center.y - p.y) + abs(it.bounds.center.x - p.x) }
    companion object {
        fun create(sizes: List<DSize>, width: Double, gap: Double, mode: DocumentMode, activePage: Int): DocumentLayout {
            require(sizes.isNotEmpty() && width > 0)
            val active = activePage.coerceIn(sizes.indices)
            val pages = mutableListOf<PagePlacement>()
            if (mode == DocumentMode.SPREAD) {
                val first = active / 2 * 2
                val indices = (first..min(first + 1, sizes.lastIndex)).toList()
                val rowWidth = indices.sumOf { sizes[it].width / sizes[it].height }
                val height = (width - if (indices.size == 2) gap else 0.0) / rowWidth
                var x = 0.0
                indices.forEach { i -> val w = height * sizes[i].width / sizes[i].height; pages += PagePlacement(i, DRect(x, 0.0, x + w, height), sizes[i]); x += w + gap }
                return DocumentLayout(pages, DSize(width, height))
            }
            var y = 0.0
            val indices = if (mode == DocumentMode.PAGE) listOf(active) else sizes.indices.toList()
            indices.forEach { i ->
                val height = width * sizes[i].height / sizes[i].width
                pages += PagePlacement(i, DRect(0.0, y, width, y + height), sizes[i])
                y += height + gap
            }
            return DocumentLayout(pages, DSize(width, (y - gap).coerceAtLeast(1.0)))
        }
    }
}
/** All transforms use Double and document coordinates; bitmap resolution is never part of saved state. */
data class DocumentTransform(val zoom: Double = 1.0, val scrollX: Double = 0.0, val scrollY: Double = 0.0) {
    fun toScreen(p: DPoint) = DPoint(p.x * zoom - scrollX, p.y * zoom - scrollY)
    fun toDocument(p: DPoint) = DPoint((p.x + scrollX) / zoom, (p.y + scrollY) / zoom)
    fun rectToScreen(r: DRect): DRect { val a = toScreen(DPoint(r.left, r.top)); val b = toScreen(DPoint(r.right, r.bottom)); return DRect(a.x, a.y, b.x, b.y) }
    fun constrained(content: DSize, viewport: DSize): DocumentTransform = copy(
        scrollX = clampAxis(scrollX, content.width * zoom, viewport.width),
        scrollY = clampAxis(scrollY, content.height * zoom, viewport.height))
    fun zoomAt(focus: DPoint, value: Double, minimum: Double, maximum: Double): DocumentTransform {
        val z = if (value.isFinite()) value.coerceIn(minimum, maximum) else zoom
        val anchor = toDocument(focus)
        return DocumentTransform(z, anchor.x * z - focus.x, anchor.y * z - focus.y)
    }
    companion object {
        fun clampAxis(value: Double, extent: Double, viewport: Double): Double =
            if (extent <= viewport) (extent - viewport) / 2 else value.coerceIn(0.0, extent - viewport)
        fun fitPage(page: DSize, viewport: DSize) = min(viewport.width / page.width, viewport.height / page.height)
        fun progress(page: Int, count: Int): Float = if (count <= 0) 0f else ((page + 1).toFloat() / count).coerceIn(0f, 1f)
        fun sanitize(anchor: DocumentAnchor, count: Int, maxZoom: Double) = anchor.copy(
            page = anchor.page.coerceIn(0, (count - 1).coerceAtLeast(0)),
            x = anchor.x.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: .5,
            y = anchor.y.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0,
            zoom = anchor.zoom.takeIf { it.isFinite() }?.coerceIn(.01, maxZoom.coerceIn(10.0, 15.0)) ?: 1.0)
    }
}
