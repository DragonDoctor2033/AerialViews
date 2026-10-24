package com.neilturner.aerialviews.ui.effects

import kotlin.math.roundToInt

/**
 * How many streaks of rain and flakes of snow there are, which is the main thing that decides
 * what the effects cost. The count grows with the intensity of the weather, from a light shower to
 * a downpour, and is shared by the three depth layers. Nothing in here allocates, as the counts
 * are worked out on every frame, while the intensity changes.
 */
object EffectBudget {
    const val RAIN_MIN = 150
    const val RAIN_MAX = 600
    const val SNOW_MIN = 110
    const val SNOW_MAX = 400

    /** A canvas which is not hardware accelerated is much slower, so it gets less of everything */
    const val SOFTWARE_QUALITY = 0.5f

    private val rainShares = FloatArray(RainProfiles.ALL.size) { RainProfiles.ALL[it].share }
    private val snowShares = FloatArray(SnowProfiles.ALL.size) { SnowProfiles.ALL[it].share }

    /**
     * @param level the intensity of the rain, from 0 to 1
     * @param quality 1 for the full count, less for a device which can't take it
     */
    fun rainCount(
        level: Float,
        quality: Float = 1f,
    ): Int = count(RAIN_MIN, RAIN_MAX, level, quality)

    fun snowCount(
        level: Float,
        quality: Float = 1f,
    ): Int = count(SNOW_MIN, SNOW_MAX, level, quality)

    /** How many of [total] streaks are in the depth [layer], 0 for the far one to 2 for the near one */
    fun rainLayerCount(
        total: Int,
        layer: Int,
    ): Int = layerCount(total, rainShares, layer)

    fun snowLayerCount(
        total: Int,
        layer: Int,
    ): Int = layerCount(total, snowShares, layer)

    /** The most streaks a depth layer ever has, which is what its arrays are made for */
    fun rainCapacity(layer: Int): Int = rainLayerCount(RAIN_MAX, layer)

    fun snowCapacity(layer: Int): Int = snowLayerCount(SNOW_MAX, layer)

    /**
     * Shares [total] out to the layers, far to near: each gets its share, rounded, and the last
     * one what is left over, so that together they are always exactly [total].
     */
    fun layerCount(
        total: Int,
        shares: FloatArray,
        layer: Int,
    ): Int {
        if (layer !in shares.indices || total <= 0) return 0
        if (layer < shares.lastIndex) return (total * shares[layer]).roundToInt()

        var used = 0
        for (i in 0 until layer) used += (total * shares[i]).roundToInt()
        return total - used
    }

    private fun count(
        min: Int,
        max: Int,
        level: Float,
        quality: Float,
    ): Int = (EffectMath.lerp(min.toFloat(), max.toFloat(), EffectMath.clamp01(level)) * EffectMath.clamp01(quality)).roundToInt()
}
