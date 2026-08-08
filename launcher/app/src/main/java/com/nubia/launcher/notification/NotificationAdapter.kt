package com.nubia.launcher.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nubia.launcher.R

/** Elenco delle notifiche nel pannello. */
class NotificationAdapter(
    private val onClick: (NotifEntry) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.Holder>() {

    private val items = mutableListOf<NotifEntry>()

    fun submit(list: List<NotifEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        val icon = entry.smallIcon?.loadDrawable(holder.itemView.context)
        if (icon != null) {
            holder.icon.setImageDrawable(icon)
            holder.icon.visibility = View.VISIBLE
        } else {
            holder.icon.visibility = View.INVISIBLE
        }
        holder.title.text = entry.title ?: entry.appName
        holder.title.visibility = if (entry.title.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.text.text = entry.text.orEmpty()
        holder.text.visibility = if (entry.text.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.appName.text = entry.appName
        holder.itemView.setOnClickListener { onClick(entry) }
    }

    override fun getItemCount(): Int = items.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.notifIcon)
        val title: TextView = view.findViewById(R.id.notifTitle)
        val text: TextView = view.findViewById(R.id.notifText)
        val appName: TextView = view.findViewById(R.id.notifApp)
    }
}
