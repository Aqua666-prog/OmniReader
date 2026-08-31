package app.omnireader.android.core

import app.omnireader.android.core.util.NaturalSort
import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalSortTest {
    @Test fun sortsPagesNumerically() {
        val input = listOf("10.jpg", "2.jpg", "1.jpg", "page20.png", "page3.png")
        assertEquals(listOf("1.jpg", "2.jpg", "10.jpg", "page3.png", "page20.png"), input.sortedWith(NaturalSort.comparator))
    }
}
