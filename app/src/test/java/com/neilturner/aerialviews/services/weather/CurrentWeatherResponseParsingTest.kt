package com.neilturner.aerialviews.services.weather

import com.neilturner.aerialviews.data.network.JsonHelper
import com.neilturner.aerialviews.testutils.OpenWeatherSamples
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Parses real answers of the OpenWeather current weather API with the same Json configuration the
 * app uses. The answers have a lot more in them than the app reads, and what the weather effects
 * read (rain, snow, clouds, sys) is left out by the API when there is none of it.
 */
internal class CurrentWeatherResponseParsingTest {
    private fun parse(body: String): CurrentWeatherResponse = JsonHelper.json.decodeFromString<CurrentWeatherResponse>(body)

    @Test
    fun `an answer with rain, clouds and sys keeps what the effects need`() {
        val response = parse(OpenWeatherSamples.RAIN)

        assertEquals(501, response.weather.single().id)
        assertEquals("10d", response.weather.single().icon)
        assertEquals("Zocca", response.name)
        assertEquals(3.16, response.rain?.oneHour)
        assertNull(response.rain?.threeHours)
        assertEquals(100, response.clouds?.all)
        assertEquals(1661834187L, response.sys?.sunrise)
        assertEquals(1661882248L, response.sys?.sunset)
        assertNull(response.snow)
    }

    @Test
    fun `an answer with snow keeps the snow and no rain`() {
        val response = parse(OpenWeatherSamples.SNOW)

        assertEquals(601, response.weather.single().id)
        assertEquals("13n", response.weather.single().icon)
        assertEquals(0.89, response.snow?.oneHour)
        assertNull(response.rain)
        assertEquals(70, response.wind.deg)
        assertEquals(4.12, response.wind.speed)
    }

    @Test
    fun `an answer without rain, snow, clouds and sys still parses`() {
        val response = parse(OpenWeatherSamples.BARE)

        assertEquals(800, response.weather.single().id)
        assertEquals("Dublin", response.name)
        assertNull(response.rain)
        assertNull(response.snow)
        assertNull(response.clouds)
        assertNull(response.sys)
    }

    @Test
    fun `the old parts of the answer are read as they always were`() {
        val response = parse(OpenWeatherSamples.RAIN)

        assertEquals(298.48, response.main.temp)
        assertEquals(64, response.main.humidity)
        assertEquals(0.62, response.wind.speed)
        assertEquals(349, response.wind.deg)
    }

    @Test
    fun `parts which are there but empty parse too`() {
        val response =
            parse(
                """
                {
                  "weather": [ { "id": 300, "main": "Drizzle", "description": "light drizzle", "icon": "09d" } ],
                  "main": { "temp": 8.0, "feels_like": 6.0, "temp_min": 7.0, "temp_max": 9.0, "pressure": 1000, "humidity": 90 },
                  "wind": { "speed": 2.0, "deg": 10 },
                  "rain": {},
                  "clouds": {},
                  "sys": {},
                  "name": "Oslo"
                }
                """.trimIndent(),
            )

        assertNotNull(response.rain)
        assertNull(response.rain?.oneHour)
        assertNull(response.rain?.mmPerHour())
        assertNull(response.clouds?.all)
        assertNull(response.sys?.sunrise)
        assertNull(response.sys?.sunset)
    }

    @Test
    fun `parts which are null parse too`() {
        val response =
            parse(
                """
                {
                  "weather": [ { "id": 300, "main": "Drizzle", "description": "light drizzle", "icon": "09d" } ],
                  "main": { "temp": 8.0, "feels_like": 6.0, "temp_min": 7.0, "temp_max": 9.0, "pressure": 1000, "humidity": 90 },
                  "wind": { "speed": 2.0, "deg": 10 },
                  "rain": null,
                  "snow": null,
                  "clouds": null,
                  "sys": null,
                  "name": "Oslo"
                }
                """.trimIndent(),
            )

        assertNull(response.rain)
        assertNull(response.snow)
        assertNull(response.clouds)
        assertNull(response.sys)
    }

