package org.fossify.gallery.helpers

import org.fossify.gallery.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaMetadataTest {
    @Test
    fun encodeDecodeTags_roundTrip() {
        val tags = listOf("klantenkaart", "card", "vacation")
        val encoded = MediaMetadata.encodeTags(tags)
        assertEquals(tags, MediaMetadata.decodeTags(encoded))
    }

    @Test
    fun decodeTags_emptyString() {
        assertEquals(emptyList<String>(), MediaMetadata.decodeTags(""))
    }
}
