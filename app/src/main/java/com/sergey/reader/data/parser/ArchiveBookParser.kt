package com.sergey.reader.data.parser

import android.content.Context
import android.net.Uri
import com.github.junrar.Junrar
import com.sergey.reader.model.ParsedBook
import com.sergey.reader.model.ParsedChapter
import com.sergey.reader.model.ParsedElement
import com.sergey.reader.util.TextUtil
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

enum class ArchiveKind { ZIP, FB2_ZIP, FB2_GZ, CBZ, CBR, CB7 }

class ArchiveBookParser(
    private val context: Context,
    private val kind: ArchiveKind
) : BookParser {

    override suspend fun parse(uri: Uri, displayName: String): ParsedBook {
        val suffix = when (kind) {
            ArchiveKind.FB2_GZ -> ".gz"
            ArchiveKind.CBR -> ".cbr"
            ArchiveKind.CB7 -> ".cb7"
            else -> ".zip"
        }
        val source = File.createTempFile("reader_archive_", suffix, context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            } ?: return ParsedBook(TextUtil.fileTitle(displayName))

            return when (kind) {
                ArchiveKind.FB2_GZ -> parseFb2Gz(source, displayName)
                ArchiveKind.CBR -> parseRarComic(source, uri, displayName)
                ArchiveKind.CB7 -> parse7zComic(source, uri, displayName)
                ArchiveKind.CBZ -> parseZipComic(source, uri, displayName)
                ArchiveKind.FB2_ZIP, ArchiveKind.ZIP -> parseZipContainer(source, uri, displayName)
            }
        } finally {
            source.delete()
        }
    }

    private suspend fun parseFb2Gz(source: File, displayName: String): ParsedBook {
        val fb2 = File.createTempFile("reader_fb2_", ".fb2", context.cacheDir)
        try {
            GZIPInputStream(source.inputStream()).use { input ->
                fb2.outputStream().use { output -> input.copyTo(output) }
            }
            return Fb2Parser(context.contentResolver).parse(Uri.fromFile(fb2), stripArchiveSuffix(displayName))
        } finally {
            fb2.delete()
        }
    }

    private suspend fun parseZipContainer(source: File, uri: Uri, displayName: String): ParsedBook {
        ZipFile(source).use { zip ->
            val entries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
            val imageEntries = entries.filter { isImage(it.name) }
            val bookEntries = entries.filter { isBookInsideArchive(it.name) }

            if (bookEntries.isEmpty() && imageEntries.isNotEmpty()) {
                return comicFromStreams(
                    uri = uri,
                    displayName = displayName,
                    names = imageEntries.map { it.name },
                    opener = { name ->
                        val entry = zip.getEntry(name) ?: error("Не найден файл $name")
                        zip.getInputStream(entry)
                    }
                )
            }

            val preferred = bookEntries.sortedWith(compareBy(
                { archivePreference(it.name) },
                { naturalKey(it.name) }
            )).firstOrNull()
                ?: throw IllegalArgumentException("В ZIP не найдено поддерживаемых книг или изображений")

            val ext = innerExtension(preferred.name)
            val extracted = File.createTempFile("reader_inner_", ".$ext", context.cacheDir)
            try {
                zip.getInputStream(preferred).use { input -> extracted.outputStream().use { output -> input.copyTo(output) } }
                return BookParserFactory(context).parserFor(preferred.name)
                    .parse(Uri.fromFile(extracted), preferred.name)
            } finally {
                extracted.delete()
            }
        }
    }

    private suspend fun parseZipComic(source: File, uri: Uri, displayName: String): ParsedBook =
        ZipFile(source).use { zip ->
            val names = zip.entries().asSequence()
                .filter { !it.isDirectory && isImage(it.name) }
                .map { it.name }
                .sortedBy(::naturalKey)
                .toList()
            if (names.isEmpty()) throw IllegalArgumentException("В CBZ нет изображений")
            comicFromStreams(uri, displayName, names) { name ->
                zip.getInputStream(zip.getEntry(name))
            }
        }

    private suspend fun parseRarComic(source: File, uri: Uri, displayName: String): ParsedBook {
        val dir = File(context.cacheDir, "reader_rar_${System.nanoTime()}").apply { mkdirs() }
        try {
            Junrar.extract(source, dir)
            val files = dir.walkTopDown().filter { it.isFile && isImage(it.name) }.sortedBy { naturalKey(it.relativeTo(dir).path) }.toList()
            if (files.isEmpty()) throw IllegalArgumentException("В CBR нет изображений")
            return comicFromFiles(uri, displayName, files)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun parse7zComic(source: File, uri: Uri, displayName: String): ParsedBook {
        val root = comicAssetRoot(uri)
        val elements = mutableListOf<ParsedElement>()
        SevenZFile(source).use { seven ->
            val entries = mutableListOf<Pair<String, ByteArray>>()
            while (true) {
                val entry = seven.nextEntry ?: break
                val name = entry.name ?: continue
                if (!entry.isDirectory && isImage(name)) {
                    val data = seven.getInputStream(entry).use { it.readBytes() }
                    entries += name to data
                }
            }
            entries.sortedBy { naturalKey(it.first) }.forEachIndexed { idx, (name, data) ->
                val target = File(root, "${(idx + 1).toString().padStart(5, '0')}.${ParserTextTools.safeExt(name)}")
                target.outputStream().use { it.write(data) }
                elements += ParsedElement(ParsedElement.Kind.IMAGE, resourcePath = target.absolutePath)
            }
        }
        if (elements.isEmpty()) throw IllegalArgumentException("В CB7 нет изображений")
        val title = TextUtil.fileTitle(displayName)
        return ParsedBook(title, chapters = listOf(ParsedChapter("Страницы", elements = elements)))
    }

    private fun comicFromFiles(uri: Uri, displayName: String, files: List<File>): ParsedBook {
        val root = comicAssetRoot(uri)
        val elements = files.mapIndexedNotNull { idx, source ->
            runCatching {
                val target = File(root, "${(idx + 1).toString().padStart(5, '0')}.${ParserTextTools.safeExt(source.name)}")
                source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                ParsedElement(ParsedElement.Kind.IMAGE, resourcePath = target.absolutePath)
            }.getOrNull()
        }
        val title = TextUtil.fileTitle(displayName)
        return ParsedBook(title, chapters = listOf(ParsedChapter("Страницы", elements = elements)))
    }

    private fun comicFromStreams(
        uri: Uri,
        displayName: String,
        names: List<String>,
        opener: (String) -> java.io.InputStream
    ): ParsedBook {
        val root = comicAssetRoot(uri)
        val elements = names.sortedBy(::naturalKey).mapIndexedNotNull { idx, name ->
            runCatching {
                val target = File(root, "${(idx + 1).toString().padStart(5, '0')}.${ParserTextTools.safeExt(name)}")
                opener(name).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                ParsedElement(ParsedElement.Kind.IMAGE, resourcePath = target.absolutePath)
            }.getOrNull()
        }
        if (elements.isEmpty()) throw IllegalArgumentException("В архиве нет изображений")
        val title = TextUtil.fileTitle(displayName)
        return ParsedBook(title, chapters = listOf(ParsedChapter("Страницы", elements = elements)))
    }

    private fun comicAssetRoot(uri: Uri): File =
        File(context.filesDir, "comic_assets/${ParserTextTools.sha256(uri.toString())}").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

    private fun isImage(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in
            setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")

    private fun isBookInsideArchive(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".fb2") || lower.endsWith(".epub") ||
            lower.endsWith(".txt") || lower.endsWith(".html") || lower.endsWith(".htm") ||
            lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".rtf") ||
            lower.endsWith(".docx") || lower.endsWith(".odt") ||
            lower.endsWith(".mobi") || lower.endsWith(".azw") || lower.endsWith(".azw3")
    }

    private fun archivePreference(name: String): Int = when (innerExtension(name)) {
        "fb2" -> 0
        "epub" -> 1
        "mobi", "azw3", "azw" -> 2
        "docx", "odt" -> 3
        "html", "htm" -> 4
        "rtf", "md", "markdown", "txt" -> 5
        else -> 9
    }

    private fun innerExtension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    private fun stripArchiveSuffix(name: String): String =
        name.replace(Regex("(?i)\\.fb2\\.gz$"), ".fb2")
            .replace(Regex("(?i)\\.gz$"), ".fb2")

    private fun naturalKey(value: String): String =
        Regex("\\d+|\\D+").findAll(value.lowercase(Locale.ROOT)).joinToString("|") { part ->
            part.value.toLongOrNull()?.toString()?.padStart(18, '0') ?: part.value
        }
}
