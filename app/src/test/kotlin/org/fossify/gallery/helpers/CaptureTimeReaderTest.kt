package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureTimeReaderTest {

    private val year1886 = -2_627_168_389_000L // 1886-10-01T23:00:11Z
    private val year2015 = 1_428_344_960_000L

    @Test
    fun normalizeCaptureTimeMillis_keepsPre1970Millis() {
        assertEquals(year1886, CaptureTimeReader.normalizeCaptureTimeMillis(year1886))
    }

    @Test
    fun normalizeCaptureTimeMillis_rejectsNearEpochAndAncientJunk() {
        assertEquals(0L, CaptureTimeReader.normalizeCaptureTimeMillis(0L))
        assertEquals(0L, CaptureTimeReader.normalizeCaptureTimeMillis(-1L))
        assertEquals(0L, CaptureTimeReader.normalizeCaptureTimeMillis(Long.MIN_VALUE))
    }

    @Test
    fun normalizeCaptureTimeMillis_rejectsQuickTimeEpoch() {
        assertEquals(0L, CaptureTimeReader.normalizeCaptureTimeMillis(-2_082_844_800_000L))
        assertEquals(0L, CaptureTimeReader.normalizeCaptureTimeMillis(-2_082_844_800_000L - 3_600_000L))
        assertEquals(year2015, CaptureTimeReader.resolveCaptureTimeMillis(-2_082_844_800_000L, year2015))
    }

    @Test
    fun resolveCaptureTimeMillis_prefersPre1970TakenOverModified() {
        assertEquals(year1886, CaptureTimeReader.resolveCaptureTimeMillis(year1886, year2015))
        assertEquals(year2015, CaptureTimeReader.resolveCaptureTimeMillis(0L, year2015))
    }
}
