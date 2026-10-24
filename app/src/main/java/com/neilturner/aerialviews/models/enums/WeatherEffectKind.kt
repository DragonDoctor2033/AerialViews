package com.neilturner.aerialviews.models.enums

/**
 * What the weather effects draw over the video for the weather outside. Rain, snow, fog and storm
 * are things which move, overcast is only a grey tone, and none draws nothing.
 */
enum class WeatherEffectKind {
    NONE,
    RAIN,
    SNOW,
    FOG,

    /** Rain with lightning */
    STORM,

    /** A grey-blue tone which dims and calms sunny footage */
    OVERCAST,
}
