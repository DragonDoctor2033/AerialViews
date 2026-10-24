package com.neilturner.aerialviews.services.weather

import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import retrofit2.Response

/**
 * Every look at the weather for the playlist, at the start and every few minutes while it plays,
 * also gives the weather effects the weather it found, so they follow the weather as often as the
 * playlist does, and know it before the weather service has asked for the first time.
 */
internal class OpenWeatherConditionFetcherEffectsTest {
    private val feed = WeatherEffectsFeed()

    private class FakeApi(
        private val answer: () -> Response<CurrentWeatherResponse>,
    ) : OpenWeatherApi {
        var calls = 0

        override suspend fun getCurrentWeather(
            lat: Double,
            lon: Double,
            apiKey: String,
            units: String,
            language: String,
        ): Response<CurrentWeatherResponse> {
            calls++
            return answer()
        }

        override suspend fun getForecast(
            lat: Double,
            lon: Double,
            apiKey: String,
            units: String,
            language: String,
        ): Response<FiveDayForecastResponse> = error("not used")

        override suspend fun getLocationByName(
            locationName: String,
            limit: Int,
            apiKey: String,
            language: String,
        ): Response<List<LocationResponse>> = error("not used")

        override suspend fun getLocationByCoordinates(
            lat: Double,
            lon: Double,
            limit: Int,
            apiKey: String,
            language: String,
        ): Response<List<LocationResponse>> = error("not used")
    }

    private val snowy =
        CurrentWeatherResponse(
            weather = listOf(Weather(id = 601, main = "Snow", description = "snow", icon = "13n")),
            main = MainWeatherData(temp = -2.0, feelsLike = -6.0, tempMin = -3.0, tempMax = -1.0, pressure = 1005, humidity = 90),
            wind = Wind(speed = 5.5, deg = 90),
            name = "Oslo",
            snow = Precipitation(oneHour = 0.8),
            clouds = Clouds(all = 100),
            sys = SunTimes(sunrise = 1_700_000_000L, sunset = 1_700_030_000L),
        )

    private fun fetcher(
        api: FakeApi,
        apiKey: String = "key",
        latitude: Double? = 59.9,
        longitude: Double? = 10.7,
    ) = OpenWeatherConditionFetcher(
        apiKey = { apiKey },
        latitude = { latitude },
        longitude = { longitude },
        api = lazyOf(api),
        effectsFeed = feed,
    )

    @Test
    fun `a look at the weather gives the effects the numbers it found`() =
        runTest {
            val id = fetcher(FakeApi { Response.success(snowy) }).fetchConditionId()

            assertEquals(601, id)
            assertEquals(
                WeatherSnapshot(
                    conditionId = 601,
                    icon = "13n",
                    rainMmPerHour = null,
                    snowMmPerHour = 0.8,
                    cloudsPercent = 100,
                    windSpeedMetersPerSecond = 5.5,
                    windFromDegrees = 90,
                    sunriseEpochSeconds = 1_700_000_000L,
                    sunsetEpochSeconds = 1_700_030_000L,
                ),
                feed.current(),
            )
        }

    @Test
    fun `every look gives them the newest weather`() =
        runTest {
            val rainy =
                snowy.copy(
                    weather = listOf(Weather(id = 500, main = "Rain", description = "light rain", icon = "10d")),
                    snow = null,
                )
            var answer = snowy
            val fetcher = fetcher(FakeApi { Response.success(answer) })

            fetcher.fetchConditionId()
            assertEquals(601, feed.current()?.conditionId)

            answer = rainy
            fetcher.fetchConditionId()
            assertEquals(500, feed.current()?.conditionId)
        }

    @Test
    fun `a look which fails gives them nothing`() =
        runTest {
            val failed = FakeApi { Response.error(500, "".toResponseBody()) }

            assertNull(fetcher(failed).fetchConditionId())

            assertNull(feed.current())
        }

    @Test
    fun `an answer with no weather in it gives them nothing`() =
        runTest {
            val api = FakeApi { Response.success(snowy.copy(weather = emptyList())) }

            assertNull(fetcher(api).fetchConditionId())

            assertNull(feed.current())
        }

    @Test
    fun `an empty answer gives them nothing`() =
        runTest {
            assertNull(fetcher(FakeApi { Response.success(null) }).fetchConditionId())

            assertNull(feed.current())
        }

    @Test
    fun `without a key or a location nothing is asked and nothing is given`() =
        runTest {
            val api = FakeApi { Response.success(snowy) }

            assertNull(fetcher(api, apiKey = "").fetchConditionId())
            assertNull(fetcher(api, latitude = null).fetchConditionId())
            assertNull(fetcher(api, longitude = null).fetchConditionId())

            assertEquals(0, api.calls)
            assertNull(feed.current())
        }

    @Test
    fun `the weather of the effects is metric, as the request is`() =
        runTest {
            fetcher(FakeApi { Response.success(snowy) }).fetchConditionId()

            // 5.5 as it came, not turned from miles per hour
            assertNotNull(feed.current())
            assertEquals(5.5, feed.current()?.windSpeedMetersPerSecond)
        }
}
