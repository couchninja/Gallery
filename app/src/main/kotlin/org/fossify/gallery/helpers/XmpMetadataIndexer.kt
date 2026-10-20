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
import org.fossify.gallery.extensions.xmpMetadataDB
import org.fossify.gallery.models.XmpMetadataEntry
import java.util.concurrent.atomic.AtomicBoolean

object XmpMetadataIndexer {
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
        val all = context.xmpMetadataDB.getAll()
        XmpTagIndex.replaceAll(all)
        val cached = all.associateBy { it.fullPath }
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
                Intent(ACTION_XMP_METADATA_INDEX_UPDATED).setPackage(context.packageName)
            )
        }
    }

    private fun collectPending(context: Context, force: Boolean, cached: Map<String, XmpMetadataEntry>): List<PendingItem> {
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
                if (!force && existing != null && existing.lastModified == lastModified) {
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
        val entries = ArrayList<XmpMetadataEntry>()
        for (item in batch) {
            val metadata = readXmpMetadataFromPath(item.path)
            entries.add(
                XmpMetadataEntry(
                    null,
                    item.path,
                    item.filename,
                    item.path.getParentPath(),
                    metadata.rating,
                    XmpMetadataEntry.encodeTags(metadata.tags),
                    item.lastModified
                )
            )
        }

        if (entries.isEmpty()) {
            return false
        }
        context.xmpMetadataDB.insertAll(entries)
        entries.forEach { XmpTagIndex.update(it.fullPath, it.tagList()) }
        return true
    }

    private fun findCached(cached: Map<String, XmpMetadataEntry>, path: String): XmpMetadataEntry? {
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
