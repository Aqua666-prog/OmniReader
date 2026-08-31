package app.omnireader.android.reader.text

import android.content.Context
import android.net.Uri
import android.util.Xml
import app.omnireader.android.core.cache.SafFileCache
import app.omnireader.android.core.model.FileFormat
import app.omnireader.android.data.db.LibraryItemEntity
import app.omnireader.android.reader.ReaderProvider
import app.omnireader.android.reader.ReaderSession
import app.omnireader.android.reader.TextChapter
import app.omnireader.android.reader.TextReaderSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.nio.charset.Charset
import java.util.ArrayDeque
import java.util.zip.ZipFile

class DocumentTextReaderProvider(
    private val context: Context,
    private val cache: SafFileCache,
) : ReaderProvider {
    override val id = "documents"
    override fun supports(format: FileFormat) = format in setOf(FileFormat.DOCX, FileFormat.ODT, FileFormat.RTF)

    override suspend fun open(item: LibraryItemEntity): ReaderSession = withContext(Dispatchers.IO) {
        when (item.format) {
            FileFormat.DOCX -> openDocx(item)
            FileFormat.ODT -> openOdt(item)
            FileFormat.RTF -> openRtf(item)
            else -> error("Формат не поддерживается DocumentTextReaderProvider")
        }
    }

    private suspend fun openDocx(item: LibraryItemEntity): TextReaderSession {
        val staged = cache.stage(Uri.parse(item.uri), item.fileName, "${item.lastModified}:${item.fileSize}")
        ZipFile(staged).use { zip ->
            val document = zip.getEntry("word/document.xml") ?: error("DOCX повреждён: отсутствует word/document.xml")
            val text = zip.getInputStream(document).use { parseDocxXml(it.readBytes()) }.trim()
            if (text.isBlank()) error("DOCX не содержит читаемого текста")
            return TextReaderSession(item, listOf(TextChapter(item.title, text)))
        }
    }

    private suspend fun openOdt(item: LibraryItemEntity): TextReaderSession {
        val staged = cache.stage(Uri.parse(item.uri), item.fileName, "${item.lastModified}:${item.fileSize}")
        ZipFile(staged).use { zip ->
            val content = zip.getEntry("content.xml") ?: error("ODT повреждён: отсутствует content.xml")
            val text = zip.getInputStream(content).use { parseOdtXml(it.readBytes()) }.trim()
            if (text.isBlank()) error("ODT не содержит читаемого текста")
            return TextReaderSession(item, listOf(TextChapter(item.title, text)))
        }
    }

    private fun openRtf(item: LibraryItemEntity): TextReaderSession {
        val bytes = context.contentResolver.openInputStream(Uri.parse(item.uri))?.use { it.readBytes() }
            ?: error("RTF недоступен через SAF")
        val text = RtfTextExtractor.extract(bytes).trim()
        if (text.isBlank()) error("RTF не содержит читаемого текста")
        return TextReaderSession(item, listOf(TextChapter(item.title, text)))
    }

    private fun parseDocxXml(bytes: ByteArray): String {
        val parser = Xml.newPullParser().apply { setInput(bytes.inputStream(), null) }
        val out = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':')) {
                    "t" -> out.append(parser.nextText())
                    "tab" -> out.append('\t')
                    "br", "cr" -> out.append('\n')
                }
            } else if (event == XmlPullParser.END_TAG && parser.name.substringAfter(':') == "p") {
                if (out.isNotEmpty() && !out.endsWith("\n\n")) out.append("\n\n")
            }
            event = parser.next()
        }
        return out.toString().replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n")
    }

    private fun parseOdtXml(bytes: ByteArray): String {
        val parser = Xml.newPullParser().apply { setInput(bytes.inputStream(), null) }
        val out = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.TEXT -> out.append(parser.text)
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "tab" -> out.append('\t')
                    "line-break" -> out.append('\n')
                    "s" -> {
                        val count = parser.getAttributeValue(null, "c")?.toIntOrNull()
                            ?: parser.getAttributeValue("urn:oasis:names:tc:opendocument:xmlns:text:1.0", "c")?.toIntOrNull()
                            ?: 1
                        repeat(count.coerceIn(1, 16)) { out.append(' ') }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "p", "h" -> if (!out.endsWith("\n\n")) out.append("\n\n")
                }
            }
            event = parser.next()
        }
        return out.toString().replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n")
    }
}

