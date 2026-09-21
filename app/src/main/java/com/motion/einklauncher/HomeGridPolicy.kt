package com.motion.einklauncher

/** Pure bounds and capacity rules for the home-screen app grid. */
internal object HomeGridPolicy {
    const val MIN_ROWS = 4
    const val MAX_ROWS = 16
    const val MIN_COLUMNS = 1
    const val MAX_COLUMNS = 2
    const val DEFAULT_ROWS = 8
    const val DEFAULT_COLUMNS = 1

    fun normalizeRows(rows: Int): Int = rows.coerceIn(MIN_ROWS, MAX_ROWS)

    fun normalizeColumns(columns: Int): Int = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    /** Apps per home page; inputs outside the supported bounds are normalized first. */
    fun capacity(rows: Int, columns: Int): Int = normalizeRows(rows) * normalizeColumns(columns)

    /**
     * Upper bound for the home content width in dp: every column may grow up to
     * [columnWidthDp], separated by [columnSpacingDp] gutters. Columns are normalized first.
     */
    fun contentMaxWidthDp(columns: Int, columnWidthDp: Int, columnSpacingDp: Int): Int {
        val normalized = normalizeColumns(columns)
        return normalized * columnWidthDp + (normalized - 1) * columnSpacingDp
    }
}
