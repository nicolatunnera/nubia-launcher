package com.nubia.launcher.home.dock

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nubia.launcher.R
import com.nubia.launcher.model.AppInfo

/** Barra inferiore (dock) con app rapide. */
class DockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onItemClick: ((AppInfo) -> Unit)? = null
    var onItemLongClick: ((AppInfo, View) -> Boolean)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun setApps(apps: List<AppInfo>, iconSizeDp: Int, showLabels: Boolean) {
        removeAllViews()
        apps.take(MAX_ITEMS).forEach { app ->
            addView(createItem(app, iconSizeDp, showLabels))
        }
    }

    fun applyIconSettings(iconSizeDp: Int, showLabels: Boolean) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val icon = child.findViewById<ImageView>(R.id.dockIcon) ?: continue
            val label = child.findViewById<TextView>(R.id.dockLabel)
            val px = (context.resources.displayMetrics.density * iconSizeDp).toInt()
            icon.layoutParams = (icon.layoutParams as ViewGroup.LayoutParams).apply {
                width = px
                height = px
            }
            label?.visibility = if (showLabels) View.VISIBLE else View.GONE
            child.layoutParams = child.layoutParams.apply {
                height = px + if (showLabels) dp(18) else 0
            }
        }
    }

    private fun createItem(app: AppInfo, iconSizeDp: Int, showLabels: Boolean): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_dock, this, false)
        val icon = view.findViewById<ImageView>(R.id.dockIcon)
        val label = view.findViewById<TextView>(R.id.dockLabel)

        icon.setImageDrawable(app.icon)
        val px = (context.resources.displayMetrics.density * iconSizeDp).toInt()
        icon.layoutParams = (icon.layoutParams as ViewGroup.LayoutParams).apply {
            width = px
            height = px
        }

        label.text = app.label
        label.visibility = if (showLabels) View.VISIBLE else View.GONE

        view.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            px + if (showLabels) dp(18) else 0
        )
        view.setOnClickListener { onItemClick?.invoke(app) }
        view.setOnLongClickListener { onItemLongClick?.invoke(app, it) ?: true }
        view.background = context.getDrawable(R.drawable.bg_cell)
        return view
    }

    private fun dp(value: Int): Int = (context.resources.displayMetrics.density * value).toInt()

    companion object {
        private const val MAX_ITEMS = 5

        private val DEFAULT_PACKAGES = listOf(
            "com.android.dialer", "com.google.android.dialer",
            "com.android.messaging", "com.google.android.apps.messaging",
            "com.android.contacts", "com.google.android.contacts",
            "com.android.chrome", "com.android.browser", "org.mozilla.firefox",
            "com.android.vending"
        )

        /** Sceglie le app di default (telefono, messaggi, ecc.) con fallback alle prime. */
        fun pickDockApps(apps: List<AppInfo>, max: Int = MAX_ITEMS): List<AppInfo> {
            val preferred = DEFAULT_PACKAGES.mapNotNull { pkg ->
                apps.firstOrNull { it.packageName == pkg }
            }
            return (preferred + apps).distinctBy { it.key }.take(max)
        }
    }
}
