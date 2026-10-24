package com.neilturner.aerialviews.providers.ncmemories

import com.neilturner.aerialviews.data.storage.FileHelper
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.prefs.NCMemoriesImagePrefs
import com.neilturner.aerialviews.models.videos.AerialExifMetadata
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import com.neilturner.aerialviews.utils.MediaTagParser
import timber.log.Timber
import kotlin.time.Instant

class NCMemoriesImageMapper(
    private val prefs: NCMemoriesImagePrefs,
    private val urlBuilder: NCMemoriesUrlBuilder,
) {
    data class ProcessResults(
        val media: List<AerialMedia>,
        val excluded: Int,
        val videos: Int,
        val photos: Int,
    )

    fun filterImagesByMediaType(images: List<Image>): List<Image> =
        images.filter { image ->
            val filename = image.baseName
            when {
                FileHelper.isSupportedVideoType(filename) -> prefs.includeVideos
                FileHelper.isSupportedImageType(filename) -> prefs.includePhotos
                else -> false // Exclude unsupported files
            }
        }

    fun processImages(images: List<Image>): ProcessResults {
        val media = mutableListOf<AerialMedia>()
        var excluded = 0
        var videosCount = 0
        var photosCount = 0

        images.forEach { image ->
            val filename = image.baseName
            val isVideo = FileHelper.isSupportedVideoType(filename)
            val isImage = FileHelper.isSupportedImageType(filename)

            if (isVideo || isImage) {
                val rawExif = image.exif
                val exif = extractExifMetadata(image)

                val uri = urlBuilder.getImageUri(image.fileId, isVideo, image.etag)
                Timber.i(
                    "Immich EXIF: basename=%s album=%s",
                    filename,
                    image.albumName.orEmpty(),
                )
                val item =
                    AerialMedia(
                        uri,
                        metadata =
                            AerialMediaMetadata(
                                exif = exif,
                                albumName = image.albumName.orEmpty(),
                                title = rawExif?.title.orEmpty(),
                                shortDescription = image.fileName.orEmpty(),
                            ),
                    ).apply {
                        source = AerialMediaSource.NCMEMORIES
                        type = if (isVideo) AerialMediaType.VIDEO else AerialMediaType.IMAGE
                        // The uri only holds a file id, the folder and file names are in the file's path
                        MediaTagParser.applyPathTags(metadata, image.fileName ?: filename)
                    }

                if (isVideo) {
                    videosCount++
                    if (prefs.includeVideos) {
                        media.add(item)
                    }
                } else {
                    photosCount++
                    if (prefs.includePhotos) {
                        media.add(item)
                    }
                }
            } else {
                excluded++
            }
        }

        return ProcessResults(
            media = media,
            excluded = excluded,
            videos = videosCount,
            photos = photosCount,
        )
    }

    private fun extractExifMetadata(image: Image): AerialExifMetadata {
        val exifInfo = image.exif
        val imageEpoch = image.epoch ?: exifInfo?.dateTimeEpoch

        return AerialExifMetadata(
            date = imageEpoch?.let { Instant.fromEpochSeconds(it) }.toString(),
            offset = exifInfo?.offsetTimeOriginal,
            latitude = exifInfo?.gpsLatitude?.toDoubleOrNull(),
            longitude = exifInfo?.gpsLongitude?.toDoubleOrNull(),
            description = exifInfo?.description,
        )
    }
}
