package org.scrctl.android.gui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.scrctl.android.gui.data.ConnectionStore
import org.scrctl.android.gui.data.SettingsStore
import org.scrctl.android.gui.data.SshKeyStore
import org.scrctl.android.gui.data.ThemeMode
import org.scrctl.android.gui.ui.session.SessionScreen
import org.scrctl.android.gui.ui.theme.ScrctlGuiTheme

/// A single remote session hosted in its own task, so freeform/desktop mode
/// can show several sessions side by side.
class SessionWindowActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CONNECTION_ID = "connectionId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val connectionStore = ConnectionStore.shared(applicationContext)
        val sshKeyStore = SshKeyStore(applicationContext)
        val settingsStore = SettingsStore(applicationContext)
        val connection = intent.getStringExtra(EXTRA_CONNECTION_ID)?.let(connectionStore::find)
        if (connection == null) {
            finish()
            return
        }

        setContent {
            val settings = remember { settingsStore.load() }
            val connections by connectionStore.connections.collectAsStateWithLifecycle()
            val current = connections.firstOrNull { it.id == connection.id } ?: connection
            val darkTheme = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            ScrctlGuiTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SessionScreen(
                        connection = current,
                        sshKey = sshKeyStore.find(current.ssh?.keyId),
                        quality = current.quality,
                        initialPointerMode = connectionStore.cursorMode(current),
                        onPointerModeChanged = { connectionStore.setCursorMode(current, it) },
                        onQualityChange = { connectionStore.setQuality(current.id, it) },
                        onPreferredDisplayChange = { connectionStore.setPreferredDisplay(current.id, it) },
                        onRemember = connectionStore::rememberLogin,
                        onConnected = { connectionStore.markConnected(connection.id) },
                        onExit = { finish() },
                    )
                }
            }
        }
    }
}
