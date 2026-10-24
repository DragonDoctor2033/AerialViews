package com.neilturner.aerialviews.services.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// Open-Meteo needs no API key: https://open-meteo.com/en/docs
interface OpenMeteoApi {
    // Null query values are left out, so one call can ask for current conditions, the daily forecast or both
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String? = null,
        @Query("daily") daily: String? = null,
        @Query("forecast_days") forecastDays: Int? = null,
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("timezone") timezone: String = "auto",
        @Query("timeformat") timeFormat: String = "unixtime",
    ): Response<ForecastResponse>

    // The geocoding API is on its own host, hence the absolute URL
    @GET("https://geocoding-api.open-meteo.com/v1/search")
    suspend fun searchLocation(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
    ): Response<GeocodingResponse>
}

// Location
data class LocationResponse(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String,
    val state: String? = null,
) {
    fun getDisplayName(): String =
        listOfNotNull(name, state, country)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
}

@Serializable
data class GeocodingResponse(
    // Missing when nothing matches
    val results: List<GeocodingResult> = emptyList(),
)

@Serializable
data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
) {
    fun toLocationResponse() =
        LocationResponse(
            name = name,
            lat = latitude,
            lon = longitude,
            country = country.orEmpty(),
            state = admin1,
        )
}

// Forecast
@Serializable
data class ForecastResponse(
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val current: CurrentConditions? = null,
    val daily: DailyForecast? = null,
)

@Serializable
data class CurrentConditions(
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("is_day") val isDay: Int = 1,
)

// Parallel arrays, one entry per day starting today. With unixtime, each time is the location's
// midnight in UTC, so utcOffsetSeconds has to be applied to get the local date
@Serializable
data class DailyForecast(
    val time: List<Long> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?> = emptyList(),
)
