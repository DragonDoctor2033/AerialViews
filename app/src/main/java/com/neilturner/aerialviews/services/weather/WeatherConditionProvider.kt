package com.neilturner.aerialviews.services.weather

import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.data.network.JsonHelper
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.WeatherVideoPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Remembers the last known weather condition, so that the weather overlay and the playlist do not
 * each have to ask OpenWeather for it.
 */
class WeatherConditionCache(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(
        val condition: WeatherCondition,
        val recordedAtMillis: Long,
    )

    @Volatile
    private var entry: Entry? = null

    fun put(condition: WeatherCondition) {
        entry = Entry(condition, clock())
    }

    /**
     * Records the condition id of fresh current weather. An id that is not a condition the playlist
     * can match forgets the old condition, because it is no longer true.
     */
    fun putOpenWeatherId(id: Int) {
        val condition = WeatherCondition.fromOpenWeatherId(id)
        if (condition != null) put(condition) else clear()
    }

    fun clear() {
        entry = null
    }

    /**
     * The remembered condition if it is younger than [maxAge], otherwise null. The wall clock is
     * used on purpose, as time spent asleep counts, and an entry from the future is not trusted.
     */
    fun get(maxAge: Duration = DEFAULT_MAX_AGE): WeatherCondition? {
        val current = entry ?: return null
        val age = clock() - current.recordedAtMillis
        return current.condition.takeIf { age >= 0 && age < maxAge.inWholeMilliseconds }
    }

    companion object {
        val DEFAULT_MAX_AGE: Duration = 60.minutes

        /** Shared by the weather overlay and the playlist, as both run in the app process. */
        val shared = WeatherConditionCache()
    }
}

/**
 * Finds the weather condition outside, for matching the playlist to it.
 *
 * The testing override wins over the real weather. Otherwise a condition that is younger than an
 * hour is reused, and if there is none a single quick request is made. Every failure gives null,
 * which means the weather is not known and the playlist is not filtered.
 */
class WeatherConditionProvider(
    private val cache: WeatherConditionCache = WeatherConditionCache.shared,
    private val overrideSource: () -> WeatherCondition? = { WeatherVideoPrefs.overrideCondition },
    private val fetchConditionId: suspend () -> Int?,
) {
    suspend fun currentCondition(maxAge: Duration = WeatherConditionCache.DEFAULT_MAX_AGE): WeatherCondition? {
        overrideSource()?.let { override ->
            Timber.i("Weather: using the testing override $override")
            return override
        }

        cache.get(maxAge)?.let { cached ->
            Timber.i("Weather: using the remembered condition $cached")
            return cached
        }

        val id =
            try {
                fetchConditionId()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Weather: failed to look up the current condition")
                null
            } ?: return null

        val condition = WeatherCondition.fromOpenWeatherId(id)
        if (condition == null) {
            Timber.w("Weather: condition id $id is not one the playlist can match")
            cache.clear()
            return null
        }

        cache.put(condition)
        Timber.i("Weather: current condition is $condition (id $id)")
        return condition
    }

    companion object {
        /** A provider that uses the saved location and the OpenWeather API key of this build. */
        fun create(): WeatherConditionProvider =
            WeatherConditionProvider(fetchConditionId = OpenWeatherConditionFetcher()::fetchConditionId)
    }
}

/**
 * Makes one OpenWeather current weather request for the saved location and returns the id of the
 * first weather condition, or null when there is no API key or location, no network, or the answer
 * is not understood. Gives up after [timeout], as it holds up the start of the screensaver.
 */
internal class OpenWeatherConditionFetcher(
    private val apiKey: () -> String = { BuildConfig.OPEN_WEATHER },
    private val latitude: () -> Double? = { GeneralPrefs.weatherLocationLat.toDoubleOrNull() },
    private val longitude: () -> Double? = { GeneralPrefs.weatherLocationLon.toDoubleOrNull() },
    private val timeout: Duration = REQUEST_TIMEOUT,
    private val api: Lazy<OpenWeatherApi> = lazy { buildApi(timeout) },
    private val effectsFeed: WeatherEffectsFeed = WeatherEffectsFeed.shared,
) {
    suspend fun fetchConditionId(): Int? {
        val key = apiKey()
        if (key.isEmpty()) {
            Timber.w("Weather: no OpenWeather API key, the weather is not known")
            return null
        }

        val lat = latitude()
        val lon = longitude()
        if (lat == null || lon == null) {
            Timber.w("Weather: no location set, the weather is not known")
            return null
        }

        return try {
            withTimeoutOrNull(timeout) {
                val response = api.value.getCurrentWeather(lat = lat, lon = lon, apiKey = key)
                if (response.isSuccessful) {
                    val body = response.body()
                    // The weather effects get this look too (in metric units, the default of the request),
                    // so they follow the weather as often as the playlist does, and know it at the start
                    body?.let { WeatherSnapshot.from(it, units = "metric") }?.let { effectsFeed.publish(it) }
                    body
                        ?.weather
                        ?.firstOrNull()
                        ?.id
                } else {
                    Timber.w("Weather: current weather request failed with HTTP ${response.code()}")
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Weather: current weather request failed")
            null
        }
    }

    companion object {
        val REQUEST_TIMEOUT: Duration = 5.seconds

        private fun buildApi(timeout: Duration): OpenWeatherApi {
            val millis = timeout.inWholeMilliseconds
            val client =
                OkHttpClient
                    .Builder()
                    .connectTimeout(millis, TimeUnit.MILLISECONDS)
                    .readTimeout(millis, TimeUnit.MILLISECONDS)
                    .callTimeout(millis, TimeUnit.MILLISECONDS)
                    .build()

            return Retrofit
                .Builder()
                .baseUrl("https://api.openweathermap.org/")
                .client(client)
                .addConverterFactory(JsonHelper.buildSerializer())
                .build()
                .create(OpenWeatherApi::class.java)
        }
    }
}
