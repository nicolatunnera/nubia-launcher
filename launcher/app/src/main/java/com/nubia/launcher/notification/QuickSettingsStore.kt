package com.nubia.launcher.notification

import android.content.Context
import android.content.SharedPreferences
import com.nubia.launcher.R

/** Interruttore rapido (toggle) del pannello. */
data class QuickToggle(val id: String, val labelRes: Int, val iconRes: Int)

/**
 * Persiste ordine e visibilità degli interruttori rapidi del pannello.
 * L'ordine è salvato come CSV per rispettare la sequenza scelta dall'utente.
 */
object QuickSettingsStore {

    private const val PREFS_NAME = "quick_settings"
    private const val KEY_ORDER = "order"
    private const val KEY_HIDDEN = "hidden"

    val all: List<QuickToggle> = listOf(
        QuickToggle("wifi", R.string.qs_wifi, R.drawable.ic_qs_wifi),
        QuickToggle("bluetooth", R.string.qs_bluetooth, R.drawable.ic_qs_bluetooth),
        QuickToggle("dnd", R.string.qs_dnd, R.drawable.ic_qs_dnd),
        QuickToggle("flashlight", R.string.qs_flashlight, R.drawable.ic_qs_flashlight)
    )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun order(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_ORDER, null)
        if (!raw.isNullOrEmpty()) {
            val saved = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val known = saved + all.map { it.id }
            return known.distinct().take(all.size)
        }
        return all.map { it.id }
    }

    fun setOrder(context: Context, order: List<String>) {
        prefs(context).edit().putString(KEY_ORDER, order.joinToString(",")).apply()
    }

    fun isHidden(context: Context, id: String): Boolean {
        val hidden = prefs(context).getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
        return id in hidden
    }

    fun setHidden(context: Context, id: String, hidden: Boolean) {
        val set = (prefs(context).getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()).toMutableSet()
        if (hidden) set.add(id) else set.remove(id)
        prefs(context).edit().putStringSet(KEY_HIDDEN, set).apply()
    }

    /** Interruttori visibili, nell'ordine scelto dall'utente. */
    fun visible(context: Context): List<QuickToggle> =
        order(context).mapNotNull { id -> all.firstOrNull { it.id == id } }
            .filterNot { isHidden(context, it.id) }

    fun moveToStart(context: Context, id: String) {
        val current = order(context).toMutableList()
        current.remove(id)
        current.add(0, id)
        setOrder(context, current)
    }

    fun moveToEnd(context: Context, id: String) {
        val current = order(context).toMutableList()
        current.remove(id)
        current.add(id)
        setOrder(context, current)
    }
}
