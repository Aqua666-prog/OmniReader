package com.sergey.reader.data.parser

import android.content.Context
import android.net.Uri
import android.text.Html
import com.sergey.reader.model.ParsedBook
import com.sergey.reader.model.ParsedChapter
import com.sergey.reader.model.ParsedElement
import com.sergey.reader.util.TextUtil
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.zip.ZipFile

internal object ParserTextTools {
    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val bomUtf8 = bytes.size >= 3 &&
            bytes[0].toInt() == 0xEF - 256 &&
            bytes[1].toInt() == 0xBB - 256 &&
            bytes[2].toInt() == 0xBF - 256
        if (bomUtf8) return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
        }
        val utf8 = bytes.toString(Charsets.UTF_8)
        if (utf8.count { it == '\uFFFD' } <= 1) return utf8
        return runCatching { bytes.toString(Charset.forName("windows-1251")) }.getOrDefault(utf8)
    }

    fun plainTextToBook(text: String, displayName: String, titleOverride: String? = null): ParsedBook {
        val clean = text.replace("\r\n", "\n").replace('\r', '\n')
        val chapters = mutableListOf<ParsedChapter>()
        var title = titleOverride?.takeIf { it.isNotBlank() } ?: TextUtil.fileTitle(displayName)
        var chapterTitle = title
        val paragraphs = mutableListOf<String>()

        fun flush() {
            val normalized = paragraphs.map(TextUtil::normalizeParagraph).filter { it.isNotBlank() }
            if (normalized.isNotEmpty() || chapters.isEmpty()) {
                chapters += ParsedChapter(chapterTitle.ifBlank { "Глава ${chapters.size + 1}" }, normalized)
            }
            paragraphs.clear()
        }

        clean.split('\n').forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEach
            val looksLikeHeading =
                line.length <= 120 &&
                    (line.matches(Regex("(?i)^(глава|chapter|часть|part|книга|book)\\s+[\\divxlcdm]+\\b.*")) ||
                        line.matches(Regex("^#{1,6}\\s+.+")))

            if (looksLikeHeading) {
                if (paragraphs.isNotEmpty()) flush()
                chapterTitle = line.replace(Regex("^#{1,6}\\s+"), "").trim()
                if (chapters.isEmpty() && titleOverride == null) title = chapterTitle
            } else {
                paragraphs += line
            }
        }
        if (paragraphs.isNotEmpty() || chapters.isEmpty()) flush()
        return ParsedBook(title = title, chapters = chapters)
    }

    fun htmlToBook(html: String, displayName: String, titleOverride: String? = null): ParsedBook {
        val titleFromTag = Regex("(?is)<title[^>]*>(.*?)</title>")
            .find(html)?.groupValues?.getOrNull(1)
            ?.let { stripHtml(it) }
            ?.takeIf { it.isNotBlank() }
        val headingMatches = Regex("(?is)<h([1-6])[^>]*>(.*?)</h\\1>").findAll(html).toList()
        if (headingMatches.isEmpty()) {
            val plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            return plainTextToBook(plain, displayName, titleOverride ?: titleFromTag)
        }

        val chapters = mutableListOf<ParsedChapter>()
        val documentTitle = titleOverride ?: titleFromTag ?: TextUtil.fileTitle(displayName)
        for (i in headingMatches.indices) {
            val h = headingMatches[i]
            val start = h.range.last + 1
            val end = headingMatches.getOrNull(i + 1)?.range?.first ?: html.length
            val chapterHtml = html.substring(start.coerceAtMost(html.length), end.coerceAtMost(html.length))
            val chapterTitle = stripHtml(h.groupValues[2]).ifBlank { "Глава ${i + 1}" }
            val plain = Html.fromHtml(chapterHtml, Html.FROM_HTML_MODE_LEGACY).toString()
            val paragraphs = plain.replace("\r\n", "\n")
                .split(Regex("\\n\\s*\\n|\\n"))
                .map(TextUtil::normalizeParagraph)
                .filter { it.isNotBlank() }
            chapters += ParsedChapter(chapterTitle, paragraphs)
        }
        return ParsedBook(documentTitle, chapters = chapters.ifEmpty { listOf(ParsedChapter(documentTitle)) })
    }

    fun stripHtml(value: String): String =
        TextUtil.normalizeParagraph(Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString())

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun safeExt(name: String): String = name.substringAfterLast('.', "bin")
        .lowercase().filter { it.isLetterOrDigit() }.take(8).ifBlank { "bin" }
}

class HtmlBookParser(private val context: Context) : BookParser {
    override suspend fun parse(uri: Uri, displayName: String): ParsedBook {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ParsedBook(TextUtil.fileTitle(displayName))
        return ParserTextTools.htmlToBook(ParserTextTools.decode(bytes), displayName)
    }
}

