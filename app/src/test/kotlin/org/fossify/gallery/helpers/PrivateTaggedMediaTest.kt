package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrivateTaggedMediaTest {

    @Before
    fun reset() {
        XmpTagIndex.replaceAll(emptyList())
    }

    @Test
    fun privateTagIsCaseInsensitive() {
        XmpTagIndex.update("/photos/a.jpg", listOf("Private"))
        assertTrue(PrivateTaggedMedia.hasPrivateTag("/photos/a.jpg"))
    }

    @Test
    fun filterHidesPrivateUnlessHiddenMediaShown() {
        XmpTagIndex.update("/photos/private.jpg", listOf("private"))
        val items = listOf(
            mediumAt("/photos/public.jpg"),
            mediumAt("/photos/private.jpg"),
        )
        assertEquals(1, PrivateTaggedMedia.filter(items, showPrivateTagged = false).size)
        assertEquals(2, PrivateTaggedMedia.filter(items, showPrivateTagged = true).size)
    }

    @Test
    fun otherTagsAreNotTreatedAsPrivate() {
        XmpTagIndex.update("/photos/a.jpg", listOf("vacation"))
        assertFalse(PrivateTaggedMedia.hasPrivateTag("/photos/a.jpg"))
    }

    private fun mediumAt(path: String) = Medium(
        id = 1L,
        name = path.substringAfterLast('/'),
        path = path,
        parentPath = path.substringBeforeLast('/'),
        modified = 0L,
        taken = 0L,
        size = 0L,
        type = TYPE_IMAGES,
        videoDuration = 0,
        isFavorite = false,
        deletedTS = 0L,
        mediaStoreId = 0L,
    )
}
