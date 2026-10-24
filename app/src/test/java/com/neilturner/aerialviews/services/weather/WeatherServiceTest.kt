package com.neilturner.aerialviews.services.weather

import android.content.Context
import com.neilturner.aerialviews.R
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

@DisplayName("Weather Service Tests")
internal class WeatherServiceTest {
    private val context: Context =
        mockk(relaxed = true) {
            every { getString(R.string.weather_code_clear) } returns "Clear sky"
            every { getString(R.string.weather_code_overcast) } returns "Overcast"
            every { resources.configuration.locales[0] } returns Locale.ENGLISH
        }

    @Test
    @DisplayName("Should map current conditions to weather event")
    fun shouldMapCurrentConditions() {
        val service = WeatherService(context, apiOverride = FakeOpenMeteoApi())
        val current =
            CurrentConditions(
                temperature = 12.6,
                humidity = 77,
                weatherCode = 0,
                windSpeed = 9.8,
                isDay = 1,
            )

        val event = service.mapCurrentConditions(current, windSpeedUnit = "kmh", city = "Custom City")

        assertEquals("13°", event.temperature)
        assertEquals("Clear sky", event.summary)
        assertEquals(R.drawable.weather_clear, event.icon)
        assertEquals("Custom City", event.city)
        assertEquals("10 km/h", event.wind)
        assertEquals("77%", event.humidity)
    }

    @Test
    @DisplayName("Should use night icon and imperial wind label")
    fun shouldMapNightConditionsInImperialUnits() {
        val service = WeatherService(context, apiOverride = FakeOpenMeteoApi())
        val current = CurrentConditions(temperature = 50.2, humidity = 60, weatherCode = 1, windSpeed = 6.3, isDay = 0)

        val event = service.mapCurrentConditions(current, windSpeedUnit = "mph", city = "Dublin")

        assertEquals("50°", event.temperature)
        assertEquals(R.drawable.weather_clear_night, event.icon)
        assertEquals("6 mph", event.wind)
    }

    @Test
    @DisplayName("Should return empty weather event when temperature is missing")
    fun shouldReturnEmptyEventWithoutTemperature() {
        val service = WeatherService(context, apiOverride = FakeOpenMeteoApi())

        val event = service.mapCurrentConditions(CurrentConditions(), windSpeedUnit = "kmh", city = "Dublin")

        assertEquals("", event.temperature)
    }

    @Test
    @DisplayName("Should map daily forecast into requested number of days using the location's date")
    fun shouldMapDailyForecast() {
        val service = WeatherService(context, apiOverride = FakeOpenMeteoApi())
        val moscow = ZoneOffset.ofHours(3)
        val daily =
            DailyForecast(
                time = (0L..4L).map { localMidnight(LocalDate.of(2026, 10, 24).plusDays(it), moscow) },
                weatherCode = listOf(3, 0, 61, 71, 95),
                temperatureMax = listOf(12.5, 12.4, 10.2, 3.0, 15.0),
                temperatureMin = listOf(6.5, 4.1, 3.0, -1.6, 9.0),
            )

        val event = service.mapDailyForecast(daily, utcOffsetSeconds = 3 * 3600, city = "Moscow", maxDays = 3)

        assertEquals("Moscow", event.city)
        assertEquals(3, event.days.size)
        assertEquals(dayName(LocalDate.of(2026, 10, 24)), event.days[0].dayName)
        assertEquals("13°", event.days[0].tempHigh)
        assertEquals("7°", event.days[0].tempLow)
        assertEquals(R.drawable.weather_cloudy_heavy, event.days[0].icon)
        assertEquals("12°", event.days[1].tempHigh)
        assertEquals(R.drawable.weather_rain_light, event.days[2].icon)
    }

