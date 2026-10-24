package com.neilturner.aerialviews.models.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AnalogClockCenterSizeTest {
    @Test
    fun `stored setting values map to their size`() {
        assertEquals(AnalogClockCenterSize.MEDIUM, AnalogClockCenterSize.fromValue("MEDIUM"))
        assertEquals(AnalogClockCenterSize.LARGE, AnalogClockCenterSize.fromValue("LARGE"))
        assertEquals(AnalogClockCenterSize.EXTRA_LARGE, AnalogClockCenterSize.fromValue("EXTRA_LARGE"))
    }

    @Test
    fun `every size maps back from its own name`() {
        AnalogClockCenterSize.entries.forEach { size ->
            assertEquals(size, AnalogClockCenterSize.fromValue(size.name))
        }
    }

    @Test
    fun `missing or unknown values fall back to large`() {
        // SMALL is a size of the clock in a slot, not of the one in the center
        listOf(null, "", " ", "large", "Extra large", "SMALL", "HUGE", "45").forEach { value ->
            assertEquals(AnalogClockCenterSize.LARGE, AnalogClockCenterSize.fromValue(value), "value '$value'")
        }
    }

    @Test
    fun `large is the default size`() {
        assertEquals(AnalogClockCenterSize.LARGE, AnalogClockCenterSize.DEFAULT)
    }

    @Test
    fun `sizes are listed from medium to extra large`() {
        assertEquals(listOf("MEDIUM", "LARGE", "EXTRA_LARGE"), AnalogClockCenterSize.entries.map { it.name })

        val percentages = AnalogClockCenterSize.entries.map { it.percentOfScreenHeight }
        assertEquals(percentages.sorted(), percentages)
        assertEquals(percentages.distinct(), percentages)
    }

    @Test
    fun `sizes are 35, 45 and 55 percent of the screen height`() {
        assertEquals(35, AnalogClockCenterSize.MEDIUM.percentOfScreenHeight)
        assertEquals(45, AnalogClockCenterSize.LARGE.percentOfScreenHeight)
        assertEquals(55, AnalogClockCenterSize.EXTRA_LARGE.percentOfScreenHeight)
    }
}
