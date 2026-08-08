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
import com.nubia.launcher.home.workspace.IconShape
import com.nubia.launcher.model.AppInfo

/** Barra inferiore (dock) con app rapide. */
class DockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onItemClick: ((AppInfo) -> Unit)? = null
    var onItemLongClick: ((AppInfo, View) -> Boolean)? = null
    var onDrawerClick: (() -> Unit)? = null
    var onDockDrop: ((String) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setOnDragListener { _, event -> handleDrag(event) }
    }

    /** Riceve il rilascio di un'app trascinata dalla home per aggiungerla al dock. */
    private fun handleDrag(event: android.view.DragEvent): Boolean {
        when (event.action) {
            android.view.DragEvent.ACTION_DRAG_STARTED -> {
                val d = event.localState
                return d is com.nubia.launcher.home.workspace.DragData && d.packageName.isNotEmpty()
            }
            android.view.DragEvent.ACTION_DROP -> {
                val d = event.localState as? com.nubia.launcher.home.workspace.DragData ?: return false
                if (d.packageName.isEmpty()) return false
                onDockDrop?.invoke(d.packageName)
                return true
            }
            android.view.DragEvent.ACTION_DRAG_ENDED -> Unit
        }
        return true
    }

    fun setApps(
        apps: List<AppInfo>,
        iconSizeDp: Int,
        showLabels: Boolean,
        iconShape: Int = 0,
        maxItems: Int = MAX_ITEMS
    ) {
        removeAllViews()
        apps.take(maxItems).forEach { app ->
            addView(createItem(app, iconSizeDp, showLabels, iconShape))
        }
        addView(createDrawerButton(iconSizeDp, iconShape))
    }

    fun applyIconSettings(iconSizeDp: Int, showLabels: Boolean, iconShape: Int = 0) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.tag == TAG_DRAWER) continue
            val icon = child.findViewById<ImageView>(R.id.dockIcon) ?: continue
            val label = child.findViewById<TextView>(R.id.dockLabel)
            val px = (context.resources.displayMetrics.density * iconSizeDp).toInt()
            icon.layoutParams = (icon.layoutParams as ViewGroup.LayoutParams).apply {
                width = px
                height = px
            }
            IconShape.apply(icon, iconShape, px)
            label?.visibility = if (showLabels) View.VISIBLE else View.GONE
            child.layoutParams = child.layoutParams.apply {
                height = px + if (showLabels) dp(18) else 0
            }
        }
    }

    private fun createItem(app: AppInfo, iconSizeDp: Int, showLabels: Boolean, iconShape: Int): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_dock, this, false)
        val icon = view.findViewById<ImageView>(R.id.dockIcon)
        val label = view.findViewById<TextView>(R.id.dockLabel)

        icon.setImageDrawable(app.icon)
        val px = (context.resources.displayMetrics.density * iconSizeDp).toInt()
        icon.layoutParams = (icon.layoutParams as ViewGroup.LayoutParams).apply {
            width = px
            height = px
        }
        IconShape.apply(icon, iconShape, px)

        label.text = app.label
        label.visibility = if (showLabels) View.VISIBLE else View.GONE

        view.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            px + if (showLabels) dp(18) else 0
        )
        view.setOnClickListener { onItemClick?.invoke(app) }
        view.setOnLongClickListener { onItemLongClick?.invoke(app, it) ?: true }
        view.foreground = context.getDrawable(R.drawable.bg_cell)
        return view
    }

    private fun createDrawerButton(iconSizeDp: Int, iconShape: Int): View {
        val px = (context.resources.displayMetrics.density * iconSizeDp).toInt()
        val size = (context.resources.displayMetrics.density * (iconSizeDp - 10)).toInt()
        return ImageView(context).apply {
            tag = TAG_DRAWER
            setImageResource(R.drawable.ic_drawer)
            contentDescription = "Tutte le app"
            layoutParams = LayoutParams(px, px)
            setPadding(size / 4, size / 4, size / 4, size / 4)
            foreground = context.getDrawable(R.drawable.bg_cell)
            IconShape.apply(this, iconShape, px)
            setOnClickListener { onDrawerClick?.invoke() }
        }
    }

    private fun dp(value: Int): Int = (context.resources.displayMetrics.density * value).toInt()

    companion object {
        private const val MAX_ITEMS = 5
        private const val TAG_DRAWER = "dock_drawer"

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
