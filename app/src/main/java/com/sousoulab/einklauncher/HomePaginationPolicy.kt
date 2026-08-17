package com.sousoulab.einklauncher

/** Pure layout policy for fitting home-screen app rows into discrete pages. */
internal object HomePaginationPolicy {
    fun resolve(
        itemCount: Int,
        availableHeightPx: Int,
        rowHeightPx: Int,
        requestedPage: Int,
    ): HomePagination {
        val safeItemCount = itemCount.coerceAtLeast(0)
        val capacity = if (availableHeightPx > 0 && rowHeightPx > 0) {
            (availableHeightPx / rowHeightPx).coerceAtLeast(1)
        } else {
            1
        }
        val pageSize = capacity.coerceAtMost(safeItemCount.coerceAtLeast(1))
        val pageCount = if (safeItemCount == 0) {
            1
        } else {
            ((safeItemCount - 1) / pageSize) + 1
        }
        val clampedPage = requestedPage.coerceIn(0, pageCount - 1)

        return HomePagination(
            pageSize = pageSize,
            pageCount = pageCount,
            clampedPage = clampedPage,
        )
    }
}

internal data class HomePagination(
    val pageSize: Int,
    val pageCount: Int,
    val clampedPage: Int,
)
