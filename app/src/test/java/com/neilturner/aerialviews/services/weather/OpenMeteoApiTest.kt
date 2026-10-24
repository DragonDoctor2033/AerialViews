package com.neilturner.aerialviews.services.weather

import com.neilturner.aerialviews.data.network.JsonHelper
import com.neilturner.aerialviews.data.network.JsonHelper.buildSerializer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

/**
 * Parses responses captured from the real Open-Meteo API, so a renamed field fails here
 * instead of leaving the weather overlay silently empty.
 */
@DisplayName("Open-Meteo API Tests")
internal class OpenMeteoApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: OpenMeteoApi

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
        api =
            Retrofit
                .Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(buildSerializer())
                .build()
                .create(OpenMeteoApi::class.java)
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    @Test
    @DisplayName("Should parse current conditions and the daily forecast")
    fun shouldParseForecast() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(forecastJson))

            val response =
                api.getForecast(
                    lat = 55.7558,
                    lon = 37.6173,
                    current = WeatherService.CURRENT_FIELDS,
                    daily = WeatherService.DAILY_FIELDS,
                    forecastDays = 3,
                )
            val body = requireNotNull(response.body())

            assertEquals(10800, body.utcOffsetSeconds)
            assertEquals(
                CurrentConditions(temperature = 9.8, humidity = 62, weatherCode = 3, windSpeed = 7.9, isDay = 1),
                body.current,
            )
            assertEquals(
                DailyForecast(
                    time = listOf(1792789200L, 1792875600L, 1792962000L),
                    weatherCode = listOf(3, 0, 3),
                    temperatureMax = listOf(12.5, 12.4, 10.2),
                    temperatureMin = listOf(6.5, 4.1, 3.0),
                ),
                body.daily,
            )
        }

    @Test
    @DisplayName("Should send only the requested sections along with units and time settings")
    fun shouldBuildForecastQuery() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(forecastJson))

            api.getForecast(
                lat = 53.35,
                lon = -6.26,
                current = WeatherService.CURRENT_FIELDS,
                temperatureUnit = "fahrenheit",
                windSpeedUnit = "mph",
            )

            val url = requireNotNull(server.takeRequest().requestUrl)
            assertEquals("/v1/forecast", url.encodedPath)
            assertEquals("53.35", url.queryParameter("latitude"))
            assertEquals("-6.26", url.queryParameter("longitude"))
            assertEquals(WeatherService.CURRENT_FIELDS, url.queryParameter("current"))
            assertNull(url.queryParameter("daily"))
            assertNull(url.queryParameter("forecast_days"))
            assertEquals("fahrenheit", url.queryParameter("temperature_unit"))
            assertEquals("mph", url.queryParameter("wind_speed_unit"))
            assertEquals("auto", url.queryParameter("timezone"))
            assertEquals("unixtime", url.queryParameter("timeformat"))
        }

    @Test
    @DisplayName("Should parse geocoding results and a search without matches")
    fun shouldParseGeocoding() {
        val response = JsonHelper.json.decodeFromString<GeocodingResponse>(geocodingJson)

        assertEquals(
            listOf("Москва, Россия", "Москва, Айдахо, США"),
            response.results.map { it.toLocationResponse().getDisplayName() },
        )
        assertEquals(55.75204, response.results[0].latitude)
        assertEquals(37.61781, response.results[0].longitude)

        val empty = JsonHelper.json.decodeFromString<GeocodingResponse>("""{"generationtime_ms": 0.5}""")
        assertEquals(emptyList<GeocodingResult>(), empty.results)
    }

    // Captured from api.open-meteo.com and geocoding-api.open-meteo.com on 2026-10-24
    private val forecastJson =
        """
        {
          "latitude": 55.7817, "longitude": 37.570797, "generationtime_ms": 0.39,
          "utc_offset_seconds": 10800, "timezone": "Europe/Moscow", "timezone_abbreviation": "GMT+3",
          "elevation": 140.0,
          "current_units": {
            "time": "unixtime", "interval": "seconds", "temperature_2m": "°C", "relative_humidity_2m": "%",
            "weather_code": "wmo code", "wind_speed_10m": "km/h", "is_day": ""
          },
          "current": {
            "time": 1792831500, "interval": 900, "temperature_2m": 9.8, "relative_humidity_2m": 62,
            "weather_code": 3, "wind_speed_10m": 7.9, "is_day": 1
          },
          "daily_units": {
            "time": "unixtime", "weather_code": "wmo code", "temperature_2m_max": "°C", "temperature_2m_min": "°C"
          },
          "daily": {
            "time": [1792789200, 1792875600, 1792962000],
            "weather_code": [3, 0, 3],
            "temperature_2m_max": [12.5, 12.4, 10.2],
            "temperature_2m_min": [6.5, 4.1, 3.0]
          }
        }
        """.trimIndent()

    private val geocodingJson =
        """
        {
          "results": [
            {
              "id": 524901, "name": "Москва", "latitude": 55.75204, "longitude": 37.61781, "elevation": 155.0,
              "feature_code": "PPLC", "country_code": "RU", "admin1_id": 524894, "timezone": "Europe/Moscow",
              "population": 10381222, "country_id": 2017370, "country": "Россия", "admin1": "Москва"
            },
            {
              "id": 5601538, "name": "Москва", "latitude": 46.73239, "longitude": -117.00017, "elevation": 786.0,
              "feature_code": "PPLA2", "country_code": "US", "admin1_id": 5596512, "admin2_id": 5598264,
              "timezone": "America/Los_Angeles", "population": 25060, "postcodes": ["83843", "83844"],
              "country_id": 6252001, "country": "США", "admin1": "Айдахо", "admin2": "Лейта"
            }
          ],
          "generationtime_ms": 0.62
        }
        """.trimIndent()
}
