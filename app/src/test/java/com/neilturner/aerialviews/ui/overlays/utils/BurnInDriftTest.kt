package com.neilturner.aerialviews.ui.overlays.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.hypot

internal class BurnInDriftTest {
    // Two days of minutes, and a day before the start of the count
    private val minutes = (-1_440L..2_880L)

    private fun distance(
        from: DriftOffset,
        to: DriftOffset,
    ): Float = hypot(to.x - from.x, to.y - from.y)

    @Test
    fun `the clock is never moved further than 8 dp`() {
        assertEquals(8f, BurnInDrift.MAX_OFFSET_DP)

        minutes.forEach { minute ->
            val offset = BurnInDrift.offsetDp(minute)

            assertTrue(abs(offset.x) <= BurnInDrift.MAX_OFFSET_DP, "x at minute $minute is ${offset.x}")
            assertTrue(abs(offset.y) <= BurnInDrift.MAX_OFFSET_DP, "y at minute $minute is ${offset.y}")
            assertTrue(hypot(offset.x, offset.y) <= BurnInDrift.MAX_OFFSET_DP, "distance at minute $minute")
        }
    }

    @Test
    fun `the clock moves by a few dp, enough to spread the burn-in`() {
        val widest = minutes.maxOf { hypot(BurnInDrift.offsetDp(it).x, BurnInDrift.offsetDp(it).y) }

        assertTrue(widest >= 3f, "widest is $widest")
        assertEquals(BurnInDrift.ORBIT_RADIUS_DP, widest, 0.001f)
    }

    @Test
    fun `the clock moves every minute`() {
        minutes.zipWithNext().forEach { (minute, next) ->
            assertNotEquals(BurnInDrift.offsetDp(minute), BurnInDrift.offsetDp(next), "minute $minute to $next")
        }
    }

    @Test
    fun `the clock moves slowly, less than 2 dp at a time`() {
        minutes.zipWithNext().forEach { (minute, next) ->
            val step = distance(BurnInDrift.offsetDp(minute), BurnInDrift.offsetDp(next))

            assertTrue(step < 2f, "step from minute $minute to $next is $step dp")
        }
    }

    @Test
    fun `the pattern repeats every 24 minutes`() {
        assertEquals(24, BurnInDrift.ORBIT_STEPS)

        minutes.forEach { minute ->
            assertEquals(BurnInDrift.offsetDp(minute), BurnInDrift.offsetDp(minute + BurnInDrift.ORBIT_STEPS), "minute $minute")
        }
    }

    @Test
    fun `a lap visits 24 different spots`() {
        val spots = (0L until BurnInDrift.ORBIT_STEPS).map { BurnInDrift.offsetDp(it) }

        assertEquals(24, spots.distinct().size)
    }

    @Test
    fun `the spots are on a circle round the real position`() {
        (0L until BurnInDrift.ORBIT_STEPS).forEach { minute ->
            val offset = BurnInDrift.offsetDp(minute)

            assertEquals(BurnInDrift.ORBIT_RADIUS_DP, hypot(offset.x, offset.y), 0.001f, "minute $minute")
        }
    }

    @Test
    fun `on average the clock is exactly where it belongs`() {
        val lap = (0L until BurnInDrift.ORBIT_STEPS).map { BurnInDrift.offsetDp(it) }

        assertEquals(0f, lap.map { it.x }.average().toFloat(), 0.001f)
        assertEquals(0f, lap.map { it.y }.average().toFloat(), 0.001f)
    }

    @Test
    fun `the clock goes both ways in both directions`() {
        val lap = (0L until BurnInDrift.ORBIT_STEPS).map { BurnInDrift.offsetDp(it) }

        assertTrue(lap.any { it.x > 5f }, "right")
        assertTrue(lap.any { it.x < -5f }, "left")
        assertTrue(lap.any { it.y > 5f }, "down")
        assertTrue(lap.any { it.y < -5f }, "up")
    }

    @Test
    fun `minutes before the start of the count work the same way`() {
        assertEquals(BurnInDrift.offsetDp(23), BurnInDrift.offsetDp(-1))
        assertEquals(BurnInDrift.offsetDp(0), BurnInDrift.offsetDp(-24))
        assertEquals(BurnInDrift.offsetDp(1), BurnInDrift.offsetDp(-23))
    }

    @Test
    fun `the biggest and smallest minutes stay within the limit`() {
        listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0L).forEach { minute ->
            val offset = BurnInDrift.offsetDp(minute)

            assertTrue(hypot(offset.x, offset.y) <= BurnInDrift.MAX_OFFSET_DP, "minute $minute")
            assertTrue(offset.x.isFinite() && offset.y.isFinite(), "minute $minute")
        }
    }

    @Test
    fun `the real time works as the minute`() {
        // 2026-10-24 15:30:00 UTC, in milliseconds from 1970, divided into minutes as the clock does
        val minute = 1_792_855_800_000L / 60_000L
        val offset = BurnInDrift.offsetDp(minute)

        assertEquals(BurnInDrift.offsetDp(minute % 24), offset)
        assertNotEquals(offset, BurnInDrift.offsetDp(minute + 1))
    }

    @Test
    fun `pixels are whole numbers scaled by the density`() {
        listOf(1f, 1.5f, 2f, 3f).forEach { density ->
            (0L until BurnInDrift.ORBIT_STEPS).forEach { minute ->
                val dp = BurnInDrift.offsetDp(minute)
                val px = BurnInDrift.offsetPx(minute, density)

                assertEquals(Math.round(dp.x * density).toFloat(), px.x, "x at minute $minute, density $density")
                assertEquals(Math.round(dp.y * density).toFloat(), px.y, "y at minute $minute, density $density")
            }
        }
    }

    @Test
    fun `the four quarters of a lap are right, up, left and down by 12 pixels on a 1080p TV`() {
        assertEquals(DriftOffset(12f, 0f), BurnInDrift.offsetPx(0, 2f))
        // Down is positive on a screen
        assertEquals(DriftOffset(0f, 12f), BurnInDrift.offsetPx(6, 2f))
        assertEquals(DriftOffset(-12f, 0f), BurnInDrift.offsetPx(12, 2f))
        assertEquals(DriftOffset(0f, -12f), BurnInDrift.offsetPx(18, 2f))
    }

    @Test
    fun `the shift on a 1080p TV is at most 8 dp which is 16 pixels`() {
        minutes.forEach { minute ->
            val px = BurnInDrift.offsetPx(minute, 2f)

            assertTrue(abs(px.x) <= 16f && abs(px.y) <= 16f, "minute $minute is $px")
            assertTrue(hypot(px.x, px.y) <= 16f, "minute $minute is $px")
        }
    }

    @Test
    fun `an unusable density counts as one pixel per dp`() {
        listOf(0f, -2f, Float.NaN, Float.POSITIVE_INFINITY).forEach { density ->
            (0L until BurnInDrift.ORBIT_STEPS).forEach { minute ->
                assertEquals(BurnInDrift.offsetPx(minute, 1f), BurnInDrift.offsetPx(minute, density), "minute $minute, density $density")
            }
        }
    }
}
