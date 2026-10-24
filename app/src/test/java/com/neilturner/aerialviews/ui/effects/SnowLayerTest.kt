package com.neilturner.aerialviews.ui.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

internal class SnowLayerTest {
    private val width = 1920f
    private val height = 1080f

    private fun layer(
        seed: Long = 11,
        capacity: Int = 200,
        profile: SnowProfile = SnowProfiles.MIDDLE,
    ) = SnowLayer(capacity, profile, Random(seed)).also { it.resize(width, height) }

    // Makes the flakes without moving them
    private fun SnowLayer.populate(
        wanted: Int,
        wind: Float = 0f,
        intensity: Float = 0.5f,
    ) = step(0f, wind, intensity, wanted)

    private fun SnowLayer.positions(): List<Pair<Float, Float>> = (0 until count).map { centreX(it) to centreY(it) }

    // Making the snow

    @Test
    fun `a layer has no flakes until it is stepped`() {
        val layer = layer()

        assertEquals(0, layer.count)
        assertEquals(200, layer.capacity)
    }

    @Test
    fun `new snow is spread over the whole screen, as if it had been falling for a while`() {
        val layer = layer()

        layer.populate(200)

        assertEquals(200, layer.count)
        val ys = layer.positions().map { it.second }
        assertTrue(ys.min() < 0.1f * height)
        assertTrue(ys.max() > 0.9f * height)
        val xs = layer.positions().map { it.first }
        assertTrue(xs.min() < 0.1f * width)
        assertTrue(xs.max() > 0.9f * width)
        (0 until 10).forEach { tenth ->
            val inTenth = ys.count { it >= tenth * height / 10 && it < (tenth + 1) * height / 10 }
            assertTrue(inTenth in 5..40, "$inTenth flakes in the tenth $tenth")
        }
    }

    @Test
    fun `the same seed gives the same snow and another seed another snow`() {
        val first = layer(seed = 1)
        val same = layer(seed = 1)
        val other = layer(seed = 2)
        listOf(first, same, other).forEach { it.populate(150) }
        repeat(100) { listOf(first, same, other).forEach { layer -> layer.step(0.016f, 0.3f, 0.5f, 150) } }

        assertEquals(first.positions(), same.positions())
        assertTrue(first.positions() != other.positions())
    }

    // Falling

    @Test
    fun `flakes fall slowly, at the speed of their layer, give or take 25 percent`() {
        val layer = layer()
        layer.populate(200)
        val before = layer.positions()

        layer.step(1f, 0f, 0.5f, 200)

        val speed = SnowProfiles.MIDDLE.fallPxPerSecond * (0.85f + 0.30f * 0.5f)
        var checked = 0
        layer.positions().forEachIndexed { i, (_, y) ->
            val fell = y - before[i].second
            // Those which went past the bottom came back at the top
            if (fell > 0f) {
                assertTrue(fell in speed * 0.75f - 0.01f..speed * 1.25f + 0.01f, "flake $i fell $fell of $speed")
                checked++
            }
        }
        assertTrue(checked > 150, "only $checked flakes checked")
    }

    @Test
    fun `snow falls much slower than rain`() {
        val snow = layer(profile = SnowProfiles.NEAR)
        val rain = RainLayer(10, RainProfiles.FAR, Random(1)).also { it.resize(width, height) }

        assertTrue(SnowProfiles.NEAR.fallPxPerSecond * 4 < rain.speedPx(0f))
        assertTrue(snow.count == 0)
    }

    @Test
    fun `heavier snow falls a little faster`() {
        fun fall(intensity: Float): Float {
            val layer = layer(seed = 5)
            layer.populate(100, intensity = intensity)
            val before = layer.positions()
            layer.step(0.5f, 0f, intensity, 100)
            return layer
                .positions()
                .mapIndexed { i, (_, y) -> y - before[i].second }
                .filter { it > 0f }
                .average()
                .toFloat()
        }

        assertTrue(fall(1f) > fall(0f))
    }

