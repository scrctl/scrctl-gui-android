package com.openscreens.app.ui.session

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TrackpadPanTest {
    @Test
    fun followsPointerUntilEachDesktopEdge() {
        val band = IntSize(400, 300)
        val screen = IntSize(400, 400)
        assertEquals(Offset(-50f, 37.5f), trackpadPan(Offset(0.625f, 0.375f), 2f, band, screen))
        assertEquals(Offset(100f, 50f), trackpadPan(Offset.Zero, 2f, band, screen))
        assertEquals(Offset(-100f, -50f), trackpadPan(Offset(1f, 1f), 2f, band, screen))
        assertEquals(Offset.Zero, trackpadPan(Offset(1f, 1f), 1f, band, screen))
    }

    @Test
    fun viewportRequestsPixelsBeyondAsymmetricSafeEdges() {
        val rect = visibleServerRect(
            zoom = 2f, pan = Offset.Zero, bandWidth = 400f, bandHeight = 400f,
            screen = IntSize(400, 400), renderBounds = Rect(-60f, -20f, 440f, 480f),
            serverWidth = 800, serverHeight = 800,
        )
        assertArrayEquals(intArrayOf(140, 180, 500, 500), rect)
    }
}
