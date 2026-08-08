package com.nubia.launcher.home.workspace

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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

    /** Pagina a cui appartiene questa griglia (usata dal drag & drop). */
    var pageIndex: Int = 0

    private val cellW get() = (right - left) / columns.coerceAtLeast(1)
    private val cellH get() = (bottom - top) / rows.coerceAtLeast(1)

    /** Ritorna l'indice di cella (0..columns*rows) dalla posizione del tocco. */
    fun cellIndexAt(x: Float, y: Float): Int {
        val col = ((x / cellW).toInt()).coerceIn(0, columns - 1)
        val row = ((y / cellH).toInt()).coerceIn(0, rows - 1)
        return row * columns + col
    }

    /** Evidenzia la griglia durante il drag (zona di rilascio). */
    fun setHighlight(on: Boolean) {
        if (on) {
            val ta = context.theme
                .obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.colorPrimary))
            val accent = ta.getColor(0, Color.WHITE)
            ta.recycle()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = resources.displayMetrics.density * 16f
                setColor((accent and 0x00FFFFFF) or 0x33000000.toInt())
            }
        } else {
            background = null
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
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(
                MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val colCount = columns.coerceAtLeast(1)
        var maxUsedRow = -1
        for (i in 0 until childCount) {
            val lp = getChildAt(i).layoutParams as GridLayoutParams
            maxUsedRow = maxOf(maxUsedRow, lp.index / colCount)
        }
        val usedRows = (maxUsedRow + 1).coerceAtLeast(1)
        val topOffset = ((height - usedRows * cellH) / 2).coerceAtLeast(0)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as GridLayoutParams
            val col = lp.index % colCount
            val row = lp.index / colCount
            val left = col * cellW
            val top = topOffset + row * cellH
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
        }
    }
}
