package org.fossify.gallery.helpers

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
}
