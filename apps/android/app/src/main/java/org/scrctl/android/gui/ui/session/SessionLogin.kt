package org.scrctl.android.gui.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.scrctl.android.gui.data.Connection
import org.scrctl.android.gui.session.CredentialChallenge

/** Lives with the session composition. Only an accepted, opted-in attempt reaches storage. */
internal class SessionLogin(connection: Connection) {
    var attempt by mutableStateOf(connection)
        private set
    var usingGuestAccess by mutableStateOf(false)
        private set
    var generation by mutableIntStateOf(0)
        private set
    var rememberLogin by mutableStateOf(false)
        private set
    var challenge by mutableStateOf<CredentialChallenge?>(null)
        private set

    fun failed(required: CredentialChallenge?) {
        challenge = required
        if (required != null) attempt = attempt.copy(vncPassword = "")
    }

    fun retry(username: String, password: String, remember: Boolean) {
        val required = challenge ?: return
        if (password.isEmpty() || (required.requiresUsername && username.isBlank())) return
        challenge = null
        usingGuestAccess = false
        rememberLogin = remember
        attempt = attempt.copy(username = username.trim(), vncPassword = password)
        generation++
    }

    fun requestGuestAccess(): Boolean {
        if (challenge?.offersMacGuestAccess != true) return false
        challenge = null
        usingGuestAccess = true
        rememberLogin = false
        return true
    }

    fun accepted(): Connection? {
        if (!rememberLogin || challenge != null) return null
        rememberLogin = false
        return attempt
    }
}
