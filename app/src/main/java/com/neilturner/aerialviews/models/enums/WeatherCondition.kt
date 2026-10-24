package com.neilturner.aerialviews.models.enums

/**
 * The kinds of weather that a video can be tagged for, and that the playlist can be matched against.
 */
enum class WeatherCondition {
    CLEAR,
    CLOUDY,
    RAIN,
    SNOW,
    STORM,
    FOG,
    ;

    /**
     * The conditions to try, after this one, when no video is tagged for it. The first condition
     * that has any tagged video wins. Clear and cloudy have no fallback.
     */
    val fallbacks: List<WeatherCondition>
        get() =
            when (this) {
                STORM -> listOf(RAIN, CLOUDY)
                RAIN, SNOW, FOG -> listOf(CLOUDY)
                CLEAR, CLOUDY -> emptyList()
            }

    /** This condition followed by its [fallbacks], in the order they are tried. */
    val lookupOrder: List<WeatherCondition>
        get() = listOf(this) + fallbacks

    companion object {
        /**
         * Maps an OpenWeather condition id (`weather[0].id`) to a condition, or null when the id is
         * not one that the playlist can match against.
         *
         * https://openweathermap.org/weather-conditions
         */
        fun fromOpenWeatherId(id: Int): WeatherCondition? =
            when (id) {
                // Thunderstorm
                in 200..299 -> STORM

                // Drizzle and rain
                in 300..399, in 500..599 -> RAIN

                // Snow
                in 600..699 -> SNOW

                // Tornado is the one atmosphere condition that is more than poor visibility
                781 -> STORM

                // Mist, smoke, haze, dust, fog, sand, ash, squalls
                in 700..799 -> FOG

                // Clear sky and few clouds
                800, 801 -> CLEAR

                // Scattered, broken and overcast clouds
                802, 803, 804 -> CLOUDY

                else -> null
            }

        /**
         * Reads a condition from its name, ignoring case. Anything else, such as the "OFF" choice of
         * the weather override setting, gives null.
         */
        fun fromName(value: String?): WeatherCondition? {
            val name = value?.trim().orEmpty()
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }

        /**
         * Writes a set of conditions as comma separated names for the playlist cache, in enum
         * order so the same set always gives the same text. An empty set (untagged) is null.
         */
        fun encode(conditions: Set<WeatherCondition>): String? =
            conditions
                .sorted()
                .joinToString(",") { it.name }
                .ifEmpty { null }

        /** Reads what [encode] wrote. Names that are not known are skipped. */
        fun decode(value: String?): Set<WeatherCondition> =
            value
                .orEmpty()
                .split(',')
                .mapNotNull { fromName(it) }
                .toSet()
    }
}
