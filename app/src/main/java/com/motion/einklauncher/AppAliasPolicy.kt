package com.motion.einklauncher

/** Pure normalization rules for optional, device-local home labels. */
internal object AppAliasPolicy {
    const val MAX_LENGTH = 48
    private val lineBreaks = Regex("[\\r\\n\\t]+")
    private val repeatedSpaces = Regex(" {2,}")

    fun normalize(value: String?): String? {
        val singleLine = value
            ?.trim()
            ?.replace(lineBreaks, " ")
            ?.replace(repeatedSpaces, " ")
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        if (singleLine.length <= MAX_LENGTH) return singleLine

        val truncated = singleLine.take(MAX_LENGTH)
        return if (truncated.last().isHighSurrogate()) truncated.dropLast(1) else truncated
    }
}
