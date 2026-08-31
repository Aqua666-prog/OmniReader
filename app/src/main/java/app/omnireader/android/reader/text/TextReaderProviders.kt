package app.omnireader.android.reader.text

import android.content.Context
import android.net.Uri
import android.text.Html
import android.util.Xml
import app.omnireader.android.core.cache.SafFileCache
import app.omnireader.android.core.model.FileFormat
import app.omnireader.android.data.db.LibraryItemEntity
import app.omnireader.android.reader.ReaderProvider
import app.omnireader.android.reader.ReaderSession
import app.omnireader.android.reader.TextChapter
import app.omnireader.android.reader.TextReaderSession
import app.omnireader.android.scanner.TextEncodingDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class TextReaderProvider(
    private val context: Context,
    private val cache: SafFileCache,
) : ReaderProvider {
    override val id = "text"
    override fun supports(format: FileFormat) = format in setOf(
        FileFormat.EPUB, FileFormat.FB2, FileFormat.FB2_ZIP, FileFormat.TXT,
        FileFormat.HTML, FileFormat.XHTML, FileFormat.MARKDOWN,
    )

    override suspend fun open(item: LibraryItemEntity): ReaderSession = withContext(Dispatchers.IO) {
        when (item.format) {
            FileFormat.EPUB -> openEpub(item)
            FileFormat.FB2 -> openFb2(item, readAll(Uri.parse(item.uri)))
            FileFormat.FB2_ZIP -> openFb2Zip(item)
            FileFormat.TXT -> openText(item)
            FileFormat.HTML, FileFormat.XHTML -> TextReaderSession(item, listOf(TextChapter(item.title, htmlToText(readAll(Uri.parse(item.uri)).toString(Charsets.UTF_8)))))
            FileFormat.MARKDOWN -> TextReaderSession(item, listOf(TextChapter(item.title, markdownToText(readAll(Uri.parse(item.uri)).toString(Charsets.UTF_8)))))
            else -> error("Формат не поддерживается текстовым ReaderProvider")
        }
    }

    private suspend fun openEpub(item: LibraryItemEntity): TextReaderSession {
        val staged = cache.stage(Uri.parse(item.uri), item.fileName, "${item.lastModified}:${item.fileSize}")
        ZipFile(staged).use { zip ->
            val container = zip.getEntry("META-INF/container.xml") ?: error("EPUB: отсутствует META-INF/container.xml")
            val containerXml = zip.getInputStream(container).bufferedReader().use { it.readText() }
            val opfPath = Regex("full-path=[\"']([^\"']+)").find(containerXml)?.groupValues?.get(1)
                ?: error("EPUB: не найден package document")
            val opfEntry = zip.getEntry(opfPath) ?: error("EPUB: package document недоступен")
            val opf = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
            val hrefById = Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf).mapNotNull { m ->
                val id = Regex("\\bid=[\"']([^\"']+)", RegexOption.IGNORE_CASE).find(m.value)?.groupValues?.get(1)
                val href = Regex("\\bhref=[\"']([^\"']+)", RegexOption.IGNORE_CASE).find(m.value)?.groupValues?.get(1)
                if (id != null && href != null) id to href else null
            }.toMap()
            val spine = Regex("<itemref\\b[^>]*idref=[\"']([^\"']+)", RegexOption.IGNORE_CASE).findAll(opf).map { it.groupValues[1] }.toList()
            val base = opfPath.substringBeforeLast('/', "")
            val chapters = spine.mapNotNull { id ->
                val href = hrefById[id] ?: return@mapNotNull null
                val path = normalize(if (base.isBlank()) href else "$base/$href")
                val entry = zip.getEntry(path) ?: return@mapNotNull null
                val html = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
                val text = htmlToText(html).trim()
                if (text.isBlank()) null else TextChapter(extractHtmlTitle(html) ?: href.substringAfterLast('/').substringBeforeLast('.'), text)
            }
            if (chapters.isEmpty()) error("EPUB: не удалось прочитать spine")
            return TextReaderSession(item, chapters)
        }
    }

    private fun openFb2(item: LibraryItemEntity, bytes: ByteArray): TextReaderSession {
        val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), null) }
        val chapters = mutableListOf<TextChapter>()
        var bodyDepth = -1
        var sectionDepth = -1
        var sectionTitle: String? = null
        var sectionText: StringBuilder? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "body" -> if (bodyDepth < 0) bodyDepth = parser.depth
                    "section" -> if (bodyDepth >= 0 && sectionDepth < 0) {
                        sectionDepth = parser.depth
                        sectionText = StringBuilder()
                    }
                    "title" -> if (sectionDepth > 0 && parser.depth == sectionDepth + 1) {
                        sectionTitle = collectElementText(parser, "title").trim().takeIf { it.isNotBlank() }
                    }
                    "p", "subtitle", "epigraph", "cite" -> if (sectionDepth > 0) {
                        val value = collectElementText(parser, parser.name.substringAfter(':')).trim()
                        if (value.isNotBlank()) sectionText?.append(value)?.append("\n\n")
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name.substringAfter(':') == "section" && parser.depth == sectionDepth) {
                    val text = sectionText?.toString()?.trim().orEmpty()
                    if (text.isNotBlank()) chapters += TextChapter(sectionTitle ?: "Глава ${chapters.size + 1}", text)
                    sectionDepth = -1
                    sectionTitle = null
                    sectionText = null
                }
            }
            event = parser.next()
        }
        if (chapters.isEmpty()) {
            val plain = htmlToText(bytes.toString(Charsets.UTF_8)).trim()
            chapters += TextChapter(item.title, plain)
        }
        return TextReaderSession(item, chapters)
    }

    private fun openFb2Zip(item: LibraryItemEntity): TextReaderSession {
        context.contentResolver.openInputStream(Uri.parse(item.uri))?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory && e.name.endsWith(".fb2", ignoreCase = true)) return openFb2(item, zip.readBytes())
                }
            }
        }
        error("FB2.ZIP: внутри не найден FB2")
    }

    private fun openText(item: LibraryItemEntity): TextReaderSession {
        val bytes = readAll(Uri.parse(item.uri))
        val charset = item.userEncodingOverride?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: item.detectedEncoding?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: TextEncodingDetector.detect(bytes)
        return TextReaderSession(item, listOf(TextChapter(item.title, TextEncodingDetector.decode(bytes, charset))))
    }

    private fun readAll(uri: Uri): ByteArray = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Файл недоступен через SAF")

    private fun collectElementText(parser: XmlPullParser, localName: String): String {
        val depth = parser.depth
        val out = StringBuilder()
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.TEXT) out.append(parser.text).append(' ')
            if (event == XmlPullParser.END_TAG && parser.depth == depth && parser.name.substringAfter(':') == localName) break
        }
        return out.toString().replace(Regex("\\s+"), " ")
    }

    private fun htmlToText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
        .replace(Regex("\\n{3,}"), "\n\n")

    private fun markdownToText(md: String): String = md
        .replace(Regex("```[\\s\\S]*?```"), "")
        .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("[*_~`]"), "")

    private fun extractHtmlTitle(html: String): String? = Regex("<(?:h1|h2|title)[^>]*>(.*?)</(?:h1|h2|title)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(html)?.groupValues?.get(1)?.let(::htmlToText)?.trim()?.takeIf { it.isNotBlank() }

    private fun normalize(path: String): String {
        val stack = ArrayDeque<String>()
        path.split('/').forEach { part -> when (part) { "", "." -> Unit; ".." -> if (stack.isNotEmpty()) stack.removeLast(); else -> stack.addLast(part) } }
        return stack.joinToString("/")
    }
}
