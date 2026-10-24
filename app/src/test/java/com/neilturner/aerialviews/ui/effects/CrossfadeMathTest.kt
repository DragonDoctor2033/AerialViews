package com.neilturner.aerialviews.ui.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CrossfadeMathTest {
    private val fade = CrossfadeMath.FADE_MILLIS

    @Test
    fun `a fade takes about two seconds`() {
        assertEquals(2000f, CrossfadeMath.FADE_MILLIS)
        assertEquals(0.5f, CrossfadeMath.approach(0f, 1f, 1000f, fade), 0.0001f)
        assertEquals(1f, CrossfadeMath.approach(0f, 1f, 2000f, fade))
    }

    @Test
    fun `it fades out as fast as it fades in`() {
        assertEquals(0.5f, CrossfadeMath.approach(1f, 0f, 1000f, fade), 0.0001f)
        assertEquals(0f, CrossfadeMath.approach(1f, 0f, 2000f, fade))
    }

    @Test
    fun `it never goes past the target`() {
        assertEquals(1f, CrossfadeMath.approach(0.9f, 1f, 5000f, fade))
        assertEquals(0f, CrossfadeMath.approach(0.1f, 0f, 5000f, fade))
        assertEquals(0.4f, CrossfadeMath.approach(0.3f, 0.4f, 1000f, fade), 0.0001f)
        assertEquals(0.4f, CrossfadeMath.approach(0.5f, 0.4f, 1000f, fade), 0.0001f)
    }

    @Test
    fun `it stays where it is at the target`() {
        assertEquals(0.7f, CrossfadeMath.approach(0.7f, 0.7f, 16f, fade))
    }

    @Test
    fun `a change of a fraction takes that fraction of the time`() {
        // A change of 0.5 is a half of the fade, so a second
        assertEquals(0.55f, CrossfadeMath.approach(0.3f, 0.8f, 500f, fade), 0.0001f)
        assertEquals(0.8f, CrossfadeMath.approach(0.3f, 0.8f, 1000f, fade), 0.0001f)
    }

    @Test
    fun `no time, negative time or time which is not a number moves nothing`() {
        assertEquals(0.3f, CrossfadeMath.approach(0.3f, 1f, 0f, fade))
        assertEquals(0.3f, CrossfadeMath.approach(0.3f, 1f, -100f, fade))
        assertEquals(0.3f, CrossfadeMath.approach(0.3f, 1f, Float.NaN, fade))
    }

    @Test
    fun `a duration which is zero, negative or not a number moves nothing`() {
        assertEquals(0.3f, CrossfadeMath.approach(0.3f, 1f, 16f, 0f))
        assertEquals(0.3f, CrossfadeMath.approach(0.3f, 1f, 16f, -5f))
        assertEquals(0.3f, CrossfadeMath.approach(0.3f, 1f, 16f, Float.NaN))
    }

    @Test
    fun `a fade is the same at 60 and 30 frames a second`() {
        fun run(
            frameMillis: Float,
            totalMillis: Float,
        ): Float {
            var value = 0f
            var time = 0f
            while (time < totalMillis - 0.001f) {
                value = CrossfadeMath.approach(value, 1f, frameMillis, fade)
                time += frameMillis
            }
            return value
        }

        // 1.2 seconds with 72 frames of 1/60 s or 36 frames of 1/30 s
        assertEquals(run(1000f / 60f, 1200f), run(1000f / 30f, 1200f), 0.001f)
        assertEquals(0.6f, run(1000f / 60f, 1200f), 0.001f)
        assertEquals(1f, run(1000f / 30f, 2000f), 0.0001f)
    }

    @Test
    fun `a fade in many small steps ends at the target`() {
        var value = 0f
        repeat(200) { value = CrossfadeMath.approach(value, 1f, 16.7f, fade) }

        assertEquals(1f, value)
    }

    @Test
    fun `the quick fade out takes half a second, the slow fade takes three`() {
        assertEquals(0f, CrossfadeMath.approach(1f, 0f, CrossfadeMath.QUICK_FADE_OUT_MILLIS, CrossfadeMath.QUICK_FADE_OUT_MILLIS))
        assertEquals(500f, CrossfadeMath.QUICK_FADE_OUT_MILLIS)
        assertEquals(3000f, CrossfadeMath.SLOW_FADE_MILLIS)
        assertTrue(CrossfadeMath.QUICK_FADE_OUT_MILLIS < CrossfadeMath.FADE_MILLIS)
        assertTrue(CrossfadeMath.FADE_MILLIS < CrossfadeMath.SLOW_FADE_MILLIS)
    }

    @Test
    fun `values which differ by less than a thousandth are the same`() {
        assertTrue(CrossfadeMath.isNear(0.5f, 0.5f))
        assertTrue(CrossfadeMath.isNear(0.5f, 0.5004f))
        assertTrue(CrossfadeMath.isNear(0.5004f, 0.5f))
        assertFalse(CrossfadeMath.isNear(0.5f, 0.502f))
        assertFalse(CrossfadeMath.isNear(0f, 1f))
    }
}
