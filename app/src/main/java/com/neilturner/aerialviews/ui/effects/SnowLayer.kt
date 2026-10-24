package com.neilturner.aerialviews.ui.effects

import kotlin.math.sin
import kotlin.random.Random

/**
 * What one depth layer of snow looks like. The sizes are for a 1080p screen and are scaled by the
 * height of the screen, so the snow looks the same on every screen.
 *
 * @param share the share of all flakes which are in this layer
 * @param fallPxPerSecond how fast a flake falls
 * @param diameterPx how big a flake is
 * @param swayPx how far a flake sways to each side while it falls
 * @param driftPxPerSecond how fast the wind, at its strongest, blows a flake sideways
 * @param alpha how opaque a flake is, from 0 to 1
 */
class SnowProfile(
    val share: Float,
    val fallPxPerSecond: Float,
    val diameterPx: Float,
    val swayPx: Float,
    val driftPxPerSecond: Float,
    val alpha: Float,
)

/** The three depth layers of the snow: far away the flakes are many, small, slow and faint, near they are few, big, quicker and bright */
object SnowProfiles {
    val FAR = SnowProfile(share = 0.45f, fallPxPerSecond = 55f, diameterPx = 9f, swayPx = 10f, driftPxPerSecond = 40f, alpha = 0.50f)
    val MIDDLE = SnowProfile(share = 0.35f, fallPxPerSecond = 90f, diameterPx = 14f, swayPx = 16f, driftPxPerSecond = 70f, alpha = 0.70f)
    val NEAR = SnowProfile(share = 0.2f, fallPxPerSecond = 140f, diameterPx = 24f, swayPx = 26f, driftPxPerSecond = 110f, alpha = 0.85f)

    val ALL = listOf(FAR, MIDDLE, NEAR)
}

/**
 * The flakes of snow of one depth layer: where they are and how they fall.
 *
 * Like the [RainLayer] it is plain arrays and a frame allocates nothing, and all the randomness
 * comes from the [random] which is handed in, so the movement can be tested with a seed. A flake
 * falls slowly, sways from side to side on a sine of its own, and is blown sideways by the wind.
 * It comes back at the top after the bottom, and at the other side after a side, where it can't be
 * seen, so the snow does not run out however long it snows.
 */
