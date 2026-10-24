package com.neilturner.aerialviews.ui.effects

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The maths of the fades of the weather effects. Everything fades in time which has passed, not in
 * frames, so a device which draws 30 frames a second fades just as fast as one which draws 60.
 */
object CrossfadeMath {
    /** Fading from nothing to everything takes about two seconds: the crossfade of two effects and the fade in at the start of a clip */
    const val FADE_MILLIS = 2000f

    /** The effects go quicker when a clip changes. The screen is mostly black then, so nobody sees them go */
    const val QUICK_FADE_OUT_MILLIS = 500f

    /** The wind, and the time of day, change slowly, as they are not worth noticing */
    const val SLOW_FADE_MILLIS = 3000f

    // Two values this close are the same, as far as anything which can be seen goes
    private const val NEAR = 0.0005f

    /**
     * Moves [current] towards [target] by as much as fits in [dtMillis], and never beyond it. A
     * change of 1, from nothing to everything, takes [durationMillis]. Nothing moves for a time
     * which is zero, negative or not a number.
     */
    fun approach(
        current: Float,
        target: Float,
        dtMillis: Float,
        durationMillis: Float,
    ): Float {
        if (!(dtMillis > 0f) || !(durationMillis > 0f)) return current
        val step = dtMillis / durationMillis
        return when {
            current < target -> min(current + step, target)
            current > target -> max(current - step, target)
            else -> current
        }
    }

    fun isNear(
        first: Float,
        second: Float,
    ): Boolean = abs(first - second) <= NEAR
}
