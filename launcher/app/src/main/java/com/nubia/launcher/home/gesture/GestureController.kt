package com.nubia.launcher.home.gesture

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Riconoscimento gesti a livello di schermata home.
 * Base per aggiungere swipe/doppio tap/pinch personalizzabili.
 */
class GestureController(context: Context) {

    var onSwipeUp: (() -> Unit)? = null
    var onSwipeDown: (() -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null
    var onLongPress: ((View?) -> Unit)? = null

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            val threshold = SWIPE_DISTANCE * context.resources.displayMetrics.density
            when {
                velocityY < -SWIPE_VELOCITY && abs(dy) > abs(dx) && abs(dy) > threshold ->
                    onSwipeUp?.invoke()
                velocityY > SWIPE_VELOCITY && abs(dy) > abs(dx) && abs(dy) > threshold ->
                    onSwipeDown?.invoke()
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTap?.invoke()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onLongPress?.invoke(null)
        }
    })

    /**
     * Inoltra un evento al riconoscitore. Va chiamato dall'Activity in
     * [android.app.Activity.dispatchTouchEvent], così lo swipe viene
     * visto anche quando il tocco parte sopra icone o liste.
     */
    fun dispatch(event: MotionEvent) {
        detector.onTouchEvent(event)
    }

    companion object {
        private const val SWIPE_VELOCITY = 800f
        private const val SWIPE_DISTANCE = 40f
    }
}