    @Test
    @DisplayName("Should skip forecast days with missing temperatures")
    fun shouldSkipDaysWithMissingTemperatures() {
        val service = WeatherService(context, apiOverride = FakeOpenMeteoApi())
        val daily =
            DailyForecast(
                time = (0L..2L).map { localMidnight(LocalDate.of(2026, 10, 24).plusDays(it), ZoneOffset.UTC) },
                weatherCode = listOf(0, 0, 0),
                temperatureMax = listOf(10.0, null, 12.0),
                temperatureMin = listOf(5.0, 6.0, 7.0),
            )

        val event = service.mapDailyForecast(daily, utcOffsetSeconds = 0, city = "Dublin", maxDays = 3)

        assertEquals(listOf("10°", "12°"), event.days.map { it.tempHigh })
    }

    @Test
    @DisplayName("Should request only current conditions when current weather requested")
    fun shouldFetchCurrentWeatherOnly() =
        runTest {
            val api = FakeOpenMeteoApi()
            val service = WeatherService(context, apiOverride = api)

            val result =
                service.fetchWeatherData(
                    requests = WeatherRequests(fetchCurrentWeather = true, fetchForecast = false),
                    config = requestConfig(),
                    displayConfig = displayConfig(),
                )

            assertEquals(1, api.forecastCalls)
            assertEquals(WeatherService.CURRENT_FIELDS, api.lastCurrent)
            assertNull(api.lastDaily)
            assertNull(api.lastForecastDays)
            assertEquals("13°", result.weather?.temperature)
            assertNull(result.forecast)
        }

    @Test
    @DisplayName("Should request only the daily forecast when forecast requested")
    fun shouldFetchForecastOnly() =
        runTest {
            val api = FakeOpenMeteoApi()
            val service = WeatherService(context, apiOverride = api)

            val result =
                service.fetchWeatherData(
                    requests = WeatherRequests(fetchCurrentWeather = false, fetchForecast = true),
                    config = requestConfig(),
                    displayConfig = displayConfig(),
                )

            assertEquals(1, api.forecastCalls)
            assertNull(api.lastCurrent)
            assertEquals(WeatherService.DAILY_FIELDS, api.lastDaily)
            assertEquals(3, api.lastForecastDays)
            assertNull(result.weather)
            assertEquals(3, result.forecast?.days?.size)
        }

    @Test
    @DisplayName("Should fetch current weather and forecast in one call with the configured units")
    fun shouldFetchCurrentWeatherAndForecast() =
        runTest {
            val api = FakeOpenMeteoApi()
            val service = WeatherService(context, apiOverride = api)

            val result =
                service.fetchWeatherData(
                    requests = WeatherRequests(fetchCurrentWeather = true, fetchForecast = true),
                    config = requestConfig().copy(temperatureUnit = "fahrenheit", windSpeedUnit = "mph"),
                    displayConfig = displayConfig(),
                )

            assertEquals(1, api.forecastCalls)
            assertEquals("fahrenheit", api.lastTemperatureUnit)
            assertEquals("mph", api.lastWindSpeedUnit)
            assertEquals("13°", result.weather?.temperature)
            assertEquals(3, result.forecast?.days?.size)
        }

    @Test
    @DisplayName("Should skip all API calls when no weather overlays are requested")
    fun shouldSkipApiCallsWhenNoWeatherRequests() =
        runTest {
            val api = FakeOpenMeteoApi()
            val service = WeatherService(context, apiOverride = api)

            val result =
                service.fetchWeatherData(
                    requests = WeatherRequests(fetchCurrentWeather = false, fetchForecast = false),
                    config = requestConfig(),
                    displayConfig = displayConfig(),
                )

            assertEquals(0, api.forecastCalls)
            assertNull(result.weather)
            assertNull(result.forecast)
        }

    @Test
    @DisplayName("Should map geocoding results to locations")
    fun shouldLookupLocation() =
        runTest {
            val api = FakeOpenMeteoApi()
            val service = WeatherService(context, apiOverride = api)

            val locations = service.lookupLocation("Moscow")

            assertEquals("Moscow", api.lastSearch)
            assertEquals("en", api.lastLanguage)
            assertEquals(1, locations.size)
            assertEquals("Moscow, Russia", locations[0].getDisplayName())
            assertEquals(55.75204, locations[0].lat)
            assertEquals(37.61781, locations[0].lon)
        }

