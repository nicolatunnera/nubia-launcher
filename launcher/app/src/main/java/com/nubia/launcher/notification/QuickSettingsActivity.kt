package com.nubia.launcher.notification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.nubia.launcher.R
import com.nubia.launcher.databinding.ActivityQuickSettingsBinding

/**
 * Ordina e personalizza gli interruttori rapidi del pannello.
 * Trascina l'icona a sinistra per riordinare; lo switch mostra/nasconde.
 */
class QuickSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuickSettingsBinding
    private lateinit var adapter: RowAdapter
    private lateinit var touchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setTitle(R.string.settings_qs_order)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.qsList.layoutManager = LinearLayoutManager(this)

        adapter = RowAdapter(
            QuickSettingsStore.order(this).mapNotNull { id ->
                QuickSettingsStore.all.firstOrNull { it.id == id }
            },
            onVisibleChange = { toggle, visible ->
                QuickSettingsStore.setHidden(this, toggle.id, !visible)
            },
            onStartDrag = { holder -> touchHelper.startDrag(holder) }
        )
        binding.qsList.adapter = adapter

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int = makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                val order = adapter.rows.map { it.id }
                QuickSettingsStore.setOrder(this@QuickSettingsActivity, order)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        touchHelper.attachToRecyclerView(binding.qsList)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private class RowAdapter(
        initial: List<QuickToggle>,
        private val onVisibleChange: (QuickToggle, Boolean) -> Unit,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
    ) : RecyclerView.Adapter<RowAdapter.Holder>() {

        val rows = initial.toMutableList()

        fun move(from: Int, to: Int) {
            if (from in rows.indices && to in rows.indices && from != to) {
                rows.add(to, rows.removeAt(from))
                notifyItemMoved(from, to)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_qs_row, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val toggle = rows[position]
            holder.label.setText(toggle.labelRes)
            holder.icon.setImageResource(toggle.iconRes)
            val ctx = holder.itemView.context
            val hidden = QuickSettingsStore.isHidden(ctx, toggle.id)
            holder.switch.isChecked = !hidden
            holder.switch.setOnCheckedChangeListener { _, checked ->
                onVisibleChange(toggle, checked)
            }
            holder.drag.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                false
            }
        }

        override fun getItemCount(): Int = rows.size

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.qsRowIcon)
            val label: TextView = view.findViewById(R.id.qsRowLabel)
            val switch: SwitchMaterial = view.findViewById(R.id.qsRowSwitch)
            val drag: View = view.findViewById(R.id.qsRowDrag)
        }
    }
}
