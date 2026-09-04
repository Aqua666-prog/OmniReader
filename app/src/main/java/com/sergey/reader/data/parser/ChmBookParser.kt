package com.sergey.reader.data.parser

import android.content.Context
import android.net.Uri
import com.sergey.reader.model.ParsedBook
import com.sergey.reader.model.ParsedChapter
import com.sergey.reader.util.TextUtil
import org.jchmlib.ChmEnumerator
import org.jchmlib.ChmFile
import org.jchmlib.ChmUnitInfo
import java.io.File
import java.nio.charset.Charset

class ChmBookParser(private val context: Context) : BookParser {
    override suspend fun parse(uri: Uri, displayName: String): ParsedBook {
        val temp = File.createTempFile("reader_chm_", ".chm", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return ParsedBook(TextUtil.fileTitle(displayName))

            val chm = ChmFile(temp.absolutePath)
            val htmlUnits = mutableListOf<ChmUnitInfo>()
            chm.enumerate(
                ChmFile.CHM_ENUMERATE_NORMAL or ChmFile.CHM_ENUMERATE_FILES,
                object : ChmEnumerator {
                    override fun enumerate(ui: ChmUnitInfo) {
                        val p = ui.path.lowercase()
                        if (p.endsWith(".htm") || p.endsWith(".html") || p.endsWith(".xhtml")) {
                            htmlUnits += ui
                        }
                    }
                }
            )

            if (htmlUnits.isEmpty()) {
                throw IllegalArgumentException("В CHM не найдено HTML-страниц")
            }

            val encoding = runCatching { Charset.forName(chm.encoding) }.getOrDefault(Charsets.UTF_8)
            val home = chm.homeFile
            val ordered = htmlUnits.sortedWith(compareBy<ChmUnitInfo>(
                { if (it.path.equals(home, true)) 0 else 1 },
                { naturalKey(it.path) }
            ))

            val chapters = mutableListOf<ParsedChapter>()
            val seenTitles = mutableSetOf<String>()
            for (ui in ordered) {
                if (ui.length <= 0L || ui.length > 16L * 1024 * 1024) continue
                val buffer = chm.retrieveObject(ui, 0, ui.length) ?: continue
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val html = runCatching { bytes.toString(encoding) }.getOrElse { bytes.toString(Charsets.UTF_8) }
                val parsed = ParserTextTools.htmlToBook(
                    html = html,
                    displayName = ui.path.substringAfterLast('/').ifBlank { displayName }
                )
                parsed.chapters.forEach { chapter ->
                    val title = chapter.title.ifBlank { ui.path.substringAfterLast('/') }
                    val unique = if (title in seenTitles) "$title · ${chapters.size + 1}" else title
                    seenTitles += unique
                    chapters += chapter.copy(title = unique, sourceRef = ui.path)
                }
                if (chapters.size >= 1200) break
            }

            val title = chm.title?.takeIf { it.isNotBlank() } ?: TextUtil.fileTitle(displayName)
            return ParsedBook(
                title = title,
                chapters = chapters.ifEmpty { listOf(ParsedChapter(title)) }
            )
        } finally {
            // jchmlib keeps a RandomAccessFile internally and has no public close()
            // in v0.5.4. Temp files are best-effort cleaned after the parser releases it.
            runCatching { temp.delete() }
        }
    }

    private fun naturalKey(value: String): String =
        Regex("\\d+|\\D+").findAll(value.lowercase()).joinToString("|") { part ->
            part.value.toLongOrNull()?.toString()?.padStart(18, '0') ?: part.value
        }
}
