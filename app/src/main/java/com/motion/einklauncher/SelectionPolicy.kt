package com.motion.einklauncher

/** Pure selection and pagination rules shared by the launcher UI. */
object SelectionPolicy {
    const val MAX_SELECTED_APPS = 12

    fun <T> add(items: List<T>, item: T): List<T> {
        if (item in items || items.size >= MAX_SELECTED_APPS) return items.toList()
        return items + item
    }

    fun <T> remove(items: List<T>, item: T): List<T> = items.filterNot { it == item }

    fun <T> moveUp(items: List<T>, item: T): List<T> {
        val index = items.indexOf(item)
        if (index <= 0) return items.toList()
        return items.swap(index, index - 1)
    }

    fun <T> moveDown(items: List<T>, item: T): List<T> {
        val index = items.indexOf(item)
        if (index == -1 || index >= items.lastIndex) return items.toList()
        return items.swap(index, index + 1)
    }

    fun <T> page(items: List<T>, requestedPage: Int, pageSize: Int): PageResult<T> {
        require(pageSize > 0) { "pageSize must be positive" }

        val pageCount = if (items.isEmpty()) 1 else ((items.size - 1) / pageSize) + 1
        val pageIndex = requestedPage.coerceIn(0, pageCount - 1)
        val start = pageIndex * pageSize
        val end = minOf(start + pageSize, items.size)
        return PageResult(
            items = items.subList(start, end).toList(),
            pageIndex = pageIndex,
            pageCount = pageCount,
        )
    }

    private fun <T> List<T>.swap(first: Int, second: Int): List<T> =
        toMutableList().also { result ->
            val value = result[first]
            result[first] = result[second]
            result[second] = value
        }
}

data class PageResult<T>(
    val items: List<T>,
    val pageIndex: Int,
    val pageCount: Int,
) {
    val hasPrevious: Boolean get() = pageIndex > 0
    val hasNext: Boolean get() = pageIndex < pageCount - 1
}
