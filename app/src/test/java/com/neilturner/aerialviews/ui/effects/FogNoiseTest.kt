package com.neilturner.aerialviews.ui.effects

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

internal class FogNoiseTest {
    private val size = 64

    private fun noise(
        seed: Long = 1,
        size: Int = this.size,
    ) = FogNoise.tileable(size, seed)

    @Test
    fun `the noise has a value for every pixel of the texture`() {
        assertEquals(size * size, noise().size)
        assertEquals(256, FogNoise.TEXTURE_SIZE)
    }

    @Test
    fun `every value is between 0 and 1`() {
        listOf(1L, 2L, 99L, 0x51F0A1L).forEach { seed ->
            val values = noise(seed)

            assertTrue(values.all { it in 0f..1f }, "seed $seed: ${values.min()} to ${values.max()}")
        }
    }

    @Test
    fun `the noise is varied, and not flat`() {
        val values = noise()

        assertTrue(values.max() - values.min() > 0.3f, "range ${values.max() - values.min()}")
        assertTrue(values.toSet().size > size * size / 4)
    }

    @Test
    fun `the same seed gives the same noise and another seed gives other noise`() {
        assertArrayEquals(noise(7), noise(7))
        assertFalse(noise(7).contentEquals(noise(8)))
    }

    // The biggest step from one pixel to the next that the noise can have: the grids of the four
    // octaves have 3, 6, 12 and 24 cells over 256 pixels, a cell goes from one value to the next in
    // at most 1.5 times (the steepest part of the curve) its value difference per its width
    private val biggestPossibleStep = 0.045f

    @Test
    fun `the noise is soft, a pixel is never far from the next one`() {
        FogLayers.SEEDS.forEach { seed ->
            val values = noise(seed, size = 256)

            var biggest = 0f
            for (y in 0 until 256) {
                for (x in 0 until 255) {
                    biggest = maxOf(biggest, abs(values[y * 256 + x + 1] - values[y * 256 + x]))
                }
            }
            // Clouds, not static
            assertTrue(biggest < biggestPossibleStep, "seed $seed: biggest step $biggest")
        }
    }

    @Test
    fun `the noise tiles, the right edge continues into the left edge`() {
        FogLayers.SEEDS.forEach { seed ->
            val values = noise(seed, size = 256)

            // The step over the seam is no bigger than a step anywhere else. A texture which did not
            // tile would jump by a third or so
            var seam = 0f
            for (y in 0 until 256) {
                seam = maxOf(seam, abs(values[y * 256] - values[y * 256 + 255]))
            }
            assertTrue(seam < biggestPossibleStep, "seed $seed: seam $seam")
        }
    }

    @Test
    fun `the noise tiles, the bottom continues into the top`() {
        FogLayers.SEEDS.forEach { seed ->
            val values = noise(seed, size = 256)

            var seam = 0f
            for (x in 0 until 256) {
                seam = maxOf(seam, abs(values[x] - values[255 * 256 + x]))
            }
            assertTrue(seam < biggestPossibleStep, "seed $seed: seam $seam")
        }
    }

    @Test
    fun `the corners of the texture meet`() {
        val values = noise(size = 256)

        // All four corners are neighbours of each other, going round the edges
        val corners = listOf(values[0], values[255], values[255 * 256], values[255 * 256 + 255])
        assertTrue(corners.max() - corners.min() < 0.2f, "corners $corners")
        assertTrue(abs(values[0] - values[255]) < biggestPossibleStep)
        assertTrue(abs(values[0] - values[255 * 256]) < biggestPossibleStep)
    }

    @Test
    fun `finer grids add detail`() {
        val smooth = FogNoise.tileable(128, seed = 3, octaves = 1)
        val detailed = FogNoise.tileable(128, seed = 3, octaves = 4)

        fun roughness(values: FloatArray) = (0 until 127).sumOf { x -> abs(values[x + 1] - values[x]).toDouble() }

        assertTrue(roughness(detailed) > roughness(smooth))
    }

    // How much fog there is

