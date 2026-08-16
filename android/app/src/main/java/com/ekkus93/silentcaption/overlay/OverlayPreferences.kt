package com.ekkus93.silentcaption.overlay

import android.content.Context
import android.content.res.Configuration

class OverlayPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(configuration: Configuration): OverlayGeometry {
        val prefix = orientationPrefix(configuration)
        return OverlayGeometry(
            x = preferences.getInt("${prefix}_x", 24),
            y = preferences.getInt("${prefix}_y", 120),
            width = preferences.getInt("${prefix}_width", 720),
            height = preferences.getInt("${prefix}_height", 240),
        )
    }

    fun save(
        configuration: Configuration,
        geometry: OverlayGeometry,
    ) {
        val prefix = orientationPrefix(configuration)
        preferences.edit()
            .putInt("${prefix}_x", geometry.x)
            .putInt("${prefix}_y", geometry.y)
            .putInt("${prefix}_width", geometry.width)
            .putInt("${prefix}_height", geometry.height)
            .apply()
    }

    private fun orientationPrefix(configuration: Configuration): String =
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "landscape"
        } else {
            "portrait"
        }

    private companion object {
        const val PREFERENCES = "caption_overlay_geometry"
    }
}
