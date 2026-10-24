package com.neilturner.aerialviews.ui.settings

import com.neilturner.aerialviews.models.enums.AnalogClockCenterSize
import com.neilturner.aerialviews.models.enums.AnalogClockHalo
import com.neilturner.aerialviews.models.enums.AnalogClockSecondHandMotion
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
            listOf(
                AnalogClockPrefs.KEY_SIZE,
                AnalogClockPrefs.KEY_SHOW_SECOND_HAND,
                AnalogClockPrefs.KEY_SECOND_HAND_MOTION,
                AnalogClockPrefs.KEY_HALO,
                AnalogClockPrefs.KEY_SHOW_IN_CENTER,
                AnalogClockPrefs.KEY_CENTER_SIZE,
            ),
            keys,
        )
        assertEquals("analog_clock_size", AnalogClockPrefs.KEY_SIZE)
        assertEquals("analog_clock_show_second_hand", AnalogClockPrefs.KEY_SHOW_SECOND_HAND)
        assertEquals("analog_clock_second_hand_motion", AnalogClockPrefs.KEY_SECOND_HAND_MOTION)
        assertEquals("analog_clock_halo", AnalogClockPrefs.KEY_HALO)
        assertEquals("analog_clock_show_in_center", AnalogClockPrefs.KEY_SHOW_IN_CENTER)
        assertEquals("analog_clock_center_size", AnalogClockPrefs.KEY_CENTER_SIZE)
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
    fun `second hand motion is a dropdown of smooth sweep and tick`() {
        val motion = ResourceXml.preference(screen, AnalogClockPrefs.KEY_SECOND_HAND_MOTION)
        assertEquals("ListPreference", motion.tagName)
        assertEquals("@array/analog_clock_second_hand_motion_entries", motion.getAttribute("app:entries"))
        assertEquals("@array/analog_clock_second_hand_motion_values", motion.getAttribute("app:entryValues"))
        assertEquals("@string/analog_clock_second_hand_motion_title", motion.getAttribute("app:title"))
        assertEquals("true", motion.getAttribute("app:useSimpleSummaryProvider"))

        val values = ResourceXml.stringArray(strings, "analog_clock_second_hand_motion_values")
        val entries = ResourceXml.stringArray(strings, "analog_clock_second_hand_motion_entries")
        assertEquals(listOf("SMOOTH", "TICK"), values)
        assertEquals(AnalogClockSecondHandMotion.entries.map { it.name }, values)
        assertEquals(values.size, entries.size)
        assertEquals("Second hand motion", ResourceXml.strings(strings).getValue("analog_clock_second_hand_motion_title"))
    }

    @Test
    fun `second hand motion is a smooth sweep by default`() {
        val motion = ResourceXml.preference(screen, AnalogClockPrefs.KEY_SECOND_HAND_MOTION)
        val default = ResourceXml.strings(strings).getValue("analog_clock_second_hand_motion_default")

        assertEquals("@string/analog_clock_second_hand_motion_default", motion.getAttribute("app:defaultValue"))
        assertEquals("SMOOTH", default)
        assertEquals(AnalogClockSecondHandMotion.DEFAULT.name, default)
    }

    @Test
    fun `only the default motion is marked with a star`() {
        val values = ResourceXml.stringArray(strings, "analog_clock_second_hand_motion_values")
        val entries = ResourceXml.stringArray(strings, "analog_clock_second_hand_motion_entries").map { it.removePrefix("@string/") }

        listOf(ResourceXml.strings(strings), ResourceXml.strings(russianStrings)).forEach { texts ->
            values.forEachIndexed { index, value ->
                val marked = texts.getValue(entries[index]).endsWith("*")
                assertEquals(value == AnalogClockSecondHandMotion.DEFAULT.name, marked, "'${entries[index]}' ($value)")
            }
        }
    }

    @Test
    fun `second hand motion only matters, and is only enabled, when the second hand is shown`() {
        val motion = ResourceXml.preference(screen, AnalogClockPrefs.KEY_SECOND_HAND_MOTION)

        assertEquals(AnalogClockPrefs.KEY_SHOW_SECOND_HAND, motion.getAttribute("app:dependency"))
    }

    @Test
    fun `second hand motion sits right under the second hand checkbox`() {
        val keys = ResourceXml.elementsWithAttribute(screen, "app:key").map { it.getAttribute("app:key") }

        assertEquals(
            keys.indexOf(AnalogClockPrefs.KEY_SHOW_SECOND_HAND) + 1,
            keys.indexOf(AnalogClockPrefs.KEY_SECOND_HAND_MOTION),
        )
    }

    @Test
    fun `the preference stores the motion by name and defaults to the smooth sweep`() {
        val prefs = mainSource("com/neilturner/aerialviews/models/prefs/AnalogClockPrefs.kt")

        assertTrue(prefs.isNotEmpty(), "No source file for AnalogClockPrefs")
        assertTrue(
            "var secondHandMotion by stringPref(AnalogClockSecondHandMotion.DEFAULT.name, KEY_SECOND_HAND_MOTION)" in prefs,
            "secondHandMotion is not declared with the default motion and its key",
        )
    }

    @Test
    fun `both kinds of analog clock get the motion from the preference`() {
        val helper = mainSource("com/neilturner/aerialviews/ui/helpers/OverlayHelper.kt")
        val assignment = "secondHandMotion = AnalogClockSecondHandMotion.fromValue(AnalogClockPrefs.secondHandMotion)"

        assertTrue(helper.isNotEmpty(), "No source file for OverlayHelper")
        // One in a slot, one in the center of the screen
        assertEquals(
            2,
            Regex
                .escape(assignment)
                .toRegex()
                .findAll(helper)
                .count(),
        )
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
    fun `the clock in the center is a checkbox which is off by default`() {
        val center = ResourceXml.preference(screen, AnalogClockPrefs.KEY_SHOW_IN_CENTER)

        assertEquals("CheckBoxPreference", center.tagName)
        assertEquals("false", center.getAttribute("app:defaultValue"))
        assertEquals("@string/analog_clock_show_in_center_title", center.getAttribute("app:title"))
        assertEquals("@string/analog_clock_show_in_center_summary", center.getAttribute("app:summary"))
        assertEquals("Show in the center of the screen", ResourceXml.strings(strings).getValue("analog_clock_show_in_center_title"))
    }

    @Test
    fun `the size in the center is a dropdown of the three sizes, large by default`() {
        val size = ResourceXml.preference(screen, AnalogClockPrefs.KEY_CENTER_SIZE)
        assertEquals("ListPreference", size.tagName)
        assertEquals("@array/analog_clock_center_size_entries", size.getAttribute("app:entries"))
        assertEquals("@array/analog_clock_center_size_values", size.getAttribute("app:entryValues"))
        assertEquals("@string/analog_clock_center_size_title", size.getAttribute("app:title"))

        val values = ResourceXml.stringArray(strings, "analog_clock_center_size_values")
        val entries = ResourceXml.stringArray(strings, "analog_clock_center_size_entries")
        assertEquals(listOf("MEDIUM", "LARGE", "EXTRA_LARGE"), values)
        assertEquals(AnalogClockCenterSize.entries.map { it.name }, values)
        assertEquals(values.size, entries.size)

        val default = ResourceXml.strings(strings).getValue("analog_clock_center_size_default")
        assertEquals("@string/analog_clock_center_size_default", size.getAttribute("app:defaultValue"))
        assertEquals("LARGE", default)
        assertEquals(AnalogClockCenterSize.DEFAULT.name, default)
    }

    @Test
    fun `the size in the center only matters, and is only enabled, when the clock is shown there`() {
        val size = ResourceXml.preference(screen, AnalogClockPrefs.KEY_CENTER_SIZE)

        assertEquals(AnalogClockPrefs.KEY_SHOW_IN_CENTER, size.getAttribute("app:dependency"))
    }

    @Test
    fun `each size in the center says its share of the screen height`() {
        val values = ResourceXml.stringArray(strings, "analog_clock_center_size_values")
        val entries = ResourceXml.stringArray(strings, "analog_clock_center_size_entries").map { it.removePrefix("@string/") }
        val texts = ResourceXml.strings(strings)
        val russian = ResourceXml.strings(russianStrings)

        values.forEachIndexed { index, value ->
            val percent = AnalogClockCenterSize.valueOf(value).percentOfScreenHeight
            assertTrue("$percent%" in texts.getValue(entries[index]), "'${entries[index]}' should say $percent%")
            assertTrue("$percent%" in russian.getValue(entries[index]), "'${entries[index]}' in Russian should say $percent%")
        }
    }

    @Test
    fun `the clock in the center has a category of its own, after the clock settings`() {
        val categories = ResourceXml.elementsWithAttribute(screen, "app:title").filter { it.tagName == "PreferenceCategory" }

        assertEquals(
            listOf("@string/category_analog_clock", "@string/category_analog_clock_center"),
            categories.map { it.getAttribute("app:title") },
        )
        val inCenterCategory =
            ResourceXml.children(categories.last(), "CheckBoxPreference") + ResourceXml.children(categories.last(), "ListPreference")
        assertEquals(
            setOf(AnalogClockPrefs.KEY_SHOW_IN_CENTER, AnalogClockPrefs.KEY_CENTER_SIZE),
            inCenterCategory.map { it.getAttribute("app:key") }.toSet(),
        )
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
        val code = mainSource(fragmentClass.replace('.', '/') + ".kt")

        assertTrue(code.isNotEmpty(), "No source file for $fragmentClass")
        assertTrue("class OverlaysAnalogClockFragment" in code)
        assertTrue("R.xml.settings_overlays_analog_clock" in code)
    }

    @Test
    fun `every string the screens use is defined`() {
        val defined = ResourceXml.defaultStringNames()
        val used = stringReferences(screen) + stringReferences(overlaysScreen).filter { "analog" in it }
        val listed =
            listOf(
                "analog_clock_size_entries",
                "analog_clock_second_hand_motion_entries",
                "analog_clock_halo_entries",
                "analog_clock_center_size_entries",
            ).flatMap { ResourceXml.stringArray(strings, it) }
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

    // A source file under src/main/java, as text, or empty when there is no such file. Gradle runs
    // unit tests with the app module as the working directory
    private fun mainSource(relativePath: String): String =
        listOf("src/main/java/$relativePath", "app/src/main/java/$relativePath")
            .map { File(it) }
            .firstOrNull { it.exists() }
            ?.readText()
            .orEmpty()

    // Names of the strings used by title, summary and default value attributes
    private fun stringReferences(path: String): Set<String> =
        listOf("app:title", "app:summary", "app:defaultValue")
            .flatMap { attribute ->
                ResourceXml.elementsWithAttribute(path, attribute).map { it.getAttribute(attribute) }
            }.filter { it.startsWith("@string/") }
            .map { it.removePrefix("@string/") }
            .toSet()
}
