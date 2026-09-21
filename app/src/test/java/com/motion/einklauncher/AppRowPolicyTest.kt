package com.motion.einklauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRowPolicyTest {
    @Test
    fun `a plain available app shows only its package name`() {
        val lines = AppRowPolicy.secondaryLines(hasAlias = false, isAvailable = true)

        assertEquals(listOf(AppRowLine.PACKAGE_NAME), lines)
    }

    @Test
    fun `a renamed app also shows the system name it came from`() {
        val lines = AppRowPolicy.secondaryLines(hasAlias = true, isAvailable = true)

        assertEquals(listOf(AppRowLine.SYSTEM_NAME, AppRowLine.PACKAGE_NAME), lines)
    }

    @Test
    fun `an app that vanished from the device is flagged`() {
        val lines = AppRowPolicy.secondaryLines(hasAlias = false, isAvailable = false)

        assertEquals(listOf(AppRowLine.UNAVAILABLE, AppRowLine.PACKAGE_NAME), lines)
    }

    @Test
    fun `a renamed app that vanished keeps both notes above the package name`() {
        val lines = AppRowPolicy.secondaryLines(hasAlias = true, isAvailable = false)

        assertEquals(
            listOf(AppRowLine.SYSTEM_NAME, AppRowLine.UNAVAILABLE, AppRowLine.PACKAGE_NAME),
            lines,
        )
    }
}