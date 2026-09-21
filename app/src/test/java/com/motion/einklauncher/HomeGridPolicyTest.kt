package com.motion.einklauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGridPolicyTest {
    @Test
    fun `defaults describe a single column of eight rows`() {
        assertEquals(8, HomeGridPolicy.DEFAULT_ROWS)
        assertEquals(1, HomeGridPolicy.DEFAULT_COLUMNS)
        assertEquals(8, HomeGridPolicy.capacity(HomeGridPolicy.DEFAULT_ROWS, HomeGridPolicy.DEFAULT_COLUMNS))
    }

    @Test
    fun `bounds allow four to sixteen rows and one to two columns`() {
        assertEquals(4, HomeGridPolicy.MIN_ROWS)
        assertEquals(16, HomeGridPolicy.MAX_ROWS)
        assertEquals(1, HomeGridPolicy.MIN_COLUMNS)
        assertEquals(2, HomeGridPolicy.MAX_COLUMNS)
    }

    @Test
    fun `normalize clamps out of range values into the supported bounds`() {
        assertEquals(4, HomeGridPolicy.normalizeRows(0))
        assertEquals(4, HomeGridPolicy.normalizeRows(4))
        assertEquals(8, HomeGridPolicy.normalizeRows(8))
        assertEquals(9, HomeGridPolicy.normalizeRows(9))
        assertEquals(10, HomeGridPolicy.normalizeRows(10))
        assertEquals(16, HomeGridPolicy.normalizeRows(16))
        assertEquals(16, HomeGridPolicy.normalizeRows(99))

        assertEquals(1, HomeGridPolicy.normalizeColumns(-3))
        assertEquals(1, HomeGridPolicy.normalizeColumns(1))
        assertEquals(2, HomeGridPolicy.normalizeColumns(2))
        assertEquals(2, HomeGridPolicy.normalizeColumns(7))
    }

    @Test
    fun `capacity multiplies rows by columns after normalization`() {
        assertEquals(8, HomeGridPolicy.capacity(8, 1))
        assertEquals(16, HomeGridPolicy.capacity(8, 2))
        assertEquals(18, HomeGridPolicy.capacity(9, 2))
        assertEquals(20, HomeGridPolicy.capacity(10, 2))
        assertEquals(32, HomeGridPolicy.capacity(16, 2))
        assertEquals(16, HomeGridPolicy.capacity(99, 0))
    }

    @Test
    fun `content width cap scales with columns and includes gutters`() {
        assertEquals(
            420,
            HomeGridPolicy.contentMaxWidthDp(1, columnWidthDp = 420, columnSpacingDp = 8),
        )
        assertEquals(
            848,
            HomeGridPolicy.contentMaxWidthDp(2, columnWidthDp = 420, columnSpacingDp = 8),
        )
    }

    @Test
    fun `content width cap normalizes out of range column counts`() {
        assertEquals(
            420,
            HomeGridPolicy.contentMaxWidthDp(0, columnWidthDp = 420, columnSpacingDp = 8),
        )
        assertEquals(
            848,
            HomeGridPolicy.contentMaxWidthDp(9, columnWidthDp = 420, columnSpacingDp = 8),
        )
    }
}
