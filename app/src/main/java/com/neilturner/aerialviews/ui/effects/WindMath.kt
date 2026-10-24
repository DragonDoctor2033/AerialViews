package com.neilturner.aerialviews.ui.effects

import kotlin.math.sin
import kotlin.math.tan

/**
 * Turns the wind into what is seen on the screen. The footage can look in any direction, so the
 * screen is taken to look north: the east is on the right, and wind which blows towards the east
 * pushes the rain to the right.
 */
object WindMath {
    /** A wind of this speed, a strong breeze, tilts the rain as far as it goes */
    const val STRONG_WIND_METERS_PER_SECOND = 12f

    /** The most the rain is tilted out of the vertical. A storm would lay it down further, but it looks silly */
    const val MAX_TILT_DEGREES = 35f

    /**
     * How far the wind pushes things sideways on the screen, from -1 (all the way to the left) to 1
     * (all the way to the right), with 0 for no wind or wind towards or away from the viewer.
     *
     * @param fromDegrees where the wind comes from, as OpenWeather has it: 0 is north, 90 is east
     */
    fun lateralFactor(
        speedMetersPerSecond: Float,
        fromDegrees: Float,
    ): Float {
        val strength = EffectMath.clamp01(speedMetersPerSecond / STRONG_WIND_METERS_PER_SECOND)
        val towardsRadians = Math.toRadians((fromDegrees + 180f).toDouble())
        return (strength * sin(towardsRadians)).toFloat().coerceIn(-1f, 1f)
    }

    /**
     * The sideways distance rain moves for every unit it falls (the tangent of the angle out of the
     * vertical), for the [lateral] factor of the wind. Rain which falls straight down has 0.
     */
    fun rainTilt(lateral: Float): Float = tan(Math.toRadians((lateral.coerceIn(-1f, 1f) * MAX_TILT_DEGREES).toDouble())).toFloat()
}
