package com.motion.einklauncher

/** The dim secondary lines drawn under an app's name, in display order. */
enum class AppRowLine {
    /** The device's own name for a renamed app, so the rename stays traceable. */
    SYSTEM_NAME,

    /** The saved app is no longer installed. */
    UNAVAILABLE,

    /** Always last: identical labels only become distinguishable here. */
    PACKAGE_NAME,
}

/** Pure rules behind the management app rows shared by the selected and available lists. */
object AppRowPolicy {
    fun secondaryLines(hasAlias: Boolean, isAvailable: Boolean): List<AppRowLine> = buildList {
        if (hasAlias) add(AppRowLine.SYSTEM_NAME)
        if (!isAvailable) add(AppRowLine.UNAVAILABLE)
        add(AppRowLine.PACKAGE_NAME)
    }
}