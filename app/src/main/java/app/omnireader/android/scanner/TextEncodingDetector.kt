package app.omnireader.android.scanner

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object TextEncodingDetector {
    private val candidates = listOf(
        "windows-1251", "KOI8-R", "ISO-8859-5", "windows-1252", "ISO-8859-1"
    )

    fun detect(bytes: ByteArray): Charset {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) return StandardCharsets.UTF_8
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return StandardCharsets.UTF_16LE
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return StandardCharsets.UTF_16BE

        if (isValidUtf8(bytes)) return StandardCharsets.UTF_8

        return candidates
            .map { Charset.forName(it) }
            .maxByOrNull { score(decodeLossy(bytes, it)) }
            ?: StandardCharsets.UTF_8
    }

    fun decode(bytes: ByteArray, charset: Charset = detect(bytes)): String {
        val skip = when {
            charset == StandardCharsets.UTF_8 && bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> 3
            (charset == StandardCharsets.UTF_16LE || charset == StandardCharsets.UTF_16BE) && bytes.size >= 2 -> 2
            else -> 0
        }
        return String(bytes, skip, bytes.size - skip, charset)
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    }

    private fun decodeLossy(bytes: ByteArray, charset: Charset): String = String(bytes, charset)

    private fun score(text: String): Int {
        var score = 0
        for (c in text.take(32_768)) {
            score += when {
                c in 'А'..'я' || c == 'Ё' || c == 'ё' -> 5
                c.isLetterOrDigit() -> 2
                c.isWhitespace() -> 1
                c.code in 0..8 || c.code in 14..31 -> -12
                c == '�' -> -20
                else -> 0
            }
        }
        return score
    }
}
