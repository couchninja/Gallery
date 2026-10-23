package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailXmpTagsUiTest {

    @Test
    fun keepTagOnly_showsClockNotTagIcon() {
        val result = thumbnailXmpTagIndicators(listOf("keep 2 months"))
        assertTrue(result.showKeep)
        assertFalse(result.showOtherTags)
    }

    @Test
    fun otherTagOnly_showsTagIconNotClock() {
        val result = thumbnailXmpTagIndicators(listOf("vacation"))
        assertFalse(result.showKeep)
        assertTrue(result.showOtherTags)
    }

    @Test
    fun mixedTags_showsBoth() {
        val result = thumbnailXmpTagIndicators(listOf("keep 2 months", "archive"))
        assertTrue(result.showKeep)
        assertTrue(result.showOtherTags)
    }

    @Test
    fun keepMatchIsCaseInsensitive() {
        val result = thumbnailXmpTagIndicators(listOf("KEEP forever"))
        assertEquals(true, result.showKeep)
    }

    @Test
    fun cardTagOnly_showsCardNotOtherTagIcon() {
        val result = thumbnailXmpTagIndicators(listOf("klantenkaart"))
        assertTrue(result.showCard)
        assertFalse(result.showKeep)
        assertFalse(result.showOtherTags)
    }

    @Test
    fun privateTagOnly_showsEyeNotOtherTagIcon() {
        val result = thumbnailXmpTagIndicators(listOf("private"))
        assertTrue(result.showPrivate)
        assertFalse(result.showCard)
        assertFalse(result.showKeep)
        assertFalse(result.showOtherTags)
    }

    @Test
    fun favoriteTagOnly_showsHeartNotOtherTagIcon() {
        val result = thumbnailXmpTagIndicators(listOf("favorite"))
        assertTrue(result.showFavorite)
        assertFalse(result.showCard)
        assertFalse(result.showPrivate)
        assertFalse(result.showKeep)
        assertFalse(result.showOtherTags)
    }

    @Test
    fun cardAndVacation_showsCardAndOtherNotKeep() {
        val result = thumbnailXmpTagIndicators(listOf("card", "vacation"))
        assertTrue(result.showCard)
        assertFalse(result.showKeep)
        assertTrue(result.showOtherTags)
    }
}
