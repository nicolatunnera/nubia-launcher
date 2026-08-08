package com.nubia.launcher.notification

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nubia.launcher.R

/** Griglia degli interruttori rapidi del pannello notifiche. */
class QuickSettingsAdapter(
    private val context: Context,
    private val onToggle: (QuickToggle) -> Unit,
    private val onLongPress: (QuickToggle, View) -> Unit
) : RecyclerView.Adapter<QuickSettingsAdapter.Holder>() {

    private val items = mutableListOf<QuickToggle>()

    fun submit(list: List<QuickToggle>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_toggle, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val toggle = items[position]
        holder.icon.setImageResource(toggle.iconRes)
        holder.label.setText(toggle.labelRes)
        val on = QuickToggleActions.isOn(context, toggle.id)
        val accent = accentColor(context)
        holder.root.backgroundTintList = if (on) {
            ColorStateList.valueOf(accent)
        } else {
            null
        }
        holder.icon.imageTintList = ColorStateList.valueOf(
            if (on) Color.WHITE else accent
        )
        holder.root.setOnClickListener { onToggle(toggle) }
        holder.root.setOnLongClickListener {
            onLongPress(toggle, it)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    private fun accentColor(context: Context): Int {
        val ta = context.theme
            .obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.colorPrimary))
        val color = ta.getColor(0, ContextCompat.getColor(context, R.color.accent_default))
        ta.recycle()
        return color
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.qsTile)
        val icon: ImageView = view.findViewById(R.id.qsIcon)
        val label: TextView = view.findViewById(R.id.qsLabel)
    }
}
