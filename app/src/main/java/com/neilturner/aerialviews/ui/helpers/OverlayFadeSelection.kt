package com.neilturner.aerialviews.ui.helpers

/**
 * Which overlays take part in auto-hide, fading out and back in together. Generic, so it can be
 * tested without android views.
 */
object OverlayFadeSelection {
    const val BOTTOM_LEFT = "BOTTOM_LEFT"
    const val BOTTOM_RIGHT = "BOTTOM_RIGHT"
    const val TOP_LEFT = "TOP_LEFT"
    const val TOP_RIGHT = "TOP_RIGHT"

    /**
     * The overlays of the corners which the user chose to fade, in the order bottom left, bottom
     * right, top left, top right. The overlay in the center of the screen belongs to no corner and
     * has no setting of its own, so it fades together with the others, whichever corners are
     * chosen and even when there are no overlays in any corner.
     */
    fun <T : Any> overlaysToFade(
        fadeCorners: Set<String>,
        bottomLeft: List<T>,
        bottomRight: List<T>,
        topLeft: List<T>,
        topRight: List<T>,
        center: T? = null,
    ): List<T> {
        val result = mutableListOf<T>()
        if (BOTTOM_LEFT in fadeCorners) result.addAll(bottomLeft)
        if (BOTTOM_RIGHT in fadeCorners) result.addAll(bottomRight)
        if (TOP_LEFT in fadeCorners) result.addAll(topLeft)
        if (TOP_RIGHT in fadeCorners) result.addAll(topRight)
        if (center != null) result.add(center)
        return result
    }
}
