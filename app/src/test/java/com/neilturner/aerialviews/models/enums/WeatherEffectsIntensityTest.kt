package com.neilturner.aerialviews.models.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WeatherEffectsIntensityTest {
    @Test
    fun `stored setting values map to their level`() {
        assertEquals(WeatherEffectsIntensity.SUBTLE, WeatherEffectsIntensity.fromValue("SUBTLE"))
        assertEquals(WeatherEffectsIntensity.NORMAL, WeatherEffectsIntensity.fromValue("NORMAL"))
        assertEquals(WeatherEffectsIntensity.STRONG, WeatherEffectsIntensity.fromValue("STRONG"))
    }

    @Test
    fun `every level maps back from its own name`() {
        WeatherEffectsIntensity.entries.forEach { level ->
            assertEquals(level, WeatherEffectsIntensity.fromValue(level.name))
        }
    }

    @Test
    fun `missing or unknown values fall back to normal`() {
        listOf(null, "", " ", "subtle", "Strong", "HEAVY", "1.4").forEach { value ->
            assertEquals(WeatherEffectsIntensity.NORMAL, WeatherEffectsIntensity.fromValue(value), "value '$value'")
        }
    }

    @Test
    fun `normal is the default level`() {
        assertEquals(WeatherEffectsIntensity.NORMAL, WeatherEffectsIntensity.DEFAULT)
    }

    @Test
    fun `the stored names are the ones the settings screen uses`() {
        // These names are written to the preferences file, so they must never change
        assertEquals(listOf("SUBTLE", "NORMAL", "STRONG"), WeatherEffectsIntensity.entries.map { it.name })
    }

    @Test
    fun `levels are listed from subtle to strong and normal changes nothing`() {
        val scales = WeatherEffectsIntensity.entries.map { it.scale }

        assertEquals(scales.sorted(), scales)
        assertEquals(scales.distinct(), scales)
        assertEquals(1.0f, WeatherEffectsIntensity.NORMAL.scale)
        assertTrue(WeatherEffectsIntensity.SUBTLE.scale < 1f && WeatherEffectsIntensity.STRONG.scale > 1f)
    }
}
