package com.ekkus93.silentcaption.overlay

enum class OverlayMode {
    Floating,
    Compact,
}

data class OverlayGeometry(
    val x: Int = 24,
    val y: Int = 120,
    val width: Int = 720,
    val height: Int = 240,
)

data class OverlayBounds(
    val width: Int,
    val height: Int,
    val margin: Int = 16,
)

data class CaptionOverlayStyle(
    val fontScale: Float = 1.0f,
    val opacity: Float = 0.88f,
    val widthFraction: Float = 0.82f,
    val margin: Int = 16,
)

data class CaptionOverlayText(
    val committed: String = "",
    val partial: String = "",
) {
    val visible: String
        get() = listOf(committed, partial).filter(String::isNotBlank).joinToString(" ")

    fun replacePartial(text: String): CaptionOverlayText = copy(partial = text)

    fun commit(text: String): CaptionOverlayText =
        copy(
            committed = listOf(committed, text).filter(String::isNotBlank).joinToString(" "),
            partial = "",
        )
}

object OverlayGeometryPolicy {
    fun clamp(
        geometry: OverlayGeometry,
        bounds: OverlayBounds,
    ): OverlayGeometry {
        val margin = bounds.margin.coerceAtLeast(0)
        val maxWidth = (bounds.width - margin * 2).coerceAtLeast(1)
        val maxHeight = (bounds.height - margin * 2).coerceAtLeast(1)
        val width = geometry.width.coerceIn(1, maxWidth)
        val height = geometry.height.coerceIn(1, maxHeight)
        val maxX = (bounds.width - width - margin).coerceAtLeast(margin)
        val maxY = (bounds.height - height - margin).coerceAtLeast(margin)
        return geometry.copy(
            x = geometry.x.coerceIn(margin, maxX),
            y = geometry.y.coerceIn(margin, maxY),
            width = width,
            height = height,
        )
    }
}
