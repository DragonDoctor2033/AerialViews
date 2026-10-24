package com.neilturner.aerialviews.ui.overlays.utils

import com.neilturner.aerialviews.models.enums.AnalogClockSecondHandMotion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AnalogClockRefreshTest {
    private val alphas = listOf(0f, 0.001f, 0.1f, 0.5f, 0.6f, 1f)

    @Test
    fun `only a second hand which is shown and set to sweep sweeps`() {
        assertTrue(AnalogClockRefresh.sweeps(AnalogClockSecondHandMotion.SMOOTH, showSecondHand = true))

        assertFalse(AnalogClockRefresh.sweeps(AnalogClockSecondHandMotion.TICK, showSecondHand = true))
        assertFalse(AnalogClockRefresh.sweeps(AnalogClockSecondHandMotion.SMOOTH, showSecondHand = false))
        assertFalse(AnalogClockRefresh.sweeps(AnalogClockSecondHandMotion.TICK, showSecondHand = false))
    }

    @Test
    fun `the default motion sweeps`() {
        assertTrue(AnalogClockRefresh.sweeps(AnalogClockSecondHandMotion.DEFAULT, showSecondHand = true))
        assertTrue(AnalogClockRefresh.sweeps(AnalogClockSecondHandMotion.fromValue(null), showSecondHand = true))
    }

    @Test
    fun `a clock which is not on screen does nothing at all, however it is set and faded`() {
        alphas.forEach { alpha ->
            assertEquals(
                ClockRefresh.STOPPED,
                AnalogClockRefresh.plan(sweeps = true, onScreen = false, alpha = alpha),
                "sweeping at $alpha",
            )
            assertEquals(
                ClockRefresh.STOPPED,
                AnalogClockRefresh.plan(sweeps = false, onScreen = false, alpha = alpha),
                "ticking at $alpha",
            )
        }
    }

    @Test
    fun `a sweeping clock is redrawn on every frame while it can be seen`() {
        alphas.filter { it > 0f }.forEach { alpha ->
            assertEquals(ClockRefresh.EVERY_FRAME, AnalogClockRefresh.plan(sweeps = true, onScreen = true, alpha = alpha), "at $alpha")
        }
    }

    @Test
    fun `a sweeping clock which is faded out stops completely and waits for the fade in`() {
        assertEquals(ClockRefresh.WAITING_FOR_FADE_IN, AnalogClockRefresh.plan(sweeps = true, onScreen = true, alpha = 0f))
        assertEquals(ClockRefresh.WAITING_FOR_FADE_IN, AnalogClockRefresh.plan(sweeps = true, onScreen = true, alpha = -0.5f))
        assertEquals(ClockRefresh.WAITING_FOR_FADE_IN, AnalogClockRefresh.plan(sweeps = true, onScreen = true, alpha = Float.NaN))
    }

    @Test
    fun `the first bit of the fade in starts the frames again`() {
        assertEquals(ClockRefresh.WAITING_FOR_FADE_IN, AnalogClockRefresh.plan(sweeps = true, onScreen = true, alpha = 0f))
        assertEquals(ClockRefresh.EVERY_FRAME, AnalogClockRefresh.plan(sweeps = true, onScreen = true, alpha = Float.MIN_VALUE))
        assertEquals(ClockRefresh.EVERY_FRAME, AnalogClockRefresh.plan(sweeps = true, onScreen = true, alpha = 0.0001f))
    }

    @Test
    fun `a ticking clock goes on once a second while it is on screen, faded out or not`() {
        // As it always has: it only skips the redraw while faded out, which the view takes care of
        alphas.forEach { alpha ->
            assertEquals(ClockRefresh.EVERY_SECOND, AnalogClockRefresh.plan(sweeps = false, onScreen = true, alpha = alpha), "at $alpha")
        }
    }

    @Test
    fun `without a second hand the smooth motion needs a redraw once a second, not on every frame`() {
        val sweeps = AnalogClockRefresh.sweeps(AnalogClockSecondHandMotion.SMOOTH, showSecondHand = false)

        alphas.forEach { alpha ->
            assertEquals(ClockRefresh.EVERY_SECOND, AnalogClockRefresh.plan(sweeps, onScreen = true, alpha = alpha), "at $alpha")
        }
    }

    @Test
    fun `a clock fading out and in runs on frames, stops, and starts again`() {
        val smooth = AnalogClockRefresh.sweeps(AnalogClockSecondHandMotion.SMOOTH, showSecondHand = true)
        val fade = listOf(0.6f, 0.3f, 0.05f, 0f, 0f, 0.02f, 0.4f, 0.6f)

        assertEquals(
            listOf(
                ClockRefresh.EVERY_FRAME,
                ClockRefresh.EVERY_FRAME,
                ClockRefresh.EVERY_FRAME,
                ClockRefresh.WAITING_FOR_FADE_IN,
                ClockRefresh.WAITING_FOR_FADE_IN,
                ClockRefresh.EVERY_FRAME,
                ClockRefresh.EVERY_FRAME,
                ClockRefresh.EVERY_FRAME,
            ),
            fade.map { AnalogClockRefresh.plan(smooth, onScreen = true, alpha = it) },
        )
    }

    @Test
    fun `blackout stops a clock for good and showing it again brings it back the way it was`() {
        val smooth = AnalogClockRefresh.sweeps(AnalogClockSecondHandMotion.SMOOTH, showSecondHand = true)
        val ticking = AnalogClockRefresh.sweeps(AnalogClockSecondHandMotion.TICK, showSecondHand = true)

        // Shown, in blackout (hidden, whatever the alpha is), shown again
        listOf(true, false, true)
            .map { onScreen ->
                AnalogClockRefresh.plan(smooth, onScreen, alpha = 0.6f)
            }.let { plans ->
                assertEquals(listOf(ClockRefresh.EVERY_FRAME, ClockRefresh.STOPPED, ClockRefresh.EVERY_FRAME), plans)
            }
        listOf(true, false, true)
            .map { onScreen ->
                AnalogClockRefresh.plan(ticking, onScreen, alpha = 0.6f)
            }.let { plans ->
                assertEquals(listOf(ClockRefresh.EVERY_SECOND, ClockRefresh.STOPPED, ClockRefresh.EVERY_SECOND), plans)
            }
    }

    @Test
    fun `a sweeping clock which is shown again while it is faded out waits for the fade in`() {
        val smooth = AnalogClockRefresh.sweeps(AnalogClockSecondHandMotion.SMOOTH, showSecondHand = true)

        // Hidden for blackout while faded out, then shown again with the alpha still at 0
        assertEquals(ClockRefresh.STOPPED, AnalogClockRefresh.plan(smooth, onScreen = false, alpha = 0f))
        assertEquals(ClockRefresh.WAITING_FOR_FADE_IN, AnalogClockRefresh.plan(smooth, onScreen = true, alpha = 0f))
    }

    @Test
    fun `only a sweeping clock is ever redrawn on every frame`() {
        for (sweeps in listOf(true, false)) {
            for (onScreen in listOf(true, false)) {
                for (alpha in alphas) {
                    val plan = AnalogClockRefresh.plan(sweeps, onScreen, alpha)

                    if (plan == ClockRefresh.EVERY_FRAME) {
                        assertTrue(sweeps && onScreen && alpha > 0f, "frames for sweeps=$sweeps onScreen=$onScreen alpha=$alpha")
                    }
                    if (plan == ClockRefresh.EVERY_SECOND) {
                        assertTrue(!sweeps && onScreen, "seconds for sweeps=$sweeps onScreen=$onScreen alpha=$alpha")
                    }
                }
            }
        }
    }
}
