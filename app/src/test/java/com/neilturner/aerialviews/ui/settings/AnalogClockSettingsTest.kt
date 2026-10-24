package com.neilturner.aerialviews.ui.settings

import com.neilturner.aerialviews.models.enums.AnalogClockHalo
import com.neilturner.aerialviews.models.enums.AnalogClockSize
import com.neilturner.aerialviews.models.prefs.AnalogClockPrefs
import com.neilturner.aerialviews.testutils.ResourceXml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Checks the Settings > Overlays > Analog clock screen is wired to its preferences and strings.
 * None of the xml can be inflated on the JVM, so the files are read directly.
 */
internal class AnalogClockSettingsTest {
    private val screen = "xml/settings_overlays_analog_clock.xml"
    private val overlaysScreen = "xml/settings_overlays.xml"
    private val strings = "values/strings_analog_clock.xml"
    private val russianStrings = "values-ru/strings_analog_clock.xml"
    private val fragmentClass = "com.neilturner.aerialviews.ui.settings.OverlaysAnalogClockFragment"

    @Test
    fun `settings screen uses the keys of the preference object`() {
        val keys = ResourceXml.elementsWithAttribute(screen, "app:key").map { it.getAttribute("app:key") }

        assertEquals(
            listOf(AnalogClockPrefs.KEY_SIZE, AnalogClockPrefs.KEY_SHOW_SECOND_HAND, AnalogClockPrefs.KEY_HALO),
            keys,
        )
        assertEquals("analog_clock_size", AnalogClockPrefs.KEY_SIZE)
        assertEquals("analog_clock_show_second_hand", AnalogClockPrefs.KEY_SHOW_SECOND_HAND)
        assertEquals("analog_clock_halo", AnalogClockPrefs.KEY_HALO)
    }

    @Test
    fun `size is a dropdown of the three sizes`() {
        val size = ResourceXml.preference(screen, AnalogClockPrefs.KEY_SIZE)
        assertEquals("ListPreference", size.tagName)
        assertEquals("@array/analog_clock_size_entries", size.getAttribute("app:entries"))
        assertEquals("@array/analog_clock_size_values", size.getAttribute("app:entryValues"))
        assertEquals("@string/analog_clock_size_title", size.getAttribute("app:title"))

        val values = ResourceXml.stringArray(strings, "analog_clock_size_values")
        val entries = ResourceXml.stringArray(strings, "analog_clock_size_entries")
        assertEquals(listOf("SMALL", "MEDIUM", "LARGE"), values)
        assertEquals(AnalogClockSize.entries.map { it.name }, values)
        assertEquals(values.size, entries.size)
    }

    @Test
    fun `size is medium by default`() {
        val size = ResourceXml.preference(screen, AnalogClockPrefs.KEY_SIZE)

        assertEquals("@string/analog_clock_size_default", size.getAttribute("app:defaultValue"))
        assertEquals("MEDIUM", ResourceXml.strings(strings).getValue("analog_clock_size_default"))
        assertEquals(AnalogClockSize.DEFAULT.name, ResourceXml.strings(strings).getValue("analog_clock_size_default"))
    }

    @Test
    fun `second hand is a checkbox which is on by default`() {
        val secondHand = ResourceXml.preference(screen, AnalogClockPrefs.KEY_SHOW_SECOND_HAND)

        assertEquals("CheckBoxPreference", secondHand.tagName)
        assertEquals("true", secondHand.getAttribute("app:defaultValue"))
        assertEquals("@string/analog_clock_show_second_hand_title", secondHand.getAttribute("app:title"))
    }

    @Test
    fun `halo is a dropdown of the four levels`() {
        val halo = ResourceXml.preference(screen, AnalogClockPrefs.KEY_HALO)
        assertEquals("ListPreference", halo.tagName)
        assertEquals("@array/analog_clock_halo_entries", halo.getAttribute("app:entries"))
        assertEquals("@array/analog_clock_halo_values", halo.getAttribute("app:entryValues"))
        assertEquals("@string/analog_clock_halo_title", halo.getAttribute("app:title"))

        val values = ResourceXml.stringArray(strings, "analog_clock_halo_values")
        val entries = ResourceXml.stringArray(strings, "analog_clock_halo_entries")
        assertEquals(listOf("OFF", "LIGHT", "MEDIUM", "STRONG"), values)
        assertEquals(AnalogClockHalo.entries.map { it.name }, values)
        assertEquals(values.size, entries.size)
    }

    @Test
    fun `halo is medium by default`() {
        val halo = ResourceXml.preference(screen, AnalogClockPrefs.KEY_HALO)
        val default = ResourceXml.strings(strings).getValue("analog_clock_halo_default")

        assertEquals("@string/analog_clock_halo_default", halo.getAttribute("app:defaultValue"))
        assertEquals("MEDIUM", default)
        assertEquals(AnalogClockHalo.DEFAULT.name, default)
    }

    @Test
    fun `analog clock is listed under overlays and opens its own screen`() {
        val entry =
            ResourceXml
                .elementsWithAttribute(overlaysScreen, "app:fragment")
                .firstOrNull { it.getAttribute("app:fragment") == fragmentClass }

        assertNotNull(entry, "$overlaysScreen has no entry for $fragmentClass")
        assertEquals("@string/appearance_analog_clock_title", entry?.getAttribute("app:title"))
        assertEquals("@string/appearance_analog_clock_summary", entry?.getAttribute("app:summary"))
    }

    @Test
    fun `analog clock sits right after the digital clock in the list`() {
        val fragments =
            ResourceXml
                .elementsWithAttribute(overlaysScreen, "app:fragment")
                .map { it.getAttribute("app:fragment").substringAfterLast('.') }

        assertTrue("OverlaysClockFragment" in fragments)
        assertEquals(fragments.indexOf("OverlaysClockFragment") + 1, fragments.indexOf("OverlaysAnalogClockFragment"))
    }

    @Test
    fun `the screen is a fragment which loads the analog clock preferences`() {
        val relativePath = fragmentClass.replace('.', '/') + ".kt"
        val source =
            listOf("src/main/java/$relativePath", "app/src/main/java/$relativePath")
                .map { File(it) }
                .firstOrNull { it.exists() }

        assertNotNull(source, "No source file for $fragmentClass")
        val code = source?.readText().orEmpty()
        assertTrue("class OverlaysAnalogClockFragment" in code)
        assertTrue("R.xml.settings_overlays_analog_clock" in code)
    }

    @Test
    fun `every string the screens use is defined`() {
        val defined = ResourceXml.defaultStringNames()
        val used = stringReferences(screen) + stringReferences(overlaysScreen).filter { "analog" in it }
        val listed =
            listOf("analog_clock_size_entries", "analog_clock_halo_entries")
                .flatMap { ResourceXml.stringArray(strings, it) }
                .map { it.removePrefix("@string/") }

        assertTrue(used.isNotEmpty())
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

    @Test
    fun `slot name is a translatable string`() {
        assertTrue("slot_analog_clock" in ResourceXml.translatableStringNames(strings))
        assertTrue("slot_analog_clock" in ResourceXml.strings(russianStrings))
    }

    // Names of the strings used by title, summary and default value attributes
    private fun stringReferences(path: String): Set<String> =
        listOf("app:title", "app:summary", "app:defaultValue")
            .flatMap { attribute ->
                ResourceXml.elementsWithAttribute(path, attribute).map { it.getAttribute(attribute) }
            }.filter { it.startsWith("@string/") }
            .map { it.removePrefix("@string/") }
            .toSet()
}
