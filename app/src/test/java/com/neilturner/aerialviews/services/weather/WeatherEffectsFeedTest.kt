package com.neilturner.aerialviews.services.weather

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

internal class WeatherEffectsFeedTest {
    private var now = 50_000_000L
    private val feed = WeatherEffectsFeed(clock = { now })

    private val rain = WeatherSnapshot(conditionId = 501, rainMmPerHour = 3.0)
    private val snow = WeatherSnapshot(conditionId = 601, snowMmPerHour = 0.5)

    private fun minutesPass(minutes: Long) {
        now += minutes.minutes.inWholeMilliseconds
    }

    @Test
    fun `a new feed has no weather`() {
        assertNull(feed.current())
        assertNull(feed.latest.value)
    }

    @Test
    fun `published weather is the current weather`() {
        feed.publish(rain)

        assertEquals(rain, feed.current())
        assertEquals(rain, feed.latest.value?.snapshot)
        assertEquals(now, feed.latest.value?.recordedAtMillis)
    }

    @Test
    fun `the newest weather replaces the old one`() {
        feed.publish(rain)
        minutesPass(61)
        feed.publish(snow)

        assertEquals(snow, feed.current())
    }

    @Test
    fun `weather is kept for less than three hours`() {
        feed.publish(rain)

        minutesPass(61)
        assertEquals(rain, feed.current())
        minutesPass(118)
        assertEquals(rain, feed.current())
        minutesPass(1)
        assertNull(feed.current())
    }

    @Test
    fun `an older weather can be asked for with a shorter time`() {
        feed.publish(rain)
        minutesPass(90)

        assertEquals(rain, feed.current())
        assertNull(feed.current(maxAge = 60.minutes))
        assertEquals(rain, feed.current(maxAge = 2.hours))
    }

    @Test
    fun `weather from the future is not trusted`() {
        feed.publish(rain)
        minutesPass(-5)

        assertNull(feed.current())
    }

    @Test
    fun `the weather can be cleared`() {
        feed.publish(rain)

        feed.clear()

        assertNull(feed.current())
        assertNull(feed.latest.value)
    }

    @Test
    fun `an entry which was kept can be checked for its age later`() {
        feed.publish(rain)
        val entry = feed.latest.value
        assertNotNull(entry)

        assertEquals(rain, feed.fresh(entry))
        minutesPass(181)
        assertNull(feed.fresh(entry))
        assertNull(feed.fresh(null))
    }

    @Test
    fun `the same weather published again is a new entry, so listeners hear of it`() {
        feed.publish(rain)
        val first = feed.latest.value
        minutesPass(61)
        feed.publish(rain)

        assertNotNull(first)
        assertEquals(first?.snapshot, feed.latest.value?.snapshot)
        assertEquals(false, first == feed.latest.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `listeners hear of the weather which was already there and of the weather to come`() =
        runTest(UnconfinedTestDispatcher()) {
            feed.publish(rain)
            val heard = mutableListOf<WeatherSnapshot?>()
            val job = launch { feed.latest.collect { heard.add(it?.snapshot) } }

            minutesPass(61)
            feed.publish(snow)
            job.cancel()

            assertEquals(listOf<WeatherSnapshot?>(rain, snow), heard)
        }
}
