package com.neilturner.aerialviews.services.weather

import androidx.annotation.StringRes
import com.neilturner.aerialviews.R
import timber.log.Timber

/**
 * Maps WMO weather codes, as returned by Open-Meteo, to icons and summaries
 * Codes reference: https://open-meteo.com/en/docs (WMO Weather interpretation codes)
 */
object WeatherCodes {
    fun getWeatherIcon(
        code: Int,
        isDay: Boolean,
    ): Int {
        Timber.d("Weather condition: code=$code, isDay=$isDay")

        return when (code) {
            // Clear, mainly clear
            0, 1 -> if (isDay) R.drawable.weather_clear else R.drawable.weather_clear_night

            // Partly cloudy
            2 -> R.drawable.weather_cloudy_light

            // Overcast
            3 -> R.drawable.weather_cloudy_heavy

            // Fog
            45, 48 -> R.drawable.weather_mist

            // Drizzle, light rain and showers
            51, 53, 55, 56, 57, 61, 80 -> R.drawable.weather_rain_light

            // Rain, freezing rain, heavy showers
            63, 65, 66, 67, 81, 82 -> R.drawable.weather_rain_heavy

            // Snow, snow grains, snow showers
            71, 73, 75, 77, 85, 86 -> R.drawable.weather_snow

            // Thunderstorm, with or without hail
            95, 96, 99 -> R.drawable.weather_thunder

            else -> if (isDay) R.drawable.weather_clear else R.drawable.weather_clear_night
        }
    }

    // Null for codes outside the WMO table, so the overlay shows no summary rather than a wrong one
    @StringRes
    fun getSummary(code: Int): Int? =
        when (code) {
            0 -> R.string.weather_code_clear
            1 -> R.string.weather_code_mainly_clear
            2 -> R.string.weather_code_partly_cloudy
            3 -> R.string.weather_code_overcast
            45 -> R.string.weather_code_fog
            48 -> R.string.weather_code_rime_fog
            51 -> R.string.weather_code_drizzle_light
            53 -> R.string.weather_code_drizzle
            55 -> R.string.weather_code_drizzle_dense
            56 -> R.string.weather_code_freezing_drizzle_light
            57 -> R.string.weather_code_freezing_drizzle_dense
            61 -> R.string.weather_code_rain_light
            63 -> R.string.weather_code_rain
            65 -> R.string.weather_code_rain_heavy
            66 -> R.string.weather_code_freezing_rain_light
            67 -> R.string.weather_code_freezing_rain_heavy
            71 -> R.string.weather_code_snow_light
            73 -> R.string.weather_code_snow
            75 -> R.string.weather_code_snow_heavy
            77 -> R.string.weather_code_snow_grains
            80 -> R.string.weather_code_rain_showers_light
            81 -> R.string.weather_code_rain_showers
            82 -> R.string.weather_code_rain_showers_violent
            85 -> R.string.weather_code_snow_showers_light
            86 -> R.string.weather_code_snow_showers_heavy
            95 -> R.string.weather_code_thunderstorm
            96 -> R.string.weather_code_thunderstorm_hail_light
            99 -> R.string.weather_code_thunderstorm_hail_heavy
            else -> null
        }
}
