package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.widget.TextClock
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ClockType
import com.neilturner.aerialviews.ui.overlays.utils.ClockFormats

class ClockOverlay : TextClock {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // The patterns TextClock chose for the language of the device. Read before any are set, as
    // these are what a clock set to "system default" would show
    private val systemFormat12 = format12Hour?.toString()
    private val systemFormat24 = format24Hour?.toString()

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    /**
     * @param showSeconds adds seconds to the time. Both the 12 hour and the 24 hour pattern get
     * them, as TextClock redraws every second only when the pattern it is showing has seconds.
     * That is all it takes, the clock then ticks once a second while it is on screen and stops
     * when it is hidden, such as in blackout
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
    }

    override fun onDetachedFromWindow() {
        // Fixes a commonly reported crash with this control ?!
        try {
            super.onDetachedFromWindow()
        } catch (e: Exception) {
            // Doesn't matter
        }
    }

    private companion object {
        const val TABULAR_DIGITS = "tnum"
    }
}