class MarkdownBookParser(private val context: Context) : BookParser {
    override suspend fun parse(uri: Uri, displayName: String): ParsedBook {
        val text = context.contentResolver.openInputStream(uri)?.use { ParserTextTools.decode(it.readBytes()) }
            ?: return ParsedBook(TextUtil.fileTitle(displayName))
        val chapters = mutableListOf<ParsedChapter>()
        var currentTitle = TextUtil.fileTitle(displayName)
        var documentTitle = currentTitle
        val paragraphs = mutableListOf<String>()

        fun flush() {
            if (paragraphs.isNotEmpty() || chapters.isEmpty()) {
                chapters += ParsedChapter(
                    title = currentTitle,
                    paragraphs = paragraphs.map(TextUtil::normalizeParagraph).filter { it.isNotBlank() }
                )
            }
            paragraphs.clear()
        }

        text.replace("\r\n", "\n").lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            val heading = Regex("^#{1,6}\\s+(.+)$").find(line)
            if (heading != null) {
                if (paragraphs.isNotEmpty()) flush()
                currentTitle = heading.groupValues[1].trim()
                if (chapters.isEmpty()) documentTitle = currentTitle
            } else if (line.isNotBlank()) {
                val cleaned = line
                    .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
                    .replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "$1")
                    .replace(Regex("(?<!\\*)\\*\\*([^*]+)\\*\\*"), "$1")
                    .replace(Regex("`([^`]+)`"), "$1")
                    .replace(Regex("^[-*+]\\s+"), "• ")
                paragraphs += cleaned
            }
        }
        if (paragraphs.isNotEmpty() || chapters.isEmpty()) flush()
        return ParsedBook(documentTitle, chapters = chapters)
    }
}

class RtfBookParser(private val context: Context) : BookParser {
    override suspend fun parse(uri: Uri, displayName: String): ParsedBook {
        val raw = context.contentResolver.openInputStream(uri)?.use { ParserTextTools.decode(it.readBytes()) }
            ?: return ParsedBook(TextUtil.fileTitle(displayName))
        val text = decodeRtf(raw)
        return ParserTextTools.plainTextToBook(text, displayName)
    }

    private fun decodeRtf(raw: String): String {
        val codePage = Regex("\\\\ansicpg(\\d+)", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val ansiCharset = runCatching {
            when (codePage) {
                null -> Charset.forName("windows-1252")
                65001 -> Charsets.UTF_8
                else -> Charset.forName("windows-$codePage")
            }
        }.getOrDefault(Charset.forName("windows-1252"))
        var s = raw
        s = s.replace(Regex("(?is)\\{\\\\fonttbl.*?\\}(?=\\s*\\{\\\\|\\s*\\\\)"), "")
        s = s.replace(Regex("(?is)\\{\\\\colortbl.*?\\}"), "")
        s = s.replace(Regex("(?is)\\{\\\\pict.*?\\}"), "")
        s = Regex("\\\\u(-?\\d+)\\??").replace(s) { m ->
            val v = m.groupValues[1].toIntOrNull() ?: return@replace ""
            val code = if (v < 0) v + 65536 else v
            code.toChar().toString()
        }
        s = Regex("\\\\'([0-9a-fA-F]{2})").replace(s) { m ->
            val b = m.groupValues[1].toInt(16).toByte()
            byteArrayOf(b).toString(ansiCharset)
        }
        s = s.replace("\\par", "\n\n").replace("\\line", "\n").replace("\\tab", "\t")
        s = s.replace("\\{", "{").replace("\\}", "}").replace("\\\\", "\\")
        s = s.replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "")
        s = s.replace(Regex("[{}]"), "")
        return s
    }
}

enum class OfficeKind { DOCX, ODT }

