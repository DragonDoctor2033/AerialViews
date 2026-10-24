package com.neilturner.aerialviews.providers.immich

import android.net.Uri
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherCondition.FOG
import com.neilturner.aerialviews.models.enums.WeatherCondition.RAIN
import com.neilturner.aerialviews.models.prefs.ImmichAssetPrefs
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ImmichAssetMapperWeatherTest {
    private fun mapper(): ImmichAssetMapper {
        val prefs = mockk<ImmichAssetPrefs>()
        every { prefs.includeVideos } returns true
        every { prefs.includePhotos } returns true
        val urlBuilder = mockk<ImmichUrlBuilder>()
        // The address of an Immich asset only holds its id, so it says nothing about the weather
        every { urlBuilder.getAssetUri(any(), any()) } returns mockk<Uri>()
        return ImmichAssetMapper(prefs, urlBuilder)
    }

    @Test
    fun `the folders and file name of the original path tag the media`() {
        val results =
            mapper().processAssets(
                listOf(
                    Asset(id = "a1", originalPath = "/mnt/external/Weather/Rain/Night/forest.mp4"),
                    Asset(id = "a2", originalPath = "/usr/src/app/upload/library/admin/2026/foggy_morning.mov"),
                    Asset(id = "a3", originalPath = "/usr/src/app/upload/library/admin/2026/holiday.mp4"),
                ),
            )

        val (rain, fog, plain) = results.media
        assertEquals(setOf(RAIN), rain.metadata.weather)
        assertEquals(TimeOfDay.NIGHT, rain.metadata.timeOfDay)
        assertEquals(setOf(FOG), fog.metadata.weather)
        assertEquals(TimeOfDay.UNKNOWN, fog.metadata.timeOfDay)
        assertEquals(emptySet<WeatherCondition>(), plain.metadata.weather)
    }
}
