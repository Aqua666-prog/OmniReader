package app.omnireader.android.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class TextEncodingDetectorTest {
    @Test fun detectsUtf8Bom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Привет".toByteArray()
        assertEquals(StandardCharsets.UTF_8, TextEncodingDetector.detect(bytes))
        assertEquals("Привет", TextEncodingDetector.decode(bytes))
    }

    @Test fun detectsUtf16LeBom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Текст".toByteArray(StandardCharsets.UTF_16LE)
        assertEquals(StandardCharsets.UTF_16LE, TextEncodingDetector.detect(bytes))
        assertEquals("Текст", TextEncodingDetector.decode(bytes))
    }

    @Test fun recognizesCyrillicSingleByteEncoding() {
        val original = "Это проверка русского текста и кодировки"
        val bytes = original.toByteArray(Charset.forName("windows-1251"))
        val decoded = TextEncodingDetector.decode(bytes)
        assertTrue(decoded.contains("русского") || decoded.count { it in 'А'..'я' } > 15)
    }
}
