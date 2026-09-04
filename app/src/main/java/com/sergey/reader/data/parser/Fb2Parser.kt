package com.sergey.reader.data.parser

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import com.sergey.reader.model.ParsedBook
import com.sergey.reader.model.ParsedChapter
import com.sergey.reader.util.SeriesInference
import com.sergey.reader.util.TextUtil
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class Fb2Parser(private val resolver: ContentResolver) : BookParser {
    override suspend fun parse(uri: Uri, displayName: String): ParsedBook {
        val input = resolver.openInputStream(uri) ?: return ParsedBook(TextUtil.fileTitle(displayName))
        input.use { stream ->
            val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
            parser.setInput(stream, null)

            var bookTitle: String? = null
            var language: String? = null
            var annotation: String? = null
            var series: String? = null
            var seriesIndex: Double? = null
            val authors = mutableListOf<String>()
            var authorFirst = ""
            var authorMiddle = ""
            var authorLast = ""
            var inTitleInfo = false
            var inAuthor = false
            var inAnnotation = false
            var annotationBuilder = StringBuilder()
            var coverId: String? = null
            var binaryId: String? = null
            var binaryBuilder: StringBuilder? = null
            var coverBytes: ByteArray? = null

            val chapters = mutableListOf<ParsedChapter>()
            var sectionDepth = 0
            var chapterTitle = ""
            val chapterParagraphs = mutableListOf<String>()
            var collectingP = false
            var paragraphBuilder = StringBuilder()
            var titleDepth = 0
            var titleBuilder = StringBuilder()

            fun flushChapter() {
                if (chapterParagraphs.isNotEmpty() || chapterTitle.isNotBlank()) {
                    val title = chapterTitle.ifBlank { "Глава ${chapters.size + 1}" }
                    chapters += ParsedChapter(title, chapterParagraphs.toList())
                }
                chapterTitle = ""
                chapterParagraphs.clear()
            }

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                        "title-info" -> inTitleInfo = true
                        "author" -> if (inTitleInfo) {
                            inAuthor = true
                            authorFirst = ""; authorMiddle = ""; authorLast = ""
                        }
                        "annotation" -> if (inTitleInfo) {
                            inAnnotation = true
                            annotationBuilder = StringBuilder()
                        }
                        "sequence" -> if (inTitleInfo) {
                            series = parser.getAttributeValue(null, "name") ?: series
                            seriesIndex = parser.getAttributeValue(null, "number")?.replace(',', '.')?.toDoubleOrNull() ?: seriesIndex
                        }
                        "image" -> if (inTitleInfo) {
                            val href = (0 until parser.attributeCount)
                                .firstNotNullOfOrNull { i -> if (parser.getAttributeName(i).endsWith("href")) parser.getAttributeValue(i) else null }
                            if (!href.isNullOrBlank()) coverId = href.removePrefix("#")
                        }
                        "binary" -> {
                            binaryId = parser.getAttributeValue(null, "id")
                            binaryBuilder = StringBuilder()
                        }
                        "section" -> {
                            if (sectionDepth == 0) flushChapter()
                            sectionDepth++
                        }
                        "title" -> if (sectionDepth > 0) {
                            titleDepth++
                            titleBuilder = StringBuilder()
                        }
                        "p", "subtitle", "text-author" -> if (sectionDepth > 0) {
                            collectingP = true
                            paragraphBuilder = StringBuilder()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text ?: ""
                        if (binaryBuilder != null) binaryBuilder?.append(text)
                        if (inAnnotation) annotationBuilder.append(text).append(' ')
                        if (collectingP) paragraphBuilder.append(text)
                        if (titleDepth > 0) titleBuilder.append(text)
                        if (inTitleInfo && parser.depth > 0) {
                            when (parser.name?.lowercase()) { /* name is unreliable for TEXT; handled with nextText-like end state below */ }
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name.lowercase()) {
                        "title-info" -> inTitleInfo = false
                        "author" -> if (inAuthor) {
                            val name = listOf(authorFirst, authorMiddle, authorLast).filter { it.isNotBlank() }.joinToString(" ")
                            if (name.isNotBlank()) authors += name
                            inAuthor = false
                        }
                        "annotation" -> if (inAnnotation) {
                            annotation = TextUtil.normalizeParagraph(annotationBuilder.toString())
                            inAnnotation = false
                        }
                        "binary" -> {
                            if (binaryId != null && binaryId == coverId) {
                                coverBytes = runCatching { Base64.decode(binaryBuilder.toString(), Base64.DEFAULT) }.getOrNull()
                            }
                            binaryId = null
                            binaryBuilder = null
                        }
                        "title" -> if (titleDepth > 0) {
                            titleDepth--
                            if (titleDepth == 0 && chapterTitle.isBlank()) chapterTitle = TextUtil.normalizeParagraph(titleBuilder.toString())
                        }
                        "p", "subtitle", "text-author" -> if (collectingP) {
                            val p = TextUtil.normalizeParagraph(paragraphBuilder.toString())
                            if (p.isNotBlank() && titleDepth == 0) chapterParagraphs += p
                            collectingP = false
                        }
                        "section" -> {
                            sectionDepth--
                            if (sectionDepth == 0) flushChapter()
                        }
                    }
                }

                // FB2 metadata values are most reliably read by looking at simple elements here.
                if (event == XmlPullParser.START_TAG && inTitleInfo) {
                    when (parser.name.lowercase()) {
                        "book-title" -> bookTitle = runCatching { parser.nextText().trim() }.getOrNull() ?: bookTitle
                        "lang" -> language = runCatching { parser.nextText().trim() }.getOrNull() ?: language
                        "first-name" -> if (inAuthor) authorFirst = runCatching { parser.nextText().trim() }.getOrDefault(authorFirst)
                        "middle-name" -> if (inAuthor) authorMiddle = runCatching { parser.nextText().trim() }.getOrDefault(authorMiddle)
                        "last-name" -> if (inAuthor) authorLast = runCatching { parser.nextText().trim() }.getOrDefault(authorLast)
                    }
                }
                event = parser.next()
            }
            flushChapter()

            val finalTitle = bookTitle?.takeIf { it.isNotBlank() } ?: TextUtil.fileTitle(displayName)
            val inferred = SeriesInference.infer(finalTitle)
            return ParsedBook(
                title = finalTitle,
                authors = authors.distinct().joinToString(", "),
                series = series ?: inferred.series,
                seriesIndex = seriesIndex ?: inferred.index,
                language = language,
                annotation = annotation,
                coverBytes = coverBytes,
                chapters = chapters.ifEmpty { listOf(ParsedChapter(finalTitle, emptyList())) }
            )
        }
    }
}
