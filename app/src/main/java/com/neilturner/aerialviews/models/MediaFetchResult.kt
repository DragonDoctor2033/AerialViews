package com.neilturner.aerialviews.models

import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.music.MusicPlaylist

data class MediaFetchResult(
    val mediaPlaylist: MediaPlaylist,
    val musicPlaylist: MusicPlaylist?,
    val musicResumeIndex: Int = 0,
    val isFromCache: Boolean = false,
    // The weather the playlist was matched to, or null when it is not matched to the weather or the
    // weather was not known. The playlist is only right for this weather
    val weatherCondition: WeatherCondition? = null,
)

enum class LoadingStatus {
    LOADING,
    BUILDING,
    RESUMING,
}
