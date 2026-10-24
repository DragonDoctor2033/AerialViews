package com.neilturner.aerialviews.services.weather

import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherEffectKind

/**
 * What the weather effects have to draw for the weather outside, kept free of android classes.
 *
 * @param kind what to draw
 * @param intensity how much of it there is, from 0 to 1: a drizzle is near 0, a downpour is near 1
 * @param windSpeedMetersPerSecond the wind speed in m/s, which tilts the rain and sends the snow sideways
 * @param windFromDegrees where the wind comes from, as OpenWeather has it: 0 is north, 90 is east
 * @param isNight dims the effects, as the footage is dark too
 */
data class WeatherEffectState(
    val kind: WeatherEffectKind,
    val intensity: Float,
    val windSpeedMetersPerSecond: Float = 0f,
    val windFromDegrees: Float = 0f,
    val isNight: Boolean = false,
) {
    companion object {
        val NONE = WeatherEffectState(WeatherEffectKind.NONE, 0f)
    }
}

/**
 * Turns the weather from OpenWeather into a [WeatherEffectState].
 *
 * The kind comes from the condition id, which tells rain from drizzle, snow, fog and clouds. The
 * intensity is a mix of what the id says (drizzle, light, heavy) and the volume of rain or snow of
 * the last hour in mm when there is one, or of the cloud cover for the clouds. Clear sky and few
 * clouds draw nothing, scattered clouds only a very light overcast tone.
 */
object WeatherEffectMapper {
    // The intensity of a condition which is picked for testing, as there is no weather to read it from
    const val TYPICAL_OVERCAST = 0.70f
    const val TYPICAL_RAIN = 0.60f
    const val TYPICAL_SNOW = 0.60f
    const val TYPICAL_STORM = 0.80f
    const val TYPICAL_FOG = 0.70f

    // A wind which tilts the rain a little, to see the tilt while testing without any weather
    const val TEST_WIND_METERS_PER_SECOND = 3.5f
    const val TEST_WIND_FROM_DEGREES = 270f

    // Below this the tone would not be seen, so there is nothing to draw
    private const val MIN_INTENSITY = 0.05f

    // How much of the intensity comes from the volume in mm when there is one, the rest is the id
    private const val VOLUME_SHARE = 0.5f

    private const val SECONDS_PER_DAY = 86_400L
    private const val NIGHT_HOURS_END = 6
    private const val NIGHT_HOURS_START = 20
    private const val ICON_NIGHT = 'n'

    private const val GROUP_THUNDERSTORM = 2
    private const val GROUP_DRIZZLE = 3
    private const val GROUP_RAIN = 5
    private const val GROUP_SNOW = 6
    private const val GROUP_ATMOSPHERE = 7
    private const val GROUP_CLOUDS = 8

    private const val TORNADO = 781
    private const val SQUALLS = 771

    // Millimeters of rain in the last hour and the intensity they stand for
    private val RAIN_MM = floatArrayOf(0f, 0.1f, 0.5f, 1f, 2.5f, 5f, 7.6f, 15f, 30f)
    private val RAIN_INTENSITY = floatArrayOf(0.05f, 0.12f, 0.25f, 0.35f, 0.50f, 0.65f, 0.75f, 0.90f, 1.00f)

    // Snow is counted in mm of water, so a millimeter is already a heavy snowfall
    private val SNOW_MM = floatArrayOf(0f, 0.25f, 0.5f, 1f, 2f, 4f)
    private val SNOW_INTENSITY = floatArrayOf(0.10f, 0.25f, 0.40f, 0.60f, 0.80f, 1.00f)

    // Percent of the sky covered and the intensity of the overcast tone. Up to a quarter is clear enough
    private val CLOUDS_PERCENT = floatArrayOf(0f, 25f, 50f, 85f, 100f)
    private val CLOUDS_INTENSITY = floatArrayOf(0f, 0f, 0.20f, 0.60f, 0.90f)

    /**
     * @param nowEpochSeconds the time now in seconds since 1970, to tell day from night with the
     * sunrise and sunset of the snapshot. It stays right when the snapshot is an hour old
     */
    fun map(
        snapshot: WeatherSnapshot,
        nowEpochSeconds: Long,
    ): WeatherEffectState {
        val (kind, intensity) = classify(snapshot)
        return WeatherEffectState(
            kind = if (intensity < MIN_INTENSITY) WeatherEffectKind.NONE else kind,
            intensity = if (intensity < MIN_INTENSITY) 0f else intensity,
            windSpeedMetersPerSecond = snapshot.windSpeedMetersPerSecond.toFloat().coerceAtLeast(0f),
            windFromDegrees = normaliseDegrees(snapshot.windFromDegrees),
            isNight = isNight(snapshot, nowEpochSeconds),
        )
    }