    @Test
    fun `low noise is clear air, high noise is thick fog, and in between it thickens`() {
        assertEquals(0f, FogNoise.fogAmount(0f))
        assertEquals(0f, FogNoise.fogAmount(0.30f))
        assertEquals(1f, FogNoise.fogAmount(0.66f))
        assertEquals(1f, FogNoise.fogAmount(1f))
        assertEquals(0.5f, FogNoise.fogAmount(0.48f), 0.001f)
    }

    @Test
    fun `the fog starts and is all there close to the middle of the noise, where most of it is`() {
        // Fog which starts at the middle is thick on a good half of the picture
        assertEquals(0.5f, FogNoise.fogAmount(0.5f), 0.1f)
        assertTrue(FogNoise.fogAmount(0.5f) > FogNoise.fogAmount(0.45f))
        assertTrue(FogNoise.fogAmount(0.34f) > 0f, "it starts earlier than it did, at 0.34")
        assertTrue(FogNoise.fogAmount(0.74f) == 1f)
    }

    @Test
    fun `fog only thickens as the noise rises`() {
        var previous = -1f
        for (step in 0..100) {
            val amount = FogNoise.fogAmount(step / 100f)
            assertTrue(amount >= previous, "at $step")
            previous = amount
        }
    }

    // The texture

    @Test
    fun `the texture has the colour of fog and only its alpha differs`() {
        val pixels = FogNoise.texturePixels(1L, red = 232, green = 236, blue = 242, size = size)

        assertEquals(size * size, pixels.size)
        pixels.forEach { pixel ->
            assertEquals(232, (pixel shr 16) and 0xFF)
            assertEquals(236, (pixel shr 8) and 0xFF)
            assertEquals(242, pixel and 0xFF)
        }
    }

    @Test
    fun `every texture of the fog has clear air and cloud in it`() {
        FogLayers.SEEDS.forEach { seed ->
            val alphas = FogNoise.texturePixels(seed, 232, 236, 242).map { (it ushr 24) and 0xFF }
            val clear = alphas.count { it == 0 }
            val thick = alphas.count { it > 128 }

            println("fog seed $seed: clear $clear, thick $thick, of ${alphas.size}")
            assertTrue(clear > 2000, "seed $seed: clear pixels: $clear")
            assertTrue(thick > 2000, "seed $seed: thick pixels: $thick")
            assertTrue(alphas.max() > 200, "seed $seed: thickest ${alphas.max()}")
        }
    }

    @Test
    fun `the fog is dense, more than a third of every texture is thick, and not flat`() {
        FogLayers.SEEDS.forEach { seed ->
            val alphas = FogNoise.texturePixels(seed, 232, 236, 242).map { ((it ushr 24) and 0xFF) / 255f }.sorted()
            val thick = alphas.count { it > 0.5f }
            val spread = alphas[alphas.size * 9 / 10] - alphas[alphas.size / 10]

            println("fog seed $seed: thick $thick of ${alphas.size}, spread $spread, mean ${alphas.average()}")
            assertTrue(thick > alphas.size / 3, "seed $seed: thick pixels: $thick")
            // Banks and thin places side by side, so that it reads as fog and not as a grey screen
            assertTrue(spread > 0.5f, "seed $seed: spread $spread")
            assertTrue(alphas.average() in 0.3..0.6, "seed $seed: mean ${alphas.average()}")
        }
    }

    @Test
    fun `the alpha of the texture is the amount of fog`() {
        val noise = FogNoise.tileable(size, seed = 5)
        val pixels = FogNoise.texturePixels(5L, 0, 0, 0, size = size)

        pixels.forEachIndexed { i, pixel ->
            assertEquals(EffectMath.toByte(FogNoise.fogAmount(noise[i])), (pixel ushr 24) and 0xFF)
        }
    }

    @Test
    fun `the three fog layers are made from different noise`() {
        val layers = FogLayers.SEEDS.map { FogNoise.tileable(64, it) }

        assertFalse(layers[0].contentEquals(layers[1]))
        assertFalse(layers[1].contentEquals(layers[2]))
        assertFalse(layers[0].contentEquals(layers[2]))
    }
}