    // Sway and wind

    @Test
    fun `flakes sway from side to side and no further than their layer says`() {
        val layer = layer()
        layer.populate(100)
        val biggestSway = SnowProfiles.MIDDLE.swayPx * 1.2f

        var swayed = false
        repeat(600) {
            layer.step(1f / 60f, 0f, 0.5f, 100)
            (0 until layer.count).forEach { i ->
                val sway = layer.centreX(i) - layer.baseX(i)
                assertTrue(abs(sway) <= biggestSway + 0.01f, "flake $i swayed $sway")
                if (abs(sway) > biggestSway * 0.3f) swayed = true
            }
        }
        assertTrue(swayed)
    }

    @Test
    fun `without wind flakes do not drift, they only sway`() {
        val layer = layer()
        layer.populate(100)
        val before = (0 until layer.count).map { layer.baseX(it) to layer.centreY(it) }

        layer.step(0.5f, 0f, 0.5f, 100)

        var checked = 0
        (0 until layer.count).forEach { i ->
            // A flake which fell off the bottom came back somewhere else
            if (layer.centreY(i) > before[i].second) {
                assertEquals(before[i].first, layer.baseX(i), 0.001f, "flake $i")
                checked++
            }
        }
        assertTrue(checked > 80, "only $checked flakes checked")
    }

    @Test
    fun `wind to the right blows the flakes to the right and wind to the left blows them to the left`() {
        val right = layer(seed = 4)
        val left = layer(seed = 4)
        right.populate(100)
        left.populate(100)
        val before = (0 until 100).map { right.baseX(it) to right.centreY(it) }

        right.step(0.2f, 1f, 0.5f, 100)
        left.step(0.2f, -1f, 0.5f, 100)

        val drift = SnowProfiles.MIDDLE.driftPxPerSecond * 0.2f
        var checked = 0
        (0 until 100).forEach { i ->
            // Flakes which fell off the bottom came back somewhere else, and those which went off a
            // side came back on the other, so they are not compared
            val fell = right.centreY(i) > before[i].second
            val wrapped = abs(right.baseX(i) - before[i].first) > width / 2
            if (fell && !wrapped) {
                assertEquals(before[i].first + drift, right.baseX(i), 0.01f, "flake $i")
                assertEquals(before[i].first - drift, left.baseX(i), 0.01f, "flake $i")
                checked++
            }
        }
        assertTrue(checked > 80, "only $checked flakes checked")
    }

    @Test
    fun `flakes near the viewer are blown faster than those far away`() {
        assertTrue(SnowProfiles.FAR.driftPxPerSecond < SnowProfiles.MIDDLE.driftPxPerSecond)
        assertTrue(SnowProfiles.MIDDLE.driftPxPerSecond < SnowProfiles.NEAR.driftPxPerSecond)
    }

    // Coming back

    @Test
    fun `flakes which have fallen off the bottom come back above the screen`() {
        val layer = layer()
        layer.populate(200)

        // So long that every flake has fallen off the bottom
        layer.step(60f, 0f, 0.5f, 200)

        layer.positions().forEachIndexed { i, (_, y) ->
            assertTrue(y < 0f, "flake $i is at $y")
        }
    }

    @Test
    fun `flakes which are blown off a side come back on the other side`() {
        val layer = layer()
        layer.populate(200)

        // Blown by the wind for half a minute, a good many widths of the screen
        repeat(300) { layer.step(0.1f, 1f, 0.5f, 200) }

        val margin = 120f
        (0 until layer.count).forEach { i ->
            assertTrue(layer.baseX(i) in -margin - 1f..width + margin + 1f, "flake $i is at ${layer.baseX(i)}")
        }
    }

