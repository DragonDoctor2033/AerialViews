package com.neilturner.aerialviews.models.enums

/**
 * How often the weather effects are drawn. 30 frames per second is lighter on weak devices, such as
 * a TV box whose GPU is also busy with the video, and is what is drawn by default. The effects are
 * made from the time which has passed, so 60 frames per second looks the same, only smoother.
 *
 * The constant name is what the settings screen stores (see R.array.weather_effects_frame_rate_values),
 * so the names must not change. The enums package is kept by the R8 rules for the same reason.
 *
 * @param fps the most frames per second which are drawn
 */
enum class WeatherEffectsFrameRate(
    val fps: Int,
) {
    FPS_60(60),
    FPS_30(30),
    ;

    companion object {
        val DEFAULT = FPS_30

        // Unknown or missing values fall back to the default instead of failing, so a stale
        // or hand-edited preference can never stop the screensaver from starting
        fun fromValue(value: String?): WeatherEffectsFrameRate = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
