package com.neilturner.aerialviews.services.weather

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import retrofit2.Response

/**
 * The weather service hands the numbers of the current weather to the weather effects, which want
 * them even when no weather overlay is shown.
 */
internal class WeatherServiceEffectsTest {
    private val context: Context = mockk(relaxed = true)
    private val feed = WeatherEffectsFeed()
    private val cache = WeatherConditionCache()

    private val rainyDublin =
        CurrentWeatherResponse(
            weather = listOf(Weather(id = 502, main = "Rain", description = "heavy intensity rain", icon = "10n")),
            main = MainWeatherData(temp = 9.0, feelsLike = 6.0, tempMin = 8.0, tempMax = 10.0, pressure = 1000, humidity = 90),
            wind = Wind(speed = 8.0, deg = 250),
            name = "Dublin",
            rain = Precipitation(oneHour = 6.5),
            clouds = Clouds(all = 100),
            sys = SunTimes(sunrise = 1_700_000_000L, sunset = 1_700_040_000L),
        )

    private class FakeApi(
        private val current: CurrentWeatherResponse,
    ) : OpenWeatherApi {
        var currentCalls = 0

        override suspend fun getCurrentWeather(
            lat: Double,
            lon: Double,
            apiKey: String,
            units: String,
            language: String,
        ): Response<CurrentWeatherResponse> {
            currentCalls++
            return Response.success(current)
        }

        override suspend fun getForecast(
            lat: Double,
            lon: Double,
            apiKey: String,
            units: String,
            language: String,
        ): Response<FiveDayForecastResponse> =
            Response.success(FiveDayForecastResponse(list = emptyList(), city = City(name = "Dublin", country = "IE")))

        override suspend fun getLocationByName(
            locationName: String,
            limit: Int,
            apiKey: String,
            language: String,
        ): Response<List<LocationResponse>> = Response.success(emptyList())

        override suspend fun getLocationByCoordinates(
            lat: Double,
            lon: Double,
            limit: Int,
            apiKey: String,
            language: String,
        ): Response<List<LocationResponse>> = Response.success(emptyList())
    }

    private fun config(units: String = "metric") =
        WeatherRequestConfig(apiKey = "key", lat = 53.3498, lon = -6.2603, units = units, language = "en")

    private val display = WeatherDisplayConfig(currentWeatherCity = "Dublin", forecastCity = "Dublin", forecastDays = 3)

    private fun service(api: FakeApi) = WeatherService(context, apiOverride = api, conditionCache = cache, effectsFeed = feed)

    @Test
    fun `the current weather is handed to the effects with its rain, clouds, wind and sun times`() =
        runTest {
            val api = FakeApi(rainyDublin)

            service(api).fetchWeatherData(WeatherRequests(fetchCurrentWeather = true), config(), display)

            assertEquals(1, api.currentCalls)
            assertEquals(
                WeatherSnapshot(
                    conditionId = 502,
                    icon = "10n",
                    rainMmPerHour = 6.5,
                    snowMmPerHour = null,
                    cloudsPercent = 100,
                    windSpeedMetersPerSecond = 8.0,
                    windFromDegrees = 250,
                    sunriseEpochSeconds = 1_700_000_000L,
                    sunsetEpochSeconds = 1_700_040_000L,
                ),
                feed.current(),
            )
        }

    @Test
    fun `the effects get the weather with no weather overlay for the text`() =
        runTest {
            val result = service(FakeApi(rainyDublin)).fetchWeatherData(WeatherRequests(fetchCurrentWeather = true), config(), display)

            // The overlay text is made as before, and the effects have their own copy
            assertEquals("9°", result.weather?.temperature)
            assertNotNull(feed.current())
        }

    @Test
    fun `the wind of imperial units is handed over in meters per second`() =
        runTest {
            service(FakeApi(rainyDublin)).fetchWeatherData(WeatherRequests(fetchCurrentWeather = true), config(units = "imperial"), display)

            assertEquals(8.0 * 0.44704, feed.current()?.windSpeedMetersPerSecond ?: 0.0, 0.0001)
        }

    @Test
    fun `nothing is handed over when only the forecast is wanted`() =
        runTest {
            val api = FakeApi(rainyDublin)

            service(api).fetchWeatherData(WeatherRequests(fetchForecast = true), config(), display)

            assertEquals(0, api.currentCalls)
            assertNull(feed.current())
        }

    @Test
    fun `the playlist still hears of the condition`() =
        runTest {
            service(FakeApi(rainyDublin)).fetchWeatherData(WeatherRequests(fetchCurrentWeather = true), config(), display)

            assertNotNull(cache.get())
        }
}
