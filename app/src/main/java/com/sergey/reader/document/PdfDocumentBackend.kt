package com.sergey.reader.document

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import io.legere.pdfiumandroid.PdfiumCore
import io.legere.pdfiumandroid.api.Config

/** PDFium 2.0.3: clipped rendering and real glyph boxes on Android 8+, including crop/rotation. */
class PdfDocumentBackend(context: Context, uri: Uri) : DocumentBackend {
    private val descriptor = openDescriptor(context, uri)
    private val document = try { PdfiumCore(context, Config(pageRetentionCount = 3)).newDocument(descriptor) }
        catch (error: Throwable) { descriptor.close(); throw error }
    override val sizes: List<DSize> = try {
        document.getPageSizes(72).map { DSize(it.width.toDouble(), it.height.toDouble()) }
    } catch (error: Throwable) { document.close(); descriptor.close(); throw error }

    override fun render(tile: TileKey): Bitmap {
        require(tile.width in 1..512 && tile.height in 1..512)
        val bitmap = Bitmap.createBitmap(tile.width,tile.height,Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.WHITE)
            requireNotNull(document.openPage(tile.page)).use { page ->
                // Negative origin translates the full raster into a small destination tile.
                // PDFium clips internally: there is no fullWidth × fullHeight bitmap allocation.
                page.renderPageBitmap(bitmap,-tile.x,-tile.y,tile.fullWidth,tile.fullHeight,renderAnnot = true)
            }
            return bitmap
        } catch (error: Throwable) { bitmap.recycle(); throw error }
    }

    override fun text(page: Int): DocumentTextPage = requireNotNull(document.openPage(page)).use { pdfPage ->
        pdfPage.openTextPage().use { textPage ->
            val count = textPage.textPageCountChars()
            require(count <= 250_000) { "Текстовый слой этой страницы слишком велик для выделения" }
            val text = StringBuilder()
            val glyphs = ArrayList<DocumentGlyph>(count.coerceAtLeast(0))
            val size = sizes[page]
            // A fixed 64 samples per document point is a coordinate transform precision,
            // independent of zoom or the resolution of any rendered bitmap.
            val coordinateWidth = (size.width * 64).toInt().coerceAtLeast(1)
            val coordinateHeight = (size.height * 64).toInt().coerceAtLeast(1)
            for (index in 0 until count) {
                val value = textPage.textPageGetText(index,1) ?: error("Не удалось извлечь текстовый слой страницы")
                val start = text.length
                text.append(value)
                val box = textPage.textPageGetCharBox(index)
                if (box != null && value.isNotEmpty()) {
                    val mapped = pdfPage.mapRectToDevice(0,0,coordinateWidth,coordinateHeight,0,box)
                    val rect = DRect(minOf(mapped.left,mapped.right)/64.0,minOf(mapped.top,mapped.bottom)/64.0,maxOf(mapped.left,mapped.right)/64.0,maxOf(mapped.top,mapped.bottom)/64.0)
                    if (rect.width > 0 && rect.height > 0) glyphs += DocumentGlyph(start,text.length,rect)
                }
            }
            DocumentTextPage(page,text.toString(),glyphs)
        }
    }
    override fun close() { try { document.close() } finally { runCatching { descriptor.close() } } }
}
