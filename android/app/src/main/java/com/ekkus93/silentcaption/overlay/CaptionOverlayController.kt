package com.ekkus93.silentcaption.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import com.ekkus93.silentcaption.ui.home.CaptionDisplayMode

object CaptionOverlayController {
    fun applyDisplayMode(
        context: Context,
        mode: CaptionDisplayMode,
    ) {
        val intent = Intent(context, CaptionOverlayService::class.java)
        if (mode == CaptionDisplayMode.Reader) {
            context.stopService(intent)
            return
        }
        intent.action = CaptionOverlayService.ACTION_SHOW
        intent.putExtra(
            CaptionOverlayService.EXTRA_MODE,
            if (mode == CaptionDisplayMode.Compact) CaptionOverlayService.MODE_COMPACT else "floating",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
