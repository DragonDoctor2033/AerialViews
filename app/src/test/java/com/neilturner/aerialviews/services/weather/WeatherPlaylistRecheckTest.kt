package com.neilturner.aerialviews.services.weather

import android.net.Uri
import com.neilturner.aerialviews.models.MediaFetchResult
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLEAR
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLOUDY
import com.neilturner.aerialviews.models.enums.WeatherCondition.FOG
import com.neilturner.aerialviews.models.enums.WeatherCondition.RAIN
import com.neilturner.aerialviews.models.enums.WeatherCondition.SNOW
import com.neilturner.aerialviews.models.videos.AerialMedia
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class WeatherPlaylistRecheckTest {
    private val base = 1_800_000_000_000L
    private val interval = 20.minutes

    private fun playlistOf(items: Int) =
        MediaFetchResult(
            mediaPlaylist = MediaPlaylist(List(items) { AerialMedia(uri = mockk<Uri>(relaxed = true)) }),
            musicPlaylist = null,
        )

    private fun TestScope.passTime(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    /** A re-check with everything it needs from outside replaced, and a record of what it did. */
    private inner class Fixture(
        scope: CoroutineScope,
        scheduler: TestCoroutineScheduler,
        builtFor: WeatherCondition? = CLEAR,
        interval: Duration = this@WeatherPlaylistRecheckTest.interval,
    ) {
        // What the weather is, as far as the re-check can find out
        var weather: WeatherCondition? = CLEAR
        var lookupFailure: Exception? = null
        val maxAges = mutableListOf<Duration>()

        // What making a playlist does
        var made: (WeatherCondition) -> MediaFetchResult? = { playlistOf(3) }
        var makingFailure: Exception? = null
        var makingGate: CompletableDeferred<Unit>? = null
        val madeFor = mutableListOf<WeatherCondition>()

        // What the screensaver was told
        val switchedTo = mutableListOf<Pair<MediaFetchResult, WeatherCondition>>()
        var switchFailure: Exception? = null

        // The wall clock, which can be set to another time
        var clockSkew = 0L

        val recheck =
            WeatherPlaylistRecheck(
                scope = scope,
                interval = interval,
                builtFor = builtFor,
                currentCondition = { maxAge ->
                    maxAges += maxAge
                    lookupFailure?.let { throw it }
                    weather
                },
                rebuild = { condition ->
                    madeFor += condition
                    makingGate?.await()
                    makingFailure?.let { throw it }
                    made(condition)
                },
                onPlaylistReady = { result, condition ->
                    switchFailure?.let { throw it }
                    switchedTo += result to condition
                },
                clock = { base + scheduler.currentTime + clockSkew },
            )

        val looks get() = maxAges.size
    }

    private fun TestScope.fixture(
        builtFor: WeatherCondition? = CLEAR,
        interval: Duration = this@WeatherPlaylistRecheckTest.interval,
    ) = Fixture(backgroundScope, testScheduler, builtFor, interval)

    // When it looks

    @Test
    fun `it does nothing until it is started`() =
        runTest {
            val fixture = fixture()

            passTime(3.hours)

            assertEquals(0, fixture.looks)
            assertFalse(fixture.recheck.isRunning)
        }

    @Test
    fun `it does not look before the interval has passed`() =
        runTest {
            val fixture = fixture()

            fixture.recheck.start()
            passTime(interval - 1.milliseconds)

            assertEquals(0, fixture.looks)
            assertTrue(fixture.recheck.isRunning)
        }

    @Test
    fun `it looks when the interval has passed`() =
        runTest {
            val fixture = fixture()

            fixture.recheck.start()
            passTime(interval)

            assertEquals(1, fixture.looks)
        }

    @Test
    fun `it looks again every interval`() =
        runTest {
            val fixture = fixture()

            fixture.recheck.start()
            passTime(interval * 3)

            assertEquals(3, fixture.looks)
        }

    @Test
    fun `it follows the interval it was given`() =
        runTest {
            val fixture = fixture(interval = 10.minutes)

            fixture.recheck.start()
            passTime(10.minutes - 1.milliseconds)
            assertEquals(0, fixture.looks)
            passTime(1.milliseconds)
            assertEquals(1, fixture.looks)
            passTime(60.minutes)
            assertEquals(7, fixture.looks)
        }

    @Test
    fun `an interval which is not a time is refused`() =
        runTest {
            listOf(Duration.ZERO, (-5).minutes, Duration.INFINITE).forEach { bad ->
                assertThrows(IllegalArgumentException::class.java, { fixture(interval = bad) }, "$bad")
            }
        }

    @Test
    fun `it asks for a weather no older than the interval, so an older one is looked up again`() =
        runTest {
            val fixture = fixture(interval = 30.minutes)

            fixture.recheck.start()
            passTime(90.minutes)

            assertEquals(listOf(30.minutes, 30.minutes, 30.minutes), fixture.maxAges)
        }

    // What it does about the weather

    @Test
    fun `the playlist is kept while the weather is the one it was made for`() =
        runTest {
            val fixture = fixture(builtFor = RAIN)
            fixture.weather = RAIN

            fixture.recheck.start()
            passTime(interval * 3)

            assertEquals(3, fixture.looks)
            assertTrue(fixture.madeFor.isEmpty())
            assertTrue(fixture.switchedTo.isEmpty())
            assertEquals(RAIN, fixture.recheck.builtFor)
        }

    @Test
    fun `a playlist is made for the new weather and handed over`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)
            val result = playlistOf(5)
            fixture.made = { result }
            fixture.weather = RAIN

            fixture.recheck.start()
            passTime(interval)

            assertEquals(listOf(RAIN), fixture.madeFor)
            assertEquals(1, fixture.switchedTo.size)
            assertSame(result, fixture.switchedTo.single().first)
            assertEquals(RAIN, fixture.switchedTo.single().second)
            assertEquals(RAIN, fixture.recheck.builtFor)
        }

    @Test
    fun `nothing is handed over before the weather changes`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)

            fixture.recheck.start()
            passTime(interval * 2)
            assertTrue(fixture.switchedTo.isEmpty())

            fixture.weather = SNOW
            passTime(interval)

            assertEquals(listOf(SNOW), fixture.switchedTo.map { it.second })
        }

    @Test
    fun `a playlist is not made again for a weather it already has one for`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)
            fixture.weather = RAIN

            fixture.recheck.start()
            passTime(interval * 4)

            assertEquals(4, fixture.looks)
            assertEquals(listOf(RAIN), fixture.madeFor)
            assertEquals(1, fixture.switchedTo.size)
        }

    @Test
    fun `every change of weather gets its own playlist`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)

            fixture.recheck.start()
            fixture.weather = RAIN
            passTime(interval)
            fixture.weather = CLOUDY
            passTime(interval)
            fixture.weather = CLEAR
            passTime(interval)
            fixture.weather = CLEAR
            passTime(interval)

            assertEquals(listOf(RAIN, CLOUDY, CLEAR), fixture.madeFor)
            assertEquals(listOf(RAIN, CLOUDY, CLEAR), fixture.switchedTo.map { it.second })
            assertEquals(CLEAR, fixture.recheck.builtFor)
        }

    @Test
    fun `the playlist is kept when the weather is not known`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)
            fixture.weather = null

            fixture.recheck.start()
            passTime(interval * 3)

            assertEquals(3, fixture.looks)
            assertTrue(fixture.madeFor.isEmpty())
            assertTrue(fixture.switchedTo.isEmpty())
            assertEquals(CLEAR, fixture.recheck.builtFor)
        }

    @Test
    fun `the weather is looked at again at the next time after it was not known`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)
            fixture.weather = null

            fixture.recheck.start()
            passTime(interval)
            fixture.weather = RAIN
            passTime(interval - 1.milliseconds)
            assertTrue(fixture.switchedTo.isEmpty())
            passTime(1.milliseconds)

            assertEquals(listOf(RAIN), fixture.switchedTo.map { it.second })
        }

    @Test
    fun `a playlist made without the weather is replaced when the weather becomes known`() =
        runTest {
            val fixture = fixture(builtFor = null)
            fixture.weather = null

            fixture.recheck.start()
            passTime(interval)
            assertTrue(fixture.switchedTo.isEmpty())

            fixture.weather = FOG
            passTime(interval)

            assertEquals(listOf(FOG), fixture.switchedTo.map { it.second })
            assertEquals(FOG, fixture.recheck.builtFor)
        }

    @Test
    fun `a look which fails is the same as a weather which is not known, and the timer goes on`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)
            fixture.lookupFailure = IOException("no network")
            fixture.weather = RAIN

            fixture.recheck.start()
            passTime(interval)
            assertEquals(1, fixture.looks)
            assertTrue(fixture.switchedTo.isEmpty())
            assertTrue(fixture.recheck.isRunning)

            fixture.lookupFailure = null
            passTime(interval)

            assertEquals(listOf(RAIN), fixture.switchedTo.map { it.second })
        }

    // When no playlist can be made

    @Test
    fun `the playlist is kept when none can be made, and one is tried for again at the next look`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)
            fixture.weather = RAIN
            fixture.made = { null }

            fixture.recheck.start()
            passTime(interval)

            assertEquals(listOf(RAIN), fixture.madeFor)
            assertTrue(fixture.switchedTo.isEmpty())
            assertEquals(CLEAR, fixture.recheck.builtFor)

            fixture.made = { playlistOf(2) }
            passTime(interval)

            assertEquals(listOf(RAIN, RAIN), fixture.madeFor)
            assertEquals(listOf(RAIN), fixture.switchedTo.map { it.second })
            assertEquals(RAIN, fixture.recheck.builtFor)
        }

    @Test
    fun `a playlist with nothing in it is not played`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)
            fixture.weather = RAIN
            fixture.made = { playlistOf(0) }

            fixture.recheck.start()
            passTime(interval)

            assertEquals(listOf(RAIN), fixture.madeFor)
            assertTrue(fixture.switchedTo.isEmpty())
            assertEquals(CLEAR, fixture.recheck.builtFor)
        }

    @Test
    fun `a failure while making a playlist keeps the playlist and the timer goes on`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)
            fixture.weather = RAIN
            fixture.makingFailure = IOException("server is down")

            fixture.recheck.start()
            passTime(interval)
            assertTrue(fixture.switchedTo.isEmpty())
            assertEquals(CLEAR, fixture.recheck.builtFor)
            assertTrue(fixture.recheck.isRunning)

            fixture.makingFailure = null
            passTime(interval)

            assertEquals(listOf(RAIN), fixture.switchedTo.map { it.second })
        }

    @Test
    fun `a failure while switching keeps the old weather, so that it is tried again`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)
            fixture.weather = RAIN
            fixture.switchFailure = IllegalStateException("could not switch")

            fixture.recheck.start()
            passTime(interval)
            assertTrue(fixture.switchedTo.isEmpty())
            assertEquals(CLEAR, fixture.recheck.builtFor)

            fixture.switchFailure = null
            passTime(interval)

            assertEquals(listOf(RAIN), fixture.switchedTo.map { it.second })
            assertEquals(RAIN, fixture.recheck.builtFor)
        }

    @Test
    fun `only one playlist is made at a time`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)
            fixture.weather = RAIN
            fixture.makingGate = CompletableDeferred()

            fixture.recheck.start()
            passTime(interval)
            assertEquals(listOf(RAIN), fixture.madeFor)

            // Still making the first one at the next look, and the weather has changed again
            fixture.weather = SNOW
            passTime(interval)
            assertEquals(2, fixture.looks)
            assertEquals(listOf(RAIN), fixture.madeFor)

            fixture.makingGate?.complete(Unit)
            runCurrent()
            assertEquals(listOf(RAIN), fixture.switchedTo.map { it.second })

            // The look after that sees that the weather is no longer the one the playlist is for
            fixture.makingGate = null
            passTime(interval)
            assertEquals(listOf(RAIN, SNOW), fixture.madeFor)
            assertEquals(listOf(RAIN, SNOW), fixture.switchedTo.map { it.second })
        }

    // Pausing, such as for a blackout

    @Test
    fun `it does not look while it is paused`() =
        runTest {
            val fixture = fixture()
            fixture.recheck.start()
            passTime(5.minutes)

            fixture.recheck.pause()
            passTime(3.hours)

            assertEquals(0, fixture.looks)
            assertFalse(fixture.recheck.isRunning)
        }

    @Test
    fun `after a pause it goes on with the time which was left`() =
        runTest {
            val fixture = fixture()
            fixture.recheck.start()
            passTime(5.minutes)
            fixture.recheck.pause()
            passTime(3.minutes)

            fixture.recheck.start()
            passTime(12.minutes - 1.milliseconds)
            assertEquals(0, fixture.looks)
            passTime(1.milliseconds)

            assertEquals(1, fixture.looks)
        }

    @Test
    fun `after a pause which was longer than the interval it looks straight away`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)
            fixture.weather = RAIN
            fixture.recheck.start()
            passTime(5.minutes)
            fixture.recheck.pause()
            passTime(8.hours)
            assertEquals(0, fixture.looks)

            fixture.recheck.start()
            runCurrent()

            assertEquals(1, fixture.looks)
            assertEquals(listOf(RAIN), fixture.switchedTo.map { it.second })
        }

    @Test
    fun `a look which was cut short by a pause is made again after it`() =
        runTest {
            val fixture = fixture()
            val slow = CompletableDeferred<Unit>()
            val recheck =
                WeatherPlaylistRecheck(
                    scope = backgroundScope,
                    interval = interval,
                    builtFor = CLEAR,
                    currentCondition = {
                        fixture.maxAges += it
                        slow.await()
                        RAIN
                    },
                    rebuild = { playlistOf(1) },
                    onPlaylistReady = { result, condition -> fixture.switchedTo += result to condition },
                    clock = { base + testScheduler.currentTime },
                )

            recheck.start()
            passTime(interval)
            assertEquals(1, fixture.looks)
            recheck.pause()
            passTime(1.hours)

            slow.complete(Unit)
            recheck.start()
            runCurrent()

            assertEquals(2, fixture.looks)
            assertEquals(listOf(RAIN), fixture.switchedTo.map { it.second })
        }

    @Test
    fun `a pause lets a playlist which is being made finish, and it is handed over`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)
            fixture.weather = RAIN
            fixture.makingGate = CompletableDeferred()
            fixture.recheck.start()
            passTime(interval)
            assertEquals(listOf(RAIN), fixture.madeFor)

            fixture.recheck.pause()
            fixture.makingGate?.complete(Unit)
            runCurrent()

            assertEquals(listOf(RAIN), fixture.switchedTo.map { it.second })
            assertEquals(RAIN, fixture.recheck.builtFor)
        }

    @Test
    fun `starting twice does not make two timers`() =
        runTest {
            val fixture = fixture()

            fixture.recheck.start()
            fixture.recheck.start()
            passTime(interval)

            assertEquals(1, fixture.looks)
        }

    @Test
    fun `a clock which was set back while paused makes it look straight away`() =
        runTest {
            val fixture = fixture()
            fixture.recheck.start()
            passTime(5.minutes)
            fixture.recheck.pause()

            fixture.clockSkew = -3.hours.inWholeMilliseconds
            fixture.recheck.start()
            runCurrent()

            assertEquals(1, fixture.looks)
        }

    // Stopping, when the screensaver ends

    @Test
    fun `it does not look once it has stopped`() =
        runTest {
            val fixture = fixture()
            fixture.recheck.start()
            passTime(5.minutes)

            fixture.recheck.stop()
            passTime(3.hours)

            assertEquals(0, fixture.looks)
            assertFalse(fixture.recheck.isRunning)
        }

    @Test
    fun `it cannot be started again once it has stopped`() =
        runTest {
            val fixture = fixture()
            fixture.recheck.start()
            fixture.recheck.stop()

            fixture.recheck.start()
            passTime(3.hours)

            assertEquals(0, fixture.looks)
            assertFalse(fixture.recheck.isRunning)
        }

    @Test
    fun `a playlist which is being made when it stops is not handed over`() =
        runTest {
            val fixture = fixture(builtFor = CLEAR)
            fixture.weather = RAIN
            fixture.makingGate = CompletableDeferred()
            fixture.recheck.start()
            passTime(interval)
            assertEquals(listOf(RAIN), fixture.madeFor)

            fixture.recheck.stop()
            fixture.makingGate?.complete(Unit)
            runCurrent()

            assertTrue(fixture.switchedTo.isEmpty())
            assertEquals(CLEAR, fixture.recheck.builtFor)
        }

    @Test
    fun `stopping leaves no coroutine behind`() =
        runTest {
            val job = Job()
            val scope = CoroutineScope(job + StandardTestDispatcher(testScheduler))
            val fixture = Fixture(scope, testScheduler)
            fixture.weather = RAIN
            fixture.makingGate = CompletableDeferred()

            fixture.recheck.start()
            passTime(interval)
            assertEquals(2, job.children.count(), "the timer and the playlist being made")

            fixture.recheck.stop()
            runCurrent()

            assertEquals(0, job.children.count())
            job.cancel()
        }

    @Test
    fun `pausing leaves no timer behind`() =
        runTest {
            val job = Job()
            val scope = CoroutineScope(job + StandardTestDispatcher(testScheduler))
            val fixture = Fixture(scope, testScheduler)

            fixture.recheck.start()
            runCurrent()
            assertEquals(1, job.children.count())

            fixture.recheck.pause()
            runCurrent()

            assertEquals(0, job.children.count())
            job.cancel()
        }

    @Test
    fun `a playlist which was made leaves no coroutine behind`() =
        runTest {
            val job = Job()
            val scope = CoroutineScope(job + StandardTestDispatcher(testScheduler))
            val fixture = Fixture(scope, testScheduler)
            fixture.weather = RAIN

            fixture.recheck.start()
            passTime(interval)

            // Only the timer is left
            assertEquals(1, job.children.count())
            fixture.recheck.stop()
            runCurrent()
            assertEquals(0, job.children.count())
            job.cancel()
        }

    // With the real lookup of the weather, which remembers it for an hour

    private class RealLookup(
        scheduler: TestCoroutineScheduler,
        private val base: Long,
    ) {
        val now = { base + scheduler.currentTime }
        val cache = WeatherConditionCache(clock = now)
        var override: WeatherCondition? = null
        var requestedId: Int? = 500
        var requests = 0

        val provider =
            WeatherConditionProvider(
                cache = cache,
                overrideSource = { override },
                fetchConditionId = {
                    requests++
                    // A request takes a few seconds, and is remembered when it is answered
                    delay(5.seconds)
                    requestedId
                },
            )
    }

    private fun TestScope.realRecheck(
        lookup: RealLookup,
        builtFor: WeatherCondition? = CLEAR,
        interval: Duration = this@WeatherPlaylistRecheckTest.interval,
        switched: MutableList<WeatherCondition> = mutableListOf(),
    ) = WeatherPlaylistRecheck(
        scope = backgroundScope,
        interval = interval,
        builtFor = builtFor,
        currentCondition = { maxAge -> lookup.provider.currentCondition(maxAge) },
        rebuild = { playlistOf(1) },
        onPlaylistReady = { _, condition -> switched += condition },
        clock = lookup.now,
    )

    @Test
    fun `a weather which was remembered for as long as the interval is looked up again`() =
        runTest {
            val lookup = RealLookup(testScheduler, base)
            lookup.cache.put(CLEAR)
            val switched = mutableListOf<WeatherCondition>()
            val recheck = realRecheck(lookup, switched = switched)

            recheck.start()
            passTime(interval)
            passTime(5.seconds)

            assertEquals(1, lookup.requests)
            assertEquals(listOf(RAIN), switched)
        }

    @Test
    fun `a weather which is younger than the interval is used without a request`() =
        runTest {
            val lookup = RealLookup(testScheduler, base)
            val switched = mutableListOf<WeatherCondition>()
            val recheck = realRecheck(lookup, switched = switched)
            recheck.start()

            // The weather overlay finds out about snow ten minutes in
            passTime(10.minutes)
            lookup.cache.put(SNOW)
            passTime(10.minutes)

            assertEquals(0, lookup.requests)
            assertEquals(listOf(SNOW), switched)
        }

    @Test
    fun `every look asks again, as the weather it remembered is no longer young enough`() =
        runTest {
            val lookup = RealLookup(testScheduler, base)
            val recheck = realRecheck(lookup)

            recheck.start()
            passTime(interval * 3 + 10.seconds)

            assertEquals(3, lookup.requests)
        }

    @Test
    fun `the testing override is used for every look without a request`() =
        runTest {
            val lookup = RealLookup(testScheduler, base)
            lookup.override = FOG
            val switched = mutableListOf<WeatherCondition>()
            val recheck = realRecheck(lookup, switched = switched)

            recheck.start()
            passTime(interval * 3)

            assertEquals(0, lookup.requests)
            assertEquals(listOf(FOG), switched)
        }

    @Test
    fun `the testing override can be changed between looks`() =
        runTest {
            val lookup = RealLookup(testScheduler, base)
            lookup.override = FOG
            val switched = mutableListOf<WeatherCondition>()
            val recheck = realRecheck(lookup, builtFor = FOG, switched = switched)

            recheck.start()
            passTime(interval)
            assertTrue(switched.isEmpty())

            lookup.override = SNOW
            passTime(interval)
            assertEquals(listOf(SNOW), switched)

            // Back to the real weather, which is rain
            lookup.override = null
            passTime(interval + 10.seconds)
            assertEquals(listOf(SNOW, RAIN), switched)
        }

    @Test
    fun `without a network the weather is not known and the playlist is kept`() =
        runTest {
            val lookup = RealLookup(testScheduler, base)
            lookup.requestedId = null
            val switched = mutableListOf<WeatherCondition>()
            val recheck = realRecheck(lookup, switched = switched)

            recheck.start()
            passTime(interval * 2 + 10.seconds)

            assertEquals(2, lookup.requests)
            assertTrue(switched.isEmpty())
            assertEquals(CLEAR, recheck.builtFor)
            assertNull(lookup.cache.get())
        }
}
