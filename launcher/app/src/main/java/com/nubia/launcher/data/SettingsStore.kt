package com.nubia.launcher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Snapshot immutabile di tutte le preferenze del launcher. */
data class LauncherSettings(
    val columns: Int = 4,
    val rows: Int = 6,
    val pages: Int = 3,
    val iconSizeDp: Int = 52,
    val dockIconSizeDp: Int = 56,
    val labelSizeSp: Int = 11,
    val showLabels: Boolean = true,
    val darkMode: Int = MODE_SYSTEM,
    val accent: Int = 0,
    val gestureDrawer: Boolean = true,
    val showClock: Boolean = true
) {
    val cellCount: Int get() = columns * rows

    companion object {
        const val MODE_SYSTEM = 0
        const val MODE_LIGHT = 1
        const val MODE_DARK = 2
    }
}

/**
 * Archivio delle preferenze, osservabile tramite StateFlow.
 * Ogni modifica (anche da PreferenceFragment) emette un nuovo snapshot.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener { _, _ ->
            _settings.value = read()
        }
    }

    fun get(): LauncherSettings = _settings.value

    private fun read(): LauncherSettings = LauncherSettings(
        columns = prefs.getString(KEY_COLUMNS, "4")?.toIntOrNull() ?: 4,
        rows = prefs.getString(KEY_ROWS, "6")?.toIntOrNull() ?: 6,
        pages = prefs.getString(KEY_PAGES, "3")?.toIntOrNull() ?: 3,
        iconSizeDp = prefs.getInt(KEY_ICON_SIZE, 52),
        dockIconSizeDp = prefs.getInt(KEY_DOCK_ICON_SIZE, 56),
        labelSizeSp = prefs.getInt(KEY_LABEL_SIZE, 11),
        showLabels = prefs.getBoolean(KEY_SHOW_LABELS, true),
        darkMode = prefs.getString(KEY_DARK_MODE, "0")?.toIntOrNull() ?: LauncherSettings.MODE_SYSTEM,
        accent = prefs.getString(KEY_ACCENT, "0")?.toIntOrNull() ?: 0,
        gestureDrawer = prefs.getBoolean(KEY_GESTURE_DRAWER, true),
        showClock = prefs.getBoolean(KEY_SHOW_CLOCK, true)
    )

    companion object {
        const val PREFS_NAME = "launcher_settings"

        const val KEY_COLUMNS = "grid_columns"
        const val KEY_ROWS = "grid_rows"
        const val KEY_PAGES = "pages"
        const val KEY_ICON_SIZE = "icon_size"
        const val KEY_DOCK_ICON_SIZE = "dock_icon_size"
        const val KEY_LABEL_SIZE = "label_size"
        const val KEY_SHOW_LABELS = "show_labels"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_ACCENT = "accent"
        const val KEY_GESTURE_DRAWER = "gesture_drawer"
        const val KEY_SHOW_CLOCK = "show_clock"
    }
}
