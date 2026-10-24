package com.neilturner.aerialviews.services.weather

import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLEAR
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLOUDY
import com.neilturner.aerialviews.models.enums.WeatherCondition.RAIN
import com.neilturner.aerialviews.models.prefs.WeatherVideoPrefs
import com.neilturner.aerialviews.services.weather.WeatherRecheckAction.KEEP
import com.neilturner.aerialviews.services.weather.WeatherRecheckAction.REBUILD
import com.neilturner.aerialviews.services.weather.WeatherRecheckAction.SKIP_UNKNOWN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class WeatherRecheckPolicyTest {
    private val start = 1_800_000_000_000L
    private val interval = 20.minutes

    private fun later(duration: Duration) = start + duration.inWholeMilliseconds

    // The setting

    @Test
    fun `every choice on the list is that many minutes`() {
        assertEquals(10.minutes, WeatherRecheckPolicy.interval("10"))
        assertEquals(20.minutes, WeatherRecheckPolicy.interval("20"))
        assertEquals(30.minutes, WeatherRecheckPolicy.interval("30"))
        assertEquals(60.minutes, WeatherRecheckPolicy.interval("60"))
    }

    @Test
    fun `zero turns re-checking off`() {
        assertNull(WeatherRecheckPolicy.interval("0"))
        assertNull(WeatherRecheckPolicy.interval(" 0 "))
        assertNull(WeatherRecheckPolicy.interval("00"))
    }

    @Test
    fun `a setting which was never stored is the default of twenty minutes`() {
        assertEquals(20, WeatherVideoPrefs.DEFAULT_RECHECK_MINUTES)
        assertEquals(20.minutes, WeatherRecheckPolicy.interval(null))
        assertEquals(20.minutes, WeatherRecheckPolicy.interval(WeatherVideoPrefs.DEFAULT_RECHECK_MINUTES.toString()))
    }

    @Test
    fun `a setting which is not a number of minutes is the default and not off`() {
        listOf("", " ", "off", "OFF", "abc", "1.5", "20 minutes", "-10", "-1", "1441", "99999999999").forEach { setting ->
            assertEquals(20.minutes, WeatherRecheckPolicy.interval(setting), "'$setting'")
        }
    }

    @Test
    fun `spaces around the number are ignored`() {
        assertEquals(30.minutes, WeatherRecheckPolicy.interval(" 30 "))
    }

    @Test
    fun `any number of minutes up to a day is kept, not only the ones on the list`() {
        assertEquals(1.minutes, WeatherRecheckPolicy.interval("1"))
        assertEquals(45.minutes, WeatherRecheckPolicy.interval("45"))
        assertEquals(24.hours, WeatherRecheckPolicy.interval(WeatherRecheckPolicy.MAX_MINUTES.toString()))
    }

    // Is a re-check due

    @Test
    fun `the first look is due straight away`() {
        assertTrue(WeatherRecheckPolicy.isDue(null, start, interval))
        assertEquals(Duration.ZERO, WeatherRecheckPolicy.timeUntilDue(null, start, interval))
    }

    @Test
    fun `a look is not due before the interval has passed`() {
        assertFalse(WeatherRecheckPolicy.isDue(start, start, interval))
        assertFalse(WeatherRecheckPolicy.isDue(start, later(10.minutes), interval))
        assertFalse(WeatherRecheckPolicy.isDue(start, later(interval - 1.milliseconds), interval))
    }

    @Test
    fun `a look is due once the interval has passed`() {
        assertTrue(WeatherRecheckPolicy.isDue(start, later(interval), interval))
        assertTrue(WeatherRecheckPolicy.isDue(start, later(interval + 1.milliseconds), interval))
        assertTrue(WeatherRecheckPolicy.isDue(start, later(5.hours), interval))
    }

    @Test
    fun `the wait is what is left of the interval`() {
        assertEquals(interval, WeatherRecheckPolicy.timeUntilDue(start, start, interval))
        assertEquals(15.minutes, WeatherRecheckPolicy.timeUntilDue(start, later(5.minutes), interval))
        assertEquals(1.milliseconds, WeatherRecheckPolicy.timeUntilDue(start, later(interval - 1.milliseconds), interval))
        assertEquals(Duration.ZERO, WeatherRecheckPolicy.timeUntilDue(start, later(interval), interval))
        assertEquals(Duration.ZERO, WeatherRecheckPolicy.timeUntilDue(start, later(3.hours), interval))
    }

    @Test
    fun `the wait follows the interval of the setting`() {
        assertTrue(WeatherRecheckPolicy.isDue(start, later(10.minutes), 10.minutes))
        assertFalse(WeatherRecheckPolicy.isDue(start, later(10.minutes), 30.minutes))
        assertTrue(WeatherRecheckPolicy.isDue(start, later(30.minutes), 30.minutes))
        assertFalse(WeatherRecheckPolicy.isDue(start, later(59.minutes), 60.minutes))
        assertTrue(WeatherRecheckPolicy.isDue(start, later(60.minutes), 60.minutes))
    }

    @Test
    fun `a clock which was set back makes a look due, as the time since the last one is not known`() {
        assertTrue(WeatherRecheckPolicy.isDue(start, start - 1, interval))
        assertTrue(WeatherRecheckPolicy.isDue(start, start - 3.hours.inWholeMilliseconds, interval))
        assertEquals(Duration.ZERO, WeatherRecheckPolicy.timeUntilDue(start, start - 1, interval))
    }

    @Test
    fun `a look is never due when re-checking is off`() {
        assertFalse(WeatherRecheckPolicy.isDue(null, start, null))
        assertFalse(WeatherRecheckPolicy.isDue(start, later(100.hours), null))
        assertEquals(Duration.INFINITE, WeatherRecheckPolicy.timeUntilDue(start, later(100.hours), null))
    }

    @Test
    fun `the time until a look is due is never negative or beyond the interval`() {
        val elapsed = listOf(-5.hours, -1.seconds, Duration.ZERO, 1.milliseconds, 7.minutes, interval, 2.hours)

        elapsed.forEach { passed ->
            val wait = WeatherRecheckPolicy.timeUntilDue(start, later(passed), interval)
            assertTrue(wait >= Duration.ZERO, "after $passed")
            assertTrue(wait <= interval, "after $passed")
        }
    }

    // Did the weather change

    @Test
    fun `the same weather is not a change`() {
        WeatherCondition.entries.forEach { condition ->
            assertFalse(WeatherRecheckPolicy.hasChanged(condition, condition), "$condition")
        }
    }

    @Test
    fun `another weather is a change`() {
        assertTrue(WeatherRecheckPolicy.hasChanged(CLEAR, RAIN))
        assertTrue(WeatherRecheckPolicy.hasChanged(RAIN, CLEAR))
        assertTrue(WeatherRecheckPolicy.hasChanged(CLOUDY, RAIN))
    }

    @Test
    fun `every pair of different weather is a change in both directions`() {
        for (from in WeatherCondition.entries) {
            for (to in WeatherCondition.entries) {
                assertEquals(from != to, WeatherRecheckPolicy.hasChanged(from, to), "$from to $to")
            }
        }
    }

    @Test
    fun `weather which is not known is not a change`() {
        assertFalse(WeatherRecheckPolicy.hasChanged(RAIN, null))
        assertFalse(WeatherRecheckPolicy.hasChanged(null, null))
    }

    @Test
    fun `weather which becomes known after a playlist made without it is a change`() {
        WeatherCondition.entries.forEach { condition ->
            assertTrue(WeatherRecheckPolicy.hasChanged(null, condition), "$condition")
        }
    }

    // What to do

    @Test
    fun `the playlist is kept while the weather is the one it was made for`() {
        WeatherCondition.entries.forEach { condition ->
            assertEquals(KEEP, WeatherRecheckPolicy.decide(condition, condition), "$condition")
        }
    }

    @Test
    fun `a playlist is made when the weather changed`() {
        assertEquals(REBUILD, WeatherRecheckPolicy.decide(CLEAR, RAIN))
        assertEquals(REBUILD, WeatherRecheckPolicy.decide(RAIN, CLEAR))
    }

    @Test
    fun `a playlist is made when the weather becomes known`() {
        assertEquals(REBUILD, WeatherRecheckPolicy.decide(null, RAIN))
    }

    @Test
    fun `the playlist is kept when the weather is not known`() {
        WeatherCondition.entries.forEach { condition ->
            assertEquals(SKIP_UNKNOWN, WeatherRecheckPolicy.decide(condition, null), "$condition")
        }
        assertEquals(SKIP_UNKNOWN, WeatherRecheckPolicy.decide(null, null))
    }

    @Test
    fun `each pair of weathers gets the action which agrees with did it change`() {
        val known = WeatherCondition.entries + null

        for (builtFor in known) {
            for (current in known) {
                val expected =
                    when {
                        current == null -> SKIP_UNKNOWN
                        WeatherRecheckPolicy.hasChanged(builtFor, current) -> REBUILD
                        else -> KEEP
                    }
                assertEquals(expected, WeatherRecheckPolicy.decide(builtFor, current), "$builtFor then $current")
            }
        }
    }
}
