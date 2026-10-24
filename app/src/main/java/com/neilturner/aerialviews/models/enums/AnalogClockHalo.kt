package com.neilturner.aerialviews.models.enums

/**
 * How strong the soft dark halo behind the analog clock face is. It keeps the thin white lines
 * readable on bright footage, such as snow or a sunny sky.
 *
 * The constant name is what the settings screen stores (see R.array.analog_clock_halo_values),
 * so the names must not change. The enums package is kept by the R8 rules for the same reason.
 *
 * @param centreOpacity how black the halo is at the very centre of the face, from 0 (nothing is
 * drawn) to 1. It fades out from there to nothing a little way beyond the edge of the face.
 */
enum class AnalogClockHalo(
    val centreOpacity: Float,
) {
    OFF(0f),
    LIGHT(0.30f),
    MEDIUM(0.45f),
    STRONG(0.60f),
    ;

    /** Is anything drawn? */
    val isVisible: Boolean get() = centreOpacity > 0f

    companion object {
        val DEFAULT = MEDIUM

        // Unknown or missing values fall back to the default instead of failing, so a stale
        // or hand-edited preference can never stop the screensaver from starting
        fun fromValue(value: String?): AnalogClockHalo = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
