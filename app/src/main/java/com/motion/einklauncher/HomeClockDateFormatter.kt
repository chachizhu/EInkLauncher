package com.motion.einklauncher

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formats the home date as zero-padded digits only, so every glyph is covered by
 * Inter and the date never mixes in a system CJK fallback font.
 */
internal object HomeClockDateFormatter {
    fun format(
        date: Date,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val formatter = SimpleDateFormat(PATTERN, locale)
        formatter.timeZone = timeZone
        return formatter.format(date)
    }

    private const val PATTERN = "yyyy-MM-dd"
}
