package com.motion.einklauncher

/** Status-bar Wi-Fi indicator states for the home screen. */
internal enum class WifiIndicator { HIDDEN, DIM, ACTIVE }

/** Pure mapping from Wi-Fi radio state to indicator presentation. */
internal object WifiIndicatorPolicy {
    fun resolve(wifiEnabled: Boolean, connected: Boolean): WifiIndicator = when {
        !wifiEnabled -> WifiIndicator.HIDDEN
        connected -> WifiIndicator.ACTIVE
        else -> WifiIndicator.DIM
    }

    /**
     * Whether a Wi-Fi network counts as connected.
     *
     * Validation means the network reached the internet, so a captive portal or a LAN-only
     * access point reads as DIM rather than ACTIVE. NET_CAPABILITY_VALIDATED only exists from
     * API 23; on older platforms [validationSupported] is false and transport presence alone
     * decides, otherwise every API 21-22 device would report a permanent "not connected".
     */
    fun isConnected(
        hasWifiTransport: Boolean,
        validated: Boolean,
        validationSupported: Boolean,
    ): Boolean = hasWifiTransport && (validated || !validationSupported)
}