    /**
     * The state for a weather picked with the testing override, at a typical intensity. The wind and
     * the time of day are the real ones when the weather is known, so the effects can be tried out
     * at any time, even without a connection.
     *
     * @param snapshot the real weather, or null when it is not known
     * @param localHour the hour of the day (0 to 23) where the screen is, for the time of day when
     * there is no weather
     */
    fun forOverride(
        condition: WeatherCondition,
        snapshot: WeatherSnapshot?,
        nowEpochSeconds: Long,
        localHour: Int,
    ): WeatherEffectState {
        val (kind, intensity) =
            when (condition) {
                WeatherCondition.CLEAR -> WeatherEffectKind.NONE to 0f
                WeatherCondition.CLOUDY -> WeatherEffectKind.OVERCAST to TYPICAL_OVERCAST
                WeatherCondition.RAIN -> WeatherEffectKind.RAIN to TYPICAL_RAIN
                WeatherCondition.SNOW -> WeatherEffectKind.SNOW to TYPICAL_SNOW
                WeatherCondition.STORM -> WeatherEffectKind.STORM to TYPICAL_STORM
                WeatherCondition.FOG -> WeatherEffectKind.FOG to TYPICAL_FOG
            }

        return WeatherEffectState(
            kind = kind,
            intensity = intensity,
            windSpeedMetersPerSecond = snapshot?.windSpeedMetersPerSecond?.toFloat()?.coerceAtLeast(0f) ?: TEST_WIND_METERS_PER_SECOND,
            windFromDegrees = snapshot?.let { normaliseDegrees(it.windFromDegrees) } ?: TEST_WIND_FROM_DEGREES,
            isNight = snapshot?.let { isNight(it, nowEpochSeconds) } ?: isNightByHour(localHour),
        )
    }

    /**
     * Is it dark? The sunrise and sunset of the snapshot say so, if it has both. They are for the
     * day it was fetched, so they are moved by whole days to the day of [nowEpochSeconds], as the
     * length of the day hardly changes from one day to the next. Without them the icon says so, as
     * OpenWeather ends the icon with `n` at night.
     */
    fun isNight(
        snapshot: WeatherSnapshot,
        nowEpochSeconds: Long,
    ): Boolean {
        val sunrise = snapshot.sunriseEpochSeconds
        val sunset = snapshot.sunsetEpochSeconds
        if (sunrise != null && sunset != null && sunrise > 0 && sunset > sunrise) {
            val daylight = sunset - sunrise
            val sinceSunrise = Math.floorMod(nowEpochSeconds - sunrise, SECONDS_PER_DAY)
            return sinceSunrise >= daylight
        }
        return snapshot.icon.lastOrNull() == ICON_NIGHT
    }

    /** A guess for when there is no weather: dark from 20:00 to 06:00 */
    fun isNightByHour(localHour: Int): Boolean = localHour < NIGHT_HOURS_END || localHour >= NIGHT_HOURS_START

    private fun classify(snapshot: WeatherSnapshot): Pair<WeatherEffectKind, Float> {
        val id = snapshot.conditionId
        return when (id / 100) {
            GROUP_THUNDERSTORM -> {
                WeatherEffectKind.STORM to withVolume(stormBase(id), snapshot.rainMmPerHour, RAIN_MM, RAIN_INTENSITY)
            }

            GROUP_DRIZZLE -> {
                WeatherEffectKind.RAIN to withVolume(drizzleBase(id), snapshot.rainMmPerHour, RAIN_MM, RAIN_INTENSITY)
            }

            GROUP_RAIN -> {
                WeatherEffectKind.RAIN to withVolume(rainBase(id), snapshot.rainMmPerHour, RAIN_MM, RAIN_INTENSITY)
            }

            GROUP_SNOW -> {
                WeatherEffectKind.SNOW to withVolume(snowBase(id), snapshot.snowMmPerHour, SNOW_MM, SNOW_INTENSITY)
            }

            GROUP_ATMOSPHERE -> {
                atmosphere(id)
            }

            GROUP_CLOUDS -> {
                clouds(id, snapshot.cloudsPercent)
            }

            else -> {
                WeatherEffectKind.NONE to 0f
            }
        }
    }

