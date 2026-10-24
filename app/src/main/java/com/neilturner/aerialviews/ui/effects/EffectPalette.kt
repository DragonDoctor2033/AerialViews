package com.neilturner.aerialviews.ui.effects

/**
 * The colours and opacities of the weather effects, kept free of android classes so that they can be
 * unit tested on the JVM. Colours are plain ints, the same as android.graphics.Color has them.
 *
 * Everything which is drawn by night is darker and fainter: the footage is dark, so a bright layer
 * on top would glow.
 */
object EffectPalette {
    /** The grey-blue tone of the sky is this opaque at the most, at an intensity of 1 */
    const val TINT_MAX_ALPHA = 0.30f

    /** The light veil which lowers the contrast in fog is this opaque at the most, at an intensity of 1 */
    const val VEIL_MAX_ALPHA = 0.34f

    /** A flash of lightning is this opaque at the most */
    const val FLASH_MAX_ALPHA = 0.70f

    /** How much brighter the rain is for the moment of a flash, at the brightest */
    const val FLASH_RAIN_BOOST = 0.8f

    /** The opacity of the layers of fog, far to near, at an intensity of 1 */
    val FOG_LAYER_ALPHA = floatArrayOf(0.46f, 0.38f, 0.30f)

    // How much fainter things are by night, from none (0) to gone (1)
    private const val NIGHT_TINT_DIM = 0.4f
    private const val NIGHT_VEIL_DIM = 0.3f
    private const val NIGHT_FOG_DIM = 0.5f
    private const val NIGHT_RAIN_DIM = 0.45f
    private const val NIGHT_SNOW_DIM = 0.4f

    // The lowest brightness the fog gets to by night, in 0 to 255, as the colour of the fog is
    // multiplied by it
    private const val NIGHT_FOG_LIGHT = 72

    // Colours by day and by night: red, green and blue
    private val TINT_DAY = intArrayOf(78, 90, 108)
    private val TINT_NIGHT = intArrayOf(10, 14, 24)
    private val VEIL_DAY = intArrayOf(216, 222, 230)
    private val VEIL_NIGHT = intArrayOf(58, 64, 78)
    private val RAIN_DAY = intArrayOf(208, 226, 255)
    private val RAIN_NIGHT = intArrayOf(150, 168, 205)
    private val FLASH = intArrayOf(236, 242, 255)

    /** The colour of the fog textures, which is white-grey with a touch of blue. Only its alpha varies */
    const val FOG_RED = 232
    const val FOG_GREEN = 236
    const val FOG_BLUE = 242

    // How much fainter things are by night, as a number to multiply the opacity with
    fun tintDim(night: Float): Float = 1f - NIGHT_TINT_DIM * EffectMath.clamp01(night)

    fun veilDim(night: Float): Float = 1f - NIGHT_VEIL_DIM * EffectMath.clamp01(night)

    fun fogDim(night: Float): Float = 1f - NIGHT_FOG_DIM * EffectMath.clamp01(night)

    fun rainDim(night: Float): Float = 1f - NIGHT_RAIN_DIM * EffectMath.clamp01(night)

    fun snowDim(night: Float): Float = 1f - NIGHT_SNOW_DIM * EffectMath.clamp01(night)

    /** The grey-blue tone over the footage: dark slate by day, almost black by night */
    fun tint(
        night: Float,
        alpha: Float,
    ): Int = colour(TINT_DAY, TINT_NIGHT, night, alpha)

    /** The light veil of fog, which lifts the dark parts of the footage */
    fun veil(
        night: Float,
        alpha: Float,
    ): Int = colour(VEIL_DAY, VEIL_NIGHT, night, alpha)

    /** White-blue rain */
    fun rain(
        night: Float,
        alpha: Float,
    ): Int = colour(RAIN_DAY, RAIN_NIGHT, night, alpha)

    /** The white-blue of a flash of lightning */
    fun flash(alpha: Float): Int = EffectMath.argb(EffectMath.toByte(alpha), FLASH[0], FLASH[1], FLASH[2])

    /**
     * What the colour of the fog is multiplied by to darken it for the night, as a grey
     * (lighting colour filter): all of it by day, and a bit under a third by night.
     */
    fun fogLight(night: Float): Int {
        val level = EffectMath.mixChannel(255, NIGHT_FOG_LIGHT, night)
        return EffectMath.argb(255, level, level, level)
    }

    private fun colour(
        day: IntArray,
        night: IntArray,
        nightShare: Float,
        alpha: Float,
    ): Int =
        EffectMath.argb(
            EffectMath.toByte(alpha),
            EffectMath.mixChannel(day[0], night[0], nightShare),
            EffectMath.mixChannel(day[1], night[1], nightShare),
            EffectMath.mixChannel(day[2], night[2], nightShare),
        )
}
