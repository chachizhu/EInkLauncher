package com.motion.einklauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiIndicatorPolicyTest {
    @Test
    fun `hidden when the wifi radio is off regardless of connectivity`() {
        assertEquals(
            WifiIndicator.HIDDEN,
            WifiIndicatorPolicy.resolve(wifiEnabled = false, connected = false),
        )
        assertEquals(
            WifiIndicator.HIDDEN,
            WifiIndicatorPolicy.resolve(wifiEnabled = false, connected = true),
        )
    }

    @Test
    fun `active when wifi is on and connected`() {
        assertEquals(
            WifiIndicator.ACTIVE,
            WifiIndicatorPolicy.resolve(wifiEnabled = true, connected = true),
        )
    }

    @Test
    fun `dim when wifi is on but not connected`() {
        assertEquals(
            WifiIndicator.DIM,
            WifiIndicatorPolicy.resolve(wifiEnabled = true, connected = false),
        )
    }
}
