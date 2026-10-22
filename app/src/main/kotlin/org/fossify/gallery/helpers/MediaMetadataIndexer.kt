package org.fossify.gallery.helpers

import android.content.Context
import android.content.Intent
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.queryCursor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.extensions.mediaMetadataDB
import org.fossify.gallery.models.MediaMetadata
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class MetadataIndexProgress(
    val isRunning: Boolean = false,
    val isFullRescan: Boolean = false,
    /** False while the run is still listing files, before [total] is known. */
    val isListed: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    /** When the last run finished, 0 if none has finished since the app started. */
    val finishedAt: Long = 0L
) {
    val percent: Int
        get() = if (total <= 0) {
            if (isRunning) 0 else 100
        } else {
            (processed.coerceAtMost(total) * 100 / total).coerceIn(0, 100)
        }
}

/** Indexes capture date, XMP rating, and XMP tags into `media_metadata` in one MediaStore pass. */
object MediaMetadataIndexer {
    private val isRunning = AtomicBoolean(false)
    private val forceRequested = AtomicBoolean(false)

    @Volatile
    private var progress = MetadataIndexProgress()

    fun getProgress(): MetadataIndexProgress = progress

    fun isFullRescanQueued(): Boolean = isRunning.get() && forceRequested.get()

    /** Non-forced requests made while a run is active are dropped; forced ones run again once it finishes. */
    fun schedule(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        if (force) {
            forceRequested.set(true)
            publishProgress(appContext, progress)
        }
        ensureBackgroundThread {
            if (!isRunning.compareAndSet(false, true)) {
                return@ensureBackgroundThread
            }

            try {
                do {
                    runIndex(appContext, forceRequested.getAndSet(false))
                } while (forceRequested.get())
            } finally {
                isRunning.set(false)
                publishProgress(appContext, progress.copy(isRunning = false, finishedAt = System.currentTimeMillis()))
            }
            if (forceRequested.get()) {
                schedule(appContext)
            }
        }
    }

    private fun runIndex(context: Context, force: Boolean) {
        var current = MetadataIndexProgress(isRunning = true, isFullRescan = force, finishedAt = progress.finishedAt)
        publishProgress(context, current)

        val cached = CachedRows(context.mediaMetadataDB.getAll())
        XmpTagIndex.replaceAll(cached.all)

        val pending = collectPending(context, force, cached)
        current = current.copy(isListed = true, total = pending.size)
        publishProgress(context, current)

        for (batch in pending.chunked(BATCH_SIZE)) {
            val result = indexBatch(context, batch)
            current = current.copy(processed = current.processed + batch.size, found = current.found + result.foundDates)
            publishProgress(context, current)
            if (result.datesChanged || result.xmpChanged) {
                notifyIndexUpdated(context, result.datesChanged, result.xmpChanged)
            }
        }

        if (pruneMetadataForMissingFiles(context, cached.all)) {
            XmpTagIndex.replaceAll(context.mediaMetadataDB.getAll())
            notifyIndexUpdated(context, datesChanged = false, xmpChanged = true)
        }
    }

    /** Drops cached rows whose files are gone (e.g. after move/delete), so tag lists stay accurate. */
    private fun pruneMetadataForMissingFiles(context: Context, entries: List<MediaMetadata>): Boolean {
        var pruned = false
        for (entry in entries) {
            if (File(entry.fullPath).exists()) {
                continue
            }
            try {
                context.mediaMetadataDB.deletePath(entry.fullPath)
                pruned = true
            } catch (_: Exception) {
            }
        }
        return pruned
    }

    private fun collectPending(context: Context, force: Boolean, cached: CachedRows): List<PendingItem> {
        val pending = ArrayList<PendingItem>()
        val projection = arrayOf(
            Images.Media._ID,
            Images.Media.DATA,
            Images.Media.DISPLAY_NAME,
            Images.Media.DATE_TAKEN,
            Images.Media.DATE_MODIFIED
        )
        val uri = Files.getContentUri("external")

        context.queryCursor(uri, projection) { cursor ->
            try {
                val path = cursor.getStringValue(Images.Media.DATA)?.takeIf { it.isNotEmpty() } ?: return@queryCursor
                val isJpeg = path.isJpegPath()
                if (!isJpeg && !path.isImageFast() && !path.isVideoFast()) {
                    return@queryCursor
                }

                val mediaStoreDateTaken = cursor.getLongValue(Images.Media.DATE_TAKEN)
                val existing = cached.find(path)
                if (!isJpeg && (MediaMetadataPending.isManualCaptureDateEntry(existing) ||
                        CaptureTimeReader.normalizeCaptureTimeMillis(mediaStoreDateTaken) != 0L)
                ) {
                    return@queryCursor
                }

                val dateModifiedMillis = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000L
                val lastModified = CaptureTimeReader.fileLastModifiedMillis(path).takeIf { it > 0L } ?: dateModifiedMillis
                val needsIndex = if (isJpeg) {
                    MediaMetadataPending.needsJpegMetadataIndex(force, existing, lastModified)
                } else {
                    MediaMetadataPending.needsCaptureDateIndex(force, existing, lastModified, mediaStoreDateTaken)
                }
                if (!needsIndex) {
                    return@queryCursor
                }

                pending.add(
                    PendingItem(
                        path = path,
                        filename = cursor.getStringValue(Images.Media.DISPLAY_NAME) ?: path.getFilenameFromPath(),
                        lastModified = lastModified,
                        mediaStoreId = cursor.getLongValue(Images.Media._ID),
                        mediaStoreDateTaken = mediaStoreDateTaken,
                        dateModifiedMillis = dateModifiedMillis,
                        isJpeg = isJpeg,
                        existing = existing
                    )
                )
            } catch (_: Exception) {
            }
        }

        return pending
    }

