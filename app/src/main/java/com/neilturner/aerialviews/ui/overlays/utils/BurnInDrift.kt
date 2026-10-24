package com.neilturner.aerialviews.ui.overlays.utils

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** How far the clock is moved from its place. Positive x is to the right, positive y is down */
data class DriftOffset(
    val x: Float,
    val y: Float,
)

/**
 * Burn-in protection for the big clock in the center of the screen, which never moves otherwise.
 * Every minute it is shifted a few dp to the next spot on a small circle, so a lap takes 24
 * minutes and the thin lines of the face are smeared over a ring instead of burning in one place.
 * The circle is centered on the real position, so the clock is on average exactly in the center.
 *
 * Pure maths, kept free of android classes so it can be unit tested on the JVM.
 */
object BurnInDrift {
    /** The clock is never moved further than this from its place, in either direction, in dp */
    const val MAX_OFFSET_DP = 8f

    /** Radius of the circle, in dp */
    const val ORBIT_RADIUS_DP = 6f

    /** Spots on the circle. One per minute */
    const val ORBIT_STEPS = 24

    private const val FULL_CIRCLE = 2.0 * PI

    /**
     * Where the clock is during the given minute, any number of minutes counted from any fixed
     * time such as the epoch. Minutes before that time work too, the pattern just keeps going.
     */
    fun offsetDp(minute: Long): DriftOffset {
        val step = minute.mod(ORBIT_STEPS.toLong()).toInt()
        val angle = FULL_CIRCLE * step / ORBIT_STEPS
        return DriftOffset(
            x = (ORBIT_RADIUS_DP * cos(angle)).toFloat(),
            y = (ORBIT_RADIUS_DP * sin(angle)).toFloat(),
        )
    }

    /**
     * [offsetDp] in whole pixels for a screen with the given [density]
     * (resources.displayMetrics.density). Whole pixels, so the drawing is never blurred by being
     * shifted half a pixel. An unusable density counts as 1.
     */
    fun offsetPx(
        minute: Long,
        density: Float,
    ): DriftOffset {
        val scale = if (density.isFinite() && density > 0f) density else 1f
        val dp = offsetDp(minute)
        return DriftOffset(
            x = (dp.x * scale).roundToInt().toFloat(),
            y = (dp.y * scale).roundToInt().toFloat(),
        )
    }
}
