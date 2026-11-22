package org.scrctl.android.gui.ui.session

import org.scrctl.android.gui.data.SessionToolbarItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionToolbarLayoutTest {
    @Test
    fun reservesOneFullSlotForOverflowInPortrait() {
        assertEquals(6, sessionToolbarInlineCount(actionCount = 12, widthPx = 352, actionWidthPx = 48))
        // 1440 px minus 8 dp of bar padding, at 3.5 px per dp.
        assertEquals(7, sessionToolbarInlineCount(actionCount = 12, widthPx = 1412, actionWidthPx = 168))
    }

    @Test
    fun reservesOverflowAtTheExactFitBoundary() {
        assertEquals(12, sessionToolbarInlineCount(actionCount = 12, widthPx = 576, actionWidthPx = 48))
        assertEquals(10, sessionToolbarInlineCount(actionCount = 12, widthPx = 575, actionWidthPx = 48))
    }

    @Test
    fun keepsAllActionsInlineWhenTheyFitAWideOrObserveOnlySession() {
        assertEquals(12, sessionToolbarInlineCount(actionCount = 12, widthPx = 800, actionWidthPx = 48))
        assertEquals(7, sessionToolbarInlineCount(actionCount = 7, widthPx = 352, actionWidthPx = 48))
    }

    @Test
    fun leavesOnlyOverflowWithOneAvailableSlot() {
        assertEquals(0, sessionToolbarInlineCount(actionCount = 12, widthPx = 48, actionWidthPx = 48))
    }
    @Test
    fun usesOneOrderAcrossTargetsAndOnlyOffersAvailableActions() {
        val order = listOf(SessionToolbarItem.FileTransfers, SessionToolbarItem.Keyboard, SessionToolbarItem.Pointer)
        val available = setOf(SessionToolbarItem.Keyboard, SessionToolbarItem.Pointer, SessionToolbarItem.Disconnect)
        val layout = sessionToolbarLayout(order, available)
        assertEquals(listOf(SessionToolbarItem.Keyboard, SessionToolbarItem.Pointer), layout.pinned)
        assertEquals(listOf(SessionToolbarItem.Pointer, SessionToolbarItem.Disconnect), layout.overflow(1))
        assertEquals(1, sessionToolbarInlineCount(2, 96, 48, hasUnpinned = true))

        val restored = sessionToolbarLayout(order, available + SessionToolbarItem.FileTransfers)
        assertEquals(order, restored.pinned)
        assertEquals(emptyList<SessionToolbarItem>(), SessionToolbarItem.decode(""))
    }
}
