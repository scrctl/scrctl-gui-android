package org.scrctl.android.gui.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.scrctl.android.gui.R

@Composable
internal fun SessionConnectingCard(
    connected: Boolean,
    requestingMacPermission: Boolean,
    endpoint: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when {
        connected -> R.string.session_waiting_title
        requestingMacPermission -> R.string.session_mac_permission_pending
        else -> R.string.session_status_connecting
    }
    Surface(
        modifier = modifier.padding(16.dp).widthIn(max = 320.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                endpoint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (requestingMacPermission && !connected) {
                Text(
                    stringResource(R.string.session_mac_permission_approve),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.library_cancel))
            }
        }
    }
}
