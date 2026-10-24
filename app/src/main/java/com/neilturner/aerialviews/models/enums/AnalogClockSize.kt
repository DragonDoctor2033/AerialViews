package com.neilturner.aerialviews.models.enums

/**
 * Face size of the analog clock overlay.
 *
 * The constant name is what the settings screen stores (see R.array.analog_clock_size_values),
 * so the names must not change. The enums package is kept by the R8 rules for the same reason.
 *
 * @param diameterAt1080p face diameter in pixels on a 1080p TV, where Android TV uses a density
 * of 2.0 (960 x 540 dp). Other screens scale this by their density, see AnalogClockGeometry.
 */
enum class AnalogClockSize(
    val diameterAt1080p: Int,
) {
    SMALL(160),
    MEDIUM(220),
    LARGE(300),
    ;

    companion object {
        val DEFAULT = MEDIUM

        // Unknown or missing values fall back to the default instead of failing, so a stale
        // or hand-edited preference can never stop the screensaver from starting
        fun fromValue(value: String?): AnalogClockSize = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
