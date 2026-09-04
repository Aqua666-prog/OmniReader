package com.sergey.reader.data.parser

import android.content.Context
import android.net.Uri
import com.sergey.reader.model.ParsedBook
import com.sergey.reader.model.ParsedChapter
import com.sergey.reader.model.ParsedElement
import com.sergey.reader.util.SeriesInference
import com.sergey.reader.util.TextUtil
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class EpubParser(private val context: Context) : BookParser {
    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String
    )

    override suspend fun parse(uri: Uri, displayName: String): ParsedBook {
        val temp = File.createTempFile("reader_", ".epub", context.cacheDir)
        val assetRoot = epubAssetRoot(uri)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return ParsedBook(TextUtil.fileTitle(displayName))

            ZipFile(temp).use { zip ->
                val containerBytes = zip.getEntry("META-INF/container.xml")?.let { zip.getInputStream(it).readBytes() }
                    ?: return ParsedBook(TextUtil.fileTitle(displayName))
                val opfPath = parseContainer(containerBytes)
                    ?: return ParsedBook(TextUtil.fileTitle(displayName))
                val opfEntry = zip.getEntry(opfPath) ?: return ParsedBook(TextUtil.fileTitle(displayName))
                val opf = parseOpf(zip.getInputStream(opfEntry).readBytes())

                val title = opf.title.ifBlank { TextUtil.fileTitle(displayName) }
                val inferred = SeriesInference.infer(title)
                val manifest = opf.manifest.associateBy { it.id }
                val chapters = mutableListOf<ParsedChapter>()

                for (idRef in opf.spine) {
                    val item = manifest[idRef] ?: continue
                    if (!(item.mediaType.contains("html") || item.mediaType.contains("xhtml"))) continue
                    val path = resolveRelative(opfPath, item.href)
                    val entry = zip.getEntry(path) ?: continue
                    val chapter = parseXhtml(zip, zip.getInputStream(entry).readBytes(), chapters.size + 1, path, assetRoot)
                    if (chapter.elements.isNotEmpty()) chapters += chapter
                }

                if (chapters.isEmpty()) {
                    opf.manifest
                        .filter { it.mediaType.contains("html") || it.mediaType.contains("xhtml") }
                        .forEach { item ->
                            val path = resolveRelative(opfPath, item.href)
                            val entry = zip.getEntry(path) ?: return@forEach
                            val chapter = parseXhtml(zip, zip.getInputStream(entry).readBytes(), chapters.size + 1, path, assetRoot)
                            if (chapter.elements.isNotEmpty()) chapters += chapter
                        }
                }

                val coverItem = opf.coverId?.let(manifest::get)
                    ?: opf.manifest.firstOrNull { "cover-image" in it.properties }
                val coverBytes = coverItem?.let {
                    zip.getEntry(resolveRelative(opfPath, it.href))?.let { entry -> zip.getInputStream(entry).readBytes() }
                }

                return ParsedBook(
                    title = title,
                    authors = opf.creators.distinct().joinToString(", "),
                    series = opf.series ?: inferred.series,
                    seriesIndex = opf.seriesIndex ?: inferred.index,
                    language = opf.language,
                    annotation = opf.description,
                    coverBytes = coverBytes,
                    chapters = chapters.ifEmpty { listOf(ParsedChapter(title)) }
                )
            }
        } finally {
            temp.delete()
        }
    }

    private fun parseContainer(bytes: ByteArray): String? {
        val parser = pull(bytes)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("rootfile", true)) {
                return parser.getAttributeValue(null, "full-path")
            }
            event = parser.next()
        }
        return null
    }

    private data class OpfData(
        var title: String = "",
        val creators: MutableList<String> = mutableListOf(),
        var language: String? = null,
        var description: String? = null,
        var series: String? = null,
        var seriesIndex: Double? = null,
        var coverId: String? = null,
        val manifest: MutableList<ManifestItem> = mutableListOf(),
        val spine: MutableList<String> = mutableListOf()
    )

    private fun parseOpf(bytes: ByteArray): OpfData {
        val out = OpfData()
        val parser = pull(bytes)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "title" -> if (out.title.isBlank()) out.title = safeNextText(parser)
                    "creator" -> safeNextText(parser).takeIf { it.isNotBlank() }?.let(out.creators::add)
                    "language" -> out.language = safeNextText(parser).takeIf { it.isNotBlank() }
                    "description" -> out.description = TextUtil.normalizeParagraph(safeNextText(parser)).takeIf { it.isNotBlank() }
                    "meta" -> {
                        val name = parser.getAttributeValue(null, "name")
                        val property = parser.getAttributeValue(null, "property")
                        val content = parser.getAttributeValue(null, "content")
                        when {
                            name.equals("cover", true) -> out.coverId = content
                            name.equals("calibre:series", true) -> out.series = content
                            name.equals("calibre:series_index", true) -> out.seriesIndex = content?.replace(',', '.')?.toDoubleOrNull()
                            property.equals("belongs-to-collection", true) -> {
                                val value = safeNextText(parser)
                                if (value.isNotBlank()) out.series = value
                            }
                            property.equals("group-position", true) -> {
                                val value = safeNextText(parser)
                                out.seriesIndex = value.replace(',', '.').toDoubleOrNull()
                            }
                        }
                    }
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        if (id.isNotBlank() && href.isNotBlank()) {
                            out.manifest += ManifestItem(
                                id = id,
                                href = href,
                                mediaType = parser.getAttributeValue(null, "media-type") ?: "",
                                properties = parser.getAttributeValue(null, "properties") ?: ""
                            )
                        }
                    }
                    "itemref" -> parser.getAttributeValue(null, "idref")?.takeIf { it.isNotBlank() }?.let(out.spine::add)
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun parseXhtml(
        zip: ZipFile,
        bytes: ByteArray,
        number: Int,
        sourceRef: String,
        assetRoot: File
    ): ParsedChapter {
        val parser = pull(bytes)
        val elements = mutableListOf<ParsedElement>()
        val blockTags = setOf("p", "li", "blockquote", "pre", "h1", "h2", "h3", "h4", "h5", "h6", "figcaption", "table")
        val headingTags = setOf("h1", "h2", "h3")
        var activeBlock: String? = null
        var buffer = StringBuilder()
        var heading: String? = null
        var ignoreDepth = 0
        var footnoteDepth = 0
        var footnoteBuffer = StringBuilder()
        var localLinkHref: String? = null
        var localLinkText = StringBuilder()

        fun flushBlock(partial: Boolean = false) {
            val text = TextUtil.normalizeParagraph(buffer.toString())
            if (text.isNotBlank()) {
                if (activeBlock in headingTags && heading == null) heading = text
                if (activeBlock !in headingTags) {
                    elements += ParsedElement(ParsedElement.Kind.PARAGRAPH, text = text)
                }
            }
            buffer = StringBuilder()
            if (!partial) activeBlock = null
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase()

                    if (ignoreDepth == 0 && footnoteDepth == 0 && name == "a") {
                        val href = findLinkHref(parser)
                        if (!href.isNullOrBlank() && !href.startsWith("http://", true) &&
                            !href.startsWith("https://", true) && !href.startsWith("mailto:", true)) {
                            localLinkHref = resolveRelative(sourceRef, href)
                            localLinkText = StringBuilder()
                        }
                    }

                    if (ignoreDepth == 0 && activeBlock != null && name == "br") buffer.append('\n')
                    if (ignoreDepth == 0 && activeBlock == "table" && (name == "td" || name == "th") && buffer.isNotBlank()) {
                        buffer.append(" · ")
                    }

                    if (footnoteDepth > 0) {
                        footnoteDepth++
                    } else if (isFootnoteStart(parser)) {
                        if (activeBlock != null && buffer.isNotBlank()) flushBlock(partial = true)
                        footnoteDepth = 1
                        footnoteBuffer = StringBuilder()
                    } else {
                        if (name == "script" || name == "style") ignoreDepth++
                        if (ignoreDepth == 0 && (name == "img" || name == "image")) {
                            if (activeBlock != null && buffer.isNotBlank()) flushBlock(partial = true)
                            findImageHref(parser)?.let { href ->
                                extractImage(zip, sourceRef, href, assetRoot)?.let { path ->
                                    elements += ParsedElement(ParsedElement.Kind.IMAGE, resourcePath = path)
                                }
                            }
                        }
                        if (ignoreDepth == 0 && name in blockTags && activeBlock == null) {
                            activeBlock = name
                            buffer = StringBuilder()
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    if (localLinkHref != null) localLinkText.append(parser.text).append(' ')
                    when {
                        footnoteDepth > 0 -> footnoteBuffer.append(parser.text).append(' ')
                        ignoreDepth == 0 && activeBlock != null -> buffer.append(parser.text).append(' ')
                    }
                }

                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase()
                    if (name == "a" && localLinkHref != null) {
                        val label = TextUtil.normalizeParagraph(localLinkText.toString())
                        // EPUB navigation is usually <li><a href="chapter.xhtml">…</a></li>.
                        // Turn these entries into real in-reader chapter links instead of dead text.
                        if (activeBlock == "li" && label.isNotBlank()) {
                            elements += ParsedElement(
                                ParsedElement.Kind.LINK,
                                text = label,
                                resourcePath = localLinkHref
                            )
                            buffer = StringBuilder()
                        }
                        localLinkHref = null
                        localLinkText = StringBuilder()
                    }
                    if (footnoteDepth > 0) {
                        footnoteDepth--
                        if (footnoteDepth == 0) {
                            val note = TextUtil.normalizeParagraph(footnoteBuffer.toString())
                            if (note.isNotBlank()) elements += ParsedElement(ParsedElement.Kind.FOOTNOTE, text = note)
                        }
                    } else {
                        if ((name == "script" || name == "style") && ignoreDepth > 0) ignoreDepth--
                        if (ignoreDepth == 0 && activeBlock == "table" && name == "tr" && buffer.isNotBlank()) flushBlock(partial = true)
                        if (ignoreDepth == 0 && activeBlock == name) flushBlock()
                    }
                }
            }
            event = parser.next()
        }
        if (activeBlock != null && buffer.isNotBlank()) flushBlock()

        val cleanHeading = heading ?: "Глава $number"
        val cleanedElements = elements.distinctConsecutive().toMutableList().apply {
            // Some EPUB generators repeat the chapter heading once more as the first
            // ordinary paragraph. The reader already renders the chapter title as a
            // dedicated CHAPTER block, so suppress only that immediate duplicate.
            val firstText = indexOfFirst { it.kind == ParsedElement.Kind.PARAGRAPH }
            if (firstText >= 0 && this[firstText].text.equals(cleanHeading, ignoreCase = true)) {
                removeAt(firstText)
            }
        }
        return ParsedChapter(
            title = cleanHeading,
            paragraphs = cleanedElements
                .filter { it.kind == ParsedElement.Kind.PARAGRAPH || it.kind == ParsedElement.Kind.FOOTNOTE }
                .map { it.text },
            sourceRef = sourceRef,
            elements = cleanedElements
        )
    }

    private fun isFootnoteStart(parser: XmlPullParser): Boolean {
        val name = parser.name.lowercase()
        if (name !in setOf("aside", "div", "section", "li")) return false
        var type: String? = null
        var role: String? = null
        for (i in 0 until parser.attributeCount) {
            when (parser.getAttributeName(i).lowercase()) {
                "type" -> type = parser.getAttributeValue(i)
                "role" -> role = parser.getAttributeValue(i)
            }
        }
        return type?.contains("footnote", ignoreCase = true) == true ||
            role?.contains("doc-footnote", ignoreCase = true) == true
    }

    private fun findLinkHref(parser: XmlPullParser): String? {
        parser.getAttributeValue(null, "href")?.takeIf { it.isNotBlank() }?.let { return it }
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).endsWith("href", ignoreCase = true)) {
                return parser.getAttributeValue(i)?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun findImageHref(parser: XmlPullParser): String? {
        parser.getAttributeValue(null, "src")?.takeIf { it.isNotBlank() }?.let { return it }
        parser.getAttributeValue(null, "href")?.takeIf { it.isNotBlank() }?.let { return it }
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i).lowercase()
            if (name == "href" || name == "src") {
                return parser.getAttributeValue(i)?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun extractImage(zip: ZipFile, documentPath: String, href: String, assetRoot: File): String? = runCatching {
        val entryPath = resolveRelative(documentPath, href)
        val entry = zip.getEntry(entryPath) ?: return@runCatching null
        val ext = entryPath.substringAfterLast('.', "img").lowercase().take(8)
        val digest = sha256(entryPath)
        val target = File(assetRoot, "$digest.$ext")
        if (!target.exists() || target.length() == 0L) {
            zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        }
        target.absolutePath
    }.getOrNull()

    private fun epubAssetRoot(uri: Uri): File = File(context.filesDir, "epub_assets/${sha256(uri.toString())}").apply { mkdirs() }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun pull(bytes: ByteArray): XmlPullParser = XmlPullParserFactory.newInstance().apply {
        isNamespaceAware = true
    }.newPullParser().apply {
        setFeature(android.util.Xml.FEATURE_RELAXED, true)
        setInput(ByteArrayInputStream(bytes), null)
    }

    private fun safeNextText(parser: XmlPullParser): String = runCatching { parser.nextText().trim() }.getOrDefault("")

    internal fun resolveRelative(baseFile: String, href: String): String {
        val cleanHref = Uri.decode(href.substringBefore('#').substringBefore('?'))
        if (cleanHref.startsWith('/')) return cleanHref.removePrefix("/")
        val base = baseFile.substringBeforeLast('/', "")
        val parts = (if (base.isBlank()) cleanHref else "$base/$cleanHref").split('/')
        val stack = ArrayDeque<String>()
        for (part in parts) {
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun List<ParsedElement>.distinctConsecutive(): List<ParsedElement> {
        if (isEmpty()) return this
        val out = ArrayList<ParsedElement>(size)
        var previous: ParsedElement? = null
        for (item in this) {
            if (item != previous) out += item
            previous = item
        }
        return out
    }
}
