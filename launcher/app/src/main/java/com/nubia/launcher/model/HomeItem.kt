package com.nubia.launcher.model

import android.appwidget.AppWidgetHostView

/**
 * Elemento della schermata home.
 * Base per estendere con widget, scorciatoie, cartelle e gesti personalizzati.
 */
sealed class HomeItem {
    abstract val id: String

    /** Scorciatoia a un'app. */
    data class App(override val id: String, val appInfo: AppInfo) : HomeItem()

    /** Widget ospitato tramite AppWidgetHost. */
    data class Widget(
        override val id: String,
        val appWidgetId: Int,
        val hostView: AppWidgetHostView
    ) : HomeItem()
}
