package com.neilturner.aerialviews.ui.effects

/**
 * The layers of fog: how big each one is stretched over the screen, which way and how fast it
 * drifts, and how it is made. Kept free of android classes, so that it can be tested on the JVM.
 *
 * The three layers drift in different directions, at different sizes, so the fog never repeats
 * itself and moves like a real bank of mist. Speeds are in widths of the screen per second, which
 * is slow: the fastest layer takes a minute and a half to cross the screen.
 */
object FogLayers {
    const val COUNT = 3

    /** Where each layer's noise comes from. Fixed, so the fog looks the same every time */
    val SEEDS = longArrayOf(0x51F0A1L, 0x2C7B93L, 0x7E4D15L)

    /**
     * How many widths of the screen one tile of the texture is stretched over: the bigger, the bigger the
     * clouds. Never less than 1, so that no tile can be seen twice side by side
     */
    val ZOOM = floatArrayOf(1.3f, 1.0f, 1.8f)

    /** Sideways drift of each layer, in widths of the screen per second. Negative is to the left */
    val SPEED_X = floatArrayOf(0.012f, -0.008f, 0.004f)

    /** Drift up or down of each layer, in widths of the screen per second. Negative is up */
    val SPEED_Y = floatArrayOf(0.002f, 0.003f, -0.005f)

    /** The wind, at its strongest, pushes the whole fog this fast, in widths of the screen per second */
    const val WIND_SPEED = 0.02f

    /**
     * Moves a layer along one axis by [dtSeconds].
     *
     * @param offset where the layer is, in pixels of the texture. The picture repeats every
     * [FogNoise.TEXTURE_SIZE], so it is kept between 0 and that, which keeps the numbers small
     * enough to stay exact however long the fog drifts
     * @param widthsPerSecond how fast it drifts, in widths of the screen per second
     * @param zoom how many widths of the screen one tile of the texture is stretched over
     */
    fun drift(
        offset: Float,
        widthsPerSecond: Float,
        zoom: Float,
        dtSeconds: Float,
    ): Float {
        val size = FogNoise.TEXTURE_SIZE.toFloat()
        // A width of the screen is this many pixels of the texture
        val texelsPerWidth = size / zoom
        var moved = (offset + widthsPerSecond * texelsPerWidth * dtSeconds) % size
        if (moved < 0f) moved += size
        // A tiny negative value plus the size can round up to the size itself
        return if (moved >= size) 0f else moved
    }
}
