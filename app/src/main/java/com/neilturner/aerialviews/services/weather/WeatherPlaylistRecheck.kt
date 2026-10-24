package com.neilturner.aerialviews.services.weather

import com.neilturner.aerialviews.models.MediaFetchResult
import com.neilturner.aerialviews.models.enums.WeatherCondition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration

/**
 * Keeps the playlist matched to the weather while the screensaver plays.
 *
 * Every [interval] the weather is looked at again. If it is another weather than the one the
 * playlist was made for, [rebuild] makes a playlist for the new weather and hands it to
 * [onPlaylistReady], which is to play it after the video that is on screen. If the weather cannot be
 * found, or no playlist could be made, the playlist that is playing is kept, and the next look is
 * one [interval] later, so a weather or a network which comes back is noticed.
 *
 * Nothing here touches the video on screen, and only one playlist is made at a time. The timer runs
 * in [scope] and stops with [pause] and [stop]. [pause] leaves a playlist which is being made to
 * finish, as making one can write the playlist cache that the playing playlist reads from, and a
 * playlist which was made has to be played. [stop] cancels it all.
 *
 * [currentCondition] is asked for a weather no older than the interval, so that a weather which was
 * remembered for longer is looked up again. Whether the testing override wins over the real weather
 * is left to it.
 */
class WeatherPlaylistRecheck(
    private val scope: CoroutineScope,
    private val interval: Duration,
    builtFor: WeatherCondition?,
    private val currentCondition: suspend (maxAge: Duration) -> WeatherCondition?,
    private val rebuild: suspend (condition: WeatherCondition) -> MediaFetchResult?,
    private val onPlaylistReady: (result: MediaFetchResult, condition: WeatherCondition) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(interval.isPositive() && interval.isFinite()) { "The interval must be a time, not $interval" }
    }

    /** The weather the playlist that is playing was made for. Null if the weather was not known. */
    @Volatile
    var builtFor: WeatherCondition? = builtFor
        private set

    private var lastCheckedAt: Long = clock()
    private var timerJob: Job? = null
    private var rebuildJob: Job? = null
    private var stopped = false

    /** Whether the timer is running. A playlist may still be being made when it is not. */
    val isRunning: Boolean
        get() = timerJob?.isActive == true

    /**
     * Starts the timer, or starts it again after [pause]. The next look is one interval after the
     * last one, so after a long pause the weather is looked at straight away.
     */
    fun start() {
        if (stopped || isRunning) return
        Timber.i("WeatherRecheck: Looking at the weather every ${interval.inWholeMinutes} minutes, playlist is for $builtFor")
        timerJob = scope.launch { runTimer() }
    }

    /** Stops the timer, for instance in blackout. A playlist which is being made is finished. */
    fun pause() {
        timerJob?.cancel()
        timerJob = null
    }

    /** Stops for good: the timer and a playlist which is being made, which is then not played. */
    fun stop() {
        stopped = true
        pause()
        rebuildJob?.cancel()
        rebuildJob = null
    }

    private suspend fun runTimer() {
        while (true) {
            val wait = WeatherRecheckPolicy.timeUntilDue(lastCheckedAt, clock(), interval)
            if (wait > Duration.ZERO) {
                delay(wait)
            } else {
                lookAtTheWeather()
            }
        }
    }

    private suspend fun lookAtTheWeather() {
        val current = lookUpWeather()
        // After the look, not before it, so that a weather which was remembered by this look is
        // older than the interval by the time of the next one, and is not used again
        lastCheckedAt = clock()

        when (WeatherRecheckPolicy.decide(builtFor, current)) {
            WeatherRecheckAction.SKIP_UNKNOWN -> {
                Timber.i("WeatherRecheck: The weather is not known, keeping the playlist made for $builtFor")
            }

            WeatherRecheckAction.KEEP -> {
                Timber.i("WeatherRecheck: The weather is still $current, keeping the playlist")
            }

            WeatherRecheckAction.REBUILD -> {
                if (current != null) rebuildFor(current)
            }
        }
    }

    /** The weather outside, or null if it cannot be found. This must never stop the timer. */
    private suspend fun lookUpWeather(): WeatherCondition? =
        try {
            currentCondition(interval)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "WeatherRecheck: Could not look up the weather")
            null
        }

    private fun rebuildFor(condition: WeatherCondition) {
        if (rebuildJob?.isActive == true) {
            Timber.i("WeatherRecheck: Still making the last playlist, not making one for $condition yet")
            return
        }

        Timber.i("WeatherRecheck: The weather changed from $builtFor to $condition, making a new playlist")
        // Not a child of the timer, which can be paused while this goes on
        rebuildJob =
            scope.launch {
                val result =
                    try {
                        rebuild(condition)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "WeatherRecheck: Could not make a playlist for $condition")
                        null
                    }

                if (result == null || result.mediaPlaylist.size == 0) {
                    Timber.w("WeatherRecheck: No playlist for $condition, keeping the one that is playing")
                    return@launch
                }

                try {
                    onPlaylistReady(result, condition)
                    builtFor = condition
                } catch (e: Exception) {
                    Timber.e(e, "WeatherRecheck: Could not switch to the playlist for $condition")
                }
            }
    }
}
