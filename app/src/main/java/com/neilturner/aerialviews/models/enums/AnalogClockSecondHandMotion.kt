package com.neilturner.aerialviews.models.enums

/**
 * How the second hand of the analog clock moves.
 *
 * The constant name is what the settings screen stores (see R.array.analog_clock_second_hand_motion_values),
 * so the names must not change. The enums package is kept by the R8 rules for the same reason.
 */
enum class AnalogClockSecondHandMotion {
    /**
     * Sweeps round without stopping, like the hand of a luxury watch. The clock is drawn again on
     * every frame the display shows while it can be seen.
     */
    SMOOTH,

    /** Jumps once a second, like a quartz watch. The clock is drawn again once a second. */
    TICK,
    ;

    companion object {
        val DEFAULT = SMOOTH

        // Unknown or missing values fall back to the default instead of failing, so a stale
        // or hand-edited preference can never stop the screensaver from starting
        fun fromValue(value: String?): AnalogClockSecondHandMotion = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
