package com.neilturner.aerialviews.ui.effects

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

internal class RainLayerTest {
    private val width = 1920f
    private val height = 1080f

    private fun layer(
        seed: Long = 7,
        capacity: Int = 300,
        profile: RainProfile = RainProfiles.MIDDLE,
    ) = RainLayer(capacity, profile, Random(seed)).also { it.resize(width, height) }

    // The head of a streak, from the lines which are drawn
    private fun RainLayer.headX(i: Int) = lines[i * 4 + 2]

    private fun RainLayer.headY(i: Int) = lines[i * 4 + 3]

    private fun RainLayer.tailX(i: Int) = lines[i * 4]

    private fun RainLayer.tailY(i: Int) = lines[i * 4 + 1]

    // Makes the streaks without moving them, and writes them out
    private fun RainLayer.populate(
        wanted: Int,
        tilt: Float = 0f,
        intensity: Float = 0.5f,
    ) {
        step(0f, tilt, intensity, wanted)
        writeLines(tilt, intensity)
    }

    private fun RainLayer.heads(): List<Pair<Float, Float>> = (0 until count).map { headX(it) to headY(it) }

    // Making the rain

    @Test
    fun `a layer has no streaks until it is stepped`() {
        val layer = layer()

        assertEquals(0, layer.count)
        assertEquals(300, layer.capacity)
        assertEquals(1200, layer.lines.size)
    }

    @Test
    fun `new rain is spread over the whole screen, as if it had been falling for a while`() {
        val layer = layer()

        layer.populate(300)

        assertEquals(300, layer.count)
        val ys = layer.heads().map { it.second }
        assertTrue(ys.min() < 0.1f * height, "highest ${ys.min()}")
        assertTrue(ys.max() > 0.9f * height, "lowest ${ys.max()}")
        // About a tenth of the streaks in each tenth of the screen
        (0 until 10).forEach { tenth ->
            val inTenth = ys.count { it >= tenth * height / 10 && it < (tenth + 1) * height / 10 }
            assertTrue(inTenth in 10..55, "$inTenth streaks in the tenth $tenth")
        }
    }

    @Test
    fun `new rain starts at no time, so a first frame can be made without moving it`() {
        val layer = layer()
        layer.populate(100)
        val before = layer.heads()

        layer.step(0f, 0f, 0.5f, 100)
        layer.writeLines(0f, 0.5f)

        assertEquals(before, layer.heads())
    }

    @Test
    fun `the same seed gives the same rain and another seed another rain`() {
        val first = layer(seed = 1)
        val same = layer(seed = 1)
        val other = layer(seed = 2)
        listOf(first, same, other).forEach { it.populate(200) }
        repeat(50) { listOf(first, same, other).forEach { layer -> layer.step(0.016f, 0.2f, 0.5f, 200) } }
        listOf(first, same, other).forEach { it.writeLines(0.2f, 0.5f) }

        assertArrayEquals(first.lines, same.lines)
        assertFalse(first.lines.contentEquals(other.lines))
    }

    // Falling

    @Test
    fun `streaks fall at the speed of their layer, give or take 15 percent`() {
        val layer = layer()
        layer.populate(200)
        val before = layer.heads()

        layer.step(0.1f, 0f, 0.5f, 200)
        layer.writeLines(0f, 0.5f)

        val speed = layer.speedPx(0.5f)
        var checked = 0
        layer.heads().forEachIndexed { i, (_, y) ->
            val fell = y - before[i].second
            // Those which went past the bottom came back at the top
            if (fell > 0f) {
                assertTrue(fell in speed * 0.1f * 0.85f - 0.01f..speed * 0.1f * 1.15f + 0.01f, "streak $i fell $fell of ${speed * 0.1f}")
                checked++
            }
        }
        assertTrue(checked > 150, "only $checked streaks checked")
    }

    @Test
    fun `without wind the rain falls straight down, give or take 3 degrees`() {
        val layer = layer()
        layer.populate(200)
        val before = layer.heads()

        layer.step(0.1f, 0f, 0.5f, 200)
        layer.writeLines(0f, 0.5f)

        layer.heads().forEachIndexed { i, (x, y) ->
            val fell = y - before[i].second
            if (fell > 0f) {
                // The jitter of the tilt is 0.05 of the way sideways for every way down
                assertTrue(abs(x - before[i].first) <= fell * 0.05f + 0.01f, "streak $i")
            }
        }
    }

    @Test
    fun `wind tilts the rain, the further it falls the further it is blown`() {
        val layer = layer()
        layer.populate(200, tilt = 0.5f)
        val before = layer.heads()

        layer.step(0.1f, 0.5f, 0.5f, 200)
        layer.writeLines(0.5f, 0.5f)

        var checked = 0
        layer.heads().forEachIndexed { i, (x, y) ->
            val fell = y - before[i].second
            if (fell > 0f) {
                val blown = x - before[i].first
                assertEquals(0.5f, blown / fell, 0.0501f, "streak $i")
                checked++
            }
        }
        assertTrue(checked > 150)
    }

