package com.neilturner.aerialviews.services.weather

import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLEAR
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLOUDY
import com.neilturner.aerialviews.models.enums.WeatherCondition.FOG
import com.neilturner.aerialviews.models.enums.WeatherCondition.RAIN
import com.neilturner.aerialviews.models.enums.WeatherCondition.SNOW
import com.neilturner.aerialviews.models.enums.WeatherCondition.STORM
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WeatherMediaFilterTest {
    private fun video(
        name: String,
        vararg tags: WeatherCondition,
    ) = AerialMedia(
        uri = mockk(relaxed = true, name = name),
        type = AerialMediaType.VIDEO,
        metadata = AerialMediaMetadata(weather = tags.toSet()),
    )

    private fun photo(name: String) =
        AerialMedia(
            uri = mockk(relaxed = true, name = name),
            type = AerialMediaType.IMAGE,
        )

    private fun filter(
        condition: WeatherCondition,
        vararg media: AerialMedia,
    ) = WeatherMediaFilter.filter(media.toList(), condition)

    // Videos tagged for the current weather

    @Test
    fun `videos tagged for the current weather are picked`() {
        val rain1 = video("rain1", RAIN)
        val rain2 = video("rain2", RAIN)
        val snow = video("snow", SNOW)
        val cloudy = video("cloudy", CLOUDY)
        val plain = video("plain")

        assertEquals(listOf(rain1, rain2), filter(RAIN, rain1, snow, plain, rain2, cloudy))
    }

    @Test
    fun `a video tagged for several kinds of weather suits each of them`() {
        val both = video("both", RAIN, SNOW)
        val rain = video("rain", RAIN)
        val plain = video("plain")

        assertEquals(listOf(both, rain), filter(RAIN, both, rain, plain))
        assertEquals(listOf(both), filter(SNOW, both, rain, plain))
    }

    @Test
    fun `the order of the playlist is kept`() {
        val a = video("a", FOG)
        val b = video("b", FOG)
        val c = video("c", FOG)

        assertEquals(listOf(c, a, b), filter(FOG, c, video("x"), a, video("y", RAIN), b))
    }

    @Test
    fun `tagged videos win over untagged ones`() {
        val snow = video("snow", SNOW)
        val plain = video("plain")

        assertEquals(listOf(snow), filter(SNOW, plain, snow))
    }

    // Fallbacks

    @Test
    fun `a storm falls back to rain`() {
        val rain = video("rain", RAIN)
        val cloudy = video("cloudy", CLOUDY)
        val plain = video("plain")

        // The first set that is not empty wins, so cloudy is not mixed in
        assertEquals(listOf(rain), filter(STORM, cloudy, rain, plain))
    }

    @Test
    fun `a storm falls back to cloudy when there is no rain either`() {
        val cloudy = video("cloudy", CLOUDY)
        val snow = video("snow", SNOW)
        val plain = video("plain")

        assertEquals(listOf(cloudy), filter(STORM, snow, plain, cloudy))
    }

    @Test
    fun `a storm prefers storm videos to rain and cloudy`() {
        val storm = video("storm", STORM)
        val rain = video("rain", RAIN)
        val cloudy = video("cloudy", CLOUDY)

        assertEquals(listOf(storm), filter(STORM, rain, cloudy, storm))
    }

    @Test
    fun `a video tagged for storm and rain is a storm video`() {
        val both = video("both", STORM, RAIN)
        val rainOnly = video("rainOnly", RAIN)

        assertEquals(listOf(both), filter(STORM, rainOnly, both))
    }

    @Test
    fun `rain falls back to cloudy`() {
        val cloudy = video("cloudy", CLOUDY)

        assertEquals(listOf(cloudy), filter(RAIN, video("plain"), cloudy, video("snow", SNOW)))
    }

    @Test
    fun `snow falls back to cloudy`() {
        val cloudy = video("cloudy", CLOUDY)

        assertEquals(listOf(cloudy), filter(SNOW, video("plain"), cloudy, video("rain", RAIN)))
    }

    @Test
    fun `fog falls back to cloudy`() {
        val cloudy = video("cloudy", CLOUDY)

        assertEquals(listOf(cloudy), filter(FOG, video("plain"), cloudy, video("rain", RAIN)))
    }

    @Test
    fun `fallbacks only go one way`() {
        val storm = video("storm", STORM)
        val rain = video("rain", RAIN)
        val plain = video("plain")

        // Rain never falls back to storm, and snow and fog never fall back to rain
        assertEquals(listOf(plain), filter(RAIN, storm, plain))
        assertEquals(listOf(plain), filter(SNOW, rain, plain))
        assertEquals(listOf(plain), filter(FOG, rain, plain))
        assertEquals(listOf(plain), filter(FOG, video("snow", SNOW), plain))
    }

    @Test
    fun `clear has no fallback`() {
        val plain = video("plain")

        assertEquals(listOf(plain), filter(CLEAR, video("cloudy", CLOUDY), plain))
    }

    @Test
    fun `cloudy has no fallback`() {
        val plain = video("plain")

        assertEquals(listOf(plain), filter(CLOUDY, video("clear", CLEAR), plain))
    }

    // Untagged videos

    @Test
    fun `snow clips never show when it is clear`() {
        val snow1 = video("snow1", SNOW)
        val snow2 = video("snow2", SNOW, STORM)
        val plain = video("plain")

        assertEquals(listOf(plain), filter(CLEAR, snow1, plain, snow2))
    }

    @Test
    fun `only untagged videos are used when nothing is tagged for the weather`() {
        val plain1 = video("plain1")
        val plain2 = video("plain2")

        assertEquals(
            listOf(plain1, plain2),
            filter(RAIN, plain1, video("snow", SNOW), video("clear", CLEAR), plain2),
        )
    }

    @Test
    fun `everything is kept when every video is tagged for other weather`() {
        val snow = video("snow", SNOW)
        val fog = video("fog", FOG)

        assertEquals(listOf(snow, fog), filter(CLEAR, snow, fog))
    }

    @Test
    fun `everything is kept when there is only one video and it is tagged for other weather`() {
        val snow = video("snow", SNOW)

        assertEquals(listOf(snow), filter(CLEAR, snow))
    }

    @Test
    fun `a playlist that has no tags at all is left as it is`() {
        val plain1 = video("plain1")
        val plain2 = video("plain2")

        WeatherCondition.entries.forEach { condition ->
            assertEquals(listOf(plain1, plain2), filter(condition, plain1, plain2), "for $condition")
        }
    }

    // Photos and empty playlists

    @Test
    fun `photos are always kept`() {
        val rain = video("rain", RAIN)
        val snow = video("snow", SNOW)
        val picture = photo("picture")

        // Videos for the weather
        assertEquals(listOf(rain, picture), filter(RAIN, rain, snow, picture))
        // Untagged videos
        val plain = video("plain")
        assertEquals(listOf(plain, picture), filter(CLEAR, snow, plain, picture))
        // Nothing suits
        assertEquals(listOf(snow, picture), filter(CLEAR, snow, picture))
    }

    @Test
    fun `photos do not count as untagged videos`() {
        val snow = video("snow", SNOW)
        val picture = photo("picture")

        assertEquals(listOf(snow, picture), filter(CLEAR, snow, picture))
    }

    @Test
    fun `a playlist of only photos is left as it is`() {
        val pictures = listOf(photo("a"), photo("b"))

        assertEquals(pictures, WeatherMediaFilter.filter(pictures, RAIN))
    }

    @Test
    fun `an empty playlist stays empty`() {
        assertEquals(emptyList<AerialMedia>(), WeatherMediaFilter.filter(emptyList(), RAIN))
    }

    @Test
    fun `the playlist given is not changed`() {
        val rain = video("rain", RAIN)
        val snow = video("snow", SNOW)
        val original = mutableListOf(snow, rain)

        WeatherMediaFilter.filter(original, RAIN)

        assertEquals(listOf(snow, rain), original)
    }

    @Test
    fun `a playlist with videos is never emptied`() {
        val tagSets =
            listOf(
                emptyList(),
                listOf(CLEAR),
                listOf(CLOUDY),
                listOf(RAIN),
                listOf(SNOW),
                listOf(STORM),
                listOf(FOG),
                listOf(RAIN, SNOW),
            )

        // Every playlist of one or two videos made from the tag sets above, for every condition
        val playlists = tagSets.map { listOf(it) } + tagSets.flatMap { first -> tagSets.map { second -> listOf(first, second) } }

        WeatherCondition.entries.forEach { condition ->
            playlists.forEach { tags ->
                val media = tags.mapIndexed { index, set -> video("v$index", *set.toTypedArray()) }

                val result = WeatherMediaFilter.filter(media, condition)

                val reason = "$condition with videos tagged $tags"
                assertTrue(result.isNotEmpty(), reason)
                assertEquals(media.filter { it in result }, result, "$reason keeps the order")
            }
        }
    }

    // Cache key

    @Test
    fun `nothing is added to the cache key while matching is off`() {
        assertNull(WeatherMediaFilter.cacheKey(enabled = false, condition = null))
        assertNull(WeatherMediaFilter.cacheKey(enabled = false, condition = RAIN))
    }

    @Test
    fun `the cache key names the condition`() {
        assertEquals("weather:RAIN", WeatherMediaFilter.cacheKey(enabled = true, condition = RAIN))
        assertEquals("weather:unknown", WeatherMediaFilter.cacheKey(enabled = true, condition = null))
    }

    @Test
    fun `every condition has its own cache key`() {
        val keys =
            WeatherCondition.entries.map { WeatherMediaFilter.cacheKey(enabled = true, condition = it) } +
                WeatherMediaFilter.cacheKey(enabled = true, condition = null)

        assertEquals(keys.size, keys.toSet().size)
        assertFalse(keys.any { it == null })
    }
}
