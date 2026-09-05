package com.sergey.reader.document

import kotlin.math.*

data class TileKey(val page: Int, val fullWidth: Int, val fullHeight: Int, val x: Int, val y: Int, val width: Int, val height: Int, val preview: Boolean = false) {
    val normalizedBounds get() = DRect(x.toDouble()/fullWidth, y.toDouble()/fullHeight, (x+width).toDouble()/fullWidth, (y+height).toDouble()/fullHeight)
}
object DocumentTiles {
    const val TILE = 512
    const val MAX_VISIBLE_TILES = 96
    fun preview(page: PagePlacement): TileKey {
        val scale = 512.0 / max(page.pageSize.width, page.pageSize.height)
        val w = max(1, (page.pageSize.width * scale).roundToInt()); val h = max(1, (page.pageSize.height * scale).roundToInt())
        return TileKey(page.index,w,h,0,0,w,h,true)
    }
    fun visible(page: PagePlacement, transform: DocumentTransform, viewport: DSize): List<TileKey> {
        val screen = transform.rectToScreen(page.bounds)
        if (!screen.intersects(DRect(0.0,0.0,viewport.width,viewport.height))) return emptyList()
        val requestedWidth = (ceil(screen.width / 64.0) * 64).coerceIn(64.0,65536.0)
        val aspect = page.pageSize.height / page.pageSize.width
        // Bound both native raster axes, including unusually tall engineering sheets.
        val width = min(requestedWidth,65536.0/aspect).toInt().coerceIn(1,65536)
        val height = (width*aspect).roundToInt().coerceIn(1,65536)
        val x0 = floor(((0.0-screen.left)/screen.width).coerceIn(0.0,1.0)*width/TILE).toInt()
        val y0 = floor(((0.0-screen.top)/screen.height).coerceIn(0.0,1.0)*height/TILE).toInt()
        val x1 = floor(((viewport.width-screen.left)/screen.width).coerceIn(0.0,1.0)*width/TILE).toInt()
        val y1 = floor(((viewport.height-screen.top)/screen.height).coerceIn(0.0,1.0)*height/TILE).toInt()
        val tiles = mutableListOf<TileKey>()
        for (y in y0..min(y1, (height-1)/TILE)) for (x in x0..min(x1,(width-1)/TILE)) {
            if (tiles.size == MAX_VISIBLE_TILES) return tiles
            tiles += TileKey(page.index,width,height,x*TILE,y*TILE,min(TILE,width-x*TILE),min(TILE,height-y*TILE))
        }
        return tiles
    }
}
