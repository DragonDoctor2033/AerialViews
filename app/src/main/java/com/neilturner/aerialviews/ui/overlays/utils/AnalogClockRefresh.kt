package com.neilturner.aerialviews.ui.overlays.utils

import com.neilturner.aerialviews.models.enums.AnalogClockSecondHandMotion

/**
 * What it takes to keep the analog clock up to date at the moment, from the least to the most.
 */
enum class ClockRefresh {
    /** Nothing. The clock is detached or can't be seen, such as in blackout, and nothing is scheduled */
    STOPPED,

    /**
     * Nothing, until it fades in. The clock is on screen with an alpha of 0, such as when auto-hide
     * has faded it out. Nothing is redrawn and nothing is scheduled, it only takes a look at the
     * alpha whenever the screen is drawn, to start again when it comes back.
     */
    WAITING_FOR_FADE_IN,

    /** Once a second, right on the second boundary of the wall clock */
    EVERY_SECOND,

    /** On every frame the display shows, for a second hand which sweeps */
    EVERY_FRAME,
}

/**
 * Decides how often the analog clock has to be drawn again, kept free of android classes so it
 * can be unit tested on the JVM.
 */
object AnalogClockRefresh {
    /**
     * Does the second hand sweep? Only when it is shown and set to move smoothly. Without a second
     * hand there is nothing to sweep, and a clock which only has an hour and a minute hand gets by
     * with an update once a second, whatever the motion is set to.
     */
    fun sweeps(
        motion: AnalogClockSecondHandMotion,
        showSecondHand: Boolean,
    ): Boolean = showSecondHand && motion == AnalogClockSecondHandMotion.SMOOTH

    /**
     * What the clock has to do now.
     *
     * @param sweeps see [sweeps]
     * @param onScreen it is attached to a window, shown (itself and everything above it) and its
     * window is visible. Not shown is how blackout looks to the clock
     * @param alpha the alpha of the view. Auto-hide fades the clock out to 0 and back in
     *
     * A ticking clock goes on once a second while it is on screen, even faded out, where it only
     * does not redraw. A sweeping clock redraws on every frame while it can be seen, and while it
     * is faded out it stops completely and waits to be faded in.
     */
    fun plan(
        sweeps: Boolean,
        onScreen: Boolean,
        alpha: Float,
    ): ClockRefresh =
        when {
            !onScreen -> ClockRefresh.STOPPED
            !sweeps -> ClockRefresh.EVERY_SECOND
            alpha > 0f -> ClockRefresh.EVERY_FRAME
            else -> ClockRefresh.WAITING_FOR_FADE_IN
        }
}
