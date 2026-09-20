package com.motion.einklauncher

/** Bounds legacy free-form sizes so upgrades never silently reduce an accessibility choice. */
internal object HomeTextSizePolicy {
    const val MIN_SP = 19
    const val DEFAULT_SP = 23
    const val MAX_SP = 50

    private const val STEP_SP = 2

    fun normalize(sizeSp: Int): Int = sizeSp.coerceIn(MIN_SP, MAX_SP)

    fun decrease(sizeSp: Int): Int =
        (normalize(sizeSp) - STEP_SP).coerceAtLeast(MIN_SP)

    fun increase(sizeSp: Int): Int =
        (normalize(sizeSp) + STEP_SP).coerceAtMost(MAX_SP)
}
