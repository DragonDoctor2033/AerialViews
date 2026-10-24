package com.neilturner.aerialviews.services.weather

import android.content.Context
import android.os.Bundle
import com.neilturner.aerialviews.data.network.JsonHelper.buildSerializer
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.NetworkHelpers.buildOkHttpClient
import com.neilturner.aerialviews.utils.FirebaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.kosert.flowbus.GlobalBus
import retrofit2.Retrofit
import timber.log.Timber
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WeatherService(
    val context: Context,
    private val apiOverride: OpenMeteoApi? = null,
) {
    private var updateJob: Job? = null
    private val maxRetries = 3
    private var totalUpdates = 0

    private val lookupDelay = 1.seconds
    private val errorDelay = 3.seconds // Slow down response when there is an error
    private val updateDelay = 61.minutes // Delay between full weather data updates
    private val rateLimitDelay = 1.minutes // Delay for rate limiting
    private val retryDelay = 30.seconds // Delay before retrying after an error

    private val openMeteoClient by lazy {
        apiOverride
            ?: Retrofit
                .Builder()
                .baseUrl("https://api.open-meteo.com/")
                .client(buildOkHttpClient(context))
                .addConverterFactory(buildSerializer())
                .build()
                .create(OpenMeteoApi::class.java)
    }

    suspend fun lookupLocation(query: String): List<LocationResponse> =
        try {
            val language = WeatherLanguage.getLanguageCode(context)
            val response = openMeteoClient.searchLocation(query, 10, language)
            delay(lookupDelay)

            when {
                response.isSuccessful -> {
                    response
                        .body()
                        ?.results
                        .orEmpty()
                        .map { it.toLocationResponse() }
                }

                response.code() in 500..599 -> {
                    Timber.e("Server error (${response.code()}) while fetching location data")
                    delay(errorDelay)
                    emptyList()
                }

                else -> {
                    Timber.e("Failed to fetch location data - HTTP ${response.code()}: ${response.message()}")
                    delay(errorDelay)
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch location data")
            delay(errorDelay)
            emptyList()
        }

    fun startUpdates(
        fetchCurrentWeather: Boolean,
        fetchForecast: Boolean,
    ) {
        updateJob?.cancel()
        if (!fetchCurrentWeather && !fetchForecast) {
            Timber.i("Weather updates not started because no weather overlays are active")
            return
        }

        updateJob =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    val config = buildRequestConfig()
                    val result =
                        if (config == null) {
                            WeatherResult()
                        } else {
                            fetchWeatherData(
                                requests =
                                    WeatherRequests(
                                        fetchCurrentWeather = fetchCurrentWeather,
                                        fetchForecast = fetchForecast,
                                    ),
                                config = config,
                                displayConfig = buildDisplayConfig(),
                            )
                        }
                    if (result.weather != null) GlobalBus.post(result.weather)
                    if (result.forecast != null) GlobalBus.post(result.forecast)
                    Timber.i("Next weather update in ${updateDelay.inWholeMinutes} minutes")
                    delay(updateDelay)
                }
            }
    }

    internal fun buildRequestConfig(): WeatherRequestConfig? {
        val lat = GeneralPrefs.weatherLocationLat.toDoubleOrNull()
        val lon = GeneralPrefs.weatherLocationLon.toDoubleOrNull()
        val imperial = GeneralPrefs.weatherTemperatureUnits == TemperatureUnit.IMPERIAL

        if (lat == null || lon == null) {
            Timber.e("Invalid location coordinates")
            return null
        }

        return WeatherRequestConfig(
            lat = lat,
            lon = lon,
            temperatureUnit = if (imperial) "fahrenheit" else "celsius",
            windSpeedUnit = if (imperial) "mph" else "kmh",
        )
    }

    internal fun buildDisplayConfig(): WeatherDisplayConfig {
        // Open-Meteo forecasts carry no place name, so fall back to the city part of the saved location
        val city =
            GeneralPrefs.weatherLocationCustomName.ifEmpty {
                GeneralPrefs.weatherLocationName.substringBefore(",").trim()
            }

        return WeatherDisplayConfig(
            currentWeatherCity = city,
            forecastCity = city,
            forecastDays = GeneralPrefs.weatherLine2Days.toIntOrNull() ?: 3,
        )
    }

    internal suspend fun fetchWeatherData(
        requests: WeatherRequests,
        config: WeatherRequestConfig,
        displayConfig: WeatherDisplayConfig,
    ): WeatherResult {
        if (!requests.fetchCurrentWeather && !requests.fetchForecast) {
            return WeatherResult()
        }

        val response =
            fetchForecastResponse(
                config = config,
                requests = requests,
                forecastDays = displayConfig.forecastDays,
                attempt = 0,
            )

        val weatherEvent =
            response?.current?.takeIf { requests.fetchCurrentWeather }?.let {
                mapCurrentConditions(
                    current = it,
                    windSpeedUnit = config.windSpeedUnit,
                    city = displayConfig.currentWeatherCity,
                )
            }

        val forecastEvent =
            response?.daily?.takeIf { requests.fetchForecast }?.let {
                mapDailyForecast(
                    daily = it,
                    utcOffsetSeconds = response.utcOffsetSeconds,
                    city = displayConfig.forecastCity,
                    maxDays = displayConfig.forecastDays,
                )
            }

        if (weatherEvent != null || forecastEvent != null) {
            totalUpdates++
        }

        return WeatherResult(
            weather = weatherEvent,
            forecast = forecastEvent,
        )
    }

    private suspend fun fetchForecastResponse(
        config: WeatherRequestConfig,
        requests: WeatherRequests,
        forecastDays: Int,
        attempt: Int,
    ): ForecastResponse? =
        try {
            val response =
                openMeteoClient.getForecast(
                    lat = config.lat,
                    lon = config.lon,
                    current = CURRENT_FIELDS.takeIf { requests.fetchCurrentWeather },
                    daily = DAILY_FIELDS.takeIf { requests.fetchForecast },
                    forecastDays = forecastDays.takeIf { requests.fetchForecast },
                    temperatureUnit = config.temperatureUnit,
                    windSpeedUnit = config.windSpeedUnit,
                )

            when {
                response.isSuccessful -> {
                    response.body().also { body ->
                        if (body == null) {
                            Timber.e("Received successful forecast response but body was null")
                        }
                    }
                }

                response.code() in 500..599 -> {
                    Timber.w("Forecast server error (${response.code()}) - attempt ${attempt + 1}/$maxRetries")
                    retryForecast(config, requests, forecastDays, attempt)
                }

                response.code() == 429 -> {
                    val error = "Forecast rate limit exceeded - backing off"
                    Timber.w(error)
                    FirebaseHelper.crashlyticsLogMessage(error)
                    delay(rateLimitDelay)
                    null
                }

                else -> {
                    val error = "Failed to fetch forecast - HTTP ${response.code()}: ${response.message()}"
                    Timber.e(error)
                    FirebaseHelper.crashlyticsLogMessage(error)
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch and parse forecast data")
            FirebaseHelper.crashlyticsException(e)
            null
        }

    private suspend fun retryForecast(
        config: WeatherRequestConfig,
        requests: WeatherRequests,
        forecastDays: Int,
        attempt: Int,
    ): ForecastResponse? {
        if (attempt >= maxRetries) {
            val error = "Max retries reached for forecast server errors - giving up"
            Timber.e(error)
            FirebaseHelper.crashlyticsLogMessage(error)
            return null
        }

        delay(retryDelay)
        return fetchForecastResponse(config, requests, forecastDays, attempt + 1)
    }

    internal fun mapCurrentConditions(
        current: CurrentConditions,
        windSpeedUnit: String,
        city: String,
    ): WeatherEvent {
        val temperature = current.temperature ?: return WeatherEvent()
        val code = current.weatherCode ?: -1
        val windLabel = if (windSpeedUnit == "mph") "mph" else "km/h"

        return WeatherEvent(
            temperature = "${temperature.roundToInt()}°",
            icon = WeatherCodes.getWeatherIcon(code, isDay = current.isDay != 0),
            summary = WeatherCodes.getSummary(code)?.let { context.getString(it) }.orEmpty(),
            city = city,
            wind = current.windSpeed?.let { "${it.roundToInt()} $windLabel" }.orEmpty(),
            humidity = current.humidity?.let { "$it%" }.orEmpty(),
        )
    }

    internal fun mapDailyForecast(
        daily: DailyForecast,
        utcOffsetSeconds: Int,
        city: String,
        maxDays: Int,
    ): ForecastEvent {
        val zoneOffset = ZoneOffset.ofTotalSeconds(utcOffsetSeconds)

        val days =
            daily.time
                .mapIndexedNotNull { index, time ->
                    val tempHigh = daily.temperatureMax.getOrNull(index) ?: return@mapIndexedNotNull null
                    val tempLow = daily.temperatureMin.getOrNull(index) ?: return@mapIndexedNotNull null
                    val code = daily.weatherCode.getOrNull(index) ?: -1
                    val date = Instant.ofEpochSecond(time).atOffset(zoneOffset).toLocalDate()

                    ForecastDay(
                        dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        icon = WeatherCodes.getWeatherIcon(code, isDay = true),
                        tempHigh = "${tempHigh.roundToInt()}°",
                        tempLow = "${tempLow.roundToInt()}°",
                    )
                }.take(maxDays)

        Timber.i("Processed forecast: ${days.size} days for $city")
        return ForecastEvent(days = days, city = city)
    }

    fun stop() {
        updateJob?.cancel()
        updateJob = null
        FirebaseHelper.analyticsEvent(
            "weather_updates",
            Bundle().apply {
                putDouble("value", totalUpdates.toDouble())
            },
        )
        Timber.i("Weather updates stopped, total updates for session: $totalUpdates")
    }

    companion object {
        internal const val CURRENT_FIELDS = "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,is_day"
        internal const val DAILY_FIELDS = "weather_code,temperature_2m_max,temperature_2m_min"
    }
}

data class WeatherEvent(
    val temperature: String = "",
    val icon: Int = -1,
    val summary: String = "",
    val city: String = "",
    val wind: String = "",
    val humidity: String = "",
)

data class ForecastDay(
    val dayName: String = "",
    val icon: Int = -1,
    val tempHigh: String = "",
    val tempLow: String = "",
)

data class ForecastEvent(
    val days: List<ForecastDay> = emptyList(),
    val city: String = "",
)

data class WeatherResult(
    val weather: WeatherEvent? = null,
    val forecast: ForecastEvent? = null,
)

data class WeatherRequests(
    val fetchCurrentWeather: Boolean = false,
    val fetchForecast: Boolean = false,
)

data class WeatherRequestConfig(
    val lat: Double,
    val lon: Double,
    val temperatureUnit: String,
    val windSpeedUnit: String,
)

data class WeatherDisplayConfig(
    val currentWeatherCity: String = "",
    val forecastCity: String = "",
    val forecastDays: Int = 3,
)
