package com.sergey.reader.util

import java.nio.charset.Charset

object TextUtil {
    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        }
        val utf8 = bytes.toString(Charsets.UTF_8)
        val badUtf8 = utf8.count { it == '\uFFFD' }
        if (badUtf8 <= 1) return utf8
        return runCatching { bytes.toString(Charset.forName("windows-1251")) }.getOrDefault(utf8)
    }

    fun normalizeParagraph(text: String): String = text
        .replace('\u00A0', ' ')
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\s*\\n\\s*"), " ")
        .trim()

    fun wordCount(text: String): Int = Regex("[\\p{L}\\p{N}’'-]+").findAll(text).count()

    fun fileTitle(displayName: String): String = displayName
        .substringBeforeLast('.', displayName)
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}
