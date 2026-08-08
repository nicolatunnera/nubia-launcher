package com.nubia.launcher.home.drawer

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nubia.launcher.R
import com.nubia.launcher.model.AppInfo

/** Griglia di app del cassetto, con filtro di ricerca, badge e long press. */
class AllAppsAdapter(
    private val iconSizeDp: Int,
    private val showLabels: Boolean
) : RecyclerView.Adapter<AllAppsAdapter.Holder>() {

    var onItemClick: ((AppInfo) -> Unit)? = null
    var onItemLongClick: ((AppInfo, View) -> Boolean)? = null

    var badges: Map<String, Int> = emptyMap()

    private var all: List<AppInfo> = emptyList()
    private var filtered: List<AppInfo> = emptyList()

    fun submit(list: List<AppInfo>) {
        all = list
        filtered = list
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val q = query.trim()
        filtered = if (q.isEmpty()) {
            all
        } else {
            all.filter {
                it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_cell, parent, false)
        val cellHeight = (parent.context.resources.displayMetrics.density * (iconSizeDp + 30)).toInt()
        view.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            cellHeight
        )
        return Holder(view)
    }

    override fun getItemCount(): Int = filtered.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(filtered[position])
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val icon: ImageView = itemView.findViewById(R.id.appIcon)
        private val label: TextView = itemView.findViewById(R.id.appLabel)
        private val badge: TextView = itemView.findViewById(R.id.appBadge)

        init {
            val ta = itemView.context.theme
                .obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
            label.setTextColor(ta.getColor(0, Color.BLACK))
            ta.recycle()
            label.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }

        fun bind(app: AppInfo) {
            icon.setImageDrawable(app.icon)
            val px = (itemView.context.resources.displayMetrics.density * iconSizeDp).toInt()
            icon.layoutParams = (icon.layoutParams as ViewGroup.LayoutParams).apply {
                width = px
                height = px
            }
            label.text = app.label
            label.visibility = if (showLabels) View.VISIBLE else View.GONE

            val count = badges[app.packageName] ?: 0
            if (count > 0) {
                badge.text = if (count > 99) "99+" else count.toString()
                badge.visibility = View.VISIBLE
            } else {
                badge.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClick?.invoke(app) }
            itemView.setOnLongClickListener {
                onItemLongClick?.invoke(app, itemView) ?: true
            }
            itemView.background = itemView.context.getDrawable(R.drawable.bg_cell)
        }
    }
}