internal object RtfTextExtractor {
    private val skippedDestinations = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "header", "footer",
        "headerl", "headerr", "footerl", "footerr", "generator", "xmlnstbl", "datastore",
        "themedata", "colorschememapping", "listtable", "listoverridetable", "rsidtbl",
    )

    private data class State(val skip: Boolean, val uc: Int)

    fun extract(bytes: ByteArray): String {
        val ascii = bytes.toString(Charsets.ISO_8859_1)
        val codePage = Regex("\\\\ansicpg(\\d+)").find(ascii)?.groupValues?.get(1)?.toIntOrNull() ?: 1252
        val charset = runCatching { Charset.forName("windows-$codePage") }.getOrElse { Charsets.ISO_8859_1 }
        val out = StringBuilder()
        val stack = ArrayDeque<State>()
        var skip = false
        var uc = 1
        var skipFallbackChars = 0
        var i = 0

        while (i < bytes.size) {
            val c = bytes[i].toInt() and 0xff
            when (c.toChar()) {
                '{' -> {
                    stack.addLast(State(skip, uc))
                    i++
                }
                '}' -> {
                    if (!stack.isEmpty()) { val state = stack.removeLast(); skip = state.skip; uc = state.uc }
                    i++
                }
                '\\' -> {
                    i++
                    if (i >= bytes.size) break
                    val next = (bytes[i].toInt() and 0xff).toChar()
                    when (next) {
                        '\\', '{', '}' -> {
                            if (!skip && skipFallbackChars == 0) out.append(next) else if (skipFallbackChars > 0) skipFallbackChars--
                            i++
                        }
                        '*' -> { skip = true; i++ }
                        else -> {
                            if (next == '\'') {
                                if (i + 2 < bytes.size) {
                                    val hex = "${(bytes[i + 1].toInt() and 0xff).toChar()}${(bytes[i + 2].toInt() and 0xff).toChar()}"
                                    val value = hex.toIntOrNull(16)
                                    if (value != null) {
                                        if (!skip && skipFallbackChars == 0) out.append(byteArrayOf(value.toByte()).toString(charset))
                                        else if (skipFallbackChars > 0) skipFallbackChars--
                                    }
                                    i += 3
                                } else i = bytes.size
                            } else if (next.isLetter()) {
                                val start = i
                                while (i < bytes.size && (bytes[i].toInt() and 0xff).toChar().isLetter()) i++
                                val word = bytes.copyOfRange(start, i).toString(Charsets.US_ASCII)
                                var sign = 1
                                if (i < bytes.size && bytes[i].toInt().toChar() == '-') { sign = -1; i++ }
                                val nStart = i
                                while (i < bytes.size && (bytes[i].toInt() and 0xff).toChar().isDigit()) i++
                                val param = if (i > nStart) bytes.copyOfRange(nStart, i).toString(Charsets.US_ASCII).toIntOrNull()?.times(sign) else null
                                if (i < bytes.size && bytes[i].toInt().toChar() == ' ') i++
                                when {
                                    word in skippedDestinations -> skip = true
                                    word == "uc" && param != null -> uc = param.coerceIn(0, 16)
                                    word == "u" && param != null -> {
                                        if (!skip) out.append((if (param < 0) param + 65536 else param).toChar())
                                        skipFallbackChars = uc
                                    }
                                    !skip && word in setOf("par", "line") -> out.append('\n')
                                    !skip && word == "tab" -> out.append('\t')
                                    !skip && word == "emdash" -> out.append('—')
                                    !skip && word == "endash" -> out.append('–')
                                    !skip && word == "bullet" -> out.append('•')
                                    !skip && word in setOf("lquote", "rquote") -> out.append('’')
                                    !skip && word in setOf("ldblquote", "rdblquote") -> out.append('”')
                                }
                            } else {
                                // Control symbol such as \~ or \_.
                                if (!skip && skipFallbackChars == 0) when (next) {
                                    '~' -> out.append('\u00a0')
                                    '_' -> out.append('‑')
                                    '-' -> Unit
                                }
                                i++
                            }
                        }
                    }
                }
                '\r', '\n' -> i++
                else -> {
                    if (!skip) {
                        if (skipFallbackChars > 0) skipFallbackChars--
                        else if (c >= 0x20) out.append(byteArrayOf(c.toByte()).toString(charset))
                    }
                    i++
                }
            }
        }
        return out.toString().replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n")
    }
}
