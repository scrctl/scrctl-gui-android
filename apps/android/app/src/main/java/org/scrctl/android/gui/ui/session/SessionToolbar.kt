package org.scrctl.android.gui.ui.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.scrctl.android.gui.R
import org.scrctl.android.gui.data.QualityPreset
import org.scrctl.android.gui.data.SessionPointerMode
import org.scrctl.android.gui.data.SessionToolbarItem
import org.scrctl.android.gui.data.RemotePlatform
import org.scrctl.android.gui.session.DisplayScreen
import org.scrctl.android.gui.ui.common.label

private val ActionSize = 48.dp

// A partially visible icon is not an action slot. Reserve a full slot for
// overflow whenever any of the available actions would otherwise be clipped.
internal fun sessionToolbarInlineCount(
    actionCount: Int,
    widthPx: Int,
    actionWidthPx: Int,
    hasUnpinned: Boolean = false,
): Int {
    val slots = (widthPx / actionWidthPx).coerceAtLeast(0)
    return if (!hasUnpinned && actionCount <= slots) actionCount
        else (slots - 1).coerceIn(0, actionCount)
}

private data class SessionAction(
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val checked: Boolean? = null,
    val submenu: List<SessionAction> = emptyList(),
    val sectionHeader: Boolean = false,
    val onClick: () -> Unit = {},
)

