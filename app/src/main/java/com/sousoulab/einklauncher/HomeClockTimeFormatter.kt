package com.sousoulab.einklauncher

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Formats the home clock as an unambiguous 24-hour hour-and-minute value. */
internal object HomeClockTimeFormatter {
    fun format(
        date: Date,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val formatter = SimpleDateFormat(PATTERN, locale)
        formatter.timeZone = timeZone
        return formatter.format(date)
    }

    private const val PATTERN = "HH:mm"
}
