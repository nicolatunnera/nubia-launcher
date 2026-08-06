package com.nubia.launcher.home.workspace

import com.nubia.launcher.data.LauncherSettings

/** Parametri di layout della home, derivati dalle impostazioni. */
data class HomeScreenConfig(
    val columns: Int,
    val rows: Int,
    val iconSizeDp: Int,
    val labelSizeSp: Int,
    val showLabels: Boolean
)

fun LauncherSettings.toHomeScreenConfig() = HomeScreenConfig(
    columns = columns,
    rows = rows,
    iconSizeDp = iconSizeDp,
    labelSizeSp = labelSizeSp,
    showLabels = showLabels
)
