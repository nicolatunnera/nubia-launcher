package com.nubia.launcher.home.workspace

import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.nubia.launcher.R
import com.nubia.launcher.model.HomeItem

/** Payload trasportato durante il trascinamento di un elemento della home. */
data class DragData(val sourceIndex: Int, val packageName: String = "")

/**
 * Area della home a più pagine (swipe orizzontale).
 * Ogni pagina è una [CellLayout] popolata con gli elementi di [HomeItem].
 * Supporta drag & drop per riordinare, creare cartelle e rimuovere elementi.
 */
class Workspace @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onItemClick: ((HomeItem) -> Unit)? = null

    /** Invocato al long-press di un elemento (ritorna false per impedire il drag). */
    var onItemDragStart: ((Int, HomeItem, View) -> Boolean)? = null

    /** Invocato al rilascio su una cella: (indiceSorgente, indiceDestinazione). */
    var onItemDrop: ((Int, Int) -> Unit)? = null

    /** Invocato al rilascio nella zona "Rimuovi". */
    var onItemRemove: ((Int) -> Unit)? = null

    /** Invocato se il drag termina senza un rilascio valido (es. per il menu). */
    var onItemDropFailed: ((Int) -> Unit)? = null

    var config: HomeScreenConfig = HomeScreenConfig(4, 6, 52, 11, true)
        set(value) {
            field = value
            pagerAdapter.notifyDataSetChanged()
        }

    var items: List<List<HomeItem>> = emptyList()
        set(value) {
            field = value
            pagerAdapter.notifyDataSetChanged()
        }

    /** Contatore notifiche per pacchetto (per i badge sulle icone). */
    var badges: Map<String, Int> = emptyMap()
        set(value) {
            field = value
            pagerAdapter.notifyDataSetChanged()
        }

    val pageCount: Int get() = pagerAdapter.itemCount

    var currentItem: Int
        get() = pager.currentItem
        set(value) = pager.setCurrentItem(value, false)

    private val pager: ViewPager2
    private val pagerAdapter = PagerAdapter()
    private lateinit var removeZone: TextView

    init {
        pager = ViewPager2(context).apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 1
            adapter = pagerAdapter
        }
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        removeZone = TextView(context).apply {
            text = context.getString(R.string.menu_remove)
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            background = context.getDrawable(R.drawable.bg_remove)
            visibility = View.GONE
            setOnDragListener { _, event -> handleRemoveDrag(event) }
        }
        removeZone.layoutParams = LayoutParams(
            dp(96), dp(38), Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { topMargin = dp(6) }
        addView(removeZone)
    }

    fun registerOnPageChangeCallback(callback: ViewPager2.OnPageChangeCallback) {
        pager.registerOnPageChangeCallback(callback)
    }

    /** Ritorna la pagina corrente (utile per il posizionamento widget). */
    fun currentPageView(): CellLayout? {
        val recycler = pager.getChildAt(0) as? RecyclerView ?: return null
        for (i in 0 until recycler.childCount) {
            val child = recycler.getChildAt(i)
            val pos = recycler.getChildAdapterPosition(child)
            if (pos == pager.currentItem && child is CellLayout) return child
        }
        return null
    }

    private fun isHomeDrag(event: DragEvent): Boolean =
        event.clipDescription != null && event.clipDescription.hasMimeType(DRAG_MIME)

    private fun handleRemoveDrag(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                if (isHomeDrag(event)) {
                    removeZone.visibility = View.VISIBLE
                    removeZone.alpha = 0.65f
                    return true
                }
                return false
            }
            DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION -> removeZone.alpha = 1f
            DragEvent.ACTION_DRAG_EXITED -> removeZone.alpha = 0.65f
            DragEvent.ACTION_DROP -> {
                val data = event.localState as? DragData
                return if (data != null) {
                    onItemRemove?.invoke(data.sourceIndex)
                    true
                } else {
                    false
                }
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                removeZone.visibility = View.GONE
                val data = event.localState as? DragData
                if (!event.result && data != null) {
                    onItemDropFailed?.invoke(data.sourceIndex)
                }
            }
        }
        return true
    }

    private fun handleCellDrag(event: DragEvent, cell: CellLayout, page: Int): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return isHomeDrag(event)
            DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION -> cell.setHighlight(true)
            DragEvent.ACTION_DRAG_EXITED -> cell.setHighlight(false)
            DragEvent.ACTION_DROP -> {
                cell.setHighlight(false)
                val data = event.localState as? DragData ?: return false
                val cellIndex = cell.cellIndexAt(event.x, event.y)
                val globalIndex = page * config.columns * config.rows + cellIndex
                onItemDrop?.invoke(data.sourceIndex, globalIndex)
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> cell.setHighlight(false)
        }
        return true
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()

    private inner class PagerAdapter : RecyclerView.Adapter<PagerAdapter.PageHolder>() {

        init {
            setHasStableIds(true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val cell = CellLayout(parent.context)
            cell.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            cell.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            return PageHolder(cell)
        }

        override fun getItemId(position: Int): Long = position.toLong()

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val cell = holder.cell
            cell.columns = config.columns
            cell.rows = config.rows
            cell.pageIndex = position
            cell.setOnDragListener { _, event -> handleCellDrag(event, cell, position) }
            cell.removeAllViews()
            val perPage = config.columns * config.rows
            items.getOrElse(position) { emptyList() }.forEachIndexed { index, item ->
                val lp = CellLayout.GridLayoutParams(index)
                val globalIndex = position * perPage + index
                when (item) {
                    is HomeItem.App -> {
                        val v = ItemViewFactory.createAppCell(
                            context = cell.context,
                            app = item.appInfo,
                            config = config,
                            onClick = { onItemClick?.invoke(item) },
                            onLongClick = { onItemDragStart?.invoke(globalIndex, item, it) ?: false },
                            badgeCount = badges[item.appInfo.packageName] ?: 0
                        )
                        cell.addView(v, lp)
                    }
                    is HomeItem.Folder -> {
                        val badge = item.apps.sumOf { badges[it.packageName] ?: 0 }
                        val v = ItemViewFactory.createFolderCell(
                            context = cell.context,
                            name = item.name,
                            config = config,
                            badgeCount = badge,
                            onClick = { onItemClick?.invoke(item) },
                            onLongClick = { onItemDragStart?.invoke(globalIndex, item, it) ?: false }
                        )
                        cell.addView(v, lp)
                    }
                    is HomeItem.Widget -> {
                        val hostView = item.hostView
                        (hostView.parent as? ViewGroup)?.removeView(hostView)
                        cell.addView(hostView, lp)
                    }
                }
            }
        }

        override fun getItemCount(): Int = items.size

        inner class PageHolder(val cell: CellLayout) : RecyclerView.ViewHolder(cell)
    }

    companion object {
        const val DRAG_MIME = "application/x-nubia-launcher-item"
    }
}
