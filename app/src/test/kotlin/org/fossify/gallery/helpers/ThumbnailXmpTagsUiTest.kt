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
}
