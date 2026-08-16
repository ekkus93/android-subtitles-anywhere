package com.ekkus93.silentcaption.overlay

import android.graphics.Point
import android.os.Build
import android.view.WindowManager

internal object OverlayBoundsProvider {
    @Suppress("DEPRECATION")
    fun current(windowManager: WindowManager): OverlayBounds =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            OverlayBounds(bounds.width(), bounds.height())
        } else {
            val size = Point()
            windowManager.defaultDisplay.getRealSize(size)
            OverlayBounds(size.x, size.y)
        }
}
