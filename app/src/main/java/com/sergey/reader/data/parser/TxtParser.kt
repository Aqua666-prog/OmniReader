package com.sergey.reader.data.parser

import android.content.ContentResolver
import android.net.Uri
import com.sergey.reader.model.ParsedBook
import com.sergey.reader.model.ParsedChapter
import com.sergey.reader.util.SeriesInference
import com.sergey.reader.util.TextUtil

class TxtParser(private val resolver: ContentResolver) : BookParser {
    override suspend fun parse(uri: Uri, displayName: String): ParsedBook {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        val text = TextUtil.decode(bytes)
        val paragraphs = text
            .split(Regex("\\r?\\n\\s*\\r?\\n"))
            .map(TextUtil::normalizeParagraph)
            .filter { it.isNotBlank() }
        val title = TextUtil.fileTitle(displayName)
        val inferred = SeriesInference.infer(title)
        return ParsedBook(
            title = title,
            series = inferred.series,
            seriesIndex = inferred.index,
            chapters = listOf(ParsedChapter(title, paragraphs.ifEmpty { listOf(text) }))
        )
    }
}
