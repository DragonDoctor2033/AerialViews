package com.neilturner.aerialviews.ui.settings

import com.neilturner.aerialviews.models.prefs.WeatherVideoPrefs
import com.neilturner.aerialviews.services.weather.WeatherRecheckPolicy
import com.neilturner.aerialviews.testutils.ResourceXml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import kotlin.time.Duration.Companion.minutes

/**
 * Checks the Settings > Playlist > Weather matching > Re-check the weather while playing setting is
 * wired to its preference and strings. None of the xml can be inflated on the JVM, so the files are
 * read directly.
 */
internal class WeatherRecheckSettingsTest {
    private val screen = "xml/settings_playlist.xml"
    private val strings = "values/strings_weather_videos.xml"
    private val russianStrings = "values-ru/strings_weather_videos.xml"
    private val key = WeatherVideoPrefs.KEY_RECHECK_MINUTES

    private fun recheck() = ResourceXml.preference(screen, key)

    private fun weatherCategory(): Element =
        ResourceXml
            .elementsWithAttribute(screen, "app:title")
            .first { it.tagName == "PreferenceCategory" && it.getAttribute("app:title") == "@string/weather_videos_category" }

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()

    // The preference object

    @Test
    fun `the setting is stored under its own key`() {
        assertEquals("weather_videos_recheck_minutes", key)
        assertEquals(key, recheck().getAttribute("app:key"))
    }

    @Test
    fun `the preference stores the minutes as text, twenty unless chosen otherwise`() {
        val prefs = mainSource("com/neilturner/aerialviews/models/prefs/WeatherVideoPrefs.kt")

        assertTrue(prefs.isNotEmpty(), "No source file for WeatherVideoPrefs")
        assertTrue(
            "var recheckMinutes by stringPref(DEFAULT_RECHECK_MINUTES.toString(), KEY_RECHECK_MINUTES)" in prefs,
            "recheckMinutes is not declared with the default minutes and its key",
        )
        assertEquals(20, WeatherVideoPrefs.DEFAULT_RECHECK_MINUTES)
    }

    // The screen

    @Test
    fun `the setting is a dropdown of off and four lengths of time`() {
        val setting = recheck()
        assertEquals("ListPreference", setting.tagName)
        assertEquals("@array/weather_videos_recheck_entries", setting.getAttribute("app:entries"))
        assertEquals("@array/weather_videos_recheck_values", setting.getAttribute("app:entryValues"))
        assertEquals("@string/weather_videos_recheck_title", setting.getAttribute("app:title"))
        assertEquals("@string/weather_videos_recheck_title", setting.getAttribute("app:dialogTitle"))
        assertEquals("true", setting.getAttribute("app:useSimpleSummaryProvider"))

        val values = ResourceXml.stringArray(strings, "weather_videos_recheck_values")
        val entries = ResourceXml.stringArray(strings, "weather_videos_recheck_entries")
        assertEquals(listOf("0", "10", "20", "30", "60"), values)
        assertEquals(values.size, entries.size)
        assertEquals("@string/weather_videos_recheck_off", entries.first())
    }

    @Test
    fun `every choice is a number of minutes the policy understands, and off is the policy's off`() {
        val values = ResourceXml.stringArray(strings, "weather_videos_recheck_values")

        values.forEach { value ->
            val interval = WeatherRecheckPolicy.interval(value)
            if (value == "0") {
                assertNull(interval, "'$value' should turn re-checking off")
            } else {
                assertEquals(value.toInt().minutes, interval, "'$value'")
            }
        }
    }

    @Test
    fun `the setting is twenty minutes by default`() {
        val default = ResourceXml.strings(strings).getValue("weather_videos_recheck_default")

        assertEquals("@string/weather_videos_recheck_default", recheck().getAttribute("app:defaultValue"))
        assertEquals("20", default)
        assertEquals(WeatherVideoPrefs.DEFAULT_RECHECK_MINUTES.toString(), default)
        assertTrue(default in ResourceXml.stringArray(strings, "weather_videos_recheck_values"))
        assertEquals(20.minutes, WeatherRecheckPolicy.interval(default))
        assertTrue("weather_videos_recheck_default" !in ResourceXml.translatableStringNames(strings))
    }