    @Test
    fun `the snow keeps snowing for a long time, at the same strength`() {
        val layer = layer()
        layer.populate(200)

        repeat(60 * 60) { frame ->
            layer.step(1f / 60f, 0.5f, 0.5f, 200)
            if (frame % 120 == 0) {
                val onScreen = layer.positions().count { (x, y) -> y in 0f..height && x in 0f..width }
                assertTrue(onScreen > 130, "$onScreen flakes on the screen after $frame frames")
            }
        }
    }

    @Test
    fun `positions stay numbers however the wind and the snow are`() {
        val layer = layer()

        for (wind in listOf(-1f, 0f, 1f)) {
            for (intensity in listOf(0f, 0.5f, 1f)) {
                layer.populate(200, wind, intensity)
                repeat(100) { layer.step(0.05f, wind, intensity, 200) }
                layer.positions().forEach { (x, y) -> assertTrue(x.isFinite() && y.isFinite()) }
            }
        }
    }

    // More and fewer flakes

    @Test
    fun `flakes which are added come in from above the screen`() {
        val layer = layer()
        layer.populate(100)

        layer.step(0.016f, 0f, 0.5f, 200)

        assertEquals(200, layer.count)
        (100 until 200).forEach { i ->
            assertTrue(layer.centreY(i) < 0f, "new flake $i is at ${layer.centreY(i)}")
        }
    }

    @Test
    fun `flakes which are taken away are not drawn, and the others stay where they are`() {
        val layer = layer()
        layer.populate(200)
        val before = layer.positions()

        layer.step(0f, 0f, 0.5f, 120)

        assertEquals(120, layer.count)
        assertEquals(before.take(120), layer.positions())
    }

    @Test
    fun `asking for more flakes than there is room for gives all there is room for`() {
        val layer = layer(capacity = 40)

        layer.populate(500)

        assertEquals(40, layer.count)
    }

    @Test
    fun `asking for fewer than none gives none`() {
        val layer = layer()
        layer.populate(100)

        layer.step(0.016f, 0f, 0.5f, -5)

        assertEquals(0, layer.count)
    }

    @Test
    fun `the snow comes back spread over the screen after it was gone`() {
        val layer = layer()
        layer.populate(200)
        layer.step(0.016f, 0f, 0.5f, 0)
        assertEquals(0, layer.count)

        layer.populate(200)

        assertTrue(layer.positions().count { (_, y) -> y in 0f..height } > 150)
    }

    // Size of the screen

    @Test
    fun `a new size starts the snow again`() {
        val layer = layer()
        layer.populate(100)

        layer.resize(1280f, 720f)

        assertEquals(0, layer.count)
        layer.populate(100)
        assertTrue(layer.positions().all { (x, y) -> x in -100f..1380f && y < 721f })
    }

    @Test
    fun `nothing moves before the size of the screen is known`() {
        val layer = SnowLayer(100, SnowProfiles.NEAR, Random(1))

        layer.step(0.016f, 0f, 0.5f, 100)

        assertEquals(0, layer.count)
    }

    @Test
    fun `flakes are as big as the profile says, give or take a bit, and follow the height of the screen`() {
        val full = layer()
        val half = SnowLayer(200, SnowProfiles.MIDDLE, Random(11)).also { it.resize(960f, 540f) }
        full.populate(100)
        half.populate(100)

        (0 until 100).forEach { i ->
            assertTrue(full.diameterPx(i) in SnowProfiles.MIDDLE.diameterPx * 0.8f - 0.01f..SnowProfiles.MIDDLE.diameterPx * 1.3f + 0.01f)
            assertEquals(full.diameterPx(i) / 2f, half.diameterPx(i), 0.001f)
        }
        assertEquals(SnowProfiles.MIDDLE.diameterPx * 1.3f, full.biggestDiameterPx, 0.001f)
        assertTrue((0 until 100).all { full.diameterPx(it) <= full.biggestDiameterPx + 0.001f })
    }

    // Looks