class OfficeBookParser(
    private val context: Context,
    private val kind: OfficeKind
) : BookParser {
    override suspend fun parse(uri: Uri, displayName: String): ParsedBook {
        val temp = File.createTempFile("office_", if (kind == OfficeKind.DOCX) ".docx" else ".odt", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return ParsedBook(TextUtil.fileTitle(displayName))

            ZipFile(temp).use { zip ->
                val xmlPath = if (kind == OfficeKind.DOCX) "word/document.xml" else "content.xml"
                val xml = zip.getEntry(xmlPath)?.let { zip.getInputStream(it).readBytes() }
                    ?: return ParsedBook(TextUtil.fileTitle(displayName))
                val parsed = if (kind == OfficeKind.DOCX) parseDocx(xml, displayName) else parseOdt(xml, displayName)
                val imageEntries = zip.entries().asSequence().filter {
                    !it.isDirectory && if (kind == OfficeKind.DOCX) it.name.startsWith("word/media/") else it.name.startsWith("Pictures/")
                }.toList()
                if (imageEntries.isEmpty()) return parsed

                val root = File(context.filesDir, "document_assets/${ParserTextTools.sha256(uri.toString())}").apply { mkdirs() }
                val images = imageEntries.mapIndexedNotNull { idx, entry ->
                    val ext = ParserTextTools.safeExt(entry.name)
                    val target = File(root, "image_${idx + 1}.$ext")
                    runCatching {
                        zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                        ParsedElement(ParsedElement.Kind.IMAGE, resourcePath = target.absolutePath)
                    }.getOrNull()
                }
                if (images.isEmpty()) return parsed
                return parsed.copy(chapters = parsed.chapters + ParsedChapter(
                    title = "Иллюстрации",
                    elements = images
                ))
            }
        } finally {
            temp.delete()
        }
    }

    private fun parseDocx(bytes: ByteArray, displayName: String): ParsedBook {
        val parser = relaxedPull(bytes)
        val chapters = mutableListOf<ParsedChapter>()
        var chapterTitle = TextUtil.fileTitle(displayName)
        var docTitle = chapterTitle
        val chapterParagraphs = mutableListOf<String>()
        var paragraph = StringBuilder()
        var inParagraph = false
        var inText = false
        var paragraphStyle = ""

        fun flushChapter() {
            if (chapterParagraphs.isNotEmpty() || chapters.isEmpty()) {
                chapters += ParsedChapter(chapterTitle, chapterParagraphs.toList())
            }
            chapterParagraphs.clear()
        }

        fun flushParagraph() {
            val text = TextUtil.normalizeParagraph(paragraph.toString())
            val isHeading = paragraphStyle.startsWith("Heading", true) ||
                paragraphStyle.startsWith("Заголовок", true) ||
                paragraphStyle.contains("title", true)
            if (text.isNotBlank()) {
                if (isHeading) {
                    if (chapterParagraphs.isNotEmpty()) flushChapter()
                    chapterTitle = text
                    if (chapters.isEmpty()) docTitle = text
                } else chapterParagraphs += text
            }
            paragraph = StringBuilder()
            paragraphStyle = ""
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                    "p" -> { inParagraph = true; paragraph = StringBuilder(); paragraphStyle = "" }
                    "t" -> if (inParagraph) inText = true
                    "pstyle" -> if (inParagraph) {
                        paragraphStyle = (0 until parser.attributeCount)
                            .firstNotNullOfOrNull { i ->
                                if (parser.getAttributeName(i).endsWith("val", true)) parser.getAttributeValue(i) else null
                            }.orEmpty()
                    }
                    "tab" -> if (inParagraph) paragraph.append('\t')
                    "br" -> if (inParagraph) paragraph.append('\n')
                }
                XmlPullParser.TEXT -> if (inText && inParagraph) paragraph.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name.lowercase()) {
                    "t" -> inText = false
                    "p" -> { flushParagraph(); inParagraph = false }
                }
            }
            event = parser.next()
        }
        if (chapterParagraphs.isNotEmpty() || chapters.isEmpty()) flushChapter()
        return ParsedBook(docTitle, chapters = chapters)
    }

    private fun parseOdt(bytes: ByteArray, displayName: String): ParsedBook {
        val parser = relaxedPull(bytes)
        val chapters = mutableListOf<ParsedChapter>()
        var chapterTitle = TextUtil.fileTitle(displayName)
        var docTitle = chapterTitle
        val chapterParagraphs = mutableListOf<String>()
        var buffer = StringBuilder()
        var active: String? = null

        fun flushParagraph(isHeading: Boolean) {
            val text = TextUtil.normalizeParagraph(buffer.toString())
            if (text.isNotBlank()) {
                if (isHeading) {
                    if (chapterParagraphs.isNotEmpty()) {
                        chapters += ParsedChapter(chapterTitle, chapterParagraphs.toList())
                        chapterParagraphs.clear()
                    }
                    chapterTitle = text
                    if (chapters.isEmpty()) docTitle = text
                } else chapterParagraphs += text
            }
            buffer = StringBuilder()
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase()
                    if ((name == "p" || name == "h") && active == null) {
                        active = name
                        buffer = StringBuilder()
                    } else if (active != null && name == "tab") buffer.append('\t')
                    else if (active != null && name == "line-break") buffer.append('\n')
                }
                XmlPullParser.TEXT -> if (active != null) buffer.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase()
                    if (active == name) {
                        flushParagraph(name == "h")
                        active = null
                    }
                }
            }
            event = parser.next()
        }
        if (chapterParagraphs.isNotEmpty() || chapters.isEmpty()) {
            chapters += ParsedChapter(chapterTitle, chapterParagraphs.toList())
        }
        return ParsedBook(docTitle, chapters = chapters)
    }

    private fun relaxedPull(bytes: ByteArray): XmlPullParser = XmlPullParserFactory.newInstance().apply {
        isNamespaceAware = true
    }.newPullParser().apply {
        runCatching { setFeature(android.util.Xml.FEATURE_RELAXED, true) }
        setInput(ByteArrayInputStream(bytes), null)
    }
}
