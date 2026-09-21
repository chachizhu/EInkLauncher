package com.motion.einklauncher

/**
 * Pure geometry for the all-apps drawer.
 *
 * The drawer keeps its own grid instead of following [HomeGridPolicy]: home is capped at two
 * columns to keep readable rows, while the drawer exists to reach an app quickly and packs four.
 * Row count is derived from the space the layout actually left free, so the grid fits any screen
 * without the drawer needing a scrolling container.
 */
internal object AppDrawerPolicy {
    const val COLUMNS = 4

    /** Row bounds: a cramped screen still gets a row, and a huge one cannot build a silly page. */
    const val MIN_ROWS = 1
    const val MAX_ROWS = 12

    /** Used before a cell has been measured, and when there is nothing to lay out. */
    const val FALLBACK_ROWS = 6

    /**
     * Rows that fit in [availableHeightPx]. Integer division guarantees
     * `rows * cellHeightPx <= availableHeightPx`, so the grid can never overflow the height the
     * layout reserved for it.
     */
    fun rowsFor(availableHeightPx: Int, cellHeightPx: Int): Int {
        require(cellHeightPx > 0) { "cellHeightPx must be positive" }
        return (availableHeightPx / cellHeightPx).coerceIn(MIN_ROWS, MAX_ROWS)
    }

    /** Splits one page into grid rows; the final row holds whatever is left over. */
    fun <T> toRows(items: List<T>, columns: Int = COLUMNS): List<List<T>> {
        require(columns > 0) { "columns must be positive" }
        return items.chunked(columns)
    }
}