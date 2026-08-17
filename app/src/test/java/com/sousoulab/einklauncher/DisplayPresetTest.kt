package com.sousoulab.einklauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayPresetTest {
    @Test
    fun `presets expose stable display specifications`() {
        assertEquals(listOf("compact", "comfortable", "large"), DisplayPreset.entries.map { it.storageValue })
        assertEquals(listOf(19, 23, 30), DisplayPreset.entries.map { it.homeTextSizeSp })
        assertEquals(listOf(48, 56, 68), DisplayPreset.entries.map { it.homeRowHeightDp })
        assertEquals(listOf(400, 500, 600), DisplayPreset.entries.map { it.fontWeight })
        assertEquals(DisplayPreset.COMFORTABLE, DisplayPreset.DEFAULT)
    }

    @Test
    fun `storage values round trip and unknown values are rejected`() {
        DisplayPreset.entries.forEach { preset ->
            assertEquals(preset, DisplayPreset.fromStorageValue(preset.storageValue))
        }
        assertNull(DisplayPreset.fromStorageValue(null))
        assertNull(DisplayPreset.fromStorageValue("unknown"))
    }

    @Test
    fun `legacy sizes migrate to the nearest preset`() {
        assertEquals(DisplayPreset.COMPACT, DisplayPreset.nearestToTextSize(Int.MIN_VALUE))
        assertEquals(DisplayPreset.COMPACT, DisplayPreset.nearestToTextSize(21))
        assertEquals(DisplayPreset.COMFORTABLE, DisplayPreset.nearestToTextSize(22))
        assertEquals(DisplayPreset.COMFORTABLE, DisplayPreset.nearestToTextSize(26))
        assertEquals(DisplayPreset.LARGE, DisplayPreset.nearestToTextSize(27))
        assertEquals(DisplayPreset.LARGE, DisplayPreset.nearestToTextSize(Int.MAX_VALUE))
    }

    @Test
    fun `stored preset takes precedence over legacy size`() {
        assertEquals(
            DisplayPreset.COMPACT,
            DisplayPreset.fromStorageOrLegacy("compact", legacyTextSizeSp = 50),
        )
        assertEquals(
            DisplayPreset.LARGE,
            DisplayPreset.fromStorageOrLegacy("unrecognized", legacyTextSizeSp = 50),
        )
    }

    @Test
    fun `preset navigation stops at either end`() {
        assertNull(DisplayPreset.COMPACT.smaller())
        assertEquals(DisplayPreset.COMPACT, DisplayPreset.COMFORTABLE.smaller())
        assertEquals(DisplayPreset.LARGE, DisplayPreset.COMFORTABLE.larger())
        assertNull(DisplayPreset.LARGE.larger())
    }
}
