package app.omnireader.android.scanner

import android.content.ContentResolver
import android.net.Uri
import app.omnireader.android.core.model.ContentType
import app.omnireader.android.core.model.FileFormat
import java.io.BufferedInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

class FormatDetector(private val resolver: ContentResolver) {
    data class Detection(val format: FileFormat, val contentType: ContentType, val confidence: Int)

    fun detect(uri: Uri, fileName: String, mimeType: String?): Detection {
        val extensionGuess = byExtension(fileName)
        val mimeGuess = byMime(mimeType)
        val magicGuess = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val buffered = BufferedInputStream(input)
                val header = ByteArray(512)
                val read = buffered.read(header)
                detectMagic(header.copyOf(read.coerceAtLeast(0)), uri, extensionGuess.format)
            }
        }.getOrNull()

        return magicGuess
            ?: mimeGuess?.takeIf { it.format != FileFormat.UNKNOWN }
            ?: extensionGuess
    }

    internal fun byExtension(fileName: String): Detection {
        val name = fileName.lowercase()
        val format = when {
            name.endsWith(".fb2.zip") -> FileFormat.FB2_ZIP
            else -> when (name.substringAfterLast('.', "")) {
                "epub" -> FileFormat.EPUB
                "fb2" -> FileFormat.FB2
                "txt" -> FileFormat.TXT
                "html", "htm" -> FileFormat.HTML
                "xhtml" -> FileFormat.XHTML
                "md", "markdown" -> FileFormat.MARKDOWN
                "rtf" -> FileFormat.RTF
                "docx" -> FileFormat.DOCX
                "odt" -> FileFormat.ODT
                "mobi" -> FileFormat.MOBI
                "azw3" -> FileFormat.AZW3
                "pdf" -> FileFormat.PDF
                "djvu" -> FileFormat.DJVU
                "djv" -> FileFormat.DJV
                "cbz" -> FileFormat.CBZ
                "cbr" -> FileFormat.CBR
                "cb7" -> FileFormat.CB7
                "cbt" -> FileFormat.CBT
                "zip" -> FileFormat.ZIP
                "rar" -> FileFormat.RAR
                "7z" -> FileFormat.SEVEN_Z
                "jpg" -> FileFormat.JPG
                "jpeg" -> FileFormat.JPEG
                "png" -> FileFormat.PNG
                "webp" -> FileFormat.WEBP
                "avif" -> FileFormat.AVIF
                "gif" -> FileFormat.GIF
                "bmp" -> FileFormat.BMP
                "tiff" -> FileFormat.TIFF
                "tif" -> FileFormat.TIF
                else -> FileFormat.UNKNOWN
            }
        }
        return Detection(format, contentType(format), 30)
    }

    private fun byMime(mime: String?): Detection? {
        val m = mime?.lowercase()?.substringBefore(';')?.trim() ?: return null
        val format = when (m) {
            "application/epub+zip" -> FileFormat.EPUB
            "application/pdf" -> FileFormat.PDF
            "text/plain" -> FileFormat.TXT
            "text/html" -> FileFormat.HTML
            "application/xhtml+xml" -> FileFormat.XHTML
            "text/markdown", "text/x-markdown" -> FileFormat.MARKDOWN
            "application/rtf", "text/rtf" -> FileFormat.RTF
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> FileFormat.DOCX
            "application/vnd.oasis.opendocument.text" -> FileFormat.ODT
            "application/x-mobipocket-ebook", "application/vnd.amazon.mobi8-ebook" -> FileFormat.MOBI
            "image/vnd.djvu", "image/x-djvu", "application/x-djvu" -> FileFormat.DJVU
            "application/zip", "application/x-zip-compressed" -> FileFormat.ZIP
            "application/vnd.rar", "application/x-rar-compressed" -> FileFormat.RAR
            "application/x-7z-compressed" -> FileFormat.SEVEN_Z
            "application/x-tar" -> FileFormat.CBT
            "image/jpeg" -> FileFormat.JPEG
            "image/png" -> FileFormat.PNG
            "image/webp" -> FileFormat.WEBP
            "image/avif" -> FileFormat.AVIF
            "image/gif" -> FileFormat.GIF
            "image/bmp", "image/x-ms-bmp" -> FileFormat.BMP
            "image/tiff" -> FileFormat.TIFF
            else -> FileFormat.UNKNOWN
        }
        return Detection(format, contentType(format), 50)
    }

    private fun detectMagic(header: ByteArray, uri: Uri, extension: FileFormat): Detection? {
        fun starts(vararg values: Int): Boolean = header.size >= values.size && values.indices.all { header[it].toInt() and 0xff == values[it] }
        fun ascii(start: Int, length: Int): String? = if (header.size >= start + length) String(header, start, length, StandardCharsets.US_ASCII) else null
        return when {
            starts(0x25, 0x50, 0x44, 0x46) -> Detection(FileFormat.PDF, ContentType.PDF, 100)
            ascii(0, 8) == "AT&TFORM" -> Detection(if (extension == FileFormat.DJV) FileFormat.DJV else FileFormat.DJVU, ContentType.DOCUMENT, 100)
            starts(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) -> {
                val f = if (extension == FileFormat.CBR) FileFormat.CBR else FileFormat.RAR
                Detection(f, ContentType.COMIC, 100)
            }
            starts(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> {
                val f = if (extension == FileFormat.CB7) FileFormat.CB7 else FileFormat.SEVEN_Z
                Detection(f, ContentType.COMIC, 100)
            }
            starts(0x50, 0x4B, 0x03, 0x04) || starts(0x50, 0x4B, 0x05, 0x06) || starts(0x50, 0x4B, 0x07, 0x08) -> inspectZip(uri)
            ascii(0, 5)?.startsWith("{\\rtf") == true -> Detection(FileFormat.RTF, ContentType.DOCUMENT, 100)
            header.size >= 68 && ascii(60, 8) == "BOOKMOBI" -> Detection(if (extension == FileFormat.AZW3) FileFormat.AZW3 else FileFormat.MOBI, ContentType.BOOK, 100)
            header.size >= 262 && ascii(257, 5) == "ustar" -> Detection(FileFormat.CBT, ContentType.COMIC, 95)
            starts(0xFF, 0xD8, 0xFF) -> Detection(FileFormat.JPEG, ContentType.IMAGE, 100)
            starts(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> Detection(FileFormat.PNG, ContentType.IMAGE, 100)
            ascii(0, 4) == "GIF8" -> Detection(FileFormat.GIF, ContentType.IMAGE, 100)
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> Detection(FileFormat.WEBP, ContentType.IMAGE, 100)
            starts(0x42, 0x4D) -> Detection(FileFormat.BMP, ContentType.IMAGE, 100)
            starts(0x49, 0x49, 0x2A, 0x00) || starts(0x4D, 0x4D, 0x00, 0x2A) || starts(0x49, 0x49, 0x2B, 0x00) || starts(0x4D, 0x4D, 0x00, 0x2B) -> Detection(FileFormat.TIFF, ContentType.IMAGE, 100)
            isAvif(header) -> Detection(FileFormat.AVIF, ContentType.IMAGE, 100)
            else -> null
        }
    }

    private fun isAvif(header: ByteArray): Boolean {
        if (header.size < 16 || String(header, 4, 4, StandardCharsets.US_ASCII) != "ftyp") return false
        val brands = String(header, 8, (header.size - 8).coerceAtMost(64), StandardCharsets.US_ASCII)
        return brands.contains("avif") || brands.contains("avis")
    }

    private fun inspectZip(uri: Uri): Detection {
        var hasEpubMime = false
        var hasContainer = false
        var hasFb2 = false
        var hasImages = false
        var hasDocx = false
        var hasOdt = false
        resolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var count = 0
                while (count++ < 512) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.lowercase()
                    when {
                        name == "mimetype" -> {
                            val small = ByteArray(96)
                            val n = zip.read(small)
                            if (n > 0) {
                                val value = String(small, 0, n, Charsets.US_ASCII).trim()
                                hasEpubMime = value == "application/epub+zip"
                                hasOdt = value == "application/vnd.oasis.opendocument.text"
                            }
                        }
                        name == "meta-inf/container.xml" -> hasContainer = true
                        name.endsWith(".fb2") -> hasFb2 = true
                        name.startsWith("word/") -> hasDocx = true
                        isImageName(name) -> hasImages = true
                    }
                }
            }
        }
        return when {
            hasEpubMime || hasContainer -> Detection(FileFormat.EPUB, ContentType.BOOK, 100)
            hasFb2 -> Detection(FileFormat.FB2_ZIP, ContentType.BOOK, 95)
            hasDocx -> Detection(FileFormat.DOCX, ContentType.DOCUMENT, 95)
            hasOdt -> Detection(FileFormat.ODT, ContentType.DOCUMENT, 95)
            hasImages -> Detection(FileFormat.CBZ, ContentType.COMIC, 90)
            else -> Detection(FileFormat.ZIP, ContentType.OTHER, 80)
        }
    }

    companion object {
        private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "avif", "gif", "bmp", "tif", "tiff")
        fun isImageName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in imageExtensions

        fun contentType(format: FileFormat): ContentType = when (format) {
            FileFormat.EPUB, FileFormat.FB2, FileFormat.FB2_ZIP, FileFormat.TXT, FileFormat.HTML, FileFormat.XHTML, FileFormat.MARKDOWN, FileFormat.MOBI, FileFormat.AZW3 -> ContentType.BOOK
            FileFormat.PDF -> ContentType.PDF
            FileFormat.CBZ, FileFormat.CBR, FileFormat.CB7, FileFormat.CBT, FileFormat.ZIP, FileFormat.RAR, FileFormat.SEVEN_Z -> ContentType.COMIC
            FileFormat.DOCX, FileFormat.ODT, FileFormat.RTF, FileFormat.DJVU, FileFormat.DJV -> ContentType.DOCUMENT
            FileFormat.IMAGE_FOLDER -> ContentType.MANGA
            FileFormat.JPG, FileFormat.JPEG, FileFormat.PNG, FileFormat.WEBP, FileFormat.AVIF, FileFormat.GIF, FileFormat.BMP, FileFormat.TIFF, FileFormat.TIF -> ContentType.IMAGE
            else -> ContentType.OTHER
        }
    }
}
