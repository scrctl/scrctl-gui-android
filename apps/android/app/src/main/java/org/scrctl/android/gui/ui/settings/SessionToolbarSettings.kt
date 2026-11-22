package org.scrctl.android.gui.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.scrctl.android.gui.R
import org.scrctl.android.gui.data.SessionToolbarItem
import org.scrctl.android.gui.ui.session.SessionToolbarPreview
import org.scrctl.android.gui.ui.session.icon
import org.scrctl.android.gui.ui.session.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionToolbarSettings(
    pinned: List<SessionToolbarItem>,
    onChange: (List<SessionToolbarItem>) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val unpinned = SessionToolbarItem.entries.filterNot { it in pinned }
    fun move(from: Int, to: Int) {
        val next = pinned.toMutableList()
        next.add(to, next.removeAt(from))
        onChange(next)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_session_toolbar)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                }
            },
            actions = {
                TextButton(onClick = { onChange(SessionToolbarItem.entries) }) {
                    Text(stringResource(R.string.session_toolbar_reset))
                }
            },
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                ToolbarSectionTitle(R.string.session_toolbar_preview)
                SessionToolbarPreview(pinned, Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
            item { ToolbarSectionTitle(R.string.session_toolbar_pinned) }
            itemsIndexed(pinned, key = { _, item -> item.id }) { index, item ->
                ToolbarItemRow(item) {
                    IconButton(enabled = index > 0, onClick = { move(index, index - 1) }) {
                        Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.session_toolbar_move_up))
                    }
                    IconButton(enabled = index < pinned.lastIndex, onClick = { move(index, index + 1) }) {
                        Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.session_toolbar_move_down))
                    }
                    IconButton(onClick = { onChange(pinned - item) }) {
                        Icon(Icons.Default.Remove, stringResource(R.string.session_toolbar_remove))
                    }
                }
            }
            if (unpinned.isNotEmpty()) {
                item { ToolbarSectionTitle(R.string.session_toolbar_unpinned) }
                itemsIndexed(unpinned, key = { _, item -> item.id }) { _, item ->
                    ToolbarItemRow(item) {
                        IconButton(onClick = { onChange(pinned + item) }) {
                            Icon(Icons.Default.Add, stringResource(R.string.session_toolbar_add))
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.session_toolbar_scope),
                    modifier = Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolbarSectionTitle(label: Int) {
    Text(
        stringResource(label),
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ToolbarItemRow(item: SessionToolbarItem, actions: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(item.icon, contentDescription = null)
            Text(
                stringResource(item.label),
                modifier = Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            actions()
        }
    }
}
