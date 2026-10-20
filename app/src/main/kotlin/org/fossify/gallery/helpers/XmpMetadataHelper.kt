package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.xmpMetadataDB
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.XmpMetadataEntry
import java.io.File
import java.util.Locale

fun String.isJpegPath(): Boolean {
    val lower = lowercase(Locale.getDefault())
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
}

fun Medium.isJpeg(): Boolean = path.isJpegPath()

fun readXmpMetadataFromPath(path: String): XmpMetadata {
    if (!path.isJpegPath()) {
        return XmpMetadata.EMPTY
    }
    return readXmpMetadataFromJpegFile(path)
}

fun writeExifRatingToPath(path: String, rating: Int): Boolean {
    return writeXmpRatingToJpegFile(path, rating)
}

fun lookupExifRating(ratings: Map<String, Int>, path: String): Int {
    ratings[path]?.let { return it }
    return ratings.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value ?: 0
}

fun syncExifRatingOnGalleryMedia(path: String, rating: Int) {
    org.fossify.gallery.activities.MediaActivity.mMedia.filterIsInstance<Medium>().firstOrNull {
        it.path.equals(path, ignoreCase = true)
    }?.exifRating = rating.coerceIn(0, 5)
    org.fossify.gallery.activities.MediaActivity.pendingExifRatingRefreshPaths.add(path)
}

fun Context.mergeExifRatingsIntoMediaAsync(
    media: List<Medium>,
    folderPath: String,
    treatAsShowAll: Boolean,
    onMerged: (changedPaths: List<String>) -> Unit
) {
    if (!config.showExifRatings || media.isEmpty()) {
        return
    }

    ensureBackgroundThread {
        val fetcher = MediaFetcher(applicationContext)
        val ratings = if (treatAsShowAll || folderPath.isEmpty() || folderPath == FAVORITES) {
            fetcher.getExifRatings()
        } else {
            fetcher.getFolderExifRatings(folderPath)
        }
        if (ratings.isEmpty()) {
            return@ensureBackgroundThread
        }

        val changedPaths = applyExifRatingsToMedia(media, ratings)
        if (changedPaths.isNotEmpty()) {
            onMerged(changedPaths)
        }
    }
}

fun applyExifRatingsToMedia(media: List<Medium>, exifRatings: HashMap<String, Int>): List<String> {
    if (exifRatings.isEmpty()) {
        return emptyList()
    }

    val mediaByPath = HashMap<String, Medium>(media.size.coerceAtLeast(exifRatings.size))
    media.forEach { medium ->
        mediaByPath[medium.path.lowercase()] = medium
    }

    val changedPaths = ArrayList<String>()
    exifRatings.forEach { (path, rating) ->
        val medium = mediaByPath[path.lowercase()] ?: return@forEach
        if (medium.exifRating != rating) {
            medium.exifRating = rating
            changedPaths.add(medium.path)
        }
    }
    return changedPaths
}

fun Context.cacheXmpMetadata(path: String, metadata: XmpMetadata, lastModified: Long? = null) {
    val modified = lastModified?.takeIf { it > 0L }
        ?: CaptureTimeReader.fileLastModifiedMillis(path).takeIf { it > 0L }
        ?: File(path).lastModified()
    val entry = XmpMetadataEntry(
        id = null,
        fullPath = path,
        filename = path.getFilenameFromPath(),
        parentPath = path.getParentPath(),
        rating = metadata.rating.coerceIn(0, 5),
        tags = XmpMetadataEntry.encodeTags(metadata.tags),
        lastModified = modified
    )
    xmpMetadataDB.insertAll(listOf(entry))
    XmpTagIndex.update(path, metadata.tags)
}

/** Cached metadata when the file is unchanged since it was indexed, otherwise read from disk and re-cached. */
fun Context.loadXmpMetadata(path: String): XmpMetadata {
    if (!path.isJpegPath()) {
        return XmpMetadata.EMPTY
    }
    val lastModified = File(path).lastModified()
    val cached = try {
        xmpMetadataDB.getByPath(path)
    } catch (_: Exception) {
        null
    }
    if (cached != null && lastModified > 0L && cached.lastModified == lastModified) {
        return XmpMetadata(cached.rating.coerceIn(0, 5), cached.tagList())
    }
    val metadata = readXmpMetadataFromJpegFile(path)
    cacheXmpMetadata(path, metadata, lastModified)
    return metadata
}

fun Context.setExifRating(path: String, rating: Int): Boolean {
    if (!path.isJpegPath()) {
        return false
    }
    if (!writeXmpRatingToJpegFile(path, rating)) {
        return false
    }
    cacheXmpMetadata(path, readXmpMetadataFromJpegFile(path), File(path).lastModified())
    return true
}
