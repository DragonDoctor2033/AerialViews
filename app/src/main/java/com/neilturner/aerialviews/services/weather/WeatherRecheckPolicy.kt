package com.neilturner.aerialviews.services.weather

import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.prefs.WeatherVideoPrefs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** What to do with the playlist that is playing, after looking at the weather again. */
enum class WeatherRecheckAction {
    /** The weather is the one the playlist was made for, so it keeps playing. */
    KEEP,

    /** The weather changed, so make a playlist for the new weather and play that one next. */
    REBUILD,

    /** The weather could not be found, such as without a network, so the playlist keeps playing. */
    SKIP_UNKNOWN,
}

/**
 * The decisions for looking at the weather again while the screensaver plays, apart from the
 * clock, the network and the screensaver, so that they can be tested on their own.
 */
object WeatherRecheckPolicy {
    /** The most minutes a setting can ask for. A longer one was not chosen from the list. */
    const val MAX_MINUTES = 24 * 60

    /**
     * How long to wait between looks at the weather, from the stored setting, or null when it is
     * off ("0"). A setting that is not a number of minutes, such as one that was damaged or left
     * by another version, is not off: it is the default.
     */
    fun interval(setting: String?): Duration? {
        val minutes = setting?.trim()?.toIntOrNull()
        return when {
            minutes == 0 -> null
            minutes != null && minutes in 1..MAX_MINUTES -> minutes.minutes
            else -> WeatherVideoPrefs.DEFAULT_RECHECK_MINUTES.minutes
        }
    }

    /**
     * How long until the weather is due to be looked at again, which is zero once it is due. The
     * time is counted on the wall clock, which goes on while the device sleeps. If the clock was
     * set back, how long ago the last look was is not known, so it is due. Never due when the
     * setting is off ([interval] is null).
     */
    fun timeUntilDue(
        lastCheckedAtMillis: Long?,
        nowMillis: Long,
        interval: Duration?,
    ): Duration {
        if (interval == null) return Duration.INFINITE
        if (lastCheckedAtMillis == null) return Duration.ZERO

        val sinceLastCheck = nowMillis - lastCheckedAtMillis
        if (sinceLastCheck < 0) return Duration.ZERO

        return (interval - sinceLastCheck.milliseconds).coerceAtLeast(Duration.ZERO)
    }

    /** Whether it is time to look at the weather again. The first look is due straight away. */
    fun isDue(
        lastCheckedAtMillis: Long?,
        nowMillis: Long,
        interval: Duration?,
    ): Boolean = interval != null && timeUntilDue(lastCheckedAtMillis, nowMillis, interval) == Duration.ZERO

    /**
     * Whether the weather is now something other than what the playlist was made for. A weather
     * that is not known is not a change, as nothing is known about it. A playlist that was made
     * when the weather was not known (null) is changed by any weather that is known.
     */
    fun hasChanged(
        builtFor: WeatherCondition?,
        current: WeatherCondition?,
    ): Boolean = current != null && current != builtFor

    /** What to do with the playlist, given the weather it was made for and the weather now. */
    fun decide(
        builtFor: WeatherCondition?,
        current: WeatherCondition?,
    ): WeatherRecheckAction =
        when {
            current == null -> WeatherRecheckAction.SKIP_UNKNOWN
            hasChanged(builtFor, current) -> WeatherRecheckAction.REBUILD
            else -> WeatherRecheckAction.KEEP
        }
}
