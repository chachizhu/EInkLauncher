package com.sousoulab.einklauncher

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences

internal class LauncherPreferences(
    private val preferences: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
    )

    fun selectedComponents(): List<ComponentName> = preferences
        .getString(KEY_SELECTED_COMPONENTS, null)
        ?.lineSequence()
        ?.mapNotNull { ComponentName.unflattenFromString(it) }
        ?.distinct()
        ?.take(SelectionPolicy.MAX_SELECTED_APPS)
        ?.toList()
        .orEmpty()

    fun saveSelectedComponents(components: List<ComponentName>) {
        val serialized = components
            .distinct()
            .take(SelectionPolicy.MAX_SELECTED_APPS)
            .joinToString(separator = "\n") { it.flattenToString() }
        preferences.edit().putString(KEY_SELECTED_COMPONENTS, serialized).apply()
    }

    fun appAlias(component: ComponentName): String? =
        appAliasForComponentKey(component.flattenToString())

    internal fun appAliasForComponentKey(componentKey: String): String? =
        AppAliasPolicy.normalize(preferences.getString(aliasKey(componentKey), null))

    fun saveAppAlias(component: ComponentName, alias: String?) {
        saveAppAliasForComponentKey(component.flattenToString(), alias)
    }

    internal fun saveAppAliasForComponentKey(componentKey: String, alias: String?) {
        val normalized = AppAliasPolicy.normalize(alias)
        preferences.edit().apply {
            if (normalized == null) {
                remove(aliasKey(componentKey))
            } else {
                putString(aliasKey(componentKey), normalized)
            }
        }.apply()
    }

    /** Resolves the closest visual treatment without mutating a legacy free-form size. */
    fun displayPreset(): DisplayPreset {
        val storageValue = preferences.getString(KEY_DISPLAY_PRESET, null)
        return DisplayPreset.fromStorageOrLegacy(
            storageValue = storageValue,
            legacyTextSizeSp = homeAppTextSizeSp(),
        )
    }

    fun hasExplicitDisplayPreset(): Boolean =
        DisplayPreset.fromStorageValue(preferences.getString(KEY_DISPLAY_PRESET, null)) != null

    fun saveDisplayPreset(preset: DisplayPreset) {
        persistDisplayPreset(preset)
    }

    fun homeClockMode(): HomeClockMode = HomeClockMode.fromStorageValue(
        preferences.getString(KEY_HOME_CLOCK_MODE, null),
    ) ?: HomeClockMode.DEFAULT

    fun saveHomeClockMode(mode: HomeClockMode) {
        preferences.edit()
            .putString(KEY_HOME_CLOCK_MODE, mode.storageValue)
            .apply()
    }

    /** Keeps an existing 19-50 sp accessibility choice until a preset is explicitly selected. */
    fun homeAppTextSizeSp(): Int {
        val storedPreset = DisplayPreset.fromStorageValue(
            preferences.getString(KEY_DISPLAY_PRESET, null),
        )
        if (storedPreset != null) {
            if (
                preferences.getInt(KEY_HOME_APP_TEXT_SIZE_SP, storedPreset.homeTextSizeSp) !=
                storedPreset.homeTextSizeSp
            ) {
                preferences.edit()
                    .putInt(KEY_HOME_APP_TEXT_SIZE_SP, storedPreset.homeTextSizeSp)
                    .apply()
            }
            return storedPreset.homeTextSizeSp
        }
        return HomeTextSizePolicy.normalize(
            preferences.getInt(KEY_HOME_APP_TEXT_SIZE_SP, HomeTextSizePolicy.DEFAULT_SP),
        )
    }

    fun saveHomeAppTextSizeSp(sizeSp: Int) {
        preferences.edit()
            .remove(KEY_DISPLAY_PRESET)
            .putInt(KEY_HOME_APP_TEXT_SIZE_SP, HomeTextSizePolicy.normalize(sizeSp))
            .apply()
    }

    fun isFirstRun(): Boolean = !preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun markOnboardingComplete() {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
    }

    private fun persistDisplayPreset(preset: DisplayPreset) {
        preferences.edit()
            .putString(KEY_DISPLAY_PRESET, preset.storageValue)
            .putInt(KEY_HOME_APP_TEXT_SIZE_SP, preset.homeTextSizeSp)
            .apply()
    }

    private fun aliasKey(componentKey: String): String = KEY_APP_ALIAS_PREFIX + componentKey

    private companion object {
        const val FILE_NAME = "launcher_preferences"
        const val KEY_SELECTED_COMPONENTS = "selected_components"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_HOME_APP_TEXT_SIZE_SP = "home_app_text_size_sp"
        const val KEY_DISPLAY_PRESET = "display_preset"
        const val KEY_HOME_CLOCK_MODE = "home_clock_mode"
        const val KEY_APP_ALIAS_PREFIX = "app_alias."
    }
}
