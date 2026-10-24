package com.neilturner.aerialviews.ui.overlays.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WallClockSecondsTest {
    @Test
    fun `the wait is the rest of the second`() {
        // The first is 250 ms into its second, so 750 ms are left
        assertEquals(750L, WallClockSeconds.millisUntilNextSecond(1_792_855_509_250L))
        assertEquals(1L, WallClockSeconds.millisUntilNextSecond(1_792_855_509_999L))
        assertEquals(500L, WallClockSeconds.millisUntilNextSecond(1_792_855_509_500L))
        assertEquals(999L, WallClockSeconds.millisUntilNextSecond(1_792_855_509_001L))
    }

    @Test
    fun `on a second boundary the next one is a whole second away`() {
        assertEquals(1000L, WallClockSeconds.millisUntilNextSecond(1_792_855_509_000L))
        assertEquals(1000L, WallClockSeconds.millisUntilNextSecond(0L))
    }

    @Test
    fun `the wait is always between 1 and 1000 ms`() {
        (0L..5_000L step 7).forEach { epochMillis ->
            val wait = WallClockSeconds.millisUntilNextSecond(1_792_855_500_000L + epochMillis)

            assertTrue(wait in 1L..1000L, "wait $wait at +$epochMillis")
        }
    }

    @Test
    fun `waiting that long lands exactly on the next second, whatever the time`() {
        listOf(0L, 1L, 499L, 500L, 999L, 1_000L, 1_792_855_509_250L, 1_792_855_509_999L).forEach { epochMillis ->
            val landing = epochMillis + WallClockSeconds.millisUntilNextSecond(epochMillis)

            assertEquals(0L, landing % 1000L, "from $epochMillis")
            assertEquals(epochMillis / 1000L + 1L, landing / 1000L, "from $epochMillis")
        }
    }

    @Test
    fun `a clock which waits this long every time shows every second once`() {
        // Start at a random fraction of a second, then wake up late by a few ms each time, as a
        // busy main thread would, and take the second which is on the wall clock each time
        var now = 1_792_855_500_873L
        val shown = mutableListOf<Long>()
        val lateBy = listOf(0L, 3L, 18L, 1L, 40L, 0L, 7L, 25L, 2L, 11L)

        lateBy.forEach { late ->
            now += WallClockSeconds.millisUntilNextSecond(now) + late
            shown += now / 1000L
        }

        assertEquals(shown.indices.map { shown.first() + it }, shown)
    }

    @Test
    fun `times before 1970 work too`() {
        // One millisecond before the epoch is 999 ms into the second before it
        assertEquals(1L, WallClockSeconds.millisUntilNextSecond(-1L))
        assertEquals(1000L, WallClockSeconds.millisUntilNextSecond(-1000L))
        assertEquals(1L, WallClockSeconds.millisUntilNextSecond(-1001L))
    }

    @Test
    fun `the extremes of time stay in range`() {
        listOf(Long.MIN_VALUE, Long.MAX_VALUE).forEach { epochMillis ->
            assertTrue(WallClockSeconds.millisUntilNextSecond(epochMillis) in 1L..1000L, "$epochMillis")
        }
    }
}
