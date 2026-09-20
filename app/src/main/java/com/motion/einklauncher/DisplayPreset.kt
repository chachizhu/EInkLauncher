package com.motion.einklauncher

/**
 * Stable display choices for the home screen.
 *
 * [storageValue] is deliberately separate from the enum name so persisted settings remain
 * compatible if the Kotlin identifiers are ever renamed.
 */
internal enum class DisplayPreset(
    val storageValue: String,
    val homeTextSizeSp: Int,
    val homeRowHeightDp: Int,
    val fontWeight: Int,
) {
    COMPACT(
        storageValue = "compact",
        homeTextSizeSp = 19,
        homeRowHeightDp = 48,
        fontWeight = 400,
    ),
    COMFORTABLE(
        storageValue = "comfortable",
        homeTextSizeSp = 23,
        homeRowHeightDp = 56,
        fontWeight = 500,
    ),
    LARGE(
        storageValue = "large",
        homeTextSizeSp = 30,
        homeRowHeightDp = 68,
        fontWeight = 600,
    ),
    ;

    fun smaller(): DisplayPreset? = entries.getOrNull(ordinal - 1)

    fun larger(): DisplayPreset? = entries.getOrNull(ordinal + 1)

    companion object {
        val DEFAULT: DisplayPreset = COMFORTABLE

        fun fromStorageValue(value: String?): DisplayPreset? =
            entries.firstOrNull { it.storageValue == value }

        /**
         * Resolves persisted state, falling back to the closest legacy free-form text size.
         * An exact tie prefers the smaller preset, preserving the intent of users who had
         * reduced the old 23 sp default to 21 sp.
         */
        fun fromStorageOrLegacy(
            storageValue: String?,
            legacyTextSizeSp: Int,
        ): DisplayPreset = fromStorageValue(storageValue) ?: nearestToTextSize(legacyTextSizeSp)

        fun nearestToTextSize(textSizeSp: Int): DisplayPreset = entries.minWith(
            compareBy<DisplayPreset> {
                kotlin.math.abs(it.homeTextSizeSp.toLong() - textSizeSp.toLong())
            }.thenBy { it.homeTextSizeSp },
        )
    }
}
