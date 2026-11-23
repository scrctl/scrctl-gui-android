package org.scrctl.android.gui.ui.session

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

private const val SENTINEL = "\u200B"

internal class RemoteKeyboardBuffer {
    val field = TextFieldState(SENTINEL)

    fun handleBackspace(send: () -> Unit) = InputTransformation {
        if (length == 0 && originalText.startsWith(SENTINEL)) {
            replace(0, 0, SENTINEL)
            placeCursorAtEnd()
            send()
        }
    }

    suspend fun forward(send: (Int) -> Unit) {
        while (true) {
            snapshotFlow { field.composition == null && !SENTINEL.contentEquals(field.text) }.first { it }
            // Consume from the editor itself. Pending edits can accumulate before a
            // frame, and the next edit must see the same reset as the remote sender.
            val typed = field.text.toString().removePrefix(SENTINEL).replace("\r\n", "\n")
            field.edit {
                replace(0, length, SENTINEL)
                placeCursorAtEnd()
            }
            typed.codePoints().forEachOrdered { send(it) }
        }
    }
}

/** Maps committed characters and line breaks to their X11 keysyms. */
internal fun x11Keysym(code: Int): Int = when (code) {
    '\r'.code, '\n'.code -> KEY_RETURN
    '\t'.code -> KEY_TAB
    in 0x20..0x7E, in 0xA0..0xFF -> code
    else -> 0x0100_0000 or code
}
