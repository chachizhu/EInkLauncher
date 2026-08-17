package com.sousoulab.einklauncher

/** Stable choices for the date and time content shown in the home status area. */
internal enum class HomeClockMode(val storageValue: String) {
    TIME_ONLY("time_only"),
    DATE_AND_TIME("date_and_time"),
    ;

    companion object {
        val DEFAULT: HomeClockMode = DATE_AND_TIME

        fun fromStorageValue(value: String?): HomeClockMode? =
            entries.firstOrNull { it.storageValue == value }
    }
}
