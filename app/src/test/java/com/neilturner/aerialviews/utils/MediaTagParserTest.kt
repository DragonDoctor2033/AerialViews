package com.neilturner.aerialviews.utils

import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLEAR
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLOUDY
import com.neilturner.aerialviews.models.enums.WeatherCondition.FOG
import com.neilturner.aerialviews.models.enums.WeatherCondition.RAIN
import com.neilturner.aerialviews.models.enums.WeatherCondition.SNOW
import com.neilturner.aerialviews.models.enums.WeatherCondition.STORM
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import com.neilturner.aerialviews.utils.MediaTagParser.PathTags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

internal class MediaTagParserTest {
    private fun tags(
        path: String?,
        weather: Set<WeatherCondition> = emptySet(),
        timeOfDay: TimeOfDay = TimeOfDay.UNKNOWN,
    ) = assertEquals(PathTags(weather, timeOfDay), MediaTagParser.parsePath(path), "tags of $path")

    // Folders and file names

    @Test
    fun `weather folder then time of day folder then file`() {
        tags("Weather/rain/night/forest.mp4", setOf(RAIN), TimeOfDay.NIGHT)
    }

    @Test
    fun `weather folder then day folder then file`() {
        tags("Weather/snow/day/x.mp4", setOf(SNOW), TimeOfDay.DAY)
    }

    @Test
    fun `tags in a file name`() {
        tags("beach_clear_day.mp4", setOf(CLEAR), TimeOfDay.DAY)
    }

    @Test
    fun `absolute paths on a usb drive or a card`() {
        tags("/storage/1234-ABCD/Movies/Weather/Fog/Night/harbour.mkv", setOf(FOG), TimeOfDay.NIGHT)
        tags("/storage/emulated/0/Movies/Weather/Rain/sunrise.mp4", setOf(RAIN), TimeOfDay.SUNRISE)
    }

    @Test
    fun `windows separators`() {
        tags("Weather\\storm\\sunset\\coast.mp4", setOf(STORM), TimeOfDay.SUNSET)
    }

    @Test
    fun `a path with no tags`() {
        tags("Videos/Aerial/forest.mp4")
        tags("forest.mp4")
        tags("/storage/emulated/0/Movies/Aerial/Community/b1-1_FULL.mov")
    }

    @Test
    fun `nothing to read`() {
        tags(null)
        tags("")
        tags("   ")
        tags("/")
    }

    @Test
    fun `a file can be tagged for more than one kind of weather`() {
        tags("Weather/storm_rain_night.mp4", setOf(STORM, RAIN), TimeOfDay.NIGHT)
        tags("snow/clear/x.mp4", setOf(SNOW, CLEAR))
    }

    @Test
    fun `tags from folders and the file name are combined`() {
        tags("Weather/rain/mist_over_hills.mp4", setOf(RAIN, FOG))
    }

    // Words

    @Test
    fun `words are split on anything that is not a letter`() {
        assertEquals(listOf("rainy", "day", "ünder", "x"), MediaTagParser.words("Rainy-Day 2_Ünder.x"))
        assertEquals(listOf("a", "b", "c"), MediaTagParser.words("a/b\\c"))
        assertEquals(listOf("snow", "mp"), MediaTagParser.words("snow1.mp4"))
        assertEquals(emptyList<String>(), MediaTagParser.words("123 _-./"))
        assertEquals(emptyList<String>(), MediaTagParser.words(""))
    }

    @Test
    fun `digits underscores and dots separate words`() {
        tags("rain2.mp4", setOf(RAIN))
        tags("2_rain_1.mp4", setOf(RAIN))
        tags("clear.night.01.mp4", setOf(CLEAR), TimeOfDay.NIGHT)
        tags("sunny-day-4k.mp4", setOf(CLEAR), TimeOfDay.DAY)
        tags("sunny day 4k.mp4", setOf(CLEAR), TimeOfDay.DAY)
    }

