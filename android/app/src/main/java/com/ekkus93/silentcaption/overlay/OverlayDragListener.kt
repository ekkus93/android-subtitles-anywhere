package com.ekkus93.silentcaption.overlay

import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt

class OverlayDragListener(
    private val windowManager: WindowManager,
    private val paramsProvider: () -> WindowManager.LayoutParams?,
    private val onDragFinished: (WindowManager.LayoutParams) -> Unit,
) : View.OnTouchListener {
    private var startX = 0
    private var startY = 0
    private var touchX = 0f
    private var touchY = 0f

    override fun onTouch(
        view: View,
        event: MotionEvent,
    ): Boolean {
        val params = paramsProvider()
        return params != null && handleTouch(view, event, params)
    }

    private fun handleTouch(
        view: View,
        event: MotionEvent,
        params: WindowManager.LayoutParams,
    ): Boolean =
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = params.x
                startY = params.y
                touchX = event.rawX
                touchY = event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = startX + (event.rawX - touchX).roundToInt()
                params.y = startY + (event.rawY - touchY).roundToInt()
                windowManager.updateViewLayout(view, params)
                true
            }
            MotionEvent.ACTION_UP -> {
                onDragFinished(params)
                true
            }
            else -> false
        }
}
