package com.neilturner.aerialviews.models.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AnalogClockSecondHandMotionTest {
    @Test
    fun `stored setting values map to their motion`() {
        assertEquals(AnalogClockSecondHandMotion.SMOOTH, AnalogClockSecondHandMotion.fromValue("SMOOTH"))
        assertEquals(AnalogClockSecondHandMotion.TICK, AnalogClockSecondHandMotion.fromValue("TICK"))
    }

    @Test
    fun `every motion maps back from its own name`() {
        AnalogClockSecondHandMotion.entries.forEach { motion ->
            assertEquals(motion, AnalogClockSecondHandMotion.fromValue(motion.name))
        }
    }

    @Test
    fun `missing or unknown values fall back to the smooth sweep`() {
        listOf(null, "", " ", "smooth", "Tick", "SWEEP", "STEP", "1").forEach { value ->
            assertEquals(AnalogClockSecondHandMotion.SMOOTH, AnalogClockSecondHandMotion.fromValue(value), "value '$value'")
        }
    }

    @Test
    fun `the smooth sweep is the default motion`() {
        assertEquals(AnalogClockSecondHandMotion.SMOOTH, AnalogClockSecondHandMotion.DEFAULT)
    }

    @Test
    fun `the stored names are the ones the settings screen uses`() {
        // These names are written to the preferences file, so they must never change
        assertEquals(listOf("SMOOTH", "TICK"), AnalogClockSecondHandMotion.entries.map { it.name })
    }
}