    @Test
    fun `only the default is marked with a star`() {
        val values = ResourceXml.stringArray(strings, "weather_videos_recheck_values")
        val entries = ResourceXml.stringArray(strings, "weather_videos_recheck_entries").map { it.removePrefix("@string/") }

        listOf(ResourceXml.strings(strings), ResourceXml.strings(russianStrings)).forEach { texts ->
            values.forEachIndexed { index, value ->
                val marked = texts.getValue(entries[index]).endsWith("*")
                assertEquals(value == WeatherVideoPrefs.DEFAULT_RECHECK_MINUTES.toString(), marked, "'${entries[index]}' ($value)")
            }
        }
    }

    @Test
    fun `each choice says how many minutes it is`() {
        val values = ResourceXml.stringArray(strings, "weather_videos_recheck_values")
        val entries = ResourceXml.stringArray(strings, "weather_videos_recheck_entries").map { it.removePrefix("@string/") }
        val english = ResourceXml.strings(strings)
        val russian = ResourceXml.strings(russianStrings)

        values.zip(entries).filter { (value, _) -> value != "0" }.forEach { (value, entry) ->
            assertTrue(english.getValue(entry).startsWith("$value minutes"), "'$entry' should say $value minutes")
            assertTrue(russian.getValue(entry).startsWith("$value минут"), "'$entry' in Russian should say $value минут")
        }
        assertEquals("Off", english.getValue("weather_videos_recheck_off"))
        assertEquals("Выкл.", russian.getValue("weather_videos_recheck_off"))
    }

    @Test
    fun `the title says what it does`() {
        assertEquals("Re-check the weather while playing", ResourceXml.strings(strings).getValue("weather_videos_recheck_title"))
    }

    @Test
    fun `the setting is greyed out while matching the weather is off`() {
        val match = ResourceXml.preference(screen, "playlist_weather_match")

        assertEquals("playlist_weather_match", recheck().getAttribute("app:dependency"))
        assertEquals("SwitchPreference", match.tagName)
        assertEquals("false", match.getAttribute("app:defaultValue"))
    }

    @Test
    fun `the setting is in the weather matching category, between the switch and the override`() {
        val keys = weatherCategory().childElements().map { it.getAttribute("app:key") }

        assertEquals(listOf("playlist_weather_match", key, "playlist_weather_override"), keys)
    }

    @Test
    fun `the setting is on the playlist screen once`() {
        val keys = ResourceXml.elementsWithAttribute(screen, "app:key").map { it.getAttribute("app:key") }

        assertEquals(1, keys.count { it == key })
    }

    // The strings

    @Test
    fun `every string the setting uses is defined`() {
        val defined = ResourceXml.defaultStringNames()
        val used =
            listOf("app:title", "app:dialogTitle", "app:defaultValue")
                .map { recheck().getAttribute(it) }
                .filter { it.startsWith("@string/") }
                .map { it.removePrefix("@string/") }
        val listed =
            ResourceXml
                .stringArray(strings, "weather_videos_recheck_entries")
                .map { it.removePrefix("@string/") }

        assertEquals(3, used.size)
        assertEquals(5, listed.size)
        (used + listed).forEach { name ->
            assertTrue(name in defined, "string '$name' is not defined")
        }
    }

    @Test
    fun `strings are translated into russian`() {
        val translatable = ResourceXml.translatableStringNames(strings)
        val russian = ResourceXml.strings(russianStrings)

        // Nothing missing, and no leftovers which no longer exist in English
        assertTrue(translatable.isNotEmpty())
        assertEquals(translatable, russian.keys)

        // A real translation, not the English text pasted in
        russian.forEach { (name, text) ->
            assertTrue(text.any { it in 'а'..'я' || it in 'А'..'Я' }, "'$name' is not in Russian: $text")
        }
    }

    // A source file under src/main/java, as text, or empty when there is no such file. Gradle runs
    // unit tests with the app module as the working directory
    private fun mainSource(relativePath: String): String =
        listOf("src/main/java/$relativePath", "app/src/main/java/$relativePath")
            .map { File(it) }
            .firstOrNull { it.exists() }
            ?.readText()
            .orEmpty()
}
