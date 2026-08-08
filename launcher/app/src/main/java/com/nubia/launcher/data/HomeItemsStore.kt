package com.nubia.launcher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Elemento della home in forma serializzata (indipendente dalle app installate). */
data class ParsedHomeItem(
    val type: String,
    val component: String = "",
    val appWidgetId: Int = -1,
    val name: String = "",
    val children: List<String> = emptyList()
)

/**
 * Salva e carica la disposizione della home (ordine, cartelle, widget e dock)
 * nelle SharedPreferences in formato JSON.
 */
class HomeItemsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(HomeItemsStore.PREFS_NAME, Context.MODE_PRIVATE)

    fun saveItems(items: List<ParsedHomeItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val json = JSONObject()
                .put("type", item.type)
                .put("component", item.component)
                .put("appWidgetId", item.appWidgetId)
                .put("name", item.name)
            if (item.children.isNotEmpty()) {
                json.put("children", JSONArray(item.children))
            }
            arr.put(json)
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun loadItems(): List<ParsedHomeItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val json = arr.getJSONObject(i)
                val children = json.optJSONArray("children")
                ParsedHomeItem(
                    type = json.getString("type"),
                    component = json.optString("component", ""),
                    appWidgetId = json.optInt("appWidgetId", -1),
                    name = json.optString("name", ""),
                    children = if (children != null) {
                        (0 until children.length()).map { children.getString(it) }
                    } else {
                        emptyList()
                    }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDock(components: List<String>) {
        prefs.edit().putStringSet(KEY_DOCK, components.toSet()).apply()
    }

    fun loadDock(): List<String> =
        (prefs.getStringSet(KEY_DOCK, emptySet()) ?: emptySet()).toList()

    companion object {
        private const val PREFS_NAME = "home_items"
        private const val KEY_ITEMS = "items"
        private const val KEY_DOCK = "dock"
    }
}
