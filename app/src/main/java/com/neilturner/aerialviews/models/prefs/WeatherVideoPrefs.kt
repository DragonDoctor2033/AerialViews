package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.neilturner.aerialviews.models.enums.WeatherCondition

/**
 * Settings for matching the playlist to the weather outside. Kept in the same preferences file as
 * [GeneralPrefs] so the screens, backup and restore treat them like any other setting.
 */
object WeatherVideoPrefs : KotprefModel() {
    const val KEY_RECHECK_MINUTES = "weather_videos_recheck_minutes"

    // Also the default of the setting on the Playlist screen, which names it in weather_videos_recheck_default
    const val DEFAULT_RECHECK_MINUTES = 20

    override val kotprefName = "${context.packageName}_preferences"

    // Prefer videos tagged for the current weather
    var matchWeather by booleanPref(false, "playlist_weather_match")

    // Minutes between looks at the weather while the screensaver plays, "0" is off.
    // Read it with WeatherRecheckPolicy.interval()
    var recheckMinutes by stringPref(DEFAULT_RECHECK_MINUTES.toString(), KEY_RECHECK_MINUTES)

    // A condition name to use instead of the real weather, or "OFF". For testing the tags.
    var weatherOverride by stringPref(OVERRIDE_OFF, "playlist_weather_override")

    /** The condition chosen with the testing override, or null when it is off. */
    val overrideCondition: WeatherCondition?
        get() = WeatherCondition.fromName(weatherOverride)

    const val OVERRIDE_OFF = "OFF"
}
