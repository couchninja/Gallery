package org.fossify.gallery.helpers

import android.content.Context
import android.content.Intent
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.queryCursor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.extensions.exifRatingsDB
import org.fossify.gallery.models.ExifRating
import java.util.concurrent.atomic.AtomicBoolean

object ExifRatingIndexer {
    private val isRunning = AtomicBoolean(false)

    fun schedule(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        ensureBackgroundThread {
            if (!isRunning.compareAndSet(false, true)) {
                return@ensureBackgroundThread
            }
            try {
                runIndex(appContext, force)
            } finally {
                isRunning.set(false)
            }
        }
    }

    private fun runIndex(context: Context, force: Boolean) {
        val cached = context.exifRatingsDB.getAllRatings().associateBy { it.fullPath }
        val pending = collectPending(context, force, cached)
        if (pending.isEmpty()) {
            return
        }

        var updated = false
        for (batch in pending.chunked(BATCH_SIZE)) {
            if (indexBatch(context, batch)) {
                updated = true
            }
        }

        if (updated) {
            context.sendBroadcast(
                Intent(ACTION_EXIF_RATING_INDEX_UPDATED).setPackage(context.packageName)
            )
        }
    }

    private fun collectPending(context: Context, force: Boolean, cached: Map<String, ExifRating>): List<PendingItem> {
        val pending = ArrayList<PendingItem>()
        val projection = arrayOf(
            Images.Media.DATA,
            Images.Media.DISPLAY_NAME,
            Images.Media.DATE_MODIFIED
        )
        val uri = Files.getContentUri("external")

        context.queryCursor(uri, projection) { cursor ->
            try {
                val path = cursor.getStringValue(Images.Media.DATA)?.takeIf { it.isNotEmpty() } ?: return@queryCursor
                if (!path.isJpegPath()) {
                    return@queryCursor
                }

                val dateModifiedMillis = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000L
                val lastModified = CaptureTimeReader.fileLastModifiedMillis(path).takeIf { it > 0L } ?: dateModifiedMillis
                val existing = findCached(cached, path)
                if (!force && existing != null && existing.lastModified == lastModified && existing.rating > 0) {
                    return@queryCursor
                }

                val filename = cursor.getStringValue(Images.Media.DISPLAY_NAME) ?: path.getFilenameFromPath()
                pending.add(PendingItem(path, filename, lastModified))
            } catch (_: Exception) {
            }
        }

        return pending
    }

    private fun indexBatch(context: Context, batch: List<PendingItem>): Boolean {
        val ratings = ArrayList<ExifRating>()
        for (item in batch) {
            val rating = readExifRatingFromPath(item.path)
            ratings.add(
                ExifRating(
                    null,
                    item.path,
                    item.filename,
                    item.path.getParentPath(),
                    rating,
                    item.lastModified
                )
            )
        }

        if (ratings.isEmpty()) {
            return false
        }
        context.exifRatingsDB.insertAll(ratings)
        return true
    }

    private fun findCached(cached: Map<String, ExifRating>, path: String): ExifRating? {
        cached[path]?.let { return it }
        return cached.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value
    }

    private data class PendingItem(
        val path: String,
        val filename: String,
        val lastModified: Long
    )

    private const val BATCH_SIZE = 100
}
