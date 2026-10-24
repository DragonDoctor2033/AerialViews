package com.neilturner.aerialviews.ui.effects

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * What one depth layer of rain looks like. The sizes are for a 1080p screen and are scaled by the
 * height of the screen, so the rain looks the same on every screen.
 *
 * @param share the share of all streaks which are in this layer
 * @param speedHeightsPerSecond how many screen heights a streak falls in a second
 * @param lengthPx the length of a streak. Streaks are longer than a frame of falling, which is
 * what motion blur looks like
 * @param strokePx the thickness of a streak
 * @param alpha how opaque a streak is, from 0 to 1
 */
class RainProfile(
    val share: Float,
    val speedHeightsPerSecond: Float,
    val lengthPx: Float,
    val strokePx: Float,
    val alpha: Float,
)

/** The three depth layers of the rain: those far away are many, short, slow and faint, those near are few, long, fast and bright */
object RainProfiles {
    val FAR = RainProfile(share = 0.5f, speedHeightsPerSecond = 0.80f, lengthPx = 26f, strokePx = 1.0f, alpha = 0.24f)
    val MIDDLE = RainProfile(share = 0.3f, speedHeightsPerSecond = 1.10f, lengthPx = 38f, strokePx = 1.5f, alpha = 0.34f)
    val NEAR = RainProfile(share = 0.2f, speedHeightsPerSecond = 1.50f, lengthPx = 56f, strokePx = 2.2f, alpha = 0.46f)

    val ALL = listOf(FAR, MIDDLE, NEAR)
}

/**
 * The streaks of rain of one depth layer: where they are, how they fall, and the lines to draw.
 *
 * The streaks are plain arrays, made once, and a frame allocates nothing: [step] moves them, and
 * [writeLines] fills [lines] for a single `drawLines`. All the randomness comes from the [random]
 * which is handed in, so that the movement can be tested with a seed.
 *
 * The wind is a [tilt], the sideways distance a streak moves for every unit it falls. A streak
 * which has fallen out of the bottom of the screen, or out of the side, comes back at the top.
 * Streaks which are added come in from above the screen, and those which are taken away are not
 * drawn any more, a few at a time while the intensity changes, so nothing pops.
 */
