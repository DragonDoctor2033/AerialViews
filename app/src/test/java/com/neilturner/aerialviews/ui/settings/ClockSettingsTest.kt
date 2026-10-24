package com.neilturner.aerialviews.ui.settings

import com.neilturner.aerialviews.models.prefs.ClockPrefs
import com.neilturner.aerialviews.testutils.ResourceXml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Checks the Settings > Overlays > Clock screen is wired to the show seconds preference and its
 * strings. None of the xml can be inflated on the JVM, so the files are read directly.
 */
internal class ClockSettingsTest {
    private val screen = "xml/settings_overlays_clock.xml"
    private val strings = "values/strings_clock.xml"
    private val russianStrings = "values-ru/strings_clock.xml"

    @Test
    fun `show seconds is a checkbox which is off by default`() {
        val seconds = ResourceXml.preference(screen, ClockPrefs.KEY_SHOW_SECONDS)

        assertEquals("clock_show_seconds", ClockPrefs.KEY_SHOW_SECONDS)
        assertEquals("CheckBoxPreference", seconds.tagName)
        assertEquals("false", seconds.getAttribute("app:defaultValue"))
        assertEquals("@string/appearance_clock_show_seconds_title", seconds.getAttribute("app:title"))
        assertEquals("@string/appearance_clock_show_seconds_summary", seconds.getAttribute("app:summary"))
    }

    @Test
    fun `the other clock settings are still on the screen`() {
        val keys = ResourceXml.elementsWithAttribute(screen, "app:key").map { it.getAttribute("app:key") }

        assertEquals(
            listOf("clock_format", ClockPrefs.KEY_SHOW_SECONDS, "clock_force_latin_digits", "clock_size", "clock_weight"),
            keys,
        )
    }

    @Test
    fun `show seconds sits right under the clock format`() {
        val keys = ResourceXml.elementsWithAttribute(screen, "app:key").map { it.getAttribute("app:key") }

        assertEquals(keys.indexOf("clock_format") + 1, keys.indexOf(ClockPrefs.KEY_SHOW_SECONDS))
    }

    @Test
    fun `every string the screen uses for seconds is defined`() {
        val defined = ResourceXml.defaultStringNames()
        val seconds = ResourceXml.preference(screen, ClockPrefs.KEY_SHOW_SECONDS)

        listOf("app:title", "app:summary").forEach { attribute ->
            val name = seconds.getAttribute(attribute).removePrefix("@string/")
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
}
