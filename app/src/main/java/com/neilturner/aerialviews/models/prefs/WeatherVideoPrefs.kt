package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.neilturner.aerialviews.models.enums.WeatherCondition

/**
 * Settings for matching the playlist to the weather outside. Kept in the same preferences file as
 * [GeneralPrefs] so the screens, backup and restore treat them like any other setting.
 */
object WeatherVideoPrefs : KotprefModel() {
    override val kotprefName = "${context.packageName}_preferences"

    // Prefer videos tagged for the current weather
    var matchWeather by booleanPref(false, "playlist_weather_match")

    // A condition name to use instead of the real weather, or "OFF". For testing the tags.
    var weatherOverride by stringPref(OVERRIDE_OFF, "playlist_weather_override")

    /** The condition chosen with the testing override, or null when it is off. */
    val overrideCondition: WeatherCondition?
        get() = WeatherCondition.fromName(weatherOverride)

    const val OVERRIDE_OFF = "OFF"
}
