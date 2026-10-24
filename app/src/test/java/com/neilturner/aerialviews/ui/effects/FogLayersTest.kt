package com.neilturner.aerialviews.ui.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FogLayersTest {
    private val size = FogNoise.TEXTURE_SIZE.toFloat()

    @Test
    fun `there are three layers, each with a seed, a size, and a direction`() {
        assertEquals(3, FogLayers.COUNT)
        assertEquals(3, FogLayers.SEEDS.size)
        assertEquals(3, FogLayers.ZOOM.size)
        assertEquals(3, FogLayers.SPEED_X.size)
        assertEquals(3, FogLayers.SPEED_Y.size)
        assertEquals(3, EffectPalette.FOG_LAYER_ALPHA.size)
    }

    @Test
    fun `the layers drift in different directions`() {
        val directions = (0 until 3).map { FogLayers.SPEED_X[it] to FogLayers.SPEED_Y[it] }

        assertEquals(3, directions.toSet().size)
        assertTrue(FogLayers.SPEED_X.any { it > 0f } && FogLayers.SPEED_X.any { it < 0f })
        assertTrue(FogLayers.SPEED_Y.any { it > 0f } && FogLayers.SPEED_Y.any { it < 0f })
    }

    @Test
    fun `the layers are different sizes`() {
        assertEquals(3, FogLayers.ZOOM.toSet().size)
        assertTrue(FogLayers.ZOOM.all { it > 0.5f })
    }

    @Test
    fun `a tile is never smaller than the screen is wide, so it can not be seen twice side by side`() {
        assertTrue(FogLayers.ZOOM.all { it >= 1f }, "${FogLayers.ZOOM.toList()}")
        assertEquals(listOf(1.3f, 1.0f, 1.8f), FogLayers.ZOOM.toList())
    }

    @Test
    fun `the fog drifts slowly, the fastest layer takes over a minute to cross the screen`() {
        val fastest = (0 until 3).maxOf { kotlin.math.abs(FogLayers.SPEED_X[it]) }

        assertTrue(1f / fastest > 60f, "crosses in ${1f / fastest} seconds")
        assertTrue(FogLayers.WIND_SPEED < 0.05f)
    }

    @Test
    fun `a layer moves by its speed in widths of the screen`() {
        // One width per second, with a tile that covers two widths, is half a tile, 128 texels
        assertEquals(128f, FogLayers.drift(0f, 1f, 2f, 1f), 0.001f)
        assertEquals(64f, FogLayers.drift(0f, 1f, 2f, 0.5f), 0.001f)
        assertEquals(10f + 64f, FogLayers.drift(10f, 0.5f, 2f, 1f), 0.001f)
    }

    @Test
    fun `a layer which drifts the other way moves back`() {
        assertEquals(100f - 64f, FogLayers.drift(100f, -0.5f, 2f, 1f), 0.001f)
    }

    @Test
    fun `a layer which does not move stays where it is`() {
        assertEquals(37f, FogLayers.drift(37f, 0f, 1.6f, 1f))
        assertEquals(37f, FogLayers.drift(37f, 0.5f, 1.6f, 0f))
    }

    @Test
    fun `the picture repeats, so a layer comes back round at the other edge of the texture`() {
        assertEquals(10f, FogLayers.drift(250f, 1f, 1f, 16f / size), 0.01f)
        assertEquals(246f, FogLayers.drift(4f, -1f, 1f, 14f / size), 0.01f)
    }

    @Test
    fun `the offset always stays within the texture, however long the fog drifts`() {
        var x = 0f
        var y = 0f
        repeat(60 * 60 * 10) {
            x = FogLayers.drift(x, 0.012f + 0.02f, 1.6f, 1f / 60f)
            y = FogLayers.drift(y, -0.005f, 2.2f, 1f / 60f)
            assertTrue(x >= 0f && x < size, "x $x")
            assertTrue(y >= 0f && y < size, "y $y")
        }
    }

    @Test
    fun `a layer which is a bit below zero stays in the texture`() {
        val moved = FogLayers.drift(0f, -1e-9f, 1f, 1f)

        assertTrue(moved >= 0f && moved < size, "moved $moved")
    }
}