class RainLayer(
    val capacity: Int,
    private val profile: RainProfile,
    private val random: Random,
) {
    var width = 0f
        private set
    var height = 0f
        private set

    // The height of the screen in units of a 1080p screen, which all the sizes are scaled by
    private var unit = 1f

    // The head of every streak, in pixels. The tail is up the line of the fall from there
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)

    // What makes every streak a little different: how fast it falls, how long it is, how much the
    // wind tilts it more or less than the others
    private val speedJitter = FloatArray(capacity) { SPEED_JITTER_MIN + random.nextFloat() * SPEED_JITTER_RANGE }
    private val lengthJitter = FloatArray(capacity) { LENGTH_JITTER_MIN + random.nextFloat() * LENGTH_JITTER_RANGE }
    private val tiltJitter = FloatArray(capacity) { (random.nextFloat() - 0.5f) * 2f * TILT_JITTER }

    /** How many streaks there are: the first [count] are moved and drawn */
    var count = 0
        private set

    /** The lines to draw: four numbers (x1, y1, x2, y2) for every streak up to [count] */
    val lines = FloatArray(capacity * 4)

    /** The thickness of a streak in pixels, for the size of the screen */
    val strokePx: Float get() = profile.strokePx * unit

    /** The opacity of the layer, before the intensity and the time of day are in it */
    val alpha: Float get() = profile.alpha

    /** Sets the size of the screen. The streaks are put in again when they are needed */
    fun resize(
        width: Float,
        height: Float,
    ) {
        this.width = width
        this.height = height
        unit = height / REFERENCE_HEIGHT_PX
        count = 0
    }

    fun speedPx(intensity: Float): Float = profile.speedHeightsPerSecond * height * (SPEED_BASE + SPEED_SHARE * intensity)

    fun lengthPx(intensity: Float): Float = profile.lengthPx * unit * (LENGTH_BASE + LENGTH_SHARE * intensity)

    /**
     * Moves the rain on by [dtSeconds], and makes it [wantedCount] streaks, as the intensity changes.
     *
     * @param tilt the tilt of the wind, see [WindMath.rainTilt]
     * @param intensity the intensity of the rain, from 0 to 1, which makes it faster and longer
     */
    fun step(
        dtSeconds: Float,
        tilt: Float,
        intensity: Float,
        wantedCount: Int,
    ) {
        if (width <= 0f || height <= 0f) return
        val wanted = wantedCount.coerceIn(0, capacity)
        val length = lengthPx(intensity)

        if (wanted > count) {
            if (count == 0) {
                // The rain is new, so it has been falling for a while already
                scatter(wanted, tilt, length)
            } else {
                for (i in count until wanted) respawn(i, tilt, length)
            }
        }
        count = wanted
        if (count == 0 || dtSeconds <= 0f) return

        val speed = speedPx(intensity)
        val drift = tilt * height
        val minX = min(0f, -drift) - SIDE_MARGIN_PX * unit
        val maxX = max(width, width - drift) + SIDE_MARGIN_PX * unit
        for (i in 0 until count) {
            val fall = speed * speedJitter[i] * dtSeconds
            y[i] += fall
            x[i] += fall * (tilt + tiltJitter[i])
            if (y[i] - length * lengthJitter[i] > height || x[i] < minX || x[i] > maxX) {
                respawn(i, tilt, length)
            }
        }
    }

    /** Fills [lines] with the streaks. The tail of a streak is up the line of its fall from the head */
    fun writeLines(
        tilt: Float,
        intensity: Float,
    ) {
        val length = lengthPx(intensity)
        val inverse = 1f / sqrt(1f + tilt * tilt)
        val towardsTailX = tilt * inverse
        val towardsTailY = inverse
        for (i in 0 until count) {
            val streak = length * lengthJitter[i]
            val at = i * 4
            lines[at] = x[i] - towardsTailX * streak
            lines[at + 1] = y[i] - towardsTailY * streak
            lines[at + 2] = x[i]
            lines[at + 3] = y[i]
        }
    }

    // Anywhere on the screen, or just above it, as if the rain had fallen from the top already: a
    // streak which is lower has been blown sideways for longer
    private fun scatter(
        wanted: Int,
        tilt: Float,
        length: Float,
    ) {
        for (i in 0 until wanted) {
            y[i] = -length + random.nextFloat() * (height + length)
            x[i] = spawnX(tilt) + tilt * y[i]
        }
    }

    // Above the screen, where it can't be seen. How far above is up to chance, so the streaks that
    // come back do not come back in a row
    private fun respawn(
        i: Int,
        tilt: Float,
        length: Float,
    ) {
        y[i] = -length * lengthJitter[i] - random.nextFloat() * height * SPAWN_DEPTH
        x[i] = spawnX(tilt)
    }

    // Streaks move sideways while they fall, so on the side the wind comes from they have to start
    // outside the screen to arrive in it
    private fun spawnX(tilt: Float): Float {
        val drift = tilt * height
        val from = min(0f, -drift)
        val to = max(width, width - drift)
        return from + random.nextFloat() * (to - from)
    }

    companion object {
        /** The height of the screen the sizes in the profiles are meant for */
        const val REFERENCE_HEIGHT_PX = 1080f

        // The most a streak is beyond the sides of the screen before it comes back at the top
        private const val SIDE_MARGIN_PX = 160f

        // Streaks come back above the screen, up to this share of its height higher
        private const val SPAWN_DEPTH = 0.2f

        // Every streak is faster or slower than the profile by up to 15%, longer or shorter by 20%
        private const val SPEED_JITTER_MIN = 0.85f
        private const val SPEED_JITTER_RANGE = 0.30f
        private const val LENGTH_JITTER_MIN = 0.8f
        private const val LENGTH_JITTER_RANGE = 0.4f

        // And tilted by a little more or less than the wind, about 3 degrees
        private const val TILT_JITTER = 0.05f

        // Heavier rain falls faster and in longer streaks
        private const val SPEED_BASE = 0.85f
        private const val SPEED_SHARE = 0.15f
        private const val LENGTH_BASE = 0.75f
        private const val LENGTH_SHARE = 0.25f
    }
}
