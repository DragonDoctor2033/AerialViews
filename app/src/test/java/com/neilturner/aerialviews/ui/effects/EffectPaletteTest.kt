package com.neilturner.aerialviews.ui.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EffectPaletteTest {
    private fun alpha(colour: Int) = (colour ushr 24) and 0xFF

    private fun red(colour: Int) = (colour shr 16) and 0xFF

    private fun green(colour: Int) = (colour shr 8) and 0xFF

    private fun blue(colour: Int) = colour and 0xFF

    private fun brightness(colour: Int) = red(colour) + green(colour) + blue(colour)

    @Test
    fun `the tone over the footage is dark slate by day and almost black by night`() {
        val day = EffectPalette.tint(night = 0f, alpha = 1f)
        val night = EffectPalette.tint(night = 1f, alpha = 1f)

        assertEquals(EffectMath.argb(255, 78, 90, 108), day)
        assertEquals(EffectMath.argb(255, 10, 14, 24), night)
        assertTrue(blue(day) > red(day), "blue-grey")
        assertTrue(brightness(night) < brightness(day) / 3)
    }

    @Test
    fun `the colour moves from day to night in between`() {
        val half = EffectPalette.tint(night = 0.5f, alpha = 1f)

        assertEquals(44, red(half))
        assertEquals(52, green(half))
        assertEquals(66, blue(half))
    }

    @Test
    fun `the night is kept between 0 and 1`() {
        assertEquals(EffectPalette.tint(0f, 0.5f), EffectPalette.tint(-3f, 0.5f))
        assertEquals(EffectPalette.tint(1f, 0.5f), EffectPalette.tint(3f, 0.5f))
        assertEquals(EffectPalette.rain(0f, 0.5f), EffectPalette.rain(Float.NaN, 0.5f))
    }

    @Test
    fun `the opacity becomes the alpha of the colour`() {
        assertEquals(0, alpha(EffectPalette.tint(0f, 0f)))
        assertEquals(255, alpha(EffectPalette.tint(0f, 1f)))
        assertEquals(128, alpha(EffectPalette.veil(0f, 0.5f)))
        assertEquals(26, alpha(EffectPalette.rain(0f, 0.1f)))
        assertEquals(0, alpha(EffectPalette.rain(0f, -1f)))
        assertEquals(255, alpha(EffectPalette.rain(0f, 7f)))
    }

    @Test
    fun `rain is white-blue by day and a dimmer blue-grey by night`() {
        val day = EffectPalette.rain(0f, 1f)
        val night = EffectPalette.rain(1f, 1f)

        assertTrue(blue(day) >= green(day) && green(day) > red(day), "white-blue")
        assertTrue(brightness(night) < brightness(day))
        assertTrue(blue(night) > red(night))
    }

    @Test
    fun `the veil of fog is light by day and dark by night`() {
        assertTrue(brightness(EffectPalette.veil(0f, 1f)) > 600)
        assertTrue(brightness(EffectPalette.veil(1f, 1f)) < 250)
    }

    @Test
    fun `a flash is white-blue and as bright as the alpha says`() {
        val flash = EffectPalette.flash(0.7f)

        assertEquals(EffectMath.argb(179, 236, 242, 255), flash)
        assertEquals(0, alpha(EffectPalette.flash(0f)))
    }

    @Test
    fun `things are dimmer by night`() {
        listOf(
            EffectPalette::tintDim,
            EffectPalette::veilDim,
            EffectPalette::fogDim,
            EffectPalette::rainDim,
            EffectPalette::snowDim,
        ).forEach { dim ->
            assertEquals(1f, dim(0f))
            assertTrue(dim(1f) < 1f && dim(1f) > 0.4f, "dimmed to ${dim(1f)}")
            assertTrue(dim(0.5f) < dim(0f) && dim(0.5f) > dim(1f))
            assertEquals(dim(0f), dim(-1f))
            assertEquals(dim(1f), dim(2f))
        }
    }

    @Test
    fun `the particles are dimmed more than the tone, which is dark already`() {
        assertTrue(EffectPalette.rainDim(1f) < EffectPalette.tintDim(1f) + 0.2f)
        assertEquals(0.55f, EffectPalette.rainDim(1f), 0.0001f)
        assertEquals(0.6f, EffectPalette.snowDim(1f), 0.0001f)
        assertEquals(0.6f, EffectPalette.tintDim(1f), 0.0001f)
    }

    @Test
    fun `the fog is lit with all of its light by day and under a third of it by night`() {
        val day = EffectPalette.fogLight(0f)
        val night = EffectPalette.fogLight(1f)

        assertEquals(EffectMath.argb(255, 255, 255, 255), day)
        assertEquals(EffectMath.argb(255, 72, 72, 72), night)
        assertTrue(red(night) * 3 < 255 + 72)
        assertEquals(red(day), green(day))
        assertEquals(red(night), blue(night))
    }

    @Test
    fun `the colour of the fog is white-grey with a touch of blue`() {
        assertTrue(EffectPalette.FOG_BLUE > EffectPalette.FOG_GREEN && EffectPalette.FOG_GREEN > EffectPalette.FOG_RED)
        assertTrue(EffectPalette.FOG_RED > 200)
    }

    @Test
    fun `no layer is more opaque than the most it is meant to be`() {
        assertTrue(EffectPalette.TINT_MAX_ALPHA in 0.1f..0.4f)
        assertTrue(EffectPalette.VEIL_MAX_ALPHA in 0.1f..0.4f)
        assertTrue(EffectPalette.FLASH_MAX_ALPHA in 0.5f..0.9f)
        // Fog stacks up, so the layers are well below solid even together
        val together = 1f - EffectPalette.FOG_LAYER_ALPHA.fold(1f) { clear, layer -> clear * (1f - layer) }
        assertTrue(together < 0.8f, "three layers together: $together")
        assertTrue(EffectPalette.FOG_LAYER_ALPHA.all { it in 0.1f..0.5f })
    }
}
