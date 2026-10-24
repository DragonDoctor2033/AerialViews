package com.neilturner.aerialviews.services.weather

import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLEAR
import com.neilturner.aerialviews.models.enums.WeatherCondition.CLOUDY
import com.neilturner.aerialviews.models.enums.WeatherCondition.FOG
import com.neilturner.aerialviews.models.enums.WeatherCondition.RAIN
import com.neilturner.aerialviews.models.enums.WeatherCondition.SNOW
import com.neilturner.aerialviews.models.enums.WeatherCondition.STORM
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class WeatherConditionProviderTest {
    private var now = 10_000_000L
    private val cache = WeatherConditionCache(clock = { now })

    private var override: WeatherCondition? = null
    private var fetchCalls = 0
    private var fetchResult: () -> Int? = { 800 }

    private val provider =
        WeatherConditionProvider(
            cache = cache,
            overrideSource = { override },
            fetchConditionId = {
                fetchCalls++
                fetchResult()
            },
        )

    private fun minutesPass(minutes: Long) {
        now += minutes.minutes.inWholeMilliseconds
    }

    // Cache

    @Test
    fun `a new cache is empty`() {
        assertNull(cache.get())
    }

    @Test
    fun `the cache keeps a condition for less than an hour`() {
        cache.put(RAIN)

        assertEquals(RAIN, cache.get())
        minutesPass(59)
        assertEquals(RAIN, cache.get())
        now += 59.seconds.inWholeMilliseconds
        assertEquals(RAIN, cache.get())
    }

    @Test
    fun `the cache forgets a condition after an hour`() {
        cache.put(RAIN)

        minutesPass(60)

        assertNull(cache.get())
    }

    @Test
    fun `the cache can be asked for a younger condition`() {
        cache.put(SNOW)
        minutesPass(45)

        assertEquals(SNOW, cache.get())
        assertNull(cache.get(maxAge = 30.minutes))
    }

    @Test
    fun `the newest condition replaces the old one and starts the hour again`() {
        cache.put(RAIN)
        minutesPass(50)
        cache.put(SNOW)
        minutesPass(50)

        assertEquals(SNOW, cache.get())
    }

    @Test
    fun `a condition from the future is not trusted`() {
        cache.put(RAIN)
        minutesPass(-10)

        assertNull(cache.get())
    }

    @Test
    fun `an OpenWeather id is remembered as its condition`() {
        cache.putOpenWeatherId(502)

        assertEquals(RAIN, cache.get())
    }

    @Test
    fun `an OpenWeather id that is not a condition forgets the old one`() {
        cache.put(RAIN)

        cache.putOpenWeatherId(900)

        assertNull(cache.get())
    }

    @Test
    fun `the cache can be cleared`() {
        cache.put(FOG)

        cache.clear()

        assertNull(cache.get())
    }

    // Provider

    @Test
    fun `a condition younger than an hour is reused without a request`() =
        runTest {
            cache.put(SNOW)
            minutesPass(59)

            assertEquals(SNOW, provider.currentCondition())
            assertEquals(0, fetchCalls)
        }

    @Test
    fun `a request is made when nothing is remembered`() =
        runTest {
            fetchResult = { 601 }

            assertEquals(SNOW, provider.currentCondition())
            assertEquals(1, fetchCalls)
        }

    @Test
    fun `a request is made when the remembered condition is an hour old`() =
        runTest {
            cache.put(SNOW)
            minutesPass(60)
            fetchResult = { 800 }

            assertEquals(CLEAR, provider.currentCondition())
            assertEquals(1, fetchCalls)
        }

    @Test
    fun `one request is enough for the next hour`() =
        runTest {
            fetchResult = { 211 }

            assertEquals(STORM, provider.currentCondition())
            minutesPass(30)
            fetchResult = { 800 }
            assertEquals(STORM, provider.currentCondition())
            assertEquals(1, fetchCalls)

            minutesPass(30)
            assertEquals(CLEAR, provider.currentCondition())
            assertEquals(2, fetchCalls)
        }

    @Test
    fun `a younger condition can be asked for`() =
        runTest {
            cache.put(RAIN)
            minutesPass(45)
            fetchResult = { 804 }

            assertEquals(CLOUDY, provider.currentCondition(maxAge = 30.minutes))
            assertEquals(1, fetchCalls)
        }

    @Test
    fun `the testing override wins over the real weather`() =
        runTest {
            override = FOG
            cache.put(RAIN)

            assertEquals(FOG, provider.currentCondition())
            assertEquals(0, fetchCalls)
        }

    @Test
    fun `the testing override works without a network or a location`() =
        runTest {
            override = SNOW
            fetchResult = { null }

            assertEquals(SNOW, provider.currentCondition())
            assertEquals(0, fetchCalls)
        }

    @Test
    fun `the testing override leaves the remembered weather alone`() =
        runTest {
            cache.put(RAIN)
            override = FOG

            provider.currentCondition()
            override = null

            assertEquals(RAIN, provider.currentCondition())
        }

    @Test
    fun `no answer means the weather is not known`() =
        runTest {
            fetchResult = { null }

            assertNull(provider.currentCondition())
            assertEquals(1, fetchCalls)
        }

    @Test
    fun `a failure means the weather is not known`() =
        runTest {
            fetchResult = { throw IOException("no network") }

            assertNull(provider.currentCondition())
        }

    @Test
    fun `an old condition is not used when the request fails`() =
        runTest {
            cache.put(RAIN)
            minutesPass(61)
            fetchResult = { null }

            assertNull(provider.currentCondition())
        }

    @Test
    fun `a failed request is not remembered`() =
        runTest {
            fetchResult = { null }
            provider.currentCondition()
            fetchResult = { 600 }

            assertEquals(SNOW, provider.currentCondition())
            assertEquals(2, fetchCalls)
        }

    @Test
    fun `an id that is not a condition means the weather is not known`() =
        runTest {
            fetchResult = { 900 }

            assertNull(provider.currentCondition())
            assertNull(cache.get())
        }

    @Test
    fun `cancelling is not treated as a failed request`() {
        fetchResult = { throw CancellationException("cancelled") }

        assertThrows(CancellationException::class.java) {
            runBlocking { provider.currentCondition() }
        }
    }

    // Request

    private class FakeOpenWeatherApi(
        private val currentWeather: suspend () -> Response<CurrentWeatherResponse>,
    ) : OpenWeatherApi {
        var currentWeatherCalls = 0
        var lastLat: Double? = null
        var lastLon: Double? = null
        var lastApiKey: String? = null

        override suspend fun getCurrentWeather(
            lat: Double,
            lon: Double,
            apiKey: String,
            units: String,
            language: String,
        ): Response<CurrentWeatherResponse> {
            currentWeatherCalls++
            lastLat = lat
            lastLon = lon
            lastApiKey = apiKey
            return currentWeather()
        }

        override suspend fun getForecast(
            lat: Double,
            lon: Double,
            apiKey: String,
            units: String,
            language: String,
        ): Response<FiveDayForecastResponse> = error("not used")

        override suspend fun getLocationByName(
            locationName: String,
            limit: Int,
            apiKey: String,
            language: String,
        ): Response<List<LocationResponse>> = error("not used")

        override suspend fun getLocationByCoordinates(
            lat: Double,
            lon: Double,
            limit: Int,
            apiKey: String,
            language: String,
        ): Response<List<LocationResponse>> = error("not used")
    }

    private fun weatherResponse(vararg ids: Int) =
        Response.success(
            CurrentWeatherResponse(
                weather = ids.map { Weather(id = it, main = "Main", description = "description", icon = "01d") },
                main = MainWeatherData(temp = 12.0, feelsLike = 11.0, tempMin = 10.0, tempMax = 14.0, pressure = 1010, humidity = 70),
                wind = Wind(speed = 5.0, deg = 90),
                name = "Dublin",
            ),
        )

    private fun fetcher(
        api: FakeOpenWeatherApi,
        apiKey: String = "key",
        latitude: Double? = 53.35,
        longitude: Double? = -6.26,
    ) = OpenWeatherConditionFetcher(
        apiKey = { apiKey },
        latitude = { latitude },
        longitude = { longitude },
        api = lazyOf(api),
    )

    @Test
    fun `the request asks for the saved location with the api key`() =
        runTest {
            val api = FakeOpenWeatherApi { weatherResponse(500) }

            assertEquals(500, fetcher(api).fetchConditionId())

            assertEquals(1, api.currentWeatherCalls)
            assertEquals(53.35, api.lastLat)
            assertEquals(-6.26, api.lastLon)
            assertEquals("key", api.lastApiKey)
        }

    @Test
    fun `the first condition of the answer is used`() =
        runTest {
            val api = FakeOpenWeatherApi { weatherResponse(701, 800) }

            assertEquals(701, fetcher(api).fetchConditionId())
        }

    @Test
    fun `an answer with no condition is not understood`() =
        runTest {
            val api = FakeOpenWeatherApi { weatherResponse() }

            assertNull(fetcher(api).fetchConditionId())
        }

    @Test
    fun `no request is made without an api key`() =
        runTest {
            val api = FakeOpenWeatherApi { weatherResponse(800) }

            assertNull(fetcher(api, apiKey = "").fetchConditionId())
            assertEquals(0, api.currentWeatherCalls)
        }

    @Test
    fun `no request is made without a location`() =
        runTest {
            val api = FakeOpenWeatherApi { weatherResponse(800) }

            assertNull(fetcher(api, latitude = null).fetchConditionId())
            assertNull(fetcher(api, longitude = null).fetchConditionId())
            assertNull(fetcher(api, latitude = null, longitude = null).fetchConditionId())
            assertEquals(0, api.currentWeatherCalls)
        }

    @Test
    fun `an error from the server is not an answer`() =
        runTest {
            val api = FakeOpenWeatherApi { Response.error(401, """{"cod":401}""".toResponseBody()) }

            assertNull(fetcher(api).fetchConditionId())
            assertEquals(1, api.currentWeatherCalls)
        }

    @Test
    fun `an empty answer is not an answer`() =
        runTest {
            val api = FakeOpenWeatherApi { Response.success(null) }

            assertNull(fetcher(api).fetchConditionId())
        }

    @Test
    fun `a failed request is not an answer`() =
        runTest {
            val api = FakeOpenWeatherApi { throw IOException("Unable to resolve host") }

            assertNull(fetcher(api).fetchConditionId())
        }

    @Test
    fun `a request that is too slow is given up on`() =
        runTest {
            val api =
                FakeOpenWeatherApi {
                    delay(60.seconds)
                    weatherResponse(800)
                }

            assertNull(fetcher(api).fetchConditionId())
            assertEquals(1, api.currentWeatherCalls)
        }

    @Test
    fun `a request that is quick enough is used`() =
        runTest {
            val api =
                FakeOpenWeatherApi {
                    delay(4.seconds)
                    weatherResponse(800)
                }

            assertEquals(800, fetcher(api).fetchConditionId())
        }

    @Test
    fun `the request is given five seconds`() {
        assertEquals(5.seconds, OpenWeatherConditionFetcher.REQUEST_TIMEOUT)
    }

    @Test
    fun `a request through the provider is remembered for next time`() =
        runTest {
            val api = FakeOpenWeatherApi { weatherResponse(741) }
            val real =
                WeatherConditionProvider(
                    cache = cache,
                    overrideSource = { null },
                    fetchConditionId = fetcher(api)::fetchConditionId,
                )

            assertEquals(FOG, real.currentCondition())
            assertEquals(FOG, real.currentCondition())
            assertEquals(1, api.currentWeatherCalls)
        }
}
