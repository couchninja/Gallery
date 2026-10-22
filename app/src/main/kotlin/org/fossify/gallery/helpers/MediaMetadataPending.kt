package org.fossify.gallery.helpers

import org.fossify.gallery.models.MediaMetadata

object MediaMetadataPending {
    fun needsJpegMetadataIndex(force: Boolean, existing: MediaMetadata?, lastModified: Long): Boolean {
        if (force) {
            return true
        }
        if (existing == null) {
            return true
        }
        return existing.lastModified != lastModified || existing.dateTakenSource == DATE_TAKEN_SOURCE_PENDING
    }

    fun needsCaptureDateIndex(
        force: Boolean,
        existing: MediaMetadata?,
        lastModified: Long,
        mediaStoreDateTaken: Long
    ): Boolean {
        if (isManualCaptureDateEntry(existing)) {
            return false
        }
        if (CaptureTimeReader.normalizeCaptureTimeMillis(mediaStoreDateTaken) != 0L) {
            return false
        }
        if (!force && existing != null && existing.lastModified == lastModified) {
            if (
                existing.dateTakenSource == DATE_TAKEN_SOURCE_NOT_FOUND ||
                CaptureTimeReader.isPlausibleCaptureTimeMillis(
                    CaptureTimeReader.normalizeCaptureTimeMillis(existing.dateTaken)
                )
            ) {
                return false
            }
        }
        return true
    }

    fun isManualCaptureDateEntry(entry: MediaMetadata?): Boolean {
        if (entry == null || entry.dateTakenSource != DATE_TAKEN_SOURCE_MANUAL) {
            return false
        }
        return CaptureTimeReader.isPlausibleCaptureTimeMillis(
            CaptureTimeReader.normalizeCaptureTimeMillis(entry.dateTaken)
        )
    }
}
