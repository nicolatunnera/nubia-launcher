package com.nubia.launcher.home.workspace

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.nubia.launcher.model.HomeItem

/**
 * Area della home a più pagine (swipe orizzontale).
 * Ogni pagina è una [CellLayout] popolata con gli elementi di [HomeItem].
 * Wrappa un [ViewPager2] interno perché la classe è final.
 */
class Workspace @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

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

    var currentItem: Int
        get() = pager.currentItem
        set(value) = pager.setCurrentItem(value, false)

    private val pager: ViewPager2
    private val pagerAdapter = PagerAdapter()

    init {
        pager = ViewPager2(context).apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 1
            adapter = pagerAdapter
        }
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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
                            onLongClick = { onItemLongClick?.invoke(item, it) ?: true }
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
