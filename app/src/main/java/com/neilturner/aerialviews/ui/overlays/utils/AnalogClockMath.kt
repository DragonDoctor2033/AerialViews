package com.neilturner.aerialviews.ui.overlays.utils

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hand rotations in degrees, clockwise from 12 o'clock, each in the range [0, 360).
 */
data class ClockHandAngles(
    val hour: Float,
    val minute: Float,
    val second: Float,
)

/**
 * Pure maths behind the analog clock overlay, kept free of android classes so it can be
 * unit tested on the JVM.
 */
object AnalogClockMath {
    private const val HOURS_ON_FACE = 12
    private const val DEGREES_PER_HOUR = 30.0
    private const val DEGREES_PER_MINUTE = 6.0
    private const val DEGREES_PER_SECOND = 6.0
    private const val SECONDS_PER_MINUTE = 60.0
    private const val MINUTES_PER_HOUR = 60.0
    private const val MILLIS_PER_SECOND = 1000
    private const val MILLIS_PER_MINUTE = 60_000
    private const val MILLIS_PER_HOUR = 3_600_000
    private const val MILLIS_PER_HALF_DAY = 43_200_000
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val FULL_CIRCLE = 360.0

    /**
     * Angles for a time of day. The hands move continuously: the minute hand includes the
     * seconds (and milliseconds) already elapsed and the hour hand includes the minutes, so
     * 3:30 puts the hour hand half way between the 3 and the 4.
     *
     * Hours use a 12 hour face, so 15 and 3 give the same angle. Values outside the usual ranges
     * are not rejected, they carry over like a real clock would, for example 75 minutes
     * is 1 hour 15 minutes.
     */
    fun handAngles(
        hours: Int,
        minutes: Int,
        seconds: Int,
        millis: Int = 0,
    ): ClockHandAngles {
        val secondFraction = seconds + millis.toDouble() / MILLIS_PER_SECOND
        val minuteFraction = minutes + secondFraction / SECONDS_PER_MINUTE
        val hourFraction = hours % HOURS_ON_FACE + minuteFraction / MINUTES_PER_HOUR

        return ClockHandAngles(
            hour = normalise(hourFraction * DEGREES_PER_HOUR),
            minute = normalise(minuteFraction * DEGREES_PER_MINUTE),
            second = normalise(secondFraction * DEGREES_PER_SECOND),
        )
    }

    /**
     * Milliseconds since midnight on the wall, for the instant [epochMillis] (milliseconds since
     * 1970, which is what System.currentTimeMillis() gives) in a time zone which is
     * [utcOffsetMillis] ahead of UTC at that instant, or behind it when negative. Always from 0
     * up to, but not including, a whole day (86 400 000), also for instants before 1970.
     *
     * The offset is passed in, so this needs no time zone and can be tested on the JVM. The caller
     * asks the time zone for the offset in force at [epochMillis] (TimeZone.getOffset(epochMillis)),
     * which includes daylight saving time. So when the clocks change, the day jumps by the
     * difference, just like the clock on the wall does.
     */
    fun millisOfDay(
        epochMillis: Long,
        utcOffsetMillis: Int,
    ): Int = (epochMillis.mod(MILLIS_PER_DAY) + utcOffsetMillis).mod(MILLIS_PER_DAY).toInt()

    /**
     * Rotation of the hour hand at a time of the day, given as [millisOfDay], see [millisOfDay].
     * It moves continuously, a 12 hour face turns once in 12 hours, so 3:30 puts it half way
     * between the 3 and the 4. In the range [0, 360), like all the angles.
     */
    fun hourAngle(millisOfDay: Int): Float = turnAngle(millisOfDay, MILLIS_PER_HALF_DAY)

    /** Rotation of the minute hand at a time of the day. It turns once an hour, without steps */
    fun minuteAngle(millisOfDay: Int): Float = turnAngle(millisOfDay, MILLIS_PER_HOUR)

    /**
     * Rotation of the second hand at a time of the day. It turns once a minute, 6 degrees a
     * second and 0.006 of a degree for every millisecond, so a clock which draws it on every
     * frame has it sweeping smoothly instead of jumping from second to second.
     */
    fun secondAngle(millisOfDay: Int): Float = turnAngle(millisOfDay, MILLIS_PER_MINUTE)

    /**
     * Delay in milliseconds until the next whole second, given how far into the current
     * second we are. Always between 1 and 1000, so a clock updating itself with this stays on
     * the wall clock's second boundaries instead of drifting.
     */
    fun millisUntilNextSecond(millisIntoSecond: Int): Long {
        val elapsed = millisIntoSecond.coerceIn(0, MILLIS_PER_SECOND - 1)
        return (MILLIS_PER_SECOND - elapsed).toLong()
    }

    /**
     * Horizontal distance from the clock centre to the end of a hand of the given length,
     * pointing [degrees] clockwise from 12 o'clock. Positive is to the right.
     */
    fun offsetX(
        degrees: Float,
        length: Float,
    ): Float = length * sin(toRadians(degrees)).toFloat()

    /**
     * Vertical distance from the clock centre to the end of a hand of the given length,
     * pointing [degrees] clockwise from 12 o'clock. Positive is down, as on a canvas, so a hand
     * pointing at 12 o'clock has a negative offset.
     */
    fun offsetY(
        degrees: Float,
        length: Float,
    ): Float = -length * cos(toRadians(degrees)).toFloat()

    private fun toRadians(degrees: Float): Double = degrees.toDouble() * (PI / 180.0)

    // How far a hand which turns once in [periodMillis] has turned, [elapsedMillis] into its turn
    private fun turnAngle(
        elapsedMillis: Int,
        periodMillis: Int,
    ): Float = normalise(elapsedMillis.mod(periodMillis) * FULL_CIRCLE / periodMillis)

    private fun normalise(degrees: Double): Float {
        val wrapped = degrees % FULL_CIRCLE
        val positive = if (wrapped < 0.0) wrapped + FULL_CIRCLE else wrapped
        val result = positive.toFloat()
        // A value a hair below 360 rounds up to exactly 360 as a float, which is 0 on the face
        return if (result >= FULL_CIRCLE.toFloat()) 0f else result
    }
}
