package com.nubia.launcher.notification

import android.graphics.drawable.Icon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Notifica mostrata nel pannello (copie non Parcelable dei dati di sistema). */
data class NotifEntry(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val smallIcon: Icon?,
    val timestamp: Long
)

/**
 * Memoria condivisa tra [NotificationListener] (che popola) e il pannello
 * in-app (che consuma), mantenendo l'ordine di arrivo.
 */
object NotificationRepository {

    private val _notifications = MutableStateFlow<List<NotifEntry>>(emptyList())
    val notifications: StateFlow<List<NotifEntry>> = _notifications.asStateFlow()

    private val entries = LinkedHashMap<String, NotifEntry>()

    fun onPosted(entry: NotifEntry) {
        entries[entry.key] = entry
        _notifications.value = entries.values.toList()
    }

    fun onRemoved(key: String) {
        entries.remove(key)
        _notifications.value = entries.values.toList()
    }

    fun clearAll() {
        entries.clear()
        _notifications.value = emptyList()
    }
}
