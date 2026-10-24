package com.neilturner.aerialviews.providers.webdav

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.enums.SchemeType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLEAR
import com.neilturner.aerialviews.models.enums.WeatherCondition.RAIN
import com.neilturner.aerialviews.models.enums.WeatherCondition.SNOW
import com.neilturner.aerialviews.models.prefs.WebDavProviderPreferences
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.providers.ProviderFetchResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class WebDavMediaProviderWeatherTest {
    private val resources =
        mockk<Resources>().also { res ->
            every { res.getString(R.string.webdav_media_test_summary1) } returns "Files found: %1\$s"
            every { res.getString(R.string.webdav_media_test_summary2) } returns "Unsupported files: %1\$s"
            every { res.getString(R.string.webdav_media_test_summary3) } returns "Videos found: %1\$s"
            every { res.getString(R.string.webdav_media_test_summary4) } returns "Photos found: %1\$s"
            every { res.getString(R.string.webdav_media_test_summary5) } returns "Selected for playback: %1\$s"
        }

    private val context =
        mockk<Context>().also { ctx ->
            every { ctx.resources } returns resources
        }

    // There is no Robolectric, so android.net.Uri is stubbed to know the name of the file it was made from
    @BeforeEach
    fun stubUriParsing() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val raw = firstArg<String>()
            mockk<Uri>(relaxed = true) {
                every { lastPathSegment } returns raw.substringAfterLast('/')
            }
        }
    }

    @AfterEach
    fun unstubUriParsing() {
        unmockkStatic(Uri::class)
    }

    private fun folder(name: String) = WebDavResourceInfo(name, isDirectory = true)

    private fun file(name: String) = WebDavResourceInfo(name, isDirectory = false, modifiedTimeMs = 1)

    /**
     * ```
     * media/
     *   beach_clear_day.mp4
     *   plain.mp4
     *   Weather/
     *     rain/night/forest.mp4
     *     snow/day/x.mp4
     * ```
     */
    private fun provider(
        userName: String = "",
        password: String = "",
    ) = WebDavMediaProvider(
        context = context,
        prefs = fakePrefs(userName, password),
        clientFactory = {
            FakeWebDavListingClient(
                mapOf(
                    "http://example.com/media" to
                        listOf(folder("media"), folder("Weather"), file("beach_clear_day.mp4"), file("plain.mp4")),
                    "http://example.com/media/Weather" to listOf(folder("Weather"), folder("rain"), folder("snow")),
                    "http://example.com/media/Weather/rain" to listOf(folder("rain"), folder("night")),
                    "http://example.com/media/Weather/rain/night" to listOf(folder("night"), file("forest.mp4")),
                    "http://example.com/media/Weather/snow" to listOf(folder("snow"), folder("day")),
                    "http://example.com/media/Weather/snow/day" to listOf(folder("day"), file("x.mp4")),
                ),
            )
        },
    )

    private suspend fun WebDavMediaProvider.fetchByName(): Map<String, AerialMedia> {
        val success = assertInstanceOf(ProviderFetchResult.Success::class.java, fetch())
        return success.media.associateBy { checkNotNull(it.uri.lastPathSegment) }
    }

    @Test
    fun `folders and file names tag the media`() =
        runTest {
            val media = provider().fetchByName()

            assertEquals(4, media.size)
            assertEquals(setOf(RAIN), media.getValue("forest.mp4").metadata.weather)
            assertEquals(TimeOfDay.NIGHT, media.getValue("forest.mp4").metadata.timeOfDay)
            assertEquals(setOf(SNOW), media.getValue("x.mp4").metadata.weather)
            assertEquals(TimeOfDay.DAY, media.getValue("x.mp4").metadata.timeOfDay)
            assertEquals(setOf(CLEAR), media.getValue("beach_clear_day.mp4").metadata.weather)
            assertEquals(TimeOfDay.DAY, media.getValue("beach_clear_day.mp4").metadata.timeOfDay)
        }

    @Test
    fun `media with nothing in its name stays untagged`() =
        runTest {
            val plain = provider().fetchByName().getValue("plain.mp4")

            assertEquals(emptySet<WeatherCondition>(), plain.metadata.weather)
            assertEquals(TimeOfDay.UNKNOWN, plain.metadata.timeOfDay)
        }

    @Test
    fun `the login is never mistaken for a tag`() =
        runTest {
            // The login is put into the address of the file, but it is not where the file is stored
            val media = provider(userName = "storm", password = "fog").fetchByName()

            assertEquals(emptySet<WeatherCondition>(), media.getValue("plain.mp4").metadata.weather)
            assertEquals(setOf(RAIN), media.getValue("forest.mp4").metadata.weather)
        }

    private fun fakePrefs(
        userName: String,
        password: String,
    ): WebDavProviderPreferences =
        object : WebDavProviderPreferences {
            override var enabled: Boolean = true
            override val mediaSelection: Set<String> = setOf("VIDEOS", "PHOTOS")
            override val mediaType: ProviderMediaType? = null
            override val musicEnabled: Boolean = false
            override val includeVideos: Boolean = true
            override val includePhotos: Boolean = true
            override var scheme: SchemeType? = SchemeType.HTTP
            override var hostName: String = "example.com"
            override var pathName: String = "/media"
            override var userName: String = userName
            override var password: String = password
            override var searchSubfolders: Boolean = true
            override var validateSsl: Boolean = true

            override fun settingsHash(): String = "test"
        }

    private class FakeWebDavListingClient(
        private val responses: Map<String, List<WebDavResourceInfo>>,
    ) : WebDavListingClient {
        override fun setCredentials(
            userName: String,
            password: String,
            preemptive: Boolean,
        ) {
        }

        override fun list(url: String): List<WebDavResourceInfo> = responses[url] ?: error("Unexpected URL $url")
    }
}
