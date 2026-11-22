package com.openscreens.app.ui.session

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
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
}
