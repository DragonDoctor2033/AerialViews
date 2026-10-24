package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.TextClock
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ClockType
import com.neilturner.aerialviews.ui.overlays.utils.ClockFormats
import com.neilturner.aerialviews.ui.overlays.utils.WallClockSeconds

class ClockOverlay : TextClock {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // The patterns TextClock chose for the language of the device. Read before any are set, as
    // these are what a clock set to "system default" would show
    private val systemFormat12 = format12Hour?.toString()
    private val systemFormat24 = format24Hour?.toString()

    private var showsSeconds = false
    private var syncing = false
    private val syncOnSecond =
        object : Runnable {
            override fun run() {
                if (!syncing) return
                refreshTimeNow()
                postDelayed(this, WallClockSeconds.millisUntilNextSecond(System.currentTimeMillis()))
            }
        }

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    /**
     * @param showSeconds adds seconds to the time. Both the 12 hour and the 24 hour pattern get
     * them, as TextClock redraws every second only when the pattern it is showing has seconds.
     * The clock then ticks once a second while it is on screen and stops when it is hidden, such
     * as in blackout. From Android 11 it is also redrawn on the second boundaries of the wall
     * clock, see [updateSync]
     */
    fun updateFormat(
        format: ClockType?,
        showSeconds: Boolean = false,
    ) {
        ClockFormats.patterns(format, showSeconds, systemFormat12, systemFormat24)?.let { patterns ->
            this.format12Hour = patterns.hour12
            this.format24Hour = patterns.hour24
        }
        // this.text.toString().lowercase() // maybe add pref? 1:30pm vs 1:30PM

        // The seconds change all the time, so keep every digit the same width. Otherwise a
        // narrow 1 would make the text, and anything lined up with it, wobble.
        fontFeatureSettings = if (showSeconds) TABULAR_DIGITS else null

        showsSeconds = showSeconds
        updateSync()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateSync()
    }

    override fun onDetachedFromWindow() {
        stopSync()
        // Fixes a commonly reported crash with this control ?!
        try {
            super.onDetachedFromWindow()
        } catch (e: Exception) {
            // Doesn't matter
        }
    }

    // Also called when an ancestor changes, such as the overlay container being hidden
    override fun onVisibilityChanged(
        changedView: View,
        visibility: Int,
    ) {
        super.onVisibilityChanged(changedView, visibility)
        updateSync()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateSync()
    }

    // TextClock redraws on its own once a second when it shows seconds, but on the second
    // boundaries of the time since boot rather than of the wall clock. Those can be any fraction
    // of a second apart, and when they are close, the seconds shown now and then skip one or
    // repeat one, and they never change at the same moment as the hand of the analog clock.
    // So while the clock is on screen it is also redrawn right on the second boundaries of the
    // wall clock. That is only possible from Android 11, which is what the Shield TV runs, and
    // the clock keeps ticking by itself on older versions.
    private fun updateSync() {
        val wanted =
            showsSeconds && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                isAttachedToWindow && isShown && windowVisibility == VISIBLE
        if (wanted) startSync() else stopSync()
    }

    private fun startSync() {
        if (syncing) return
        syncing = true
        syncOnSecond.run()
    }

    private fun stopSync() {
        if (!syncing) return
        syncing = false
        removeCallbacks(syncOnSecond)
    }

    private fun refreshTimeNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) refreshTime()
    }

    private companion object {
        const val TABULAR_DIGITS = "tnum"
    }
}