@Composable
internal fun SessionToolbar(
    toolbarItems: List<SessionToolbarItem>,
    platform: RemotePlatform,
    onCommand: (DesktopCommand) -> Unit,
    observing: Boolean,
    onObserveModeChange: (Boolean) -> Unit,
    pointerMode: SessionPointerMode,
    onPointerModeSelected: (SessionPointerMode) -> Unit,
    onResetZoom: (() -> Unit)?,
    onActualSize: (() -> Unit)?,
    vertical: Boolean,
    displays: List<DisplayScreen>,
    supportsCurtainMode: Boolean,
    onCurtainRequest: (Boolean) -> Unit,
    selectedDisplayId: Int?,
    onSelectDisplay: (Int?) -> Unit,
    quality: QualityPreset,
    changingQuality: Boolean,
    onQualitySelected: (QualityPreset) -> Unit,
    isFullscreen: Boolean,
    onKeyboard: () -> Unit,
    connected: Boolean,
    clipboardSharing: Boolean,
    onClipboardSharingChange: (Boolean) -> Unit,
    onSendClipboard: () -> Unit,
    onCopyClipboard: () -> Unit,
    onComposeClipboardText: () -> Unit,
    scaling: SessionScaling,
    onScalingSelected: (SessionScaling) -> Unit,
    macTarget: Boolean,
    onHotCorner: (Float, Float) -> Unit,
    supportsFileUpload: Boolean,
    supportsFileDownload: Boolean,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    canUpload: Boolean,
    canDownload: Boolean,
    onShowFileTransfers: () -> Unit,
    onScreenshot: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onHideChrome: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val disconnect = SessionAction(
        stringResource(R.string.session_disconnect),
        Icons.AutoMirrored.Outlined.ExitToApp,
        onClick = onDisconnect,
    )
    val hide = SessionAction(
        stringResource(R.string.session_hide_controls),
        Icons.Filled.KeyboardArrowDown,
        onClick = onHideChrome,
    )
    val commands = SessionAction(
        stringResource(R.string.remote_commands),
        Icons.Outlined.KeyboardCommandKey,
        enabled = connected && !observing,
        submenu = desktopCommands(platform).map { command ->
            SessionAction(stringResource(command.title), onClick = { onCommand(command) })
        },
    )
    val actions = buildMap {
        if (!observing) {
            put(SessionToolbarItem.Pointer, SessionAction(
                stringResource(R.string.session_pointer_mode),
                pointerMode.icon,
                submenu = SessionPointerMode.entries.map { mode ->
                    SessionAction(
                        stringResource(mode.label),
                        mode.icon,
                        checked = mode == pointerMode,
                        onClick = { onPointerModeSelected(mode) },
                    )
                },
            ))
        }
        if (displays.size > 1 || selectedDisplayId != null) {
            val displayActions = buildList {
                add(SessionAction(
                    stringResource(R.string.session_all_displays),
                    checked = selectedDisplayId == null,
                    onClick = { onSelectDisplay(null) },
                ))
                displays.forEachIndexed { index, screen ->
                    add(SessionAction(
                        stringResource(R.string.session_display_entry, index + 1, screen.width, screen.height),
                        checked = selectedDisplayId == screen.id,
                        onClick = { onSelectDisplay(screen.id) },
                    ))
                }
            }
            put(SessionToolbarItem.Displays, SessionAction(stringResource(R.string.session_displays), Icons.Outlined.Monitor, submenu = displayActions))
        }
        if (!observing) {
            put(SessionToolbarItem.Keyboard, SessionAction(stringResource(R.string.session_keyboard), Icons.Outlined.Keyboard, onClick = onKeyboard))
        }
        put(SessionToolbarItem.Clipboard, SessionAction(
            stringResource(R.string.session_clipboard),
            Icons.Outlined.ContentPaste,
            enabled = connected,
            submenu = buildList {
                add(SessionAction(stringResource(R.string.session_clipboard_send_local), enabled = !observing, onClick = onSendClipboard))
                add(SessionAction(stringResource(R.string.session_clipboard_copy_remote), onClick = onCopyClipboard))
                add(SessionAction(
                    stringResource(R.string.editor_shared_clipboard),
                    checked = clipboardSharing,
                    onClick = { onClipboardSharingChange(!clipboardSharing) },
                ))
                if (!observing) {
                    add(SessionAction(stringResource(R.string.session_clipboard_action), onClick = onComposeClipboardText))
                }
            },
        ))
        if (connected && (supportsFileUpload || supportsFileDownload)) {
            put(SessionToolbarItem.FileTransfers, SessionAction(
                stringResource(R.string.session_file_transfers),
                Icons.Outlined.SwapVert,
                submenu = buildList {
                    if (supportsFileUpload) {
                        add(SessionAction(stringResource(R.string.session_send_file), Icons.Outlined.UploadFile, enabled = canUpload && !observing, onClick = onUpload))
                    }
                    if (supportsFileDownload) {
                        add(SessionAction(stringResource(R.string.session_download), Icons.Outlined.Download, enabled = canDownload, onClick = onDownload))
                    }
                    add(SessionAction(stringResource(R.string.session_file_transfers), Icons.Outlined.SwapVert, onClick = onShowFileTransfers))
                },
            ))
        }
        put(SessionToolbarItem.DisplayOptions, SessionAction(
            stringResource(R.string.session_display_options),
            Icons.Outlined.Tune,
            submenu = buildList {
                if (supportsCurtainMode && !observing) {
                    add(SessionAction(stringResource(R.string.session_enable_curtain), onClick = { onCurtainRequest(true) }))
                    add(SessionAction(stringResource(R.string.session_disable_curtain), onClick = { onCurtainRequest(false) }))
                }
                add(SessionAction(stringResource(R.string.session_scaling), sectionHeader = true))
                addAll(SessionScaling.entries.map { mode ->
                    SessionAction(stringResource(mode.label), checked = mode == scaling, onClick = { onScalingSelected(mode) })
                })
                add(SessionAction(
                    stringResource(if (isFullscreen) R.string.session_exit_fullscreen else R.string.session_enter_fullscreen),
                    if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    onClick = onToggleFullscreen,
                ))
                onActualSize?.let { add(SessionAction(stringResource(R.string.session_actual_size), onClick = it)) }
                onResetZoom?.let { add(SessionAction(stringResource(R.string.session_fit_desktop), onClick = it)) }
                add(SessionAction(stringResource(R.string.session_quality), sectionHeader = true))
                addAll(QualityPreset.entries.map { preset ->
                    SessionAction(preset.label(), enabled = connected && !changingQuality, checked = preset == quality, onClick = { onQualitySelected(preset) })
                })
            },
        ))
        if (macTarget && !observing) {
            put(SessionToolbarItem.HotCorners, SessionAction(
                stringResource(R.string.session_hot_corners),
                Icons.Outlined.GridView,
                enabled = connected,
                submenu = listOf(
                    SessionAction(stringResource(R.string.session_top_left), onClick = { onHotCorner(0f, 0f) }),
                    SessionAction(stringResource(R.string.session_top_right), onClick = { onHotCorner(1f, 0f) }),
                    SessionAction(stringResource(R.string.session_bottom_left), onClick = { onHotCorner(0f, 1f) }),
                    SessionAction(stringResource(R.string.session_bottom_right), onClick = { onHotCorner(1f, 1f) }),
                    SessionAction(stringResource(R.string.session_show_dock), onClick = { onHotCorner(0.5f, 1f) }),
                ),
            ))
        }
        if (!observing) put(SessionToolbarItem.Commands, commands)
        put(SessionToolbarItem.Observe, SessionAction(
            stringResource(if (observing) R.string.session_observe_on else R.string.session_observe_off),
            Icons.Outlined.Visibility,
            checked = observing,
            onClick = { onObserveModeChange(!observing) },
        ))
        put(SessionToolbarItem.Screenshot, SessionAction(stringResource(R.string.session_screenshot), Icons.Outlined.PhotoCamera, onClick = onScreenshot))
        put(SessionToolbarItem.Hide, hide)
        put(SessionToolbarItem.Disconnect, disconnect)
    }

    SessionToolbarBar(toolbarItems, actions, vertical, modifier)
}

