package com.neilturner.aerialviews.ui.overlays.utils

import com.neilturner.aerialviews.models.enums.AnalogClockHalo
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The colour stops of the halo, ready for a radial gradient of black. [positions] run from 0 at the
 * centre to 1 at the outer edge of the halo and [alphas] are the alpha, 0 to 255, at each of them.
 */
class HaloGradient(
    val positions: FloatArray,
    val alphas: IntArray,
)

/**
 * Pure maths behind the soft dark halo drawn behind the analog clock face, kept free of android
 * classes so it can be unit tested on the JVM.
 *
 * Real background blur needs API 31 and the Shield TV is on API 30, so the clock gets a radial
 * gradient of black instead: dark in the middle, where the hands are, and fading out smoothly to
 * nothing a little way beyond the ticks.
 */
object AnalogClockHaloMath {
    /** The halo has faded out completely this many face radii from the centre */
    const val REACH = 1.25f

    /** Out to this share of the halo's radius it is as dark as in the middle, then it fades out */
    const val CORE = 0.5f

    // Enough stops that the straight lines between them look like one smooth curve
    const val GRADIENT_STOPS = 17

    private const val MAX_ALPHA = 255

    /** Radius in pixels at which the halo has faded out, for a face of the given radius */
    fun outerRadius(faceRadius: Float): Float = finiteOrZero(faceRadius) * REACH

    /**
     * Room in pixels the halo needs around the face, on every side. The view has to be this much
     * bigger than the face, or the halo would be cut off at the edge of the view. Zero when the
     * halo is off.
     */
    fun marginPx(
        faceRadius: Float,
        halo: AnalogClockHalo,
    ): Int {
        val radius = finiteOrZero(faceRadius)
        return if (halo.isVisible && radius > 0f) ceil(radius * (REACH - 1f)).toInt() else 0
    }

    /**
     * How black the halo is, from 0 to 1, at [fraction] of the way from the centre (0) to the
     * outer edge (1) of the halo. Fully dark out to [CORE], then eased out to nothing.
     */
    fun opacityAt(
        halo: AnalogClockHalo,
        fraction: Float,
    ): Float {
        // Anything which isn't a position on the halo is beyond the edge of it
        val position = if (fraction.isNaN()) 1f else fraction.coerceIn(0f, 1f)
        return halo.centreOpacity * (1f - smoothStep(CORE, 1f, position))
    }

    /** [opacityAt] as an alpha from 0 to 255 */
    fun alphaAt(
        halo: AnalogClockHalo,
        fraction: Float,
    ): Int = (opacityAt(halo, fraction) * MAX_ALPHA).roundToInt()

    fun gradient(halo: AnalogClockHalo): HaloGradient {
        val positions = FloatArray(GRADIENT_STOPS) { it / (GRADIENT_STOPS - 1f) }
        val alphas = IntArray(GRADIENT_STOPS) { alphaAt(halo, positions[it]) }
        return HaloGradient(positions, alphas)
    }

    // 0 up to edge0, 1 from edge1, and an S shaped curve in between
    private fun smoothStep(
        edge0: Float,
        edge1: Float,
        x: Float,
    ): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun finiteOrZero(value: Float): Float = if (value.isFinite()) value.coerceAtLeast(0f) else 0f
}
