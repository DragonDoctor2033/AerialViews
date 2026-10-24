package com.neilturner.aerialviews.ui.overlays.utils

import com.neilturner.aerialviews.models.enums.AnalogClockHalo
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
    fun `lengths scale in proportion to the radius`() {
        val small = AnalogClockGeometry.forRadius(100f)
        val large = AnalogClockGeometry.forRadius(200f)

        assertEquals(small.quarterTickLength * 2, large.quarterTickLength, DELTA)
        assertEquals(small.regularTickLength * 2, large.regularTickLength, DELTA)
        assertEquals(small.hourHandLength * 2, large.hourHandLength, DELTA)
        assertEquals(small.minuteHandLength * 2, large.minuteHandLength, DELTA)
        assertEquals(small.secondHandLength * 2, large.secondHandLength, DELTA)
    }

    @Test
    fun `widths scale in proportion to the radius while the face is small enough for no boost`() {
        // Both are below the radius where lines start getting thicker
        val small = AnalogClockGeometry.forRadius(50f)
        val large = AnalogClockGeometry.forRadius(100f)

        assertEquals(small.centreDotRadius * 2, large.centreDotRadius, DELTA)
        assertEquals(small.hourHandWidth * 2, large.hourHandWidth, DELTA)
        assertEquals(small.minuteHandWidth * 2, large.minuteHandWidth, DELTA)
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

    @Test
    fun `lines are not boosted up to the medium clock in a corner`() {
        // Radius 110 is the medium clock on a 1080p TV, 80 the small one
        listOf(0f, 10f, 40f, 80f, 110f).forEach { radius ->
            assertEquals(1f, AnalogClockGeometry.strokeBoost(radius), DELTA, "radius $radius")
        }
    }

    @Test
    fun `lines are 30 percent thicker from a radius of 300 pixels`() {
        assertEquals(1.3f, AnalogClockGeometry.strokeBoost(300f), DELTA)
        assertEquals(1.3f, AnalogClockGeometry.strokeBoost(500f), DELTA)
        assertEquals(1.3f, AnalogClockGeometry.strokeBoost(5000f), DELTA)
    }

    @Test
    fun `the boost grows in a straight line in between`() {
        // Half way from 110 to 300 is 205
        assertEquals(1.15f, AnalogClockGeometry.strokeBoost(205f), DELTA)

        val boosts = listOf(110f, 150f, 189f, 205f, 243f, 297f, 300f).map { AnalogClockGeometry.strokeBoost(it) }
        assertEquals(boosts.sorted(), boosts)
        assertTrue(boosts.first() < boosts.last())
    }

    @Test
    fun `an unusable radius gets no boost`() {
        listOf(-50f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { radius ->
            assertEquals(1f, AnalogClockGeometry.strokeBoost(radius), DELTA, "radius $radius")
        }
    }

    @Test
    fun `the clocks in the center of the screen have thicker lines than the same face scaled up`() {
        // Large in the center on a 1080p TV is a 486 pixel face, a radius of 243
        val unboosted = AnalogClockGeometry.forRadius(100f)
        val large = AnalogClockGeometry.forRadius(243f)
        val boost = AnalogClockGeometry.strokeBoost(243f)

        assertTrue(boost > 1.2f)
        assertEquals(unboosted.hourHandWidth / 100f * 243f * boost, large.hourHandWidth, 0.01f)
        assertEquals(unboosted.minuteHandWidth / 100f * 243f * boost, large.minuteHandWidth, 0.01f)
        assertEquals(unboosted.secondHandWidth / 100f * 243f * boost, large.secondHandWidth, 0.01f)
        assertEquals(unboosted.quarterTickWidth / 100f * 243f * boost, large.quarterTickWidth, 0.01f)
        assertEquals(unboosted.regularTickWidth / 100f * 243f * boost, large.regularTickWidth, 0.01f)
        assertEquals(unboosted.centreDotRadius / 100f * 243f * boost, large.centreDotRadius, 0.01f)
    }

    @Test
    fun `the boost only makes lines thicker, never longer`() {
        val unboosted = AnalogClockGeometry.forRadius(100f)
        val large = AnalogClockGeometry.forRadius(297f)

        assertEquals(unboosted.hourHandLength * 2.97f, large.hourHandLength, 0.01f)
        assertEquals(unboosted.minuteHandLength * 2.97f, large.minuteHandLength, 0.01f)
        assertEquals(unboosted.secondHandLength * 2.97f, large.secondHandLength, 0.01f)
        assertEquals(unboosted.quarterTickLength * 2.97f, large.quarterTickLength, 0.01f)
        assertEquals(unboosted.regularTickLength * 2.97f, large.regularTickLength, 0.01f)
    }

    @Test
    fun `boosted hands still stop short of the ticks on the biggest faces`() {
        // The faces of the three center sizes on a 1080p TV, and an extra large one on a 4K UI
        listOf(189f, 243f, 297f, 594f).forEach { radius ->
            val sizes = AnalogClockGeometry.forRadius(radius)
            val innerEdgeOfQuarterTicks = radius - sizes.quarterTickLength

            assertTrue(sizes.hourHandLength + sizes.hourHandWidth / 2 < innerEdgeOfQuarterTicks, "hour hand at radius $radius")
            assertTrue(sizes.minuteHandLength + sizes.minuteHandWidth / 2 < innerEdgeOfQuarterTicks, "minute hand at radius $radius")
            assertTrue(sizes.secondHandLength + sizes.secondHandWidth / 2 < innerEdgeOfQuarterTicks, "second hand at radius $radius")
            assertTrue(sizes.hourHandWidth < radius * 0.06f, "hour hand is still thin at radius $radius")
        }
    }

    @Test
    fun `the view is just the face without a halo`() {
        listOf(0, 160, 220, 300, 486).forEach { diameter ->
            assertEquals(diameter, AnalogClockGeometry.viewSizePx(diameter, AnalogClockHalo.OFF), "diameter $diameter")
        }
    }

    @Test
    fun `the view has room for the halo all round the face`() {
        // A medium clock, 220 px across, needs 25 percent of its 110 px radius on each side
        assertEquals(220 + 2 * 28, AnalogClockGeometry.viewSizePx(220, AnalogClockHalo.MEDIUM))
        assertEquals(160 + 2 * 20, AnalogClockGeometry.viewSizePx(160, AnalogClockHalo.MEDIUM))
        assertEquals(300 + 2 * 38, AnalogClockGeometry.viewSizePx(300, AnalogClockHalo.MEDIUM))
        assertEquals(486 + 2 * 61, AnalogClockGeometry.viewSizePx(486, AnalogClockHalo.MEDIUM))
    }

    @Test
    fun `every visible halo level takes the same room and a bigger face takes more`() {
        AnalogClockHalo.entries.filter { it.isVisible }.forEach { halo ->
            assertEquals(AnalogClockGeometry.viewSizePx(300, AnalogClockHalo.MEDIUM), AnalogClockGeometry.viewSizePx(300, halo), "$halo")
            assertTrue(AnalogClockGeometry.viewSizePx(300, halo) > 300, "$halo")
            assertTrue(AnalogClockGeometry.viewSizePx(486, halo) > AnalogClockGeometry.viewSizePx(300, halo), "$halo")
        }
    }

    @Test
    fun `a face with no size has no view, even with a halo`() {
        assertEquals(0, AnalogClockGeometry.viewSizePx(0, AnalogClockHalo.STRONG))
        assertEquals(0, AnalogClockGeometry.viewSizePx(-20, AnalogClockHalo.STRONG))
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