    @Test
    fun `near snow is bigger, faster and brighter than far snow`() {
        val all = SnowProfiles.ALL

        for (i in 0 until all.lastIndex) {
            assertTrue(all[i].diameterPx < all[i + 1].diameterPx)
            assertTrue(all[i].fallPxPerSecond < all[i + 1].fallPxPerSecond)
            assertTrue(all[i].swayPx < all[i + 1].swayPx)
            assertTrue(all[i].alpha < all[i + 1].alpha)
        }
    }

    @Test
    fun `the three layers share all the flakes, and there are most far away`() {
        val shares = SnowProfiles.ALL.map { it.share }

        assertEquals(1f, shares.sum(), 0.0001f)
        assertTrue(shares[0] > shares[1] && shares[1] > shares[2])
        assertEquals(3, SnowProfiles.ALL.size)
        assertEquals(SnowProfiles.NEAR.alpha, layer(profile = SnowProfiles.NEAR).alpha)
    }

    @Test
    fun `the near flakes are big enough to be seen from the sofa, two to three times what they were`() {
        // They were 24 px at 1080p. A flake of 56 px is 5 percent of the height of the screen
        val near = SnowProfiles.NEAR

        assertEquals(56f, near.diameterPx)
        assertTrue(near.diameterPx in 2f * 24f..3f * 24f)
        val full = layer(profile = near)
        full.populate(40)
        assertTrue((0 until 40).all { full.diameterPx(it) in 44f..74f }, "from ${full.diameterPx(0)}")
        // On a screen of 4K they are twice as big in pixels, and as big on the screen
        val big = SnowLayer(200, near, Random(11)).also { it.resize(3840f, 2160f) }
        big.populate(40)
        assertEquals(2f, big.diameterPx(5) / full.diameterPx(5), 0.001f)
    }

    @Test
    fun `the near and the middle flakes are bright, and the far ones are only a fine texture`() {
        assertEquals(0.9f, SnowProfiles.NEAR.alpha)
        assertTrue(SnowProfiles.MIDDLE.alpha in 0.75f..0.85f)
        assertTrue(SnowProfiles.FAR.diameterPx <= 12f)
        assertTrue(SnowProfiles.FAR.alpha <= 0.6f)
        assertTrue(SnowProfiles.MIDDLE.diameterPx in 20f..32f)
    }

    @Test
    fun `a flake which comes back is out of sight, with its sway and the biggest of its size`() {
        for (profile in SnowProfiles.ALL) {
            val biggest = layer(profile = profile).biggestDiameterPx
            // The margin at the sides is 120 px, at 1080p
            assertTrue(biggest / 2f + profile.swayPx * 1.2f < 120f, "$profile")
        }
    }

    // The sprite of a flake

    @Test
    fun `a flake is solid in the middle and clear at the edge`() {
        assertEquals(255, SnowSprite.alphaAt(0f))
        assertEquals(SnowSprite.BODY_ALPHA, SnowSprite.alphaAt(SnowSprite.BODY_END))
        assertEquals(0, SnowSprite.alphaAt(1f))
        assertTrue(SnowSprite.BODY_ALPHA >= 225, "nearly as solid as the middle")
        assertEquals(0.5f, SnowSprite.BODY_END)
    }

    @Test
    fun `a flake only gets clearer from the middle to the edge`() {
        var previous = 256
        for (step in 0..100) {
            val alpha = SnowSprite.alphaAt(step / 100f)
            assertTrue(alpha <= previous, "at $step")
            previous = alpha
        }
    }

    @Test
    fun `the half way to the edge of the soft part is half of the solid part`() {
        val half = (SnowSprite.BODY_END + 1f) / 2f

        assertEquals(SnowSprite.BODY_ALPHA / 2f, SnowSprite.alphaAt(half).toFloat(), 1f)
    }

    @Test
    fun `beyond the sprite is clear air`() {
        assertEquals(0, SnowSprite.alphaAt(2f))
        assertEquals(255, SnowSprite.alphaAt(-1f))
        assertEquals(255, SnowSprite.alphaAt(Float.NaN))
    }
}
