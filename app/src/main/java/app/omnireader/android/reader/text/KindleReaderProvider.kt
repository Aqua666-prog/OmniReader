package app.omnireader.android.reader.text

import android.content.Context
import android.net.Uri
import android.text.Html
import app.omnireader.android.core.cache.SafFileCache
import app.omnireader.android.core.model.FileFormat
import app.omnireader.android.data.db.LibraryItemEntity
import app.omnireader.android.reader.ReaderProvider
import app.omnireader.android.reader.ReaderSession
import app.omnireader.android.reader.TextChapter
import app.omnireader.android.reader.TextReaderSession
import app.omnireader.android.reader.UnsupportedReaderException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

class KindleReaderProvider(
    private val context: Context,
    private val cache: SafFileCache,
) : ReaderProvider {
    override val id = "kindle"
    override fun supports(format: FileFormat) = format in setOf(FileFormat.MOBI, FileFormat.AZW3)

    override suspend fun open(item: LibraryItemEntity): ReaderSession = withContext(Dispatchers.IO) {
        val staged = cache.stage(Uri.parse(item.uri), item.fileName, "${item.lastModified}:${item.fileSize}")
        val book = MobiBookParser.parse(staged)
        val plain = Html.fromHtml(book.html, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('\u0000', ' ')
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        if (plain.isBlank()) error("MOBI/AZW3 не содержит читаемого текста")
        TextReaderSession(item, splitChapters(item.title, plain))
    }

    private fun splitChapters(defaultTitle: String, text: String): List<TextChapter> {
        val chunks = text.split('\u000c').map(String::trim).filter(String::isNotBlank)
        if (chunks.size <= 1) return listOf(TextChapter(defaultTitle, text))
        return chunks.mapIndexed { index, chunk -> TextChapter("Глава ${index + 1}", chunk) }
    }
}

internal object MobiBookParser {
    data class ParsedMetadata(val title: String?, val author: String?)

    data class ParsedBook(
        val title: String?,
        val author: String?,
        val html: String,
    )


    fun extractMetadata(file: File): ParsedMetadata = RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < 86) return@use ParsedMetadata(null, null)
        val header = ByteArray(78)
        raf.readFully(header)
        val recordCount = u16(header, 76)
        if (recordCount <= 0 || recordCount > 100_000) return@use ParsedMetadata(null, null)
        raf.seek(78)
        val firstOffset = readU32(raf)
        raf.skipBytes(4)
        val endOffset = if (recordCount > 1) readU32(raf) else raf.length()
        val length = endOffset - firstOffset
        if (firstOffset < 0 || length < 24 || length > 1024 * 1024 || endOffset > raf.length()) {
            return@use ParsedMetadata(null, null)
        }
        val record0 = ByteArray(length.toInt())
        raf.seek(firstOffset)
        raf.readFully(record0)
        val mobi = record0.size >= 32 && record0.copyOfRange(16, 20).toString(Charsets.US_ASCII) == "MOBI"
        if (!mobi) return@use ParsedMetadata(null, null)
        val encoding = u32(record0, 28).toInt()
        val charset = when (encoding) {
            65001 -> Charsets.UTF_8
            1252 -> Charset.forName("windows-1252")
            else -> runCatching { Charset.forName("windows-$encoding") }.getOrElse { Charsets.UTF_8 }
        }
        ParsedMetadata(
            title = fullName(record0, charset) ?: exthValue(record0, 503, charset),
            author = exthValue(record0, 100, charset),
        )
    }

    fun parse(file: File): ParsedBook = RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < 86) throw IllegalArgumentException("MOBI/AZW3 повреждён: слишком короткий файл")
        val header = ByteArray(78)
        raf.readFully(header)
        val recordCount = u16(header, 76)
        if (recordCount < 2 || recordCount > 100_000) throw IllegalArgumentException("MOBI/AZW3: некорректная таблица записей")

        val offsets = LongArray(recordCount + 1)
        raf.seek(78)
        repeat(recordCount) { index ->
            offsets[index] = readU32(raf)
            raf.skipBytes(4) // attributes + unique id
        }
        offsets[recordCount] = raf.length()
        for (i in 0 until recordCount - 1) {
            val a = offsets[i]
            val b = offsets[i + 1]
            if (a < 0 || a >= b || b > raf.length()) throw IllegalArgumentException("MOBI/AZW3: повреждена таблица записей")
        }
        if (offsets[recordCount - 1] < 0 || offsets[recordCount - 1] >= raf.length()) {
            throw IllegalArgumentException("MOBI/AZW3: повреждена последняя запись")
        }

        val record0 = readRecord(raf, offsets, 0, maxBytes = 1024 * 1024)
        if (record0.size < 16) throw IllegalArgumentException("MOBI/AZW3: отсутствует PalmDOC header")
        val compression = u16(record0, 0)
        val textLength = u32(record0, 4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val textRecords = u16(record0, 8)
        val encryption = u16(record0, 12)
        if (encryption != 0) throw UnsupportedReaderException("MOBI/AZW3 защищён DRM. OmniReader читает только DRM-free файлы")
        if (textRecords <= 0 || textRecords >= recordCount) throw IllegalArgumentException("MOBI/AZW3: некорректное число текстовых записей")
        if (compression !in setOf(1, 2)) {
            val label = if (compression == 17480) "HUFF/CDIC" else "тип $compression"
            throw UnsupportedReaderException("MOBI/AZW3 использует $label. Этот вариант компрессии пока не декодируется")
        }

        val mobi = record0.size >= 24 && record0.copyOfRange(16, 20).toString(Charsets.US_ASCII) == "MOBI"
        val encoding = if (mobi && record0.size >= 32) u32(record0, 28).toInt() else 1252
        val charset = when (encoding) {
            65001 -> Charsets.UTF_8
            1252 -> Charset.forName("windows-1252")
            else -> runCatching { Charset.forName("windows-$encoding") }.getOrElse { Charsets.UTF_8 }
        }
        val title = if (mobi) fullName(record0, charset) ?: exthValue(record0, 503, charset) else null
        val author = if (mobi) exthValue(record0, 100, charset) else null

        val out = ByteArrayBuilder(textLength.coerceAtLeast(1024).coerceAtMost(2 * 1024 * 1024))
        for (i in 1..textRecords) {
            val record = readRecord(raf, offsets, i, maxBytes = 32 * 1024 * 1024)
            val decoded = if (compression == 1) record else palmDocDecompress(record)
            val remaining = textLength - out.size
            if (remaining <= 0) break
            out.append(decoded, 0, decoded.size.coerceAtMost(remaining))
        }
        ParsedBook(title, author, out.toByteArray().toString(charset))
    }

    private fun fullName(record0: ByteArray, charset: Charset): String? {
        if (record0.size < 16 + 0x5c) return null
        val offset = u32(record0, 16 + 0x54).toInt()
        val length = u32(record0, 16 + 0x58).toInt()
        if (offset < 0 || length <= 0 || offset > record0.size - length) return null
        return record0.copyOfRange(offset, offset + length).toString(charset).trim('\u0000', ' ').takeIf(String::isNotBlank)
    }

    private fun exthValue(record0: ByteArray, wantedType: Int, charset: Charset): String? {
        if (record0.size < 16 + 0x84 || record0.copyOfRange(16, 20).toString(Charsets.US_ASCII) != "MOBI") return null
        val mobiLength = u32(record0, 20).toInt()
        if (mobiLength <= 0 || 16 + mobiLength + 12 > record0.size) return null
        val flags = u32(record0, 16 + 0x80).toInt()
        if (flags and 0x40 == 0) return null
        val start = 16 + mobiLength
        if (record0.copyOfRange(start, start + 4).toString(Charsets.US_ASCII) != "EXTH") return null
        val length = u32(record0, start + 4).toInt()
        val count = u32(record0, start + 8).toInt().coerceIn(0, 10_000)
        val end = (start + length).coerceAtMost(record0.size)
        var pos = start + 12
        repeat(count) {
            if (pos + 8 > end) return null
            val type = u32(record0, pos).toInt()
            val recLen = u32(record0, pos + 4).toInt()
            if (recLen < 8 || pos > end - recLen) return null
            if (type == wantedType) {
                return record0.copyOfRange(pos + 8, pos + recLen).toString(charset).trim('\u0000', ' ').takeIf(String::isNotBlank)
            }
            pos += recLen
        }
        return null
    }

    internal fun palmDocDecompressForTest(input: ByteArray): ByteArray = palmDocDecompress(input)

    private fun palmDocDecompress(input: ByteArray): ByteArray {
        val out = ByteArrayBuilder((input.size * 2).coerceAtMost(8 * 1024 * 1024))
        var i = 0
        while (i < input.size) {
            val b = input[i].toInt() and 0xff
            when {
                b in 1..8 -> {
                    val count = b.coerceAtMost(input.size - i - 1)
                    if (count <= 0) break
                    out.append(input, i + 1, count)
                    i += count + 1
                }
                b <= 0x7f -> {
                    out.append(b.toByte())
                    i++
                }
                b >= 0xc0 -> {
                    out.append(' '.code.toByte())
                    out.append((b xor 0x80).toByte())
                    i++
                }
                else -> {
                    if (i + 1 >= input.size) break
                    val pair = (b shl 8) or (input[i + 1].toInt() and 0xff)
                    val distance = (pair shr 3) and 0x7ff
                    val length = (pair and 0x7) + 3
                    if (distance <= 0 || distance > out.size) throw IllegalArgumentException("MOBI: повреждён PalmDOC back-reference")
                    repeat(length) { out.append(out[out.size - distance]) }
                    i += 2
                }
            }
        }
        return out.toByteArray()
    }

    private fun readRecord(raf: RandomAccessFile, offsets: LongArray, index: Int, maxBytes: Int): ByteArray {
        val start = offsets[index]
        val end = offsets[index + 1]
        val length = end - start
        if (length < 0 || length > maxBytes) throw IllegalArgumentException("MOBI/AZW3: запись слишком велика или повреждена")
        val bytes = ByteArray(length.toInt())
        raf.seek(start)
        raf.readFully(bytes)
        return bytes
    }

    private fun u16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
    }

    private fun u32(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) return 0
        return ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)
    }

    private fun readU32(raf: RandomAccessFile): Long = raf.readInt().toLong() and 0xffff_ffffL

    private class ByteArrayBuilder(initialCapacity: Int) {
        private var data = ByteArray(initialCapacity.coerceAtLeast(64))
        var size: Int = 0
            private set

        operator fun get(index: Int): Byte = data[index]

        fun append(value: Byte) {
            ensure(1)
            data[size++] = value
        }

        fun append(bytes: ByteArray, offset: Int, length: Int) {
            if (length <= 0) return
            ensure(length)
            bytes.copyInto(data, size, offset, offset + length)
            size += length
        }

        fun toByteArray(): ByteArray = data.copyOf(size)

        private fun ensure(additional: Int) {
            val wanted = size + additional
            if (wanted <= data.size) return
            var next = data.size
            while (next < wanted) next = (next * 2).coerceAtLeast(wanted)
            data = data.copyOf(next)
        }
    }
}
