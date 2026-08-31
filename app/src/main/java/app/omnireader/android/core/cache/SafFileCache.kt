package app.omnireader.android.core.cache

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Small bounded staging cache for APIs that require a seekable java.io.File. */
class SafFileCache(private val context: Context) {
    private val root = File(context.cacheDir, "reader_files").apply { mkdirs() }

    suspend fun stage(uri: Uri, hintName: String, versionToken: String? = null): File = withContext(Dispatchers.IO) {
        val key = sha256(uri.toString() + "|" + versionToken.orEmpty()).take(24)
        val ext = hintName.substringAfterLast('.', "bin").take(8)
        val out = File(root, "$key.$ext")
        if (out.exists() && out.length() > 0L) {
            out.setLastModified(System.currentTimeMillis())
            return@withContext out
        }
        val tmp = File(root, "$key.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                }
            }
        } ?: error("Не удалось открыть файл через SAF")
        if (!tmp.renameTo(out)) {
            tmp.copyTo(out, overwrite = true)
            tmp.delete()
        }
        prune(keep = out)
        out
    }

    suspend fun clear() = withContext(Dispatchers.IO) { root.listFiles()?.forEach(File::delete) }

    private fun prune(
        maxBytes: Long = 512L * 1024L * 1024L,
        maxAgeMs: Long = 3L * 24 * 60 * 60 * 1000,
        keep: File? = null,
    ) {
        val files = root.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        val now = System.currentTimeMillis()
        files.filter { it != keep && now - it.lastModified() > maxAgeMs }.forEach(File::delete)
        var total = root.listFiles()?.sumOf { it.length() } ?: 0L
        if (total <= maxBytes) return
        for (file in root.listFiles()?.sortedBy { it.lastModified() }.orEmpty()) {
            if (file == keep) continue
            total -= file.length()
            file.delete()
            if (total <= maxBytes) break
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
