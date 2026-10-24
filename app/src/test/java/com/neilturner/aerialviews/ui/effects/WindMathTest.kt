package com.neilturner.aerialviews.ui.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

internal class WindMathTest {
    private val strong = WindMath.STRONG_WIND_METERS_PER_SECOND

    @Test
    fun `wind from the west blows to the right of the screen`() {
        assertEquals(1f, WindMath.lateralFactor(strong, 270f), 0.0001f)
    }

    @Test
    fun `wind from the east blows to the left of the screen`() {
        assertEquals(-1f, WindMath.lateralFactor(strong, 90f), 0.0001f)
    }

    @Test
    fun `wind from the north or the south blows towards or away from the viewer, not sideways`() {
        assertEquals(0f, WindMath.lateralFactor(strong, 0f), 0.0001f)
        assertEquals(0f, WindMath.lateralFactor(strong, 180f), 0.0001f)
        assertEquals(0f, WindMath.lateralFactor(strong, 360f), 0.0001f)
    }

    @Test
    fun `wind from between is part of the way sideways`() {
        // From the south west it blows to the north east, which is as much right as it is away
        assertEquals(0.7071f, WindMath.lateralFactor(strong, 225f), 0.001f)
        assertEquals(-0.7071f, WindMath.lateralFactor(strong, 135f), 0.001f)
    }

    @Test
    fun `no wind is no tilt, whatever the direction is`() {
        for (degrees in 0..360 step 45) {
            assertEquals(0f, WindMath.lateralFactor(0f, degrees.toFloat()), 0.0001f, "from $degrees")
        }
    }

    @Test
    fun `the stronger the wind the more it pushes, up to a strong breeze`() {
        assertEquals(0.25f, WindMath.lateralFactor(3f, 270f), 0.0001f)
        assertEquals(0.5f, WindMath.lateralFactor(6f, 270f), 0.0001f)
        assertEquals(1f, WindMath.lateralFactor(12f, 270f), 0.0001f)
        assertEquals(1f, WindMath.lateralFactor(30f, 270f), 0.0001f)
    }

    @Test
    fun `a wind which is below zero or not a number does nothing`() {
        assertEquals(0f, WindMath.lateralFactor(-5f, 270f))
        assertEquals(0f, WindMath.lateralFactor(Float.NaN, 270f))
    }

    @Test
    fun `the factor stays between minus 1 and 1 for every speed and direction`() {
        for (speed in listOf(0f, 0.5f, 3f, 8f, 12f, 40f)) {
            for (degrees in 0..720 step 15) {
                val factor = WindMath.lateralFactor(speed, degrees.toFloat())
                assertTrue(factor in -1f..1f, "speed $speed from $degrees: $factor")
            }
        }
    }

    @Test
    fun `directions more than a full turn round are the same direction`() {
        assertEquals(WindMath.lateralFactor(8f, 270f), WindMath.lateralFactor(8f, 630f), 0.0001f)
        assertEquals(WindMath.lateralFactor(8f, 100f), WindMath.lateralFactor(8f, 460f), 0.0001f)
    }

    @Test
    fun `rain without wind falls straight down`() {
        assertEquals(0f, WindMath.rainTilt(0f))
    }

    @Test
    fun `rain is tilted at most 35 degrees`() {
        val tangent = Math.tan(Math.toRadians(35.0)).toFloat()

        assertEquals(tangent, WindMath.rainTilt(1f), 0.0001f)
        assertEquals(-tangent, WindMath.rainTilt(-1f), 0.0001f)
        assertEquals(0.7002f, WindMath.rainTilt(1f), 0.001f)
    }

    @Test
    fun `rain is tilted to the side the wind blows to`() {
        assertTrue(WindMath.rainTilt(0.5f) > 0f)
        assertTrue(WindMath.rainTilt(-0.5f) < 0f)
        assertEquals(WindMath.rainTilt(0.5f), -WindMath.rainTilt(-0.5f), 0.0001f)
        assertEquals(0.3153f, WindMath.rainTilt(0.5f), 0.001f)
    }

    @Test
    fun `the tilt keeps growing with the wind`() {
        var previous = -10f
        for (step in -10..10) {
            val tilt = WindMath.rainTilt(step / 10f)
            assertTrue(tilt > previous, "at $step")
            previous = tilt
        }
    }

    @Test
    fun `a factor beyond the range does not tilt more than the most`() {
        assertEquals(WindMath.rainTilt(1f), WindMath.rainTilt(5f))
        assertEquals(WindMath.rainTilt(-1f), WindMath.rainTilt(-5f))
        assertTrue(abs(WindMath.rainTilt(100f)) < 1f)
    }
}
