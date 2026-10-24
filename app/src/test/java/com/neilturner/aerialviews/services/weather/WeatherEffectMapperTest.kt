package com.neilturner.aerialviews.services.weather

import com.neilturner.aerialviews.data.network.JsonHelper
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherEffectKind
import com.neilturner.aerialviews.testutils.OpenWeatherSamples
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WeatherEffectMapperTest {
    private val noon = 1_700_000_000L

    private fun state(
        id: Int,
        rainMm: Double? = null,
        snowMm: Double? = null,
        clouds: Int? = null,
    ): WeatherEffectState =
        WeatherEffectMapper.map(
            WeatherSnapshot(conditionId = id, icon = "01d", rainMmPerHour = rainMm, snowMmPerHour = snowMm, cloudsPercent = clouds),
            noon,
        )

    // Kinds

    @Test
    fun `thunderstorms are storms`() {
        listOf(200, 201, 202, 210, 211, 212, 221, 230, 231, 232).forEach { id ->
            assertEquals(WeatherEffectKind.STORM, state(id).kind, "id $id")
        }
    }

    @Test
    fun `drizzle and rain are rain`() {
        listOf(300, 301, 302, 310, 311, 312, 313, 314, 321, 500, 501, 502, 503, 504, 511, 520, 521, 522, 531).forEach { id ->
            assertEquals(WeatherEffectKind.RAIN, state(id).kind, "id $id")
        }
    }

    @Test
    fun `snow and sleet are snow`() {
        listOf(600, 601, 602, 611, 612, 613, 615, 616, 620, 621, 622).forEach { id ->
            assertEquals(WeatherEffectKind.SNOW, state(id).kind, "id $id")
        }
    }

    @Test
    fun `mist, smoke, haze, dust, fog, sand and ash are fog`() {
        listOf(701, 711, 721, 731, 741, 751, 761, 762).forEach { id ->
            assertEquals(WeatherEffectKind.FOG, state(id).kind, "id $id")
        }
    }

    @Test
    fun `a tornado is a storm and squalls are dark clouds`() {
        assertEquals(WeatherEffectKind.STORM, state(781).kind)
        assertEquals(1f, state(781).intensity)
        assertEquals(WeatherEffectKind.OVERCAST, state(771).kind)
    }

    @Test
    fun `an atmosphere condition which is not known is fog`() {
        assertEquals(WeatherEffectKind.FOG, state(799).kind)
    }

    @Test
    fun `clear sky and few clouds draw nothing`() {
        assertEquals(WeatherEffectState.NONE.kind, state(800).kind)
        assertEquals(WeatherEffectKind.NONE, state(801).kind)
        assertEquals(0f, state(800).intensity)
        assertEquals(0f, state(801).intensity)
    }

    @Test
    fun `few clouds draw nothing, however much of the sky the answer says is covered`() {
        assertEquals(WeatherEffectKind.NONE, state(801, clouds = 20).kind)
        assertEquals(WeatherEffectKind.NONE, state(801, clouds = 90).kind)
        assertEquals(WeatherEffectKind.NONE, state(800, clouds = 0).kind)
    }

    @Test
    fun `scattered clouds are a very light overcast tone`() {
        val scattered = state(802)

        assertEquals(WeatherEffectKind.OVERCAST, scattered.kind)
        assertTrue(scattered.intensity in 0.05f..0.25f, "intensity ${scattered.intensity}")
    }

    @Test
    fun `broken and overcast clouds are overcast, the more the thicker`() {
        val broken = state(803)
        val overcast = state(804)

        assertEquals(WeatherEffectKind.OVERCAST, broken.kind)
        assertEquals(WeatherEffectKind.OVERCAST, overcast.kind)
        assertTrue(broken.intensity > state(802).intensity)
        assertTrue(overcast.intensity > broken.intensity)
        assertEquals(0.50f, broken.intensity, 0.001f)
        assertEquals(0.85f, overcast.intensity, 0.001f)
    }

    @Test
    fun `the cover of the sky moves the intensity of the clouds`() {
        // Half from the id, half from how much of the sky is covered
        assertEquals((0.85f + 0.9f) / 2f, state(804, clouds = 100).intensity, 0.001f)
        assertEquals((0.85f + 0.6f) / 2f, state(804, clouds = 85).intensity, 0.001f)
        assertEquals((0.50f + 0.2f) / 2f, state(803, clouds = 50).intensity, 0.001f)
        assertTrue(state(803, clouds = 90).intensity > state(803, clouds = 55).intensity)
    }

    @Test
    fun `scattered clouds with hardly any cover are the lightest tone there is`() {
        // 0.15 from the id and nothing from the cover of the sky, which is still above the smallest tone worth drawing
        val state = state(802, clouds = 5)

        assertEquals(WeatherEffectKind.OVERCAST, state.kind)
        assertEquals(0.075f, state.intensity, 0.001f)
    }

    @Test
    fun `ids which are not weather draw nothing`() {
        listOf(0, -1, 100, 199, 900, 906, 951, 1000, 8000).forEach { id ->
            assertEquals(WeatherEffectState.NONE.kind, state(id).kind, "id $id")
            assertEquals(0f, state(id).intensity, "id $id")
        }
    }

    // Intensity from the id

    @Test
    fun `drizzle is lighter than rain, and rain gets heavier with the id`() {
        assertTrue(state(300).intensity < state(301).intensity)
        assertTrue(state(301).intensity < state(302).intensity)
        assertTrue(state(302).intensity < state(500).intensity)
        assertTrue(state(500).intensity < state(501).intensity)
        assertTrue(state(501).intensity < state(502).intensity)
        assertTrue(state(502).intensity < state(503).intensity)
        assertTrue(state(503).intensity < state(504).intensity)
        assertEquals(1f, state(504).intensity)
    }

    @Test
    fun `snow gets heavier from light snow to heavy snow`() {
        assertTrue(state(600).intensity < state(601).intensity)
        assertTrue(state(601).intensity < state(602).intensity)
    }

    @Test
    fun `a heavy thunderstorm is stronger than a light one`() {
        assertTrue(state(210).intensity < state(211).intensity)
        assertTrue(state(211).intensity < state(212).intensity)
        assertTrue(state(200).intensity < state(202).intensity)
    }

    @Test
    fun `fog is thicker than mist or haze`() {
        assertTrue(state(741).intensity > state(701).intensity)
        assertTrue(state(741).intensity > state(721).intensity)
    }

    @Test
    fun `every intensity is between 0 and 1`() {
        (200..232).plus(300..321).plus(500..531).plus(600..622).plus(700..781).plus(800..804).forEach { id ->
            listOf(null, 0.0, 0.3, 4.0, 80.0).forEach { mm ->
                val state = state(id, rainMm = mm, snowMm = mm, clouds = 100)
                assertTrue(state.intensity in 0f..1f, "id $id with $mm mm: ${state.intensity}")
            }
        }
    }

    @Test
    fun `any kind except none has an intensity worth drawing`() {
        (200..232).plus(300..321).plus(500..531).plus(600..622).plus(700..781).plus(800..804).forEach { id ->
            val state = state(id)
            if (state.kind != WeatherEffectKind.NONE) {
                assertTrue(state.intensity >= 0.05f, "id $id: ${state.intensity}")
            } else {
                assertEquals(0f, state.intensity, "id $id")
            }
        }
    }

    // Intensity from the volume in mm per hour

    @Test
    fun `without a volume the id alone gives the intensity`() {
        assertEquals(0.30f, state(500).intensity, 0.0001f)
        assertEquals(0.30f, state(500, rainMm = null).intensity, 0.0001f)
        assertEquals(0.30f, state(500, rainMm = 0.0).intensity, 0.0001f)
    }

    @Test
    fun `more millimeters of rain make heavier rain for the same id`() {
        val drizzle = state(500, rainMm = 0.1).intensity
        val some = state(500, rainMm = 2.5).intensity
        val heavy = state(500, rainMm = 8.0).intensity
        val downpour = state(500, rainMm = 40.0).intensity

        assertTrue(drizzle < some, "$drizzle < $some")
        assertTrue(some < heavy, "$some < $heavy")
        assertTrue(heavy < downpour, "$heavy < $downpour")
    }

    @Test
    fun `the volume and the id are mixed half and half`() {
        // 500 is 0.30, and 5 mm an hour is 0.65
        assertEquals((0.30f + 0.65f) / 2f, state(500, rainMm = 5.0).intensity, 0.0001f)
        // 0.5 mm an hour is 0.25, between the points of the table
        assertEquals((0.30f + 0.25f) / 2f, state(500, rainMm = 0.5).intensity, 0.0001f)
        // Between 2.5 mm (0.50) and 5 mm (0.65), a fifth of the way
        assertEquals((0.30f + 0.53f) / 2f, state(500, rainMm = 3.0).intensity, 0.0001f)
    }

    @Test
    fun `a very heavy volume lifts a light id but not to the top`() {
        val state = state(500, rainMm = 100.0)

        assertEquals((0.30f + 1f) / 2f, state.intensity, 0.0001f)
    }

    @Test
    fun `drizzle ids use the rain volume too`() {
        assertEquals((0.12f + 0.35f) / 2f, state(300, rainMm = 1.0).intensity, 0.0001f)
    }

    @Test
    fun `storms use the rain volume`() {
        assertEquals((0.60f + 0.75f) / 2f, state(211, rainMm = 7.6).intensity, 0.0001f)
        assertEquals(WeatherEffectKind.STORM, state(211, rainMm = 7.6).kind)
    }

    @Test
    fun `snow uses the snow volume and ignores rain`() {
        // 601 is 0.55 and 1 mm of water an hour is 0.60
        assertEquals((0.55f + 0.60f) / 2f, state(601, snowMm = 1.0).intensity, 0.0001f)
        assertEquals(0.55f, state(601, rainMm = 20.0).intensity, 0.0001f)
    }

    @Test
    fun `snow is counted in water, so half a millimeter is already a good snowfall`() {
        // The same half millimeter is 0.25 as rain and 0.40 as snow
        assertEquals((0.30f + 0.25f) / 2f, state(500, rainMm = 0.5).intensity, 0.0001f)
        assertEquals((0.55f + 0.40f) / 2f, state(601, snowMm = 0.5).intensity, 0.0001f)
    }

    @Test
    fun `rain ids ignore the snow volume`() {
        assertEquals(0.30f, state(500, snowMm = 0.5).intensity, 0.0001f)
    }

    @Test
    fun `a volume does not change the weather the id says`() {
        assertEquals(WeatherEffectKind.NONE, state(800, rainMm = 5.0).kind)
        assertEquals(WeatherEffectKind.OVERCAST, state(804, rainMm = 5.0).kind)
        assertEquals(WeatherEffectKind.FOG, state(741, snowMm = 5.0).kind)
    }

    // Wind

    @Test
    fun `the wind is passed on`() {
        val snapshot = WeatherSnapshot(conditionId = 500, windSpeedMetersPerSecond = 7.5, windFromDegrees = 250)

        val state = WeatherEffectMapper.map(snapshot, noon)

        assertEquals(7.5f, state.windSpeedMetersPerSecond)
        assertEquals(250f, state.windFromDegrees)
    }

    @Test
    fun `directions are kept between 0 and 360`() {
        listOf(0 to 0f, 360 to 0f, 361 to 1f, 720 to 0f, -90 to 270f, -1 to 359f, 359 to 359f, 450 to 90f).forEach { (given, expected) ->
            val snapshot = WeatherSnapshot(conditionId = 500, windFromDegrees = given)

            assertEquals(expected, WeatherEffectMapper.map(snapshot, noon).windFromDegrees, "$given degrees")
        }
    }

    @Test
    fun `a wind speed which is below zero is no wind`() {
        val snapshot = WeatherSnapshot(conditionId = 500, windSpeedMetersPerSecond = -3.0)

        assertEquals(0f, WeatherEffectMapper.map(snapshot, noon).windSpeedMetersPerSecond)
    }

    // Day and night

    private fun sun(
        sunrise: Long?,
        sunset: Long?,
        icon: String = "",
    ) = WeatherSnapshot(conditionId = 800, icon = icon, sunriseEpochSeconds = sunrise, sunsetEpochSeconds = sunset)

    @Test
    fun `it is night before sunrise and after sunset, and day in between`() {
        val day = 86_400L * 20_000
        val snapshot = sun(sunrise = day + 6 * 3600, sunset = day + 18 * 3600)

        assertTrue(WeatherEffectMapper.isNight(snapshot, day + 3 * 3600))
        assertTrue(WeatherEffectMapper.isNight(snapshot, day + 6 * 3600 - 1))
        assertFalse(WeatherEffectMapper.isNight(snapshot, day + 6 * 3600))
        assertFalse(WeatherEffectMapper.isNight(snapshot, day + 12 * 3600))
        assertFalse(WeatherEffectMapper.isNight(snapshot, day + 18 * 3600 - 1))
        assertTrue(WeatherEffectMapper.isNight(snapshot, day + 18 * 3600))
        assertTrue(WeatherEffectMapper.isNight(snapshot, day + 23 * 3600))
    }

    @Test
    fun `the sun times of an older answer are moved to the day of now`() {
        val day = 86_400L * 20_000
        val snapshot = sun(sunrise = day + 6 * 3600, sunset = day + 18 * 3600)

        // Two days later, and a day earlier, at the same hours
        assertFalse(WeatherEffectMapper.isNight(snapshot, day + 2 * 86_400 + 12 * 3600))
        assertTrue(WeatherEffectMapper.isNight(snapshot, day + 2 * 86_400 + 22 * 3600))
        assertTrue(WeatherEffectMapper.isNight(snapshot, day + 2 * 86_400 + 4 * 3600))
        assertFalse(WeatherEffectMapper.isNight(snapshot, day - 86_400 + 9 * 3600))
        assertTrue(WeatherEffectMapper.isNight(snapshot, day - 86_400 + 20 * 3600))
    }

    @Test
    fun `a day which crosses midnight of Greenwich is told right`() {
        // Sunrise at 19:00 UTC and sunset at 08:00 UTC the next morning, as in New Zealand
        val day = 86_400L * 20_000
        val snapshot = sun(sunrise = day + 19 * 3600, sunset = day + 32 * 3600)

        assertFalse(WeatherEffectMapper.isNight(snapshot, day + 23 * 3600))
        assertFalse(WeatherEffectMapper.isNight(snapshot, day + 86_400 + 2 * 3600))
        assertTrue(WeatherEffectMapper.isNight(snapshot, day + 86_400 + 10 * 3600))
        assertTrue(WeatherEffectMapper.isNight(snapshot, day + 18 * 3600))
    }

    @Test
    fun `the sun times win over the icon`() {
        val day = 86_400L * 20_000
        val snapshot = sun(sunrise = day + 6 * 3600, sunset = day + 18 * 3600, icon = "01n")

        assertFalse(WeatherEffectMapper.isNight(snapshot, day + 12 * 3600))
        assertTrue(WeatherEffectMapper.isNight(snapshot.copy(icon = "01d"), day + 22 * 3600))
    }

    @Test
    fun `the icon tells day from night when there are no sun times`() {
        assertTrue(WeatherEffectMapper.isNight(sun(null, null, icon = "10n"), noon))
        assertFalse(WeatherEffectMapper.isNight(sun(null, null, icon = "10d"), noon))
        assertFalse(WeatherEffectMapper.isNight(sun(null, null, icon = ""), noon))
    }

    @Test
    fun `the icon is used when only one of the sun times is there`() {
        assertTrue(WeatherEffectMapper.isNight(sun(noon - 3600, null, icon = "02n"), noon))
        assertFalse(WeatherEffectMapper.isNight(sun(null, noon + 3600, icon = "02d"), noon))
    }

    @Test
    fun `the icon is used when the sun times make no sense`() {
        // Polar summers and winters come as zeros, and a sunset before the sunrise is no day
        assertTrue(WeatherEffectMapper.isNight(sun(0, 0, icon = "01n"), noon))
        assertTrue(WeatherEffectMapper.isNight(sun(noon, noon - 1, icon = "01n"), noon))
        assertFalse(WeatherEffectMapper.isNight(sun(noon, noon, icon = "01d"), noon))
    }

    @Test
    fun `a day which lasts more than a full day never turns to night`() {
        val snapshot = sun(sunrise = noon, sunset = noon + 90_000)

        assertFalse(WeatherEffectMapper.isNight(snapshot, noon + 80_000))
        assertFalse(WeatherEffectMapper.isNight(snapshot, noon + 50_000))
    }

    @Test
    fun `night is part of the state`() {
        val night = WeatherEffectMapper.map(WeatherSnapshot(conditionId = 500, icon = "10n"), noon)
        val day = WeatherEffectMapper.map(WeatherSnapshot(conditionId = 500, icon = "10d"), noon)

        assertTrue(night.isNight)
        assertFalse(day.isNight)
        assertEquals(night.kind, day.kind)
        assertEquals(night.intensity, day.intensity)
    }

    @Test
    fun `the guess by the hour is night from 20 to 6`() {
        assertTrue(WeatherEffectMapper.isNightByHour(0))
        assertTrue(WeatherEffectMapper.isNightByHour(5))
        assertFalse(WeatherEffectMapper.isNightByHour(6))
        assertFalse(WeatherEffectMapper.isNightByHour(12))
        assertFalse(WeatherEffectMapper.isNightByHour(19))
        assertTrue(WeatherEffectMapper.isNightByHour(20))
        assertTrue(WeatherEffectMapper.isNightByHour(23))
    }

    // The testing override

    private fun override(
        condition: WeatherCondition,
        snapshot: WeatherSnapshot? = null,
        localHour: Int = 12,
    ) = WeatherEffectMapper.forOverride(condition, snapshot, noon, localHour)

    @Test
    fun `each condition of the override has its kind at a typical intensity`() {
        assertEquals(WeatherEffectKind.NONE, override(WeatherCondition.CLEAR).kind)
        assertEquals(0f, override(WeatherCondition.CLEAR).intensity)

        assertEquals(WeatherEffectKind.OVERCAST, override(WeatherCondition.CLOUDY).kind)
        assertEquals(WeatherEffectMapper.TYPICAL_OVERCAST, override(WeatherCondition.CLOUDY).intensity)

        assertEquals(WeatherEffectKind.RAIN, override(WeatherCondition.RAIN).kind)
        assertEquals(WeatherEffectMapper.TYPICAL_RAIN, override(WeatherCondition.RAIN).intensity)

        assertEquals(WeatherEffectKind.SNOW, override(WeatherCondition.SNOW).kind)
        assertEquals(WeatherEffectMapper.TYPICAL_SNOW, override(WeatherCondition.SNOW).intensity)

        assertEquals(WeatherEffectKind.STORM, override(WeatherCondition.STORM).kind)
        assertEquals(WeatherEffectMapper.TYPICAL_STORM, override(WeatherCondition.STORM).intensity)

        assertEquals(WeatherEffectKind.FOG, override(WeatherCondition.FOG).kind)
        assertEquals(WeatherEffectMapper.TYPICAL_FOG, override(WeatherCondition.FOG).intensity)
    }

    @Test
    fun `the override covers every condition and the typical intensities are strong enough to see`() {
        WeatherCondition.entries.forEach { condition ->
            val state = override(condition)

            if (condition == WeatherCondition.CLEAR) {
                assertEquals(WeatherEffectKind.NONE, state.kind)
            } else {
                assertTrue(state.kind != WeatherEffectKind.NONE, "$condition")
                assertTrue(state.intensity in 0.4f..1f, "$condition ${state.intensity}")
            }
        }
    }

    @Test
    fun `the override has a light wind of its own when the weather is not known`() {
        val state = override(WeatherCondition.RAIN)

        assertEquals(WeatherEffectMapper.TEST_WIND_METERS_PER_SECOND, state.windSpeedMetersPerSecond)
        assertEquals(WeatherEffectMapper.TEST_WIND_FROM_DEGREES, state.windFromDegrees)
    }

    @Test
    fun `the override takes the real wind when the weather is known`() {
        val snapshot = WeatherSnapshot(conditionId = 800, windSpeedMetersPerSecond = 9.0, windFromDegrees = 90)

        val state = override(WeatherCondition.RAIN, snapshot)

        assertEquals(9f, state.windSpeedMetersPerSecond)
        assertEquals(90f, state.windFromDegrees)
    }

    @Test
    fun `the override takes the time of day from the weather, or else from the hour`() {
        assertTrue(override(WeatherCondition.RAIN, localHour = 23).isNight)
        assertTrue(override(WeatherCondition.RAIN, localHour = 3).isNight)
        assertFalse(override(WeatherCondition.RAIN, localHour = 12).isNight)

        // The weather knows better than the hour
        val dayOutside = WeatherSnapshot(conditionId = 800, icon = "01d")
        val nightOutside = WeatherSnapshot(conditionId = 800, icon = "01n")
        assertFalse(override(WeatherCondition.RAIN, dayOutside, localHour = 23).isNight)
        assertTrue(override(WeatherCondition.RAIN, nightOutside, localHour = 12).isNight)
    }

    // Tables

    @Test
    fun `values between the points of a table are on a straight line`() {
        val xs = floatArrayOf(0f, 10f, 20f)
        val ys = floatArrayOf(1f, 3f, 4f)

        assertEquals(1f, WeatherEffectMapper.interpolate(0f, xs, ys))
        assertEquals(2f, WeatherEffectMapper.interpolate(5f, xs, ys))
        assertEquals(3f, WeatherEffectMapper.interpolate(10f, xs, ys))
        assertEquals(3.5f, WeatherEffectMapper.interpolate(15f, xs, ys))
        assertEquals(4f, WeatherEffectMapper.interpolate(20f, xs, ys))
    }

    @Test
    fun `values beyond the ends of a table take the value of the end`() {
        val xs = floatArrayOf(0f, 10f)
        val ys = floatArrayOf(1f, 3f)

        assertEquals(1f, WeatherEffectMapper.interpolate(-5f, xs, ys))
        assertEquals(3f, WeatherEffectMapper.interpolate(500f, xs, ys))
    }

    // The real answers of the API, parsed and mapped

    private fun mapped(
        body: String,
        now: Long,
    ): WeatherEffectState {
        val response = JsonHelper.json.decodeFromString<CurrentWeatherResponse>(body)
        return WeatherEffectMapper.map(WeatherSnapshot.from(response, units = "metric")!!, now)
    }

    @Test
    fun `the rain answer is moderate rain, and it is day when it is given and night after sunset`() {
        // Given at 14:03 UTC, with the sunset at 18:17 UTC
        val atTheTime = mapped(OpenWeatherSamples.RAIN, now = 1661870592L)
        val afterSunset = mapped(OpenWeatherSamples.RAIN, now = 1661870592L + 6 * 3600)

        assertEquals(WeatherEffectKind.RAIN, atTheTime.kind)
        // 501 is 0.50, and 3.16 mm an hour is 0.54
        assertEquals(0.52f, atTheTime.intensity, 0.01f)
        assertEquals(0.62f, atTheTime.windSpeedMetersPerSecond, 0.001f)
        assertEquals(349f, atTheTime.windFromDegrees)
        assertFalse(atTheTime.isNight)
        assertTrue(afterSunset.isNight)
    }

    @Test
    fun `the snow answer is snow in the night`() {
        // The icon (13n) and the sun times (given after sunset) both say it is night
        val state = mapped(OpenWeatherSamples.SNOW, now = 1675123456L)

        assertEquals(WeatherEffectKind.SNOW, state.kind)
        assertTrue(state.isNight)
        // 601 is 0.55, and 0.89 mm of water an hour is 0.56
        assertEquals(0.55f, state.intensity, 0.01f)
        assertEquals(4.12f, state.windSpeedMetersPerSecond, 0.001f)
        assertEquals(70f, state.windFromDegrees)
    }

    @Test
    fun `the answer without rain, snow, clouds and sys is clear and draws nothing`() {
        val state = mapped(OpenWeatherSamples.BARE, now = noon)

        assertEquals(WeatherEffectKind.NONE, state.kind)
        assertEquals(0f, state.intensity)
        assertFalse(state.isNight)
    }
}
