package com.sergey.reader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUtilTest {
    @Test
    fun stripsFileExtensionAndUnderscores() {
        assertEquals("Kusuriya no Hitorigoto RU Vol 06", TextUtil.fileTitle("Kusuriya_no_Hitorigoto_RU_Vol_06.epub"))
    }

    @Test
    fun countsUnicodeWords() {
        assertEquals(5, TextUtil.wordCount("Маомао пошла в старый дом."))
    }

    @Test
    fun normalizesWhitespace() {
        val text = TextUtil.normalizeParagraph("  один\n   два\tтри  ")
        assertEquals("один два три", text)
    }

    @Test
    fun utf8Decode() {
        val original = "Привет, книга"
        assertTrue(TextUtil.decode(original.toByteArray()).contains("книга"))
    }
}
