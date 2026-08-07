package com.nubia.launcher.home.workspace

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * Griglia (righe x colonne) che dispone i figli in celle fisse.
 * Ogni figlio porta un [GridLayoutParams] con l'indice di cella.
 */
class CellLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    var columns: Int = 4
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    var rows: Int = 6
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    class GridLayoutParams : LayoutParams {
        var index: Int = 0

        constructor() : super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        constructor(index: Int) : this() {
            this.index = index
        }

        constructor(source: LayoutParams) : super(source)
    }

    override fun generateDefaultLayoutParams(): LayoutParams = GridLayoutParams()

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = GridLayoutParams()

    override fun generateLayoutParams(p: LayoutParams): LayoutParams =
        if (p is GridLayoutParams) GridLayoutParams(p) else GridLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams): Boolean = p is GridLayoutParams

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cellW = w / columns.coerceAtLeast(1)
        val cellH = h / rows.coerceAtLeast(1)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(
                MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cellW = (r - l) / columns.coerceAtLeast(1)
        val cellH = (b - t) / rows.coerceAtLeast(1)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as GridLayoutParams
            val col = lp.index % columns.coerceAtLeast(1)
            val row = lp.index / columns.coerceAtLeast(1)
            val left = col * cellW
            val top = row * cellH
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
        }
    }
}
