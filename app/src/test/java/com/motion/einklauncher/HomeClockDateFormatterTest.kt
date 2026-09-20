package com.motion.einklauncher

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeClockDateFormatterTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `single digit month and day are zero padded`() {
        assertEquals("2026-01-05", formatUtc(month = Calendar.JANUARY, day = 5))
    }

    @Test
    fun `double digit month and day keep both digits`() {
        assertEquals("2026-09-21", formatUtc(month = Calendar.SEPTEMBER, day = 21))
    }

    @Test
    fun `year uses four digits`() {
        val formatted = formatUtc(month = Calendar.DECEMBER, day = 31)

        assertEquals("2026-12-31", formatted)
        assertEquals(10, formatted.length)
    }

    @Test
    fun `output is digits and separator only so Inter covers every glyph`() {
        val formatted = formatUtc(month = Calendar.DECEMBER, day = 31)

        assertFalse(formatted.any { it.isLetter() })
    }

    private fun formatUtc(month: Int, day: Int): String {
        val calendar = Calendar.getInstance(utc, Locale.US)
        calendar.clear()
        calendar.set(2026, month, day, 12, 0, 0)
        return HomeClockDateFormatter.format(
            date = Date(calendar.timeInMillis),
            locale = Locale.US,
            timeZone = utc,
        )
    }
}
