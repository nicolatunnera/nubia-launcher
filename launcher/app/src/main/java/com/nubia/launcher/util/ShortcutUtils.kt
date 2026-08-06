package com.nubia.launcher.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.nubia.launcher.model.AppInfo

/** Creazione di scorciatoie dinamiche (ShortcutManager). */
object ShortcutUtils {

    fun createAppShortcut(context: Context, app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        val shortcut = ShortcutInfoCompat.Builder(context, app.key)
            .setIntent(intent)
            .setShortLabel(app.label)
            .setLongLabel(app.label)
            .setIcon(IconCompat.createWithAdaptiveBitmap(toBitmap(app.icon, 96.dp(context))))
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    private fun toBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}
