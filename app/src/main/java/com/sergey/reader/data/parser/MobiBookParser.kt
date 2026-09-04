package com.sergey.reader.data.parser

import android.content.Context
import android.net.Uri
import com.sergey.reader.model.ParsedBook
import com.sergey.reader.util.TextUtil

/**
 * Lightweight DRM-free PalmDOC/MOBI reader.
 *
 * It intentionally refuses encrypted books and HUFF/CDIC compression instead of
 * returning corrupted text. Most classic .mobi files use PalmDOC compression.
 * AZW/AZW3 are accepted when their text records use one of the supported
 * compression modes.
 */
class MobiBookParser(private val context: Context) : BookParser {
    override suspend fun parse(uri: Uri, displayName: String): ParsedBook {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ParsedBook(TextUtil.fileTitle(displayName))
        if (bytes.size < 100) throw IllegalArgumentException("Файл MOBI/AZW слишком короткий или повреждён")

        val recordCount = u16(bytes, 76)
        if (recordCount < 2) throw IllegalArgumentException("В MOBI/AZW не найдены текстовые записи")
        val offsets = IntArray(recordCount)
        for (i in 0 until recordCount) {
            val pos = 78 + i * 8
            if (pos + 4 > bytes.size) throw IllegalArgumentException("Повреждена таблица записей MOBI")
            offsets[i] = u32(bytes, pos).coerceIn(0, bytes.size)
        }

        val record0Start = offsets[0]
        val record0End = offsets.getOrNull(1)?.coerceAtLeast(record0Start) ?: bytes.size
        if (record0End - record0Start < 16) throw IllegalArgumentException("Повреждён заголовок PalmDOC")

        val compression = u16(bytes, record0Start)
        val textRecordCount = u16(bytes, record0Start + 8).coerceAtMost(recordCount - 1)
        val encryption = u16(bytes, record0Start + 12)
        if (encryption != 0) {
            throw IllegalArgumentException("Зашифрованные/DRM MOBI и AZW не поддерживаются")
        }
        if (compression != 1 && compression != 2) {
            throw IllegalArgumentException(
                "Эта книга использует HUFF/CDIC или другой вариант сжатия MOBI. " +
                    "Нужен расширенный MOBI-бэкенд."
            )
        }

        val out = java.io.ByteArrayOutputStream()
        for (i in 1..textRecordCount) {
            val start = offsets.getOrNull(i) ?: break
            val end = offsets.getOrNull(i + 1) ?: bytes.size
            if (start !in 0..bytes.size || end !in 0..bytes.size || end < start) continue
            val record = bytes.copyOfRange(start, end)
            val decoded = if (compression == 1) record else decompressPalmDoc(record)
            out.write(decoded)
        }

        var text = ParserTextTools.decode(out.toByteArray())
        // MOBI records often contain zero padding and a few binary trailer bytes.
        text = text.trimEnd('\u0000', '\u0001', '\u0002', '\u0003', '\u0004', '\u0005', '\u0006', '\u0007', '\u0008')

        val title = readMobiTitle(bytes, record0Start, record0End)
            ?.takeIf { it.isNotBlank() }
            ?: TextUtil.fileTitle(displayName)

        return if (text.contains('<') && text.contains('>')) {
            ParserTextTools.htmlToBook(text, displayName, title)
        } else {
            ParserTextTools.plainTextToBook(text, displayName, title)
        }
    }

    private fun decompressPalmDoc(input: ByteArray): ByteArray {
        val out = ArrayList<Byte>(input.size * 2)
        var i = 0
        while (i < input.size) {
            val c = input[i].toInt() and 0xFF
            when {
                c == 0 || c in 9..0x7F -> {
                    out += c.toByte()
                    i++
                }
                c in 1..8 -> {
                    i++
                    repeat(c) {
                        if (i < input.size) out += input[i++]
                    }
                }
                c in 0x80..0xBF -> {
                    if (i + 1 >= input.size) break
                    val pair = (c shl 8) or (input[i + 1].toInt() and 0xFF)
                    i += 2
                    val distance = (pair shr 3) and 0x7FF
                    val length = (pair and 0x7) + 3
                    if (distance <= 0 || distance > out.size) continue
                    repeat(length) {
                        val source = out.size - distance
                        if (source in out.indices) out += out[source]
                    }
                }
                else -> {
                    out += 0x20
                    out += (c xor 0x80).toByte()
                    i++
                }
            }
        }
        return ByteArray(out.size) { out[it] }
    }

    private fun readMobiTitle(bytes: ByteArray, record0Start: Int, record0End: Int): String? {
        val mobi = record0Start + 16
        if (mobi + 4 > record0End) return null
        val magic = bytes.copyOfRange(mobi, (mobi + 4).coerceAtMost(bytes.size)).toString(Charsets.US_ASCII)
        if (magic != "MOBI") return null
        if (mobi + 0x5C > record0End) return null
        val nameOffset = u32(bytes, mobi + 0x54)
        val nameLength = u32(bytes, mobi + 0x58)
        val start = record0Start + nameOffset
        val end = start + nameLength
        if (start < record0Start || end > bytes.size || start >= end) return null
        return runCatching { bytes.copyOfRange(start, end).toString(Charsets.UTF_8).trim('\u0000', ' ') }.getOrNull()
    }

    private fun u16(data: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun u32(data: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > data.size) return 0
        val value = ((data[offset].toLong() and 0xFF) shl 24) or
            ((data[offset + 1].toLong() and 0xFF) shl 16) or
            ((data[offset + 2].toLong() and 0xFF) shl 8) or
            (data[offset + 3].toLong() and 0xFF)
        return value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
