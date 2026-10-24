package com.neilturner.aerialviews.services.weather

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The numbers of the latest current weather which the weather effects are made from: not the
 * text for the overlays, but the raw values, in metric units.
 *
 * @param conditionId the OpenWeather condition id (`weather[0].id`)
 * @param icon the OpenWeather icon (`weather[0].icon`), which ends in `n` at night
 * @param rainMmPerHour the rain of the last hour, in mm, or null when it is not raining
 * @param snowMmPerHour the snow of the last hour, in mm of water, or null when it is not snowing
 * @param cloudsPercent how much of the sky is covered, or null when it is not known
 * @param windSpeedMetersPerSecond the wind speed in m/s
 * @param windFromDegrees where the wind comes from, in degrees: 0 is north, 90 is east
 * @param sunriseEpochSeconds sunrise at the location, in seconds since 1970, or null when not known
 * @param sunsetEpochSeconds sunset at the location, in seconds since 1970, or null when not known
 */
data class WeatherSnapshot(
    val conditionId: Int,
    val icon: String = "",
    val rainMmPerHour: Double? = null,
    val snowMmPerHour: Double? = null,
    val cloudsPercent: Int? = null,
    val windSpeedMetersPerSecond: Double = 0.0,
    val windFromDegrees: Int = 0,
    val sunriseEpochSeconds: Long? = null,
    val sunsetEpochSeconds: Long? = null,
) {
    companion object {
        private const val METERS_PER_SECOND_IN_A_MILE_PER_HOUR = 0.44704

        /**
         * Takes what the effects need from a current weather answer, or null when it has no
         * weather at all.
         *
         * @param units the units the answer was asked for. Only `imperial` gives the wind in miles
         * per hour, with `metric` and `standard` it is in meters per second
         */
        fun from(
            response: CurrentWeatherResponse,
            units: String,
        ): WeatherSnapshot? {
            val weather = response.weather.firstOrNull() ?: return null
            val windSpeed =
                if (units.equals("imperial", ignoreCase = true)) {
                    response.wind.speed * METERS_PER_SECOND_IN_A_MILE_PER_HOUR
                } else {
                    response.wind.speed
                }

            return WeatherSnapshot(
                conditionId = weather.id,
                icon = weather.icon,
                rainMmPerHour = response.rain?.mmPerHour(),
                snowMmPerHour = response.snow?.mmPerHour(),
                cloudsPercent = response.clouds?.all,
                windSpeedMetersPerSecond = windSpeed,
                windFromDegrees = response.wind.deg,
                sunriseEpochSeconds = response.sys?.sunrise,
                sunsetEpochSeconds = response.sys?.sunset,
            )
        }
    }
}

/**
 * Hands the latest weather from the weather service to the weather effects, which have to get it
 * even when no weather overlay is shown. Like the condition cache, the last one is remembered, so a
 * screensaver which starts again knows the weather at once, without waiting for the network.
 */
class WeatherEffectsFeed(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** A snapshot and when it was recorded, so that an old one can be told apart */
    data class Entry(
        val snapshot: WeatherSnapshot,
        val recordedAtMillis: Long,
    )

    private val entry = MutableStateFlow<Entry?>(null)

    /** The latest entry, which is null until the first weather comes in. Collect it to hear of new weather */
    val latest: StateFlow<Entry?> = entry.asStateFlow()

    fun publish(snapshot: WeatherSnapshot) {
        entry.value = Entry(snapshot, clock())
    }

    fun clear() {
        entry.value = null
    }

    /**
     * The latest weather if it is younger than [maxAge], otherwise null. The wall clock is used on
     * purpose, as time spent asleep counts, and an entry from the future is not trusted. Weather is
     * fetched every hour, so an older one means that the updates have been failing for a while.
     */
    fun current(maxAge: Duration = DEFAULT_MAX_AGE): WeatherSnapshot? = fresh(entry.value, maxAge)

    /** The snapshot of [candidate] if it is not too old, see [current] */
    fun fresh(
        candidate: Entry?,
        maxAge: Duration = DEFAULT_MAX_AGE,
    ): WeatherSnapshot? {
        val recorded = candidate ?: return null
        val age = clock() - recorded.recordedAtMillis
        return recorded.snapshot.takeIf { age >= 0 && age < maxAge.inWholeMilliseconds }
    }

    companion object {
        val DEFAULT_MAX_AGE: Duration = 3.hours

        /** Shared by the weather service and the screen, as both run in the app process */
        val shared = WeatherEffectsFeed()
    }
}
