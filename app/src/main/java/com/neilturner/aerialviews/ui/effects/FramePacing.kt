package com.neilturner.aerialviews.ui.effects

/**
 * Keeps the weather effects to the frame rate the viewer picked, kept free of android classes so it
 * can be unit tested on the JVM. The effects move by the time which has passed, not by frames, so
 * a lower frame rate looks the same, only less smooth, and costs less.
 */
object FramePacing {
    /** The most time one step may move the effects on. After a stall they carry on from there instead of jumping */
    const val MAX_STEP_MILLIS = 100f

    /** The frame rate of the display, which every frame is drawn at when the frame rate is this or more */
    const val DISPLAY_FPS = 60

    // A frame which comes at 70% of the time between two frames is on time, as frames come a bit early or late
    private const val ON_TIME = 0.7

    // Waiting 85% of the time between two frames before asking for the next one skips the frames
    // in between at once, instead of waking up for each of them to find that it is too early. The
    // next frame is then the first one after that, which is the one at the full time on a display
    // whose frames come at 60, 90 or 120 a second
    private const val WAIT = 0.85

    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val MILLIS_PER_SECOND = 1_000.0
    private const val NANOS_PER_MILLI = 1_000_000f

    /** The time between two frames of [fps] frames per second, in nanoseconds */
    fun intervalNanos(fps: Int): Long = (NANOS_PER_SECOND / fps.coerceAtLeast(1)).toLong()

    /** Is it time to draw, [sinceLastDrawNanos] after the last frame was drawn? */
    fun shouldDraw(
        sinceLastDrawNanos: Long,
        fps: Int,
    ): Boolean = sinceLastDrawNanos >= intervalNanos(fps) * ON_TIME

    /**
     * How long to wait before asking for the next frame after drawing one. Nothing at 60, which
     * is the rate of the display, since then every frame is drawn, and part of the interval at a
     * lower rate.
     */
    fun waitMillis(fps: Int): Long = if (fps >= DISPLAY_FPS) 0L else (MILLIS_PER_SECOND / fps.coerceAtLeast(1) * WAIT).toLong()

    /** How far to move the effects on after [elapsedNanos] since the last frame: never back and never by more than [MAX_STEP_MILLIS] */
    fun stepMillis(elapsedNanos: Long): Float = (elapsedNanos / NANOS_PER_MILLI).coerceIn(0f, MAX_STEP_MILLIS)
}
