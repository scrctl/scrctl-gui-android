package org.scrctl.android.gui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.scrctl.android.gui.ui.library.parseQuickConnectUrl

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openVncLink(intent)
        setContent {
            ScrctlGuiApp(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openVncLink(intent)
    }

    private fun openVncLink(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW || !intent.scheme.equals("vnc", ignoreCase = true)) return
        val target = intent.dataString?.let(::parseQuickConnectUrl) ?: return
        viewModel.quickConnect(target.host, target.port, target.username)
    }
}
