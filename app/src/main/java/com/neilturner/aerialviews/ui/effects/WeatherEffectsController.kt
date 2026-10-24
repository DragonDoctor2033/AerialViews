package com.neilturner.aerialviews.ui.effects

import com.neilturner.aerialviews.models.enums.SceneType
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherEffectsFrameRate
import com.neilturner.aerialviews.models.enums.WeatherEffectsIntensity
import com.neilturner.aerialviews.models.prefs.WeatherEffectsPrefs
import com.neilturner.aerialviews.models.prefs.WeatherVideoPrefs
import com.neilturner.aerialviews.services.weather.WeatherEffectMapper
import com.neilturner.aerialviews.services.weather.WeatherEffectState
import com.neilturner.aerialviews.services.weather.WeatherEffectsFeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalTime

/**
 * The settings of the weather effects, as they were when the screensaver started.
 */
data class WeatherEffectsSettings(
    val enabled: Boolean,
    val intensity: WeatherEffectsIntensity,
    val skipOnWeatherClips: Boolean,
    val frameRate: WeatherEffectsFrameRate,
) {
    companion object {
        fun fromPrefs() =
            WeatherEffectsSettings(
                enabled = WeatherEffectsPrefs.enabled,
                intensity = WeatherEffectsIntensity.fromValue(WeatherEffectsPrefs.intensity),
                skipOnWeatherClips = WeatherEffectsPrefs.skipOnWeatherClips,
                frameRate = WeatherEffectsFrameRate.fromValue(WeatherEffectsPrefs.frameRate),
            )
    }
}

/**
 * Tells the view that draws the weather effects what the weather is, and when it may be seen.
 * The screen controller calls it where it knows what is playing, and it does the rest:
 *
 * - The weather comes from the [feed], which the weather service fills (also when no weather
 *   overlay is shown), or from the testing override of the playlist settings, which wins and has
 *   a typical intensity so the effects can be tried out at any time.
 * - The effects are not seen while a clip loads, so they fade in when it is shown, and out when
 *   the next one loads. On a clip tagged with weather they are not seen at all, if the setting
 *   "Skip on weather clips" is on, as that clip already shows weather. Nor are they on footage from
 *   space, whatever the settings say, see [WeatherEffectClipRule].
 * - In blackout the view is hidden and everything stops.
 * - When the effects are off in the settings, nothing is done at all: the view is taken out of
 *   the layout, no weather is listened for, and the calls of the screen controller do nothing.
 *
 * It does not touch android classes itself, only the [surface], so it can be tested with a fake.
 */
class WeatherEffectsController(
    private val surface: EffectsSurface,
    private val scope: CoroutineScope,
    private val settings: WeatherEffectsSettings = WeatherEffectsSettings.fromPrefs(),
    private val feed: WeatherEffectsFeed = WeatherEffectsFeed.shared,
    private val overrideSource: () -> WeatherCondition? = { WeatherVideoPrefs.overrideCondition },
    private val clock: () -> Long = System::currentTimeMillis,
    private val hourSource: () -> Int = { LocalTime.now().hour },
) {
    private var started = false
    private var feedJob: Job? = null

    // The effects may only be seen once the clip is shown, and when it is not a clip with weather
    private var clipAllowed = true
    private var clipShown = false
    private var blackout = false
    private var lastState: WeatherEffectState? = null

    /** Do the effects need the weather to be looked up, even when no weather overlay is shown? */
    val needsWeather: Boolean get() = settings.enabled

    fun start() {
        if (started) return
        if (!settings.enabled) {
            Timber.i("WeatherEffects: Off")
            surface.disable()
            return
        }

        started = true
        surface.setFrameRate(settings.frameRate.fps)
        // Weather which comes in later is faded to, while the clip plays
        feedJob = scope.launch { feed.latest.collect { apply() } }
        apply()
    }

    /**
     * A new clip, or photo, is being loaded. The effects go until it is shown, and stay away if it
     * is tagged with weather and that is to be skipped, or if it is footage from space.
     *
     * @param weatherTags the weather the clip is tagged with, which is empty when it has none
     * @param scene what the clip shows, which is unknown when it has no scene
     */
    fun onMediaChanged(
        weatherTags: Set<WeatherCondition>,
        scene: SceneType = SceneType.UNKNOWN,
    ) {
        if (!started) return
        clipAllowed = !WeatherEffectClipRule.shouldSkip(settings.skipOnWeatherClips, weatherTags, scene)
        clipShown = false
        if (WeatherEffectClipRule.shouldSkipScene(scene)) {
            Timber.i("WeatherEffects: Skipped, the clip is a $scene scene")
        } else if (!clipAllowed) {
            Timber.i("WeatherEffects: Skipped, the clip is tagged $weatherTags")
        }
        apply()
    }

    /** The clip is on screen, so the effects can fade in on it */
    fun onMediaShown() {
        if (!started) return
        clipShown = true
        apply()
    }

    /** The screen goes black, or comes back from it. A blackout ends the clip which was shown, the next one is shown again */
    fun setBlackout(blackout: Boolean) {
        if (!started) return
        this.blackout = blackout
        if (blackout) clipShown = false
        apply()
        surface.setBlackout(blackout)
    }

    /** The screen is over. The view is let go of, and the calls after this do nothing */
    fun stop() {
        if (!started) return
        started = false
        feedJob?.cancel()
        feedJob = null
        surface.release()
    }

    private fun apply() {
        val state = currentState()
        if (state != lastState) {
            Timber.i("WeatherEffects: ${state.kind} at ${state.intensity}${if (state.isNight) ", at night" else ""}")
            lastState = state
        }
        surface.setTargets(EffectTargets.from(state, settings.intensity, shown = clipAllowed && clipShown && !blackout))
    }

    /** What the weather is now: the testing override, else the latest weather if it is not too old, else nothing */
    internal fun currentState(): WeatherEffectState {
        val nowSeconds = clock() / MILLIS_PER_SECOND
        val snapshot = feed.current()
        val override = overrideSource()
        return when {
            override != null -> WeatherEffectMapper.forOverride(override, snapshot, nowSeconds, hourSource())
            snapshot != null -> WeatherEffectMapper.map(snapshot, nowSeconds)
            else -> WeatherEffectState.NONE
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
