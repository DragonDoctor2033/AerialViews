package com.neilturner.aerialviews.models.prefs

import com.chibatching.kotpref.KotprefModel
import com.neilturner.aerialviews.models.enums.WeatherEffectsFrameRate
import com.neilturner.aerialviews.models.enums.WeatherEffectsIntensity

/**
 * Settings for the weather effects: rain, snow, fog, lightning and a grey tone drawn over the video
 * to match the weather outside. They live in the same preferences file as the other overlay
 * settings, so the settings screen (settings_overlays_weather_effects.xml) can read and write them
 * through the same keys.
 */
object WeatherEffectsPrefs : KotprefModel() {
    const val KEY_ENABLED = "weather_effects_enabled"
    const val KEY_INTENSITY = "weather_effects_intensity"
    const val KEY_SKIP_ON_WEATHER_CLIPS = "weather_effects_skip_on_weather_clips"
    const val KEY_FRAME_RATE = "weather_effects_frame_rate"

    override val kotprefName = "${context.packageName}_preferences"

    // On in this fork. While it is off nothing is made, scheduled or drawn, and no weather is looked up for it
    var enabled by booleanPref(true, KEY_ENABLED)

    // Stored as the WeatherEffectsIntensity constant name, read it with WeatherEffectsIntensity.fromValue()
    var intensity by stringPref(WeatherEffectsIntensity.DEFAULT.name, KEY_INTENSITY)

    // Clips tagged with a weather already show it, so the effects go while one plays
    var skipOnWeatherClips by booleanPref(true, KEY_SKIP_ON_WEATHER_CLIPS)

    // Stored as the WeatherEffectsFrameRate constant name, read it with WeatherEffectsFrameRate.fromValue()
    var frameRate by stringPref(WeatherEffectsFrameRate.DEFAULT.name, KEY_FRAME_RATE)
}
