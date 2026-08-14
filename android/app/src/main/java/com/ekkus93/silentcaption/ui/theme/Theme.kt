package com.ekkus93.silentcaption.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SilentCaptionColors = darkColorScheme(
    primary = Color(0xFF39D6C5),
    onPrimary = Color(0xFF00201C),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFF2F6F8),
    surface = Color(0xFF131A22),
    onSurface = Color(0xFFF2F6F8),
    surfaceVariant = Color(0xFF1B2530),
    onSurfaceVariant = Color(0xFF9BAAB5),
    error = Color(0xFFFF6B6B),
)

@Composable
fun SilentCaptionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SilentCaptionColors,
        content = content,
    )
}