    @Test
    fun `wind from the other side tilts the rain the other way`() {
        val layer = layer()
        layer.populate(200, tilt = -0.5f)
        val before = layer.heads()

        layer.step(0.1f, -0.5f, 0.5f, 200)
        layer.writeLines(-0.5f, 0.5f)

        layer.heads().forEachIndexed { i, (x, y) ->
            if (y - before[i].second > 0f) {
                assertTrue(x < before[i].first, "streak $i")
            }
        }
    }

    @Test
    fun `the tail of a streak is up the line of its fall`() {
        val layer = layer()
        layer.populate(100, tilt = 0.5f)

        (0 until layer.count).forEach { i ->
            val dx = layer.headX(i) - layer.tailX(i)
            val dy = layer.headY(i) - layer.tailY(i)
            assertTrue(dy > 0f, "streak $i")
            assertEquals(0.5f, dx / dy, 0.0001f, "streak $i")
        }
    }

    @Test
    fun `with no wind the streaks are vertical`() {
        val layer = layer()
        layer.populate(100)

        (0 until layer.count).forEach { i ->
            assertEquals(layer.headX(i), layer.tailX(i), 0.0001f)
            assertTrue(layer.headY(i) > layer.tailY(i))
        }
    }

    @Test
    fun `streaks are as long as the profile says, give or take 20 percent`() {
        val layer = layer()
        layer.populate(200)
        val length = layer.lengthPx(0.5f)

        (0 until layer.count).forEach { i ->
            val long = layer.headY(i) - layer.tailY(i)
            assertTrue(long in length * 0.8f - 0.01f..length * 1.2f + 0.01f, "streak $i is $long of $length")
        }
    }

    // Coming back

    @Test
    fun `streaks which have fallen off the bottom come back above the screen`() {
        val layer = layer()
        layer.populate(200)

        // So long that every streak has fallen off the bottom
        layer.step(5f, 0f, 0.5f, 200)
        layer.writeLines(0f, 0.5f)

        layer.heads().forEachIndexed { i, (_, y) ->
            assertTrue(y < 0f, "streak $i is at $y")
            assertTrue(layer.tailY(i) < 0f, "streak $i has its tail at ${layer.tailY(i)}")
        }
    }

    @Test
    fun `streaks come back in different places, not in a row`() {
        val layer = layer()
        layer.populate(200)

        layer.step(5f, 0f, 0.5f, 200)
        layer.writeLines(0f, 0.5f)

        val ys = layer.heads().map { it.second }
        assertTrue(ys.max() - ys.min() > 0.1f * height, "spread ${ys.max() - ys.min()}")
        assertTrue(ys.distinct().size > 190)
    }

    @Test
    fun `streaks which are blown out of the side come back at the top`() {
        val layer = layer()
        layer.populate(200, tilt = 0.7f)

        // Blown a long way sideways by 50 steps of a tenth of a second at the top speed
        repeat(50) { layer.step(0.1f, 0.7f, 1f, 200) }
        layer.writeLines(0.7f, 1f)

        val drift = 0.7f * height
        val margin = 160f
        layer.heads().forEachIndexed { i, (x, _) ->
            assertTrue(x >= -drift - margin - 1f && x <= width + margin + 1f, "streak $i is at $x")
        }
    }

    @Test
    fun `the rain keeps raining for a long time, at the same strength`() {
        val layer = layer()
        layer.populate(300, tilt = 0.3f)

        repeat(60 * 30) { frame ->
            layer.step(1f / 60f, 0.3f, 0.5f, 300)
            if (frame % 60 == 0) {
                layer.writeLines(0.3f, 0.5f)
                // The streaks which are on the screen make up most of them, the others are about to come in
                val onScreen = layer.heads().count { (x, y) -> y in 0f..height && x in 0f..width }
                assertTrue(onScreen > 100, "$onScreen streaks on the screen after $frame frames")
                layer.heads().forEach { (x, y) ->
                    assertTrue(y.isFinite() && x.isFinite())
                    assertTrue(y < height + 200f && y > -height * 0.5f, "head at $y")
                }
            }
        }
    }

    @Test
    fun `positions stay numbers however the wind and the rain are`() {
        val layer = layer()

        for (tilt in listOf(-0.7f, 0f, 0.7f)) {
            for (intensity in listOf(0f, 0.5f, 1f)) {
                layer.populate(300, tilt, intensity)
                repeat(100) { layer.step(0.03f, tilt, intensity, 300) }
                layer.writeLines(tilt, intensity)
                (0 until layer.lines.size).forEach { assertTrue(layer.lines[it].isFinite(), "tilt $tilt intensity $intensity") }
            }
        }
    }

