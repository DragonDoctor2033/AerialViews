package com.neilturner.aerialviews.services

import android.content.Context
import android.os.Bundle
import com.neilturner.aerialviews.data.PlaylistCacheRepository
import com.neilturner.aerialviews.data.network.NetworkHelper
import com.neilturner.aerialviews.models.MediaFetchResult
import com.neilturner.aerialviews.models.MediaPlaylist
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.ProviderSourceType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLEAR
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLOUDY
import com.neilturner.aerialviews.models.enums.WeatherCondition.FOG
import com.neilturner.aerialviews.models.enums.WeatherCondition.RAIN
import com.neilturner.aerialviews.models.enums.WeatherCondition.SNOW
import com.neilturner.aerialviews.models.enums.WeatherCondition.STORM
import com.neilturner.aerialviews.models.music.MusicPlaylist
import com.neilturner.aerialviews.models.music.MusicTrack
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import com.neilturner.aerialviews.providers.MediaProvider
import com.neilturner.aerialviews.providers.ProviderFetchResult
import com.neilturner.aerialviews.utils.FirebaseHelper
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MediaServiceWeatherTest {
    private val context = mockk<Context>(relaxed = true)

    private var weatherLookups = 0

    @BeforeEach
    fun setUp() {
        mockkConstructor(Bundle::class)
        every { anyConstructed<Bundle>().putString(any(), any()) } returns Unit
        every { anyConstructed<Bundle>().keySet() } returns emptySet()
        mockkObject(FirebaseHelper)
        every { FirebaseHelper.analyticsEvent(any(), any()) } returns Unit
        mockkObject(NetworkHelper)
        every { NetworkHelper.isOnWifiOrEthernet(any()) } returns true
        mockkObject(TimeOfDayHelper)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(TimeOfDayHelper)
        unmockkObject(NetworkHelper)
        unmockkObject(FirebaseHelper)
        unmockkConstructor(Bundle::class)
    }

    private fun video(
        name: String,
        vararg tags: WeatherCondition,
        timeOfDay: TimeOfDay = TimeOfDay.UNKNOWN,
    ) = AerialMedia(
        uri = mockk(relaxed = true, name = name),
        type = AerialMediaType.VIDEO,
        source = AerialMediaSource.LOCAL,
        metadata = AerialMediaMetadata(weather = tags.toSet(), timeOfDay = timeOfDay),
    )

    private fun photo(name: String) =
        AerialMedia(
            uri = mockk(relaxed = true, name = name),
            type = AerialMediaType.IMAGE,
            source = AerialMediaSource.LOCAL,
        )

    private fun service(
        media: List<AerialMedia>,
        config: MediaService.Config = config(),
        music: List<MusicTrack> = emptyList(),
        weather: suspend () -> WeatherCondition?,
    ) = MediaService(
        context = context,
        providers = mutableListOf(FakeMediaProvider(context, media, music)),
        config = config,
        hashFn = { "test-hash" },
        currentWeather = {
            weatherLookups++
            weather()
        },
    )

    private fun config() =
        MediaService.Config(
            removeDuplicates = false,
            ignoreNonManifestVideos = false,
            autoTimeOfDay = false,
            playlistTimeOfDayDayIncludes = emptySet(),
            playlistTimeOfDayNightIncludes = emptySet(),
            playlistCache = false,
            shuffleVideos = false,
            shuffleMusic = false,
            repeatMusic = false,
            weatherMatch = true,
        )

    private fun MediaPlaylist.items(): List<AerialMedia> = List(size) { checkNotNull(nextItem()) }

    // Matching to the weather

    @Test
    fun `videos tagged for the current weather are played`() =
        runTest {
            val rain = video("rain", RAIN)
            val snow = video("snow", SNOW)
            val plain = video("plain")
            val picture = photo("picture")

            val result = service(listOf(rain, snow, plain, picture)) { RAIN }.fetchMedia()

            assertEquals(setOf(rain, picture), result.mediaPlaylist.items().toSet())
            assertEquals(2, result.mediaPlaylist.size)
        }

    @Test
    fun `untagged videos are played when none are tagged for the current weather`() =
        runTest {
            val snow = video("snow", SNOW)
            val plain = video("plain")

            val result = service(listOf(snow, plain)) { CLEAR }.fetchMedia()

            assertEquals(listOf(plain), result.mediaPlaylist.items())
        }

    @Test
    fun `the fallback chain is followed`() =
        runTest {
            val rain = video("rain", RAIN)
            val cloudy = video("cloudy", CLOUDY)
            val plain = video("plain")

            val result = service(listOf(plain, cloudy, rain)) { STORM }.fetchMedia()

            assertEquals(listOf(rain), result.mediaPlaylist.items())
        }

    @Test
    fun `everything is played when every video is tagged for other weather`() =
        runTest {
            val snow = video("snow", SNOW)
            val fog = video("fog", FOG)

            val result = service(listOf(snow, fog)) { CLEAR }.fetchMedia()

            assertEquals(setOf(snow, fog), result.mediaPlaylist.items().toSet())
        }

    @Test
    fun `nothing is filtered when the weather is not known`() =
        runTest {
            val snow = video("snow", SNOW)
            val plain = video("plain")

            val result = service(listOf(snow, plain)) { null }.fetchMedia()

            assertEquals(1, weatherLookups)
            assertEquals(setOf(snow, plain), result.mediaPlaylist.items().toSet())
        }

    @Test
    fun `nothing is filtered when looking up the weather fails`() =
        runTest {
            val snow = video("snow", SNOW)
            val plain = video("plain")

            val result = service(listOf(snow, plain)) { error("no network") }.fetchMedia()

            assertEquals(setOf(snow, plain), result.mediaPlaylist.items().toSet())
        }

    @Test
    fun `cancelling while looking up the weather is not swallowed`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                service(listOf(video("plain"))) { throw CancellationException("cancelled") }.fetchMedia()
            }
        }
    }

    @Test
    fun `the weather is not looked up when matching is off`() =
        runTest {
            val snow = video("snow", SNOW)
            val rain = video("rain", RAIN)

            val result =
                service(listOf(snow, rain), config = config().copy(weatherMatch = false)) { RAIN }
                    .fetchMedia()

            assertEquals(0, weatherLookups)
            assertEquals(setOf(snow, rain), result.mediaPlaylist.items().toSet())
        }

    @Test
    fun `the playlist is never empty`() =
        runTest {
            val snow = video("snow", SNOW)

            val result = service(listOf(snow)) { FOG }.fetchMedia()

            assertEquals(listOf(snow), result.mediaPlaylist.items())
        }

    // Time of day

    @Test
    fun `the time of day filter still works and comes first`() =
        runTest {
            every { TimeOfDayHelper.getCurrentTimePeriod() } returns TimeOfDay.DAY
            val rainAtNight = video("rainAtNight", RAIN, timeOfDay = TimeOfDay.NIGHT)
            val plainByDay = video("plainByDay", timeOfDay = TimeOfDay.DAY)

            val result =
                service(
                    listOf(rainAtNight, plainByDay),
                    config = config().copy(autoTimeOfDay = true),
                ) { RAIN }.fetchMedia()

            // The night clip is not played by day, even though it is the only one for the rain
            assertEquals(listOf(plainByDay), result.mediaPlaylist.items())
        }

    @Test
    fun `the weather picks from the clips that suit the time of day`() =
        runTest {
            every { TimeOfDayHelper.getCurrentTimePeriod() } returns TimeOfDay.NIGHT
            val rainAtNight = video("rainAtNight", RAIN, timeOfDay = TimeOfDay.NIGHT)
            val rainByDay = video("rainByDay", RAIN, timeOfDay = TimeOfDay.DAY)
            val rainAnytime = video("rainAnytime", RAIN)
            val snowAtNight = video("snowAtNight", SNOW, timeOfDay = TimeOfDay.NIGHT)

            val result =
                service(
                    listOf(rainAtNight, rainByDay, rainAnytime, snowAtNight),
                    config = config().copy(autoTimeOfDay = true),
                ) { RAIN }.fetchMedia()

            assertEquals(setOf(rainAtNight, rainAnytime), result.mediaPlaylist.items().toSet())
        }

    @Test
    fun `the time of day filter is unchanged when matching is off`() =
        runTest {
            every { TimeOfDayHelper.getCurrentTimePeriod() } returns TimeOfDay.DAY
            val rainAtNight = video("rainAtNight", RAIN, timeOfDay = TimeOfDay.NIGHT)
            val snowByDay = video("snowByDay", SNOW, timeOfDay = TimeOfDay.DAY)

            val result =
                service(
                    listOf(rainAtNight, snowByDay),
                    config = config().copy(autoTimeOfDay = true, weatherMatch = false),
                ) { RAIN }.fetchMedia()

            assertEquals(listOf(snowByDay), result.mediaPlaylist.items())
        }

    // Playlist cache

    @Test
    fun `the playlist that is cached is the one matched to the weather`() =
        runTest {
            val cachedItems = slot<List<AerialMedia>>()
            mockkConstructor(PlaylistCacheRepository::class)

            try {
                coEvery { anyConstructed<PlaylistCacheRepository>().isCacheValid(any()) } returns false
                coEvery { anyConstructed<PlaylistCacheRepository>().getCachedPlaylist() } returns null
                coEvery {
                    anyConstructed<PlaylistCacheRepository>()
                        .cachePlaylist(capture(cachedItems), any(), any(), any())
                } just Runs

                val rain = video("rain", RAIN)
                val snow = video("snow", SNOW)

                val result =
                    service(
                        listOf(rain, snow),
                        config = config().copy(playlistCache = true),
                    ) { RAIN }.fetchMedia()

                assertEquals(listOf(rain), cachedItems.captured)
                assertEquals(listOf(rain), result.mediaPlaylist.items())
            } finally {
                unmockkConstructor(PlaylistCacheRepository::class)
            }
        }

    // The weather the playlist was matched to

    @Test
    fun `the result says which weather the playlist was matched to`() =
        runTest {
            val result = service(listOf(video("rain", RAIN), video("plain"))) { RAIN }.fetchMedia()

            assertEquals(RAIN, result.weatherCondition)
        }

    @Test
    fun `the result says the weather even when no video is tagged for it`() =
        runTest {
            val result = service(listOf(video("snow", SNOW), video("plain"))) { CLEAR }.fetchMedia()

            // The videos which were left are untagged ones, but the playlist was made for the clear sky
            assertEquals(CLEAR, result.weatherCondition)
        }

    @Test
    fun `the result has no weather when the weather was not known`() =
        runTest {
            val result = service(listOf(video("snow", SNOW), video("plain"))) { null }.fetchMedia()

            assertNull(result.weatherCondition)
        }

    @Test
    fun `the result has no weather when looking it up failed`() =
        runTest {
            val result = service(listOf(video("plain"))) { error("no network") }.fetchMedia()

            assertNull(result.weatherCondition)
        }

    @Test
    fun `the result has no weather when matching is off`() =
        runTest {
            val result =
                service(listOf(video("rain", RAIN)), config = config().copy(weatherMatch = false)) { RAIN }
                    .fetchMedia()

            assertNull(result.weatherCondition)
            assertEquals(0, weatherLookups)
        }

    @Test
    fun `each playlist is made for the weather the service is given`() =
        runTest {
            val rain = video("rain", RAIN)
            val snow = video("snow", SNOW)

            val forRain = service(listOf(rain, snow)) { RAIN }.fetchMedia()
            val forSnow = service(listOf(rain, snow)) { SNOW }.fetchMedia()

            assertEquals(listOf(RAIN, SNOW), listOf(forRain.weatherCondition, forSnow.weatherCondition))
            assertEquals(listOf(rain), forRain.mediaPlaylist.items())
            assertEquals(listOf(snow), forSnow.mediaPlaylist.items())
        }

    @Test
    fun `a playlist from the cache says which weather it was matched to`() =
        runTest {
            val cached =
                MediaFetchResult(
                    mediaPlaylist = MediaPlaylist(listOf(video("rain", RAIN))),
                    musicPlaylist = null,
                    musicResumeIndex = 2,
                    isFromCache = true,
                )
            mockkConstructor(PlaylistCacheRepository::class)

            try {
                coEvery { anyConstructed<PlaylistCacheRepository>().isCacheValid(any()) } returns true
                coEvery { anyConstructed<PlaylistCacheRepository>().getCachedPlaylist() } returns cached

                val result =
                    service(listOf(video("rain", RAIN)), config = config().copy(playlistCache = true)) { RAIN }
                        .fetchMedia()

                assertEquals(RAIN, result.weatherCondition)
                assertSame(cached.mediaPlaylist, result.mediaPlaylist)
                assertEquals(2, result.musicResumeIndex)
                assertTrue(result.isFromCache)
            } finally {
                unmockkConstructor(PlaylistCacheRepository::class)
            }
        }

    @Test
    fun `a playlist which was just cached says which weather it was matched to`() =
        runTest {
            val readBack =
                MediaFetchResult(
                    mediaPlaylist = MediaPlaylist(listOf(video("snow", SNOW))),
                    musicPlaylist = null,
                    isFromCache = true,
                )
            mockkConstructor(PlaylistCacheRepository::class)

            try {
                coEvery { anyConstructed<PlaylistCacheRepository>().isCacheValid(any()) } returns false
                coEvery { anyConstructed<PlaylistCacheRepository>().getCachedPlaylist() } returns readBack
                coEvery {
                    anyConstructed<PlaylistCacheRepository>().cachePlaylist(any(), any(), any(), any())
                } just Runs

                val result =
                    service(listOf(video("snow", SNOW)), config = config().copy(playlistCache = true)) { SNOW }
                        .fetchMedia()

                assertEquals(SNOW, result.weatherCondition)
                assertSame(readBack.mediaPlaylist, result.mediaPlaylist)
            } finally {
                unmockkConstructor(PlaylistCacheRepository::class)
            }
        }

    @Test
    fun `a playlist which was not read back from the cache still says which weather it was matched to`() =
        runTest {
            mockkConstructor(PlaylistCacheRepository::class)

            try {
                coEvery { anyConstructed<PlaylistCacheRepository>().isCacheValid(any()) } returns false
                coEvery { anyConstructed<PlaylistCacheRepository>().getCachedPlaylist() } returns null
                coEvery {
                    anyConstructed<PlaylistCacheRepository>().cachePlaylist(any(), any(), any(), any())
                } just Runs

                val result =
                    service(listOf(video("fog", FOG)), config = config().copy(playlistCache = true)) { FOG }
                        .fetchMedia()

                assertEquals(FOG, result.weatherCondition)
                assertEquals(1, result.mediaPlaylist.size)
            } finally {
                unmockkConstructor(PlaylistCacheRepository::class)
            }
        }

    // Replacing a playlist which is playing from the cache

    private fun withCache(block: suspend () -> Unit) =
        runTest {
            mockkConstructor(PlaylistCacheRepository::class)
            try {
                coEvery { anyConstructed<PlaylistCacheRepository>().isCacheValid(any()) } returns false
                coEvery { anyConstructed<PlaylistCacheRepository>().getCachedPlaylist() } returns null
                coEvery {
                    anyConstructed<PlaylistCacheRepository>().cachePlaylist(any(), any(), any(), any())
                } just Runs
                block()
            } finally {
                unmockkConstructor(PlaylistCacheRepository::class)
            }
        }

    private fun cacheWrites() =
        coVerify(exactly = 1) { anyConstructed<PlaylistCacheRepository>().cachePlaylist(any(), any(), any(), any()) }

    private fun noCacheWrites() =
        coVerify(exactly = 0) { anyConstructed<PlaylistCacheRepository>().cachePlaylist(any(), any(), any(), any()) }

    @Test
    fun `a playlist which found nothing does not empty the cache when asked to keep it`() =
        withCache {
            val result =
                service(emptyList(), config = config().copy(playlistCache = true)) { RAIN }
                    .fetchMedia(keepCacheWhenEmpty = true)

            noCacheWrites()
            coVerify(exactly = 0) { anyConstructed<PlaylistCacheRepository>().getCachedPlaylist() }
            assertEquals(0, result.mediaPlaylist.size)
            assertEquals(RAIN, result.weatherCondition)
        }

    @Test
    fun `a playlist which found nothing is cached as it always was when not asked to keep the cache`() =
        withCache {
            val result =
                service(emptyList(), config = config().copy(playlistCache = true)) { RAIN }
                    .fetchMedia()

            cacheWrites()
            assertEquals(0, result.mediaPlaylist.size)
        }

    @Test
    fun `a playlist which found something is cached even when asked to keep the cache if it is empty`() =
        withCache {
            val rain = video("rain", RAIN)

            val result =
                service(listOf(rain), config = config().copy(playlistCache = true)) { RAIN }
                    .fetchMedia(keepCacheWhenEmpty = true)

            cacheWrites()
            assertEquals(listOf(rain), result.mediaPlaylist.items())
        }

    @Test
    fun `asking to keep the cache changes nothing while the cache is off`() =
        runTest {
            val rain = video("rain", RAIN)

            val result =
                service(listOf(rain), config = config().copy(playlistCache = false)) { RAIN }
                    .fetchMedia(keepCacheWhenEmpty = true)
            val empty =
                service(emptyList(), config = config().copy(playlistCache = false)) { RAIN }
                    .fetchMedia(keepCacheWhenEmpty = true)

            assertEquals(listOf(rain), result.mediaPlaylist.items())
            assertEquals(0, empty.mediaPlaylist.size)
        }

    private fun track(name: String) = MusicTrack(uri = mockk(relaxed = true, name = name), source = AerialMediaSource.LOCAL)

    private fun cachedMusicInto(cachedMusic: MutableList<MusicPlaylist?>) {
        coEvery {
            anyConstructed<PlaylistCacheRepository>().cachePlaylist(any(), any(), any(), any())
        } coAnswers {
            cachedMusic += secondArg<MusicPlaylist?>()
        }
    }

    @Test
    fun `the music which is playing is cached as it is, not listed and shuffled again`() =
        withCache {
            val playing = MusicPlaylist(tracks = listOf(track("b"), track("a")), shuffle = true, repeat = true)
            val cachedMusic = mutableListOf<MusicPlaylist?>()
            cachedMusicInto(cachedMusic)

            val result =
                service(
                    listOf(video("rain", RAIN)),
                    config = config().copy(playlistCache = true, shuffleMusic = true),
                    music = listOf(track("a"), track("b"), track("c")),
                ) { RAIN }.fetchMedia(playingMusic = playing)

            assertSame(playing, cachedMusic.single())
            assertSame(playing, result.musicPlaylist)
        }

    @Test
    fun `the music is listed as usual when none is playing`() =
        withCache {
            val listed = listOf(track("a"), track("b"), track("c"))
            val cachedMusic = mutableListOf<MusicPlaylist?>()
            cachedMusicInto(cachedMusic)

            service(
                listOf(video("rain", RAIN)),
                config = config().copy(playlistCache = true),
                music = listed,
            ) { RAIN }.fetchMedia()

            assertEquals(listed, cachedMusic.single()?.tracks)
        }

    @Test
    fun `the result keeps the music which is playing while the cache is off`() =
        runTest {
            val playing = MusicPlaylist(tracks = listOf(track("x")))

            val result =
                service(listOf(video("rain", RAIN)), music = listOf(track("a"))) { RAIN }
                    .fetchMedia(playingMusic = playing)

            assertSame(playing, result.musicPlaylist)
        }

    private class FakeMediaProvider(
        context: Context,
        private val media: List<AerialMedia>,
        private val music: List<MusicTrack> = emptyList(),
    ) : MediaProvider(context) {
        override val type: ProviderSourceType = ProviderSourceType.LOCAL
        override val enabled: Boolean = true

        override suspend fun fetch(): ProviderFetchResult = ProviderFetchResult.Success(media = media, summary = "")

        override suspend fun fetchMusic(): List<MusicTrack> = music

        override suspend fun fetchMetadata(media: List<AerialMedia>): List<AerialMedia> = media

        override fun settingsHash(): String = "fake"
    }
}
