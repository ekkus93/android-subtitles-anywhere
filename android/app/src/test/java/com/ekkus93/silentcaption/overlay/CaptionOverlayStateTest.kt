package com.ekkus93.silentcaption.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionOverlayStateTest {
    @Test
    fun `geometry is recovered inside changed screen bounds`() {
        val recovered =
            OverlayGeometryPolicy.clamp(
                OverlayGeometry(x = 1800, y = -200, width = 1400, height = 900),
                OverlayBounds(width = 1080, height = 1920, margin = 24),
            )

        assertEquals(24, recovered.x)
        assertEquals(24, recovered.y)
        assertEquals(1032, recovered.width)
        assertEquals(900, recovered.height)
    }

    @Test
    fun `partial caption replacement does not duplicate prior partial`() {
        val state =
            CaptionOverlayText(committed = "Hello", partial = "wor")
                .replacePartial("world")

        assertEquals("Hello world", state.visible)
    }

    @Test
    fun `final caption clears partial and preserves committed history`() {
        val state =
            CaptionOverlayText(committed = "Hello", partial = "world")
                .commit("world")

        assertEquals("Hello world", state.visible)
        assertTrue(state.partial.isEmpty())
    }

    @Test
    fun `long unbroken caption remains represented without truncating state`() {
        val text = "x".repeat(4096)

        assertEquals(text, CaptionOverlayText(partial = text).visible)
    }
}
