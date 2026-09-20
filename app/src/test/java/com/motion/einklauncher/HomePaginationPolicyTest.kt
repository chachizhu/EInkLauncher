package com.motion.einklauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class HomePaginationPolicyTest {
    @Test
    fun `all items share one page when they fit`() {
        val result = HomePaginationPolicy.resolve(
            itemCount = 5,
            availableHeightPx = 500,
            rowHeightPx = 60,
            requestedPage = 0,
        )

        assertEquals(5, result.pageSize)
        assertEquals(1, result.pageCount)
        assertEquals(0, result.clampedPage)
    }

    @Test
    fun `available height determines page size and partial final page`() {
        val result = HomePaginationPolicy.resolve(
            itemCount = 8,
            availableHeightPx = 200,
            rowHeightPx = 52,
            requestedPage = 1,
        )

        assertEquals(3, result.pageSize)
        assertEquals(3, result.pageCount)
        assertEquals(1, result.clampedPage)
    }

    @Test
    fun `exact page boundaries do not add an empty page`() {
        val result = HomePaginationPolicy.resolve(
            itemCount = 8,
            availableHeightPx = 208,
            rowHeightPx = 52,
            requestedPage = 1,
        )

        assertEquals(4, result.pageSize)
        assertEquals(2, result.pageCount)
        assertEquals(1, result.clampedPage)
    }

    @Test
    fun `requested page is clamped at both boundaries`() {
        val beforeFirst = HomePaginationPolicy.resolve(
            itemCount = 8,
            availableHeightPx = 104,
            rowHeightPx = 52,
            requestedPage = Int.MIN_VALUE,
        )
        val afterLast = HomePaginationPolicy.resolve(
            itemCount = 8,
            availableHeightPx = 104,
            rowHeightPx = 52,
            requestedPage = Int.MAX_VALUE,
        )

        assertEquals(0, beforeFirst.clampedPage)
        assertEquals(3, afterLast.clampedPage)
    }

    @Test
    fun `less than one row of height still exposes one item per page`() {
        val result = HomePaginationPolicy.resolve(
            itemCount = 3,
            availableHeightPx = 10,
            rowHeightPx = 52,
            requestedPage = 2,
        )

        assertEquals(1, result.pageSize)
        assertEquals(3, result.pageCount)
        assertEquals(2, result.clampedPage)
    }

    @Test
    fun `non-positive dimensions safely fall back to one item per page`() {
        val zeroHeight = HomePaginationPolicy.resolve(
            itemCount = 3,
            availableHeightPx = 0,
            rowHeightPx = 52,
            requestedPage = 0,
        )
        val invalidRowHeight = HomePaginationPolicy.resolve(
            itemCount = 3,
            availableHeightPx = 500,
            rowHeightPx = Int.MIN_VALUE,
            requestedPage = 0,
        )

        assertEquals(HomePagination(pageSize = 1, pageCount = 3, clampedPage = 0), zeroHeight)
        assertEquals(HomePagination(pageSize = 1, pageCount = 3, clampedPage = 0), invalidRowHeight)
    }

    @Test
    fun `empty and negative item counts still expose one safe page`() {
        val empty = HomePaginationPolicy.resolve(
            itemCount = 0,
            availableHeightPx = 500,
            rowHeightPx = 52,
            requestedPage = Int.MAX_VALUE,
        )
        val invalidCount = HomePaginationPolicy.resolve(
            itemCount = Int.MIN_VALUE,
            availableHeightPx = 0,
            rowHeightPx = 0,
            requestedPage = Int.MIN_VALUE,
        )

        val expected = HomePagination(pageSize = 1, pageCount = 1, clampedPage = 0)
        assertEquals(expected, empty)
        assertEquals(expected, invalidCount)
    }

    @Test
    fun `maximum integer inputs do not overflow`() {
        val onePage = HomePaginationPolicy.resolve(
            itemCount = Int.MAX_VALUE,
            availableHeightPx = Int.MAX_VALUE,
            rowHeightPx = 1,
            requestedPage = Int.MAX_VALUE,
        )
        val manyPages = HomePaginationPolicy.resolve(
            itemCount = Int.MAX_VALUE,
            availableHeightPx = 2,
            rowHeightPx = 1,
            requestedPage = Int.MAX_VALUE,
        )

        assertEquals(
            HomePagination(
                pageSize = Int.MAX_VALUE,
                pageCount = 1,
                clampedPage = 0,
            ),
            onePage,
        )
        assertEquals(
            HomePagination(
                pageSize = 2,
                pageCount = 1_073_741_824,
                clampedPage = 1_073_741_823,
            ),
            manyPages,
        )
    }
}
