package com.neilturner.aerialviews.ui.settings

import com.neilturner.aerialviews.models.enums.WeatherEffectsFrameRate
import com.neilturner.aerialviews.models.enums.WeatherEffectsIntensity
import com.neilturner.aerialviews.models.prefs.WeatherEffectsPrefs
import com.neilturner.aerialviews.testutils.ResourceXml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Checks the Settings > Overlays > Weather effects screen is wired to its preferences and strings.
 * None of the xml can be inflated on the JVM, so the files are read directly.
 */
internal class WeatherEffectsSettingsTest {
    private val screen = "xml/settings_overlays_weather_effects.xml"
    private val overlaysScreen = "xml/settings_overlays.xml"
    private val strings = "values/strings_weather_effects.xml"
    private val russianStrings = "values-ru/strings_weather_effects.xml"
    private val fragmentClass = "com.neilturner.aerialviews.ui.settings.OverlaysWeatherEffectsFragment"

    // The preferences

    @Test
    fun `the preference keys are the ones the settings screen and the backup use`() {
        assertEquals("weather_effects_enabled", WeatherEffectsPrefs.KEY_ENABLED)
        assertEquals("weather_effects_intensity", WeatherEffectsPrefs.KEY_INTENSITY)
        assertEquals("weather_effects_skip_on_weather_clips", WeatherEffectsPrefs.KEY_SKIP_ON_WEATHER_CLIPS)
        assertEquals("weather_effects_frame_rate", WeatherEffectsPrefs.KEY_FRAME_RATE)
    }

    @Test
    fun `settings screen uses the keys of the preference object, in order`() {
        val keys = ResourceXml.elementsWithAttribute(screen, "app:key").map { it.getAttribute("app:key") }

        assertEquals(
            listOf(
                WeatherEffectsPrefs.KEY_ENABLED,
                WeatherEffectsPrefs.KEY_INTENSITY,
                WeatherEffectsPrefs.KEY_SKIP_ON_WEATHER_CLIPS,
                WeatherEffectsPrefs.KEY_FRAME_RATE,
            ),
            keys,
        )
    }

    @Test
    fun `the keys are not used by any other screen`() {
        val others =
            ResourceXml
                .file("xml")
                .listFiles { file -> file.extension == "xml" && file.name != "settings_overlays_weather_effects.xml" }
                .orEmpty()
                .flatMap { file -> ResourceXml.elementsWithAttribute("xml/${file.name}", "app:key").map { it.getAttribute("app:key") } }

        listOf(
            WeatherEffectsPrefs.KEY_ENABLED,
            WeatherEffectsPrefs.KEY_INTENSITY,
            WeatherEffectsPrefs.KEY_SKIP_ON_WEATHER_CLIPS,
            WeatherEffectsPrefs.KEY_FRAME_RATE,
        ).forEach { key -> assertTrue(key !in others, "$key is also used by another screen") }
    }

    @Test
    fun `the preferences live in the shared file, are stored by name, and have the defaults of the screen`() {
        val prefs = mainSource("com/neilturner/aerialviews/models/prefs/WeatherEffectsPrefs.kt")

        assertTrue(prefs.isNotEmpty(), "No source file for WeatherEffectsPrefs")
        assertTrue("override val kotprefName = \"\${context.packageName}_preferences\"" in prefs, "not in the shared preferences file")
        assertTrue("var enabled by booleanPref(true, KEY_ENABLED)" in prefs)
        assertTrue("var intensity by stringPref(WeatherEffectsIntensity.DEFAULT.name, KEY_INTENSITY)" in prefs)
        assertTrue("var skipOnWeatherClips by booleanPref(true, KEY_SKIP_ON_WEATHER_CLIPS)" in prefs)
        assertTrue("var frameRate by stringPref(WeatherEffectsFrameRate.DEFAULT.name, KEY_FRAME_RATE)" in prefs)
    }

    // Enable

    @Test
    fun `the effects are a checkbox which is on by default`() {
        val enabled = ResourceXml.preference(screen, WeatherEffectsPrefs.KEY_ENABLED)

        assertEquals("CheckBoxPreference", enabled.tagName)
        assertEquals("true", enabled.getAttribute("app:defaultValue"))
        assertEquals("@string/weather_effects_enabled_title", enabled.getAttribute("app:title"))
        assertEquals("@string/weather_effects_enabled_summary", enabled.getAttribute("app:summary"))
        assertEquals("Show weather effects", ResourceXml.strings(strings).getValue("weather_effects_enabled_title"))
    }

