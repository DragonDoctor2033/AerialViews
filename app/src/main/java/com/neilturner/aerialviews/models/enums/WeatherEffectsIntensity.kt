package com.neilturner.aerialviews.models.enums

/**
 * How strong the weather effects are drawn, which the viewer picks in the settings. It scales the
 * intensity of the weather, so light rain stays lighter than heavy rain at every level.
 *
 * The constant name is what the settings screen stores (see R.array.weather_effects_intensity_values),
 * so the names must not change. The enums package is kept by the R8 rules for the same reason.
 *
 * @param scale what the intensity of the weather, from 0 to 1, is multiplied by. The result is
 * kept to the range from 0 to 1, so the strongest rain is not any denser on a higher level
 */
enum class WeatherEffectsIntensity(
    val scale: Float,
) {
    SUBTLE(0.6f),
    NORMAL(1.0f),
    STRONG(1.4f),
    ;

    companion object {
        val DEFAULT = NORMAL

        // Unknown or missing values fall back to the default instead of failing, so a stale
        // or hand-edited preference can never stop the screensaver from starting
        fun fromValue(value: String?): WeatherEffectsIntensity = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
