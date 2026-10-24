package com.neilturner.aerialviews.ui.overlays.utils

/**
 * Timing for the clocks which show seconds, kept free of android classes so it can be unit tested
 * on the JVM.
 */
object WallClockSeconds {
    private const val MILLIS_PER_SECOND = 1000L

    /**
     * How long to wait until the wall clock reaches its next whole second, given the time in
     * milliseconds since 1970. Always between 1 and 1000: on a second boundary the next one is a
     * whole second away. A clock which redraws after this long every time stays on the second
     * boundaries, so each second is shown once, and none are skipped or shown twice.
     */
    fun millisUntilNextSecond(epochMillis: Long): Long = MILLIS_PER_SECOND - epochMillis.mod(MILLIS_PER_SECOND)
}
