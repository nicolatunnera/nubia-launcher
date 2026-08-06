package com.nubia.launcher.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.nubia.launcher.model.AppInfo
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Carica e pubblica la lista delle app avviabili, aggiornata in background. */
class AppManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val collator: Collator = Collator.getInstance(Locale.getDefault())

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    suspend fun load() = withContext(Dispatchers.Default) {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val loaded = packageManager
            .queryIntentActivities(launchIntent, 0)
            .asSequence()
            .filter { it.activityInfo != null }
            .filterNot { it.activityInfo.packageName == context.packageName }
            .map { resolve ->
                AppInfo(
                    component = ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name),
                    label = resolve.loadLabel(packageManager).toString(),
                    icon = resolve.loadIcon(packageManager)
                )
            }
            .distinctBy { it.key }
            .sortedWith(compareBy(collator) { it.label })
            .toList()
        _apps.value = loaded
    }

    fun launch(app: AppInfo) {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // L'app non è più disponibile: ignorato nello scheletro.
        }
    }
}
