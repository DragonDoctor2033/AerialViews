package com.neilturner.aerialviews.models.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AnalogClockHaloTest {
    @Test
    fun `stored setting values map to their level`() {
        assertEquals(AnalogClockHalo.OFF, AnalogClockHalo.fromValue("OFF"))
        assertEquals(AnalogClockHalo.LIGHT, AnalogClockHalo.fromValue("LIGHT"))
        assertEquals(AnalogClockHalo.MEDIUM, AnalogClockHalo.fromValue("MEDIUM"))
        assertEquals(AnalogClockHalo.STRONG, AnalogClockHalo.fromValue("STRONG"))
    }

    @Test
    fun `every level maps back from its own name`() {
        AnalogClockHalo.entries.forEach { halo ->
            assertEquals(halo, AnalogClockHalo.fromValue(halo.name))
        }
    }

    @Test
    fun `missing or unknown values fall back to medium`() {
        listOf(null, "", " ", "off", "Strong", "HEAVY", "45").forEach { value ->
            assertEquals(AnalogClockHalo.MEDIUM, AnalogClockHalo.fromValue(value), "value '$value'")
        }
    }

    @Test
    fun `medium is the default level`() {
        assertEquals(AnalogClockHalo.MEDIUM, AnalogClockHalo.DEFAULT)
    }

    @Test
    fun `levels are listed from off to strong`() {
        assertEquals(listOf("OFF", "LIGHT", "MEDIUM", "STRONG"), AnalogClockHalo.entries.map { it.name })

        val opacities = AnalogClockHalo.entries.map { it.centreOpacity }
        assertEquals(opacities.sorted(), opacities)
        assertEquals(opacities.distinct(), opacities)
    }

    @Test
    fun `only off is invisible`() {
        assertFalse(AnalogClockHalo.OFF.isVisible)
        assertEquals(0f, AnalogClockHalo.OFF.centreOpacity)

        AnalogClockHalo.entries.filter { it != AnalogClockHalo.OFF }.forEach { halo ->
            assertTrue(halo.isVisible, "$halo")
        }
    }

    @Test
    fun `medium is about 45 percent black in the middle and no level is solid`() {
        assertEquals(0.45f, AnalogClockHalo.MEDIUM.centreOpacity)
        assertTrue(AnalogClockHalo.entries.all { it.centreOpacity in 0f..0.8f })
    }
}
