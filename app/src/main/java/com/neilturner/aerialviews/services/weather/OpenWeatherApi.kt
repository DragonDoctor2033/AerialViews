package com.neilturner.aerialviews.services.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherApi {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") language: String = "en",
    ): Response<CurrentWeatherResponse>

    @GET("data/2.5/forecast")
    suspend fun getForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") language: String = "en",
    ): Response<FiveDayForecastResponse>

    @GET("geo/1.0/direct")
    suspend fun getLocationByName(
        @Query("q") locationName: String,
        @Query("limit") limit: Int = 10,
        @Query("appid") apiKey: String,
        @Query("lang") language: String = "en",
    ): Response<List<LocationResponse>>

    @GET("geo/1.0/reverse")
    suspend fun getLocationByCoordinates(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("limit") limit: Int = 5,
        @Query("appid") apiKey: String,
        @Query("lang") language: String = "en",
    ): Response<List<LocationResponse>>
}

// Location
@Serializable
data class LocationResponse(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String,
    val state: String? = null,
) {
    fun getDisplayName(): String =
        if (state != null) {
            "$name, $state, $country"
        } else {
            "$name, $country"
        }
}

// Weather (shared by current and forecast)
@Serializable
data class MainWeatherData(
    val temp: Double,
    @SerialName("feels_like") val feelsLike: Double,
    @SerialName("temp_min") val tempMin: Double,
    @SerialName("temp_max") val tempMax: Double,
    val pressure: Int,
    val humidity: Int,
)

@Serializable
data class Weather(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String,
)

@Serializable
data class Wind(
    val speed: Double,
    val deg: Int,
)

// Rain or snow, in mm of water. The API names the keys "1h" and "3h", which are no identifiers
@Serializable
data class Precipitation(
    @SerialName("1h") val oneHour: Double? = null,
    @SerialName("3h") val threeHours: Double? = null,
) {
    /** The volume of the last hour, or a third of the last three hours when only those are known */
    fun mmPerHour(): Double? = oneHour ?: threeHours?.div(3.0)
}

@Serializable
data class Clouds(
    val all: Int? = null,
)

// The "sys" part of the answer also has the country, which is not read
@Serializable
data class SunTimes(
    val sunrise: Long? = null,
    val sunset: Long? = null,
)

// Current Weather. Everything after the name is optional, as the API leaves out rain, snow and
// clouds when there are none, so that an answer without them still parses
@Serializable
data class CurrentWeatherResponse(
    val weather: List<Weather>,
    val main: MainWeatherData,
    val wind: Wind,
    val name: String,
    val rain: Precipitation? = null,
    val snow: Precipitation? = null,
    val clouds: Clouds? = null,
    val sys: SunTimes? = null,
)

// 5 Day Forecast
@Serializable
data class FiveDayForecastResponse(
    val list: List<ForecastItem>,
    val city: City,
)

@Serializable
data class ForecastItem(
    val dt: Long,
    val main: MainWeatherData,
    val weather: List<Weather>,
    val wind: Wind,
    @SerialName("dt_txt") val dtTxt: String,
)

@Serializable
data class City(
    val name: String,
    val country: String,
    val timezone: Int = 0,
)