    @Test
    fun `volumes given as whole numbers or in quotes still parse`() {
        val response =
            parse(
                """
                {
                  "weather": [ { "id": 502, "main": "Rain", "description": "heavy intensity rain", "icon": "10d" } ],
                  "main": { "temp": 8.0, "feels_like": 6.0, "temp_min": 7.0, "temp_max": 9.0, "pressure": 1000, "humidity": 90 },
                  "wind": { "speed": 2.0, "deg": 10 },
                  "rain": { "1h": 12 },
                  "snow": { "1h": "0.5" },
                  "name": "Oslo"
                }
                """.trimIndent(),
            )

        assertEquals(12.0, response.rain?.oneHour)
        assertEquals(0.5, response.snow?.oneHour)
    }

    @Test
    fun `the volume of three hours is a third per hour when the last hour is not given`() {
        assertEquals(1.2, Precipitation(oneHour = 1.2, threeHours = 9.0).mmPerHour())
        assertEquals(2.0, Precipitation(oneHour = null, threeHours = 6.0).mmPerHour())
        assertNull(Precipitation().mmPerHour())
    }

    @Test
    fun `the snapshot has the numbers of the rain answer`() {
        val snapshot = WeatherSnapshot.from(parse(OpenWeatherSamples.RAIN), units = "metric")

        assertEquals(
            WeatherSnapshot(
                conditionId = 501,
                icon = "10d",
                rainMmPerHour = 3.16,
                snowMmPerHour = null,
                cloudsPercent = 100,
                windSpeedMetersPerSecond = 0.62,
                windFromDegrees = 349,
                sunriseEpochSeconds = 1661834187L,
                sunsetEpochSeconds = 1661882248L,
            ),
            snapshot,
        )
    }

    @Test
    fun `the snapshot of the snow answer has the snow and no rain`() {
        val snapshot = WeatherSnapshot.from(parse(OpenWeatherSamples.SNOW), units = "metric")

        assertEquals(601, snapshot?.conditionId)
        assertEquals("13n", snapshot?.icon)
        assertEquals(0.89, snapshot?.snowMmPerHour)
        assertNull(snapshot?.rainMmPerHour)
        assertEquals(70, snapshot?.windFromDegrees)
    }

    @Test
    fun `the snapshot of an answer without the optional parts has none of them`() {
        val snapshot = WeatherSnapshot.from(parse(OpenWeatherSamples.BARE), units = "metric")

        assertEquals(800, snapshot?.conditionId)
        assertNull(snapshot?.rainMmPerHour)
        assertNull(snapshot?.snowMmPerHour)
        assertNull(snapshot?.cloudsPercent)
        assertNull(snapshot?.sunriseEpochSeconds)
        assertNull(snapshot?.sunsetEpochSeconds)
        assertEquals(9.8, snapshot?.windSpeedMetersPerSecond)
    }

    @Test
    fun `the wind of an imperial answer is turned from miles per hour into meters per second`() {
        val metric = WeatherSnapshot.from(parse(OpenWeatherSamples.BARE), units = "metric")
        val standard = WeatherSnapshot.from(parse(OpenWeatherSamples.BARE), units = "standard")
        val imperial = WeatherSnapshot.from(parse(OpenWeatherSamples.BARE), units = "imperial")

        assertEquals(9.8, metric?.windSpeedMetersPerSecond)
        assertEquals(9.8, standard?.windSpeedMetersPerSecond)
        assertEquals(9.8 * 0.44704, imperial?.windSpeedMetersPerSecond ?: 0.0, 0.0001)
        assertEquals(4.38, imperial?.windSpeedMetersPerSecond ?: 0.0, 0.01)
    }

    @Test
    fun `there is no snapshot of an answer without a weather`() {
        val response = parse(OpenWeatherSamples.BARE).copy(weather = emptyList())

        assertNull(WeatherSnapshot.from(response, units = "metric"))
    }
}
