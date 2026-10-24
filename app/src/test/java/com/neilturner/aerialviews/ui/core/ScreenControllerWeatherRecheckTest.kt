package com.neilturner.aerialviews.ui.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Checks that the screen controller starts, pauses and stops the weather re-check where it should,
 * and only takes items from a playlist at one place. The controller needs a device to run, so its
 * source is read, the way the settings tests read the xml.
 */
internal class ScreenControllerWeatherRecheckTest {
    private val source =
        listOf(
            "src/main/java/com/neilturner/aerialviews/ui/core/ScreenController.kt",
            "app/src/main/java/com/neilturner/aerialviews/ui/core/ScreenController.kt",
        ).map { File(it) }
            .firstOrNull { it.exists() }
            ?.readText()
            .orEmpty()

    // Everything between the braces of the function which starts with the signature
    private fun body(signature: String): String {
        assertTrue(source.isNotEmpty(), "No source file for ScreenController")
        val start = source.indexOf(signature)
        assertTrue(start >= 0, "ScreenController has no '$signature'")

        val open = source.indexOf('{', start)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(open, index + 1)
            }
        }
        error("The braces of '$signature' are not balanced")
    }

    private fun count(text: String) =
        Regex
            .escape(text)
            .toRegex()
            .findAll(source)
            .count()

    // Timer

    @Test
    fun `a blackout stops the re-check`() {
        assertTrue("weatherRecheck?.pause()" in body("private fun enterBlackOutMode("))
    }

    @Test
    fun `the sleep timer and the scheduled blackout start a blackout, so they stop the re-check too`() {
        assertTrue("toggleBlackOutMode(BlackOutSource.SLEEP_TIMER)" in body("private fun scheduleSleepTimer("))
        assertTrue("enterBlackOutMode(source)" in body("fun toggleBlackOutMode("))
        assertTrue("enterBlackOutMode(BlackOutSource.SCHEDULED)" in body("private fun checkScheduledBlackout("))
    }

    @Test
    fun `the re-check pauses instead of stopping, so a blackout does not end it for good`() {
        assertFalse("weatherRecheck?.stop()" in body("private fun enterBlackOutMode("))
        assertTrue("weatherRecheck?.start()" in body("private fun exitBlackOutMode("))
    }

    @Test
    fun `ending the screensaver stops the re-check and lets go of it, before the scope ends`() {
        val stop = body("fun stop() {")

        assertTrue("weatherRecheck?.stop()" in stop)
        assertTrue("weatherRecheck = null" in stop)
        assertTrue(stop.indexOf("weatherRecheck?.stop()") < stop.indexOf("mainScope.cancel()"))
    }

    @Test
    fun `the re-check runs in the scope of the screen, which ends with it, and not in a scope of its own`() {
        val setUp = body("private fun setUpWeatherRecheck(")

        assertTrue("scope = mainScope" in setUp)
        assertTrue("mainScope.cancel()" in body("fun stop() {"))
    }

    // Set up

    @Test
    fun `the re-check is set up once the playlist is playing, for the weather it was made for`() {
        val setUp = "setUpWeatherRecheck(mediaResult.weatherCondition)"

        assertEquals(1, count(setUp))
        assertTrue(source.indexOf("playlist = mediaResult.mediaPlaylist") < source.indexOf(setUp))
    }

    @Test
    fun `the re-check needs matching to be on and the setting not to be off`() {
        val setUp = body("private fun setUpWeatherRecheck(")

        assertTrue("WeatherVideoPrefs.matchWeather" in setUp)
        assertTrue("WeatherRecheckPolicy.interval(WeatherVideoPrefs.recheckMinutes)" in setUp)
        assertTrue("interval == null" in setUp)
        assertTrue("isStopped" in setUp)
    }

    @Test
    fun `the re-check does not start in a blackout`() {
        assertTrue("if (!blackOutMode) it.start()" in body("private fun setUpWeatherRecheck("))
    }

    @Test
    fun `the new playlist is made in the usual way for the weather which was found, and cannot empty the cache`() {
        val setUp = body("private fun setUpWeatherRecheck(")

        assertTrue("MediaService(context, currentWeather = { condition })" in setUp)
        assertTrue("keepCacheWhenEmpty = true" in setUp)
        assertTrue("playingMusic = playingMusic" in setUp)
    }

    // The next media boundary

    @Test
    fun `switching to the new playlist does not touch what is on screen`() {
        val switch = body("private fun switchPlaylist(")

        assertTrue("playlist = result.mediaPlaylist" in switch)
        listOf("loadNextItem", "loadItem", "nextItem()", "previousItem()", "videoPlayer", "imagePlayer", "fadeOut", "currentMedia")
            .forEach { assertFalse(it in switch, "switchPlaylist should not use $it") }
    }

    @Test
    fun `items are only taken from the playlist when the next one is loaded`() {
        val next = body("private fun loadNextItem(")

        assertEquals(1, count("playlist.nextItem()"))
        assertEquals(1, count("playlist.previousItem()"))
        assertTrue("playlist.nextItem()" in next)
        assertTrue("playlist.previousItem()" in next)
    }

    @Test
    fun `going back from a playlist which has not played an item yet plays its first`() {
        assertTrue("previous && playlist.currentPosition >= 0" in body("private fun loadNextItem("))
    }

    @Test
    fun `with the playlist cache on the saved music track follows the switch`() {
        assertTrue("if (GeneralPrefs.playlistCache) saveMusicTrackPosition()" in body("private fun switchPlaylist("))
    }
}
