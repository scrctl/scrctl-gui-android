package org.scrctl.android.gui.ui.session

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.scrctl.android.gui.session.DisplayScreen

class RemoteWheelStepTest {
    private val first = DisplayScreen(1, 0, 0, 1920, 1080, 0)
    private val second = DisplayScreen(2, 1920, 0, 3840, 2160, 0)
    private val framebuffer = IntSize(5760, 2160)

    @Test
    fun otherServersKeepTheirDensityAdjustedStep() {
        assertEquals(72f, remoteWheelStep(72f, false, framebuffer, listOf(first, second), 2, IntOffset.Zero))
    }

    @Test
    fun selectedDisplayTakesPriorityOverCursorPosition() {
        assertEquals(12f, remoteWheelStep(24f, true, framebuffer, listOf(first, second), 2, IntOffset.Zero))
    }

    @Test
    fun combinedDesktopUsesTheDisplayAtTheWheelTarget() {
        for (base in listOf(24f, 48f, 72f)) {
            assertEquals(base, remoteWheelStep(base, true, framebuffer, listOf(first, second), null, IntOffset(1919, 50)))
            assertEquals(base / 2f, remoteWheelStep(base, true, framebuffer, listOf(first, second), null, IntOffset(1920, 50)))
        }
    }

    @Test
    fun unknownOriginsOrGapsUseTheFirstAdvertisedDisplay() {
        val unknownOrigins = listOf(second.copy(x = 0), first)
        assertEquals(12f, remoteWheelStep(24f, true, framebuffer, unknownOrigins, null, IntOffset(2400, 50)))
        assertEquals(24f, remoteWheelStep(24f, true, framebuffer, unknownOrigins, 1, IntOffset(2400, 50)))
        assertEquals(12f, remoteWheelStep(24f, true, framebuffer, listOf(second, first), null, IntOffset(50, 2000)))
    }

    @Test
    fun framebufferFallbackUsesTheShortEdgeAndClampsGain() {
        assertEquals(24f, remoteWheelStep(24f, true, IntSize(720, 1280), emptyList(), null, IntOffset.Zero))
        assertEquals(12f, remoteWheelStep(24f, true, IntSize(2160, 3840), emptyList(), null, IntOffset.Zero))
        assertEquals(6f, remoteWheelStep(24f, true, IntSize(8192, 6144), emptyList(), null, IntOffset.Zero))
    }
}
