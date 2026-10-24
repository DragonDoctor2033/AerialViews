package com.neilturner.aerialviews.ui.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class OverlayFadeSelectionTest {
    private val allCorners = setOf("BOTTOM_LEFT", "BOTTOM_RIGHT", "TOP_LEFT", "TOP_RIGHT")

    private val bottomLeft = listOf("bottom left 1", "bottom left 2")
    private val bottomRight = listOf("bottom right")
    private val topLeft = listOf("top left")
    private val topRight = listOf("top right 1", "top right 2")

    private fun select(
        corners: Set<String>,
        center: String? = null,
        bottomLeft: List<String> = this.bottomLeft,
        bottomRight: List<String> = this.bottomRight,
        topLeft: List<String> = this.topLeft,
        topRight: List<String> = this.topRight,
    ) = OverlayFadeSelection.overlaysToFade(corners, bottomLeft, bottomRight, topLeft, topRight, center)

    @Test
    fun `the corner names are the values of the fade corners setting`() {
        assertEquals("BOTTOM_LEFT", OverlayFadeSelection.BOTTOM_LEFT)
        assertEquals("BOTTOM_RIGHT", OverlayFadeSelection.BOTTOM_RIGHT)
        assertEquals("TOP_LEFT", OverlayFadeSelection.TOP_LEFT)
        assertEquals("TOP_RIGHT", OverlayFadeSelection.TOP_RIGHT)
    }

    @Test
    fun `every chosen corner fades, bottom left first and top right last`() {
        assertEquals(
            listOf("bottom left 1", "bottom left 2", "bottom right", "top left", "top right 1", "top right 2"),
            select(allCorners),
        )
    }

    @Test
    fun `a corner which is not chosen does not fade`() {
        assertEquals(listOf("bottom right", "top left"), select(setOf("BOTTOM_RIGHT", "TOP_LEFT")))
        assertEquals(listOf("top right 1", "top right 2"), select(setOf("TOP_RIGHT")))
    }

    @Test
    fun `no corner chosen and no center clock means nothing fades`() {
        assertTrue(select(emptySet()).isEmpty())
    }

    @Test
    fun `names which are not corners are ignored`() {
        assertTrue(select(setOf("CENTER", "top_left", "", "LEFT")).isEmpty())
    }

    @Test
    fun `the center clock fades together with the others, after the corners`() {
        assertEquals(
            listOf("bottom left 1", "bottom left 2", "bottom right", "top left", "top right 1", "top right 2", "center"),
            select(allCorners, center = "center"),
        )
    }

    @Test
    fun `the center clock fades with the corners which are chosen, whichever they are`() {
        assertEquals(listOf("top left", "center"), select(setOf("TOP_LEFT"), center = "center"))
        assertEquals(listOf("bottom left 1", "bottom left 2", "center"), select(setOf("BOTTOM_LEFT"), center = "center"))
    }

    @Test
    fun `the center clock fades even when no corner has an overlay`() {
        // The clock is on its own, so the auto-hide settings still have to hide it
        val result =
            select(
                allCorners,
                center = "center",
                bottomLeft = emptyList(),
                bottomRight = emptyList(),
                topLeft = emptyList(),
                topRight = emptyList(),
            )

        assertEquals(listOf("center"), result)
    }

    @Test
    fun `the center clock has no corner to choose, so it fades even if none are chosen`() {
        assertEquals(listOf("center"), select(emptySet(), center = "center"))
    }

    @Test
    fun `the center clock is in the list once`() {
        assertEquals(1, select(allCorners, center = "center").count { it == "center" })
    }
}