class SnowLayer(
    val capacity: Int,
    private val profile: SnowProfile,
    private val random: Random,
) {
    var width = 0f
        private set
    var height = 0f
        private set

    // The height of the screen in units of a 1080p screen, which all the sizes are scaled by
    private var unit = 1f

    // The middle of every flake, before the sway, in pixels
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)

    // What makes every flake a little different: where it is in its sway, how fast it sways and
    // how far, how fast it falls and how big it is
    private val phase = FloatArray(capacity) { random.nextFloat() * TWO_PI }
    private val swaySpeed = FloatArray(capacity) { SWAY_SPEED_MIN + random.nextFloat() * SWAY_SPEED_RANGE }
    private val swayScale = FloatArray(capacity) { SWAY_SCALE_MIN + random.nextFloat() * SWAY_SCALE_RANGE }
    private val fallJitter = FloatArray(capacity) { FALL_JITTER_MIN + random.nextFloat() * FALL_JITTER_RANGE }
    private val sizeJitter = FloatArray(capacity) { SIZE_JITTER_MIN + random.nextFloat() * SIZE_JITTER_RANGE }

    /** How many flakes there are: the first [count] are moved and drawn */
    var count = 0
        private set

    /** The opacity of the layer, before the intensity and the time of day are in it */
    val alpha: Float get() = profile.alpha

    /** The diameter of the biggest flake of the layer, in pixels, for the size of the screen */
    val biggestDiameterPx: Float get() = profile.diameterPx * unit * (SIZE_JITTER_MIN + SIZE_JITTER_RANGE)

    /** Sets the size of the screen. The flakes are put in again when they are needed */
    fun resize(
        width: Float,
        height: Float,
    ) {
        this.width = width
        this.height = height
        unit = height / RainLayer.REFERENCE_HEIGHT_PX
        count = 0
    }

    /** Where the flake is, with the sway */
    fun centreX(i: Int): Float = baseX(i) + sin(phase[i]) * profile.swayPx * unit * swayScale[i]

    /** Where the flake is without the sway: the place the wind and the fall have moved it to, which it sways around */
    fun baseX(i: Int): Float = x[i]

    fun centreY(i: Int): Float = y[i]

    /** How big the flake is across, in pixels */
    fun diameterPx(i: Int): Float = profile.diameterPx * unit * sizeJitter[i]

    /**
     * Moves the snow on by [dtSeconds], and makes it [wantedCount] flakes, as the intensity changes.
     *
     * @param wind how far the wind blows the snow sideways, from -1 (left) to 1 (right), see [WindMath]
     * @param intensity the intensity of the snow, from 0 to 1, which makes it fall a little faster
     */
    fun step(
        dtSeconds: Float,
        wind: Float,
        intensity: Float,
        wantedCount: Int,
    ) {
        if (width <= 0f || height <= 0f) return
        val wanted = wantedCount.coerceIn(0, capacity)

        if (wanted > count) {
            if (count == 0) {
                // The snow is new, so it has been falling for a while already
                scatter(wanted)
            } else {
                for (i in count until wanted) respawn(i)
            }
        }
        count = wanted
        if (count == 0 || dtSeconds <= 0f) return

        val margin = SIDE_MARGIN_PX * unit
        val around = width + 2f * margin
        val fall = profile.fallPxPerSecond * unit * (FALL_BASE + FALL_SHARE * intensity) * dtSeconds
        val drift = wind * profile.driftPxPerSecond * unit * dtSeconds
        for (i in 0 until count) {
            y[i] += fall * fallJitter[i]
            x[i] += drift
            phase[i] += swaySpeed[i] * dtSeconds
            if (phase[i] > TWO_PI) phase[i] -= TWO_PI

            if (x[i] < -margin) {
                x[i] += around
            } else if (x[i] > width + margin) {
                x[i] -= around
            }
            if (y[i] - diameterPx(i) / 2f > height) respawn(i)
        }
    }

    // Anywhere on the screen, or just above it
    private fun scatter(wanted: Int) {
        val margin = SIDE_MARGIN_PX * unit
        for (i in 0 until wanted) {
            x[i] = -margin + random.nextFloat() * (width + 2f * margin)
            y[i] = -diameterPx(i) + random.nextFloat() * (height + diameterPx(i))
        }
    }

    // Above the screen, where it can't be seen. How far above is up to chance, so the flakes that
    // come back do not come back in a row
    private fun respawn(i: Int) {
        val margin = SIDE_MARGIN_PX * unit
        x[i] = -margin + random.nextFloat() * (width + 2f * margin)
        y[i] = -diameterPx(i) - random.nextFloat() * height * SPAWN_DEPTH
    }

    companion object {
        private const val TWO_PI = (2.0 * Math.PI).toFloat()

        // More than the sway and half the biggest flake, so a flake is out of sight when it comes back
        private const val SIDE_MARGIN_PX = 80f

        // Flakes come back above the screen, up to this share of its height higher
        private const val SPAWN_DEPTH = 0.15f

        // A sway takes between about 2 and 5 seconds, and is a bit more or less than the profile says
        private const val SWAY_SPEED_MIN = 1.2f
        private const val SWAY_SPEED_RANGE = 1.8f
        private const val SWAY_SCALE_MIN = 0.6f
        private const val SWAY_SCALE_RANGE = 0.6f

        private const val FALL_JITTER_MIN = 0.75f
        private const val FALL_JITTER_RANGE = 0.5f
        private const val SIZE_JITTER_MIN = 0.8f
        private const val SIZE_JITTER_RANGE = 0.5f

        // Heavier snow falls a little faster
        private const val FALL_BASE = 0.85f
        private const val FALL_SHARE = 0.30f
    }
}
