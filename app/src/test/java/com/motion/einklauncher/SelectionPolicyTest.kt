package com.motion.einklauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionPolicyTest {
    @Test
    fun `add appends a new item without changing the source list`() {
        val source = listOf("Reader", "Files")

        val result = SelectionPolicy.add(source, "Notes")

        assertEquals(listOf("Reader", "Files", "Notes"), result)
        assertEquals(listOf("Reader", "Files"), source)
    }

    @Test
    fun `add ignores a duplicate item`() {
        val source = listOf("Reader", "Files")

        assertEquals(source, SelectionPolicy.add(source, "Reader"))
    }

    @Test
    fun `add enforces the twelve app limit`() {
        val source = (1..SelectionPolicy.MAX_SELECTED_APPS).toList()

        assertEquals(12, SelectionPolicy.MAX_SELECTED_APPS)
        assertEquals(source, SelectionPolicy.add(source, 13))
    }

    @Test
    fun `remove drops the requested item and preserves order`() {
        val source = listOf("Reader", "Files", "Notes")

        val result = SelectionPolicy.remove(source, "Files")

        assertEquals(listOf("Reader", "Notes"), result)
        assertEquals(listOf("Reader", "Files", "Notes"), source)
    }

    @Test
    fun `move up swaps only with the preceding item`() {
        val source = listOf("Reader", "Files", "Notes")

        assertEquals(
            listOf("Reader", "Notes", "Files"),
            SelectionPolicy.moveUp(source, "Notes"),
        )
        assertEquals(source, SelectionPolicy.moveUp(source, "Reader"))
        assertEquals(source, SelectionPolicy.moveUp(source, "Missing"))
    }

    @Test
    fun `move down swaps only with the following item`() {
        val source = listOf("Reader", "Files", "Notes")

        assertEquals(
            listOf("Files", "Reader", "Notes"),
            SelectionPolicy.moveDown(source, "Reader"),
        )
        assertEquals(source, SelectionPolicy.moveDown(source, "Notes"))
        assertEquals(source, SelectionPolicy.moveDown(source, "Missing"))
    }

    @Test
    fun `page partitions items and reports navigation state`() {
        val source = (1..10).toList()

        val first = SelectionPolicy.page(source, requestedPage = 0, pageSize = 4)
        val middle = SelectionPolicy.page(source, requestedPage = 1, pageSize = 4)
        val last = SelectionPolicy.page(source, requestedPage = 2, pageSize = 4)

        assertEquals(listOf(1, 2, 3, 4), first.items)
        assertEquals(0, first.pageIndex)
        assertEquals(3, first.pageCount)
        assertFalse(first.hasPrevious)
        assertTrue(first.hasNext)

        assertEquals(listOf(5, 6, 7, 8), middle.items)
        assertTrue(middle.hasPrevious)
        assertTrue(middle.hasNext)

        assertEquals(listOf(9, 10), last.items)
        assertTrue(last.hasPrevious)
        assertFalse(last.hasNext)
    }

    @Test
    fun `page clamps requests to the available range`() {
        val source = listOf("A", "B", "C")

        val beforeFirst = SelectionPolicy.page(source, requestedPage = -10, pageSize = 2)
        val afterLast = SelectionPolicy.page(source, requestedPage = 10, pageSize = 2)

        assertEquals(0, beforeFirst.pageIndex)
        assertEquals(listOf("A", "B"), beforeFirst.items)
        assertEquals(1, afterLast.pageIndex)
        assertEquals(listOf("C"), afterLast.items)
    }

    @Test
    fun `empty collection still exposes one non-navigable page`() {
        val result = SelectionPolicy.page(emptyList<String>(), requestedPage = 4, pageSize = 6)

        assertTrue(result.items.isEmpty())
        assertEquals(0, result.pageIndex)
        assertEquals(1, result.pageCount)
        assertFalse(result.hasPrevious)
        assertFalse(result.hasNext)
    }

    @Test
    fun `page rejects non-positive page size`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            SelectionPolicy.page(listOf("Reader"), requestedPage = 0, pageSize = 0)
        }

        assertEquals("pageSize must be positive", error.message)
    }
}
