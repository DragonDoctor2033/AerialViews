package com.neilturner.aerialviews.ui.effects

import com.neilturner.aerialviews.models.enums.WeatherEffectKind
import com.neilturner.aerialviews.models.enums.WeatherEffectsIntensity
import com.neilturner.aerialviews.services.weather.WeatherEffectState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EffectTargetsTest {
    private fun targets(
        kind: WeatherEffectKind,
        intensity: Float,
        setting: WeatherEffectsIntensity = WeatherEffectsIntensity.NORMAL,
        night: Boolean = false,
        shown: Boolean = true,
        windSpeed: Float = 0f,
        windFrom: Float = 0f,
    ) = EffectTargets.from(
        WeatherEffectState(kind, intensity, windSpeed, windFrom, night),
        setting,
        shown,
    )

    @Test
    fun `no weather wants nothing`() {
        val none = targets(WeatherEffectKind.NONE, 0f)

        assertEquals(EffectTargets(shown = true), none)
        assertEquals(0f, none.rain)
        assertEquals(0f, none.tint)
    }

    @Test
    fun `rain is rain with a grey sky`() {
        val rain = targets(WeatherEffectKind.RAIN, 0.6f)

        assertEquals(0.6f, rain.rain)
        assertEquals(0f, rain.snow)
        assertEquals(0f, rain.fog)
        assertFalse(rain.lightning)
        assertEquals(0.30f + 0.45f * 0.6f, rain.tint, 0.0001f)
    }

    @Test
    fun `a storm is rain with lightning and the darkest sky`() {
        val storm = targets(WeatherEffectKind.STORM, 0.8f)

        assertEquals(0.8f, storm.rain)
        assertTrue(storm.lightning)
        assertEquals(0.55f + 0.40f * 0.8f, storm.tint, 0.0001f)
        assertTrue(storm.tint > targets(WeatherEffectKind.RAIN, 0.8f).tint)
    }

    @Test
    fun `snow is snow with a light grey sky`() {
        val snow = targets(WeatherEffectKind.SNOW, 0.5f)

        assertEquals(0.5f, snow.snow)
        assertEquals(0f, snow.rain)
        assertEquals(0.15f + 0.30f * 0.5f, snow.tint, 0.0001f)
        assertTrue(snow.tint < targets(WeatherEffectKind.RAIN, 0.5f).tint)
    }

    @Test
    fun `fog is fog with a little grey`() {
        val fog = targets(WeatherEffectKind.FOG, 0.7f)

        assertEquals(0.7f, fog.fog)
        assertEquals(0.25f * 0.7f, fog.tint, 0.0001f)
        assertEquals(0f, fog.rain)
    }

    @Test
    fun `overcast is a grey tone and nothing else`() {
        val overcast = targets(WeatherEffectKind.OVERCAST, 0.4f)

        assertEquals(0.4f, overcast.tint)
        assertEquals(0f, overcast.rain)
        assertEquals(0f, overcast.snow)
        assertEquals(0f, overcast.fog)
        assertFalse(overcast.lightning)
    }

    @Test
    fun `only a storm has lightning`() {
        WeatherEffectKind.entries.forEach { kind ->
            assertEquals(kind == WeatherEffectKind.STORM, targets(kind, 0.7f).lightning, "$kind")
        }
    }

    @Test
    fun `the tone never goes past 1`() {
        WeatherEffectKind.entries.forEach { kind ->
            WeatherEffectsIntensity.entries.forEach { setting ->
                listOf(0f, 0.3f, 0.9f, 1f).forEach { intensity ->
                    val tint = targets(kind, intensity, setting).tint
                    assertTrue(tint in 0f..1f, "$kind $setting $intensity: $tint")
                }
            }
        }
    }

    @Test
    fun `the setting scales the intensity of the weather`() {
        assertEquals(0.3f, targets(WeatherEffectKind.RAIN, 0.5f, WeatherEffectsIntensity.SUBTLE).rain, 0.0001f)
        assertEquals(0.5f, targets(WeatherEffectKind.RAIN, 0.5f, WeatherEffectsIntensity.NORMAL).rain, 0.0001f)
        assertEquals(0.7f, targets(WeatherEffectKind.RAIN, 0.5f, WeatherEffectsIntensity.STRONG).rain, 0.0001f)
    }

    @Test
    fun `a stronger setting is never less than a weaker one, for every kind`() {
        listOf(WeatherEffectKind.RAIN, WeatherEffectKind.SNOW, WeatherEffectKind.FOG, WeatherEffectKind.STORM, WeatherEffectKind.OVERCAST)
            .forEach { kind ->
                listOf(0.1f, 0.4f, 0.8f).forEach { intensity ->
                    val subtle = targets(kind, intensity, WeatherEffectsIntensity.SUBTLE)
                    val normal = targets(kind, intensity, WeatherEffectsIntensity.NORMAL)
                    val strong = targets(kind, intensity, WeatherEffectsIntensity.STRONG)

                    assertTrue(subtle.tint <= normal.tint && normal.tint <= strong.tint, "$kind $intensity")
                    assertTrue(subtle.rain <= normal.rain && normal.rain <= strong.rain, "$kind $intensity")
                    assertTrue(subtle.snow <= normal.snow && normal.snow <= strong.snow, "$kind $intensity")
                    assertTrue(subtle.fog <= normal.fog && normal.fog <= strong.fog, "$kind $intensity")
                }
            }
    }

    @Test
    fun `the strongest setting does not go past the heaviest rain`() {
        assertEquals(1f, targets(WeatherEffectKind.RAIN, 0.9f, WeatherEffectsIntensity.STRONG).rain)
        assertEquals(1f, targets(WeatherEffectKind.SNOW, 1f, WeatherEffectsIntensity.STRONG).snow)
    }

    @Test
    fun `light rain stays lighter than heavy rain at every setting`() {
        WeatherEffectsIntensity.entries.forEach { setting ->
            val light = targets(WeatherEffectKind.RAIN, 0.2f, setting).rain
            val heavy = targets(WeatherEffectKind.RAIN, 0.7f, setting).rain

            assertTrue(light < heavy, "$setting")
        }
    }

    @Test
    fun `weather with an intensity of 0 wants nothing, whatever it is`() {
        WeatherEffectKind.entries.forEach { kind ->
            val targets = targets(kind, 0f)

            assertEquals(EffectTargets(shown = true), targets, "$kind")
        }
    }

    @Test
    fun `night and the wind come along`() {
        val state = targets(WeatherEffectKind.RAIN, 0.5f, night = true, windSpeed = 12f, windFrom = 270f)

        assertTrue(state.night)
        assertEquals(1f, state.wind, 0.0001f)
        assertFalse(targets(WeatherEffectKind.RAIN, 0.5f, night = false).night)
    }

    @Test
    fun `night comes along when there is no weather too`() {
        assertTrue(targets(WeatherEffectKind.NONE, 0f, night = true).night)
    }

    @Test
    fun `whether the effects are to be seen comes along`() {
        assertTrue(targets(WeatherEffectKind.RAIN, 0.5f, shown = true).shown)
        assertFalse(targets(WeatherEffectKind.RAIN, 0.5f, shown = false).shown)
        assertFalse(targets(WeatherEffectKind.NONE, 0f, shown = false).shown)
    }
}
