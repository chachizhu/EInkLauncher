package com.sousoulab.einklauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeClockModeTest {
    @Test
    fun `date and time is the default`() {
        assertEquals(HomeClockMode.DATE_AND_TIME, HomeClockMode.DEFAULT)
    }

    @Test
    fun `storage values round trip independently from enum names`() {
        assertEquals("time_only", HomeClockMode.TIME_ONLY.storageValue)
        assertEquals("date_and_time", HomeClockMode.DATE_AND_TIME.storageValue)

        HomeClockMode.entries.forEach { mode ->
            assertEquals(mode, HomeClockMode.fromStorageValue(mode.storageValue))
        }
    }

    @Test
    fun `missing and unknown storage values are not recognized`() {
        assertNull(HomeClockMode.fromStorageValue(null))
        assertNull(HomeClockMode.fromStorageValue(""))
        assertNull(HomeClockMode.fromStorageValue("future_mode"))
    }
}