internal data class SessionToolbarLayout(
    val pinned: List<SessionToolbarItem>,
    val unpinned: List<SessionToolbarItem>,
) {
    fun overflow(inlineCount: Int): List<SessionToolbarItem> = pinned.drop(inlineCount) + unpinned
}

internal fun sessionToolbarLayout(
    order: List<SessionToolbarItem>,
    available: Set<SessionToolbarItem>,
): SessionToolbarLayout {
    val pinned = order.distinct().filter { it in available }
    val unpinned = SessionToolbarItem.entries.filter { it in available && it !in pinned }
    return SessionToolbarLayout(pinned, unpinned)
}

@Composable
internal fun SessionToolbarPreview(items: List<SessionToolbarItem>, modifier: Modifier = Modifier) {
    val actions = SessionToolbarItem.entries.associateWith {
        SessionAction(stringResource(it.label), it.icon)
    }
    SessionToolbarBar(items, actions, vertical = false, modifier = modifier, preview = true)
}

@Composable
private fun SessionToolbarBar(
    order: List<SessionToolbarItem>,
    actions: Map<SessionToolbarItem, SessionAction>,
    vertical: Boolean,
    modifier: Modifier,
    preview: Boolean = false,
) {
    val layout = sessionToolbarLayout(order, actions.keys)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val actionWidth = with(density) { ActionSize.roundToPx() }
        val edgePadding = 4.dp
        val padding = with(density) { 2 * edgePadding.roundToPx() }
        val available = (if (vertical) constraints.maxHeight else constraints.maxWidth) - padding
        val inlineCount = sessionToolbarInlineCount(layout.pinned.size, available, actionWidth, layout.unpinned.isNotEmpty())
        val shown = layout.pinned.take(inlineCount).map(actions::getValue).toMutableList()
        val overflow = layout.overflow(inlineCount)
        if (overflow.isNotEmpty()) {
            shown += SessionAction(
                stringResource(R.string.session_more_actions),
                Icons.Outlined.MoreHoriz,
                submenu = overflow.map(actions::getValue),
            )
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
            val buttons: @Composable () -> Unit = {
                shown.forEach { action ->
                    key(action.label) {
                        if (preview) {
                            Box(Modifier.size(ActionSize), contentAlignment = Alignment.Center) {
                                Icon(requireNotNull(action.icon), contentDescription = action.label)
                            }
                        } else {
                            SessionActionButton(action)
                        }
                    }
                }
            }
            if (vertical) {
                Column(Modifier.padding(edgePadding)) { buttons() }
            } else {
                Row(Modifier.padding(edgePadding)) { buttons() }
            }
        }
    }
}

internal val SessionToolbarItem.label: Int
    get() = when (this) {
        SessionToolbarItem.Pointer -> R.string.session_pointer_mode
        SessionToolbarItem.Displays -> R.string.session_displays
        SessionToolbarItem.Keyboard -> R.string.session_keyboard
        SessionToolbarItem.Clipboard -> R.string.session_clipboard
        SessionToolbarItem.FileTransfers -> R.string.session_file_transfers
        SessionToolbarItem.DisplayOptions -> R.string.session_display_options
        SessionToolbarItem.HotCorners -> R.string.session_hot_corners
        SessionToolbarItem.Commands -> R.string.remote_commands
        SessionToolbarItem.Observe -> R.string.session_toolbar_observe
        SessionToolbarItem.Screenshot -> R.string.session_screenshot
        SessionToolbarItem.Hide -> R.string.session_hide_controls
        SessionToolbarItem.Disconnect -> R.string.session_disconnect
    }

