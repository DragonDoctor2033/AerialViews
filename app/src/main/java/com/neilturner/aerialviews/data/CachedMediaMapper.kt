package com.neilturner.aerialviews.data

import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.enums.SceneType
import com.neilturner.aerialviews.models.enums.TimeOfDay
import com.neilturner.aerialviews.models.enums.WeatherCondition
import com.neilturner.aerialviews.models.videos.AerialExifMetadata
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata

/** Reads a row of the playlist cache back into the media item it was saved from. */
internal fun CachedMediaEntity.toAerialMedia(): AerialMedia =
    AerialMedia(
        uri = uri.toUri(),
        type = enumValueOf<AerialMediaType>(type),
        source = enumValueOf<AerialMediaSource>(source),
        metadata =
            AerialMediaMetadata(
                shortDescription = shortDescription,
                pointsOfInterest = pointsOfInterest,
                timeOfDay = enumValueOf<TimeOfDay>(timeOfDay),
                scene = enumValueOf<SceneType>(scene),
                albumName = albumName,
                title = title,
                // Rows saved before the weather tags existed have none
                weather = WeatherCondition.decode(weather),
                exif =
                    AerialExifMetadata(
                        date = exifDate,
                        offset = exifOffset,
                        latitude = exifLatitude,
                        longitude = exifLongitude,
                        city = exifCity,
                        state = exifState,
                        country = exifCountry,
                        description = exifDescription,
                    ),
            ),
        audioUri = audioUri?.toUri(),
    )

/** Builds the playlist cache row for a media item at position [playlistOrder] in the playlist. */
internal fun AerialMedia.toCachedEntity(playlistOrder: Int): CachedMediaEntity =
    CachedMediaEntity(
        playlistOrder = playlistOrder,
        uri = uri.toString(),
        type = type.name,
        source = source.name,
        shortDescription = metadata.shortDescription,
        pointsOfInterest = metadata.pointsOfInterest,
        timeOfDay = metadata.timeOfDay.name,
        scene = metadata.scene.name,
        albumName = metadata.albumName,
        title = metadata.title,
        exifDate = metadata.exif.date,
        exifOffset = metadata.exif.offset,
        exifLatitude = metadata.exif.latitude,
        exifLongitude = metadata.exif.longitude,
        exifCity = metadata.exif.city,
        exifState = metadata.exif.state,
        exifCountry = metadata.exif.country,
        exifDescription = metadata.exif.description,
        audioUri = audioUri?.toString(),
        weather = WeatherCondition.encode(metadata.weather),
    )
