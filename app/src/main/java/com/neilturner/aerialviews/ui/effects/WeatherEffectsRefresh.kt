package com.neilturner.aerialviews.ui.effects

import com.neilturner.aerialviews.models.enums.WeatherCondition

/**
 * What it takes to keep the weather effects going at the moment.
 */
enum class EffectsRefresh {
    /** Nothing. Nothing moves, the view is detached or can't be seen, such as in blackout, and nothing is scheduled */
    STOPPED,

    /**
     * Nothing, until it fades in. Something moves, but the view has an alpha of 0, so nothing is drawn
     * and nothing is scheduled. It only takes a look at the alpha whenever the screen is drawn.
     */
    WAITING_FOR_FADE_IN,

    /** On every frame, up to the frame rate which was picked */
    EVERY_FRAME,
}

/**
 * Decides when the weather effects have to be drawn again, kept free of android classes so it
 * can be unit tested on the JVM. Like the analog clock it costs nothing while it can't be seen.
 */
object WeatherEffectsRefresh {
    /**
     * What the effects have to do now.
     *
     * @param animating something which is shown moves, or is still fading. A picture which is final,
     * such as the grey tone of overcast weather once it has faded in, or no weather at all, is not
     * drawn again, and neither is a picture which can't be seen, as it is faded out
     * @param onScreen the view is attached to a window, shown (itself and everything above it) and
     * its window is visible. Not shown is how blackout looks to the view
     * @param alpha the alpha of the view
     */
    fun plan(
        animating: Boolean,
        onScreen: Boolean,
        alpha: Float,
    ): EffectsRefresh =
        when {
            !animating || !onScreen -> EffectsRefresh.STOPPED
            alpha > 0f -> EffectsRefresh.EVERY_FRAME
            else -> EffectsRefresh.WAITING_FOR_FADE_IN
        }
}

/**
 * The rule of the setting "Skip on weather clips", kept free of android classes so it can be unit
 * tested on the JVM. A clip tagged with weather already shows that weather, rain on top of a rain
 * clip would be too much, so the effects go while it plays.
 */
object WeatherEffectClipRule {
    /**
     * @param skipOnWeatherClips the setting
     * @param tags the weather the clip is tagged with, which is empty for a clip with no weather in it
     * and for photos. Any tag counts, also one which differs from the weather outside
     */
    fun shouldSkip(
        skipOnWeatherClips: Boolean,
        tags: Set<WeatherCondition>,
    ): Boolean = skipOnWeatherClips && tags.isNotEmpty()
}
