package com.nubia.launcher.model

import android.content.ComponentName
import android.graphics.drawable.Drawable

/** Riferimento a un'app installata. */
data class AppInfo(
    val component: ComponentName,
    val label: String,
    val icon: Drawable
) {
    val key: String get() = component.flattenToString()
}
