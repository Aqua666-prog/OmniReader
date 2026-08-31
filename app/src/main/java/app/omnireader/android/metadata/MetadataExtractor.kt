package app.omnireader.android.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Xml
import app.omnireader.android.core.cache.SafFileCache
import app.omnireader.android.core.model.FileFormat
import app.omnireader.android.core.util.NaturalSort
import app.omnireader.android.reader.djvu.DjvuSupport
import app.omnireader.android.reader.text.MobiBookParser
import app.omnireader.android.scanner.FormatDetector
import com.github.junrar.Archive
import com.t8rin.djvu_coder.DJVUDecoder
import com.t8rin.tiff_coder.TiffCoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class MetadataExtractor(
    private val context: Context,
    private val cache: SafFileCache,
) {
    data class Metadata(
        val title: String? = null,
        val author: String? = null,
        val description: String? = null,
        val coverPath: String? = null,
        val chapterCount: Int? = null,
        val pageCount: Int? = null,
    )

    suspend fun extractImageFolder(images: List<Pair<String, Uri>>, keyId: String): Metadata = withContext(Dispatchers.IO) {
        val sorted = images.sortedWith { a, b -> NaturalSort.comparator.compare(a.first, b.first) }
        val preferred = preferredImageName(sorted.map { it.first })?.let { wanted -> sorted.firstOrNull { it.first == wanted } }
            ?: sorted.firstOrNull()
        val cover = preferred?.second?.let { uri -> decodeUriBitmap(uri)?.let { saveThumbBitmap(it, keyId) } }
        Metadata(coverPath = cover, pageCount = sorted.size)
    }

    suspend fun extract(
        uri: Uri,
        format: FileFormat,
        fileName: String,
        versionToken: String? = null,
    ): Metadata = withContext(Dispatchers.IO) {
        runCatching {
            when (format) {
                FileFormat.EPUB -> epub(uri, fileName, versionToken)
                FileFormat.FB2 -> fb2(uri)
                FileFormat.FB2_ZIP -> fb2Zip(uri)
                FileFormat.CBZ, FileFormat.ZIP -> comicZip(uri)
                FileFormat.CBR, FileFormat.RAR -> comicRar(uri, fileName, versionToken)
                FileFormat.CB7, FileFormat.SEVEN_Z -> comicSevenZ(uri, fileName, versionToken)
                FileFormat.CBT -> comicTar(uri, fileName, versionToken)
                FileFormat.PDF -> pdf(uri, versionToken)
                FileFormat.DJVU, FileFormat.DJV -> djvu(uri, fileName, versionToken)
                FileFormat.TIFF, FileFormat.TIF -> tiff(uri, fileName, versionToken)
                FileFormat.DOCX -> docx(uri, fileName, versionToken)
                FileFormat.ODT -> odt(uri, fileName, versionToken)
                FileFormat.MOBI, FileFormat.AZW3 -> kindle(uri, fileName, versionToken)
                FileFormat.JPG, FileFormat.JPEG, FileFormat.PNG, FileFormat.WEBP,
                FileFormat.AVIF, FileFormat.GIF, FileFormat.BMP -> image(uri)
                else -> Metadata()
            }
        }.getOrElse { Metadata() }
    }

    private suspend fun epub(uri: Uri, fileName: String, versionToken: String?): Metadata {
        val staged = cache.stage(uri, fileName, versionToken)
        ZipFile(staged).use { zip ->
            val container = zip.getEntry("META-INF/container.xml") ?: return Metadata()
            val containerText = zip.getInputStream(container).bufferedReader().use { it.readText() }
            val opfPath = Regex("full-path=[\"']([^\"']+)").find(containerText)?.groupValues?.get(1) ?: return Metadata()
            val opfEntry = zip.getEntry(opfPath) ?: return Metadata()
            val opf = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
            val title = tagValue(opf, "title")
            val author = tagValue(opf, "creator")
            val description = tagValue(opf, "description")
            val spineCount = Regex("<itemref\\b", RegexOption.IGNORE_CASE).findAll(opf).count()
            val manifest = Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf).map { it.value }.toList()
            val coverId = Regex("<meta[^>]+name=[\"']cover[\"'][^>]+content=[\"']([^\"']+)", RegexOption.IGNORE_CASE).find(opf)?.groupValues?.get(1)
            val coverLine = manifest.firstOrNull { line -> coverId != null && Regex("id=[\"']${Regex.escape(coverId)}[\"']", RegexOption.IGNORE_CASE).containsMatchIn(line) }
                ?: manifest.firstOrNull { it.contains("cover", ignoreCase = true) && it.contains("image/", ignoreCase = true) }
            val href = coverLine?.let { Regex("href=[\"']([^\"']+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
            val cover = href?.let {
                val base = opfPath.substringBeforeLast('/', "")
                val path = if (base.isBlank()) it else "$base/$it"
                zip.getEntry(path)?.let { entry -> saveThumb(zip.getInputStream(entry).readBytes(), uri.toString()) }
            }
            return Metadata(title, author, description, cover, spineCount.takeIf { it > 0 })
        }
    }

    private fun fb2(uri: Uri): Metadata = context.contentResolver.openInputStream(uri)?.use { stream ->
        parseFb2(stream.readBytes(), uri.toString())
    } ?: Metadata()

    private fun fb2Zip(uri: Uri): Metadata = context.contentResolver.openInputStream(uri)?.use { raw ->
        ZipInputStream(raw.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".fb2", ignoreCase = true)) return@use parseFb2(zip.readBytes(), uri.toString())
            }
            Metadata()
        }
    } ?: Metadata()

    private fun parseFb2(bytes: ByteArray, key: String): Metadata {
        val parser = Xml.newPullParser().apply { setInput(bytes.inputStream(), null) }
        var title: String? = null
        val authors = mutableListOf<String>()
        var description: String? = null
        var coverId: String? = null
        var currentBinaryId: String? = null
        var cover: String? = null
        var sections = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':')) {
                    "book-title" -> if (title == null) title = parser.nextText().trim()
                    "author" -> if (authors.isEmpty()) {
                        val parts = mutableListOf<String>()
                        val depth = parser.depth
                        while (!(parser.eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                            parser.next()
                            if (parser.eventType == XmlPullParser.START_TAG && parser.name.substringAfter(':') in setOf("first-name", "middle-name", "last-name", "nickname")) {
                                parts += parser.nextText().trim()
                            }
                        }
                        parts.filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }?.let(authors::add)
                    }
                    "annotation" -> if (description == null) description = collectText(parser, "annotation").trim().take(2000)
                    "image" -> if (coverId == null) coverId = parser.getAttributeValue(null, "href")?.removePrefix("#")
                        ?: parser.getAttributeValue("http://www.w3.org/1999/xlink", "href")?.removePrefix("#")
                    "section" -> sections++
                    "binary" -> currentBinaryId = parser.getAttributeValue(null, "id")
                }
            } else if (event == XmlPullParser.TEXT && currentBinaryId != null && currentBinaryId == coverId && cover == null) {
                runCatching { android.util.Base64.decode(parser.text.trim(), android.util.Base64.DEFAULT) }.getOrNull()?.let { cover = saveThumb(it, key) }
            } else if (event == XmlPullParser.END_TAG && parser.name.substringAfter(':') == "binary") {
                currentBinaryId = null
            }
            event = parser.next()
        }
        return Metadata(title, authors.firstOrNull(), description, cover, sections.takeIf { it > 0 })
    }

    private fun collectText(parser: XmlPullParser, end: String): String {
        val depth = parser.depth
        val out = StringBuilder()
        while (true) {
            val e = parser.next()
            if (e == XmlPullParser.TEXT) out.append(parser.text).append(' ')
            if (e == XmlPullParser.END_TAG && parser.depth == depth && parser.name.substringAfter(':') == end) break
        }
        return out.toString()
    }

    private fun comicZip(uri: Uri): Metadata {
        val result = context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var first: ByteArray? = null
                var preferred: ByteArray? = null
                var pages = 0
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory && FormatDetector.isImageName(e.name)) {
                        pages++
                        val preferredName = isPreferredCoverName(e.name)
                        if (first == null || preferredName) {
                            val candidate = zip.readBytes()
                            if (first == null) first = candidate
                            if (preferredName && preferred == null) preferred = candidate
                        }
                    }
                }
                Pair(preferred ?: first, pages)
            }
        }
        return Metadata(coverPath = result?.first?.let { saveThumb(it, uri.toString()) }, pageCount = result?.second)
    }

    private suspend fun comicRar(uri: Uri, fileName: String, versionToken: String?): Metadata {
        val staged = cache.stage(uri, fileName, versionToken)
        Archive(staged).use { archive ->
            val headers = archive.fileHeaders.filter { !it.isDirectory && FormatDetector.isImageName(it.fileName) }
            val names = headers.map { it.fileName }.sortedWith(NaturalSort.comparator)
            val selectedName = preferredImageName(names) ?: names.firstOrNull()
            val selected = headers.firstOrNull { it.fileName == selectedName }
            val coverBytes = selected?.let { header -> ByteArrayOutputStream().use { out -> archive.extractFile(header, out); out.toByteArray() } }
            return Metadata(coverPath = coverBytes?.let { saveThumb(it, uri.toString()) }, pageCount = names.size)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun comicSevenZ(uri: Uri, fileName: String, versionToken: String?): Metadata {
        val staged = cache.stage(uri, fileName, versionToken)
        SevenZFile(staged).use { archive ->
            val entries = archive.entries.asSequence().filter { !it.isDirectory && FormatDetector.isImageName(it.name) }.associateBy { it.name }
            val names = entries.keys.sortedWith(NaturalSort.comparator)
            val selectedName = preferredImageName(names) ?: names.firstOrNull()
            val cover = selectedName?.let { name -> entries[name]?.let { e -> archive.getInputStream(e).use { it.readBytes() } } }
            return Metadata(coverPath = cover?.let { saveThumb(it, uri.toString()) }, pageCount = names.size)
        }
    }

    private suspend fun comicTar(uri: Uri, fileName: String, versionToken: String?): Metadata {
        val staged = cache.stage(uri, fileName, versionToken)
        val names = mutableListOf<String>()
        TarArchiveInputStream(BufferedInputStream(FileInputStream(staged))).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                if (!entry.isDirectory && FormatDetector.isImageName(entry.name)) names += entry.name
            }
        }
        val sorted = names.sortedWith(NaturalSort.comparator)
        val selected = preferredImageName(sorted) ?: sorted.firstOrNull()
        var cover: ByteArray? = null
        if (selected != null) {
            TarArchiveInputStream(BufferedInputStream(FileInputStream(staged))).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    if (!entry.isDirectory && entry.name == selected) { cover = tar.readBytes(); break }
                }
            }
        }
        return Metadata(coverPath = cover?.let { saveThumb(it, uri.toString()) }, pageCount = sorted.size)
    }

    private suspend fun pdf(uri: Uri, versionToken: String?): Metadata {
        fun render(pfd: ParcelFileDescriptor): Metadata = PdfRenderer(pfd).use { renderer ->
            if (renderer.pageCount == 0) return Metadata(pageCount = 0)
            renderer.openPage(0).use { page ->
                val width = 360
                val height = (width.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return Metadata(coverPath = saveThumbBitmap(bitmap, uri.toString()), pageCount = renderer.pageCount)
            }
        }
        val direct = context.contentResolver.openFileDescriptor(uri, "r")
        if (direct != null) {
            try {
                return render(direct)
            } catch (_: IllegalArgumentException) {
                try { direct.close() } catch (_: Throwable) { Unit }
            } catch (t: Throwable) {
                try { direct.close() } catch (_: Throwable) { Unit }
                throw t
            }
        }
        val staged = cache.stage(uri, "document.pdf", versionToken)
        return ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY).use(::render)
    }

    private suspend fun djvu(uri: Uri, fileName: String, versionToken: String?): Metadata {
        val staged = cache.stage(uri, fileName, versionToken)
        val decoder = DJVUDecoder(staged)
        val count = DjvuSupport.countPages(staged, decoder)
        val cover = if (count > 0) decoder.decode(0, 450)?.let { saveThumbBitmap(it, uri.toString()) } else null
        return Metadata(coverPath = cover, pageCount = count)
    }

    private suspend fun tiff(uri: Uri, fileName: String, versionToken: String?): Metadata {
        val staged = cache.stage(uri, fileName, versionToken)
        val count = TiffCoder.pageCount(staged)
        val cover = if (count > 0) TiffCoder.decode(staged, 0, sampleSize = 4)?.let { saveThumbBitmap(it, uri.toString()) } else null
        return Metadata(coverPath = cover, pageCount = count)
    }

    private suspend fun docx(uri: Uri, fileName: String, versionToken: String?): Metadata {
        val staged = cache.stage(uri, fileName, versionToken)
        ZipFile(staged).use { zip ->
            val core = zip.getEntry("docProps/core.xml") ?: return Metadata()
            val xml = zip.getInputStream(core).bufferedReader().use { it.readText() }
            return Metadata(title = tagValue(xml, "title"), author = tagValue(xml, "creator"), description = tagValue(xml, "description"))
        }
    }

    private suspend fun odt(uri: Uri, fileName: String, versionToken: String?): Metadata {
        val staged = cache.stage(uri, fileName, versionToken)
        ZipFile(staged).use { zip ->
            val meta = zip.getEntry("meta.xml") ?: return Metadata()
            val xml = zip.getInputStream(meta).bufferedReader().use { it.readText() }
            return Metadata(title = tagValue(xml, "title"), author = tagValue(xml, "creator"), description = tagValue(xml, "description"))
        }
    }

    private suspend fun kindle(uri: Uri, fileName: String, versionToken: String?): Metadata {
        val staged = cache.stage(uri, fileName, versionToken)
        val meta = MobiBookParser.extractMetadata(staged)
        return Metadata(title = meta.title, author = meta.author)
    }

    private fun image(uri: Uri): Metadata = Metadata(coverPath = decodeUriBitmap(uri)?.let { saveThumbBitmap(it, uri.toString()) }, pageCount = 1)

    private fun decodeUriBitmap(uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= 28) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    if (info.size.width > 720) {
                        val h = (info.size.height * (720f / info.size.width)).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(720, h)
                    }
                }
            }.getOrNull()
        } else {
            context.contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input) }
        }
    }

    private fun preferredImageName(names: List<String>): String? = names.firstOrNull(::isPreferredCoverName)

    private fun isPreferredCoverName(name: String): Boolean = name.substringAfterLast('/').lowercase() in setOf(
        "cover.jpg", "cover.jpeg", "cover.png", "cover.webp", "front.jpg", "front.jpeg", "front.png", "folder.jpg", "folder.jpeg", "folder.png",
    )

    private fun saveThumbBitmap(source: Bitmap, id: String): String? {
        if (source.width <= 0 || source.height <= 0) { source.recycle(); return null }
        val maxW = 360
        val ratio = (maxW.toFloat() / source.width).coerceAtMost(1f)
        val bitmap = if (ratio < 1f) Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        ) else source
        val dir = File(context.cacheDir, "covers").apply { mkdirs() }
        val out = File(dir, "${key(id)}.jpg")
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        if (bitmap !== source) bitmap.recycle()
        source.recycle()
        return out.absolutePath
    }

    private fun saveThumb(bytes: ByteArray, id: String): String? {
        if (bytes.isEmpty()) return null
        val source = decodeImageBytes(bytes) ?: return null
        return saveThumbBitmap(source, id)
    }

    private fun decodeImageBytes(bytes: ByteArray): Bitmap? {
        val isTiff = bytes.size >= 4 && (
            (bytes[0] == 0x49.toByte() && bytes[1] == 0x49.toByte() && (bytes[2] == 0x2A.toByte() || bytes[2] == 0x2B.toByte()) && bytes[3] == 0x00.toByte()) ||
            (bytes[0] == 0x4D.toByte() && bytes[1] == 0x4D.toByte() && bytes[2] == 0x00.toByte() && (bytes[3] == 0x2A.toByte() || bytes[3] == 0x2B.toByte()))
        )
        if (isTiff) {
            val temp = File.createTempFile("cover-", ".tiff", context.cacheDir)
            return try {
                temp.outputStream().use { it.write(bytes) }
                TiffCoder.decode(temp, 0, sampleSize = 4)
            } finally {
                temp.delete()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decoded = runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    if (info.size.width > 720) {
                        val height = (info.size.height * (720f / info.size.width)).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(720, height)
                    }
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun key(value: String) = value.hashCode().toUInt().toString(16)

    private fun tagValue(xml: String, local: String): String? = Regex(
        "<(?:[\\w-]+:)?$local(?:\\s[^>]*)?>(.*?)</(?:[\\w-]+:)?$local>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    ).find(xml)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), " ")?.replace(Regex("\\s+"), " ")?.trim()
}
