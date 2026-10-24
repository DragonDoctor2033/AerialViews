package com.neilturner.aerialviews.ui.effects

import com.neilturner.aerialviews.testutils.ResourceXml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File

/**
 * Checks that the weather effects are where they should be in the layout of the screen, and that the
 * screen controller calls them where it knows what is playing. The controller needs a device to run,
 * so its source is read, the way the settings tests read the xml.
 */
internal class WeatherEffectsWiringTest {
    private val screenController =
        listOf(
            "src/main/java/com/neilturner/aerialviews/ui/core/ScreenController.kt",
            "app/src/main/java/com/neilturner/aerialviews/ui/core/ScreenController.kt",
        ).map { File(it) }
            .firstOrNull { it.exists() }
            ?.readText()
            .orEmpty()

    // Everything between the braces of the function which starts with the signature
    private fun body(signature: String): String {
        assertTrue(screenController.isNotEmpty(), "No source file for ScreenController")
        val start = screenController.indexOf(signature)
        assertTrue(start >= 0, "ScreenController has no '$signature'")

        val open = screenController.indexOf('{', start)
        var depth = 0
        for (index in open until screenController.length) {
            when (screenController[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return screenController.substring(open, index + 1)
            }
        }
        error("The braces of '$signature' are not balanced")
    }

    private fun count(text: String) =
        Regex
            .escape(text)
            .toRegex()
            .findAll(screenController)
            .count()

    // The layout

    private fun layers(): List<Element> {
        val root = ResourceXml.root("layout/aerial_activity.xml")
        return (0 until root.childNodes.length)
            .map { root.childNodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { it as Element }
    }

    private fun idOf(layer: Element) = layer.getAttribute("android:id").removePrefix("@+id/")

    @Test
    fun `the weather is drawn over the video and the photos, and below the overlays`() {
        val ids = layers().map { idOf(it) }

        assertEquals("video_view", ids[0])
        assertEquals("image_view", ids[1])
        assertEquals("weather_effects", ids[2])
        assertEquals("overlay_view", ids[3])
        assertTrue(ids.indexOf("weather_effects") > ids.indexOf("video_view"))
        assertTrue(ids.indexOf("weather_effects") > ids.indexOf("image_view"))
        assertTrue(ids.indexOf("weather_effects") < ids.indexOf("overlay_view"))
    }

    @Test
    fun `the weather is also below the loading view and the brightness, so it fades with the clip and dims with it`() {
        val ids = layers().map { idOf(it) }

        assertTrue(ids.indexOf("weather_effects") < ids.indexOf("loading_view"))
        assertTrue(ids.indexOf("weather_effects") < ids.indexOf("brightness_view"))
        assertTrue(ids.indexOf("weather_effects") < ids.indexOf("notification_container"))
    }

    @Test
    fun `the view fills the screen, takes no focus and is not read out`() {
        val view = layers().first { idOf(it) == "weather_effects" }

        assertEquals("com.neilturner.aerialviews.ui.effects.WeatherEffectsView", view.tagName)
        assertEquals("match_parent", view.getAttribute("android:layout_width"))
        assertEquals("match_parent", view.getAttribute("android:layout_height"))
        assertEquals("false", view.getAttribute("android:focusable"))
        assertEquals("no", view.getAttribute("android:importantForAccessibility"))
    }

    @Test
    fun `there is one weather view`() {
        assertEquals(1, layers().count { it.tagName.endsWith("WeatherEffectsView") })
    }

    // The screen controller

    @Test
    fun `the controller is made for the view and started once, before anything plays`() {
        assertEquals(1, count("WeatherEffectsController(binding.weatherEffects, mainScope)"))
        assertEquals(1, count("weatherEffects.start()"))
        assertTrue(screenController.indexOf("weatherEffects.start()") < screenController.indexOf("overlayHelper = OverlayHelper("))
    }

    @Test
    fun `a clip that loads takes the effects away, with its weather tags, where the clip is known`() {
        assertEquals(1, count("weatherEffects.onMediaChanged(media.metadata.weather)"))
        assertTrue("weatherEffects.onMediaChanged(media.metadata.weather)" in body("private fun loadItem("))
    }

    @Test
    fun `a clip that is shown brings the effects in`() {
        assertEquals(1, count("weatherEffects.onMediaShown()"))

        // The function has braces in its comments, so it is found by where it starts and ends
        val start = screenController.indexOf("private fun fadeInNextItem(")
        val end = screenController.indexOf("private fun fadeOutCurrentItem(")
        val hook = screenController.indexOf("weatherEffects.onMediaShown()")
        assertTrue(start in 0 until end, "fadeInNextItem should come before fadeOutCurrentItem")
        assertTrue(hook in start until end, "the effects are brought in by fadeInNextItem")

        // Not in blackout, which returns first
        val guard = screenController.indexOf("if (blackOutMode) return", start)
        assertTrue(guard in start until hook)
    }

    @Test
    fun `blackout hides the effects and the end of blackout shows them again before the next clip loads`() {
        val enter = body("private fun enterBlackOutMode(")
        val exit = body("private fun exitBlackOutMode(")

        assertTrue("weatherEffects.setBlackout(true)" in enter)
        assertTrue("weatherEffects.setBlackout(false)" in exit)
        assertTrue(exit.indexOf("weatherEffects.setBlackout(false)") < exit.indexOf("loadNextItem()"))
        assertFalse("weatherEffects.setBlackout(false)" in enter)
    }

    @Test
    fun `ending the screensaver lets the effects go, before the scope ends`() {
        val stop = body("fun stop() {")

        assertTrue("weatherEffects.stop()" in stop)
        assertTrue(stop.indexOf("weatherEffects.stop()") < stop.indexOf("mainScope.cancel()"))
    }

    @Test
    fun `the weather is looked up for the effects, also when no weather overlay is shown`() {
        assertTrue("val needsCurrentWeather = hasWeatherNowOverlay || weatherEffects.needsWeather" in screenController)
        assertTrue("if (needsCurrentWeather || hasForecastOverlay) {" in screenController)
        assertTrue("fetchCurrentWeather = needsCurrentWeather," in screenController)
        // The forecast is only for the forecast overlay
        assertTrue("fetchForecast = hasForecastOverlay," in screenController)
    }

    @Test
    fun `the effects do not get in the way of the weather re-check of the playlist`() {
        // Both are set up in the same places, which the tests of the re-check look at
        assertTrue("weatherRecheck?.pause()" in body("private fun enterBlackOutMode("))
        assertTrue("weatherRecheck?.start()" in body("private fun exitBlackOutMode("))
        assertTrue("weatherRecheck?.stop()" in body("fun stop() {"))
    }
}
