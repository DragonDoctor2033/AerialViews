package com.neilturner.aerialviews.ui.overlays.utils

import com.neilturner.aerialviews.models.enums.ClockType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClockFormatsTest {
    @Test
    fun `24 hour time is hours and minutes, or with seconds`() {
        assertEquals("HH:mm", ClockFormats.hour24(showSeconds = false))
        assertEquals("HH:mm:ss", ClockFormats.hour24(showSeconds = true))
    }

    @Test
    fun `12 hour time is hours, minutes and am pm, or with seconds`() {
        assertEquals("h:mm a", ClockFormats.hour12(showSeconds = false))
        assertEquals("h:mm:ss a", ClockFormats.hour12(showSeconds = true))
    }

    @Test
    fun `a 24 hour clock uses its pattern whatever the system is set to`() {
        assertEquals(ClockPatterns("HH:mm", "HH:mm"), ClockFormats.patterns(ClockType.HOUR_24, showSeconds = false))
        assertEquals(ClockPatterns("HH:mm:ss", "HH:mm:ss"), ClockFormats.patterns(ClockType.HOUR_24, showSeconds = true))
    }

    @Test
    fun `a 12 hour clock uses its pattern whatever the system is set to`() {
        assertEquals(ClockPatterns("h:mm a", "h:mm a"), ClockFormats.patterns(ClockType.HOUR_12, showSeconds = false))
        assertEquals(ClockPatterns("h:mm:ss a", "h:mm:ss a"), ClockFormats.patterns(ClockType.HOUR_12, showSeconds = true))
    }

    @Test
    fun `the system default is left alone when there are no seconds`() {
        assertNull(ClockFormats.patterns(ClockType.DEFAULT, showSeconds = false))
        assertNull(ClockFormats.patterns(null, showSeconds = false))
        // The patterns of the system make no difference
        assertNull(ClockFormats.patterns(ClockType.DEFAULT, showSeconds = false, systemHour12 = "h:mm a", systemHour24 = "HH:mm"))
    }

    @Test
    fun `the system default gets seconds added to the patterns of the system`() {
        val english = ClockFormats.patterns(ClockType.DEFAULT, showSeconds = true, systemHour12 = "h:mm a", systemHour24 = "HH:mm")
        assertEquals(ClockPatterns(hour12 = "h:mm:ss a", hour24 = "HH:mm:ss"), english)

        // A device in Finnish or Danish writes the time with dots, and the seconds follow
        val finnish = ClockFormats.patterns(ClockType.DEFAULT, showSeconds = true, systemHour12 = "h.mm a", systemHour24 = "H.mm")
        assertEquals(ClockPatterns(hour12 = "h.mm.ss a", hour24 = "H.mm.ss"), finnish)

        // No leading zero on the hours stays that way
        val russian = ClockFormats.patterns(ClockType.DEFAULT, showSeconds = true, systemHour12 = "h:mm a", systemHour24 = "H:mm")
        assertEquals("H:mm:ss", russian?.hour24)
    }

    @Test
    fun `a missing system default is replaced by the english patterns`() {
        val expected = ClockPatterns(hour12 = "h:mm:ss a", hour24 = "HH:mm:ss")

        assertEquals(expected, ClockFormats.patterns(ClockType.DEFAULT, showSeconds = true))
        assertEquals(expected, ClockFormats.patterns(null, showSeconds = true))
        assertEquals(expected, ClockFormats.patterns(ClockType.DEFAULT, showSeconds = true, systemHour12 = "", systemHour24 = "  "))
    }

    @Test
    fun `with seconds on every clock updates every second in both hour modes`() {
        // TextClock ticks once a second only when the pattern it shows has seconds. Which one it
        // shows depends on the system's 12/24 hour setting, so both have to.
        val systems = listOf(null to null, "h:mm a" to "HH:mm", "h.mm a" to "H.mm", "a h:mm" to "H:mm", "HH 'h' mm" to "HH 'h' mm")

        (ClockType.entries + null).forEach { type ->
            systems.forEach { (system12, system24) ->
                val patterns =
                    ClockFormats.patterns(type, showSeconds = true, systemHour12 = system12, systemHour24 = system24)
                        ?: error("no patterns for $type")

                assertTrue(ClockFormats.hasSeconds(patterns.hour12), "12 hour '${patterns.hour12}' for $type, $system12")
                assertTrue(ClockFormats.hasSeconds(patterns.hour24), "24 hour '${patterns.hour24}' for $type, $system24")
            }
        }
    }

    @Test
    fun `with seconds off no pattern has seconds`() {
        listOf(ClockType.HOUR_12, ClockType.HOUR_24).forEach { type ->
            val patterns = ClockFormats.patterns(type, showSeconds = false) ?: error("no patterns for $type")

            assertFalse(ClockFormats.hasSeconds(patterns.hour12), "12 hour for $type")
            assertFalse(ClockFormats.hasSeconds(patterns.hour24), "24 hour for $type")
        }
    }

    @Test
    fun `seconds go right after the minutes in the style of the pattern`() {
        mapOf(
            "HH:mm" to "HH:mm:ss",
            "H:mm" to "H:mm:ss",
            "k:mm" to "k:mm:ss",
            "h:mm a" to "h:mm:ss a",
            "hh:mm a" to "hh:mm:ss a",
            "a h:mm" to "a h:mm:ss",
            "ah:mm" to "ah:mm:ss",
            "aK:mm" to "aK:mm:ss",
            "h:mm B" to "h:mm:ss B",
            "H.mm" to "H.mm.ss",
            "HH.mm" to "HH.mm.ss",
            "H：mm" to "H：mm：ss",
            "h:mm a" to "h:mm:ss a",
            "EEE HH:mm" to "EEE HH:mm:ss",
        ).forEach { (pattern, expected) ->
            assertEquals(expected, ClockFormats.withSeconds(pattern), "pattern '$pattern'")
        }
    }

    @Test
    fun `a separator which is more than a plain one is replaced by a colon`() {
        assertEquals("HH 'h' mm:ss", ClockFormats.withSeconds("HH 'h' mm"))
        assertEquals("H mm:ss", ClockFormats.withSeconds("H mm"))
        assertEquals("H-mm:ss", ClockFormats.withSeconds("H-mm"))
    }

    @Test
    fun `adding seconds twice changes nothing more`() {
        listOf("HH:mm", "h:mm a", "H.mm", "HH:mm:ss", "h:mm:ss a", "HH 'h' mm", "mm").forEach { pattern ->
            val once = ClockFormats.withSeconds(pattern)

            assertEquals(once, ClockFormats.withSeconds(once), "pattern '$pattern'")
        }
    }

    @Test
    fun `a pattern which already has seconds is returned as it is`() {
        listOf("HH:mm:ss", "h:mm:ss a", "H.mm.ss", "HH:mm:s", "ss", "a h:mm:ss").forEach { pattern ->
            assertEquals(pattern, ClockFormats.withSeconds(pattern))
        }
    }

    @Test
    fun `month and fraction letters are not minutes or seconds`() {
        // MM is the month and SSS is a fraction of a second
        assertEquals("dd/MM HH:mm:ss", ClockFormats.withSeconds("dd/MM HH:mm"))
        assertFalse(ClockFormats.hasSeconds("HH:mm:SSS"))
        assertFalse(ClockFormats.hasSeconds("dd/MM"))
    }

    @Test
    fun `quoted text is not a field`() {
        assertFalse(ClockFormats.hasSeconds("HH:mm 'ss'"))
        assertEquals("HH:mm:ss 'ss'", ClockFormats.withSeconds("HH:mm 'ss'"))

        // The minutes in quotes are only text, so there are none and the seconds go on the end
        assertEquals("HH:'mm':ss", ClockFormats.withSeconds("HH:'mm'"))

        // Two quotes in a row are a quote character, and the text goes on
        assertEquals("h:mm:ss 'o''clock' a", ClockFormats.withSeconds("h:mm 'o''clock' a"))
        assertEquals("h:mm:ss a 'it''s ss'", ClockFormats.withSeconds("h:mm a 'it''s ss'"))
        assertFalse(ClockFormats.hasSeconds("h:mm 'o''clock s'"))
    }

    @Test
    fun `a pattern with no minutes gets seconds on the end`() {
        assertEquals("HH:ss", ClockFormats.withSeconds("HH"))
        assertEquals(":ss", ClockFormats.withSeconds(""))
    }

    @Test
    fun `an unfinished quote does not stop the seconds being found`() {
        assertEquals("HH:mm:ss 'text", ClockFormats.withSeconds("HH:mm 'text"))
        assertTrue(ClockFormats.hasSeconds("HH:mm:ss 'text"))
    }

    @Test
    fun `seconds are found in a pattern`() {
        assertTrue(ClockFormats.hasSeconds("HH:mm:ss"))
        assertTrue(ClockFormats.hasSeconds("h:mm:ss a"))
        assertTrue(ClockFormats.hasSeconds("s"))
        assertFalse(ClockFormats.hasSeconds("HH:mm"))
        assertFalse(ClockFormats.hasSeconds("h:mm a"))
        assertFalse(ClockFormats.hasSeconds(""))
    }
}
