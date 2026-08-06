package com.nubia.launcher.home.workspace

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.nubia.launcher.model.HomeItem

/**
 * Area della home a più pagine (swipe orizzontale).
 * Ogni pagina è una [CellLayout] popolata con gli elementi di [HomeItem].
 */
class Workspace @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewPager2(context, attrs) {

    var onItemClick: ((HomeItem) -> Unit)? = null
    var onItemLongClick: ((HomeItem, View) -> Boolean)? = null

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

    val pageCount: Int get() = pagerAdapter.itemCount

    private val pagerAdapter = PagerAdapter()

    init {
        orientation = ORIENTATION_HORIZONTAL
        offscreenPageLimit = 1
        this.adapter = pagerAdapter
    }

    /** Ritorna la pagina corrente (utile per il posizionamento widget). */
    fun currentPageView(): CellLayout? {
        val recycler = getChildAt(0) as? RecyclerView ?: return null
        for (i in 0 until recycler.childCount) {
            val child = recycler.getChildAt(i)
            val pos = recycler.getChildAdapterPosition(child)
            if (pos == currentItem && child is CellLayout) return child
        }
        return null
    }

    private inner class PagerAdapter : RecyclerView.Adapter<PagerAdapter.PageHolder>() {

        init {
            setHasStableIds(true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val cell = CellLayout(parent.context)
            cell.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            return PageHolder(cell)
        }

        override fun getItemId(position: Int): Long = position.toLong()

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val cell = holder.cell
            cell.columns = config.columns
            cell.rows = config.rows
            cell.removeAllViews()
            items.getOrElse(position) { emptyList() }.forEachIndexed { index, item ->
                val lp = CellLayout.GridLayoutParams(index)
                when (item) {
                    is HomeItem.App -> {
                        val v = ItemViewFactory.createAppCell(
                            context = cell.context,
                            app = item.appInfo,
                            config = config,
                            onClick = { onItemClick?.invoke(item) },
                            onLongClick = { onItemLongClick?.invoke(item, it) }
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
}
