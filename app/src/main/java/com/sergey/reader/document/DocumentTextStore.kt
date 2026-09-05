package com.sergey.reader.document

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * Disk spill for OCR pages. The cache is optional, process-wide capped and keyed by a stable
 * document revision when the provider exposes one. Providers without a reliable revision get a
 * session-only key so stale OCR can never be reused after the app is restarted/reopens the URI.
 */
class DocumentTextStore(context: Context, uri: Uri) {
    private val root = File(context.cacheDir, "document-ocr-v2").apply { mkdirs() }
    private val revision = revision(context, uri) ?: "session:${UUID.randomUUID()}"
    private val key = MessageDigest.getInstance("SHA-256")
        .digest("$uri:$revision".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun read(page: Int): DocumentTextPage? = synchronized(lock) {
        val file = File(root, "$key-$page.json")
        if (!file.isFile || file.length() <= 0L) return@synchronized null
        if (file.length() > MAX_PAGE_BYTES) {
            file.delete()
            return@synchronized null
        }
        runCatching {
            val json = JSONObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
            if (json.optInt("version", 0) != FORMAT_VERSION) error("Unsupported OCR cache version")
            val array = json.getJSONArray("glyphs")
            val glyphs = ArrayList<DocumentGlyph>(array.length())
            for (i in 0 until array.length()) {
                val r = array.getJSONArray(i)
                glyphs += DocumentGlyph(
                    r.getInt(0),
                    r.getInt(1),
                    DRect(r.getDouble(2), r.getDouble(3), r.getDouble(4), r.getDouble(5)),
                )
            }
            file.setLastModified(System.currentTimeMillis())
            DocumentTextPage(page, json.getString("text"), glyphs, recognized = true)
        }.getOrElse {
            file.delete()
            null
        }
    }

    @Throws(IOException::class)
    fun write(page: DocumentTextPage) = synchronized(lock) {
        if (!page.recognized) return@synchronized
        val glyphs = JSONArray()
        page.glyphs.forEach { g ->
            glyphs.put(JSONArray(listOf(g.start, g.end, g.bounds.left, g.bounds.top, g.bounds.right, g.bounds.bottom)))
        }
        val data = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("text", page.text)
            .put("glyphs", glyphs)
            .toString()
            .toByteArray(Charsets.UTF_8)
        if (data.size > MAX_PAGE_BYTES) return@synchronized

        val atomic = AtomicFile(File(root, "$key-${page.pageIndex}.json"))
        val stream = atomic.startWrite()
        try {
            stream.write(data)
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            runCatching { atomic.failWrite(stream) }
            if (error is IOException) throw error
            throw IOException("Не удалось записать OCR-кэш", error)
        }
        trimLocked()
    }

    private fun trimLocked() {
        val files = root.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "json" }
            .sortedBy { it.lastModified() }
        var size = files.sumOf { it.length() }
        for (file in files) {
            if (size <= MAX_TOTAL_BYTES) break
            val bytes = file.length()
            if (file.delete()) size -= bytes
        }
    }

    companion object {
        private const val FORMAT_VERSION = 2
        private const val MAX_PAGE_BYTES = 8L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
        private val lock = Any()

        private fun revision(context: Context, uri: Uri): String? {
            if (uri.scheme == "file") {
                val file = File(requireNotNull(uri.path))
                return "file:${file.length()}:${file.lastModified()}"
            }
            return runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        DocumentsContract.Document.COLUMN_SIZE,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val modified = if (!cursor.isNull(0)) cursor.getLong(0) else 0L
                    val size = if (!cursor.isNull(1)) cursor.getLong(1) else -1L
                    // Size alone is not a revision: a provider can replace a same-sized document.
                    if (modified > 0L) "provider:$modified:$size" else null
                }
            }.getOrNull()
        }
    }
}
