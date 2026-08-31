package app.omnireader.android.reader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class DocumentAndKindleParserTest {
    @Test fun rtfExtractsPlainTextAndUnicode() {
        val rtf = "{\\rtf1\\ansi\\ansicpg1251 Привет\\par Test \\u1046?}"
            .toByteArray(Charset.forName("windows-1251"))
        val text = RtfTextExtractor.extract(rtf)
        assertTrue(text.contains("Test"))
        assertTrue(text.contains("Ж"))
    }

    @Test fun palmDocLiteralAndSpaceShortcutDecode() {
        val input = byteArrayOf('A'.code.toByte(), 'B'.code.toByte(), 0xC3.toByte())
        val decoded = MobiBookParser.palmDocDecompressForTest(input).toString(Charsets.ISO_8859_1)
        assertEquals("AB C", decoded)
    }
}
