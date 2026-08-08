package com.nubia.launcher.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Ascolta le notifiche di sistema (richiede l'accesso dalle Impostazioni)
 * e le inoltra al [NotificationRepository] per il pannello in-app.
 */
class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras
        NotificationRepository.onPosted(
            NotifEntry(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = appName(sbn.packageName),
                title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                smallIcon = sbn.notification?.smallIcon,
                timestamp = sbn.postTime
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationRepository.onRemoved(sbn.key)
    }

    private fun appName(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }
}
