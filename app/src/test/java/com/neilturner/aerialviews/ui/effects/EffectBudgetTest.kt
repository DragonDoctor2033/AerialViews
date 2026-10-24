package com.neilturner.aerialviews.ui.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EffectBudgetTest {
    @Test
    fun `rain is 150 streaks in a light shower and 600 in a downpour`() {
        assertEquals(150, EffectBudget.rainCount(0f))
        assertEquals(600, EffectBudget.rainCount(1f))
        assertEquals(375, EffectBudget.rainCount(0.5f))
        assertEquals(150, EffectBudget.RAIN_MIN)
        assertEquals(600, EffectBudget.RAIN_MAX)
    }

    @Test
    fun `snow is 110 flakes in a light fall and 400 in a heavy one`() {
        assertEquals(110, EffectBudget.snowCount(0f))
        assertEquals(400, EffectBudget.snowCount(1f))
        assertEquals(255, EffectBudget.snowCount(0.5f))
        assertEquals(110, EffectBudget.SNOW_MIN)
        assertEquals(400, EffectBudget.SNOW_MAX)
    }

    @Test
    fun `a typical snowfall has about a third more flakes than it had`() {
        // At an intensity of 0.6, the one of the testing override, there were 212 flakes
        val typical = EffectBudget.snowCount(0.6f)

        assertEquals(284, typical)
        assertTrue(typical in 270..300, "$typical flakes")
        assertTrue(typical > 212 * 1.25f)
    }

    @Test
    fun `the count grows with the intensity and never shrinks`() {
        var rain = 0
        var snow = 0
        for (step in 0..100) {
            val level = step / 100f
            assertTrue(EffectBudget.rainCount(level) >= rain, "rain at $level")
            assertTrue(EffectBudget.snowCount(level) >= snow, "snow at $level")
            rain = EffectBudget.rainCount(level)
            snow = EffectBudget.snowCount(level)
        }
    }

    @Test
    fun `an intensity beyond the range is kept in it`() {
        assertEquals(150, EffectBudget.rainCount(-1f))
        assertEquals(600, EffectBudget.rainCount(2f))
        assertEquals(150, EffectBudget.rainCount(Float.NaN))
        assertEquals(110, EffectBudget.snowCount(-1f))
        assertEquals(400, EffectBudget.snowCount(9f))
    }

    @Test
    fun `a device which can't take as much has a share of the count`() {
        assertEquals(300, EffectBudget.rainCount(1f, quality = 0.5f))
        assertEquals(75, EffectBudget.rainCount(0f, quality = 0.5f))
        assertEquals(200, EffectBudget.snowCount(1f, quality = EffectBudget.SOFTWARE_QUALITY))
        assertEquals(0, EffectBudget.rainCount(1f, quality = 0f))
        assertEquals(600, EffectBudget.rainCount(1f, quality = 3f))
    }

    @Test
    fun `a canvas without hardware acceleration gets half`() {
        assertEquals(0.5f, EffectBudget.SOFTWARE_QUALITY)
    }

    @Test
    fun `the layers always add up to the whole count`() {
        for (total in 0..700) {
            val rain = (0 until 3).sumOf { EffectBudget.rainLayerCount(total, it) }
            val snow = (0 until 3).sumOf { EffectBudget.snowLayerCount(total, it) }

            assertEquals(total, rain, "rain $total")
            assertEquals(total, snow, "snow $total")
        }
    }

    @Test
    fun `most streaks are far away and the fewest are near`() {
        val far = EffectBudget.rainLayerCount(600, 0)
        val middle = EffectBudget.rainLayerCount(600, 1)
        val near = EffectBudget.rainLayerCount(600, 2)

        assertEquals(300, far)
        assertEquals(180, middle)
        assertEquals(120, near)
        assertTrue(far > middle && middle > near)
    }

    @Test
    fun `most flakes are far away and the fewest are near`() {
        val far = EffectBudget.snowLayerCount(400, 0)
        val middle = EffectBudget.snowLayerCount(400, 1)
        val near = EffectBudget.snowLayerCount(400, 2)

        assertEquals(200, far)
        assertEquals(144, middle)
        assertEquals(56, near)
        assertTrue(far > middle && middle > near)
    }

    @Test
    fun `each layer has room for its share of the most there is`() {
        assertEquals(600, (0 until 3).sumOf { EffectBudget.rainCapacity(it) })
        assertEquals(400, (0 until 3).sumOf { EffectBudget.snowCapacity(it) })
        assertEquals(listOf(300, 180, 120), (0 until 3).map { EffectBudget.rainCapacity(it) })
        assertEquals(listOf(200, 144, 56), (0 until 3).map { EffectBudget.snowCapacity(it) })
    }

    @Test
    fun `a layer never needs more room than it has, at any count and quality`() {
        for (step in 0..100) {
            val level = step / 100f
            for (quality in listOf(0.5f, 1f)) {
                val rain = EffectBudget.rainCount(level, quality)
                val snow = EffectBudget.snowCount(level, quality)
                for (layer in 0 until 3) {
                    assertTrue(EffectBudget.rainLayerCount(rain, layer) <= EffectBudget.rainCapacity(layer), "rain $level $quality $layer")
                    assertTrue(EffectBudget.snowLayerCount(snow, layer) <= EffectBudget.snowCapacity(layer), "snow $level $quality $layer")
                }
            }
        }
    }

    @Test
    fun `a layer which does not exist has no streaks`() {
        assertEquals(0, EffectBudget.rainLayerCount(600, -1))
        assertEquals(0, EffectBudget.rainLayerCount(600, 3))
        assertEquals(0, EffectBudget.rainLayerCount(0, 0))
        assertEquals(0, EffectBudget.rainLayerCount(-10, 2))
    }

    @Test
    fun `the shares of shares that are not round still add up`() {
        val shares = floatArrayOf(0.4f, 0.35f, 0.25f)

        for (total in listOf(1, 2, 3, 7, 11, 101)) {
            val sum = (0 until 3).sumOf { EffectBudget.layerCount(total, shares, it) }
            assertEquals(total, sum, "total $total")
        }
    }

    @Test
    fun `the three layers of both kinds are far, middle and near`() {
        assertEquals(3, RainProfiles.ALL.size)
        assertEquals(3, SnowProfiles.ALL.size)
    }
}
