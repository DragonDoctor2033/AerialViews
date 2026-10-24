package com.neilturner.aerialviews.models.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AnalogClockSizeTest {
    @Test
    fun `stored setting values map to their size`() {
        assertEquals(AnalogClockSize.SMALL, AnalogClockSize.fromValue("SMALL"))
        assertEquals(AnalogClockSize.MEDIUM, AnalogClockSize.fromValue("MEDIUM"))
        assertEquals(AnalogClockSize.LARGE, AnalogClockSize.fromValue("LARGE"))
    }

    @Test
    fun `every size maps back from its own name`() {
        AnalogClockSize.entries.forEach { size ->
            assertEquals(size, AnalogClockSize.fromValue(size.name))
        }
    }

    @Test
    fun `missing or unknown values fall back to medium`() {
        listOf(null, "", " ", "small", "Huge", "EXTRA_LARGE", "220").forEach { value ->
            assertEquals(AnalogClockSize.MEDIUM, AnalogClockSize.fromValue(value), "value '$value'")
        }
    }

    @Test
    fun `medium is the default size`() {
        assertEquals(AnalogClockSize.MEDIUM, AnalogClockSize.DEFAULT)
    }

    @Test
    fun `sizes are listed from small to large`() {
        assertEquals(listOf("SMALL", "MEDIUM", "LARGE"), AnalogClockSize.entries.map { it.name })

        val diameters = AnalogClockSize.entries.map { it.diameterAt1080p }
        assertEquals(diameters.sorted(), diameters)
        assertEquals(diameters.distinct(), diameters)
    }

    @Test
    fun `medium is about 220 pixels on a 1080p TV`() {
        assertEquals(220, AnalogClockSize.MEDIUM.diameterAt1080p)
        assertTrue(AnalogClockSize.entries.all { it.diameterAt1080p > 0 })
    }
}
