package com.neilturner.aerialviews.data

import android.net.Uri
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.SceneType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLEAR
import com.neilturner.aerialviews.models.enums.WeatherCondition.FOG
import com.neilturner.aerialviews.models.enums.WeatherCondition.RAIN
import com.neilturner.aerialviews.models.enums.WeatherCondition.STORM
import com.neilturner.aerialviews.models.videos.AerialExifMetadata
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class CachedMediaMapperTest {
    // There is no Robolectric, so android.net.Uri is stubbed to keep the text it was made from
    private fun fakeUri(value: String): Uri {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns value
        return uri
    }

    @BeforeEach
    fun stubUriParsing() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers { fakeUri(firstArg()) }
    }

    @AfterEach
    fun unstubUriParsing() {
        unmockkStatic(Uri::class)
    }

    private fun media(weather: Set<WeatherCondition>) =
        AerialMedia(
            uri = fakeUri("file:///storage/Weather/rain/night/forest.mp4"),
            type = AerialMediaType.VIDEO,
            source = AerialMediaSource.LOCAL,
            metadata =
                AerialMediaMetadata(
                    shortDescription = "Forest in the rain",
                    pointsOfInterest = mapOf(0 to "Trees", 30 to "Lake"),
                    timeOfDay = TimeOfDay.NIGHT,
                    scene = SceneType.NATURE,
                    albumName = "Album",
                    title = "Forest",
                    weather = weather,
                    exif = AerialExifMetadata(date = "2026-01-02", city = "Dublin", country = "Ireland"),
                ),
            audioUri = fakeUri("file:///storage/Weather/rain/night/forest.m4a"),
        )

    @Test
    fun `weather tags are saved in the playlist cache`() {
        val entity = media(setOf(STORM, RAIN)).toCachedEntity(playlistOrder = 7)

        assertEquals("RAIN,STORM", entity.weather)
        assertEquals(7, entity.playlistOrder)
    }

    @Test
    fun `untagged media is saved without a tag`() {
        val entity = media(emptySet()).toCachedEntity(playlistOrder = 0)

        assertNull(entity.weather)
    }

    @Test
    fun `weather tags survive a trip through the playlist cache`() {
        listOf(emptySet(), setOf(FOG), setOf(CLEAR, RAIN), WeatherCondition.entries.toSet()).forEach { weather ->
            val original = media(weather)

            val restored = original.toCachedEntity(playlistOrder = 3).toAerialMedia()

            assertEquals(weather, restored.metadata.weather, "tags $weather")
        }
    }

    @Test
    fun `everything else survives a trip through the playlist cache too`() {
        val original = media(setOf(RAIN))

        val restored = original.toCachedEntity(playlistOrder = 3).toAerialMedia()

        assertEquals(original.metadata, restored.metadata)
        assertEquals(original.type, restored.type)
        assertEquals(original.source, restored.source)
        assertEquals(original.uri.toString(), restored.uri.toString())
        assertEquals(original.audioUri.toString(), restored.audioUri.toString())
    }

    @Test
    fun `playlists cached before the weather tags existed are untagged`() {
        val legacyRow = media(setOf(RAIN)).toCachedEntity(playlistOrder = 0).copy(weather = null)

        assertEquals(emptySet<WeatherCondition>(), legacyRow.toAerialMedia().metadata.weather)
    }

    @Test
    fun `tags written by a newer version that are not known are skipped`() {
        val newerRow = media(setOf(RAIN)).toCachedEntity(playlistOrder = 0).copy(weather = "RAIN,HAIL")

        assertEquals(setOf(RAIN), newerRow.toAerialMedia().metadata.weather)
    }
}
