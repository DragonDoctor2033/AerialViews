package com.neilturner.aerialviews.ui.overlays.utils

import com.neilturner.aerialviews.models.enums.ClockType

/**
 * The two patterns a TextClock is given. It shows [hour12] when the system is set to 12 hour time
 * and [hour24] when it is set to 24 hour time.
 */
data class ClockPatterns(
    val hour12: String,
    val hour24: String,
)

/**
 * Time patterns of the digital clock overlay, in the syntax of android.text.format.DateFormat.
 * Kept free of android classes so it can be unit tested on the JVM.
 *
 * A TextClock only redraws every second when the pattern it is showing contains seconds, so with
 * seconds on, both patterns always have them. Otherwise a system set to the other hour mode would
 * quietly stay on one update a minute.
 */
object ClockFormats {
    const val HOUR_24 = "HH:mm"
    const val HOUR_24_SECONDS = "HH:mm:ss"
    const val HOUR_12 = "h:mm a"
    const val HOUR_12_SECONDS = "h:mm:ss a"

    private const val SECONDS = 's'
    private const val MINUTES = 'm'
    private const val QUOTE = '\''
    private const val SECONDS_FIELD = "ss"
    private const val DEFAULT_SEPARATOR = ":"

    // The letters of the hour fields, h and K for 12 hour time, H and k for 24 hour time
    private val hourFields = setOf('h', 'H', 'k', 'K')

    // What sits between the hours and the minutes in the time formats of the world's locales
    private val separators = setOf(':', '.', '：')

    fun hour24(showSeconds: Boolean): String = if (showSeconds) HOUR_24_SECONDS else HOUR_24

    fun hour12(showSeconds: Boolean): String = if (showSeconds) HOUR_12_SECONDS else HOUR_12

    /**
     * The patterns for a clock format setting, or null when the clock should be left alone to
     * follow the system. That is the case for [ClockType.DEFAULT] without seconds.
     *
     * With seconds, the default follows the system's own style: the patterns it would have used,
     * [systemHour12] and [systemHour24], get the seconds added. Those are the patterns the
     * TextClock picked for the language of the device. Without them the English style is used.
     */
    fun patterns(
        type: ClockType?,
        showSeconds: Boolean,
        systemHour12: String? = null,
        systemHour24: String? = null,
    ): ClockPatterns? =
        when (type) {
            ClockType.HOUR_24 -> {
                hour24(showSeconds).let { ClockPatterns(hour12 = it, hour24 = it) }
            }

            ClockType.HOUR_12 -> {
                hour12(showSeconds).let { ClockPatterns(hour12 = it, hour24 = it) }
            }

            ClockType.DEFAULT, null -> {
                if (showSeconds) {
                    ClockPatterns(
                        hour12 = withSeconds(systemHour12?.takeIf { it.isNotBlank() } ?: HOUR_12),
                        hour24 = withSeconds(systemHour24?.takeIf { it.isNotBlank() } ?: HOUR_24),
                    )
                } else {
                    null
                }
            }
        }

    /** Does the pattern show seconds? A quoted letter is only text, such as 'sec' */
    fun hasSeconds(pattern: String): Boolean = fieldRuns(pattern).any { it.letter == SECONDS }

    /**
     * Adds seconds to a time pattern, straight after the minutes and in the same style: `HH:mm`
     * becomes `HH:mm:ss`, `h:mm a` becomes `h:mm:ss a` and the Finnish `H.mm` becomes `H.mm.ss`.
     * A pattern which already has seconds is returned as it is, so it is safe to apply twice.
     * A pattern with no minutes at all just gets `:ss` on the end.
     */
    fun withSeconds(pattern: String): String {
        val runs = fieldRuns(pattern)
        if (runs.any { it.letter == SECONDS }) return pattern

        val minutes =
            runs.lastOrNull { it.letter == MINUTES }
                ?: return pattern + DEFAULT_SEPARATOR + SECONDS_FIELD

        val separator = separatorBefore(pattern, runs, minutes)
        return pattern.substring(0, minutes.end) + separator + SECONDS_FIELD + pattern.substring(minutes.end)
    }

    // The separator between the hours and the minutes, if it is a plain one, otherwise a colon
    private fun separatorBefore(
        pattern: String,
        runs: List<FieldRun>,
        minutes: FieldRun,
    ): String {
        val previous = runs.getOrNull(runs.indexOf(minutes) - 1) ?: return DEFAULT_SEPARATOR
        if (previous.letter !in hourFields) return DEFAULT_SEPARATOR

        val between = pattern.substring(previous.end, minutes.start)
        return if (between.length == 1 && between.single() in separators) between else DEFAULT_SEPARATOR
    }

    // A run of the same letter outside quotes, such as the mm of a pattern. [end] is exclusive
    private data class FieldRun(
        val letter: Char,
        val start: Int,
        val end: Int,
    )

    private fun fieldRuns(pattern: String): List<FieldRun> {
        val runs = mutableListOf<FieldRun>()
        var quoted = false
        var index = 0
        while (index < pattern.length) {
            val char = pattern[index]
            when {
                char == QUOTE -> {
                    // Two quotes in a row are a single quote character, quoted or not
                    val escaped = pattern.getOrNull(index + 1) == QUOTE
                    if (!escaped) quoted = !quoted
                    index += if (escaped) 2 else 1
                }

                quoted || !char.isAsciiLetter() -> {
                    index++
                }

                else -> {
                    val start = index
                    while (index < pattern.length && pattern[index] == char) index++
                    runs += FieldRun(char, start, index)
                }
            }
        }
        return runs
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
}
