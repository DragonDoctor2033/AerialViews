package com.neilturner.aerialviews.ui.overlays.utils

import com.neilturner.aerialviews.models.enums.AnalogClockSize
import kotlin.math.roundToInt

/**
 * Size in pixels of everything drawn on the analog clock face. Lengths are measured from the
 * centre of the face (ticks from the outer edge inwards), widths are stroke widths.
 */
data class ClockFaceSizes(
    val regularTickLength: Float,
    val regularTickWidth: Float,
    val quarterTickLength: Float,
    val quarterTickWidth: Float,
    val hourHandLength: Float,
    val hourHandWidth: Float,
    val minuteHandLength: Float,
    val minuteHandWidth: Float,
    val secondHandLength: Float,
    val secondHandWidth: Float,
    val centreDotRadius: Float,
)

/**
 * Proportions of the clock face, kept free of android classes so they can be unit tested.
 * Everything is a fraction of the face radius, so all three sizes look identical, just scaled.
 */
object AnalogClockGeometry {
    const val TICK_COUNT = 12

    // Android TV lays a 1080p screen out as 960 x 540 dp, which is a density of 2.0
    const val REFERENCE_DENSITY = 2f

    private const val DEGREES_PER_TICK = 30f
    private const val QUARTER_TICK_STEP = 3

    // Never thinner than a pixel, or the anti-aliased lines fade out on a small clock
    private const val MIN_STROKE_PX = 1f

    private const val REGULAR_TICK_LENGTH = 0.08f
    private const val REGULAR_TICK_WIDTH = 0.018f
    private const val QUARTER_TICK_LENGTH = 0.15f
    private const val QUARTER_TICK_WIDTH = 0.032f

    // The hands stop short of the ticks, the second hand only just
    private const val HOUR_HAND_LENGTH = 0.50f
    private const val HOUR_HAND_WIDTH = 0.040f
    private const val MINUTE_HAND_LENGTH = 0.78f
    private const val MINUTE_HAND_WIDTH = 0.030f
    private const val SECOND_HAND_LENGTH = 0.82f
    private const val SECOND_HAND_WIDTH = 0.014f

    private const val CENTRE_DOT_RADIUS = 0.05f

    fun forRadius(radius: Float): ClockFaceSizes {
        val r = if (radius.isFinite()) radius.coerceAtLeast(0f) else 0f
        return ClockFaceSizes(
            regularTickLength = r * REGULAR_TICK_LENGTH,
            regularTickWidth = stroke(r * REGULAR_TICK_WIDTH),
            quarterTickLength = r * QUARTER_TICK_LENGTH,
            quarterTickWidth = stroke(r * QUARTER_TICK_WIDTH),
            hourHandLength = r * HOUR_HAND_LENGTH,
            hourHandWidth = stroke(r * HOUR_HAND_WIDTH),
            minuteHandLength = r * MINUTE_HAND_LENGTH,
            minuteHandWidth = stroke(r * MINUTE_HAND_WIDTH),
            secondHandLength = r * SECOND_HAND_LENGTH,
            secondHandWidth = stroke(r * SECOND_HAND_WIDTH),
            centreDotRadius = r * CENTRE_DOT_RADIUS,
        )
    }

    /** Rotation of a tick mark, clockwise from 12 o'clock: 0, 30, 60 ... 330 */
    fun tickAngle(index: Int): Float = index.mod(TICK_COUNT) * DEGREES_PER_TICK

    /** The marks at 12, 3, 6 and 9 o'clock are longer and thicker than the other eight */
    fun isQuarterTick(index: Int): Boolean = index.mod(TICK_COUNT) % QUARTER_TICK_STEP == 0

    /**
     * Face diameter in pixels for a [size] on a screen with the given [density]
     * (resources.displayMetrics.density). The size is defined as pixels on a 1080p TV and scaled
     * from there, so an unusable density falls back to the TV reference.
     */
    fun diameterPx(
        size: AnalogClockSize,
        density: Float,
    ): Int {
        val scale = if (density.isFinite() && density > 0f) density else REFERENCE_DENSITY
        return (size.diameterAt1080p * scale / REFERENCE_DENSITY).roundToInt()
    }

    private fun stroke(width: Float): Float = width.coerceAtLeast(MIN_STROKE_PX)
}