internal val SessionToolbarItem.icon: ImageVector
    get() = when (this) {
        SessionToolbarItem.Pointer -> Icons.Outlined.Mouse
        SessionToolbarItem.Displays -> Icons.Outlined.Monitor
        SessionToolbarItem.Keyboard -> Icons.Outlined.Keyboard
        SessionToolbarItem.Clipboard -> Icons.Outlined.ContentPaste
        SessionToolbarItem.FileTransfers -> Icons.Outlined.SwapVert
        SessionToolbarItem.DisplayOptions -> Icons.Outlined.Tune
        SessionToolbarItem.HotCorners -> Icons.Outlined.GridView
        SessionToolbarItem.Commands -> Icons.Outlined.KeyboardCommandKey
        SessionToolbarItem.Observe -> Icons.Outlined.Visibility
        SessionToolbarItem.Screenshot -> Icons.Outlined.PhotoCamera
        SessionToolbarItem.Hide -> Icons.Filled.KeyboardArrowDown
        SessionToolbarItem.Disconnect -> Icons.AutoMirrored.Outlined.ExitToApp
    }

internal enum class SessionScaling(val label: Int) {
    Fit(R.string.session_scale_fit),
    Fill(R.string.session_scale_fill),
}

private val SessionPointerMode.label: Int
    get() = when (this) {
        SessionPointerMode.Direct -> R.string.session_pointer_direct
        SessionPointerMode.Trackpad -> R.string.session_pointer_trackpad
        SessionPointerMode.Hybrid -> R.string.session_pointer_hybrid
    }

private val SessionPointerMode.icon: ImageVector
    get() = if (this == SessionPointerMode.Hybrid) Icons.Outlined.Mouse else Icons.Outlined.TouchApp

@Composable
private fun SessionActionButton(action: SessionAction) {
    val icon = requireNotNull(action.icon)
    val size = Modifier.size(ActionSize)
    if (action.checked != null) {
        IconToggleButton(
            checked = action.checked,
            onCheckedChange = { action.onClick() },
            enabled = action.enabled,
            modifier = size,
            colors = IconButtonDefaults.iconToggleButtonColors(
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Icon(icon, contentDescription = action.label)
        }
    } else if (action.submenu.isEmpty()) {
        IconButton(enabled = action.enabled, modifier = size, onClick = action.onClick) {
            Icon(icon, contentDescription = action.label)
        }
    } else {
        var expanded by remember(action.enabled) { mutableStateOf(false) }
        Box {
            IconButton(enabled = action.enabled, modifier = size, onClick = { expanded = true }) {
                Icon(icon, contentDescription = action.label)
            }
            if (expanded) {
                SessionActionMenu(action) { expanded = false }
            }
        }
    }
}

@Composable
private fun SessionActionMenu(root: SessionAction, onDismiss: () -> Unit) {
    var submenuLabel by remember { mutableStateOf<String?>(null) }
    val submenu = root.submenu.firstOrNull { it.label == submenuLabel && it.enabled }
    val items = submenu?.submenu ?: root.submenu
    val scrollState = key(submenu?.label) { rememberScrollState() }
    DropdownMenu(expanded = true, onDismissRequest = onDismiss, scrollState = scrollState) {
        if (submenu != null) {
            DropdownMenuItem(
                text = { Text(root.label) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null) },
                onClick = { submenuLabel = null },
            )
        }
        items.forEach { item ->
            if (item.sectionHeader) {
                Text(
                    text = item.label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@forEach
            }
            val leadingIcon: (@Composable () -> Unit)? = item.icon?.let { icon ->
                { Icon(icon, contentDescription = null) }
            }
            DropdownMenuItem(
                text = { Text(item.label) },
                leadingIcon = leadingIcon,
                enabled = item.enabled,
                modifier = item.checked?.let { checked -> Modifier.semantics { selected = checked } } ?: Modifier,
                trailingIcon = {
                    when {
                        item.submenu.isNotEmpty() -> Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                        item.checked == true -> Icon(Icons.Outlined.Check, contentDescription = null)
                    }
                },
                onClick = {
                    if (item.submenu.isNotEmpty()) {
                        submenuLabel = item.label
                    } else {
                        onDismiss()
                        item.onClick()
                    }
                },
            )
        }
    }
}
