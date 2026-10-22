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
import org.fossify.gallery.extensions.dateTakensDB
import org.fossify.gallery.models.DateTaken
import java.util.concurrent.atomic.AtomicBoolean

data class DateTakenIndexProgress(
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

object DateTakenIndexer {
    private val isRunning = AtomicBoolean(false)
    private val forceRequested = AtomicBoolean(false)

    @Volatile
    private var progress = DateTakenIndexProgress()

    fun getProgress(): DateTakenIndexProgress = progress

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
        var current = DateTakenIndexProgress(isRunning = true, isFullRescan = force, finishedAt = progress.finishedAt)
        publishProgress(context, current)

        val cached = context.dateTakensDB.getAllDateTakens().associateBy { it.fullPath }
        val pending = collectPending(context, force, cached)
        current = current.copy(isListed = true, total = pending.size)
        publishProgress(context, current)

        for (batch in pending.chunked(BATCH_SIZE)) {
            val found = indexBatch(context, batch, cached)
            current = current.copy(processed = current.processed + batch.size, found = current.found + found)
            publishProgress(context, current)
        }
    }

    private fun collectPending(context: Context, force: Boolean, cached: Map<String, DateTaken>): List<PendingItem> {
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
                if (!path.isImageFast() && !path.isVideoFast()) {
                    return@queryCursor
                }

                val existing = findCachedDateTaken(cached, path)
                if (isManualCaptureDateEntry(existing)) {
                    return@queryCursor
                }

                val mediaStoreDateTaken = cursor.getLongValue(Images.Media.DATE_TAKEN)
                if (CaptureTimeReader.normalizeCaptureTimeMillis(mediaStoreDateTaken) != 0L) {
                    return@queryCursor
                }

                val mediaStoreId = cursor.getLongValue(Images.Media._ID)
                val dateModifiedMillis = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000L
                val lastModified = CaptureTimeReader.fileLastModifiedMillis(path).takeIf { it > 0L } ?: dateModifiedMillis
                if (!force && existing != null && existing.lastModified == lastModified) {
                    if (
                        existing.source == DATE_TAKEN_SOURCE_NOT_FOUND ||
                        CaptureTimeReader.isPlausibleCaptureTimeMillis(
                            CaptureTimeReader.normalizeCaptureTimeMillis(existing.taken)
                        )
                    ) {
                        return@queryCursor
                    }
                }

                val filename = cursor.getStringValue(Images.Media.DISPLAY_NAME)
                    ?: path.getFilenameFromPath()
                pending.add(
                    PendingItem(
                        path = path,
                        filename = filename,
                        lastModified = lastModified,
                        mediaStoreId = mediaStoreId,
                        dateModifiedMillis = dateModifiedMillis
                    )
                )
            } catch (_: Exception) {
            }
        }

        return pending
    }

    /** Returns how many files in [batch] had a capture date. */
    private fun indexBatch(context: Context, batch: List<PendingItem>, cached: Map<String, DateTaken>): Int {
        val dateTakens = ArrayList<DateTaken>()
        var improvedSortOrder = false
        for (item in batch) {
            val existing = findCachedDateTaken(cached, item.path)
            if (isManualCaptureDateEntry(existing)) {
                continue
            }

            val captureMillis = CaptureTimeReader.readCaptureTimeMillis(
                context = context,
                path = item.path,
                filename = item.filename,
                mediaStoreId = item.mediaStoreId
            )
            val resolved = captureMillis?.let { CaptureTimeReader.normalizeCaptureTimeMillis(it) } ?: 0L
            if (resolved != 0L) {
                val fallbackModified = item.dateModifiedMillis.takeIf { it > 0L } ?: item.lastModified
                if (resolved != fallbackModified) {
                    improvedSortOrder = true
                }
            }
            dateTakens.add(
                DateTaken(
                    null,
                    item.path,
                    item.filename,
                    item.path.getParentPath(),
                    resolved,
                    0,
                    item.lastModified,
                    if (resolved != 0L) DATE_TAKEN_SOURCE_INDEXED else DATE_TAKEN_SOURCE_NOT_FOUND
                )
            )
        }

        if (dateTakens.isNotEmpty()) {
            context.dateTakensDB.insertAll(dateTakens)
        }
        if (improvedSortOrder) {
            context.sendBroadcast(
                Intent(ACTION_DATE_TAKEN_INDEX_UPDATED).setPackage(context.packageName)
            )
        }
        return dateTakens.count { it.source == DATE_TAKEN_SOURCE_INDEXED }
    }

    private fun findCachedDateTaken(cached: Map<String, DateTaken>, path: String): DateTaken? {
        cached[path]?.let { return it }
        return cached.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value
    }

    private fun isManualCaptureDateEntry(entry: DateTaken?): Boolean {
        if (entry == null || entry.source != DATE_TAKEN_SOURCE_MANUAL) {
            return false
        }
        return CaptureTimeReader.isPlausibleCaptureTimeMillis(
            CaptureTimeReader.normalizeCaptureTimeMillis(entry.taken)
        )
    }

    private fun publishProgress(context: Context, newProgress: DateTakenIndexProgress) {
        progress = newProgress
        context.sendBroadcast(Intent(ACTION_DATE_TAKEN_INDEX_PROGRESS).setPackage(context.packageName))
    }

    private data class PendingItem(
        val path: String,
        val filename: String,
        val lastModified: Long,
        val mediaStoreId: Long,
        val dateModifiedMillis: Long
    )

    private const val BATCH_SIZE = 100
}
