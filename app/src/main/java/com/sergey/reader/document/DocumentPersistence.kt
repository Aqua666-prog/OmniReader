package com.sergey.reader.document

import androidx.room.withTransaction
import com.sergey.reader.data.db.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Application-owned writer survives disposal of a viewer. Only the newest pending viewport is kept. */
class DocumentPersistence(private val db: ReaderDatabase) {
    private data class Save(val bookId: Long,val anchor: DocumentAnchor,val total: Int)
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val updates = Channel<Unit>(Channel.CONFLATED)
    private val pending = java.util.concurrent.ConcurrentHashMap<Long,Save>()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    init { scope.launch {
        for (signal in updates) {
          for ((key, save) in pending.entries.toList()) {
            if (!pending.remove(key,save)) continue
            try {
                db.withTransaction {
                    val a = save.anchor
                    db.documentDao().savePosition(DocumentPositionEntity(save.bookId,a.page,a.x,a.y,a.zoom,System.currentTimeMillis()))
                    db.bookDao().updateDocumentProgress(save.bookId,DocumentTransform.progress(a.page,save.total),System.currentTimeMillis())
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _error.value = "Не удалось сохранить позицию: ${error.message.orEmpty()}" }
          }
        }
    } }
    fun save(bookId: Long,anchor: DocumentAnchor,total: Int) { pending[bookId]=Save(bookId,anchor,total); updates.trySend(Unit) }
    fun clearError() { _error.value = null }
    suspend fun restore(book: BookEntity,legacyBlock: Int?): DocumentAnchor {
        if (legacyBlock != null) {
            val page = db.documentDao().pageForLegacyParagraph(book.id,legacyBlock-1)?.toIntOrNull() ?: 0
            return DocumentAnchor(page)
        }
        db.documentDao().position(book.id)?.let { return DocumentAnchor(it.pageIndex,it.centerX,it.centerY,it.zoom) }
        val page = db.documentDao().pageForLegacyParagraph(book.id,book.positionBlock-1)?.toIntOrNull() ?: 0
        return DocumentAnchor(page)
    }
    suspend fun annotate(bookId: Long,selection: DocumentSelection,note: String?,quote: Boolean = false): Long = db.withTransaction {
        val first = selection.parts.first()
        val block = db.documentDao().legacyBlock(bookId,first.page.pageIndex.toString()) ?: 0
        val color = if (quote) 0x66FFF59DL else 0x6679C7FFL
        val id = db.annotationDao().insert(
            AnnotationEntity(
                bookId=bookId,
                blockIndex=block,
                startOffset=first.start,
                endOffset=first.end,
                selectedText=selection.text,
                type=if (quote) "QUOTE" else "NOTE",
                colorHex=color,
                note=note,
            )
        )
        val pages = JSONArray()
        selection.parts.forEach { part ->
            val rects = JSONArray()
            part.bounds.forEach { rect ->
                rects.put(JSONArray(listOf(rect.left,rect.top,rect.right,rect.bottom)))
            }
            pages.put(JSONObject().put("page",part.page.pageIndex).put("rects",rects))
        }
        val payload = JSONObject().put("version",2).put("pages",pages).toString()
        // One DB row per annotation is retained for schema compatibility; v2 payload carries all pages.
        db.documentDao().saveMark(DocumentMarkEntity(id,bookId,first.page.pageIndex,payload,color))
        id
    }
    companion object {
        fun decodeMarks(marks: List<DocumentMarkEntity>): Map<Int,List<Pair<DRect,Int>>> {
            val out = linkedMapOf<Int,MutableList<Pair<DRect,Int>>>()
            marks.forEach { mark ->
                runCatching {
                    val trimmed = mark.boundsJson.trimStart()
                    if (trimmed.startsWith("{")) {
                        val root = JSONObject(mark.boundsJson)
                        val pages = root.getJSONArray("pages")
                        for (i in 0 until pages.length()) {
                            val page = pages.getJSONObject(i)
                            val pageIndex = page.getInt("page")
                            val rects = page.getJSONArray("rects")
                            val target = out.getOrPut(pageIndex) { mutableListOf() }
                            for (j in 0 until rects.length()) {
                                val r = rects.getJSONArray(j)
                                target += DRect(r.getDouble(0),r.getDouble(1),r.getDouble(2),r.getDouble(3)) to mark.color.toInt()
                            }
                        }
                    } else {
                        // v1 / Room schema 5 payload: a flat rectangle array for one page.
                        val rects = JSONArray(mark.boundsJson)
                        val target = out.getOrPut(mark.pageIndex) { mutableListOf() }
                        for (i in 0 until rects.length()) {
                            val r = rects.getJSONArray(i)
                            target += DRect(r.getDouble(0),r.getDouble(1),r.getDouble(2),r.getDouble(3)) to mark.color.toInt()
                        }
                    }
                }
            }
            return out
        }
    }
}
