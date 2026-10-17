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
    val isRunning: Boolean,
    val processed: Int,
    val total: Int
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

    @Volatile
    private var progress = DateTakenIndexProgress(isRunning = false, processed = 0, total = 0)

    @Volatile
    private var hasCompletedRun = false

    fun getProgress(): DateTakenIndexProgress = progress

    fun hasCompletedRun(): Boolean = hasCompletedRun

    fun schedule(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        ensureBackgroundThread {
            if (!isRunning.compareAndSet(false, true)) {
                return@ensureBackgroundThread
            }

            try {
                runIndex(appContext, force)
            } finally {
                hasCompletedRun = true
                isRunning.set(false)
                publishProgress(appContext, progress.copy(isRunning = false))
            }
        }
    }

    private fun runIndex(context: Context, force: Boolean) {
        val cached = context.dateTakensDB.getAllDateTakens().associateBy { it.fullPath }
        val pending = collectPending(context, force, cached)
        val total = pending.size
        publishProgress(context, DateTakenIndexProgress(isRunning = true, processed = 0, total = total))

        if (pending.isEmpty()) {
            return
        }

        var processed = 0
        for (batch in pending.chunked(BATCH_SIZE)) {
            indexBatch(context, batch, cached)
            processed += batch.size
            publishProgress(context, DateTakenIndexProgress(isRunning = true, processed = processed, total = total))
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
                if (CaptureTimeReader.normalizeCaptureTimeMillis(mediaStoreDateTaken) > 0L) {
                    return@queryCursor
                }

                val mediaStoreId = cursor.getLongValue(Images.Media._ID)
                val dateModifiedMillis = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000L
                val lastModified = CaptureTimeReader.fileLastModifiedMillis(path).takeIf { it > 0L } ?: dateModifiedMillis
                if (!force) {
                    if (
                        existing != null &&
                        existing.lastModified == lastModified &&
                        CaptureTimeReader.isPlausibleCaptureTimeMillis(
                            CaptureTimeReader.normalizeCaptureTimeMillis(existing.taken)
                        ) &&
                        !looksLikeModifiedFallback(existing.taken, dateModifiedMillis, lastModified)
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

    private fun indexBatch(context: Context, batch: List<PendingItem>, cached: Map<String, DateTaken>) {
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
            if (resolved <= 0L) {
                continue
            }

            val fallbackModified = item.dateModifiedMillis.takeIf { it > 0L } ?: item.lastModified
            if (resolved != fallbackModified) {
                improvedSortOrder = true
            }
            dateTakens.add(
                DateTaken(
                    null,
                    item.path,
                    item.filename,
                    item.path.getParentPath(),
                    resolved,
                    AUTO_DATE_TAKEN_INDEX_LAST_FIXED,
                    item.lastModified
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
    }

    private fun findCachedDateTaken(cached: Map<String, DateTaken>, path: String): DateTaken? {
        cached[path]?.let { return it }
        return cached.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value
    }

    private fun looksLikeModifiedFallback(taken: Long, dateModifiedMillis: Long, fileLastModified: Long): Boolean {
        val normalizedTaken = CaptureTimeReader.normalizeCaptureTimeMillis(taken)
        if (normalizedTaken <= 0L) {
            return false
        }
        val modified = dateModifiedMillis.takeIf { it > 0L } ?: fileLastModified
        if (modified <= 0L) {
            return false
        }
        return kotlin.math.abs(normalizedTaken - modified) < 86_400_000L
    }

    private fun isManualCaptureDateEntry(entry: DateTaken?): Boolean {
        if (entry == null) {
            return false
        }
        if (entry.lastFixed == AUTO_DATE_TAKEN_INDEX_LAST_FIXED) {
            return false
        }
        return CaptureTimeReader.isPlausibleCaptureTimeMillis(
            CaptureTimeReader.normalizeCaptureTimeMillis(entry.taken)
        )
    }

    private fun publishProgress(context: Context, newProgress: DateTakenIndexProgress) {
        progress = newProgress
        context.sendBroadcast(
            Intent(ACTION_DATE_TAKEN_INDEX_PROGRESS).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_INDEX_RUNNING, newProgress.isRunning)
                putExtra(EXTRA_INDEX_PROCESSED, newProgress.processed)
                putExtra(EXTRA_INDEX_TOTAL, newProgress.total)
            }
        )
    }

    private data class PendingItem(
        val path: String,
        val filename: String,
        val lastModified: Long,
        val mediaStoreId: Long,
        val dateModifiedMillis: Long
    )

    private const val BATCH_SIZE = 100

    const val EXTRA_INDEX_RUNNING = "running"
    const val EXTRA_INDEX_PROCESSED = "processed"
    const val EXTRA_INDEX_TOTAL = "total"
}
