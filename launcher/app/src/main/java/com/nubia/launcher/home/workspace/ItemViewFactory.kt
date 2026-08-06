package com.nubia.launcher.home.workspace

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.nubia.launcher.R
import com.nubia.launcher.model.AppInfo

/** Costruisce le viste delle celle (icona + nome) per home e dock. */
object ItemViewFactory {

    fun createAppCell(
        context: Context,
        app: AppInfo,
        config: HomeScreenConfig,
        onClick: (View) -> Unit,
        onLongClick: (View) -> Boolean
    ): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_app_cell, null)
        val icon = view.findViewById<ImageView>(R.id.appIcon)
        val label = view.findViewById<TextView>(R.id.appLabel)
        val density = context.resources.displayMetrics.density

        icon.setImageDrawable(app.icon)
        val px = (density * config.iconSizeDp).toInt()
        icon.layoutParams = (icon.layoutParams as ViewGroup.LayoutParams).apply {
            width = px
            height = px
        }

        label.text = app.label
        label.textSize = config.labelSizeSp.toFloat()
        label.visibility = if (config.showLabels) View.VISIBLE else View.GONE

        view.setOnClickListener(onClick)
        view.setOnLongClickListener { onLongClick(it) }
        view.background = context.getDrawable(R.drawable.bg_cell)
        return view
    }
}
