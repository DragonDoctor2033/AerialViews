package com.neilturner.aerialviews.ui.overlays.utils

import com.neilturner.aerialviews.models.enums.AnalogClockHalo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AnalogClockHaloMathTest {
    private val visibleLevels = AnalogClockHalo.entries.filter { it.isVisible }

    // 0, 0.01 ... 1
    private val fractions = (0..100).map { it / 100f }

    @Test
    fun `the halo is 45 percent black in the middle at the default level`() {
        assertEquals(AnalogClockHalo.MEDIUM, AnalogClockHalo.DEFAULT)
        assertEquals(0.45f, AnalogClockHaloMath.opacityAt(AnalogClockHalo.MEDIUM, 0f), DELTA)
        // 45 percent of 255 is 114.75
        assertEquals(115, AnalogClockHaloMath.alphaAt(AnalogClockHalo.MEDIUM, 0f))
    }

    @Test
    fun `every level has its own alpha in the middle`() {
        assertEquals(0, AnalogClockHaloMath.alphaAt(AnalogClockHalo.OFF, 0f))
        assertEquals(77, AnalogClockHaloMath.alphaAt(AnalogClockHalo.LIGHT, 0f))
        assertEquals(115, AnalogClockHaloMath.alphaAt(AnalogClockHalo.MEDIUM, 0f))
        assertEquals(153, AnalogClockHaloMath.alphaAt(AnalogClockHalo.STRONG, 0f))
    }

    @Test
    fun `stronger levels are darker everywhere on the halo`() {
        fractions.filter { it < 1f }.forEach { fraction ->
            val alphas = AnalogClockHalo.entries.map { AnalogClockHaloMath.alphaAt(it, fraction) }

            assertEquals(alphas.sorted(), alphas, "alphas at $fraction")
        }
        val middle = AnalogClockHalo.entries.map { AnalogClockHaloMath.alphaAt(it, 0f) }
        assertEquals(middle.distinct(), middle)
    }

    @Test
    fun `with the halo off nothing is drawn at any distance`() {
        fractions.forEach { fraction ->
            assertEquals(0, AnalogClockHaloMath.alphaAt(AnalogClockHalo.OFF, fraction), "fraction $fraction")
        }
        assertTrue(AnalogClockHaloMath.gradient(AnalogClockHalo.OFF).alphas.all { it == 0 })
    }

    @Test
    fun `the halo is completely transparent at its outer edge and beyond`() {
        visibleLevels.forEach { halo ->
            assertEquals(0, AnalogClockHaloMath.alphaAt(halo, 1f), "$halo at the edge")
            assertEquals(0, AnalogClockHaloMath.alphaAt(halo, 1.5f), "$halo beyond the edge")
            assertEquals(0, AnalogClockHaloMath.alphaAt(halo, 100f), "$halo far away")
            assertEquals(0, AnalogClockHaloMath.alphaAt(halo, Float.POSITIVE_INFINITY), "$halo at infinity")
            assertEquals(0, AnalogClockHaloMath.alphaAt(halo, Float.NaN), "$halo for nothing")
        }
    }

    @Test
    fun `a position before the middle is the middle`() {
        visibleLevels.forEach { halo ->
            assertEquals(AnalogClockHaloMath.alphaAt(halo, 0f), AnalogClockHaloMath.alphaAt(halo, -0.5f), "$halo")
            assertEquals(AnalogClockHaloMath.alphaAt(halo, 0f), AnalogClockHaloMath.alphaAt(halo, Float.NEGATIVE_INFINITY), "$halo")
        }
    }

    @Test
    fun `the halo is as dark as the middle out to the core, then fades out`() {
        visibleLevels.forEach { halo ->
            val middle = AnalogClockHaloMath.alphaAt(halo, 0f)

            assertEquals(middle, AnalogClockHaloMath.alphaAt(halo, AnalogClockHaloMath.CORE), "$halo at the core")
            assertTrue(AnalogClockHaloMath.alphaAt(halo, 0.75f) < middle, "$halo has started to fade")
            assertTrue(AnalogClockHaloMath.alphaAt(halo, 0.75f) > 0, "$halo has not faded out yet")
        }
    }

    @Test
    fun `the halo only ever gets lighter on the way out`() {
        visibleLevels.forEach { halo ->
            val alphas = fractions.map { AnalogClockHaloMath.alphaAt(halo, it) }

            assertEquals(alphas.sortedDescending(), alphas, "$halo")
        }
    }

    @Test
    fun `the halo has mostly faded at the edge of the face but the ticks still have something dark behind them`() {
        visibleLevels.forEach { halo ->
            val atFaceEdge = AnalogClockHaloMath.opacityAt(halo, 1f / AnalogClockHaloMath.REACH)

            assertTrue(atFaceEdge > 0.05f * halo.centreOpacity, "$halo at the face edge is $atFaceEdge")
            assertTrue(atFaceEdge < 0.5f * halo.centreOpacity, "$halo at the face edge is $atFaceEdge")
        }
    }

    @Test
    fun `the halo reaches a bit beyond the face`() {
        assertTrue(AnalogClockHaloMath.REACH > 1.1f)
        assertTrue(AnalogClockHaloMath.REACH < 1.5f)
        assertEquals(125f, AnalogClockHaloMath.outerRadius(100f), DELTA)
        assertEquals(0f, AnalogClockHaloMath.outerRadius(0f), DELTA)
    }

    @Test
    fun `an unusable face radius has no halo radius`() {
        listOf(-10f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { radius ->
            assertEquals(0f, AnalogClockHaloMath.outerRadius(radius), DELTA, "radius $radius")
            assertEquals(0, AnalogClockHaloMath.marginPx(radius, AnalogClockHalo.STRONG), "radius $radius")
        }
    }

    @Test
    fun `no halo needs no room`() {
        listOf(0f, 40f, 110f, 243f, 600f).forEach { radius ->
            assertEquals(0, AnalogClockHaloMath.marginPx(radius, AnalogClockHalo.OFF), "radius $radius")
        }
    }

    @Test
    fun `the room is a quarter of the face radius, rounded up`() {
        assertEquals(20, AnalogClockHaloMath.marginPx(80f, AnalogClockHalo.MEDIUM))
        assertEquals(28, AnalogClockHaloMath.marginPx(110f, AnalogClockHalo.MEDIUM))
        assertEquals(38, AnalogClockHaloMath.marginPx(150f, AnalogClockHalo.MEDIUM))
        assertEquals(61, AnalogClockHaloMath.marginPx(243f, AnalogClockHalo.MEDIUM))
        assertEquals(1, AnalogClockHaloMath.marginPx(1f, AnalogClockHalo.MEDIUM))
    }

    @Test
    fun `the room is the same at every visible level, so changing the level doesn't move the face`() {
        visibleLevels.forEach { halo ->
            assertEquals(AnalogClockHaloMath.marginPx(150f, AnalogClockHalo.MEDIUM), AnalogClockHaloMath.marginPx(150f, halo), "$halo")
        }
    }

    @Test
    fun `the room is as big as the distance from the face to the edge of the halo`() {
        listOf(80f, 110f, 150f, 243f, 297f).forEach { radius ->
            val room = AnalogClockHaloMath.marginPx(radius, AnalogClockHalo.MEDIUM)

            assertTrue(room >= AnalogClockHaloMath.outerRadius(radius) - radius, "room $room at radius $radius")
            // And never a whole pixel more than that
            assertTrue(room < AnalogClockHaloMath.outerRadius(radius) - radius + 1f, "room $room at radius $radius")
        }
    }

    @Test
    fun `the gradient runs from the middle to the edge`() {
        visibleLevels.forEach { halo ->
            val gradient = AnalogClockHaloMath.gradient(halo)

            assertEquals(AnalogClockHaloMath.GRADIENT_STOPS, gradient.positions.size)
            assertEquals(gradient.positions.size, gradient.alphas.size)
            assertEquals(0f, gradient.positions.first(), "$halo starts in the middle")
            assertEquals(1f, gradient.positions.last(), "$halo ends at the edge")
        }
    }

    @Test
    fun `gradient positions only go up, as a radial gradient needs`() {
        val positions = AnalogClockHaloMath.gradient(AnalogClockHalo.MEDIUM).positions

        positions.toList().zipWithNext().forEach { (before, after) ->
            assertTrue(after > before, "$after follows $before")
        }
        assertTrue(positions.all { it in 0f..1f })
    }

    @Test
    fun `gradient alphas start at the level, end at nothing and are valid alpha values`() {
        visibleLevels.forEach { halo ->
            val alphas = AnalogClockHaloMath.gradient(halo).alphas

            assertEquals(AnalogClockHaloMath.alphaAt(halo, 0f), alphas.first(), "$halo first")
            assertEquals(0, alphas.last(), "$halo last")
            assertTrue(alphas.all { it in 0..255 }, "$halo ${alphas.toList()}")
            assertEquals(alphas.sortedDescending(), alphas.toList(), "$halo ${alphas.toList()}")
        }
    }

    @Test
    fun `each gradient stop has the alpha of the halo at that position`() {
        val gradient = AnalogClockHaloMath.gradient(AnalogClockHalo.STRONG)

        gradient.positions.indices.forEach { index ->
            assertEquals(AnalogClockHaloMath.alphaAt(AnalogClockHalo.STRONG, gradient.positions[index]), gradient.alphas[index])
        }
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
