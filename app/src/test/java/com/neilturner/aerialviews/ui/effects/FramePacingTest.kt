package com.neilturner.aerialviews.ui.effects

import com.neilturner.aerialviews.models.enums.WeatherEffectsFrameRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FramePacingTest {
    private val millis = 1_000_000L

    @Test
    fun `the time between frames is a second divided by the frame rate`() {
        assertEquals(16_666_666L, FramePacing.intervalNanos(60))
        assertEquals(33_333_333L, FramePacing.intervalNanos(30))
        assertEquals(1_000_000_000L, FramePacing.intervalNanos(1))
    }

    @Test
    fun `a frame rate of nothing or less is a frame a second, not a crash`() {
        assertEquals(1_000_000_000L, FramePacing.intervalNanos(0))
        assertEquals(1_000_000_000L, FramePacing.intervalNanos(-30))
    }

    @Test
    fun `a frame is on time at 70 percent of the time between two frames`() {
        assertTrue(FramePacing.shouldDraw(17 * millis, 60))
        assertTrue(FramePacing.shouldDraw(12 * millis, 60))
        assertFalse(FramePacing.shouldDraw(11 * millis, 60))
        assertFalse(FramePacing.shouldDraw(8 * millis, 60))

        assertTrue(FramePacing.shouldDraw(33 * millis, 30))
        assertTrue(FramePacing.shouldDraw(24 * millis, 30))
        assertFalse(FramePacing.shouldDraw(23 * millis, 30))
        assertFalse(FramePacing.shouldDraw(16 * millis, 30))
    }

    @Test
    fun `a frame which was a long time ago is always on time`() {
        assertTrue(FramePacing.shouldDraw(5_000 * millis, 60))
        assertTrue(FramePacing.shouldDraw(5_000 * millis, 30))
    }

    @Test
    fun `at the frame rate of the display there is no waiting, at a lower one there is`() {
        assertEquals(0L, FramePacing.waitMillis(60))
        assertEquals(0L, FramePacing.waitMillis(120))
        assertEquals(28L, FramePacing.waitMillis(30))
        assertEquals(35L, FramePacing.waitMillis(24))
        assertEquals(850L, FramePacing.waitMillis(1))
    }

    @Test
    fun `the frame rates which can be picked are 60 and 30`() {
        assertEquals(60, WeatherEffectsFrameRate.FPS_60.fps)
        assertEquals(30, WeatherEffectsFrameRate.FPS_30.fps)
        assertEquals(60, FramePacing.DISPLAY_FPS)
    }

    // Plays the frames of a display for ten seconds the way the view does: a callback comes at every
    // frame of the display, nothing is drawn if it is too early, and the next callback is asked for
    // after the wait. Returns the frames which were drawn in a second
    private fun framesDrawnPerSecond(
        displayHz: Int,
        fps: Int,
    ): Double {
        val vsync = 1_000_000_000.0 / displayHz
        val end = 10_000_000_000.0
        var now = 0.0
        var lastDraw = Double.NaN
        var drawn = 0

        while (now < end) {
            if (lastDraw.isNaN() || FramePacing.shouldDraw((now - lastDraw).toLong(), fps)) {
                lastDraw = now
                drawn++
            }
            // The next callback comes at the first frame of the display after the wait, and not at this one
            val due = now + FramePacing.waitMillis(fps) * millis
            now = maxOf(Math.ceil(due / vsync) * vsync, now + vsync)
        }
        return drawn / 10.0
    }

    @Test
    fun `60 frames a second on a display of 60 draws every frame`() {
        assertEquals(60.0, framesDrawnPerSecond(60, 60), 0.5)
    }

    @Test
    fun `30 frames a second on a display of 60 draws every other frame`() {
        assertEquals(30.0, framesDrawnPerSecond(60, 30), 0.5)
    }

    @Test
    fun `on a display of 120 the frames are held to 60 or 30`() {
        assertEquals(60.0, framesDrawnPerSecond(120, 60), 0.5)
        assertEquals(30.0, framesDrawnPerSecond(120, 30), 0.5)
    }

    @Test
    fun `on a display that was switched to match the video there are as many frames as the display has`() {
        // The refresh rate follows the video, so a display of 24, 25 or 50 gives fewer frames than were asked for
        assertEquals(24.0, framesDrawnPerSecond(24, 60), 0.5)
        assertEquals(24.0, framesDrawnPerSecond(24, 30), 0.5)
        assertEquals(50.0, framesDrawnPerSecond(50, 60), 0.5)
        assertEquals(25.0, framesDrawnPerSecond(50, 30), 0.5)
    }

    @Test
    fun `the effects never move by more than a tenth of a second at a time`() {
        assertEquals(100f, FramePacing.MAX_STEP_MILLIS)
        assertEquals(16.5f, FramePacing.stepMillis(16_500_000L), 0.001f)
        assertEquals(100f, FramePacing.stepMillis(5_000 * millis))
        assertEquals(100f, FramePacing.stepMillis(100 * millis))
    }

    @Test
    fun `the effects never move back`() {
        assertEquals(0f, FramePacing.stepMillis(-5 * millis))
        assertEquals(0f, FramePacing.stepMillis(0L))
    }
}
