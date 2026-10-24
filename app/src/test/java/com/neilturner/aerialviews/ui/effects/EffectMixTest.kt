package com.neilturner.aerialviews.ui.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EffectMixTest {
    private val mix = EffectMix()

    private val rainy = EffectTargets(rain = 0.6f, tint = 0.5f, shown = true)
    private val snowy = EffectTargets(snow = 0.5f, tint = 0.3f, shown = true)
    private val gloomy = EffectTargets(tint = 0.5f, shown = true)

    // Steps the mix by a number of whole frames which add up to the time, 60 a second unless said otherwise
    private fun run(
        millis: Float,
        targets: EffectTargets,
        frameMillis: Float = 1000f / 60f,
        into: EffectMix = mix,
    ) {
        repeat((millis / frameMillis + 0.5f).toInt()) { into.step(frameMillis, targets) }
    }

    // A new mix

    @Test
    fun `a new mix has nothing in it`() {
        assertEquals(0f, mix.gate)
        assertEquals(0f, mix.rain.presence)
        assertEquals(0f, mix.snow.presence)
        assertEquals(0f, mix.fog.presence)
        assertEquals(0f, mix.tint.presence)
        assertEquals(0f, mix.night)
        assertEquals(0f, mix.wind)
        assertEquals(0f, mix.gateOpacity)
    }

    @Test
    fun `a mix whose gate is closed is final, and so is what it is going to show`() {
        assertTrue(mix.isSettled(EffectTargets(shown = false)))
        assertTrue(mix.isSettled(EffectTargets(rain = 0.6f, lightning = true, shown = false)))
    }

    // The gate

    @Test
    fun `the gate opens in two seconds`() {
        run(1000f, rainy)
        assertEquals(0.5f, mix.gate, 0.02f)
        assertEquals(0.5f, mix.gateOpacity, 0.04f)

        run(1100f, rainy)
        assertEquals(1f, mix.gate)
        assertEquals(1f, mix.gateOpacity)
    }

    @Test
    fun `the gate closes in half a second`() {
        run(2500f, rainy)
        assertEquals(1f, mix.gate)

        run(250f, rainy.copy(shown = false))
        assertEquals(0.5f, mix.gate, 0.04f)
        run(300f, rainy.copy(shown = false))
        assertEquals(0f, mix.gate)
    }

    @Test
    fun `the gate opens smoothly and never jumps`() {
        var previous = 0f
        repeat(150) {
            mix.step(1000f / 60f, rainy)
            assertTrue(mix.gateOpacity >= previous)
            assertTrue(mix.gateOpacity - previous < 0.04f, "jump of ${mix.gateOpacity - previous}")
            previous = mix.gateOpacity
        }
    }

    // Layers

    @Test
    fun `while the gate is closed the layers are where they are going at once`() {
        mix.step(0f, rainy.copy(shown = false))

        assertEquals(1f, mix.rain.presence)
        assertEquals(0.6f, mix.rain.level)
        assertEquals(1f, mix.tint.presence)
        assertEquals(0.5f, mix.tint.level)
        assertEquals(0f, mix.gate)
    }

    @Test
    fun `when the gate opens only the gate fades, the layers are there already`() {
        mix.step(0f, rainy.copy(shown = false))

        run(500f, rainy)

        assertEquals(1f, mix.rain.presence)
        assertEquals(1f, mix.tint.presence)
        assertEquals(0.25f, mix.gate, 0.02f)
    }

    @Test
    fun `a layer which was not there is faded in at its level from the start`() {
        run(2500f, gloomy)

        run(100f, rainy)

        // The rain is on its way in, but at the intensity of the weather, not growing from nothing
        assertTrue(mix.rain.presence in 0.01f..0.1f)
        assertEquals(0.6f, mix.rain.level)
    }

    @Test
    fun `rain changing into snow is a crossfade in two seconds`() {
        run(2500f, rainy)
        assertEquals(1f, mix.rain.presence)

        run(1000f, snowy)
        assertEquals(0.5f, mix.rain.presence, 0.03f)
        assertEquals(0.5f, mix.snow.presence, 0.03f)

        run(1100f, snowy)
        assertEquals(0f, mix.rain.presence)
        assertEquals(1f, mix.snow.presence)
    }

    @Test
    fun `in a crossfade the old weather and the new weather always add up to the whole picture`() {
        run(2500f, rainy)

        repeat(120) {
            mix.step(1000f / 60f, snowy)
            val sum = mix.rain.opacity + mix.snow.opacity
            assertEquals(1f, sum, 0.03f, "frame $it")
        }
    }

    @Test
    fun `a layer which stays fades its level to the new intensity`() {
        run(2500f, rainy)

        run(500f, rainy.copy(rain = 0.1f))
        // 0.5 of intensity takes a second, so a quarter of that in half a second
        assertEquals(0.6f - 0.25f, mix.rain.level, 0.02f)
        assertEquals(1f, mix.rain.presence)

        run(1000f, rainy.copy(rain = 0.1f))
        assertEquals(0.1f, mix.rain.level)
    }

    @Test
    fun `a layer which fades out keeps its level while it goes`() {
        run(2500f, rainy)

        run(500f, gloomy)

        assertEquals(0.6f, mix.rain.level)
        assertTrue(mix.rain.presence in 0.01f..0.99f)
    }

    @Test
    fun `the opacity of a layer starts and ends softly`() {
        val fade = EffectFade()
        fade.step(16f, 0.5f)
        // 16 ms of 2000 ms is under 1% of presence, and under 0.01% of opacity
        assertTrue(fade.presence < 0.01f)
        assertTrue(fade.opacity < 0.0003f)
    }

    @Test
    fun `night and the wind come on slowly, in three seconds`() {
        val targets = rainy.copy(night = true, wind = 1f)

        run(1500f, targets)
        assertEquals(0.5f, mix.night, 0.03f)
        assertEquals(0.5f, mix.wind, 0.03f)

        run(1600f, targets)
        assertEquals(1f, mix.night)
        assertEquals(1f, mix.wind)
    }

    @Test
    fun `the wind can turn all the way round`() {
        run(3500f, rainy.copy(wind = 1f))

        run(6100f, rainy.copy(wind = -1f))

        assertEquals(-1f, mix.wind)
    }

    // At 60 and 30 frames a second

    @Test
    fun `the mix is the same after the same time at 60 and 30 frames a second`() {
        val first = EffectMix()
        val second = EffectMix()
        first.step(0f, rainy.copy(shown = false))
        second.step(0f, rainy.copy(shown = false))

        run(1500f, snowy, frameMillis = 1000f / 60f, into = first)
        run(1500f, snowy, frameMillis = 1000f / 30f, into = second)

        assertEquals(first.gate, second.gate, 0.002f)
        assertEquals(first.rain.presence, second.rain.presence, 0.002f)
        assertEquals(first.snow.presence, second.snow.presence, 0.002f)
        assertEquals(first.tint.level, second.tint.level, 0.002f)
    }

    // Settled

    @Test
    fun `rain is never final while it is shown`() {
        run(10_000f, rainy)

        assertFalse(mix.isSettled(rainy))
    }

    @Test
    fun `snow and fog are never final while they are shown`() {
        run(10_000f, snowy)
        assertFalse(mix.isSettled(snowy))

        val foggy = EffectTargets(fog = 0.7f, shown = true)
        run(10_000f, foggy)
        assertFalse(mix.isSettled(foggy))
    }

    @Test
    fun `lightning is never final`() {
        val storm = rainy.copy(lightning = true)
        run(10_000f, storm)

        assertFalse(mix.isSettled(storm))
    }

    @Test
    fun `a grey tone is final once it has faded in`() {
        assertFalse(mix.isSettled(gloomy))

        run(1000f, gloomy)
        assertFalse(mix.isSettled(gloomy))

        run(1500f, gloomy)
        assertTrue(mix.isSettled(gloomy))
    }

    @Test
    fun `a grey tone is final once it has faded in, also by night`() {
        val night = gloomy.copy(night = true)

        run(2000f, night)
        assertFalse(mix.isSettled(night))

        run(1500f, night)
        assertTrue(mix.isSettled(night))
    }

    @Test
    fun `a still picture is not final while it is on its way`() {
        run(3000f, gloomy)

        assertFalse(mix.isSettled(gloomy.copy(tint = 0.9f)))
        assertFalse(mix.isSettled(gloomy.copy(shown = false)))
    }

    @Test
    fun `no weather is final once the gate has opened and nothing is there`() {
        val clear = EffectTargets(shown = true)
        assertFalse(mix.isSettled(clear))

        run(2500f, clear)
        assertTrue(mix.isSettled(clear))
    }

    @Test
    fun `rain which is taken away is final again once it has faded out`() {
        run(3000f, rainy)

        run(1000f, EffectTargets(shown = true))
        assertFalse(mix.isSettled(EffectTargets(shown = true)))

        run(1500f, EffectTargets(shown = true))
        assertTrue(mix.isSettled(EffectTargets(shown = true)))
    }

    @Test
    fun `the picture is final once the gate has closed, whatever else is going on`() {
        run(3000f, rainy)
        val closed = rainy.copy(shown = false)
        assertFalse(mix.isSettled(closed))

        run(600f, closed)

        assertEquals(0f, mix.gate)
        assertTrue(mix.isSettled(closed))
    }

    @Test
    fun `after the gate has closed the old weather is gone, and the next weather is there when it opens`() {
        run(3000f, rainy)
        run(600f, rainy.copy(shown = false))

        // The weather changed while nobody could see
        mix.step(16f, snowy.copy(shown = false))
        assertEquals(0f, mix.rain.presence)
        assertEquals(1f, mix.snow.presence)

        run(500f, snowy)
        assertEquals(0f, mix.rain.presence)
        assertEquals(1f, mix.snow.presence)
    }

    @Test
    fun `a clip changing is the gate closing and opening again`() {
        run(3000f, rainy)
        assertEquals(1f, mix.gate)

        // The next clip loads
        run(600f, rainy.copy(shown = false))
        assertEquals(0f, mix.gate)

        // And is shown
        run(1000f, rainy)
        assertEquals(0.5f, mix.gate, 0.03f)
        run(1100f, rainy)
        assertEquals(1f, mix.gate)
    }

    @Test
    fun `stepping by no time with the gate closed brings the layers to the targets`() {
        mix.step(0f, snowy.copy(shown = false, night = true, wind = 0.5f))

        assertEquals(1f, mix.snow.presence)
        assertEquals(1f, mix.night)
        assertEquals(0.5f, mix.wind)
    }

    @Test
    fun `stepping by no time with the gate open changes nothing`() {
        run(3000f, rainy)

        mix.step(0f, snowy)

        assertEquals(1f, mix.rain.presence)
        assertEquals(0f, mix.snow.presence)
    }

    // A layer on its own

    @Test
    fun `a fade is at its target when it has got there`() {
        val fade = EffectFade()
        assertTrue(fade.isAt(0f))
        assertFalse(fade.isAt(0.5f))

        repeat(200) { fade.step(16f, 0.5f) }
        assertTrue(fade.isAt(0.5f))
        assertFalse(fade.isAt(0.6f))
        assertFalse(fade.isAt(0f))
    }

    @Test
    fun `a fade snaps to its target`() {
        val fade = EffectFade()

        fade.snapTo(0.7f)
        assertEquals(1f, fade.presence)
        assertEquals(0.7f, fade.level)

        fade.snapTo(0f)
        assertEquals(0f, fade.presence)
        assertEquals(0f, fade.level)

        fade.snapTo(-1f)
        assertEquals(0f, fade.presence)
        assertEquals(0f, fade.level)
    }
}
