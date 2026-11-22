package org.scrctl.android.gui.ui.session

import org.scrctl.android.gui.data.Connection
import org.scrctl.android.gui.data.DisconnectAction
import org.scrctl.android.gui.data.RemotePlatform
import org.scrctl.android.gui.session.sendChord

internal class DisconnectRequest private constructor(
    val action: DisconnectAction,
    val interruptsTransfers: Boolean,
    private val platform: RemotePlatform,
) {
    companion object {
        fun create(connection: Connection, inControl: Boolean, interruptsTransfers: Boolean): DisconnectRequest? {
            val action = if (inControl && connection.disconnectAction in DisconnectAction.options(connection.platform)) {
                connection.disconnectAction
            } else DisconnectAction.None
            if (action == DisconnectAction.None && !interruptsTransfers) return null
            return DisconnectRequest(action, interruptsTransfers, connection.platform)
        }
    }

    fun sendConfirmedAction(
        connection: Connection, inControl: Boolean, width: Int, height: Int,
        release: () -> Unit, pointer: (Int, Int, Int) -> Unit, key: (Int, Boolean) -> Unit,
    ) {
        if (!inControl || action == DisconnectAction.None || action != connection.disconnectAction ||
            platform != connection.platform) return
        release()
        when (action) {
            DisconnectAction.Lock, DisconnectAction.CtrlAltDelete -> {
                val command = when {
                    action == DisconnectAction.CtrlAltDelete -> ctrlAltDelete
                    platform == RemotePlatform.Windows -> lockWindows
                    else -> lockMac
                }
                sendChord(command.keysym, command.modifiers, key)
            }
            DisconnectAction.LogOut -> sendChord('q'.code, listOf(KEY_SHIFT, KEY_ALT, KEY_SUPER), key)
            DisconnectAction.TopLeft, DisconnectAction.TopRight, DisconnectAction.BottomLeft, DisconnectAction.BottomRight -> {
                if (width < 1 || height < 1) return
                val right = action == DisconnectAction.TopRight || action == DisconnectAction.BottomRight
                val bottom = action == DisconnectAction.BottomLeft || action == DisconnectAction.BottomRight
                pointer(if (right) width - 1 else 0, if (bottom) height - 1 else 0, 0)
            }
            DisconnectAction.None -> Unit
        }
    }
}