    @Test
    fun `the three other settings only matter, and are only enabled, when the effects are on`() {
        listOf(
            WeatherEffectsPrefs.KEY_INTENSITY,
            WeatherEffectsPrefs.KEY_SKIP_ON_WEATHER_CLIPS,
            WeatherEffectsPrefs.KEY_FRAME_RATE,
        ).forEach { key ->
            assertEquals(WeatherEffectsPrefs.KEY_ENABLED, ResourceXml.preference(screen, key).getAttribute("app:dependency"), key)
        }
        assertEquals("", ResourceXml.preference(screen, WeatherEffectsPrefs.KEY_ENABLED).getAttribute("app:dependency"))
    }

    // Intensity

    @Test
    fun `intensity is a dropdown of subtle, normal and strong`() {
        val intensity = ResourceXml.preference(screen, WeatherEffectsPrefs.KEY_INTENSITY)
        assertEquals("ListPreference", intensity.tagName)
        assertEquals("@array/weather_effects_intensity_entries", intensity.getAttribute("app:entries"))
        assertEquals("@array/weather_effects_intensity_values", intensity.getAttribute("app:entryValues"))
        assertEquals("@string/weather_effects_intensity_title", intensity.getAttribute("app:title"))
        assertEquals("true", intensity.getAttribute("app:useSimpleSummaryProvider"))

        val values = ResourceXml.stringArray(strings, "weather_effects_intensity_values")
        val entries = ResourceXml.stringArray(strings, "weather_effects_intensity_entries")
        assertEquals(listOf("SUBTLE", "NORMAL", "STRONG"), values)
        assertEquals(WeatherEffectsIntensity.entries.map { it.name }, values)
        assertEquals(values.size, entries.size)
        assertEquals("Intensity", ResourceXml.strings(strings).getValue("weather_effects_intensity_title"))
    }

    @Test
    fun `intensity is normal by default`() {
        val intensity = ResourceXml.preference(screen, WeatherEffectsPrefs.KEY_INTENSITY)
        val default = ResourceXml.strings(strings).getValue("weather_effects_intensity_default")

        assertEquals("@string/weather_effects_intensity_default", intensity.getAttribute("app:defaultValue"))
        assertEquals("NORMAL", default)
        assertEquals(WeatherEffectsIntensity.DEFAULT.name, default)
    }

    // Skip on weather clips

    @Test
    fun `skip on weather clips is a checkbox which is on by default`() {
        val skip = ResourceXml.preference(screen, WeatherEffectsPrefs.KEY_SKIP_ON_WEATHER_CLIPS)

        assertEquals("CheckBoxPreference", skip.tagName)
        assertEquals("true", skip.getAttribute("app:defaultValue"))
        assertEquals("@string/weather_effects_skip_title", skip.getAttribute("app:title"))
        assertEquals("@string/weather_effects_skip_summary", skip.getAttribute("app:summary"))
        assertEquals("Skip on weather clips", ResourceXml.strings(strings).getValue("weather_effects_skip_title"))
    }

    // Frame rate

    @Test
    fun `frame rate is a dropdown of 60 and 30`() {
        val rate = ResourceXml.preference(screen, WeatherEffectsPrefs.KEY_FRAME_RATE)
        assertEquals("ListPreference", rate.tagName)
        assertEquals("@array/weather_effects_frame_rate_entries", rate.getAttribute("app:entries"))
        assertEquals("@array/weather_effects_frame_rate_values", rate.getAttribute("app:entryValues"))
        assertEquals("@string/weather_effects_frame_rate_title", rate.getAttribute("app:title"))
        assertEquals("true", rate.getAttribute("app:useSimpleSummaryProvider"))

        val values = ResourceXml.stringArray(strings, "weather_effects_frame_rate_values")
        val entries = ResourceXml.stringArray(strings, "weather_effects_frame_rate_entries").map { it.removePrefix("@string/") }
        assertEquals(listOf("FPS_60", "FPS_30"), values)
        assertEquals(WeatherEffectsFrameRate.entries.map { it.name }, values)

        // Each entry says its number of frames
        val texts = ResourceXml.strings(strings)
        values.forEachIndexed { index, value ->
            val fps = WeatherEffectsFrameRate.valueOf(value).fps
            assertTrue(texts.getValue(entries[index]).startsWith("$fps "), "'${entries[index]}' should say $fps")
        }
    }

    @Test
    fun `frame rate is 30 by default`() {
        val rate = ResourceXml.preference(screen, WeatherEffectsPrefs.KEY_FRAME_RATE)
        val default = ResourceXml.strings(strings).getValue("weather_effects_frame_rate_default")

        assertEquals("@string/weather_effects_frame_rate_default", rate.getAttribute("app:defaultValue"))
        assertEquals("FPS_30", default)
        assertEquals(WeatherEffectsFrameRate.DEFAULT.name, default)
    }

