package com.motion.einklauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDrawerPolicyTest {
    @Test
    fun `a drawer page is four columns wide`() {
        assertEquals(4, AppDrawerPolicy.COLUMNS)
    }

    @Test
    fun `rows fill the available height using whole rows only`() {
        assertEquals(5, AppDrawerPolicy.rowsFor(availableHeightPx = 500, cellHeightPx = 100))
        assertEquals(6, AppDrawerPolicy.rowsFor(availableHeightPx = 600, cellHeightPx = 100))
        assertEquals(5, AppDrawerPolicy.rowsFor(availableHeightPx = 599, cellHeightPx = 100))
    }

    /** The property the whole screen depends on: a computed row count never overflows its space. */
    @Test
    fun `every row count fits inside the height it was given`() {
        val cellHeightPx = 97
        for (available in cellHeightPx..3000) {
            val rows = AppDrawerPolicy.rowsFor(available, cellHeightPx)
            assertTrue(
                "rows=$rows needs ${rows * cellHeightPx}px but only $available is free",
                rows * cellHeightPx <= available,
            )
        }
    }

    @Test
    fun `a short screen still shows one row`() {
        assertEquals(1, AppDrawerPolicy.rowsFor(availableHeightPx = 10, cellHeightPx = 100))
        assertEquals(1, AppDrawerPolicy.rowsFor(availableHeightPx = 0, cellHeightPx = 100))
        assertEquals(1, AppDrawerPolicy.rowsFor(availableHeightPx = -50, cellHeightPx = 100))
    }

    @Test
    fun `a tall screen stops at the row cap`() {
        assertEquals(AppDrawerPolicy.MAX_ROWS, AppDrawerPolicy.rowsFor(100_000, 10))
    }

    @Test
    fun `row calculation rejects a non-positive cell height`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            AppDrawerPolicy.rowsFor(availableHeightPx = 500, cellHeightPx = 0)
        }

        assertEquals("cellHeightPx must be positive", error.message)
    }

    @Test
    fun `rows split a page into equal grid rows`() {
        val grid = AppDrawerPolicy.toRows((1..24).toList())

        assertEquals(6, grid.size)
        assertTrue(grid.all { it.size == 4 })
        assertEquals(listOf(1, 2, 3, 4), grid.first())
        assertEquals(listOf(21, 22, 23, 24), grid.last())
    }

    @Test
    fun `the final row keeps whatever is left over`() {
        val grid = AppDrawerPolicy.toRows(listOf(1, 2, 3, 4, 5, 6))

        assertEquals(2, grid.size)
        assertEquals(listOf(1, 2, 3, 4), grid[0])
        assertEquals(listOf(5, 6), grid[1])
    }

    @Test
    fun `an empty page has no rows`() {
        assertTrue(AppDrawerPolicy.toRows(emptyList<Int>()).isEmpty())
    }

    @Test
    fun `row splitting rejects a non-positive column count`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            AppDrawerPolicy.toRows(listOf(1, 2), columns = 0)
        }

        assertEquals("columns must be positive", error.message)
    }
}