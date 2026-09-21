package com.motion.einklauncher

/**
 * Stable display choices for the home screen.
 *
 * [storageValue] is deliberately separate from the enum name so persisted settings remain
 * compatible if the Kotlin identifiers are ever renamed.
 *
 * Row height, weight and gaps move together with [homeTextSizeSp], so a preset is a single
 * typographic decision rather than four unrelated numbers.
 */
internal enum class DisplayPreset(
    val storageValue: String,
    val homeTextSizeSp: Int,
    val homeRowHeightDp: Int,
    val fontWeight: Int,
    /**
     * Multiplies the base gaps so spacing tracks the type size. Held as a ratio rather than as
     * hand-copied dp values so the presets cannot silently drift apart.
     */
    private val spacingScale: Float,
) {
    COMPACT(
        storageValue = "compact",
        homeTextSizeSp = 19,
        homeRowHeightDp = 48,
        fontWeight = 400,
        spacingScale = 1f,
    ),
    COMFORTABLE(
        storageValue = "comfortable",
        homeTextSizeSp = 23,
        homeRowHeightDp = 56,
        fontWeight = 500,
        spacingScale = 1f,
    ),
    LARGE(
        storageValue = "large",
        homeTextSizeSp = 30,
        // 56 dp x 30/23, so the row grows in step with the type instead of lagging behind it.
        homeRowHeightDp = 73,
        fontWeight = 500,
        // 30 sp / 23 sp: the large preset keeps the comfortable preset's proportions.
        spacingScale = 30f / 23f,
    ),
    ;

    val homeRowSpacingDp: Int get() = scaledSpacing(BASE_ROW_SPACING_DP)

    val homeColumnSpacingDp: Int get() = scaledSpacing(BASE_COLUMN_SPACING_DP)

    val homeIconLabelSpacingDp: Int get() = scaledSpacing(BASE_ICON_LABEL_SPACING_DP)

    val homeRowPaddingDp: Int get() = scaledSpacing(BASE_ROW_PADDING_DP)

    private fun scaledSpacing(baseDp: Int): Int = kotlin.math.round(baseDp * spacingScale).toInt()

    fun smaller(): DisplayPreset? = entries.getOrNull(ordinal - 1)

    fun larger(): DisplayPreset? = entries.getOrNull(ordinal + 1)

    companion object {
        /** Gaps as measured at COMFORTABLE; the preset's spacing scale moves them with the type. */
        const val BASE_ROW_SPACING_DP = 4

        const val BASE_COLUMN_SPACING_DP = 8

        const val BASE_ICON_LABEL_SPACING_DP = 12

        const val BASE_ROW_PADDING_DP = 12

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
