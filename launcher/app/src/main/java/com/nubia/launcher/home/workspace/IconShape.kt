package com.nubia.launcher.home.workspace

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView

/**
 * Applica una forma uniforme alle icone delle app (come Pixel/Nova):
 * le mascherà tagliando l'immagine al contorno impostato.
 */
object IconShape {

    fun apply(view: ImageView, iconShape: Int, sizePx: Int, fill: Int = Color.WHITE) {
        when (iconShape) {
            2 -> { // Nessuna
                view.clipToOutline = false
                view.background = null
            }
            1 -> { // Cerchio
                val bg = GradientDrawable().apply {
                    setShape(GradientDrawable.OVAL)
                    setColor(fill)
                }
                view.background = bg
                view.outlineProvider = ViewOutlineProvider.BACKGROUND
                view.clipToOutline = true
            }
            else -> { // Arrotondata (squircle)
                val radius = sizePx * 0.22f
                val bg = GradientDrawable().apply {
                    setShape(GradientDrawable.RECTANGLE)
                    setCornerRadius(radius)
                    setColor(fill)
                }
                view.background = bg
                view.outlineProvider = ViewOutlineProvider.BACKGROUND
                view.clipToOutline = true
            }
        }
    }
}
