package com.ekkus93.silentcaption.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class CaptionOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: OverlayPreferences
    private var captionView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var captionText = CaptionOverlayText()
    private var mode = OverlayMode.Floating

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        preferences = OverlayPreferences(this)
        OverlayNotification.createChannel(this)
        startForeground(OverlayNotification.ID, OverlayNotification.build(this))
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_PARTIAL ->
                updateText(captionText.replacePartial(intent.getStringExtra(EXTRA_TEXT).orEmpty()))
            ACTION_FINAL ->
                updateText(captionText.commit(intent.getStringExtra(EXTRA_TEXT).orEmpty()))
            else -> showOverlay(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        captionView?.let(windowManager::removeView)
        captionView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay(intent: Intent?) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        mode =
            if (intent?.getStringExtra(EXTRA_MODE) == MODE_COMPACT) {
                OverlayMode.Compact
            } else {
                OverlayMode.Floating
            }
        if (captionView != null) {
            applyMode()
            return
        }
        val geometry = recoveredGeometry()
        val params = overlayLayoutParams(geometry)
        val dragListener = OverlayDragListener(windowManager, { layoutParams }, ::persistRecoveredGeometry)
        val view = CaptionOverlayViewFactory.create(this, captionText.visible, dragListener)
        layoutParams = params
        captionView = view
        windowManager.addView(view, params)
        applyMode()
    }

    private fun applyMode() {
        val view = captionView ?: return
        view.maxLines =
            if (mode == OverlayMode.Compact) CaptionOverlayViewFactory.COMPACT_MAX_LINES else Int.MAX_VALUE
        view.textSize =
            if (mode == OverlayMode.Compact) {
                CaptionOverlayViewFactory.COMPACT_TEXT_SIZE
            } else {
                CaptionOverlayViewFactory.FLOATING_TEXT_SIZE
            }
        val params = layoutParams ?: return
        params.height =
            if (mode == OverlayMode.Compact) WindowManager.LayoutParams.WRAP_CONTENT else params.height
        windowManager.updateViewLayout(view, params)
    }

    private fun updateText(state: CaptionOverlayText) {
        captionText = state
        captionView?.text = state.visible
    }

    private fun recoveredGeometry(): OverlayGeometry {
        val bounds = currentOverlayBounds()
        return OverlayGeometryPolicy.clamp(
            preferences.load(resources.configuration),
            bounds,
        )
    }

    @Suppress("DEPRECATION")
    private fun currentOverlayBounds(): OverlayBounds =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            OverlayBounds(bounds.width(), bounds.height())
        } else {
            val size = Point()
            windowManager.defaultDisplay.getRealSize(size)
            OverlayBounds(size.x, size.y)
        }

    private fun overlayLayoutParams(geometry: OverlayGeometry) =
        WindowManager
            .LayoutParams(
                geometry.width,
                geometry.height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = geometry.x
                y = geometry.y
            }

    private fun persistRecoveredGeometry(params: WindowManager.LayoutParams) {
        val bounds = currentOverlayBounds()
        val geometry =
            OverlayGeometryPolicy.clamp(
                OverlayGeometry(
                    params.x,
                    params.y,
                    params.width,
                    captionView?.height ?: params.height,
                ),
                bounds,
            )
        params.x = geometry.x
        params.y = geometry.y
        preferences.save(resources.configuration, geometry)
        captionView?.let { windowManager.updateViewLayout(it, params) }
    }

    companion object {
        const val ACTION_SHOW = "com.ekkus93.silentcaption.overlay.SHOW"
        const val ACTION_STOP = "com.ekkus93.silentcaption.overlay.STOP"
        const val ACTION_PARTIAL = "com.ekkus93.silentcaption.overlay.PARTIAL"
        const val ACTION_FINAL = "com.ekkus93.silentcaption.overlay.FINAL"
        const val EXTRA_TEXT = "text"
        const val EXTRA_MODE = "mode"
        const val MODE_COMPACT = "compact"
    }
}
