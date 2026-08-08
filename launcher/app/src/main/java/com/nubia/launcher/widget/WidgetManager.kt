package com.nubia.launcher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import com.nubia.launcher.model.HomeItem

/**
 * Gestione dei widget sulla schermata home tramite [AppWidgetHost].
 * Base per il supporto completo ai widget (persistenza e ridimensionamento inclusi).
 */
class WidgetManager(private val context: Context) {

    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val widgetHost = AppWidgetHost(context, HOST_ID)
    private val allocatedIds = mutableListOf<Int>()

    fun startListening() = widgetHost.startListening()

    fun stopListening() = widgetHost.stopListening()

    /** Prepara l'Intent per il selettore widget di sistema. */
    fun buildPickIntent(): Intent {
        allocatedIds.forEach { widgetHost.deleteAppWidgetId(it) }
        allocatedIds.clear()
        val providers = try {
            appWidgetManager.installedProviders
        } catch (_: Exception) {
            emptyList()
        }
        providers.forEach { allocatedIds.add(widgetHost.allocateAppWidgetId()) }
        return Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allocatedIds.toIntArray())
    }

    /** Crea la vista del widget scelto dall'utente e pulisce gli id non usati. */
    fun consumePickedWidget(data: Intent?): HomeItem.Widget? {
        val chosen = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        val info = if (chosen != -1) appWidgetManager.getAppWidgetInfo(chosen) else null
        val widget = if (chosen != -1 && info != null) {
            val hostView = widgetHost.createView(context, chosen, info)
            hostView.setAppWidget(chosen, info)
            HomeItem.Widget(id = "widget_$chosen", appWidgetId = chosen, hostView = hostView)
        } else {
            null
        }
        allocatedIds.filter { it != chosen }.forEach { widgetHost.deleteAppWidgetId(it) }
        allocatedIds.clear()
        return widget
    }

    /** Ricrea la vista di un widget persistito (es. al riavvio del launcher). */
    fun restoreWidget(appWidgetId: Int): HomeItem.Widget? {
        return try {
            val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: run {
                widgetHost.deleteAppWidgetId(appWidgetId)
                return null
            }
            val hostView = widgetHost.createView(context, appWidgetId, info)
            hostView.setAppWidget(appWidgetId, info)
            HomeItem.Widget(id = "widget_$appWidgetId", appWidgetId = appWidgetId, hostView = hostView)
        } catch (_: Exception) {
            null
        }
    }

    fun removeWidget(item: HomeItem.Widget) {
        (item.hostView.parent as? ViewGroup)?.removeView(item.hostView)
        widgetHost.deleteAppWidgetId(item.appWidgetId)
    }

    fun close() {
        widgetHost.stopListening()
    }

    companion object {
        private const val HOST_ID = 0x4E1
    }
}
