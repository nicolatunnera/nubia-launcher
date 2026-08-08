package com.nubia.launcher.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.google.android.material.color.DynamicColors
import com.nubia.launcher.R
import com.nubia.launcher.data.LauncherSettings

/** Applica tema chiaro/scuro + colore accento a runtime. */
object ThemeManager {

    /** Valore accento = "Material You" (colori dinamici dal wallpaper, solo API 31+). */
    const val ACCENT_DYNAMIC = -1

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

        if (settings.accent == ACCENT_DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context is Activity && DynamicColors.applyToActivityIfAvailable(context)) return
        }

        context.setTheme(if (dark) R.style.Theme_NubiaLauncher_Dark else R.style.Theme_NubiaLauncher)
        val index = if (settings.accent == ACCENT_DYNAMIC) 0 else settings.accent
        context.theme.applyStyle(ACCENT_OVERLAYS[index.coerceIn(ACCENT_OVERLAYS.indices)], true)
    }
}