    @Test
    @DisplayName("Should build display names without blank or repeated parts")
    fun shouldBuildLocationDisplayNames() {
        assertEquals(
            "Paris, Île-de-France, France",
            LocationResponse("Paris", 48.85, 2.35, "France", "Île-de-France").getDisplayName(),
        )
        assertEquals("Custom Location", LocationResponse("Custom Location", 1.0, 2.0, "").getDisplayName())
    }

    @Test
    @DisplayName("Should map WMO codes to icons and summaries")
    fun shouldMapWeatherCodes() {
        assertEquals(R.drawable.weather_clear, WeatherCodes.getWeatherIcon(0, isDay = true))
        assertEquals(R.drawable.weather_clear_night, WeatherCodes.getWeatherIcon(0, isDay = false))
        assertEquals(R.drawable.weather_cloudy_light, WeatherCodes.getWeatherIcon(2, isDay = true))
        assertEquals(R.drawable.weather_mist, WeatherCodes.getWeatherIcon(45, isDay = true))
        assertEquals(R.drawable.weather_rain_heavy, WeatherCodes.getWeatherIcon(65, isDay = true))
        assertEquals(R.drawable.weather_snow, WeatherCodes.getWeatherIcon(86, isDay = false))
        assertEquals(R.drawable.weather_thunder, WeatherCodes.getWeatherIcon(99, isDay = true))
        assertEquals(R.string.weather_code_thunderstorm, WeatherCodes.getSummary(95))
        assertNull(WeatherCodes.getSummary(42))
    }

    private fun localMidnight(
        date: LocalDate,
        offset: ZoneOffset,
    ): Long = OffsetDateTime.of(date.atStartOfDay(), offset).toEpochSecond()

    private fun dayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun requestConfig() =
        WeatherRequestConfig(
            lat = 53.3498,
            lon = -6.2603,
            temperatureUnit = "celsius",
            windSpeedUnit = "kmh",
        )

    private fun displayConfig() =
        WeatherDisplayConfig(
            currentWeatherCity = "Custom City",
            forecastCity = "Custom City",
            forecastDays = 3,
        )
}

private class FakeOpenMeteoApi : OpenMeteoApi {
    var forecastCalls = 0
    var lastCurrent: String? = null
    var lastDaily: String? = null
    var lastForecastDays: Int? = null
    var lastTemperatureUnit: String? = null
    var lastWindSpeedUnit: String? = null
    var lastSearch: String? = null
    var lastLanguage: String? = null

    override suspend fun getForecast(
        lat: Double,
        lon: Double,
        current: String?,
        daily: String?,
        forecastDays: Int?,
        temperatureUnit: String,
        windSpeedUnit: String,
        timezone: String,
        timeFormat: String,
    ): Response<ForecastResponse> {
        forecastCalls++
        lastCurrent = current
        lastDaily = daily
        lastForecastDays = forecastDays
        lastTemperatureUnit = temperatureUnit
        lastWindSpeedUnit = windSpeedUnit

        val today = LocalDate.now(ZoneOffset.UTC)
        val days = forecastDays ?: 0
        return Response.success(
            ForecastResponse(
                utcOffsetSeconds = 0,
                current =
                    current?.let {
                        CurrentConditions(temperature = 12.6, humidity = 75, weatherCode = 0, windSpeed = 9.5, isDay = 1)
                    },
                daily =
                    daily?.let {
                        DailyForecast(
                            time = (0 until days).map { today.plusDays(it.toLong()).atStartOfDay().toEpochSecond(ZoneOffset.UTC) },
                            weatherCode = List(days) { 3 },
                            temperatureMax = List(days) { 15.0 },
                            temperatureMin = List(days) { 9.0 },
                        )
                    },
            ),
        )
    }

    override suspend fun searchLocation(
        name: String,
        count: Int,
        language: String,
    ): Response<GeocodingResponse> {
        lastSearch = name
        lastLanguage = language
        return Response.success(
            GeocodingResponse(
                results =
                    listOf(
                        GeocodingResult(
                            name = "Moscow",
                            latitude = 55.75204,
                            longitude = 37.61781,
                            country = "Russia",
                            admin1 = "Moscow",
                        ),
                    ),
            ),
        )
    }
}
