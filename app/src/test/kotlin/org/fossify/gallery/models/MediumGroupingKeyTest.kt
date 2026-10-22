package org.fossify.gallery.models

import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_DAILY
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MediumGroupingKeyTest {

    private fun calendarDayStart(ts: Long): String {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ENGLISH).apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis.toString()
    }

    private fun mediumModifiedAt(ts: Long) = Medium().apply { modified = ts }

    @Test
    fun dailyKeyMatchesCalendarDayStart() {
        val samples = listOf(
            1L,
            86_399_999L,
            86_400_000L,
            1_729_600_000_000L,
            1_729_641_599_999L,
            -1L,
            -86_400_001L,
            -2_208_988_800_000L,
            -12_219_292_800_000L,
            -12_219_292_800_001L,
        )
        samples.forEach { ts ->
            assertEquals(
                "ts=$ts",
                calendarDayStart(ts),
                mediumModifiedAt(ts).getGroupingKey(GROUP_BY_LAST_MODIFIED_DAILY),
            )
        }
    }
}
