package com.neilturner.aerialviews.models.videos

import android.net.Uri
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.utils.MediaTagParser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
abstract class AbstractVideo {
    @SerialName("url-1080-H264")
    val video1080h264: String? = null

    @SerialName("url-1080-SDR")
    val video1080sdr: String? = null

    @SerialName("url-1080-HDR")
    val video1080hdr: String? = null

    @SerialName("url-4K-SDR")
    val video4ksdr: String? = null

    @SerialName("url-4K-HDR")
    val video4khdr: String? = null

    @SerialName("url-audio")
    val audioUrl: String? = null

    @SerialName("accessibilityLabel")
    val description: String = ""

    @SerialName("pointsOfInterest")
    val pointsOfInterest: Map<Int, String> = emptyMap()

    val timeOfDay: String = ""
    val scene: String = ""

    // Custom feeds only: a string or an array of strings, read leniently by weatherTags()
    val weather: JsonElement? = null

    fun audioUri(): Uri? = audioUrl?.takeIf { it.isNotBlank() }?.toUri()

    fun weatherTags(): Set<WeatherCondition> = MediaTagParser.weatherFromJson(weather)

    abstract fun uriAtQuality(quality: VideoQuality?): Uri

    abstract fun allUrls(): List<String>
}