    private fun indexBatch(context: Context, batch: List<PendingItem>): BatchResult {
        val entries = ArrayList<MediaMetadata>(batch.size)
        var datesChanged = false
        var xmpChanged = false
        var foundDates = 0

        for (item in batch) {
            val existing = item.existing
            val keepManualDate = MediaMetadataPending.isManualCaptureDateEntry(existing)
            val xmp = if (item.isJpeg) readXmpMetadataFromPath(item.path) else null

            val dateTaken: Long
            val dateTakenSource: Int
            val lastFixed: Int
            if (keepManualDate) {
                dateTaken = existing!!.dateTaken
                dateTakenSource = existing.dateTakenSource
                lastFixed = existing.lastFixed
            } else {
                val resolved = CaptureTimeReader.readCaptureTimeMillis(
                    context = context,
                    path = item.path,
                    filename = item.filename,
                    mediaStoreId = item.mediaStoreId
                )?.let { CaptureTimeReader.normalizeCaptureTimeMillis(it) } ?: 0L
                if (resolved != 0L) {
                    foundDates++
                    if (resolved != previousSortDate(item)) {
                        datesChanged = true
                    }
                }
                dateTaken = resolved
                dateTakenSource = if (resolved != 0L) DATE_TAKEN_SOURCE_INDEXED else DATE_TAKEN_SOURCE_NOT_FOUND
                lastFixed = 0
            }

            // A rating or tag write that landed while this batch was reading would be overwritten; the next run picks it up.
            if (CaptureTimeReader.fileLastModifiedMillis(item.path).let { it > 0L && it != item.lastModified }) {
                continue
            }

            val rating = xmp?.rating?.coerceIn(0, 5) ?: existing?.rating ?: 0
            val tags = xmp?.let { MediaMetadata.encodeTags(it.tags) } ?: existing?.tags ?: ""
            if (xmp != null && (rating != (existing?.rating ?: 0) || tags != (existing?.tags ?: ""))) {
                xmpChanged = true
            }

            entries.add(
                MediaMetadata(
                    id = null,
                    fullPath = item.path,
                    filename = item.filename,
                    parentPath = item.path.getParentPath(),
                    lastModified = item.lastModified,
                    dateTaken = dateTaken,
                    dateTakenSource = dateTakenSource,
                    lastFixed = lastFixed,
                    rating = rating,
                    tags = tags
                )
            )
        }

        if (entries.isEmpty()) {
            return BatchResult(datesChanged = false, xmpChanged = false, foundDates = foundDates)
        }
        context.mediaMetadataDB.insertAll(entries)
        entries.forEach { XmpTagIndex.update(it.fullPath, it.tagList()) }
        return BatchResult(datesChanged, xmpChanged, foundDates)
    }

    /** The capture time grids sorted by before this read: cached date, then MediaStore, then file mtime. */
    private fun previousSortDate(item: PendingItem): Long {
        item.existing?.let { CaptureTimeReader.normalizeCaptureTimeMillis(it.dateTaken) }?.takeIf { it != 0L }?.let { return it }
        CaptureTimeReader.normalizeCaptureTimeMillis(item.mediaStoreDateTaken).takeIf { it != 0L }?.let { return it }
        return item.dateModifiedMillis.takeIf { it > 0L } ?: item.lastModified
    }

    private fun notifyIndexUpdated(context: Context, datesChanged: Boolean, xmpChanged: Boolean) {
        context.sendBroadcast(
            Intent(ACTION_MEDIA_METADATA_INDEX_UPDATED).setPackage(context.packageName)
                .putExtra(EXTRA_METADATA_DATES_CHANGED, datesChanged)
                .putExtra(EXTRA_METADATA_XMP_CHANGED, xmpChanged)
        )
    }

    private fun publishProgress(context: Context, newProgress: MetadataIndexProgress) {
        progress = newProgress
        context.sendBroadcast(Intent(ACTION_MEDIA_METADATA_INDEX_PROGRESS).setPackage(context.packageName))
    }

    private class CachedRows(val all: List<MediaMetadata>) {
        private val byPath = all.associateBy { it.fullPath }
        private val byLowercasePath = all.associateBy { it.fullPath.lowercase() }

        fun find(path: String): MediaMetadata? = byPath[path] ?: byLowercasePath[path.lowercase()]
    }

    private class PendingItem(
        val path: String,
        val filename: String,
        val lastModified: Long,
        val mediaStoreId: Long,
        val mediaStoreDateTaken: Long,
        val dateModifiedMillis: Long,
        val isJpeg: Boolean,
        val existing: MediaMetadata?
    )

    private data class BatchResult(val datesChanged: Boolean, val xmpChanged: Boolean, val foundDates: Int)

    private const val BATCH_SIZE = 100
}