    @Test
    fun `case does not matter`() {
        tags("WEATHER/RAIN/NIGHT/FOREST.MP4", setOf(RAIN), TimeOfDay.NIGHT)
        tags("weather/RaIn/NiGhT/forest.mp4", setOf(RAIN), TimeOfDay.NIGHT)
    }

    @Test
    fun `case folding does not depend on the language of the device`() {
        val default = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            tags("RAIN/FOG/NIGHT/SNOW/MIST/X.MP4", setOf(RAIN, FOG, SNOW), TimeOfDay.NIGHT)
        } finally {
            java.util.Locale.setDefault(default)
        }
    }

    @Test
    fun `only whole words count`() {
        tags("rainbow.mp4")
        tags("snowman/snowflakes.mp4")
        tags("stormy_seas.mp4")
        tags("foggyish.mp4")
        tags("clearance.mp4")
        tags("sunnyside.mp4")
        tags("birthday.mp4")
        tags("midnight.mp4")
        tags("sunrises.mp4")
    }

    @Test
    fun `words that are not tags are ignored`() {
        tags("hail.mp4")
        tags("weather.mp4")
        tags("sun/moon/cloud.mp4")
    }

    @TestFactory
    fun `every word of every kind of weather`(): List<DynamicTest> {
        val words =
            mapOf(
                CLEAR to listOf("clear", "sunny"),
                CLOUDY to listOf("cloudy", "clouds", "overcast"),
                RAIN to listOf("rain", "rainy", "drizzle"),
                SNOW to listOf("snow", "snowy"),
                STORM to listOf("storm", "thunder", "thunderstorm"),
                FOG to listOf("fog", "foggy", "mist"),
            )

        return words.flatMap { (condition, names) ->
            names.flatMap { name ->
                listOf(name, name.uppercase(), name.replaceFirstChar { it.uppercase() }).map { spelling ->
                    dynamicTest("folder $spelling is $condition") {
                        tags("Videos/$spelling/clip.mp4", setOf(condition))
                    }
                }
            }
        }
    }

    @Test
    fun `every kind of weather has a word`() {
        val found = listOf("clear", "cloudy", "rain", "snow", "storm", "fog").map { MediaTagParser.weatherIn(it) }

        assertEquals(WeatherCondition.entries.map { setOf(it) }, found)
    }

    @TestFactory
    fun `every word of every time of day`(): List<DynamicTest> =
        mapOf(
            "day" to TimeOfDay.DAY,
            "night" to TimeOfDay.NIGHT,
            "sunrise" to TimeOfDay.SUNRISE,
            "sunset" to TimeOfDay.SUNSET,
        ).flatMap { (word, timeOfDay) ->
            listOf(word, word.uppercase()).map { spelling ->
                dynamicTest("folder $spelling is $timeOfDay") {
                    tags("Videos/$spelling/clip.mp4", timeOfDay = timeOfDay)
                }
            }
        }

    // Time of day

    @Test
    fun `the name closest to the file decides the time of day`() {
        tags("Day/Weather/rain/night/forest.mp4", setOf(RAIN), TimeOfDay.NIGHT)
        tags("night/sunset_beach.mp4", timeOfDay = TimeOfDay.SUNSET)
        tags("Weather/clear/day/sunrise.mp4", setOf(CLEAR), TimeOfDay.SUNRISE)
    }

    @Test
    fun `a time of day folder applies to the files inside it`() {
        tags("night/forest.mp4", timeOfDay = TimeOfDay.NIGHT)
        tags("Weather/rain/day/forest/lake.mp4", setOf(RAIN), TimeOfDay.DAY)
    }

    @Test
    fun `a name with two different times of day says nothing`() {
        tags("day_to_night.mp4")
        tags("Day/day_to_night.mp4")
        tags("sunrise-sunset.mp4")
    }

    @Test
    fun `the same time of day twice is still that time of day`() {
        tags("day_day_1.mp4", timeOfDay = TimeOfDay.DAY)
    }

    @Test
    fun `weather words do not set a time of day`() {
        tags("clear.mp4", setOf(CLEAR))
        tags("storm/rain.mp4", setOf(STORM, RAIN))
    }

    // Locations that are urls

    @Test
    fun `only the path of a url is read, not the host, login, query or fragment`() {
        tags(
            "https://snow:rain@sunny.example.com:8443/remote.php/dav/files/me/Weather/fog/day/x.mp4?token=storm#clouds",
            setOf(FOG),
            TimeOfDay.DAY,
        )
        tags("http://storm.example.com/videos/plain.mp4")
        tags("smb://night:fog@nas/Videos/Weather/snow/x.mp4?domain=STORM&enc=false&dialects=SMB_3_1_1", setOf(SNOW))
    }

    @Test
    fun `a url without a path has nothing to read`() {
        tags("https://rain.example.com")
        tags("https://rain.example.com/")
        tags("https://rain.example.com?x=/snow/")
    }

    @Test
    fun `percent encoded names in urls are decoded`() {
        tags("https://example.com/Weather/%72ain/night%20time/x.mp4", setOf(RAIN), TimeOfDay.NIGHT)
        tags("https://example.com/%E2%98%82/sn%6Fw/x.mp4", setOf(SNOW))
    }

    @Test
    fun `a url with bad percent encoding does not fail`() {
        tags("https://example.com/100%/rain/x.mp4", setOf(RAIN))
        tags("https://example.com/%zz/fog/x.mp4", setOf(FOG))
    }

    @Test
    fun `file urls`() {
        tags("file:///storage/emulated/0/Movies/Weather/Clouds/x.mp4", setOf(CLOUDY))
    }

    @Test
    fun `a file path is never treated as an encoded url`() {
        // A real file name can hold a percent sign, it must not be decoded or fail
        tags("/storage/emulated/0/Movies/100%/rain/x%41.mp4", setOf(RAIN))
        tags("/Movies/a?b#c/fog/x.mp4", setOf(FOG))
    }

    // Filling in media

    @Test
    fun `tags are added to media that has none`() {
        val metadata = AerialMediaMetadata()

        MediaTagParser.applyPathTags(metadata, "Weather/rain/night/forest.mp4")

        assertEquals(setOf(RAIN), metadata.weather)
        assertEquals(TimeOfDay.NIGHT, metadata.timeOfDay)
    }

    @Test
    fun `a time of day that is already known is kept`() {
        val metadata = AerialMediaMetadata(timeOfDay = TimeOfDay.SUNSET)

        MediaTagParser.applyPathTags(metadata, "Weather/rain/night/forest.mp4")

        assertEquals(setOf(RAIN), metadata.weather)
        assertEquals(TimeOfDay.SUNSET, metadata.timeOfDay)
    }

    @Test
    fun `weather is added to the tags already there`() {
        val metadata = AerialMediaMetadata(weather = setOf(SNOW))

        MediaTagParser.applyPathTags(metadata, "Weather/rain/forest.mp4")

        assertEquals(setOf(SNOW, RAIN), metadata.weather)
    }

    @Test
    fun `media with nothing to read is left alone`() {
        val metadata = AerialMediaMetadata(timeOfDay = TimeOfDay.DAY, weather = setOf(FOG))

        MediaTagParser.applyPathTags(metadata, "Videos/forest.mp4")
        MediaTagParser.applyPathTags(metadata, null)

        assertEquals(setOf(FOG), metadata.weather)
        assertEquals(TimeOfDay.DAY, metadata.timeOfDay)
    }

    @Test
    fun `media with nothing to read stays untagged`() {
        val metadata = AerialMediaMetadata()

        MediaTagParser.applyPathTags(metadata, "Videos/forest.mp4")

        assertEquals(emptySet<WeatherCondition>(), metadata.weather)
        assertEquals(TimeOfDay.UNKNOWN, metadata.timeOfDay)
    }
}
