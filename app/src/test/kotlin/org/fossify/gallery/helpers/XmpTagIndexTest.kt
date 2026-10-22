package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XmpTagIndexTest {

    @Before
    fun resetIndex() {
        XmpTagIndex.replaceAll(emptyList())
    }

    @Test
    fun clearingTagsRemovesTagNameWhenPathCasingDiffers() {
        XmpTagIndex.update("/storage/photo.jpg", listOf("tst"))
        XmpTagIndex.update("/Storage/photo.jpg", emptyList())

        assertFalse(XmpTagIndex.getAllTagNamesSorted().contains("tst"))
        assertTrue(XmpTagIndex.getPathsWithTag("tst").isEmpty())
    }

    @Test
    fun getTagsMatchesPathIgnoringCase() {
        XmpTagIndex.update("/storage/Photo.jpg", listOf("private"))

        assertEquals(listOf("private"), XmpTagIndex.getTags("/storage/Photo.jpg"))
        assertEquals(listOf("private"), XmpTagIndex.getTags("/STORAGE/photo.JPG"))
        assertTrue(XmpTagIndex.getTags("/storage/other.jpg").isEmpty())
    }

    @Test
    fun updateWithDifferentCasingKeepsSingleEntry() {
        XmpTagIndex.update("/storage/photo.jpg", listOf("a"))
        XmpTagIndex.update("/Storage/Photo.jpg", listOf("b"))

        assertEquals(listOf("b"), XmpTagIndex.getTags("/storage/photo.jpg"))
        assertTrue(XmpTagIndex.getPathsWithTag("a").isEmpty())
        assertEquals(setOf("/Storage/Photo.jpg"), XmpTagIndex.getPathsWithTag("b"))
    }
}
