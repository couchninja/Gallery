package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.gallery.extensions.exifRatingsDB
import org.fossify.gallery.models.ExifRating
import org.fossify.gallery.models.Medium
import java.io.File
import java.util.Locale

fun String.isJpegPath(): Boolean {
    val lower = lowercase(Locale.getDefault())
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
}

fun Medium.isJpeg(): Boolean = path.isJpegPath()

fun readExifRatingFromPath(path: String): Int {
    if (!path.isJpegPath()) {
        return 0
    }
    return readXmpRatingFromJpegFile(path)
}

fun writeExifRatingToPath(path: String, rating: Int): Boolean {
    return writeXmpRatingToJpegFile(path, rating)
}

fun lookupExifRating(ratings: Map<String, Int>, path: String): Int {
    ratings[path]?.let { return it }
    return ratings.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value ?: 0
}

fun applyExifRatingsToMedia(media: List<Medium>, exifRatings: HashMap<String, Int>) {
    if (exifRatings.isEmpty()) {
        return
    }
    media.forEach { medium ->
        medium.exifRating = lookupExifRating(exifRatings, medium.path)
    }
}

fun Context.cacheExifRating(path: String, rating: Int, lastModified: Long? = null) {
    val modified = lastModified?.takeIf { it > 0L }
        ?: CaptureTimeReader.fileLastModifiedMillis(path).takeIf { it > 0L }
        ?: File(path).lastModified()
    val entry = ExifRating(
        id = null,
        fullPath = path,
        filename = path.getFilenameFromPath(),
        parentPath = path.getParentPath(),
        rating = rating.coerceIn(0, 5),
        lastModified = modified
    )
    exifRatingsDB.insertAll(listOf(entry))
}

fun Context.setExifRating(path: String, rating: Int): Boolean {
    if (!path.isJpegPath()) {
        return false
    }
    if (!writeXmpRatingToJpegFile(path, rating)) {
        return false
    }
    cacheExifRating(path, rating, File(path).lastModified())
    return true
}