    // More and fewer streaks

    @Test
    fun `streaks which are added come in from above the screen`() {
        val layer = layer()
        layer.populate(100)

        layer.step(0.016f, 0f, 0.5f, 200)
        layer.writeLines(0f, 0.5f)

        assertEquals(200, layer.count)
        (100 until 200).forEach { i ->
            assertTrue(layer.headY(i) < 0f, "new streak $i is at ${layer.headY(i)}")
            assertTrue(layer.tailY(i) < 0f)
        }
    }

    @Test
    fun `streaks which are taken away are not drawn, and the others stay where they are`() {
        val layer = layer()
        layer.populate(200)
        val before = layer.heads()

        layer.step(0f, 0f, 0.5f, 120)
        layer.writeLines(0f, 0.5f)

        assertEquals(120, layer.count)
        assertEquals(before.take(120), layer.heads())
    }

    @Test
    fun `asking for more streaks than there is room for gives all there is room for`() {
        val layer = layer(capacity = 50)

        layer.populate(500)

        assertEquals(50, layer.count)
    }

    @Test
    fun `asking for fewer than none gives none`() {
        val layer = layer()
        layer.populate(100)

        layer.step(0.016f, 0f, 0.5f, -5)

        assertEquals(0, layer.count)
    }

    @Test
    fun `the rain comes back spread over the screen after it was gone`() {
        val layer = layer()
        layer.populate(200)
        layer.step(0.016f, 0f, 0.5f, 0)
        assertEquals(0, layer.count)

        layer.populate(200)

        val ys = layer.heads().map { it.second }
        assertTrue(ys.count { it in 0f..height } > 150)
    }

    // Size of the screen

    @Test
    fun `a new size starts the rain again`() {
        val layer = layer()
        layer.populate(200)

        layer.resize(1280f, 720f)

        assertEquals(0, layer.count)
        layer.populate(200)
        assertTrue(layer.heads().all { (x, y) -> x in -50f..1330f && y < 720f + 1f })
    }

    @Test
    fun `nothing moves before the size of the screen is known`() {
        val layer = RainLayer(100, RainProfiles.NEAR, Random(1))

        layer.step(0.016f, 0f, 0.5f, 100)

        assertEquals(0, layer.count)
    }

    @Test
    fun `sizes and speeds follow the height of the screen`() {
        val full = layer()
        val half = RainLayer(300, RainProfiles.MIDDLE, Random(7)).also { it.resize(960f, 540f) }

        assertEquals(full.speedPx(0.5f) / 2f, half.speedPx(0.5f), 0.01f)
        assertEquals(full.lengthPx(0.5f) / 2f, half.lengthPx(0.5f), 0.01f)
        assertEquals(full.strokePx / 2f, half.strokePx, 0.001f)
    }

    // Looks

    @Test
    fun `heavier rain falls faster and in longer streaks`() {
        val layer = layer()

        assertTrue(layer.speedPx(1f) > layer.speedPx(0.5f))
        assertTrue(layer.speedPx(0.5f) > layer.speedPx(0f))
        assertTrue(layer.lengthPx(1f) > layer.lengthPx(0.5f))
        assertTrue(layer.lengthPx(0.5f) > layer.lengthPx(0f))
    }

    @Test
    fun `near rain is longer, faster, thicker and brighter than far rain`() {
        val layers = RainProfiles.ALL.map { layer(profile = it) }

        for (i in 0 until layers.lastIndex) {
            assertTrue(layers[i].lengthPx(0.5f) < layers[i + 1].lengthPx(0.5f))
            assertTrue(layers[i].speedPx(0.5f) < layers[i + 1].speedPx(0.5f))
            assertTrue(layers[i].strokePx < layers[i + 1].strokePx)
            assertTrue(layers[i].alpha < layers[i + 1].alpha)
        }
    }

    @Test
    fun `the three layers share all the streaks, and there are most far away`() {
        val shares = RainProfiles.ALL.map { it.share }

        assertEquals(1f, shares.sum(), 0.0001f)
        assertTrue(shares[0] > shares[1] && shares[1] > shares[2])
        assertEquals(3, RainProfiles.ALL.size)
    }

    @Test
    fun `a streak is longer than the way it falls in a frame, so it looks blurred by its movement`() {
        RainProfiles.ALL.forEach { profile ->
            val layer = layer(profile = profile)
            listOf(0f, 0.5f, 1f).forEach { intensity ->
                // The shortest streak of the layer and the fastest one
                val shortest = layer.lengthPx(intensity) * 0.8f
                val fallInAFrame = layer.speedPx(intensity) * 1.15f / 60f

                assertTrue(shortest > fallInAFrame, "length $shortest against $fallInAFrame at an intensity of $intensity")
            }
        }
    }
}
