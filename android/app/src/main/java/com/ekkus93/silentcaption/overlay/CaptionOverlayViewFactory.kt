package com.ekkus93.silentcaption.overlay

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.TextView

object CaptionOverlayViewFactory {
    fun create(
        context: Context,
        text: String,
        touchListener: View.OnTouchListener,
    ): TextView =
        TextView(context).apply {
            this.text = text.ifBlank { EMPTY_CAPTION }
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(BACKGROUND_ALPHA, BACKGROUND_RED, BACKGROUND_GREEN, BACKGROUND_BLUE))
            textSize = FLOATING_TEXT_SIZE
            gravity = Gravity.CENTER_VERTICAL
            setPadding(HORIZONTAL_PADDING, VERTICAL_PADDING, HORIZONTAL_PADDING, VERTICAL_PADDING)
            contentDescription = "Live captions"
            textDirection = View.TEXT_DIRECTION_LOCALE
            setOnTouchListener(touchListener)
        }

    const val FLOATING_TEXT_SIZE = 22f
    const val COMPACT_TEXT_SIZE = 18f
    const val COMPACT_MAX_LINES = 2
    private const val EMPTY_CAPTION = "Captions will appear here"
    private const val BACKGROUND_ALPHA = 224
    private const val BACKGROUND_RED = 24
    private const val BACKGROUND_GREEN = 28
    private const val BACKGROUND_BLUE = 32
    private const val HORIZONTAL_PADDING = 24
    private const val VERTICAL_PADDING = 16
}
