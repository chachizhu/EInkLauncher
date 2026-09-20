package com.motion.einklauncher

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherPreferencesTest {
    @Test
    fun `app aliases are local to a component and blank values restore the system label`() {
        val store = InMemorySharedPreferences()
        val preferences = LauncherPreferences(store)
        val reader = "reader.package/reader.package.ReaderActivity"
        val notes = "notes.package/notes.package.NotesActivity"

        preferences.saveAppAliasForComponentKey(reader, "  Continue\n\treading  ")
        preferences.saveAppAliasForComponentKey(notes, "N".repeat(60))

        assertEquals("Continue reading", preferences.appAliasForComponentKey(reader))
        assertEquals(48, preferences.appAliasForComponentKey(notes)?.length)

        preferences.saveAppAliasForComponentKey(reader, "   ")

        assertNull(preferences.appAliasForComponentKey(reader))
        assertEquals(48, preferences.appAliasForComponentKey(notes)?.length)
    }

    @Test
    fun `app alias truncation never stores half of a surrogate pair`() {
        val store = InMemorySharedPreferences()
        val preferences = LauncherPreferences(store)
        val reader = "reader.package/reader.package.ReaderActivity"

        preferences.saveAppAliasForComponentKey(reader, "N".repeat(47) + "😀")

        assertEquals("N".repeat(47), preferences.appAliasForComponentKey(reader))
    }

    @Test
    fun `legacy text size remains exact and does not touch selected apps`() {
        val serializedSelection = "reader.package/.ReaderActivity\nnotes.package/.NotesActivity"
        val store = InMemorySharedPreferences(
            mapOf(
                "home_app_text_size_sp" to 27,
                "selected_components" to serializedSelection,
            ),
        )

        val preset = LauncherPreferences(store).displayPreset()

        assertEquals(DisplayPreset.LARGE, preset)
        assertNull(store.getString("display_preset", null))
        assertEquals(27, store.getInt("home_app_text_size_sp", -1))
        assertEquals(27, LauncherPreferences(store).homeAppTextSizeSp())
        assertEquals(serializedSelection, store.getString("selected_components", null))
    }

    @Test
    fun `stored preset is authoritative and repairs the legacy mirror`() {
        val store = InMemorySharedPreferences(
            mapOf(
                "display_preset" to "compact",
                "home_app_text_size_sp" to 50,
            ),
        )

        val preferences = LauncherPreferences(store)

        assertEquals(DisplayPreset.COMPACT, preferences.displayPreset())
        assertEquals(19, preferences.homeAppTextSizeSp())
        assertEquals(19, store.getInt("home_app_text_size_sp", -1))
    }

    @Test
    fun `saving preset updates both new and compatibility keys`() {
        val selectedApps = "reader.package/.ReaderActivity"
        val store = InMemorySharedPreferences(mapOf("selected_components" to selectedApps))
        val preferences = LauncherPreferences(store)

        preferences.saveDisplayPreset(DisplayPreset.COMFORTABLE)

        assertEquals("comfortable", store.getString("display_preset", null))
        assertEquals(23, store.getInt("home_app_text_size_sp", -1))
        assertEquals(selectedApps, store.getString("selected_components", null))
    }

    @Test
    fun `legacy save API preserves the exact size and clears an explicit preset`() {
        val store = InMemorySharedPreferences()
        val preferences = LauncherPreferences(store)

        preferences.saveHomeAppTextSizeSp(21)

        assertEquals(DisplayPreset.COMPACT, preferences.displayPreset())
        assertEquals(false, preferences.hasExplicitDisplayPreset())
        assertEquals(21, preferences.homeAppTextSizeSp())
    }

    @Test
    fun `missing clock preference uses the new default without changing legacy state`() {
        val selectedApps = "reader.package/.ReaderActivity"
        val store = InMemorySharedPreferences(
            mapOf(
                "selected_components" to selectedApps,
                "home_app_text_size_sp" to 27,
            ),
        )

        val mode = LauncherPreferences(store).homeClockMode()

        assertEquals(HomeClockMode.DATE_AND_TIME, mode)
        assertNull(store.getString("home_clock_mode", null))
        assertEquals(selectedApps, store.getString("selected_components", null))
        assertEquals(27, store.getInt("home_app_text_size_sp", -1))
    }

    @Test
    fun `unknown clock preference falls back without rewriting its stored value`() {
        val store = InMemorySharedPreferences(
            mapOf(
                "home_clock_mode" to "future_mode",
                "display_preset" to "large",
            ),
        )

        val mode = LauncherPreferences(store).homeClockMode()

        assertEquals(HomeClockMode.DATE_AND_TIME, mode)
        assertEquals("future_mode", store.getString("home_clock_mode", null))
        assertEquals("large", store.getString("display_preset", null))
    }

    @Test
    fun `missing grid preference uses the default layout without writing state`() {
        val store = InMemorySharedPreferences()
        val preferences = LauncherPreferences(store)

        assertEquals(8, preferences.homeGridRows())
        assertEquals(1, preferences.homeGridColumns())
        assertEquals(8, preferences.homeGridCapacity())
        assertEquals(false, store.contains("home_grid_rows"))
        assertEquals(false, store.contains("home_grid_columns"))
    }

    @Test
    fun `saving grid values normalizes them into the supported bounds`() {
        val store = InMemorySharedPreferences()
        val preferences = LauncherPreferences(store)

        preferences.saveHomeGridRows(99)
        preferences.saveHomeGridColumns(0)

        assertEquals(10, preferences.homeGridRows())
        assertEquals(1, preferences.homeGridColumns())
        assertEquals(10, preferences.homeGridCapacity())
        assertEquals(10, store.getInt("home_grid_rows", -1))
        assertEquals(1, store.getInt("home_grid_columns", -1))

        preferences.saveHomeGridRows(9)
        preferences.saveHomeGridColumns(2)

        assertEquals(9, preferences.homeGridRows())
        assertEquals(2, preferences.homeGridColumns())
        assertEquals(18, preferences.homeGridCapacity())
    }

    @Test
    fun `out of range stored grid values fall back into bounds on read`() {
        val store = InMemorySharedPreferences(
            mapOf(
                "home_grid_rows" to 3,
                "home_grid_columns" to 5,
            ),
        )
        val preferences = LauncherPreferences(store)

        assertEquals(8, preferences.homeGridRows())
        assertEquals(2, preferences.homeGridColumns())
        assertEquals(16, preferences.homeGridCapacity())
    }

    @Test
    fun `saving grid values leaves selection and display preferences untouched`() {
        val selectedApps = "reader.package/.ReaderActivity"
        val store = InMemorySharedPreferences(
            mapOf(
                "selected_components" to selectedApps,
                "display_preset" to "compact",
                "home_app_text_size_sp" to 19,
            ),
        )
        val preferences = LauncherPreferences(store)

        preferences.saveHomeGridRows(10)
        preferences.saveHomeGridColumns(2)

        assertEquals(selectedApps, store.getString("selected_components", null))
        assertEquals("compact", store.getString("display_preset", null))
        assertEquals(19, store.getInt("home_app_text_size_sp", -1))
    }

    @Test
    fun `saving clock modes is independent from selection and display preferences`() {
        val selectedApps = "reader.package/.ReaderActivity"
        val store = InMemorySharedPreferences(
            mapOf(
                "selected_components" to selectedApps,
                "display_preset" to "compact",
                "home_app_text_size_sp" to 19,
            ),
        )
        val preferences = LauncherPreferences(store)

        preferences.saveHomeClockMode(HomeClockMode.DATE_AND_TIME)

        assertEquals(HomeClockMode.DATE_AND_TIME, preferences.homeClockMode())
        assertEquals("date_and_time", store.getString("home_clock_mode", null))
        assertEquals(selectedApps, store.getString("selected_components", null))
        assertEquals("compact", store.getString("display_preset", null))
        assertEquals(19, store.getInt("home_app_text_size_sp", -1))

        preferences.saveDisplayPreset(DisplayPreset.COMFORTABLE)

        assertEquals(HomeClockMode.DATE_AND_TIME, preferences.homeClockMode())
        assertEquals("date_and_time", store.getString("home_clock_mode", null))
        assertEquals(selectedApps, store.getString("selected_components", null))
        assertEquals("comfortable", store.getString("display_preset", null))
        assertEquals(23, store.getInt("home_app_text_size_sp", -1))

        preferences.saveHomeClockMode(HomeClockMode.TIME_ONLY)

        assertEquals(HomeClockMode.TIME_ONLY, preferences.homeClockMode())
        assertEquals("time_only", store.getString("home_clock_mode", null))
        assertEquals(selectedApps, store.getString("selected_components", null))
        assertEquals("comfortable", store.getString("display_preset", null))
        assertEquals(23, store.getInt("home_app_text_size_sp", -1))
    }
}

private class InMemorySharedPreferences(
    initialValues: Map<String, Any?> = emptyMap(),
) : SharedPreferences {
    private val values = initialValues.toMutableMap()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    private class Editor(
        private val values: MutableMap<String, Any?>,
    ) : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
            record(key, value)
        }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = apply {
            record(key, values?.toSet())
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply {
            record(key, value)
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply {
            record(key, value)
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply {
            record(key, value)
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply {
            record(key, value)
        }

        override fun remove(key: String): SharedPreferences.Editor = apply {
            changes.remove(key)
            removals += key
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
            changes.clear()
            removals.clear()
        }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun record(key: String, value: Any?) {
            removals.remove(key)
            if (value == null) {
                changes.remove(key)
                removals += key
            } else {
                changes[key] = value
            }
        }

        private fun applyChanges() {
            if (clearRequested) values.clear()
            removals.forEach(values::remove)
            values.putAll(changes)
        }
    }
}
