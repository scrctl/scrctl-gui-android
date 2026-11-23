package org.scrctl.android.gui.ui.session

import org.scrctl.android.gui.data.Connection
import org.scrctl.android.gui.session.CredentialChallenge
import org.junit.Assert.*
import org.junit.Test

class SessionLoginTest {
    private val profile = Connection(name = "Studio", host = "studio.local")

    @Test fun rejectedAndUncheckedCredentialsNeverReachStorage() {
        val login = SessionLogin(profile)
        login.failed(CredentialChallenge.MissingUsername)
        login.retry("  ", "wrong", true)
        assertEquals(0, login.generation)
        login.retry(" user ", "wrong", true)
        login.failed(CredentialChallenge.Rejected)
        assertEquals("", login.attempt.vncPassword)
        assertNull(login.accepted())
        login.retry(" user ", " correct ", false)
        assertEquals("user", login.attempt.username)
        assertEquals(" correct ", login.attempt.vncPassword)
        assertNull(login.accepted())
        assertEquals(2, login.generation)
        assertEquals("", SessionLogin(profile).attempt.vncPassword)
    }

    @Test fun guestLaunchCanSwitchToAccountLoginWithoutSavingGuestCredentials() {
        val login = SessionLogin(profile, asGuest = true)
        assertTrue(login.usingGuestAccess)
        assertNull(login.accepted())

        login.failed(CredentialChallenge.MacGuestRejected)
        login.retry("me", "correct", false)

        assertFalse(login.usingGuestAccess)
        assertEquals(1, login.generation)
        assertEquals("me", login.attempt.username)
        assertNull(login.accepted())
    }

    @Test fun acceptedOptInCanBeSavedOnce() {
        val login = SessionLogin(profile)
        login.failed(CredentialChallenge.MissingPassword)
        login.retry("", "correct", true)
        assertEquals(profile.copy(vncPassword = "correct"), login.accepted())
        assertNull(login.accepted())
        assertEquals("correct", login.attempt.vncPassword)
    }
}
