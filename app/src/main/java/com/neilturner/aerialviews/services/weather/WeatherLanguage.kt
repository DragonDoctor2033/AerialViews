package com.neilturner.aerialviews.services.weather

import android.content.Context
import timber.log.Timber
import java.util.Locale

object WeatherLanguage {
    // Gets the language code for Open-Meteo place names based on device locale. The geocoding API
    // takes lower-case ISO 639-1 codes and falls back to English or the native name on its own
    fun getLanguageCode(context: Context): String {
        val langCode = getDeviceLocale(context).language.lowercase()
        Timber.i("Weather: device language: $langCode")

        // Older Java versions still report the legacy codes for these languages
        return when (langCode) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            "" -> "en"
            else -> langCode
        }
    }

    // Get the current device locale
    private fun getDeviceLocale(context: Context): Locale = context.resources.configuration.locales[0]
}
