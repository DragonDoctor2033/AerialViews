package com.neilturner.aerialviews.providers.ncmemories

import android.net.Uri
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherCondition.FOG
import com.neilturner.aerialviews.models.enums.WeatherCondition.SNOW
import com.neilturner.aerialviews.models.prefs.NCMemoriesImagePrefs
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class NCMemoriesImageMapperWeatherTest {
    private fun mapper(): NCMemoriesImageMapper {
        val prefs = mockk<NCMemoriesImagePrefs>()
        every { prefs.includeVideos } returns true
        every { prefs.includePhotos } returns true
        val urlBuilder = mockk<NCMemoriesUrlBuilder>()
        // The address of a Nextcloud file only holds its id, so it says nothing about the weather
        every { urlBuilder.getImageUri(any(), any(), any()) } returns mockk<Uri>()
        return NCMemoriesImageMapper(prefs, urlBuilder)
    }

    @Test
    fun `the folders and file name of the file tag the media`() {
        val results =
            mapper().processImages(
                listOf(
                    Image(fileId = 1, etag = "e1", baseName = "forest.mp4", fileName = "/Videos/Weather/fog/night/forest.mp4"),
                    Image(fileId = 2, etag = "e2", baseName = "snowy_hill.mp4"),
                    Image(fileId = 3, etag = "e3", baseName = "holiday.mp4", fileName = "/Videos/holiday.mp4"),
                ),
            )

        val (fog, snow, plain) = results.media
        assertEquals(setOf(FOG), fog.metadata.weather)
        assertEquals(TimeOfDay.NIGHT, fog.metadata.timeOfDay)
        assertEquals(setOf(SNOW), snow.metadata.weather)
        assertEquals(emptySet<WeatherCondition>(), plain.metadata.weather)
    }
}
