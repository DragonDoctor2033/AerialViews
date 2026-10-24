package com.neilturner.aerialviews.utils

import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder

/**
 * Reads weather and time-of-day tags from folder names, file names and feed entries.
 *
 * Text is split into words on anything that is not a letter, and the words are compared without
 * regard to case. `Weather/rain/night/forest.mp4`, `beach_clear_day.mp4` and `Rainy-Day 2.mov` are
 * all understood. Only whole words count, so "rainbow" is not "rain".
 *
 * Everything here is a pure function of its input, so it can be tested without a device.
 */
object MediaTagParser {
    private val nonLetters = Regex("[^\\p{L}]+")
    private val urlScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

    private val weatherWords =
        mapOf(
            "clear" to WeatherCondition.CLEAR,
            "sunny" to WeatherCondition.CLEAR,
            "cloudy" to WeatherCondition.CLOUDY,
            "clouds" to WeatherCondition.CLOUDY,
            "overcast" to WeatherCondition.CLOUDY,
            "rain" to WeatherCondition.RAIN,
            "rainy" to WeatherCondition.RAIN,
            "drizzle" to WeatherCondition.RAIN,
            "snow" to WeatherCondition.SNOW,
            "snowy" to WeatherCondition.SNOW,
            "storm" to WeatherCondition.STORM,
            "thunder" to WeatherCondition.STORM,
            "thunderstorm" to WeatherCondition.STORM,
            "fog" to WeatherCondition.FOG,
            "foggy" to WeatherCondition.FOG,
            "mist" to WeatherCondition.FOG,
        )

    private val timeOfDayWords =
        mapOf(
            "day" to TimeOfDay.DAY,
            "night" to TimeOfDay.NIGHT,
            "sunrise" to TimeOfDay.SUNRISE,
            "sunset" to TimeOfDay.SUNSET,
        )

    /** What the location of a media file says about it. */
    data class PathTags(
        val weather: Set<WeatherCondition> = emptySet(),
        val timeOfDay: TimeOfDay = TimeOfDay.UNKNOWN,
    )

    /** The lower case words of [text], split on anything that is not a letter. */
    fun words(text: String): List<String> =
        text
            .split(nonLetters)
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }

    /** The weather named by the words of [text]. */
    fun weatherIn(text: String): Set<WeatherCondition> = words(text).mapNotNull { weatherWords[it] }.toSet()

    /**
     * Reads the tags of a file from where it is stored: a file path, or the URL of a file on a
     * server. Every folder name and the file name count towards the weather.
     *
     * The time of day comes from the name closest to the file, so a `sunset` file name wins over a
     * `day` folder. A name that mentions two different times of day, like `day_to_night.mp4`, says
     * nothing and leaves the time of day unknown.
     */
    fun parsePath(location: String?): PathTags {
        if (location.isNullOrBlank()) return PathTags()

        val segments = pathSegments(location)
        val weather = segments.flatMap { weatherIn(it) }.toSet()
        return PathTags(weather = weather, timeOfDay = timeOfDayOf(segments))
    }

    /**
     * Adds the tags found in [location] to [metadata]. The time of day is only filled in when the
     * provider did not already know it.
     */
    fun applyPathTags(
        metadata: AerialMediaMetadata,
        location: String?,
    ) {
        val tags = parsePath(location)
        if (tags.weather.isNotEmpty()) {
            metadata.weather = metadata.weather + tags.weather
        }
        if (metadata.timeOfDay == TimeOfDay.UNKNOWN) {
            metadata.timeOfDay = tags.timeOfDay
        }
    }

    /**
     * Reads the optional "weather" field of a custom feed entry: a string or an array of strings
     * using the same words as file names. Anything that is not understood is ignored, so a typo in
     * one entry can never stop a feed from loading.
     */
    fun weatherFromJson(element: JsonElement?): Set<WeatherCondition> =
        when (element) {
            null, is JsonNull, is JsonObject -> emptySet()
            is JsonPrimitive -> weatherIn(element.content)
            is JsonArray -> element.flatMap { weatherFromJson(it) }.toSet()
        }

    private fun timeOfDayOf(segments: List<String>): TimeOfDay {
        for (segment in segments.asReversed()) {
            val found = words(segment).mapNotNull { timeOfDayWords[it] }.toSet()
            when {
                found.size == 1 -> return found.first()
                found.size > 1 -> return TimeOfDay.UNKNOWN
            }
        }
        return TimeOfDay.UNKNOWN
    }

    /** Folder names followed by the file name. */
    private fun pathSegments(location: String): List<String> = pathOf(location).split('/', '\\').filter { it.isNotEmpty() }

    /**
     * The path part of a URL, without the scheme, user info, host, port, query or fragment. Only
     * where the file is stored can say anything about it, and credentials or a host name must never
     * be mistaken for a tag. Anything that is not a URL is already a path.
     */
    private fun pathOf(location: String): String {
        val scheme = urlScheme.find(location) ?: return location
        val rest = location.substring(scheme.range.last + 1).substringBefore('?').substringBefore('#')
        val path = rest.substringAfter('/', missingDelimiterValue = "")
        return percentDecode(path)
    }

    private fun percentDecode(value: String): String {
        if (!value.contains('%')) return value
        return try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: IllegalArgumentException) {
            value
        }
    }
}
