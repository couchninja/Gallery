package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleCachedMediaTest {

    private fun medium(path: String) = Medium().apply {
        this.path = path
        name = path.substringAfterLast('/')
        parentPath = path.substringBeforeLast('/', missingDelimiterValue = "")
    }

    @Test
    fun mediaMissingOnDisk_returnsOnlyPathsWithMissingFiles() {
        val kept = "/storage/emulated/0/Pictures/kept.jpg"
        val gone = "/storage/emulated/0/Pictures/gone.jpg"
        val media = listOf(medium(kept), medium(gone))

        val missing = mediaMissingOnDisk(media) { it != gone }

        assertEquals(listOf(gone), missing.map { it.path })
    }

    @Test
    fun mediaMissingOnDisk_emptyWhenAllFilesExist() {
        val paths = listOf("/a/1.jpg", "/a/2.jpg")
        val media = paths.map { medium(it) }

        assertTrue(mediaMissingOnDisk(media) { true }.isEmpty())
    }

    @Test
    fun shouldRemoveCachedMediaForPath_trueWhenFileMissing() {
        assertTrue(shouldRemoveCachedMediaForPath("/missing.jpg") { false })
    }

    @Test
    fun shouldRemoveCachedMediaForPath_falseWhenFilePresent() {
        assertFalse(shouldRemoveCachedMediaForPath("/present.jpg") { true })
    }
}
