package org.scrctl.android.gui.ui.session

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun zoomedTrackpadRecentersOverHalfASecondWithoutMovingTheRemotePointer() = runBlocking {
        val frames = Channel<Long>(Channel.UNLIMITED)
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
                onFrame(frames.receive())
        }
        val viewport = SessionViewport(CoroutineScope(coroutineContext + clock))
        val size = IntSize(400, 400)
        val cursor = Offset(0.625f, 0.375f)
        viewport.zoom = 2f
        viewport.trackpadPosition = cursor
        try {
            withTimeout(2_000) {
                viewport.startRecentering(size, size)
                assertEquals(Offset.Zero, viewport.pan)

                val firstFrame = 1_000_000L
                frames.send(firstFrame)
                frames.send(firstFrame + 250_000_000L)
                while (viewport.pan == Offset.Zero) yield()
                assertEquals(Offset(-25f, 25f), viewport.pan)
                assertTrue(viewport.trackpadPointOnCanvas(size, size).x > 200f)

                frames.send(firstFrame + 500_000_000L)
                while (viewport.pan != Offset(-50f, 50f)) yield()
                assertEquals(Offset(200f, 200f), viewport.trackpadPointOnCanvas(size, size))
                assertEquals(cursor, viewport.trackpadPosition)
            }
        } finally {
            viewport.stopRecentering()
            frames.cancel()
        }
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

    @Test
    fun fillFitsEntireDesktopAndCapsAtFourTimesTheRemoteLogicalWidth() {
        val bounds = sessionZoomBounds(800f, 400f, 1200, 200)
        assertEquals(SessionZoomBounds(0.5f, 0.75f, 3f), bounds)
        assertEquals(400f, 800f * bounds.minimum)
        assertEquals(600f, 800f * bounds.actual)
        assertEquals(2400f, 800f * bounds.maximum)
    }
}
