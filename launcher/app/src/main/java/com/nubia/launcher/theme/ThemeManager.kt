package com.nubia.launcher.theme

import android.content.Context
import android.content.res.Configuration
import com.nubia.launcher.R
import com.nubia.launcher.data.LauncherSettings

/** Applica tema chiaro/scuro + colore accento a runtime. */
object ThemeManager {

    private val ACCENT_OVERLAYS = intArrayOf(
        R.style.ThemeOverlay_Accent_Default,
        R.style.ThemeOverlay_Accent_Blue,
        R.style.ThemeOverlay_Accent_Purple,
        R.style.ThemeOverlay_Accent_Red,
        R.style.ThemeOverlay_Accent_Green,
        R.style.ThemeOverlay_Accent_Orange,
        R.style.ThemeOverlay_Accent_Pink
    )

    fun isDark(context: Context, settings: LauncherSettings): Boolean = when (settings.darkMode) {
        LauncherSettings.MODE_DARK -> true
        LauncherSettings.MODE_LIGHT -> false
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    /** Da chiamare prima di [android.app.Activity.setContentView]. */
    fun apply(context: Context, settings: LauncherSettings) {
        val dark = isDark(context, settings)
        context.setTheme(if (dark) R.style.Theme_NubiaLauncher_Dark else R.style.Theme_NubiaLauncher)
        val accent = ACCENT_OVERLAYS[settings.accent.coerceIn(ACCENT_OVERLAYS.indices)]
        context.theme.applyStyle(accent, true)
    }
}
