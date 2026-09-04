package com.sergey.reader.data.fonts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class UserFont(
    val name: String,
    val path: String
)

class FontRepository(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "fonts").apply { mkdirs() }
    private val _fonts = MutableStateFlow(scanFonts())
    val fonts: StateFlow<List<UserFont>> = _fonts.asStateFlow()

    suspend fun importFont(uri: Uri): Result<UserFont> = withContext(Dispatchers.IO) {
        runCatching {
            val original = displayName(uri)
            val ext = original.substringAfterLast('.', "").lowercase()
            require(ext in setOf("ttf", "otf")) { "Нужен файл .ttf или .otf" }
            val safe = original.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
            var target = File(dir, safe)
            var i = 2
            while (target.exists()) {
                val stem = safe.substringBeforeLast('.', safe)
                target = File(dir, "$stem ($i).$ext")
                i++
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Не удалось прочитать шрифт")
            val result = UserFont(target.nameWithoutExtension, target.absolutePath)
            _fonts.value = scanFonts()
            result
        }
    }

    fun listFonts(): List<UserFont> = scanFonts()

    suspend fun deleteFont(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        val deleted = file.parentFile?.canonicalFile == dir.canonicalFile && file.delete()
        _fonts.value = scanFonts()
        deleted
    }

    fun refresh() {
        _fonts.value = scanFonts()
    }

    private fun scanFonts(): List<UserFont> = dir.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }
        .sortedBy { it.name.lowercase() }
        .map { UserFont(it.nameWithoutExtension, it.absolutePath) }

    private fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "font.ttf"
    }
}
