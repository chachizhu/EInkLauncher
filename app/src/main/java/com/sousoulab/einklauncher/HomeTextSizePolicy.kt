package com.sousoulab.einklauncher

/** Bounded text sizes keep all eight launcher rows usable on compact e-reader screens. */
internal object HomeTextSizePolicy {
    const val MIN_SP = 19
    const val DEFAULT_SP = 23
    const val MAX_SP = 29

    private const val STEP_SP = 2

    fun normalize(sizeSp: Int): Int = sizeSp.coerceIn(MIN_SP, MAX_SP)

    fun decrease(sizeSp: Int): Int =
        (normalize(sizeSp) - STEP_SP).coerceAtLeast(MIN_SP)

    fun increase(sizeSp: Int): Int =
        (normalize(sizeSp) + STEP_SP).coerceAtMost(MAX_SP)
}
