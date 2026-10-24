package com.neilturner.aerialviews.ui.effects

import com.neilturner.aerialviews.models.enums.WeatherCondition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WeatherEffectsRefreshTest {
    private val alphas = listOf(0f, 0.001f, 0.1f, 0.5f, 1f)

    // When it draws

    @Test
    fun `effects which move are drawn on every frame while they can be seen`() {
        alphas.filter { it > 0f }.forEach { alpha ->
            assertEquals(
                EffectsRefresh.EVERY_FRAME,
                WeatherEffectsRefresh.plan(animating = true, onScreen = true, alpha = alpha),
                "at $alpha",
            )
        }
    }

    @Test
    fun `nothing is drawn, or scheduled, when there is nothing to move`() {
        alphas.forEach { alpha ->
            assertEquals(EffectsRefresh.STOPPED, WeatherEffectsRefresh.plan(animating = false, onScreen = true, alpha = alpha), "at $alpha")
        }
    }

    @Test
    fun `nothing is drawn, or scheduled, when the view is detached or hidden, such as in blackout`() {
        alphas.forEach { alpha ->
            assertEquals(EffectsRefresh.STOPPED, WeatherEffectsRefresh.plan(animating = true, onScreen = false, alpha = alpha), "at $alpha")
            assertEquals(
                EffectsRefresh.STOPPED,
                WeatherEffectsRefresh.plan(animating = false, onScreen = false, alpha = alpha),
                "at $alpha",
            )
        }
    }

    @Test
    fun `a view with an alpha of 0 stops completely and waits for the fade in`() {
        assertEquals(EffectsRefresh.WAITING_FOR_FADE_IN, WeatherEffectsRefresh.plan(animating = true, onScreen = true, alpha = 0f))
        assertEquals(EffectsRefresh.WAITING_FOR_FADE_IN, WeatherEffectsRefresh.plan(animating = true, onScreen = true, alpha = -0.5f))
        assertEquals(EffectsRefresh.WAITING_FOR_FADE_IN, WeatherEffectsRefresh.plan(animating = true, onScreen = true, alpha = Float.NaN))
    }

    @Test
    fun `the first bit of a fade in starts the frames again`() {
        assertEquals(EffectsRefresh.WAITING_FOR_FADE_IN, WeatherEffectsRefresh.plan(true, true, 0f))
        assertEquals(EffectsRefresh.EVERY_FRAME, WeatherEffectsRefresh.plan(true, true, Float.MIN_VALUE))
        assertEquals(EffectsRefresh.EVERY_FRAME, WeatherEffectsRefresh.plan(true, true, 0.0001f))
    }

    @Test
    fun `a view fading out and in runs on frames, stops, and starts again`() {
        val fade = listOf(0.6f, 0.3f, 0.05f, 0f, 0f, 0.02f, 0.4f, 0.6f)

        assertEquals(
            listOf(
                EffectsRefresh.EVERY_FRAME,
                EffectsRefresh.EVERY_FRAME,
                EffectsRefresh.EVERY_FRAME,
                EffectsRefresh.WAITING_FOR_FADE_IN,
                EffectsRefresh.WAITING_FOR_FADE_IN,
                EffectsRefresh.EVERY_FRAME,
                EffectsRefresh.EVERY_FRAME,
                EffectsRefresh.EVERY_FRAME,
            ),
            fade.map { WeatherEffectsRefresh.plan(true, true, it) },
        )
    }

    @Test
    fun `blackout stops the effects for good and showing them again brings them back`() {
        // Shown, in blackout (hidden, whatever the alpha is), shown again
        val plans = listOf(true, false, true).map { WeatherEffectsRefresh.plan(animating = true, onScreen = it, alpha = 1f) }

        assertEquals(listOf(EffectsRefresh.EVERY_FRAME, EffectsRefresh.STOPPED, EffectsRefresh.EVERY_FRAME), plans)
    }

    @Test
    fun `a view which is shown again while it is faded out waits for the fade in`() {
        assertEquals(EffectsRefresh.STOPPED, WeatherEffectsRefresh.plan(animating = true, onScreen = false, alpha = 0f))
        assertEquals(EffectsRefresh.WAITING_FOR_FADE_IN, WeatherEffectsRefresh.plan(animating = true, onScreen = true, alpha = 0f))
    }

    @Test
    fun `frames only ever come when something moves, the view can be seen and it is not faded out`() {
        for (animating in listOf(true, false)) {
            for (onScreen in listOf(true, false)) {
                for (alpha in alphas) {
                    val plan = WeatherEffectsRefresh.plan(animating, onScreen, alpha)

                    assertEquals(animating && onScreen && alpha > 0f, plan == EffectsRefresh.EVERY_FRAME, "$animating $onScreen $alpha")
                    assertEquals(
                        animating && onScreen && alpha <= 0f,
                        plan == EffectsRefresh.WAITING_FOR_FADE_IN,
                        "$animating $onScreen $alpha",
                    )
                }
            }
        }
    }

    // What the mix says

    @Test
    fun `with no weather the effects stop as soon as the gate has opened and nothing is there`() {
        val mix = EffectMix()
        val nothing = EffectTargets(shown = true)

        // The gate has to open, and then nothing has to be drawn
        assertEquals(EffectsRefresh.EVERY_FRAME, WeatherEffectsRefresh.plan(!mix.isSettled(nothing), true, 1f))
        repeat(150) { mix.step(1000f / 60f, nothing) }
        assertEquals(EffectsRefresh.STOPPED, WeatherEffectsRefresh.plan(!mix.isSettled(nothing), true, 1f))
    }

    @Test
    fun `rain keeps the frames going for as long as it rains`() {
        val mix = EffectMix()
        val rain = EffectTargets(rain = 0.6f, tint = 0.5f, shown = true)

        repeat(60 * 60) { mix.step(1000f / 60f, rain) }

        assertEquals(EffectsRefresh.EVERY_FRAME, WeatherEffectsRefresh.plan(!mix.isSettled(rain), true, 1f))
    }

    @Test
    fun `overcast weather stops the frames once the grey tone has faded in`() {
        val mix = EffectMix()
        val overcast = EffectTargets(tint = 0.6f, shown = true)

        repeat(60) { mix.step(1000f / 60f, overcast) }
        assertEquals(EffectsRefresh.EVERY_FRAME, WeatherEffectsRefresh.plan(!mix.isSettled(overcast), true, 1f))

        repeat(120) { mix.step(1000f / 60f, overcast) }
        assertEquals(EffectsRefresh.STOPPED, WeatherEffectsRefresh.plan(!mix.isSettled(overcast), true, 1f))
    }

    @Test
    fun `a clip with weather of its own stops the frames once the effects have faded out`() {
        val mix = EffectMix()
        val rain = EffectTargets(rain = 0.6f, shown = true)
        val skipped = rain.copy(shown = false)
        repeat(200) { mix.step(1000f / 60f, rain) }

        repeat(10) { mix.step(1000f / 60f, skipped) }
        assertEquals(EffectsRefresh.EVERY_FRAME, WeatherEffectsRefresh.plan(!mix.isSettled(skipped), true, 1f))

        repeat(60) { mix.step(1000f / 60f, skipped) }
        assertEquals(EffectsRefresh.STOPPED, WeatherEffectsRefresh.plan(!mix.isSettled(skipped), true, 1f))
    }

    @Test
    fun `closing the gate for a blackout settles the picture at once`() {
        val mix = EffectMix()
        val rain = EffectTargets(rain = 0.6f, shown = true)
        repeat(200) { mix.step(1000f / 60f, rain) }
        assertFalse(mix.isSettled(rain.copy(shown = false)))

        mix.closeGate()
        mix.step(0f, rain.copy(shown = false))

        assertEquals(0f, mix.gate)
        assertTrue(mix.isSettled(rain.copy(shown = false)))
        // And the layers are where the weather is going, for the next clip
        assertEquals(1f, mix.rain.presence)
    }

    // The rule of the setting "Skip on weather clips"

    @Test
    fun `a clip with any weather tag is skipped when the setting is on`() {
        WeatherCondition.entries.forEach { condition ->
            assertTrue(WeatherEffectClipRule.shouldSkip(true, setOf(condition)), "$condition")
        }
        assertTrue(WeatherEffectClipRule.shouldSkip(true, setOf(WeatherCondition.CLEAR, WeatherCondition.RAIN)))
        assertTrue(WeatherEffectClipRule.shouldSkip(true, WeatherCondition.entries.toSet()))
    }

    @Test
    fun `a clip with no weather tag, and a photo, is never skipped`() {
        assertFalse(WeatherEffectClipRule.shouldSkip(true, emptySet()))
        assertFalse(WeatherEffectClipRule.shouldSkip(false, emptySet()))
    }

    @Test
    fun `no clip is skipped when the setting is off`() {
        WeatherCondition.entries.forEach { condition ->
            assertFalse(WeatherEffectClipRule.shouldSkip(false, setOf(condition)), "$condition")
        }
        assertFalse(WeatherEffectClipRule.shouldSkip(false, WeatherCondition.entries.toSet()))
    }
}
