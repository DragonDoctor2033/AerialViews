package com.neilturner.aerialviews.ui.effects

/**
 * Small helpers for the weather effects. They are free of android classes, so that they can be
 * tested on the JVM, and they allocate nothing, as they run on every frame.
 */
object EffectMath {
    /** Keeps a share between 0 and 1. Anything which is not a number is 0 */
    fun clamp01(value: Float): Float =
        when {
            value > 1f -> 1f
            value > 0f -> value
            else -> 0f
        }

    fun lerp(
        from: Float,
        to: Float,
        share: Float,
    ): Float = from + (to - from) * share

    /**
     * An S-shaped curve from 0 to 1 which is flat at both ends. The progress of a fade is linear, and
     * this is what it looks like when it starts and stops softly, instead of with a jolt.
     */
    fun smoothstep(progress: Float): Float {
        val t = clamp01(progress)
        return t * t * (3f - 2f * t)
    }

    /** The same colour as android.graphics.Color.argb(), which a unit test can't call. All four are 0 to 255 */
    fun argb(
        alpha: Int,
        red: Int,
        green: Int,
        blue: Int,
    ): Int = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    /** A share from 0 to 1 as a whole number from 0 to 255 */
    fun toByte(share: Float): Int = (clamp01(share) * 255f + 0.5f).toInt()

    /** One colour channel on the way from its value by day to its value by night, as a whole number */
    fun mixChannel(
        day: Int,
        night: Int,
        nightShare: Float,
    ): Int = (lerp(day.toFloat(), night.toFloat(), clamp01(nightShare)) + 0.5f).toInt()
}
