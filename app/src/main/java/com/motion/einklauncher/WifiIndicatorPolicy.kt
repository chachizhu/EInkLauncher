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
}
