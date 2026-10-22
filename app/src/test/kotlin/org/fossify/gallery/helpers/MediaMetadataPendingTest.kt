package org.fossify.gallery.helpers

import org.fossify.gallery.models.MediaMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMetadataPendingTest {
    @Test
    fun needsJpegMetadataIndex_whenMtimeDiffers() {
        val existing = sampleRow(lastModified = 100L)
        assertTrue(MediaMetadataPending.needsJpegMetadataIndex(false, existing, 200L))
    }

    @Test
    fun needsJpegMetadataIndex_whenUpToDate() {
        val existing = sampleRow(lastModified = 100L)
        assertFalse(MediaMetadataPending.needsJpegMetadataIndex(false, existing, 100L))
    }

    @Test
    fun needsJpegMetadataIndex_whenDatePending() {
        val existing = sampleRow(lastModified = 100L).copy(dateTakenSource = DATE_TAKEN_SOURCE_PENDING)
        assertTrue(MediaMetadataPending.needsJpegMetadataIndex(false, existing, 100L))
    }

    @Test
    fun needsCaptureDateIndex_keepsManualDate() {
        val existing = sampleRow(lastModified = 100L).copy(
            dateTaken = 1_600_000_000_000L,
            dateTakenSource = DATE_TAKEN_SOURCE_MANUAL
        )
        assertFalse(MediaMetadataPending.needsCaptureDateIndex(true, existing, 200L, 0L))
    }

    @Test
    fun needsCaptureDateIndex_skipsWhenMediaStoreHasDate() {
        assertFalse(
            MediaMetadataPending.needsCaptureDateIndex(
                force = false,
                existing = null,
                lastModified = 100L,
                mediaStoreDateTaken = 1_600_000_000_000L
            )
        )
    }

    @Test
    fun needsCaptureDateIndex_whenNoCacheAndNoMediaStoreDate() {
        assertTrue(
            MediaMetadataPending.needsCaptureDateIndex(
                force = false,
                existing = null,
                lastModified = 100L,
                mediaStoreDateTaken = 0L
            )
        )
    }

    private fun sampleRow(lastModified: Long) = MediaMetadata(
        id = 1,
        fullPath = "/photos/a.jpg",
        filename = "a.jpg",
        parentPath = "/photos",
        lastModified = lastModified,
        dateTaken = 0L,
        dateTakenSource = DATE_TAKEN_SOURCE_NOT_FOUND,
        lastFixed = 0,
        rating = 0,
        tags = ""
    )
}