    @Test
    fun `the 30 frames are the one with the star in both languages, and 60 can still be picked`() {
        val values = ResourceXml.stringArray(strings, "weather_effects_frame_rate_values")
        val entries = ResourceXml.stringArray(strings, "weather_effects_frame_rate_entries").map { it.removePrefix("@string/") }

        listOf(ResourceXml.strings(strings), ResourceXml.strings(russianStrings)).forEach { texts ->
            assertTrue(texts.getValue(entries[values.indexOf("FPS_30")]).endsWith("*"))
            assertTrue(!texts.getValue(entries[values.indexOf("FPS_60")]).contains("*"))
        }
        assertTrue("FPS_60" in values)
    }

    @Test
    fun `only the default is marked with a star in the dropdowns`() {
        listOf(
            Triple("weather_effects_intensity_values", "weather_effects_intensity_entries", WeatherEffectsIntensity.DEFAULT.name),
            Triple("weather_effects_frame_rate_values", "weather_effects_frame_rate_entries", WeatherEffectsFrameRate.DEFAULT.name),
        ).forEach { (valuesName, entriesName, default) ->
            val values = ResourceXml.stringArray(strings, valuesName)
            val entries = ResourceXml.stringArray(strings, entriesName).map { it.removePrefix("@string/") }

            listOf(ResourceXml.strings(strings), ResourceXml.strings(russianStrings)).forEach { texts ->
                values.forEachIndexed { index, value ->
                    val marked = texts.getValue(entries[index]).endsWith("*")
                    assertEquals(value == default, marked, "'${entries[index]}' ($value)")
                }
            }
        }
    }

    // The place in the menu

    @Test
    fun `weather effects are listed under overlays and open their own screen`() {
        val entry =
            ResourceXml
                .elementsWithAttribute(overlaysScreen, "app:fragment")
                .firstOrNull { it.getAttribute("app:fragment") == fragmentClass }

        assertNotNull(entry, "$overlaysScreen has no entry for $fragmentClass")
        assertEquals("@string/appearance_weather_effects_title", entry?.getAttribute("app:title"))
        assertEquals("@string/appearance_weather_effects_summary", entry?.getAttribute("app:summary"))
        assertEquals("Weather effects", ResourceXml.strings(strings).getValue("appearance_weather_effects_title"))
    }

    @Test
    fun `weather effects sit right after the weather overlay in the list`() {
        val fragments =
            ResourceXml
                .elementsWithAttribute(overlaysScreen, "app:fragment")
                .map { it.getAttribute("app:fragment").substringAfterLast('.') }

        assertTrue("OverlaysWeatherFragment" in fragments)
        assertEquals(fragments.indexOf("OverlaysWeatherFragment") + 1, fragments.indexOf("OverlaysWeatherEffectsFragment"))
    }

    @Test
    fun `the screen is a fragment which loads the weather effects preferences`() {
        val code = mainSource(fragmentClass.replace('.', '/') + ".kt")

        assertTrue(code.isNotEmpty(), "No source file for $fragmentClass")
        assertTrue("class OverlaysWeatherEffectsFragment" in code)
        assertTrue("R.xml.settings_overlays_weather_effects" in code)
        assertTrue("MenuStateFragment()" in code)
    }

    // The strings

    @Test
    fun `every string the screens use is defined`() {
        val defined = ResourceXml.defaultStringNames()
        val used = stringReferences(screen) + stringReferences(overlaysScreen).filter { "weather_effects" in it }
        val listed =
            listOf("weather_effects_intensity_entries", "weather_effects_frame_rate_entries")
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
    fun `the values and defaults are not translated, as they are stored`() {
        val translatable = ResourceXml.translatableStringNames(strings)

        assertTrue("weather_effects_intensity_default" !in translatable)
        assertTrue("weather_effects_frame_rate_default" !in translatable)
        assertTrue(translatable.none { it.endsWith("_values") })
    }

    @Test
    fun `the strings are all in their own files`() {
        // New files, so that nothing here conflicts with the strings of upstream
        val names = ResourceXml.strings(strings).keys
        val others =
            ResourceXml
                .file("values")
                .listFiles { file ->
                    file.extension == "xml" && file.name != "strings_weather_effects.xml"
                }.orEmpty()

        others.forEach { file ->
            val clash = ResourceXml.strings("values/${file.name}").keys.intersect(names)
            assertTrue(clash.isEmpty(), "${file.name} also has ${clash.joinToString()}")
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

    // Names of the strings used by title, summary and default value attributes
    private fun stringReferences(path: String): Set<String> =
        listOf("app:title", "app:summary", "app:defaultValue")
            .flatMap { attribute ->
                ResourceXml.elementsWithAttribute(path, attribute).map { it.getAttribute(attribute) }
            }.filter { it.startsWith("@string/") }
            .map { it.removePrefix("@string/") }
            .toSet()
}
