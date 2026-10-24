package com.neilturner.aerialviews.ui.effects

import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherEffectKind
import com.neilturner.aerialviews.models.enums.WeatherEffectsFrameRate
import com.neilturner.aerialviews.models.enums.WeatherEffectsIntensity
import com.neilturner.aerialviews.services.weather.WeatherEffectsFeed
import com.neilturner.aerialviews.services.weather.WeatherSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class WeatherEffectsControllerTest {
    private class FakeSurface : EffectsSurface {
        var framesPerSecond = 0
        val targets = mutableListOf<EffectTargets>()
        var blackout: Boolean? = null
        var disabled = false
        var released = false

        override fun setFrameRate(fps: Int) {
            framesPerSecond = fps
        }

        override fun setTargets(targets: EffectTargets) {
            this.targets.add(targets)
        }

        override fun setBlackout(blackout: Boolean) {
            this.blackout = blackout
        }

        override fun disable() {
            disabled = true
        }

        override fun release() {
            released = true
        }

        val last: EffectTargets get() = targets.last()
    }

    private val surface = FakeSurface()
    private var now = 1_700_000_000_000L
    private val feed = WeatherEffectsFeed(clock = { now })
    private var override: WeatherCondition? = null
    private var hour = 12

    private val rainy = WeatherSnapshot(conditionId = 502, icon = "10d", windSpeedMetersPerSecond = 0.0)

    private fun settings(
        enabled: Boolean = true,
        intensity: WeatherEffectsIntensity = WeatherEffectsIntensity.NORMAL,
        skip: Boolean = true,
        frameRate: WeatherEffectsFrameRate = WeatherEffectsFrameRate.FPS_60,
    ) = WeatherEffectsSettings(enabled, intensity, skip, frameRate)

    private fun controller(
        scope: CoroutineScope,
        settings: WeatherEffectsSettings = settings(),
    ) = WeatherEffectsController(
        surface = surface,
        scope = scope,
        settings = settings,
        feed = feed,
        overrideSource = { override },
        clock = { now },
        hourSource = { hour },
    )

    // The controller is started, a clip is loaded and shown
    private fun WeatherEffectsController.playClip(tags: Set<WeatherCondition> = emptySet()) {
        onMediaChanged(tags)
        onMediaShown()
    }

    // The settings

    @Test
    fun `when the effects are off nothing is done at all`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope, settings(enabled = false))

            controller.start()
            feed.publish(rainy)
            controller.playClip()
            controller.setBlackout(true)
            controller.stop()

            assertTrue(surface.disabled)
            assertTrue(surface.targets.isEmpty())
            assertEquals(0, surface.framesPerSecond)
            assertNull(surface.blackout)
            assertFalse(surface.released)
            assertFalse(controller.needsWeather)
        }

    @Test
    fun `when the effects are on the weather has to be looked up for them`() =
        runTest(UnconfinedTestDispatcher()) {
            assertTrue(controller(backgroundScope).needsWeather)
        }

    @Test
    fun `the frame rate of the settings is handed to the view`() =
        runTest(UnconfinedTestDispatcher()) {
            controller(backgroundScope, settings(frameRate = WeatherEffectsFrameRate.FPS_30)).start()
            assertEquals(30, surface.framesPerSecond)
        }

    @Test
    fun `the default frame rate is 60`() =
        runTest(UnconfinedTestDispatcher()) {
            controller(backgroundScope).start()
            assertEquals(60, surface.framesPerSecond)
        }

    @Test
    fun `the view is not disabled when the effects are on`() =
        runTest(UnconfinedTestDispatcher()) {
            controller(backgroundScope).start()
            assertFalse(surface.disabled)
        }

    // When it can be seen

    @Test
    fun `at the start nothing can be seen and there is no weather`() =
        runTest(UnconfinedTestDispatcher()) {
            controller(backgroundScope).start()

            assertEquals(EffectTargets(shown = false), surface.last)
        }

    @Test
    fun `the effects wait for the clip to be shown, and then fade in with it`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            feed.publish(rainy)
            controller.start()

            controller.onMediaChanged(emptySet())
            assertFalse(surface.last.shown)
            assertTrue(surface.last.rain > 0f)

            controller.onMediaShown()
            assertTrue(surface.last.shown)
        }

    @Test
    fun `the effects go when the next clip loads, and come back when it is shown`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            feed.publish(rainy)
            controller.start()
            controller.playClip()
            assertTrue(surface.last.shown)

            controller.onMediaChanged(emptySet())
            assertFalse(surface.last.shown)
            controller.onMediaShown()
            assertTrue(surface.last.shown)
        }

    @Test
    fun `a photo has no weather tags, so the effects are over photos too`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            feed.publish(rainy)
            controller.start()

            controller.playClip(emptySet())

            assertTrue(surface.last.shown)
        }

    // Skip on weather clips

    @Test
    fun `a clip tagged with weather has no effects`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            feed.publish(rainy)
            controller.start()

            controller.playClip(setOf(WeatherCondition.RAIN))

            assertFalse(surface.last.shown)
            // The weather itself is still known, ready for the next clip
            assertTrue(surface.last.rain > 0f)
        }

    @Test
    fun `any weather tag counts, also one that differs from the weather outside`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            feed.publish(rainy)
            controller.start()

            WeatherCondition.entries.forEach { tag ->
                controller.playClip(setOf(tag))
                assertFalse(surface.last.shown, "$tag")
            }
        }

    @Test
    fun `the effects come back on the next clip without a weather tag`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            feed.publish(rainy)
            controller.start()
            controller.playClip(setOf(WeatherCondition.SNOW))
            assertFalse(surface.last.shown)

            controller.playClip(emptySet())

            assertTrue(surface.last.shown)
        }

    @Test
    fun `with the setting off the effects are over weather clips too`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope, settings(skip = false))
            feed.publish(rainy)
            controller.start()

            controller.playClip(setOf(WeatherCondition.RAIN, WeatherCondition.CLOUDY))

            assertTrue(surface.last.shown)
        }

    @Test
    fun `a clip with weather of its own, still showing, keeps the effects away when the weather changes`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            controller.start()
            controller.playClip(setOf(WeatherCondition.RAIN))

            feed.publish(rainy)

            assertFalse(surface.last.shown)
            assertTrue(surface.last.rain > 0f)
        }

    // The weather

    @Test
    fun `weather which comes in later is faded to while the clip plays`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            controller.start()
            controller.playClip()
            assertEquals(0f, surface.last.rain)
            assertTrue(surface.last.shown)

            feed.publish(rainy)

            assertTrue(surface.last.rain > 0.5f)
            assertTrue(surface.last.shown)
        }

    @Test
    fun `weather which is already known is there from the start`() =
        runTest(UnconfinedTestDispatcher()) {
            feed.publish(WeatherSnapshot(conditionId = 601, icon = "13d"))

            controller(backgroundScope).start()

            assertTrue(surface.last.snow > 0f)
            assertEquals(0f, surface.last.rain)
        }

    @Test
    fun `each kind of weather asks for its own effects`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            controller.start()
            controller.playClip()

            feed.publish(WeatherSnapshot(conditionId = 500))
            assertTrue(surface.last.rain > 0f && surface.last.snow == 0f && !surface.last.lightning)

            feed.publish(WeatherSnapshot(conditionId = 601))
            assertTrue(surface.last.snow > 0f && surface.last.rain == 0f)

            feed.publish(WeatherSnapshot(conditionId = 741))
            assertTrue(surface.last.fog > 0f && surface.last.snow == 0f)

            feed.publish(WeatherSnapshot(conditionId = 211))
            assertTrue(surface.last.rain > 0f && surface.last.lightning)

            feed.publish(WeatherSnapshot(conditionId = 804))
            assertTrue(surface.last.tint > 0f && surface.last.rain == 0f && surface.last.fog == 0f)

            feed.publish(WeatherSnapshot(conditionId = 800))
            assertEquals(EffectTargets(shown = true), surface.last)
        }

    @Test
    fun `the wind and the night come along`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            controller.start()
            controller.playClip()

            feed.publish(WeatherSnapshot(conditionId = 500, icon = "10n", windSpeedMetersPerSecond = 12.0, windFromDegrees = 270))

            assertTrue(surface.last.night)
            assertEquals(1f, surface.last.wind, 0.001f)
        }

    @Test
    fun `weather that is too old is not used, as the updates have been failing`() =
        runTest(UnconfinedTestDispatcher()) {
            feed.publish(rainy)
            val controller = controller(backgroundScope)
            controller.start()
            assertTrue(surface.last.rain > 0f)

            // The next time something is decided, the weather is four hours old
            now += 4 * 60 * 60 * 1000L
            controller.playClip()

            assertEquals(0f, surface.last.rain)
        }

    // The intensity setting

    @Test
    fun `the intensity of the settings scales the weather`() =
        runTest(UnconfinedTestDispatcher()) {
            feed.publish(WeatherSnapshot(conditionId = 501))
            val levels =
                WeatherEffectsIntensity.entries.map { setting ->
                    FakeSurface().let { fake ->
                        WeatherEffectsController(
                            fake,
                            backgroundScope,
                            settings(intensity = setting),
                            feed,
                            { null },
                            { now },
                            { hour },
                        ).start()
                        fake.last.rain
                    }
                }

            assertEquals(listOf(0.3f, 0.5f, 0.7f), levels.map { it.rounded() })
            assertTrue(levels[0] < levels[1] && levels[1] < levels[2])
        }

    // To two decimals
    private fun Float.rounded() = Math.round(this * 100) / 100f

    // The testing override

    @Test
    fun `the testing override is a typical weather of that kind, whatever the real weather is`() =
        runTest(UnconfinedTestDispatcher()) {
            feed.publish(WeatherSnapshot(conditionId = 800, icon = "01d"))
            val controller = controller(backgroundScope)
            controller.start()
            controller.playClip()
            assertEquals(0f, surface.last.rain)

            override = WeatherCondition.RAIN
            controller.playClip()

            assertEquals(0.6f, surface.last.rain, 0.001f)
        }

    @Test
    fun `the override works without any weather, such as with no network`() =
        runTest(UnconfinedTestDispatcher()) {
            override = WeatherCondition.SNOW
            val controller = controller(backgroundScope)
            controller.start()
            controller.playClip()

            assertEquals(0.6f, surface.last.snow, 0.001f)
            assertTrue(surface.last.shown)
        }

    @Test
    fun `every override gives its effects`() =
        runTest(UnconfinedTestDispatcher()) {
            val kinds = mutableMapOf<WeatherCondition, EffectTargets>()
            WeatherCondition.entries.forEach { condition ->
                override = condition
                val controller = controller(backgroundScope)
                controller.start()
                controller.playClip()
                kinds[condition] = surface.last
            }

            assertEquals(EffectTargets(shown = true), kinds.getValue(WeatherCondition.CLEAR).copy(night = false, wind = 0f))
            assertTrue(kinds.getValue(WeatherCondition.CLOUDY).tint > 0f)
            assertTrue(kinds.getValue(WeatherCondition.RAIN).rain > 0f)
            assertTrue(kinds.getValue(WeatherCondition.SNOW).snow > 0f)
            assertTrue(kinds.getValue(WeatherCondition.STORM).lightning)
            assertTrue(kinds.getValue(WeatherCondition.FOG).fog > 0f)
        }

    @Test
    fun `the override is for the effects what it is for the playlist, and a clip with weather still has none`() =
        runTest(UnconfinedTestDispatcher()) {
            override = WeatherCondition.RAIN
            val controller = controller(backgroundScope)
            controller.start()

            controller.playClip(setOf(WeatherCondition.RAIN))

            assertFalse(surface.last.shown)
        }

    @Test
    fun `with the override the time of day is the real one when the weather is known, else the hour`() =
        runTest(UnconfinedTestDispatcher()) {
            override = WeatherCondition.RAIN
            hour = 23
            val controller = controller(backgroundScope)
            controller.start()
            assertTrue(surface.last.night)

            feed.publish(WeatherSnapshot(conditionId = 800, icon = "01d"))
            assertFalse(surface.last.night)
        }

    @Test
    fun `switching the override off goes back to the real weather`() =
        runTest(UnconfinedTestDispatcher()) {
            feed.publish(WeatherSnapshot(conditionId = 601))
            override = WeatherCondition.RAIN
            val controller = controller(backgroundScope)
            controller.start()
            controller.playClip()
            assertTrue(surface.last.rain > 0f)

            override = null
            controller.playClip()

            assertTrue(surface.last.snow > 0f)
            assertEquals(0f, surface.last.rain)
        }

    // Blackout

    @Test
    fun `blackout hides the view and the effects, and the next clip brings them back`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            feed.publish(rainy)
            controller.start()
            controller.playClip()
            assertTrue(surface.last.shown)

            controller.setBlackout(true)
            assertEquals(true, surface.blackout)
            assertFalse(surface.last.shown)

            controller.setBlackout(false)
            assertEquals(false, surface.blackout)
            // The clip which was on screen is gone, the next one has to be shown
            assertFalse(surface.last.shown)

            controller.playClip()
            assertTrue(surface.last.shown)
        }

    @Test
    fun `a clip which is shown during blackout is not shown`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            feed.publish(rainy)
            controller.start()
            controller.setBlackout(true)

            controller.playClip()

            assertFalse(surface.last.shown)
        }

    // The end

    @Test
    fun `stopping lets the view go and ignores weather and clips after that`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            controller.start()
            controller.playClip()
            val seen = surface.targets.size

            controller.stop()
            feed.publish(rainy)
            controller.playClip()
            controller.setBlackout(true)

            assertTrue(surface.released)
            assertEquals(seen, surface.targets.size)
            assertNull(surface.blackout)
        }

    @Test
    fun `starting twice starts once`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)
            controller.start()
            val seen = surface.targets.size

            controller.start()

            assertEquals(seen, surface.targets.size)
        }

    @Test
    fun `before it is started the calls of the screen do nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = controller(backgroundScope)

            controller.onMediaChanged(emptySet())
            controller.onMediaShown()
            controller.setBlackout(true)
            controller.stop()

            assertTrue(surface.targets.isEmpty())
            assertFalse(surface.released)
        }

    @Test
    fun `the state is the kind and the intensity of the weather`() =
        runTest(UnconfinedTestDispatcher()) {
            feed.publish(WeatherSnapshot(conditionId = 601, icon = "13n"))

            val state = controller(backgroundScope).currentState()

            assertEquals(WeatherEffectKind.SNOW, state.kind)
            assertTrue(state.isNight)
        }
}
