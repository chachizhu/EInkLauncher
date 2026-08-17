package com.sousoulab.einklauncher

import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeClockTimeFormatterTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `midnight uses two digit 24 hour time`() {
        assertEquals("00:05", formatUtc(hour = 0, minute = 5, second = 43))
    }

    @Test
    fun `afternoon does not use AM or PM`() {
        val formatted = formatUtc(hour = 13, minute = 7, second = 51)

        assertEquals("13:07", formatted)
        assertFalse(formatted.contains("AM", ignoreCase = true))
        assertFalse(formatted.contains("PM", ignoreCase = true))
    }

    @Test
    fun `end of day omits seconds`() {
        val formatted = formatUtc(hour = 23, minute = 59, second = 58)

        assertEquals("23:59", formatted)
        assertEquals(5, formatted.length)
        assertEquals(1, formatted.count { it == ':' })
    }

    private fun formatUtc(hour: Int, minute: Int, second: Int): String {
        val milliseconds = (
            hour * 60L * 60L +
                minute * 60L +
                second
            ) * 1_000L
        return HomeClockTimeFormatter.format(
            date = Date(milliseconds),
            locale = Locale.US,
            timeZone = utc,
        )
    }
}
