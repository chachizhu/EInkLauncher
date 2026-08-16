package com.sousoulab.einklauncher

import android.content.ComponentName
import android.content.Context

internal class LauncherPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

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

    fun homeAppTextSizeSp(): Int = HomeTextSizePolicy.normalize(
        preferences.getInt(KEY_HOME_APP_TEXT_SIZE_SP, HomeTextSizePolicy.DEFAULT_SP),
    )

    fun saveHomeAppTextSizeSp(sizeSp: Int) {
        preferences.edit()
            .putInt(KEY_HOME_APP_TEXT_SIZE_SP, HomeTextSizePolicy.normalize(sizeSp))
            .apply()
    }

    fun isFirstRun(): Boolean = !preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun markOnboardingComplete() {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
    }

    private companion object {
        const val FILE_NAME = "launcher_preferences"
        const val KEY_SELECTED_COMPONENTS = "selected_components"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_HOME_APP_TEXT_SIZE_SP = "home_app_text_size_sp"
    }
}
