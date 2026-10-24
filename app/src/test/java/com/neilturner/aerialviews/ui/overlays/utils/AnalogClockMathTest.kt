package com.neilturner.aerialviews.ui.overlays.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

internal class AnalogClockMathTest {
    private fun angles(
        hours: Int,
        minutes: Int,
        seconds: Int = 0,
        millis: Int = 0,
    ) = AnalogClockMath.handAngles(hours, minutes, seconds, millis)

    private fun assertAngles(
        hour: Float,
        minute: Float,
        second: Float,
        actual: ClockHandAngles,
    ) {
        assertEquals(hour, actual.hour, DELTA, "hour hand")
        assertEquals(minute, actual.minute, DELTA, "minute hand")
        assertEquals(second, actual.second, DELTA, "second hand")
    }

    @Test
    fun `midnight and noon point every hand at 12`() {
        assertAngles(0f, 0f, 0f, angles(0, 0))
        assertAngles(0f, 0f, 0f, angles(12, 0))
    }

    @Test
    fun `whole hours put the hour hand on the hour marks`() {
        assertAngles(30f, 0f, 0f, angles(1, 0))
        assertAngles(90f, 0f, 0f, angles(3, 0))
        assertAngles(180f, 0f, 0f, angles(6, 0))
        assertAngles(270f, 0f, 0f, angles(9, 0))
        assertAngles(330f, 0f, 0f, angles(11, 0))
    }

    @Test
    fun `hour hand uses a 12 hour face`() {
        assertEquals(angles(3, 0).hour, angles(15, 0).hour)
        assertEquals(angles(6, 0).hour, angles(18, 0).hour)
        assertEquals(angles(11, 0).hour, angles(23, 0).hour)
        assertEquals(0f, angles(24, 0).hour)
    }

    @Test
    fun `minute hand moves 6 degrees per minute`() {
        assertEquals(6f, angles(0, 1).minute, DELTA)
        assertEquals(90f, angles(5, 15).minute, DELTA)
        assertEquals(180f, angles(5, 30).minute, DELTA)
        assertEquals(270f, angles(5, 45).minute, DELTA)
        assertEquals(354f, angles(5, 59).minute, DELTA)
    }

    @Test
    fun `second hand moves 6 degrees per second`() {
        assertEquals(6f, angles(0, 0, 1).second, DELTA)
        assertEquals(90f, angles(0, 0, 15).second, DELTA)
        assertEquals(180f, angles(0, 0, 30).second, DELTA)
        assertEquals(354f, angles(0, 0, 59).second, DELTA)
    }

    @Test
    fun `hour hand moves half a degree per minute`() {
        // 3:30 is half way between the 3 and the 4
        assertEquals(105f, angles(3, 30).hour, DELTA)
        assertEquals(195f, angles(6, 30).hour, DELTA)
        assertEquals(97.5f, angles(3, 15).hour, DELTA)
        assertEquals(359.5f, angles(11, 59).hour, DELTA)
    }

    @Test
    fun `minute hand includes the seconds already elapsed`() {
        // 30 seconds is half a minute, which is 3 degrees
        assertEquals(123f, angles(10, 20, 30).minute, DELTA)
        assertEquals(120.1f, angles(10, 20, 1).minute, DELTA)
    }

    @Test
    fun `hour hand includes the seconds already elapsed`() {
        // 30 seconds is 1/120 of an hour, which is a quarter of a degree
        assertEquals(0.25f, angles(0, 0, 30).hour, DELTA)
        assertEquals(105.25f, angles(3, 30, 30).hour, DELTA)
    }

    @Test
    fun `hands include the milliseconds already elapsed`() {
        val halfSecond = angles(0, 0, 0, 500)
        assertEquals(3f, halfSecond.second, DELTA)
        assertEquals(0.05f, halfSecond.minute, DELTA)
        assertEquals(0.004167f, halfSecond.hour, DELTA)

        assertEquals(36.6f, angles(0, 0, 6, 100).second, DELTA)
        assertEquals(0f, angles(0, 0, 0, 0).second, DELTA)
    }

    @Test
    fun `millisecond argument is optional`() {
        assertEquals(AnalogClockMath.handAngles(4, 20, 10, 0), AnalogClockMath.handAngles(4, 20, 10))
    }

    @Test
    fun `angles stay below a full turn just before the hands wrap`() {
        listOf(
            angles(11, 59, 59, 999),
            angles(23, 59, 59, 999),
            angles(0, 59, 59, 999),
            angles(5, 0, 59, 999),
        ).forEach { result ->
            listOf(result.hour, result.minute, result.second).forEach { degrees ->
                assertTrue(degrees >= 0f && degrees < 360f, "$degrees is outside [0, 360) in $result")
            }
        }
    }

    @Test
    fun `second hand keeps turning smoothly through the wrap`() {
        val before = angles(1, 2, 59, 990).second
        val after = angles(1, 3, 0, 10).second
        assertEquals(359.94f, before, DELTA)
        assertEquals(0.06f, after, DELTA)
    }

    @Test
    fun `angles match the closed form for every second of the day`() {
        for (secondOfDay in 0 until SECONDS_PER_DAY) {
            val result = angles(secondOfDay / 3600, secondOfDay / 60 % 60, secondOfDay % 60)

            // 360 degrees takes 12 hours, 1 hour and 1 minute respectively
            assertEquals(secondOfDay % 43_200 / 120f, result.hour, DELTA, "hour hand at second $secondOfDay")
            assertEquals(secondOfDay % 3_600 / 10f, result.minute, DELTA, "minute hand at second $secondOfDay")
            assertEquals(secondOfDay % 60 * 6f, result.second, DELTA, "second hand at second $secondOfDay")
        }
    }

