package com.neilturner.aerialviews.providers.custom

import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLEAR
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLOUDY
import com.neilturner.aerialviews.models.enums.WeatherCondition.FOG
import com.neilturner.aerialviews.models.enums.WeatherCondition.RAIN
import com.neilturner.aerialviews.models.enums.WeatherCondition.SNOW
import com.neilturner.aerialviews.models.enums.WeatherCondition.STORM
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CustomFeedWeatherTest {
    // Mirrors the production JsonHelper configuration used to parse custom feeds
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private fun weatherOf(weatherField: String): Set<WeatherCondition> {
        val body = """{ "assets": [ { "url-1080-SDR": "https://example.com/v.mp4", $weatherField } ] }"""
        return json
            .decodeFromString<FeedVideos>(body)
            .assets!!
            .single()
            .weatherTags()
    }

    @Test
    fun `a string names one kind of weather`() {
        assertEquals(setOf(RAIN), weatherOf(""" "weather": "rain" """))
    }

    @Test
    fun `an array names several kinds of weather`() {
        assertEquals(setOf(RAIN, SNOW), weatherOf(""" "weather": ["rain", "snow"] """))
        assertEquals(setOf(FOG), weatherOf(""" "weather": ["fog"] """))
    }

    @Test
    fun `the same words as file names are understood`() {
        assertEquals(setOf(CLEAR), weatherOf(""" "weather": "sunny" """))
        assertEquals(setOf(CLOUDY), weatherOf(""" "weather": ["clouds"] """))
        assertEquals(setOf(CLOUDY), weatherOf(""" "weather": "overcast" """))
        assertEquals(setOf(RAIN), weatherOf(""" "weather": "drizzle" """))
        assertEquals(setOf(SNOW), weatherOf(""" "weather": "snowy" """))
        assertEquals(setOf(STORM), weatherOf(""" "weather": "thunderstorm" """))
        assertEquals(setOf(STORM), weatherOf(""" "weather": "thunder" """))
        assertEquals(setOf(FOG), weatherOf(""" "weather": "mist" """))
    }

    @Test
    fun `case does not matter`() {
        assertEquals(setOf(STORM), weatherOf(""" "weather": "STORM" """))
        assertEquals(setOf(RAIN, FOG), weatherOf(""" "weather": ["Rain", "FoGgY"] """))
    }

    @Test
    fun `every kind of weather can be named`() {
        val names = WeatherCondition.entries.joinToString(", ") { "\"${it.name}\"" }

        assertEquals(WeatherCondition.entries.toSet(), weatherOf(""" "weather": [$names] """))
    }

    @Test
    fun `a string can name several kinds of weather`() {
        assertEquals(setOf(RAIN, SNOW), weatherOf(""" "weather": "rain, snow" """))
        assertEquals(setOf(RAIN, SNOW), weatherOf(""" "weather": "rain/snow" """))
    }

    @Test
    fun `a feed without the field is untagged`() {
        val body = """{ "assets": [ { "url-1080-SDR": "https://example.com/v.mp4" } ] }"""

        val asset = json.decodeFromString<FeedVideos>(body).assets!!.single()

        assertEquals(emptySet<WeatherCondition>(), asset.weatherTags())
    }

    @Test
    fun `words that are not weather are ignored`() {
        assertEquals(emptySet<WeatherCondition>(), weatherOf(""" "weather": "hail" """))
        assertEquals(emptySet<WeatherCondition>(), weatherOf(""" "weather": "" """))
        assertEquals(setOf(SNOW), weatherOf(""" "weather": ["hail", "snow", "sleet"] """))
        assertEquals(emptySet<WeatherCondition>(), weatherOf(""" "weather": [] """))
    }

    @Test
    fun `values of the wrong kind are ignored and never stop the feed from loading`() {
        assertEquals(emptySet<WeatherCondition>(), weatherOf(""" "weather": null """))
        assertEquals(emptySet<WeatherCondition>(), weatherOf(""" "weather": 5 """))
        assertEquals(emptySet<WeatherCondition>(), weatherOf(""" "weather": true """))
        assertEquals(emptySet<WeatherCondition>(), weatherOf(""" "weather": {"rain": true} """))
        assertEquals(setOf(RAIN), weatherOf(""" "weather": [null, 5, {"snow": 1}, "rain"] """))
    }

    @Test
    fun `an unquoted word is accepted like the rest of the lenient feed format`() {
        assertEquals(setOf(RAIN), weatherOf(""" "weather": rain """))
    }

    @Test
    fun `tags are read per entry`() {
        val body =
            """
            {
              "assets": [
                { "url-1080-SDR": "https://example.com/a.mp4", "weather": "rain" },
                { "url-1080-SDR": "https://example.com/b.mp4" },
                { "url-1080-SDR": "https://example.com/c.mp4", "weather": ["snow", "fog"] }
              ]
            }
            """.trimIndent()

        val assets = json.decodeFromString<FeedVideos>(body).assets!!

        assertEquals(
            listOf(setOf(RAIN), emptySet(), setOf(SNOW, FOG)),
            assets.map { it.weatherTags() },
        )
    }

    @Test
    fun `the other fields of an entry are not affected`() {
        val body =
            """
            {
              "assets": [
                {
                  "accessibilityLabel": "Rain on the lake",
                  "timeOfDay": "night",
                  "scene": "nature",
                  "weather": ["rain", "storm"],
                  "url-1080-SDR": "https://example.com/v.mp4",
                  "url-audio": "https://example.com/a.m4a"
                }
              ],
              "photos": [ { "url-1080": "https://example.com/p.jpg", "title": "Photo" } ]
            }
            """.trimIndent()

        val feed = json.decodeFromString<FeedVideos>(body)
        val asset = feed.assets!!.single()

        assertEquals(setOf(RAIN, STORM), asset.weatherTags())
        assertEquals("Rain on the lake", asset.description)
        assertEquals("night", asset.timeOfDay)
        assertEquals("nature", asset.scene)
        assertEquals("https://example.com/a.m4a", asset.audioUrl)
        assertEquals("Photo", feed.photos!!.single().title)
    }
}
