package com.neilturner.aerialviews.services.weather

import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.videos.AerialMedia

/**
 * Picks the videos that suit the weather outside.
 *
 * The first set that is not empty wins:
 *  1. Videos tagged for the current condition, then for each of its fallbacks in turn
 *     (storm, rain, cloudy; rain, cloudy; snow, cloudy; fog, cloudy; clear and cloudy have none).
 *  2. Videos with no weather tag, so that snow never shows when it is clear.
 *  3. All of the videos, so the playlist is never left empty.
 *
 * Photos have no weather tags and are always kept.
 */
object WeatherMediaFilter {
    fun filter(
        media: List<AerialMedia>,
        condition: WeatherCondition,
    ): List<AerialMedia> {
        val videos = media.filter { it.type == AerialMediaType.VIDEO }

        val taggedFor = condition.lookupOrder.firstOrNull { tag -> videos.any { tag in it.metadata.weather } }
        if (taggedFor != null) {
            return media.filter { it.type != AerialMediaType.VIDEO || taggedFor in it.metadata.weather }
        }

        if (videos.any { it.metadata.weather.isEmpty() }) {
            return media.filter { it.type != AerialMediaType.VIDEO || it.metadata.weather.isEmpty() }
        }

        return media
    }

    /**
     * The part of the playlist cache key that comes from the weather. Nothing is added while the
     * setting is off, so playlists saved before it existed stay valid. A playlist that was filtered
     * for one condition must never be reused for another, or for when the condition is not known.
     */
    fun cacheKey(
        enabled: Boolean,
        condition: WeatherCondition?,
    ): String? = if (enabled) "weather:${condition?.name ?: "unknown"}" else null
}
