package com.neilturner.aerialviews.ui.overlays.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.min

/**
 * The angles of the hands of a clock which sweeps, which are worked out from the instant in
 * milliseconds since 1970 and the offset of the time zone, on every frame.
 */
internal class AnalogClockMathSweepTest {
    // Milliseconds since midnight, for a time of the day
    private fun at(
        hours: Int,
        minutes: Int = 0,
        seconds: Int = 0,
        millis: Int = 0,
    ): Int = ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis

    private fun epoch(utc: String): Long = Instant.parse(utc).toEpochMilli()

    // Distance between two angles round the face, so 359.99 and 0.01 are close
    private fun distance(
        a: Float,
        b: Float,
    ): Float {
        val difference = abs(a - b) % 360f
        return min(difference, 360f - difference)
    }

    private fun assertAngle(
        expected: Float,
        actual: Float,
        message: String,
    ) {
        assertTrue(distance(expected, actual) <= DELTA, "$message: expected $expected, was $actual")
    }

    // Everything the clock does on a frame, in one go: the three angles for an instant in a time zone
    private fun anglesAt(
        epochMillis: Long,
        zone: TimeZone,
    ): List<Float> {
        val millisOfDay = AnalogClockMath.millisOfDay(epochMillis, zone.getOffset(epochMillis))
        return listOf(
            AnalogClockMath.hourAngle(millisOfDay),
            AnalogClockMath.minuteAngle(millisOfDay),
            AnalogClockMath.secondAngle(millisOfDay),
        )
    }

    @Test
    fun `midnight is the start of the day`() {
        assertEquals(0, AnalogClockMath.millisOfDay(0L, 0))
        assertEquals(0, AnalogClockMath.millisOfDay(DAY.toLong(), 0))
        assertEquals(0, AnalogClockMath.millisOfDay(5L * DAY, 0))
        assertEquals(0, AnalogClockMath.millisOfDay(epoch("2026-10-24T00:00:00Z"), 0))
    }

    @Test
    fun `the last millisecond of the day is just before midnight`() {
        assertEquals(DAY - 1, AnalogClockMath.millisOfDay(DAY - 1L, 0))
        assertEquals(DAY - 1, AnalogClockMath.millisOfDay(3L * DAY - 1L, 0))
        assertEquals(at(23, 59, 59, 999), AnalogClockMath.millisOfDay(epoch("2026-10-24T23:59:59.999Z"), 0))
    }

    @Test
    fun `east of Greenwich the day is ahead of UTC`() {
        // Moscow, 3 hours ahead
        assertEquals(at(3), AnalogClockMath.millisOfDay(0L, 3 * HOUR))
        // India, 5 hours 30 minutes ahead
        assertEquals(at(5, 30), AnalogClockMath.millisOfDay(0L, 5 * HOUR + 30 * MINUTE))
        // Nepal, 5 hours 45 minutes ahead
        assertEquals(at(14, 45, 12, 250), AnalogClockMath.millisOfDay(epoch("2026-10-24T09:00:12.250Z"), 5 * HOUR + 45 * MINUTE))
    }

    @Test
    fun `west of Greenwich the day is behind UTC`() {
        // New York in winter, 5 hours behind
        assertEquals(at(19), AnalogClockMath.millisOfDay(0L, -5 * HOUR))
        assertEquals(at(7, 30, 15), AnalogClockMath.millisOfDay(epoch("2026-01-15T12:30:15Z"), -5 * HOUR))
    }

    @Test
    fun `an offset takes the day over midnight in both directions`() {
        // 23:30 UTC is 01:30 the next day, 2 hours ahead
        assertEquals(at(1, 30), AnalogClockMath.millisOfDay(epoch("2026-10-24T23:30:00Z"), 2 * HOUR))
        // 00:30 UTC is 23:30 the day before, an hour behind
        assertEquals(at(23, 30), AnalogClockMath.millisOfDay(epoch("2026-10-24T00:30:00Z"), -HOUR))
        // Exactly local midnight
        assertEquals(0, AnalogClockMath.millisOfDay(epoch("2026-10-23T21:00:00Z"), 3 * HOUR))
        assertEquals(0, AnalogClockMath.millisOfDay(epoch("2026-10-24T05:00:00Z"), -5 * HOUR))
    }

