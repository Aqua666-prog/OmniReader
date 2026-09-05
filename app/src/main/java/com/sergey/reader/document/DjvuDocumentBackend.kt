package com.sergey.reader.document

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.github.axet.djvulibre.DjvuLibre

/** Uses the original pinned native library. DjVu rectangles have a bottom-left origin. */
class DjvuDocumentBackend(context: Context, uri: Uri) : DocumentBackend {
    private val descriptor = openDescriptor(context, uri)
    private val document = try { DjvuLibre(descriptor.fileDescriptor) }
        catch (error: Throwable) { descriptor.close(); throw error }
    override val sizes: List<DSize> = try {
        document.setCacheSize(24 * 1024 * 1024)
        List(document.pagesCount) { index -> document.getPageInfo(index).let { DSize(it.width.toDouble(),it.height.toDouble()) } }
    } catch (error: Throwable) { document.close(); descriptor.close(); throw error }

    override fun render(tile: TileKey): Bitmap {
        require(tile.width in 1..512 && tile.height in 1..512)
        val bitmap = Bitmap.createBitmap(tile.width,tile.height,Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.WHITE)
            // JNI passes all/dst to DjVuImage::get_pixmap. `all` describes the scaled
            // whole page; `dst` is its bottom-origin tile. JNI flips rows into Android.
            document.renderPage(bitmap,tile.page,0,0,tile.fullWidth,tile.fullHeight,
                tile.x,tile.fullHeight-tile.y-tile.height,tile.width,tile.height)
            return bitmap
        } catch (error: Throwable) { bitmap.recycle(); throw error }
    }
    override fun text(page: Int): DocumentTextPage {
        val size = sizes[page]
        val zones = document.getText(page,DjvuLibre.ZONE_WORD)
        val text = StringBuilder()
        val glyphs = mutableListOf<DocumentGlyph>()
        zones?.text.orEmpty().forEachIndexed { index, word ->
            val value = word.orEmpty()
            val start = text.length
            text.append(value)
            val b = zones?.bounds?.getOrNull(index)
            if (b != null && value.isNotEmpty()) glyphs += DocumentGlyph(start,text.length,
                DRect(b.left.toDouble(),size.height-b.bottom,b.right.toDouble(),size.height-b.top))
            // Preserve word bytes and order; insert only the absent word separator.
            if (value.isNotEmpty() && !value.last().isWhitespace()) text.append(' ')
        }
        return DocumentTextPage(page,text.toString(),glyphs)
    }
    override fun close() { try { document.close() } finally { runCatching { descriptor.close() } } }
}
