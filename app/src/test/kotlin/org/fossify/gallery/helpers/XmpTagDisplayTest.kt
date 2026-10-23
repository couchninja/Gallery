package org.fossify.gallery.helpers

import org.fossify.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XmpTagDisplayTest {

    @Test
    fun listIconRes_keepTagUsesClock() {
        assertEquals(R.drawable.ic_clock_vector, XmpTagDisplay.listIconResForTag("keep 2 months"))
    }

    @Test
    fun listIconRes_cardTagUsesCardIcon() {
        assertEquals(R.drawable.ic_cards_vector, XmpTagDisplay.listIconResForTag("Card"))
    }

    @Test
    fun listIconRes_privateTagUsesEyeIcon() {
        assertEquals(R.drawable.ic_xmp_tag_private_vector, XmpTagDisplay.listIconResForTag("Private"))
    }

    @Test
    fun listIconRes_favoriteTagUsesHeartIcon() {
        assertEquals(R.drawable.ic_xmp_tag_favorite_vector, XmpTagDisplay.listIconResForTag("Favorite"))
    }

    @Test
    fun listIconRes_genericTagUsesTagIcon() {
        assertEquals(R.drawable.ic_xmp_tags_vector, XmpTagDisplay.listIconResForTag("vacation"))
    }

    @Test
    fun mediumHasCardTag_detectsKlantenkaart() {
        assertTrue(XmpTagDisplay.mediumHasCardTag(listOf("klantenkaart", "other")))
        assertFalse(XmpTagDisplay.mediumHasCardTag(listOf("vacation")))
    }
}