    @Test
    fun `instants before 1970 work too`() {
        assertEquals(DAY - 1, AnalogClockMath.millisOfDay(-1L, 0))
        assertEquals(0, AnalogClockMath.millisOfDay(-DAY.toLong(), 0))
        assertEquals(DAY - 1, AnalogClockMath.millisOfDay(-DAY - 1L, 0))
        // A millisecond before the epoch, an hour ahead of UTC
        assertEquals(HOUR - 1, AnalogClockMath.millisOfDay(-1L, HOUR))
        assertEquals(at(23, 59, 59, 999), AnalogClockMath.millisOfDay(epoch("1969-12-31T23:59:59.999Z"), 0))
    }

    @Test
    fun `the time of the day is always from 0 up to a whole day`() {
        val offsets = listOf(-12 * HOUR, -5 * HOUR, 0, 3 * HOUR, 5 * HOUR + 45 * MINUTE, 14 * HOUR)
        var instant = epoch("2023-01-01T00:00:00Z")
        while (instant < epoch("2027-01-01T00:00:00Z")) {
            offsets.forEach { offset ->
                val millisOfDay = AnalogClockMath.millisOfDay(instant, offset)
                assertTrue(millisOfDay in 0 until DAY, "$millisOfDay at $instant with offset $offset")
            }
            instant += 7_919_013L
        }
    }

    @Test
    fun `the extremes of time stay in range`() {
        listOf(Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE - 1).forEach { instant ->
            listOf(Int.MIN_VALUE, -14 * HOUR, 0, 14 * HOUR, Int.MAX_VALUE).forEach { offset ->
                val millisOfDay = AnalogClockMath.millisOfDay(instant, offset)
                assertTrue(millisOfDay in 0 until DAY, "$millisOfDay at $instant with offset $offset")
            }
        }
    }

    @Test
    fun `the next millisecond is the next millisecond of the day`() {
        listOf(0L, 1L, 86_399_998L, 1_792_855_509_250L, -5L).forEach { instant ->
            listOf(0, 3 * HOUR, -5 * HOUR).forEach { offset ->
                val now = AnalogClockMath.millisOfDay(instant, offset)
                val next = AnalogClockMath.millisOfDay(instant + 1, offset)
                assertEquals((now + 1) % DAY, next, "at $instant with offset $offset")
            }
        }
    }

    @Test
    fun `when the offset changes the day moves by the difference, like the clocks on the wall`() {
        val instant = epoch("2026-03-29T01:00:00Z")

        // Winter time and summer time of Berlin at the very same instant
        val before = AnalogClockMath.millisOfDay(instant, HOUR)
        val after = AnalogClockMath.millisOfDay(instant, 2 * HOUR)

        assertEquals(at(2), before)
        assertEquals(at(3), after)
        assertEquals(HOUR, after - before)
    }

    @Test
    fun `an hour is skipped when the clocks go forward in spring`() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val last = epoch("2026-03-29T00:59:59.999Z")
        val first = epoch("2026-03-29T01:00:00Z")
        assertEquals(HOUR, berlin.getOffset(last))
        assertEquals(2 * HOUR, berlin.getOffset(first))

        // 01:59:59.999 is followed by 03:00:00.000
        assertEquals(at(1, 59, 59, 999), AnalogClockMath.millisOfDay(last, berlin.getOffset(last)))
        assertEquals(at(3), AnalogClockMath.millisOfDay(first, berlin.getOffset(first)))

