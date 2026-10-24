package com.neilturner.aerialviews.ui.effects

import kotlin.random.Random

/**
 * Soft cloud-like noise for the fog, made from random values on a grid which are blended smoothly
 * (value noise), a few times with a finer grid each time (fractal), so there are big shapes with
 * smaller ones in them. It is made once, at a low resolution, and the view stretches it over the
 * screen with a filter, which keeps it soft.
 *
 * The noise tiles: the right edge continues into the left edge, and the bottom into the top. So a
 * texture made of it can be moved across the screen for ever without a seam.
 *
 * Kept free of android classes, so that it can be tested on the JVM, and made from a seed, so that
 * the same seed always gives the same fog.
 */
object FogNoise {
    /** The side of a fog texture. Small, as it is stretched over the whole screen */
    const val TEXTURE_SIZE = 256

    // Where the clouds in the noise start and are all there: noise below the first is clear air. The
    // noise is mostly around its middle, 0.5, so these are close to it: a good half of the fog is
    // thick, and only a few of it is clear air
    private const val CLOUD_FROM = 0.30f
    private const val CLOUD_TO = 0.66f

    private const val BASE_CELLS = 3
    private const val OCTAVES = 4
    private const val PERSISTENCE = 0.55f

    /**
     * Fractal value noise of [size] times [size] values between 0 and 1, row by row.
     *
     * @param baseCells how many cells the coarsest grid has along a side. Every octave has twice as many
     * @param octaves how many grids are blended
     * @param persistence how much less each finer grid counts than the one before
     */
    fun tileable(
        size: Int,
        seed: Long,
        baseCells: Int = BASE_CELLS,
        octaves: Int = OCTAVES,
        persistence: Float = PERSISTENCE,
    ): FloatArray {
        val values = FloatArray(size * size)
        val random = Random(seed)
        var amplitude = 1f
        var total = 0f
        var cells = baseCells

        repeat(octaves) {
            val grid = FloatArray(cells * cells) { random.nextFloat() }
            addOctave(values, size, grid, cells, amplitude)
            total += amplitude
            amplitude *= persistence
            cells *= 2
        }

        for (i in values.indices) values[i] /= total
        return values
    }

    // Adds one grid of random values, blended smoothly, to the values. The cells wrap around the
    // edges, which is what makes the noise tile
    private fun addOctave(
        values: FloatArray,
        size: Int,
        grid: FloatArray,
        cells: Int,
        amplitude: Float,
    ) {
        for (y in 0 until size) {
            val cellY = y * cells / size
            val shareY = EffectMath.smoothstep((y * cells % size).toFloat() / size)
            val rowA = cellY * cells
            val rowB = ((cellY + 1) % cells) * cells

            for (x in 0 until size) {
                val cellX = x * cells / size
                val shareX = EffectMath.smoothstep((x * cells % size).toFloat() / size)
                val nextX = (cellX + 1) % cells

                val top = EffectMath.lerp(grid[rowA + cellX], grid[rowA + nextX], shareX)
                val bottom = EffectMath.lerp(grid[rowB + cellX], grid[rowB + nextX], shareX)
                values[y * size + x] += amplitude * EffectMath.lerp(top, bottom, shareY)
            }
        }
    }

    /**
     * Turns the noise into how much fog there is at each point, from 0 to 1: clear air where the
     * noise is low, then clouds which thicken, and are all there where it is high.
     */
    fun fogAmount(
        noise: Float,
        from: Float = CLOUD_FROM,
        to: Float = CLOUD_TO,
    ): Float = EffectMath.smoothstep((noise - from) / (to - from))

    /**
     * The pixels of a fog texture for a bitmap, as ARGB colours which are all one [red], [green]
     * and [blue] and differ only in their alpha, which is how much fog there is.
     */
    fun texturePixels(
        seed: Long,
        red: Int,
        green: Int,
        blue: Int,
        size: Int = TEXTURE_SIZE,
    ): IntArray {
        val noise = tileable(size, seed)
        return IntArray(noise.size) { i ->
            EffectMath.argb(EffectMath.toByte(fogAmount(noise[i])), red, green, blue)
        }
    }
}
