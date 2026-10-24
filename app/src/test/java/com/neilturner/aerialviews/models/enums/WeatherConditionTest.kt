package com.neilturner.aerialviews.models.enums

import com.neilturner.aerialviews.models.enums.WeatherCondition.CLEAR
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLOUDY
import com.neilturner.aerialviews.models.enums.WeatherCondition.FOG
import com.neilturner.aerialviews.models.enums.WeatherCondition.RAIN
import com.neilturner.aerialviews.models.enums.WeatherCondition.SNOW
import com.neilturner.aerialviews.models.enums.WeatherCondition.STORM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class WeatherConditionTest {
    private fun assertIdsMapTo(
        expected: WeatherCondition?,
        ids: Iterable<Int>,
    ) = ids.forEach { id ->
        assertEquals(expected, WeatherCondition.fromOpenWeatherId(id), "OpenWeather id $id")
    }

    @Test
    fun `thunderstorm ids are storms`() = assertIdsMapTo(STORM, 200..299)

    @Test
    fun `drizzle ids are rain`() = assertIdsMapTo(RAIN, 300..399)

    @Test
    fun `rain ids are rain`() = assertIdsMapTo(RAIN, 500..599)

    @Test
    fun `snow ids are snow`() = assertIdsMapTo(SNOW, 600..699)

    @Test
    fun `tornado is a storm`() = assertIdsMapTo(STORM, listOf(781))

    @Test
    fun `every other atmosphere id is fog`() = assertIdsMapTo(FOG, (700..799).filter { it != 781 })

    @Test
    fun `clear sky and few clouds are clear`() = assertIdsMapTo(CLEAR, listOf(800, 801))

    @Test
    fun `scattered, broken and overcast clouds are cloudy`() = assertIdsMapTo(CLOUDY, listOf(802, 803, 804))

    @Test
    fun `ids that are not conditions are unknown`() {
        assertIdsMapTo(null, listOf(Int.MIN_VALUE, -1, 0, 100, 199, 805, 899, 900, 1000, Int.MAX_VALUE))
        assertIdsMapTo(null, 400..499)
    }

    @Test
    fun `thunderstorms fall back to rain and then cloudy`() {
        assertEquals(listOf(RAIN, CLOUDY), STORM.fallbacks)
        assertEquals(listOf(STORM, RAIN, CLOUDY), STORM.lookupOrder)
    }

    @Test
    fun `rain, snow and fog fall back to cloudy`() {
        assertEquals(listOf(CLOUDY), RAIN.fallbacks)
        assertEquals(listOf(CLOUDY), SNOW.fallbacks)
        assertEquals(listOf(CLOUDY), FOG.fallbacks)
        assertEquals(listOf(RAIN, CLOUDY), RAIN.lookupOrder)
        assertEquals(listOf(SNOW, CLOUDY), SNOW.lookupOrder)
        assertEquals(listOf(FOG, CLOUDY), FOG.lookupOrder)
    }

    @Test
    fun `clear and cloudy have no fallback`() {
        assertEquals(emptyList<WeatherCondition>(), CLEAR.fallbacks)
        assertEquals(emptyList<WeatherCondition>(), CLOUDY.fallbacks)
        assertEquals(listOf(CLEAR), CLEAR.lookupOrder)
        assertEquals(listOf(CLOUDY), CLOUDY.lookupOrder)
    }

    @Test
    fun `names are read without regard to case or spaces`() {
        assertEquals(RAIN, WeatherCondition.fromName("RAIN"))
        assertEquals(SNOW, WeatherCondition.fromName("snow"))
        assertEquals(STORM, WeatherCondition.fromName(" Storm "))
    }

    @Test
    fun `the off choice of the override and other text are not conditions`() {
        assertNull(WeatherCondition.fromName("OFF"))
        assertNull(WeatherCondition.fromName(""))
        assertNull(WeatherCondition.fromName("  "))
        assertNull(WeatherCondition.fromName(null))
        assertNull(WeatherCondition.fromName("hail"))
        assertNull(WeatherCondition.fromName("rain,snow"))
    }

    @Test
    fun `every choice of the override setting is a condition or off`() {
        val choices = listOf("OFF", "CLEAR", "CLOUDY", "RAIN", "SNOW", "STORM", "FOG")

        assertEquals(
            listOf(null, CLEAR, CLOUDY, RAIN, SNOW, STORM, FOG),
            choices.map { WeatherCondition.fromName(it) },
        )
    }

    @Test
    fun `an empty set is stored as nothing`() {
        assertNull(WeatherCondition.encode(emptySet()))
    }

    @Test
    fun `conditions are stored as names in a fixed order`() {
        assertEquals("RAIN", WeatherCondition.encode(setOf(RAIN)))
        assertEquals("CLEAR,RAIN,SNOW", WeatherCondition.encode(setOf(SNOW, CLEAR, RAIN)))
        assertEquals(
            WeatherCondition.encode(linkedSetOf(FOG, STORM)),
            WeatherCondition.encode(linkedSetOf(STORM, FOG)),
        )
    }

    @Test
    fun `stored conditions are read back`() {
        assertEquals(setOf(RAIN, SNOW), WeatherCondition.decode("RAIN,SNOW"))
        assertEquals(setOf(CLEAR), WeatherCondition.decode("CLEAR"))

        WeatherCondition.entries.forEach { condition ->
            assertEquals(setOf(condition), WeatherCondition.decode(WeatherCondition.encode(setOf(condition))))
        }
        assertEquals(
            WeatherCondition.entries.toSet(),
            WeatherCondition.decode(WeatherCondition.encode(WeatherCondition.entries.toSet())),
        )
    }

    @Test
    fun `rows without tags are read as untagged`() {
        assertEquals(emptySet<WeatherCondition>(), WeatherCondition.decode(null))
        assertEquals(emptySet<WeatherCondition>(), WeatherCondition.decode(""))
    }

    @Test
    fun `stored names that are not known are skipped`() {
        assertEquals(setOf(FOG), WeatherCondition.decode("HAIL,FOG, ,TORNADO"))
        assertEquals(emptySet<WeatherCondition>(), WeatherCondition.decode("HAIL"))
    }
}
