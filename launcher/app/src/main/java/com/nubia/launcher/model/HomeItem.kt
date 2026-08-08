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

    /** Cartella con più app (creata trascinando un'icona su un'altra). */
    class Folder(override val id: String) : HomeItem() {
        var name: String = "Cartella"
        val apps: MutableList<AppInfo> = mutableListOf()
    }
}
