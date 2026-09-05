package com.sergey.reader.ui.document

import android.graphics.Bitmap
import android.util.LruCache
import com.sergey.reader.document.*
import kotlinx.coroutines.*

/** Main-thread cache owner; one cancellable generation, native access serialized by the session. */
class DocumentTileRenderer(private val session: DocumentSession, private val changed: () -> Unit, private val failed: (String) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = object : LruCache<TileKey,Bitmap>(32*1024*1024) {
        override fun sizeOf(key: TileKey,value: Bitmap) = value.allocationByteCount
        // Published bitmaps may be retained by a hardware display list. Drop references on
        // eviction; do not recycle a bitmap that RenderThread may still be reading.
    }
    private var job: Job? = null
    private var generation = 0
    private var wanted: List<TileKey> = emptyList()
    private var lastFailure: String? = null
    private var lastFailureAt = 0L
    private fun reportFailure(message: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (message != lastFailure || now-lastFailureAt >= 3000L) {
            lastFailure = message
            lastFailureAt = now
            failed(message)
        }
    }
    fun get(key: TileKey): Bitmap? = cache.get(key)
    fun cachedFor(page: Int): Map<TileKey,Bitmap> = cache.snapshot().filterKeys { it.page == page }
    fun request(keys: List<TileKey>) {
        val bounded = keys.distinct().take(DocumentTiles.MAX_VISIBLE_TILES + 6)
        if (bounded == wanted && job?.isActive == true) return
        wanted = bounded
        val token = ++generation
        job?.cancel()
        job = scope.launch {
            for (key in bounded) {
                ensureActive()
                if (cache.get(key) != null) continue
                var bitmap: Bitmap? = null
                try {
                    bitmap = session.render(key)
                    ensureActive()
                    if (token == generation) { cache.put(key,bitmap); bitmap = null; changed() }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: OutOfMemoryError) { if(token==generation){cache.evictAll();reportFailure(DocumentErrors.message(error))}; break }
                catch (error: LinkageError) { if(token==generation) reportFailure(DocumentErrors.message(error)); break }
                catch (error: Exception) { if(token==generation) reportFailure(DocumentErrors.message(error)); break }
                finally { bitmap?.recycle() }
            }
        }
    }
    fun cancelPending() { generation++; job?.cancel(); wanted = emptyList() }
    fun release() { scope.cancel(); cache.evictAll() }
}
