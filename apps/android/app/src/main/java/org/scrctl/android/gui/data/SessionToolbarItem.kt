package org.scrctl.android.gui.data

/** Stable preference identifiers, listed in the default toolbar order. */
enum class SessionToolbarItem(val id: String) {
    Pointer("pointer"),
    Displays("displays"),
    Keyboard("keyboard"),
    Clipboard("clipboard"),
    FileTransfers("download"),
    DisplayOptions("display"),
    HotCorners("hotCorners"),
    Commands("commands"),
    Observe("observe"),
    Screenshot("screenshot"),
    Hide("hide"),
    Disconnect("disconnect"),
    ;

    companion object {
        fun decode(value: String?): List<SessionToolbarItem> =
            value?.split(',')?.mapNotNull { id -> entries.firstOrNull { it.id == id } }
                ?.distinct() ?: entries.toList()
    }
}
