package com.nubia.launcher.notification

import android.app.NotificationManager
import android.content.Context

/** Conta le notifiche attive per pacchetto (per i badge sulle icone). */
object NotificationBadgeHelper {

    /** Ritorna mappa pacchetto -> numero di notifiche attive. */
    fun refresh(context: Context): Map<String, Int> {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.activeNotifications
                .groupingBy { it.packageName }
                .mapValues { it.value.size }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
