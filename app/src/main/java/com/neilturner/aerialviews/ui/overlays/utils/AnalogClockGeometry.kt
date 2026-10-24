package com.neilturner.aerialviews.ui.overlays.utils

import com.neilturner.aerialviews.models.enums.AnalogClockCenterSize
import com.neilturner.aerialviews.models.enums.AnalogClockHalo
import com.neilturner.aerialviews.models.enums.AnalogClockSize
import kotlin.math.min
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
 * The one exception is the thickness of the lines, which grows a little on the bigger faces so
 * they still read from the sofa, see [strokeBoost].
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

    // A 1080p TV, which Android TV lays out at 960 x 540 dp
    private const val REFERENCE_SCREEN_HEIGHT_PX = 1080
    private const val MAX_SHARE_OF_WIDTH = 0.7
    private const val PERCENT = 100.0

    // Lines get thicker, in proportion, on bigger faces. Up to the medium clock in a corner (a
    // radius of 110 px on a 1080p TV) nothing changes, from there the boost grows in a straight
    // line to its full size at a radius of 300 px, which is about the biggest clock in the center
    const val BOOST_FROM_RADIUS = 110f
    const val BOOST_TO_RADIUS = 300f
    const val MAX_BOOST = 0.30f

    fun forRadius(radius: Float): ClockFaceSizes {
        val r = if (radius.isFinite()) radius.coerceAtLeast(0f) else 0f
        val boost = strokeBoost(r)
        return ClockFaceSizes(
            regularTickLength = r * REGULAR_TICK_LENGTH,
            regularTickWidth = stroke(r * REGULAR_TICK_WIDTH * boost),
            quarterTickLength = r * QUARTER_TICK_LENGTH,
            quarterTickWidth = stroke(r * QUARTER_TICK_WIDTH * boost),
            hourHandLength = r * HOUR_HAND_LENGTH,
            hourHandWidth = stroke(r * HOUR_HAND_WIDTH * boost),
            minuteHandLength = r * MINUTE_HAND_LENGTH,
            minuteHandWidth = stroke(r * MINUTE_HAND_WIDTH * boost),
            secondHandLength = r * SECOND_HAND_LENGTH,
            secondHandWidth = stroke(r * SECOND_HAND_WIDTH * boost),
            centreDotRadius = r * CENTRE_DOT_RADIUS * boost,
        )
    }

    /**
     * How many times thicker than its proportion a line is drawn on a face of this [radius] in
     * pixels: 1 up to [BOOST_FROM_RADIUS], then more and more up to 1 + [MAX_BOOST] at
     * [BOOST_TO_RADIUS] and beyond. Thin hands that look fine on a small clock get lost on a
     * big one, seen from across the room.
     */
    fun strokeBoost(radius: Float): Float {
        if (!radius.isFinite()) return 1f
        val progress = (radius - BOOST_FROM_RADIUS) / (BOOST_TO_RADIUS - BOOST_FROM_RADIUS)
        return 1f + MAX_BOOST * progress.coerceIn(0f, 1f)
    }

    /**
     * Face diameter in pixels of the big clock in the center, which is a share of the height of
     * the screen: 35, 45 or 55 percent, so 378, 486 or 594 px on a 1080p TV. A screen which is
     * taller than it is wide, such as a phone held upright, would get a clock wider than the
     * screen, so the face is held to 70 percent of the width, which leaves room for the halo
     * around it. A screen with no usable size is taken to be a 1080p TV.
     */
    fun centerDiameterPx(
        size: AnalogClockCenterSize,
        screenHeightPx: Int,
        screenWidthPx: Int,
    ): Int {
        val height = if (screenHeightPx > 0) screenHeightPx else REFERENCE_SCREEN_HEIGHT_PX
        val wanted = (height * size.percentOfScreenHeight / PERCENT).roundToInt()
        if (screenWidthPx <= 0) return wanted
        return min(wanted, (screenWidthPx * MAX_SHARE_OF_WIDTH).roundToInt())
    }

    /**
     * Side of the square view in pixels: the face, plus all round it the room which the [halo]
     * needs to fade out in, or just the face if there is none.
     */
    fun viewSizePx(
        faceDiameterPx: Int,
        halo: AnalogClockHalo,
    ): Int {
        val face = faceDiameterPx.coerceAtLeast(0)
        return face + 2 * AnalogClockHaloMath.marginPx(face / 2f, halo)
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
