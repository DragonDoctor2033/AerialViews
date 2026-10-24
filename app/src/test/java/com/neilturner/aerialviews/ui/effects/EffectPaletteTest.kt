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
    fun `the veil of fog is light grey by day and a darker grey by night, so that it does not glow`() {
        val day = EffectPalette.veil(0f, 1f)
        val night = EffectPalette.veil(1f, 1f)

        assertEquals(EffectMath.argb(255, 216, 222, 230), day)
        assertEquals(EffectMath.argb(255, 80, 86, 98), night)
        assertTrue(brightness(day) > 600)
        assertTrue(brightness(night) < brightness(day) * 0.45f)
        // But it is not black: it has to be seen over footage which is bright, too
        assertTrue(brightness(night) > 200)
        assertTrue(blue(night) > red(night), "a grey with a touch of blue")
    }

    @Test
    fun `a flash is white-blue and as bright as the alpha says`() {
        val flash = EffectPalette.flash(0.7f)

        assertEquals(EffectMath.argb(179, 236, 242, 255), flash)
        assertEquals(0, alpha(EffectPalette.flash(0f)))
    }

    // Lightning

    @Test
    fun `a flash is soft, 0 35 to 0 4 at the most, and fades to 40 percent towards the bottom of the screen`() {
        assertEquals(0.38f, EffectPalette.FLASH_MAX_ALPHA)
        assertTrue(EffectPalette.FLASH_MAX_ALPHA in 0.35f..0.40f)
        assertEquals(0.40f, EffectPalette.FLASH_BOTTOM_SHARE)
        assertEquals(1f, EffectPalette.flashHeightShare(0f), 0.0001f)
        assertEquals(0.4f, EffectPalette.flashHeightShare(1f), 0.0001f)
        assertEquals(0.7f, EffectPalette.flashHeightShare(0.5f), 0.0001f)
        // The ends of the gradient of the view: the colour of the flash, all of it and 40 percent of it
        assertEquals(255, alpha(EffectPalette.flash(1f)))
        assertEquals(102, alpha(EffectPalette.flash(EffectPalette.FLASH_BOTTOM_SHARE)))
        assertEquals(red(EffectPalette.flash(1f)), red(EffectPalette.flash(EffectPalette.FLASH_BOTTOM_SHARE)))
    }

    @Test
    fun `the brightness of a flash only goes down from the top of the screen to the bottom`() {
        var previous = 2f
        for (step in 0..20) {
            val share = EffectPalette.flashHeightShare(step / 20f)
            assertTrue(share <= previous, "at $step")
            previous = share
        }
        assertEquals(1f, EffectPalette.flashHeightShare(-3f), 0.0001f)
        assertEquals(0.4f, EffectPalette.flashHeightShare(3f), 0.0001f)
    }

    @Test
    fun `the opacity of a flash is the brightness of the sky, the gate and the rain of the storm`() {
        // At the top, with the heaviest rain, the brightest flash is the most there is
        assertEquals(0.38f, EffectPalette.flashAlpha(1f, 1f, 1f), 0.0001f)
        // With no rain it is 60 percent of that, and it follows the brightness and the gate linearly
        assertEquals(0.38f * 0.6f, EffectPalette.flashAlpha(1f, 1f, 0f), 0.0001f)
        assertEquals(0.38f * 0.5f * 0.92f, EffectPalette.flashAlpha(0.5f, 1f, 0.8f), 0.0001f)
        assertEquals(0f, EffectPalette.flashAlpha(0f, 1f, 1f))
        assertEquals(0f, EffectPalette.flashAlpha(1f, 0f, 1f))
    }

    @Test
    fun `a flash of a strike in a typical storm is much softer than it was, 0 35 at the top and 0 14 at the bottom at the most`() {
        // The weather of a storm is 0.8, and the brightest strike is 1. It was 0.64 over all of the screen
        val top = EffectPalette.flashAlpha(1f, 1f, 0.8f)
        val bottom = top * EffectPalette.flashHeightShare(1f)

        assertTrue(top in 0.3f..0.36f, "top $top")
        assertTrue(bottom in 0.12f..0.15f, "bottom $bottom")
        // Over all of the screen it is under half of the flat 0.64 it was
        assertTrue((top + bottom) / 2f < 0.64f / 2f, "on average ${(top + bottom) / 2f}")
    }

    @Test
    fun `the rain is lit up less by a flash, as the flash is softer`() {
        assertEquals(0.5f, EffectPalette.FLASH_RAIN_BOOST)
        assertTrue(EffectPalette.FLASH_RAIN_BOOST < 0.8f, "it was 0.8")
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
    fun `the rain is dimmed more than the tone, which is dark already`() {
        assertTrue(EffectPalette.rainDim(1f) < EffectPalette.tintDim(1f) + 0.2f)
        assertEquals(0.55f, EffectPalette.rainDim(1f), 0.0001f)
        assertEquals(0.6f, EffectPalette.tintDim(1f), 0.0001f)
    }

    @Test
    fun `snow is only a little dimmer by night, so that it is still clearly seen`() {
        assertEquals(0.8f, EffectPalette.snowDim(1f), 0.0001f)
        assertTrue(EffectPalette.snowDim(1f) > EffectPalette.rainDim(1f))
        assertTrue(EffectPalette.snowDim(1f) > EffectPalette.tintDim(1f))
    }

    // Snow

    @Test
    fun `the opacity of snow is its layer, a base and a share of the intensity, the fade and the night`() {
        // The layer comes first, then the intensity gives from 85 percent of it to all of it
        assertEquals(0.9f * 0.85f, EffectPalette.snowAlpha(0.9f, 0f, 1f, 0f), 0.0001f)
        assertEquals(0.9f, EffectPalette.snowAlpha(0.9f, 1f, 1f, 0f), 0.0001f)
        assertEquals(0.9f * 0.94f, EffectPalette.snowAlpha(0.9f, 0.6f, 1f, 0f), 0.0001f)
        // The fade of the layer in and out is a plain factor, and the night is a fifth less
        assertEquals(0f, EffectPalette.snowAlpha(0.9f, 1f, 0f, 0f))
        assertEquals(0.45f, EffectPalette.snowAlpha(0.9f, 1f, 0.5f, 0f), 0.0001f)
        assertEquals(0.9f * 0.8f, EffectPalette.snowAlpha(0.9f, 1f, 1f, 1f), 0.0001f)
    }

    @Test
    fun `the near flakes of a typical snowfall are nearly solid, by day and by night`() {
        val level = 0.6f
        val near = SnowProfiles.NEAR.alpha

        assertTrue(EffectPalette.snowAlpha(near, level, 1f, 0f) in 0.82f..0.9f)
        assertTrue(EffectPalette.snowAlpha(near, level, 1f, 1f) in 0.65f..0.72f)
        // And the middle ones are well seen
        assertTrue(EffectPalette.snowAlpha(SnowProfiles.MIDDLE.alpha, level, 1f, 1f) > 0.55f)
    }

    @Test
    fun `the fog is lit with all of its light by day and a bit over a third of it by night`() {
        val day = EffectPalette.fogLight(0f)
        val night = EffectPalette.fogLight(1f)

        assertEquals(EffectMath.argb(255, 255, 255, 255), day)
        assertEquals(EffectMath.argb(255, 96, 96, 96), night)
        assertTrue(red(night) < 255 * 0.4f)
        assertTrue(red(night) > 255 * 0.3f)
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
        assertTrue(EffectPalette.VEIL_MAX_ALPHA in 0.3f..0.5f)
        assertTrue(EffectPalette.FLASH_MAX_ALPHA in 0.3f..0.45f)
        assertTrue(EffectPalette.FOG_LAYER_ALPHA.all { it in 0.4f..0.8f })

        // Fog stacks up, so where all three layers are at their thickest it is dense but not solid
        fun together(level: Float) = 1f - EffectPalette.FOG_LAYER_ALPHA.fold(1f) { clear, layer -> clear * (1f - layer * level) }
        assertTrue(together(0.7f) < 0.85f, "three layers together, in typical fog: ${together(0.7f)}")
        assertTrue(together(1f) < 0.97f, "three layers together, at the most: ${together(1f)}")
    }

    // Fog

    @Test
    fun `fog is clearly seen at a typical intensity, a veil of a quarter to a third and peaks of 0 4 to 0 5`() {
        // The weather of fog and the testing override are 0.7, at the normal intensity of the settings
        val typical = 0.7f

        val veil = EffectPalette.veilAlpha(typical, 1f, 0f)
        assertTrue(veil in 0.25f..0.35f, "veil $veil")
        EffectPalette.FOG_LAYER_ALPHA.indices.forEach { layer ->
            val peak = EffectPalette.fogLayerAlpha(layer, typical, 1f, 0f)
            assertTrue(peak in 0.4f..0.5f, "layer $layer peaks at $peak")
        }
    }

    @Test
    fun `the veil and the layers of fog are the intensity, the fade and the night`() {
        assertEquals(0.45f, EffectPalette.veilAlpha(1f, 1f, 0f), 0.0001f)
        assertEquals(0.45f * 0.5f * 0.25f, EffectPalette.veilAlpha(0.5f, 0.25f, 0f), 0.0001f)
        assertEquals(0f, EffectPalette.veilAlpha(0.7f, 0f, 0f))
        assertEquals(0.45f * 0.85f, EffectPalette.veilAlpha(1f, 1f, 1f), 0.0001f)

        assertEquals(0.70f, EffectPalette.fogLayerAlpha(0, 1f, 1f, 0f), 0.0001f)
        assertEquals(0.58f * 0.5f * 0.5f, EffectPalette.fogLayerAlpha(2, 0.5f, 0.5f, 0f), 0.0001f)
        assertEquals(0.64f * 0.7f, EffectPalette.fogLayerAlpha(1, 1f, 1f, 1f), 0.0001f)
        assertEquals(0f, EffectPalette.fogLayerAlpha(1, 1f, 0f, 0f))
    }

    @Test
    fun `by night the veil is dimmed less than the clouds, so that the fog still reads as fog`() {
        assertEquals(0.85f, EffectPalette.veilDim(1f), 0.0001f)
        assertEquals(0.7f, EffectPalette.fogDim(1f), 0.0001f)
        assertTrue(EffectPalette.veilDim(1f) > EffectPalette.fogDim(1f))
        // A veil over footage of the night is still a good quarter
        assertTrue(EffectPalette.veilAlpha(0.7f, 1f, 1f) > 0.25f)
    }

    @Test
    fun `the veil is a little denser towards the bottom of the screen, like fog on the ground`() {
        assertEquals(0.7f, EffectPalette.veilHeightShare(0f), 0.0001f)
        assertEquals(1f, EffectPalette.veilHeightShare(1f), 0.0001f)
        assertEquals(0.85f, EffectPalette.veilHeightShare(0.5f), 0.0001f)
        assertEquals(0.7f, EffectPalette.VEIL_TOP_SHARE)
        // Only a little: the top is not clear air
        assertTrue(EffectPalette.VEIL_TOP_SHARE in 0.6f..0.85f)
        var previous = 0f
        for (step in 0..20) {
            val share = EffectPalette.veilHeightShare(step / 20f)
            assertTrue(share >= previous, "at $step")
            previous = share
        }
        assertEquals(0.7f, EffectPalette.veilHeightShare(-3f), 0.0001f)
        assertEquals(1f, EffectPalette.veilHeightShare(3f), 0.0001f)
    }
}
