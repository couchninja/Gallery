package org.fossify.gallery.helpers

import android.content.Context
import android.provider.MediaStore
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.queryCursor
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.updateDirectoryPath
import org.fossify.gallery.models.Medium
import org.fossify.gallery.extensions.config

fun Context.syncMediaStoreItemsWithIdGreaterThan(lastMediaStoreId: Long) {
    if (lastMediaStoreId <= 0L) {
        return
    }

    val filterMedia = config.filterMedia
    if (filterMedia == 0) {
        return
    }

    val showHidden = config.shouldShowHidden
    val favoritePaths = getFavoritePaths()
    val projection = arrayOf(
        Images.Media._ID,
        Images.Media.DISPLAY_NAME,
        Images.Media.DATA,
        Images.Media.DATE_MODIFIED,
        Images.Media.DATE_TAKEN,
        Images.Media.SIZE,
        MediaStore.MediaColumns.DURATION,
    )
    val uri = Files.getContentUri("external")
    val selection = "${Images.Media._ID} > ?"
    val selectionArgs = arrayOf(lastMediaStoreId.toString())
    val toInsert = ArrayList<Medium>()
    val affectedFolders = HashSet<String>()

    queryCursor(uri, projection, selection, selectionArgs) { cursor ->
        try {
            val mediaStoreId = cursor.getLongValue(Images.Media._ID)
            val filename = cursor.getStringValue(Images.Media.DISPLAY_NAME) ?: return@queryCursor
            val path = cursor.getStringValue(Images.Media.DATA)?.takeIf { it.isNotEmpty() } ?: return@queryCursor

            val isImage = path.isImageFast()
            val isVideo = if (isImage) false else path.isVideoFast()
            val isGif = if (isImage || isVideo) false else path.isGif()
            val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
            val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()

            if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) {
                return@queryCursor
            }

            if (isVideo && filterMedia and TYPE_VIDEOS == 0) {
                return@queryCursor
            }

            if (isImage && filterMedia and TYPE_IMAGES == 0) {
                return@queryCursor
            }

            if (isGif && filterMedia and TYPE_GIFS == 0) {
                return@queryCursor
            }

            if (isRaw && filterMedia and TYPE_RAWS == 0) {
                return@queryCursor
            }

            if (isSvg && filterMedia and TYPE_SVGS == 0) {
                return@queryCursor
            }

            if (!showHidden && filename.startsWith('.')) {
                return@queryCursor
            }

            val size = cursor.getLongValue(Images.Media.SIZE)
            if (size <= 0L) {
                return@queryCursor
            }

            val type = when {
                isVideo -> TYPE_VIDEOS
                isGif -> TYPE_GIFS
                isRaw -> TYPE_RAWS
                isSvg -> TYPE_SVGS
                else -> TYPE_IMAGES
            }

            val lastModified = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000L
            val mediaStoreDateTaken = cursor.getLongValue(Images.Media.DATE_TAKEN)
            val dateTaken = CaptureTimeReader.resolveCaptureTimeMillis(mediaStoreDateTaken, lastModified)
            val videoDuration = kotlin.math.round(
                cursor.getIntValue(MediaStore.MediaColumns.DURATION) / 1000.0
            ).toInt()
            val isFavorite = favoritePaths.contains(path)
            val parentPath = path.getParentPath()

            toInsert.add(
                Medium(
                    id = null,
                    name = filename,
                    path = path,
                    parentPath = parentPath,
                    modified = lastModified,
                    taken = dateTaken,
                    size = size,
                    type = type,
                    videoDuration = videoDuration,
                    isFavorite = isFavorite,
                    deletedTS = 0L,
                    mediaStoreId = mediaStoreId,
                )
            )
            affectedFolders.add(parentPath)
        } catch (_: Exception) {
        }
    }

    if (toInsert.isEmpty()) {
        return
    }

    try {
        mediaDB.insertAll(toInsert)
    } catch (_: Exception) {
        return
    }

    affectedFolders.forEach { folder ->
        try {
            updateDirectoryPath(folder)
        } catch (_: Exception) {
        }
    }
}
