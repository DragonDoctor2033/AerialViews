package com.neilturner.aerialviews.ui.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class LightningSchedulerTest {
    private val frame = 5L

    // Runs a storm for a time and returns when each strike started, and the brightness of every frame
    private class Storm(
        val strikes: List<Long>,
        val brightness: List<Float>,
    )

    private fun storm(
        scheduler: LightningScheduler,
        millis: Long,
        frameMillis: Long = frame,
        from: Long = 0L,
    ): Storm {
        val starts = mutableListOf<Long>()
        val levels = ArrayList<Float>()
        var seen = scheduler.strikes
        var now = from
        while (now < from + millis) {
            levels.add(scheduler.brightness(now, enabled = true))
            if (scheduler.strikes > seen) {
                starts.add(now)
                seen = scheduler.strikes
            }
            now += frameMillis
        }
        return Storm(starts, levels)
    }

    private fun newScheduler(
        seed: Long,
        chance: Float = LightningScheduler.DOUBLE_FLASH_CHANCE,
    ) = LightningScheduler(Random(seed), doubleFlashChance = chance)

    // The numbers

    @Test
    fun `strikes come 8 to 25 seconds apart and never closer than 4 seconds`() {
        assertEquals(8_000L, LightningScheduler.MIN_INTERVAL_MILLIS)
        assertEquals(25_000L, LightningScheduler.MAX_INTERVAL_MILLIS)
        assertEquals(4_000L, LightningScheduler.MIN_GAP_MILLIS)
    }

    // When

    @Test
    fun `nothing happens before the first strike`() {
        val storm = storm(newScheduler(1), 3_900L)

        assertTrue(storm.strikes.isEmpty())
        assertTrue(storm.brightness.all { it == 0f })
    }

    @Test
    fun `the first strike comes between 4 and 12 and a half seconds into the storm`() {
        for (seed in 1L..40L) {
            val first = storm(newScheduler(seed), 13_000L).strikes.firstOrNull()

            assertTrue(first != null && first in 4_000L..12_500L + frame, "seed $seed: first strike at $first")
        }
    }

    @Test
    fun `every strike after the first comes 8 to 25 seconds after the one before`() {
        for (seed in 1L..10L) {
            val strikes = storm(newScheduler(seed), 30 * 60_000L).strikes

            assertTrue(strikes.size > 60, "seed $seed: only ${strikes.size} strikes in half an hour")
            strikes.zipWithNext { before, after ->
                val gap = after - before
                assertTrue(gap >= 8_000L, "seed $seed: $gap ms between strikes")
                assertTrue(gap <= 25_000L + frame, "seed $seed: $gap ms between strikes")
            }
        }
    }

    @Test
    fun `the gaps are at random, not always the same`() {
        val gaps = storm(newScheduler(3), 30 * 60_000L).strikes.zipWithNext { before, after -> after - before }

        assertTrue(gaps.toSet().size > 30, "${gaps.toSet().size} different gaps")
        assertTrue(gaps.min() < 11_000L && gaps.max() > 22_000L, "gaps from ${gaps.min()} to ${gaps.max()}")
        // About the middle of 8 and 25 seconds
        assertEquals(16_500.0, gaps.average(), 2_000.0)
    }

    @Test
    fun `two strikes are never within 4 seconds, whatever the intervals are`() {
        for (seed in 1L..10L) {
            val quick = LightningScheduler(Random(seed), minIntervalMillis = 500L, maxIntervalMillis = 2_000L)
            val strikes = storm(quick, 10 * 60_000L).strikes

            assertTrue(strikes.size > 100, "seed $seed: ${strikes.size} strikes")
            strikes.zipWithNext { before, after ->
                assertTrue(after - before >= LightningScheduler.MIN_GAP_MILLIS, "seed $seed: ${after - before} ms between strikes")
            }
        }
    }

    @Test
    fun `strikes are the same with the same seed and different with another`() {
        val first = storm(newScheduler(5), 5 * 60_000L)
        val same = storm(newScheduler(5), 5 * 60_000L)
        val other = storm(newScheduler(6), 5 * 60_000L)

        assertEquals(first.strikes, same.strikes)
        assertEquals(first.brightness, same.brightness)
        assertTrue(first.strikes != other.strikes)
    }

    @Test
    fun `the number of strikes is counted`() {
        val scheduler = newScheduler(2)

        val storm = storm(scheduler, 10 * 60_000L)

        assertEquals(storm.strikes.size, scheduler.strikes)
    }

    // Looks

    @Test
    fun `the sky is never brighter than 1 or darker than 0`() {
        for (seed in 1L..5L) {
            val levels = storm(newScheduler(seed), 10 * 60_000L).brightness

            assertTrue(levels.all { it in 0f..1f }, "seed $seed")
            assertTrue(levels.any { it > 0.7f })
        }
    }

    @Test
    fun `the sky is dark most of the time, a storm is not a strobe`() {
        val levels = storm(newScheduler(4), 10 * 60_000L).brightness

        val lit = levels.count { it > 0f }
        assertTrue(lit < levels.size / 10, "lit for $lit of ${levels.size} frames")
    }

    // Each strike as the viewer sees it, cut from the frames: from its start to 700 ms later
    private fun strikes(
        seed: Long,
        chance: Float,
        millis: Long = 20 * 60_000L,
    ): List<List<Float>> {
        val storm = storm(newScheduler(seed, chance), millis)
        val frames = (700 / frame).toInt()
        return storm.strikes
            .filter { it + 700 <= millis }
            .map { start -> storm.brightness.subList((start / frame).toInt(), (start / frame).toInt() + frames) }
    }

    // A double flash falls, and then rises again
    private fun isDouble(strike: List<Float>): Boolean {
        var falling = false
        for (i in 1 until strike.size) {
            if (strike[i] < strike[i - 1] - 0.0001f) falling = true
            if (falling && strike[i] > strike[i - 1] + 0.0001f) return true
        }
        return false
    }

    @Test
    fun `a strike lights up at once, is brightest after 25 ms, and fades away`() {
        strikes(1, chance = 0f).forEach { strike ->
            assertEquals(0f, strike[0])
            val peak = strike.max()
            assertTrue(peak in 0.75f..1f, "peak $peak")
            assertEquals(peak, strike[(25 / frame).toInt()], 0.0001f)
            assertFalse(isDouble(strike))
            // And it is over in just over half a second
            assertEquals(0f, strike[(550 / frame).toInt()])
        }
    }

    @Test
    fun `a single flash only ever fades after it has peaked`() {
        strikes(2, chance = 0f).forEach { strike ->
            val peakAt = strike.indexOf(strike.max())
            for (i in peakAt + 1 until strike.size) {
                assertTrue(strike[i] <= strike[i - 1] + 0.0001f, "rises again at $i")
            }
        }
    }

    @Test
    fun `with no chance of a double flash there is none, with every chance every strike is one`() {
        val singles = strikes(3, chance = 0f)
        val doubles = strikes(3, chance = 1f)

        assertTrue(singles.size > 50 && doubles.size > 50)
        assertTrue(singles.none { isDouble(it) })
        assertTrue(doubles.all { isDouble(it) })
    }

    @Test
    fun `about a third of the strikes are a double flash`() {
        val strikes = strikes(7, chance = LightningScheduler.DOUBLE_FLASH_CHANCE, millis = 60 * 60_000L)

        val share = strikes.count { isDouble(it) }.toFloat() / strikes.size
        assertEquals(0.35f, share, 0.1f)
        assertTrue(strikes.size > 150)
    }

    @Test
    fun `the second flash of a double flash comes 130 to 260 ms after the first, and is weaker`() {
        strikes(8, chance = 1f).forEach { strike ->
            val firstPeak = strike.max()
            // The flash fades, then rises again for the second flash, and fades again
            val dip = (6 until strike.size - 1).first { strike[it + 1] > strike[it] + 0.0001f }
            val secondPeak = (dip + 1 until strike.size - 1).first { strike[it + 1] <= strike[it] }

            // The second flash starts 130 to 260 ms in, and takes 20 ms to peak
            assertTrue(secondPeak * frame in 150L..285L, "second peak at ${secondPeak * frame} ms")
            assertTrue(strike[secondPeak] < firstPeak)
            assertEquals(0.7f, strike[secondPeak] / firstPeak, 0.06f)
        }
    }

    // Switching it off

    @Test
    fun `when the storm is off nothing is planned and the sky stays dark`() {
        val scheduler = newScheduler(1)

        for (now in 0L..60_000L step 100L) {
            assertEquals(0f, scheduler.brightness(now, enabled = false))
        }
        assertEquals(0, scheduler.strikes)
    }

    @Test
    fun `a strike which has started goes on when the storm is switched off, and there is no other`() {
        val storm = storm(newScheduler(1, chance = 0f), 30_000L)
        val start = storm.strikes.first()

        // The same storm, switched off 60 ms into the first strike
        val again = newScheduler(1, chance = 0f)
        val levels = mutableListOf<Float>()
        var now = 0L
        while (now < 30_000L) {
            levels.add(again.brightness(now, enabled = now < start + 60L))
            now += frame
        }

        val strikeFrame = (start / frame).toInt()
        // The strike is the same as in the storm which was left on
        assertEquals(storm.brightness.subList(strikeFrame, strikeFrame + 100), levels.subList(strikeFrame, strikeFrame + 100))
        // And nothing comes after it
        assertTrue(levels.drop(strikeFrame + 110).all { it == 0f })
        assertEquals(1, again.strikes)
    }

    @Test
    fun `when the storm comes back the first strike is a few seconds away again`() {
        val scheduler = newScheduler(9)
        storm(scheduler, 30_000L)
        for (now in 30_000L..40_000L step 100L) scheduler.brightness(now, enabled = false)

        val storm = storm(scheduler, 20_000L, from = 40_000L)

        val first = storm.strikes.first() - 40_000L
        assertTrue(first in 4_000L..12_500L + frame, "first strike $first ms after the storm came back")
    }

    @Test
    fun `a storm which comes back right away is still 4 seconds from the strike before`() {
        // Intervals so short that only the gap keeps the strikes apart
        val scheduler = LightningScheduler(Random(1), minIntervalMillis = 100L, maxIntervalMillis = 200L, doubleFlashChance = 0f)
        var now = 0L
        while (scheduler.strikes == 0) {
            scheduler.brightness(now, enabled = true)
            now += frame
        }
        val firstStrike = now - frame

        // Off for an instant in the middle of the strike, and on again
        scheduler.brightness(now + 100L, enabled = false)
        val storm = storm(scheduler, 30_000L, from = now + 150L)

        val second = storm.strikes.first()
        assertTrue(second - firstStrike >= 4_000L, "${second - firstStrike} ms after the first strike")
        assertTrue(second - firstStrike < 4_000L + 300L, "${second - firstStrike} ms after the first strike")
    }

    // A screen which was not looked at

    @Test
    fun `a strike which was due while nothing was drawn is not made up for with a late flash`() {
        val scheduler = newScheduler(1)
        scheduler.brightness(0L, enabled = true)

        // Ten minutes later a frame comes, and a strike was due long ago
        val level = scheduler.brightness(10 * 60_000L, enabled = true)

        assertEquals(0f, level)
        assertEquals(0, scheduler.strikes)
    }

    @Test
    fun `after a pause the next strike is 8 to 25 seconds away`() {
        val scheduler = newScheduler(1)
        scheduler.brightness(0L, enabled = true)
        val resumed = 10 * 60_000L

        val storm = storm(scheduler, 40_000L, from = resumed)

        val wait = storm.strikes.first() - resumed
        assertTrue(wait in 8_000L..25_000L + frame, "waited $wait ms")
    }

    @Test
    fun `a frame which comes a little late still gets its strike`() {
        val due = storm(newScheduler(1, chance = 0f), 14_000L).strikes.first()

        // The same storm, but frames come once a second, so the strike is late by up to a second
        val late = storm(newScheduler(1, chance = 0f), 14_000L, frameMillis = 1_000L).strikes.first()

        assertTrue(late in due - frame..due + 1_000L, "strike at $late instead of $due")
    }

    // The shape of a flash

    @Test
    fun `a flash is dark before it starts and after it is over`() {
        assertEquals(0f, LightningFlash.flash(-10f))
        assertEquals(0f, LightningFlash.flash(0f))
        assertEquals(0f, LightningFlash.flash(Float.NaN))
        assertEquals(0f, LightningFlash.flash(LightningFlash.SINGLE_MILLIS.toFloat()))
        assertEquals(0f, LightningFlash.flash(5_000f))
    }

    @Test
    fun `a flash rises in 25 ms to its full brightness and then fades`() {
        assertEquals(0.5f, LightningFlash.flash(12.5f), 0.0001f)
        assertEquals(1f, LightningFlash.flash(25f))
        assertTrue(LightningFlash.flash(60f) < 1f)
        assertTrue(LightningFlash.flash(60f) > LightningFlash.flash(120f))
        assertTrue(LightningFlash.flash(120f) > LightningFlash.flash(300f))
    }

    @Test
    fun `a flash fades to nothing smoothly, without a jump at the end`() {
        var previous = 1f
        var biggestJump = 0f
        for (ms in 25..(LightningFlash.SINGLE_MILLIS + 20).toInt()) {
            val level = LightningFlash.flash(ms.toFloat())
            assertTrue(level <= previous)
            biggestJump = maxOf(biggestJump, previous - level)
            previous = level
        }
        // Not even the last step to nothing is more than a hundredth
        assertTrue(biggestJump < 0.02f, "biggest jump $biggestJump")
        assertEquals(0f, previous)
    }

    @Test
    fun `a double flash is the first flash and then a second one, weaker, after the delay`() {
        // The first flash has faded to 0.1 by 280 ms, and the second flash peaks at 0.7
        assertEquals(LightningFlash.flash(100f), LightningFlash.level(100L, doubleFlash = true, secondDelayMillis = 260L))
        assertEquals(0.7f, LightningFlash.level(280L, doubleFlash = true, secondDelayMillis = 260L), 0.0001f)
        assertEquals(LightningFlash.flash(280f), LightningFlash.level(280L, doubleFlash = false, secondDelayMillis = 260L))
        assertEquals(0.7f, LightningFlash.SECOND_FLASH_PEAK)
    }

    @Test
    fun `a double flash is never darker than a single one`() {
        for (delay in listOf(130L, 200L, 260L)) {
            for (ms in 0L..800L step 5L) {
                val single = LightningFlash.level(ms, doubleFlash = false, secondDelayMillis = delay)
                val double = LightningFlash.level(ms, doubleFlash = true, secondDelayMillis = delay)

                assertTrue(double >= single, "delay $delay at $ms ms")
                assertTrue(double in 0f..1f)
            }
        }
    }

    @Test
    fun `a strike takes just over half a second, a double one the delay and a bit more`() {
        assertEquals(540L, LightningFlash.durationMillis(doubleFlash = false, secondDelayMillis = 200L))
        assertEquals(640L, LightningFlash.durationMillis(doubleFlash = true, secondDelayMillis = 200L))
        assertEquals(700L, LightningFlash.durationMillis(doubleFlash = true, secondDelayMillis = 260L))
        assertEquals(570L, LightningFlash.durationMillis(doubleFlash = true, secondDelayMillis = 130L))
        assertEquals(0f, LightningFlash.level(LightningFlash.durationMillis(true, 260L), true, 260L))
    }
}