    // Mist, smoke, haze, dust, fog, sand and ash are fog. A tornado is a storm, squalls are dark clouds
    private fun atmosphere(id: Int): Pair<WeatherEffectKind, Float> =
        when (id) {
            TORNADO -> WeatherEffectKind.STORM to 1f
            SQUALLS -> WeatherEffectKind.OVERCAST to 0.65f
            701 -> WeatherEffectKind.FOG to 0.35f
            711 -> WeatherEffectKind.FOG to 0.45f
            721 -> WeatherEffectKind.FOG to 0.30f
            731 -> WeatherEffectKind.FOG to 0.35f
            741 -> WeatherEffectKind.FOG to 0.70f
            751 -> WeatherEffectKind.FOG to 0.45f
            761 -> WeatherEffectKind.FOG to 0.40f
            762 -> WeatherEffectKind.FOG to 0.60f
            else -> WeatherEffectKind.FOG to 0.40f
        }

    // 800 clear and 801 few clouds draw nothing. 802 scattered clouds is a very light tone, and
    // broken and overcast clouds are told apart by the id and by how much of the sky is covered
    private fun clouds(
        id: Int,
        cloudsPercent: Int?,
    ): Pair<WeatherEffectKind, Float> {
        val base =
            when (id) {
                800, 801 -> return WeatherEffectKind.NONE to 0f
                802 -> 0.15f
                803 -> 0.50f
                804 -> 0.85f
                else -> 0.50f
            }
        val intensity =
            if (cloudsPercent == null) {
                base
            } else {
                (base + interpolate(cloudsPercent.toFloat(), CLOUDS_PERCENT, CLOUDS_INTENSITY)) / 2f
            }
        return WeatherEffectKind.OVERCAST to intensity
    }

    // Half the volume and half the id, if there is a volume. Without one the id alone is used
    private fun withVolume(
        base: Float,
        mmPerHour: Double?,
        mm: FloatArray,
        intensities: FloatArray,
    ): Float {
        val volume = mmPerHour?.toFloat()
        if (volume == null || volume <= 0f) return base.coerceIn(0f, 1f)
        val fromVolume = interpolate(volume, mm, intensities)
        return (base * (1f - VOLUME_SHARE) + fromVolume * VOLUME_SHARE).coerceIn(0f, 1f)
    }

    private fun stormBase(id: Int): Float =
        when (id) {
            200 -> 0.45f
            201 -> 0.60f
            202 -> 0.85f
            210 -> 0.35f
            211 -> 0.60f
            212 -> 0.90f
            221 -> 0.65f
            230 -> 0.40f
            231 -> 0.50f
            232 -> 0.60f
            else -> 0.60f
        }

    private fun drizzleBase(id: Int): Float =
        when (id) {
            300 -> 0.12f
            301 -> 0.18f
            302 -> 0.28f
            310 -> 0.18f
            311 -> 0.25f
            312 -> 0.35f
            313 -> 0.35f
            314 -> 0.50f
            321 -> 0.28f
            else -> 0.20f
        }

    private fun rainBase(id: Int): Float =
        when (id) {
            500 -> 0.30f
            501 -> 0.50f
            502 -> 0.75f
            503 -> 0.90f
            504 -> 1.00f
            511 -> 0.45f
            520 -> 0.40f
            521 -> 0.60f
            522 -> 0.85f
            531 -> 0.60f
            else -> 0.50f
        }

    // Sleet and rain with snow are snow with less of it, as the playlist treats them as snow too
    private fun snowBase(id: Int): Float =
        when (id) {
            600 -> 0.30f
            601 -> 0.55f
            602 -> 0.90f
            611 -> 0.40f
            612 -> 0.30f
            613 -> 0.45f
            615 -> 0.35f
            616 -> 0.50f
            620 -> 0.40f
            621 -> 0.65f
            622 -> 0.90f
            else -> 0.50f
        }

    private fun normaliseDegrees(degrees: Int): Float = (((degrees % 360) + 360) % 360).toFloat()

    // Straight lines between the points of the table, and the first or last value beyond its ends
    internal fun interpolate(
        x: Float,
        xs: FloatArray,
        ys: FloatArray,
    ): Float {
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        val upper = xs.indexOfFirst { it >= x }
        val lower = upper - 1
        val share = (x - xs[lower]) / (xs[upper] - xs[lower])
        return ys[lower] + (ys[upper] - ys[lower]) * share
    }
}
