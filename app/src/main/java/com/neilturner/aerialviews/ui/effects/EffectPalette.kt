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

    /**
     * The veil which lowers the contrast in fog is this opaque at the most, at an intensity of 1. The
     * fog of a typical foggy day, at an intensity of 0.7, has a veil of about 0.31 at the bottom of the screen
     */
    const val VEIL_MAX_ALPHA = 0.45f

    /** The veil is this much as dense at the top of the screen as it is at the bottom, like fog which lies on the ground */
    const val VEIL_TOP_SHARE = 0.70f

    /** A flash of lightning is this opaque at the most */
    const val FLASH_MAX_ALPHA = 0.70f

    /** How much brighter the rain is for the moment of a flash, at the brightest */
    const val FLASH_RAIN_BOOST = 0.8f

    /**
     * The opacity of the layers of fog, far to near, at an intensity of 1, where the noise is at its
     * thickest. Fog at an intensity of 0.7 has peaks of 0.41 to 0.49 in it
     */
    val FOG_LAYER_ALPHA = floatArrayOf(0.70f, 0.64f, 0.58f)

    // How much fainter things are by night, from none (0) to gone (1)
    private const val NIGHT_TINT_DIM = 0.4f
    private const val NIGHT_VEIL_DIM = 0.15f
    private const val NIGHT_FOG_DIM = 0.3f
    private const val NIGHT_RAIN_DIM = 0.45f

    // Snow is only a little dimmer by night, as it has to stay clearly seen
    private const val NIGHT_SNOW_DIM = 0.2f

    // The lowest brightness the fog gets to by night, in 0 to 255, as the colour of the fog is
    // multiplied by it
    private const val NIGHT_FOG_LIGHT = 96

    // Lighter snow is fainter: a base and a share of the intensity
    private const val SNOW_ALPHA_BASE = 0.85f
    private const val SNOW_ALPHA_SHARE = 0.15f

    // Colours by day and by night: red, green and blue
    private val TINT_DAY = intArrayOf(78, 90, 108)
    private val TINT_NIGHT = intArrayOf(10, 14, 24)
    private val VEIL_DAY = intArrayOf(216, 222, 230)
    private val VEIL_NIGHT = intArrayOf(80, 86, 98)
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

    /** The veil of fog, which lowers the contrast: light grey by day, and a darker grey by night so that it does not glow */
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
     * (lighting colour filter): all of it by day, and a bit over a third by night.
     */
    fun fogLight(night: Float): Int {
        val level = EffectMath.mixChannel(255, NIGHT_FOG_LIGHT, night)
        return EffectMath.argb(255, level, level, level)
    }

    /**
     * How opaque the veil of fog is at the bottom of the screen, with everything in it which is not the colour
     *
     * @param level the intensity of the fog, from 0 to 1
     * @param opacity how much of the fog is there, as it fades in and out, from 0 to 1
     */
    fun veilAlpha(
        level: Float,
        opacity: Float,
        night: Float,
    ): Float = VEIL_MAX_ALPHA * level * opacity * veilDim(night)

    /** How much of the veil there is at a [height] of the screen, from 0 (top) to 1 (bottom): the fog is denser towards the ground */
    fun veilHeightShare(height: Float): Float = EffectMath.lerp(VEIL_TOP_SHARE, 1f, EffectMath.clamp01(height))

    /** How opaque a layer of fog is where its noise is at its thickest, [layer] 0 is the far one */
    fun fogLayerAlpha(
        layer: Int,
        level: Float,
        opacity: Float,
        night: Float,
    ): Float = FOG_LAYER_ALPHA[layer] * level * opacity * fogDim(night)

    /**
     * How opaque the flakes of a layer of snow are, with everything in it which is not the colour
     *
     * @param layerAlpha the opacity of the layer, see [SnowProfile]
     * @param level the intensity of the snow, from 0 to 1
     * @param opacity how much of the snow is there, as it fades in and out, from 0 to 1
     */
    fun snowAlpha(
        layerAlpha: Float,
        level: Float,
        opacity: Float,
        night: Float,
    ): Float = layerAlpha * opacity * (SNOW_ALPHA_BASE + SNOW_ALPHA_SHARE * level) * snowDim(night)

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
