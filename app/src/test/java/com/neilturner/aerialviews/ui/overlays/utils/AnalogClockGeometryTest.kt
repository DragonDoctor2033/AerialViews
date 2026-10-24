package com.neilturner.aerialviews.ui.overlays.utils

import com.neilturner.aerialviews.models.enums.AnalogClockSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AnalogClockGeometryTest {
    private val radii = listOf(40f, 75f, 107f, 150f, 400f)

    @Test
    fun `there are 12 ticks 30 degrees apart starting at 12 o'clock`() {
        assertEquals(12, AnalogClockGeometry.TICK_COUNT)

        val angles = (0 until AnalogClockGeometry.TICK_COUNT).map { AnalogClockGeometry.tickAngle(it) }
        assertEquals(listOf(0f, 30f, 60f, 90f, 120f, 150f, 180f, 210f, 240f, 270f, 300f, 330f), angles)
    }

    @Test
    fun `tick index wraps around the face`() {
        assertEquals(0f, AnalogClockGeometry.tickAngle(12))
        assertEquals(30f, AnalogClockGeometry.tickAngle(13))
        assertEquals(330f, AnalogClockGeometry.tickAngle(-1))
    }

    @Test
    fun `only the marks at 12 3 6 and 9 are quarter ticks`() {
        val quarters = (0 until AnalogClockGeometry.TICK_COUNT).filter { AnalogClockGeometry.isQuarterTick(it) }

        assertEquals(listOf(0, 3, 6, 9), quarters)
        // Which are the ticks pointing up, right, down and left
        assertEquals(listOf(0f, 90f, 180f, 270f), quarters.map { AnalogClockGeometry.tickAngle(it) })
    }

    @Test
    fun `quarter ticks are longer and thicker than the others`() {
        radii.forEach { radius ->
            val sizes = AnalogClockGeometry.forRadius(radius)
            assertTrue(sizes.quarterTickLength > sizes.regularTickLength, "length at radius $radius")
            assertTrue(sizes.quarterTickWidth > sizes.regularTickWidth, "width at radius $radius")
        }
    }

    @Test
    fun `the minute hand is longer than the hour hand and the second hand is longest`() {
        radii.forEach { radius ->
            val sizes = AnalogClockGeometry.forRadius(radius)
            assertTrue(sizes.minuteHandLength > sizes.hourHandLength, "minute vs hour at radius $radius")
            assertTrue(sizes.secondHandLength > sizes.minuteHandLength, "second vs minute at radius $radius")
        }
    }

    @Test
    fun `the second hand is the thinnest and the hour hand the thickest`() {
        radii.forEach { radius ->
            val sizes = AnalogClockGeometry.forRadius(radius)
            assertTrue(sizes.hourHandWidth > sizes.minuteHandWidth, "hour vs minute at radius $radius")
            assertTrue(sizes.minuteHandWidth > sizes.secondHandWidth, "minute vs second at radius $radius")
        }
    }

    @Test
    fun `hands are thin and the centre dot is wider than them`() {
        radii.forEach { radius ->
            val sizes = AnalogClockGeometry.forRadius(radius)
            assertTrue(sizes.hourHandWidth < radius * 0.06f, "hour hand at radius $radius")
            assertTrue(sizes.centreDotRadius * 2 > sizes.hourHandWidth, "dot at radius $radius")
        }
    }

    @Test
    fun `everything stays inside the face and the hands stop short of the ticks`() {
        radii.forEach { radius ->
            val sizes = AnalogClockGeometry.forRadius(radius)
            val innerEdgeOfQuarterTicks = radius - sizes.quarterTickLength

            // Round ends stick out half a stroke beyond the end of a hand
            val tips =
                listOf(
                    sizes.hourHandLength + sizes.hourHandWidth / 2,
                    sizes.minuteHandLength + sizes.minuteHandWidth / 2,
                    sizes.secondHandLength + sizes.secondHandWidth / 2,
                )
            tips.forEach { tip ->
                assertTrue(tip < innerEdgeOfQuarterTicks, "hand tip at $tip reaches the ticks at $innerEdgeOfQuarterTicks")
            }

            assertTrue(sizes.quarterTickLength < radius / 2, "ticks are only a thin ring at radius $radius")
            assertTrue(sizes.centreDotRadius < sizes.hourHandLength, "dot at radius $radius")
        }
    }

    @Test
    fun `sizes scale in proportion to the radius`() {
        val small = AnalogClockGeometry.forRadius(100f)
        val large = AnalogClockGeometry.forRadius(200f)

        assertEquals(small.quarterTickLength * 2, large.quarterTickLength, DELTA)
        assertEquals(small.regularTickLength * 2, large.regularTickLength, DELTA)
        assertEquals(small.hourHandLength * 2, large.hourHandLength, DELTA)
        assertEquals(small.minuteHandLength * 2, large.minuteHandLength, DELTA)
        assertEquals(small.secondHandLength * 2, large.secondHandLength, DELTA)
        assertEquals(small.centreDotRadius * 2, large.centreDotRadius, DELTA)
        assertEquals(small.hourHandWidth * 2, large.hourHandWidth, DELTA)
        assertEquals(small.quarterTickWidth * 2, large.quarterTickWidth, DELTA)
    }

    @Test
    fun `lines never get thinner than a pixel`() {
        val tiny = AnalogClockGeometry.forRadius(10f)

        listOf(
            tiny.regularTickWidth,
            tiny.quarterTickWidth,
            tiny.hourHandWidth,
            tiny.minuteHandWidth,
            tiny.secondHandWidth,
        ).forEach { width ->
            assertTrue(width >= 1f, "stroke width $width")
        }
    }

    @Test
    fun `a face with no room has nothing to draw`() {
        listOf(0f, -50f, Float.NaN, Float.POSITIVE_INFINITY).forEach { radius ->
            val sizes = AnalogClockGeometry.forRadius(radius)
            assertEquals(0f, sizes.hourHandLength, "hour hand for $radius")
            assertEquals(0f, sizes.minuteHandLength, "minute hand for $radius")
            assertEquals(0f, sizes.secondHandLength, "second hand for $radius")
            assertEquals(0f, sizes.quarterTickLength, "ticks for $radius")
            assertEquals(0f, sizes.centreDotRadius, "dot for $radius")
        }
    }

    @Test
    fun `medium is about 220 pixels across on a 1080p TV`() {
        // Android TV lays 1080p out at a density of 2.0
        assertEquals(220, AnalogClockGeometry.diameterPx(AnalogClockSize.MEDIUM, 2f))
        assertEquals(AnalogClockSize.MEDIUM, AnalogClockSize.DEFAULT)
    }

    @Test
    fun `diameter scales with the screen density`() {
        assertEquals(110, AnalogClockGeometry.diameterPx(AnalogClockSize.MEDIUM, 1f))
        assertEquals(165, AnalogClockGeometry.diameterPx(AnalogClockSize.MEDIUM, 1.5f))
        assertEquals(220, AnalogClockGeometry.diameterPx(AnalogClockSize.MEDIUM, 2f))
        assertEquals(440, AnalogClockGeometry.diameterPx(AnalogClockSize.MEDIUM, 4f))
    }

    @Test
    fun `diameter is rounded to whole pixels`() {
        // 220 * 1.33125 / 2 = 146.4375
        assertEquals(146, AnalogClockGeometry.diameterPx(AnalogClockSize.MEDIUM, 1.33125f))
        // 220 * 2.75 / 2 = 302.5
        assertEquals(303, AnalogClockGeometry.diameterPx(AnalogClockSize.MEDIUM, 2.75f))
    }

    @Test
    fun `larger sizes are larger at every density`() {
        listOf(1f, 1.33125f, 1.5f, 2f, 2.625f, 3f, 4f).forEach { density ->
            val small = AnalogClockGeometry.diameterPx(AnalogClockSize.SMALL, density)
            val medium = AnalogClockGeometry.diameterPx(AnalogClockSize.MEDIUM, density)
            val large = AnalogClockGeometry.diameterPx(AnalogClockSize.LARGE, density)

            assertTrue(small < medium, "small vs medium at density $density")
            assertTrue(medium < large, "medium vs large at density $density")
        }
    }

    @Test
    fun `every size has a diameter on a 1080p TV`() {
        assertEquals(160, AnalogClockGeometry.diameterPx(AnalogClockSize.SMALL, 2f))
        assertEquals(220, AnalogClockGeometry.diameterPx(AnalogClockSize.MEDIUM, 2f))
        assertEquals(300, AnalogClockGeometry.diameterPx(AnalogClockSize.LARGE, 2f))
    }

    @Test
    fun `an unusable density falls back to the 1080p TV size`() {
        listOf(0f, -2f, Float.NaN, Float.POSITIVE_INFINITY).forEach { density ->
            assertEquals(220, AnalogClockGeometry.diameterPx(AnalogClockSize.MEDIUM, density), "density $density")
        }
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
