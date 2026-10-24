package com.neilturner.aerialviews.ui.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EffectMathTest {
    @Test
    fun `a share is kept between 0 and 1`() {
        assertEquals(0f, EffectMath.clamp01(-3f))
        assertEquals(0f, EffectMath.clamp01(0f))
        assertEquals(0.25f, EffectMath.clamp01(0.25f))
        assertEquals(1f, EffectMath.clamp01(1f))
        assertEquals(1f, EffectMath.clamp01(7f))
        assertEquals(1f, EffectMath.clamp01(Float.POSITIVE_INFINITY))
        assertEquals(0f, EffectMath.clamp01(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun `a share which is not a number is 0`() {
        assertEquals(0f, EffectMath.clamp01(Float.NaN))
        assertEquals(0f, EffectMath.smoothstep(Float.NaN))
        assertEquals(0, EffectMath.toByte(Float.NaN))
    }

    @Test
    fun `lerp goes from the first value to the second`() {
        assertEquals(10f, EffectMath.lerp(10f, 20f, 0f))
        assertEquals(15f, EffectMath.lerp(10f, 20f, 0.5f))
        assertEquals(20f, EffectMath.lerp(10f, 20f, 1f))
        assertEquals(5f, EffectMath.lerp(10f, 0f, 0.5f))
    }

    @Test
    fun `smoothstep starts at 0, ends at 1 and is half way half way`() {
        assertEquals(0f, EffectMath.smoothstep(0f))
        assertEquals(0.5f, EffectMath.smoothstep(0.5f), 0.0001f)
        assertEquals(1f, EffectMath.smoothstep(1f))
        assertEquals(0f, EffectMath.smoothstep(-1f))
        assertEquals(1f, EffectMath.smoothstep(2f))
    }

    @Test
    fun `smoothstep is slow at the ends and quick in the middle`() {
        // A linear fade would be at 0.1 at 10%, and 0.9 at 90%
        assertTrue(EffectMath.smoothstep(0.1f) < 0.03f)
        assertTrue(EffectMath.smoothstep(0.9f) > 0.97f)
    }

    @Test
    fun `smoothstep never goes back`() {
        var previous = -1f
        for (step in 0..200) {
            val value = EffectMath.smoothstep(step / 200f)
            assertTrue(value >= previous, "step $step")
            previous = value
        }
    }

    @Test
    fun `what fades out and what fades in always add up to 1`() {
        // So an effect which replaces another does not make the picture lighter or darker half way
        for (step in 0..100) {
            val progress = step / 100f
            val sum = EffectMath.smoothstep(progress) + EffectMath.smoothstep(1f - progress)
            assertEquals(1f, sum, 0.0001f, "at $progress")
        }
    }

    @Test
    fun `argb makes the same colours as android Color`() {
        assertEquals(0xFFFF0000.toInt(), EffectMath.argb(255, 255, 0, 0))
        assertEquals(0x80102030.toInt(), EffectMath.argb(0x80, 0x10, 0x20, 0x30))
        assertEquals(0x00000000, EffectMath.argb(0, 0, 0, 0))
        assertEquals(0xFFFFFFFF.toInt(), EffectMath.argb(255, 255, 255, 255))
        assertEquals(0x7F000001, EffectMath.argb(0x7F, 0, 0, 1))
    }

    @Test
    fun `a share is turned into a byte and rounded`() {
        assertEquals(0, EffectMath.toByte(0f))
        assertEquals(255, EffectMath.toByte(1f))
        assertEquals(128, EffectMath.toByte(0.5f))
        assertEquals(26, EffectMath.toByte(0.1f))
        assertEquals(0, EffectMath.toByte(-1f))
        assertEquals(255, EffectMath.toByte(3f))
    }

    @Test
    fun `a colour channel moves from day to night`() {
        assertEquals(200, EffectMath.mixChannel(200, 100, 0f))
        assertEquals(150, EffectMath.mixChannel(200, 100, 0.5f))
        assertEquals(100, EffectMath.mixChannel(200, 100, 1f))
        assertEquals(200, EffectMath.mixChannel(200, 100, -1f))
        assertEquals(100, EffectMath.mixChannel(200, 100, 5f))
    }
}
