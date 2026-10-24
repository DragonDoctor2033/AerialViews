package com.neilturner.aerialviews.models.enums

/**
 * Face size of the big analog clock in the center of the screen. Unlike the clock in a slot, which
 * has a size in pixels, this one is a share of the height of the screen, so it looks the same on
 * any screen.
 *
 * The constant name is what the settings screen stores (see R.array.analog_clock_center_size_values),
 * so the names must not change. The enums package is kept by the R8 rules for the same reason.
 *
 * @param percentOfScreenHeight diameter of the face as a percentage of the height of the screen
 */
enum class AnalogClockCenterSize(
    val percentOfScreenHeight: Int,
) {
    MEDIUM(35),
    LARGE(45),
    EXTRA_LARGE(55),
    ;

    companion object {
        val DEFAULT = LARGE

        // Unknown or missing values fall back to the default instead of failing, so a stale
        // or hand-edited preference can never stop the screensaver from starting
        fun fromValue(value: String?): AnalogClockCenterSize = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