    @Test
    fun `values past the end of a range carry over like a real clock`() {
        // 60 seconds is 1 minute, 75 minutes is 1 hour 15 minutes
        assertAngles(0.5f, 6f, 0f, angles(0, 0, 60))
        assertAngles(67.5f, 90f, 0f, angles(1, 75, 0))
        // 2:59:60.000 plus 1000 ms is 3:00:01
        assertAngles(90.00833f, 0.1f, 6f, angles(2, 59, 60, 1000))
    }

    @Test
    fun `negative values wrap back onto the face`() {
        assertEquals(330f, angles(-1, 0).hour, DELTA)
        assertEquals(354f, angles(0, -1).minute, DELTA)
        assertEquals(354f, angles(0, 0, -1).second, DELTA)

        listOf(angles(-5, -30, -45, -500), angles(-100, 0), angles(0, -1000)).forEach { result ->
            listOf(result.hour, result.minute, result.second).forEach { degrees ->
                assertTrue(degrees >= 0f && degrees < 360f, "$degrees is outside [0, 360) in $result")
            }
        }
    }

    @Test
    fun `next second is a whole second away at most`() {
        assertEquals(1000L, AnalogClockMath.millisUntilNextSecond(0))
        assertEquals(999L, AnalogClockMath.millisUntilNextSecond(1))
        assertEquals(500L, AnalogClockMath.millisUntilNextSecond(500))
        assertEquals(1L, AnalogClockMath.millisUntilNextSecond(999))
    }

    @Test
    fun `next second delay is clamped for out of range input`() {
        assertEquals(1000L, AnalogClockMath.millisUntilNextSecond(-250))
        assertEquals(1L, AnalogClockMath.millisUntilNextSecond(1000))
        assertEquals(1L, AnalogClockMath.millisUntilNextSecond(5000))
    }

    @Test
    fun `next second delay is always positive and never longer than a second`() {
        for (millis in 0..999) {
            val delay = AnalogClockMath.millisUntilNextSecond(millis)
            assertTrue(delay in 1L..1000L, "delay $delay for $millis ms")
            // Waiting that long lands exactly on the next whole second
            assertEquals(1000, millis + delay.toInt())
        }
    }

    @Test
    fun `offsets point up right down and left for the cardinal angles`() {
        val length = 100f

        // 12 o'clock is up, which is negative y on a canvas
        assertEquals(0f, AnalogClockMath.offsetX(0f, length), DELTA)
        assertEquals(-100f, AnalogClockMath.offsetY(0f, length), DELTA)

        assertEquals(100f, AnalogClockMath.offsetX(90f, length), DELTA)
        assertEquals(0f, AnalogClockMath.offsetY(90f, length), DELTA)

        assertEquals(0f, AnalogClockMath.offsetX(180f, length), DELTA)
        assertEquals(100f, AnalogClockMath.offsetY(180f, length), DELTA)

        assertEquals(-100f, AnalogClockMath.offsetX(270f, length), DELTA)
        assertEquals(0f, AnalogClockMath.offsetY(270f, length), DELTA)
    }

    @Test
    fun `offsets turn clockwise`() {
        // 45 degrees is half way between 12 and 3, so up and to the right
        val diagonal = 100f / sqrt(2f)
        assertEquals(diagonal, AnalogClockMath.offsetX(45f, 100f), DELTA)
        assertEquals(-diagonal, AnalogClockMath.offsetY(45f, 100f), DELTA)

        // 225 degrees is half way between 6 and 9, so down and to the left
        assertEquals(-diagonal, AnalogClockMath.offsetX(225f, 100f), DELTA)
        assertEquals(diagonal, AnalogClockMath.offsetY(225f, 100f), DELTA)
    }

    @Test
    fun `offsets scale with the length and a full turn is a no-op`() {
        assertEquals(0f, AnalogClockMath.offsetX(90f, 0f), DELTA)
        assertEquals(50f, AnalogClockMath.offsetX(90f, 50f), DELTA)
        assertEquals(AnalogClockMath.offsetX(0f, 80f), AnalogClockMath.offsetX(360f, 80f), DELTA)
        assertEquals(AnalogClockMath.offsetY(0f, 80f), AnalogClockMath.offsetY(360f, 80f), DELTA)
    }

    @Test
    fun `hour hand points where a real clock points`() {
        val length = 100f

        // 12:00 up, 3:00 right, 6:00 down, 9:00 left
        listOf(
            Triple(12, 0f to -100f, "12 o'clock"),
            Triple(3, 100f to 0f, "3 o'clock"),
            Triple(6, 0f to 100f, "6 o'clock"),
            Triple(9, -100f to 0f, "9 o'clock"),
        ).forEach { (hours, expected, name) ->
            val hour = angles(hours, 0).hour
            assertEquals(expected.first, AnalogClockMath.offsetX(hour, length), DELTA, "x at $name")
            assertEquals(expected.second, AnalogClockMath.offsetY(hour, length), DELTA, "y at $name")
        }
    }

    private companion object {
        const val DELTA = 0.001f
        const val SECONDS_PER_DAY = 86_400
    }
}
