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

    @Test
    fun `a validated wifi transport counts as connected`() {
        assertEquals(
            true,
            WifiIndicatorPolicy.isConnected(
                hasWifiTransport = true,
                validated = true,
                validationSupported = true,
            ),
        )
    }

    @Test
    fun `a wifi transport without validation does not count as connected`() {
        assertEquals(
            false,
            WifiIndicatorPolicy.isConnected(
                hasWifiTransport = true,
                validated = false,
                validationSupported = true,
            ),
        )
    }

    @Test
    fun `validation never substitutes for a missing wifi transport`() {
        assertEquals(
            false,
            WifiIndicatorPolicy.isConnected(
                hasWifiTransport = false,
                validated = true,
                validationSupported = true,
            ),
        )
    }

    @Test
    fun `platforms without validation fall back to transport presence`() {
        assertEquals(
            true,
            WifiIndicatorPolicy.isConnected(
                hasWifiTransport = true,
                validated = false,
                validationSupported = false,
            ),
        )
        assertEquals(
            false,
            WifiIndicatorPolicy.isConnected(
                hasWifiTransport = false,
                validated = false,
                validationSupported = false,
            ),
        )
    }
}
