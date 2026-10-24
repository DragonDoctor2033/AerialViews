package com.neilturner.aerialviews.models.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WeatherEffectsFrameRateTest {
    @Test
    fun `stored setting values map to their frame rate`() {
        assertEquals(WeatherEffectsFrameRate.FPS_60, WeatherEffectsFrameRate.fromValue("FPS_60"))
        assertEquals(WeatherEffectsFrameRate.FPS_30, WeatherEffectsFrameRate.fromValue("FPS_30"))
    }

    @Test
    fun `every frame rate maps back from its own name`() {
        WeatherEffectsFrameRate.entries.forEach { rate ->
            assertEquals(rate, WeatherEffectsFrameRate.fromValue(rate.name))
        }
    }

    @Test
    fun `missing or unknown values fall back to 30`() {
        listOf(null, "", " ", "60", "fps_60", "FPS_24", "30").forEach { value ->
            assertEquals(WeatherEffectsFrameRate.FPS_30, WeatherEffectsFrameRate.fromValue(value), "value '$value'")
        }
    }

    @Test
    fun `30 frames a second is the default`() {
        assertEquals(WeatherEffectsFrameRate.FPS_30, WeatherEffectsFrameRate.DEFAULT)
        assertEquals(30, WeatherEffectsFrameRate.DEFAULT.fps)
    }

    @Test
    fun `60 frames a second can still be picked, and is not mixed up with the default`() {
        assertEquals(WeatherEffectsFrameRate.FPS_60, WeatherEffectsFrameRate.fromValue("FPS_60"))
        assertEquals(60, WeatherEffectsFrameRate.fromValue("FPS_60").fps)
        assertTrue(WeatherEffectsFrameRate.FPS_60 in WeatherEffectsFrameRate.entries)
        assertNotEquals(WeatherEffectsFrameRate.DEFAULT, WeatherEffectsFrameRate.FPS_60)
    }

    @Test
    fun `the stored names are the ones the settings screen uses`() {
        // These names are written to the preferences file, so they must never change
        assertEquals(listOf("FPS_60", "FPS_30"), WeatherEffectsFrameRate.entries.map { it.name })
    }

    @Test
    fun `each frame rate says how many frames a second it is`() {
        assertEquals(60, WeatherEffectsFrameRate.FPS_60.fps)
        assertEquals(30, WeatherEffectsFrameRate.FPS_30.fps)
    }
}