        val (hourBefore, minuteBefore, secondBefore) = anglesAt(last, berlin)
        val (hourAfter, minuteAfter, secondAfter) = anglesAt(first, berlin)

        // The hour hand jumps an hour, which is 30 degrees, like the hour hand of a clock which is put forward
        assertAngle(60f, hourBefore, "hour before")
        assertAngle(90f, hourAfter, "hour after")
        // The minute and the second hand go on as if nothing happened, one millisecond further
        assertAngle(0f, minuteBefore, "minute before")
        assertAngle(0f, minuteAfter, "minute after")
        assertAngle(359.994f, secondBefore, "second before")
        assertAngle(0f, secondAfter, "second after")
    }

    @Test
    fun `an hour is repeated when the clocks go back in autumn`() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val last = epoch("2026-10-25T00:59:59.999Z")
        val first = epoch("2026-10-25T01:00:00Z")
        assertEquals(2 * HOUR, berlin.getOffset(last))
        assertEquals(HOUR, berlin.getOffset(first))

        // 02:59:59.999 is followed by 02:00:00.000 again
        assertEquals(at(2, 59, 59, 999), AnalogClockMath.millisOfDay(last, berlin.getOffset(last)))
        assertEquals(at(2), AnalogClockMath.millisOfDay(first, berlin.getOffset(first)))

        val (hourBefore, minuteBefore, secondBefore) = anglesAt(last, berlin)
        val (hourAfter, minuteAfter, secondAfter) = anglesAt(first, berlin)

        assertAngle(90f, hourBefore, "hour before")
        assertAngle(60f, hourAfter, "hour after")
        assertAngle(0f, minuteBefore, "minute before")
        assertAngle(0f, minuteAfter, "minute after")
        assertAngle(359.994f, secondBefore, "second before")
        assertAngle(0f, secondAfter, "second after")
    }

    @Test
    fun `the second hand keeps sweeping at the same speed through a daylight saving change`() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")

        listOf(epoch("2026-03-29T01:00:00Z"), epoch("2026-10-25T01:00:00Z")).forEach { change ->
            for (instant in change - 3_000 until change + 3_000) {
                val now = anglesAt(instant, berlin)[SECOND_INDEX]
                val next = anglesAt(instant + 1, berlin)[SECOND_INDEX]
                assertAngle(now + SWEEP_PER_MILLI, next, "second hand at $instant")
            }
        }
    }

    @Test
    fun `every hand points at 12 at midnight and at noon`() {
        listOf(0, at(12)).forEach { millisOfDay ->
            assertEquals(0f, AnalogClockMath.hourAngle(millisOfDay), DELTA, "hour at $millisOfDay")
            assertEquals(0f, AnalogClockMath.minuteAngle(millisOfDay), DELTA, "minute at $millisOfDay")
            assertEquals(0f, AnalogClockMath.secondAngle(millisOfDay), DELTA, "second at $millisOfDay")
        }
    }

    @Test
    fun `the hour hand turns once in 12 hours`() {
        assertEquals(30f, AnalogClockMath.hourAngle(at(1)), DELTA)
        assertEquals(90f, AnalogClockMath.hourAngle(at(3)), DELTA)
        assertEquals(180f, AnalogClockMath.hourAngle(at(6)), DELTA)
        assertEquals(270f, AnalogClockMath.hourAngle(at(9)), DELTA)
        assertEquals(330f, AnalogClockMath.hourAngle(at(11)), DELTA)

        // The same on a 24 hour clock
        assertEquals(AnalogClockMath.hourAngle(at(3)), AnalogClockMath.hourAngle(at(15)), DELTA)
        assertEquals(AnalogClockMath.hourAngle(at(11)), AnalogClockMath.hourAngle(at(23)), DELTA)

        // Half way between the 3 and the 4 at 3:30, and a quarter of a degree for half a minute
        assertEquals(105f, AnalogClockMath.hourAngle(at(3, 30)), DELTA)
        assertEquals(105.25f, AnalogClockMath.hourAngle(at(3, 30, 30)), DELTA)
    }

    @Test
    fun `the minute hand turns once an hour, without taking steps`() {
        assertEquals(90f, AnalogClockMath.minuteAngle(at(5, 15)), DELTA)
        assertEquals(180f, AnalogClockMath.minuteAngle(at(5, 30)), DELTA)
        assertEquals(270f, AnalogClockMath.minuteAngle(at(5, 45)), DELTA)

        // Half a minute is 3 degrees, half a second 0.05
        assertEquals(3f, AnalogClockMath.minuteAngle(at(0, 0, 30)), DELTA)
        assertEquals(123f, AnalogClockMath.minuteAngle(at(10, 20, 30)), DELTA)
        assertEquals(0.05f, AnalogClockMath.minuteAngle(at(0, 0, 0, 500)), DELTA)
    }

    @Test
    fun `the second hand turns 6 degrees a second and 0006 of a degree a millisecond`() {
        assertEquals(6f, AnalogClockMath.secondAngle(at(0, 0, 1)), DELTA)
        assertEquals(90f, AnalogClockMath.secondAngle(at(0, 0, 15)), DELTA)
        assertEquals(180f, AnalogClockMath.secondAngle(at(0, 0, 30)), DELTA)
        assertEquals(354f, AnalogClockMath.secondAngle(at(0, 0, 59)), DELTA)

        assertEquals(SWEEP_PER_MILLI, AnalogClockMath.secondAngle(at(0, 0, 0, 1)), DELTA)
        assertEquals(3f, AnalogClockMath.secondAngle(at(0, 0, 0, 500)), DELTA)
        assertEquals(36.6f, AnalogClockMath.secondAngle(at(0, 0, 6, 100)), DELTA)
        assertEquals(359.994f, AnalogClockMath.secondAngle(at(0, 0, 59, 999)), DELTA)
    }

    @Test
    fun `the second hand is in the same place in every minute`() {
        listOf(at(0, 0, 17, 250), at(7, 41, 17, 250), at(13, 59, 17, 250), at(23, 12, 17, 250)).forEach { millisOfDay ->
            assertEquals(103.5f, AnalogClockMath.secondAngle(millisOfDay), DELTA, "at $millisOfDay")
        }
    }

    @Test
    fun `the second hand never takes a step, it moves 0006 of a degree every millisecond, also through the minute`() {
        val minute = at(13, 37)

        for (millis in 0 until MINUTE) {
            val now = AnalogClockMath.secondAngle(minute + millis)
            val next = AnalogClockMath.secondAngle(minute + millis + 1)

            // Round the wrap at the end of the minute, 359.994 is followed by 0
            val step = (next - now + 360f) % 360f
            assertTrue(abs(step - SWEEP_PER_MILLI) < STEP_DELTA, "step $step at millisecond $millis")
        }
    }

    @Test
    fun `the minute and the hour hand never go backwards or jump either`() {
        val start = at(10, 59, 58)

        for (millis in 0 until 4_000) {
            val minuteStep = (AnalogClockMath.minuteAngle(start + millis + 1) - AnalogClockMath.minuteAngle(start + millis) + 360f) % 360f
            val hourStep = (AnalogClockMath.hourAngle(start + millis + 1) - AnalogClockMath.hourAngle(start + millis) + 360f) % 360f

            assertTrue(minuteStep < 0.001f, "minute step $minuteStep at millisecond $millis")
            assertTrue(hourStep < 0.001f, "hour step $hourStep at millisecond $millis")
        }
    }

    @Test
    fun `the sweeping hands agree with the hands of the ticking clock for every second of the day`() {
        for (secondOfDay in 0 until SECONDS_PER_DAY) {
            val hours = secondOfDay / 3600
            val minutes = secondOfDay / 60 % 60
            val seconds = secondOfDay % 60
            val ticking = AnalogClockMath.handAngles(hours, minutes, seconds)
            val millisOfDay = secondOfDay * 1000

            assertAngle(ticking.hour, AnalogClockMath.hourAngle(millisOfDay), "hour hand at second $secondOfDay")
            assertAngle(ticking.minute, AnalogClockMath.minuteAngle(millisOfDay), "minute hand at second $secondOfDay")
            assertAngle(ticking.second, AnalogClockMath.secondAngle(millisOfDay), "second hand at second $secondOfDay")
        }
    }

    @Test
    fun `the sweeping hands agree with the hands of the ticking clock with the milliseconds, too`() {
        var millisOfDay = 0
        while (millisOfDay < DAY) {
            val ticking =
                AnalogClockMath.handAngles(
                    hours = millisOfDay / HOUR,
                    minutes = millisOfDay / MINUTE % 60,
                    seconds = millisOfDay / 1000 % 60,
                    millis = millisOfDay % 1000,
                )

            assertAngle(ticking.hour, AnalogClockMath.hourAngle(millisOfDay), "hour hand at $millisOfDay")
            assertAngle(ticking.minute, AnalogClockMath.minuteAngle(millisOfDay), "minute hand at $millisOfDay")
            assertAngle(ticking.second, AnalogClockMath.secondAngle(millisOfDay), "second hand at $millisOfDay")
            millisOfDay += 997
        }
    }

    @Test
    fun `angles stay below a full turn just before the hands wrap`() {
        // The last two seconds before the hour hand, the minute hand and the second hand wrap round,
        // in the morning and in the afternoon
        listOf(at(11, 59, 58), at(23, 59, 58), at(0, 59, 58), at(5, 59, 58)).forEach { start ->
            for (millis in 0 until 2_000) {
                val millisOfDay = start + millis
                listOf(
                    AnalogClockMath.hourAngle(millisOfDay),
                    AnalogClockMath.minuteAngle(millisOfDay),
                    AnalogClockMath.secondAngle(millisOfDay),
                ).forEach { degrees ->
                    assertTrue(degrees >= 0f && degrees < 360f, "$degrees is outside [0, 360) at $millisOfDay")
                }
            }
        }
    }

    @Test
    fun `a time outside of the day wraps round instead of failing`() {
        assertEquals(AnalogClockMath.secondAngle(MINUTE - 1), AnalogClockMath.secondAngle(-1), DELTA)
        assertEquals(0f, AnalogClockMath.hourAngle(DAY), DELTA)
        assertEquals(AnalogClockMath.minuteAngle(1_500), AnalogClockMath.minuteAngle(HOUR + 1_500), DELTA)
        assertEquals(AnalogClockMath.hourAngle(at(23)), AnalogClockMath.hourAngle(-at(1)), DELTA)
    }

    @Test
    fun `the hands of an instant in a time zone`() {
        // 13:00:00.250 in UTC is 16:00:00.250 in Moscow
        val moscow = TimeZone.getTimeZone("Europe/Moscow")
        val angles = anglesAt(epoch("2026-10-24T13:00:00.250Z"), moscow)

        assertEquals(120.002f, angles[HOUR_INDEX], DELTA)
        assertEquals(0.025f, angles[MINUTE_INDEX], DELTA)
        assertEquals(1.5f, angles[SECOND_INDEX], DELTA)
    }

    private companion object {
        const val DELTA = 0.001f

        // The second hand moves 360 degrees in 60 000 milliseconds. A float near 360 is only
        // exact to 0.00003, so the size of a step is checked with more room than that
        const val SWEEP_PER_MILLI = 0.006f
        const val STEP_DELTA = 0.0002f

        const val MINUTE = 60_000
        const val HOUR = 3_600_000
        const val DAY = 86_400_000
        const val SECONDS_PER_DAY = 86_400

        const val HOUR_INDEX = 0
        const val MINUTE_INDEX = 1
        const val SECOND_INDEX = 2
    }
}
