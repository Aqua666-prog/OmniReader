package app.omnireader.android.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesParserTest {
    @Test fun extractsVolume() {
        val result = SeriesParser.parse("Spice and Wolf 03.epub")
        assertEquals("Spice and Wolf", result.series)
        assertEquals(3.0, result.volume!!, 0.0)
    }

    @Test fun usesParentAsSeriesFallback() {
        val result = SeriesParser.parse("Prologue.fb2", "Chronicles")
        assertEquals("Chronicles", result.series)
    }
}
