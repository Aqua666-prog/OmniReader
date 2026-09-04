package com.sergey.reader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesInferenceTest {
    @Test
    fun russianVolume() {
        val result = SeriesInference.infer("Монолог фармацевта Том 6")
        assertEquals("Монолог фармацевта", result.series)
        assertEquals(6.0, result.index!!, 0.0)
    }

    @Test
    fun englishVolume() {
        val result = SeriesInference.infer("Spice and Wolf Vol 12")
        assertEquals("Spice and Wolf", result.series)
        assertEquals(12.0, result.index!!, 0.0)
    }

    @Test
    fun plainTitle() {
        val result = SeriesInference.infer("Имя розы")
        assertNull(result.series)
        assertNull(result.index)
    }
}
