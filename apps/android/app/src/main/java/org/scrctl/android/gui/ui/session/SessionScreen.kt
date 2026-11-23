package org.scrctl.android.gui.ui.session

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.scrctl.android.gui.R
import org.scrctl.android.gui.data.SettingsStore
import org.scrctl.android.gui.data.Connection
import org.scrctl.android.gui.data.DisconnectAction
import org.scrctl.android.gui.data.QualityPreset
import org.scrctl.android.gui.data.RemotePlatform
import org.scrctl.android.gui.data.SessionPointerMode
import org.scrctl.android.gui.data.SshKey
import org.scrctl.android.gui.session.ClipboardEncodingException
import org.scrctl.android.gui.session.SessionClipboard
import org.scrctl.android.gui.session.MacSessionChoice
import org.scrctl.android.gui.session.RemoteSession
import org.scrctl.android.gui.session.sendChord
import org.scrctl.android.gui.session.DisplayScreen
import org.scrctl.android.gui.session.SessionEvent
import org.scrctl.android.gui.session.uploadPickedFiles
import org.scrctl.android.gui.session.SessionProvider
import org.scrctl.android.gui.ui.theme.SystemBarIconAppearance
import org.scrctl.android.gui.ui.theme.findActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val SessionBackdrop = Color(0xFF060808)


private enum class SessionStatus { Connecting, Connected, Disconnected, Failed }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(
    connection: Connection,
    sshKey: SshKey?,
    quality: QualityPreset,
    onQualityChange: (QualityPreset) -> Unit,
    onRemember: (Connection) -> Boolean,
    onPreferredDisplayChange: (Int?) -> Unit,
    onConnected: () -> Unit,
    onExit: () -> Unit,
    observeOnly: Boolean = false,
    initialPointerMode: SessionPointerMode,
    onPointerModeChanged: (SessionPointerMode) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember(context.applicationContext) { SettingsStore(context.applicationContext) }
    val toolbarUpdates = remember(settings) { settings.observeToolbarItems() }
    val toolbarItems by toolbarUpdates.collectAsStateWithLifecycle(initialValue = settings.load().toolbarItems)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var clipboardSharing by rememberSaveable(connection.id) { mutableStateOf(connection.sharedClipboard) }
    var observing by rememberSaveable(connection.id, observeOnly) {
        mutableStateOf(observeOnly || connection.startInObserveMode)
    }
    val login = remember(connection.id) { SessionLogin(connection) }
    val session: RemoteSession = remember(connection.id, observeOnly, login.generation) {
        SessionProvider.create(context, login.attempt.copy(sharedClipboard = clipboardSharing), quality, sshKey, startInObserveMode = observing)
    }

    val recovery = remember(session) { SessionRecovery() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val manager = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val clipboard = remember(session) {
        SessionClipboard(
            shared = clipboardSharing,
            read = {
                // Only read inline text. Coercing a URI could open a content provider.
                manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            },
            write = { manager?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), it)) },
        )
    }
    var clipboardFetch by remember(session) { mutableStateOf<Job?>(null) }
    var clipboardFetchRequested by remember(session) { mutableStateOf(false) }
    var clipboardSendRequested by remember(session) { mutableStateOf(false) }

    var status by remember { mutableStateOf(SessionStatus.Connecting) }
    var remoteWidth by remember { mutableIntStateOf(0) }
    var remoteHeight by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf<SessionEvent.Frame?>(null) }
    var scaling by rememberSaveable { mutableStateOf(SessionScaling.Fit) }
    val viewport = remember(scope) { SessionViewport(scope) }
    var zoom by viewport::zoom
    var pan by viewport::pan
    var trackpadPosition by viewport::trackpadPosition
    var pressIndicator by remember { mutableStateOf<PressIndicator?>(null) }
    var showClipboardDialog by remember(session) { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(true) }
    var isExiting by remember { mutableStateOf(false) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var bottomBarHeight by remember { mutableIntStateOf(0) }
    var toolbarWidth by remember { mutableIntStateOf(0) }
    var keyboardActive by remember { mutableStateOf(false) }
    var modifiers by remember { mutableIntStateOf(0) }
    var backgroundedAt by remember { mutableLongStateOf(0L) }
    var cursorShape by remember(session) { mutableStateOf<SessionEvent.Cursor?>(null) }
    var cursorPosition by remember { mutableStateOf<IntOffset?>(null) }
    var pointerMode by rememberSaveable(connection.id) { mutableStateOf(initialPointerMode) }
    val trackpadMode = pointerMode == SessionPointerMode.Trackpad
    var desktopScreens by remember { mutableStateOf<List<DisplayScreen>>(emptyList()) }
    var needsMacSessionChoice by remember(session) { mutableStateOf(false) }
    var supportsCurtainMode by remember(session) { mutableStateOf(false) }
    var isAppleRemote by remember(session) { mutableStateOf(false) }
    var supportsFileUpload by remember(session) { mutableStateOf(false) }
    var supportsFileDownload by remember(session) { mutableStateOf(false) }
    var changingQuality by remember(session) { mutableStateOf(false) }
    var selectedDisplayId by remember(session) { mutableStateOf<Int?>(null) }
    var pendingDisplayRestore by remember(session) {
        mutableStateOf(connection.preferredDisplayId.takeIf { connection.rememberSelectedDisplay })
    }
    val currentConnection by rememberUpdatedState(connection)
    val displaySwitch = remember(session) { Mutex() }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<PendingDownload?>(null) }
    var downloadLabel by remember { mutableStateOf<String?>(null) }
    var showFileTransfers by remember { mutableStateOf(false) }
    var disconnectRequest by remember(session) { mutableStateOf<DisconnectRequest?>(null) }
    var sentFiles by remember { mutableStateOf<List<SessionTransferItem>>(emptyList()) }
    var receivedFiles by remember { mutableStateOf<List<SessionTransferItem>>(emptyList()) }
    var pendingUpload by remember { mutableStateOf<PendingUpload?>(null) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }
    var uploadLabel by remember { mutableStateOf<String?>(null) }

    fun resetTransferProgress(): PendingDownload? {
        val unfinished = pendingDownload
        uploadJob?.cancel()
        uploadJob = null
        pendingUpload = null
        pendingDownload = null
        uploadLabel = null
        downloadLabel = null
        return unfinished
    }

    val sendFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (observing || !supportsFileUpload || uris.isEmpty() || pendingUpload != null || status != SessionStatus.Connected) {
            return@rememberLauncherForActivityResult
        }
        val handle = session.nextTransferHandle()
        pendingUpload = PendingUpload(handle, uris)
        uploadLabel = context.getString(R.string.session_upload_preparing)
        scope.launch {
            try {
                session.announceUpload(
                    handle,
                    uris.indices.map { index -> "file:///android/$index" },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (pendingUpload?.handle == handle) {
                    pendingUpload = null
                    uploadLabel = null
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.session_upload_failed, error.message ?: ""),
                    )
                }
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    val pointerMemory = remember(session) { PointerMemory() }
    val taps = remember(session) { DeferredTapController() }
    val twoFingerTaps = remember(session) { DeferredTapController() }
    val trackPointer: (Int, Int) -> Unit = { x, y ->
        cursorPosition = IntOffset(x, y)
        pointerMemory.remember(x, y)
    }

    val toggleObserveMode: (Boolean) -> Unit = { enabled ->
        disconnectRequest = null
        session.setObserveMode(enabled)
        observing = enabled
        if (enabled) {
            keyboardActive = false
            modifiers = 0
            showClipboardDialog = false
            pressIndicator = null
            taps.cancel()
            val pending = pendingUpload
            pendingUpload = null
            if (pending != null) {
                scope.launch { runCatching { session.cancelTransfer(pending.handle) } }
            }
        }
    }


    val imeVisible = WindowInsets.isImeVisible
    // Focus may survive the IME being dismissed with Back. Drop the accessory row shortly
    // after the IME really goes away, but leave enough time for it to open on slow devices.
    LaunchedEffect(keyboardActive, imeVisible) {
        if (keyboardActive && !imeVisible) {
            delay(IME_OPEN_GRACE_MS)
            keyboardActive = false
        }
    }

    LaunchedEffect(keyboardActive) {
        if (keyboardActive) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    val exitSession: () -> Unit = {
        if (!isExiting) {
            isExiting = true
            val unfinished = resetTransferProgress()
            if (status == SessionStatus.Connecting && unfinished == null) {
                session.close()
                onExit()
            } else {
                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    try {
                        session.disconnect()
                    } finally {
                        discardDownload(unfinished)
                        onExit()
                    }
                }
            }
        }
    }

    val requestExit: () -> Unit = {
        disconnectRequest = DisconnectRequest.create(
            currentConnection, status == SessionStatus.Connected && !observing,
            interruptsTransfers = pendingUpload != null || pendingDownload != null,
        )
        if (disconnectRequest == null) exitSession()
    }
    BackHandler { requestExit() }
    // When the soft keyboard is up, Back should close it before it disconnects.
    BackHandler(enabled = keyboardActive) { keyboardActive = false }

    if (connection.keepScreenAwake && status == SessionStatus.Connected && foreground && !isExiting) {
        DisposableEffect(view) {
            val wasKeepingScreenOn = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = wasKeepingScreenOn }
        }
    }

    DisposableEffect(isFullscreen) {
        val controller = view.context.findActivity()?.window?.let {
            WindowCompat.getInsetsController(it, view)
        }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isFullscreen) {
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // The canvas stays dark in both app themes, so system bar icons stay light.
    SystemBarIconAppearance(darkIcons = false)

    suspend fun selectDisplay(id: Int?, userInitiated: Boolean) {
        try {
            if (!userInitiated || id != selectedDisplayId) {
                session.selectDisplay(id)
            }
            selectedDisplayId = id
            if (userInitiated) {
                onPreferredDisplayChange(id)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (userInitiated) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        error.message ?: context.getString(R.string.session_displays),
                    )
                }
            }
        }
    }

    fun shareLocalText() {
        if (observing) return
        val text = clipboard.reply(view.hasWindowFocus()) ?: return
        scope.launch {
            try {
                session.sendClipboard(text, automatic = true)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.d("SessionScreen", "Automatic clipboard send failed (${error.javaClass.simpleName})")
            }
        }
    }

    LaunchedEffect(session) {
        session.events.collect { event ->
            if (isExiting) return@collect
            when (event) {
                SessionEvent.MacSessionChoiceRequired -> {
                    disconnectRequest = null
                    needsMacSessionChoice = true
                    status = SessionStatus.Failed
                }
                SessionEvent.Connecting -> {
                    disconnectRequest = null
                    needsMacSessionChoice = false
                    supportsCurtainMode = false
                    isAppleRemote = false
                    supportsFileUpload = false
                    supportsFileDownload = false
                    clipboard.reset()
                    clipboardFetchRequested = false
                    clipboardSendRequested = false
                    clipboardFetch?.cancel()
                    showClipboardDialog = false
                    status = SessionStatus.Connecting
                    val unfinished = resetTransferProgress()
                    scope.launch { discardDownload(unfinished) }
                    cursorPosition = null
                    cursorShape = null
                    desktopScreens = emptyList()
                    selectedDisplayId = null
                    pendingDisplayRestore = currentConnection.preferredDisplayId
                        .takeIf { currentConnection.rememberSelectedDisplay }
                    // A new session starts on a fresh transport: the old bitmap is
                    // cropped to a viewport the new session knows nothing about.
                    frame = null
                }
                is SessionEvent.Connected -> {
                    supportsCurtainMode = event.supportsCurtainMode
                    isAppleRemote = event.appleRemote
                    supportsFileUpload = event.fileUpload
                    supportsFileDownload = event.fileDownload
                    status = SessionStatus.Connected
                    remoteWidth = event.width
                    remoteHeight = event.height
                    // The core publishes a whole-framebuffer viewport on connect and
                    // emits the new crop with its first complete frame.
                    frame = null
                    login.accepted()?.let { accepted ->
                        if (!onRemember(accepted)) {
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.session_remember_failed)) }
                        }
                    }
                    onConnected()
                }
                is SessionEvent.Resized -> {
                    remoteWidth = event.width
                    remoteHeight = event.height
                    // Frame and viewport stay paired: keep the previous crop so the
                    // stale bitmap is drawn at its real geometry instead of being
                    // stretched over the whole framebuffer.
                    if (!viewportFits(frame?.viewport, event.width, event.height)) {
                        frame = null
                    }
                }
                is SessionEvent.DesktopLayout -> {
                    remoteWidth = event.width
                    remoteHeight = event.height
                    desktopScreens = event.screens
                    if (!viewportFits(frame?.viewport, event.width, event.height)) {
                        frame = null
                    }
                    displaySwitch.withLock {
                        if (status == SessionStatus.Connected) {
                            val selected = selectedDisplayId
                            val remembered = pendingDisplayRestore
                            if (selected != null && desktopScreens.none { it.id == selected }) {
                                selectedDisplayId = null
                                selectDisplay(null, userInitiated = false)
                            } else if (remembered != null && desktopScreens.any { it.id == remembered }) {
                                pendingDisplayRestore = null
                                selectDisplay(remembered, userInitiated = false)
                            }
                        }
                    }
                }
                is SessionEvent.Cursor -> cursorShape = event
                is SessionEvent.Frame -> {
                    status = SessionStatus.Connected
                    frame = event
                }
                is SessionEvent.UploadRequest -> {
                    val pending = pendingUpload?.takeIf { it.handle == event.handle }
                    if (pending != null && uploadJob == null) {
                        uploadJob = scope.launch {
                            val message = try {
                                val names = uploadPickedFiles(
                                    session = session,
                                    handle = event.handle,
                                    destination = event.path,
                                    uris = pending.uris,
                                    resolver = context.contentResolver,
                                    cacheDir = context.cacheDir,
                                ) { name -> uploadLabel = name }
                                sentFiles = names.map { SessionTransferItem(it, event.path) } + sentFiles
                                context.getString(R.string.session_upload_done, names.size)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                runCatching { session.cancelTransfer(event.handle) }
                                context.getString(
                                    R.string.session_upload_failed,
                                    error.message ?: error.javaClass.simpleName,
                                )
                            } finally {
                                if (pendingUpload?.handle == event.handle) {
                                    pendingUpload = null
                                    uploadLabel = null
                                    uploadJob = null
                                }
                            }
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                }
                is SessionEvent.TransferProgress -> {
                    if (pendingUpload?.handle == event.handle) event.name?.let { uploadLabel = it }
                }
                is SessionEvent.TransferComplete -> Unit
                is SessionEvent.RemoteClipboard -> {
                    val requested = clipboardFetch?.isActive == true
                    clipboardFetch?.cancel()
                    clipboard.receive(event.text, view.hasWindowFocus(), requested)
                }
                SessionEvent.ClipboardRequest -> shareLocalText()
                is SessionEvent.DownloadProgress -> {
                    if (pendingDownload?.handle == event.handle) {
                        downloadLabel = buildString {
                            append(
                                event.name
                                    ?: context.getString(R.string.session_download_progress_fallback),
                            )
                            if (event.total > 0) {
                                append(' ')
                                append((event.bytes.toDouble() / event.total * 100).toInt().coerceIn(0, 100))
                                append('%')
                            }
                        }
                    }
                }
                is SessionEvent.DownloadComplete -> {
                    val download = pendingDownload?.takeIf { it.handle == event.handle }
                    if (download != null) {
                        pendingDownload = null
                        downloadLabel = null
                        scope.launch {
                            val items = withContext(Dispatchers.IO) {
                                val saved = if (event.error == null) {
                                    download.root.listFiles().orEmpty().sortedBy { it.name }
                                } else {
                                    emptyList()
                                }
                                if (saved.isEmpty()) download.root.deleteRecursively()
                                saved
                            }
                            if (items.isNotEmpty()) {
                                receivedFiles = items.map {
                                    SessionTransferItem(it.name, download.root.absolutePath, it)
                                } + receivedFiles
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.session_transfer_download_complete),
                                )
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.getString(
                                        R.string.session_download_failed,
                                        event.error ?: context.getString(R.string.session_transfer_no_downloaded_items),
                                    ),
                                )
                            }
                        }
                    }
                }
                is SessionEvent.DragInfo,
                is SessionEvent.FileTransfer,
                -> Unit
                is SessionEvent.Disconnected -> {
                    disconnectRequest = null
                    clipboard.reset()
                    clipboardFetchRequested = false
                    clipboardSendRequested = false
                    clipboardFetch?.cancel()
                    showClipboardDialog = false
                    status = SessionStatus.Disconnected
                    val unfinished = resetTransferProgress()
                    scope.launch { discardDownload(unfinished) }
                    recovery.failed(event.message ?: context.getString(R.string.session_status_disconnected), retryable = true)
                }
                is SessionEvent.Failed -> {
                    disconnectRequest = null
                    clipboard.reset()
                    clipboardFetchRequested = false
                    clipboardSendRequested = false
                    clipboardFetch?.cancel()
                    showClipboardDialog = false
                    status = SessionStatus.Failed
                    val unfinished = resetTransferProgress()
                    scope.launch { discardDownload(unfinished) }
                    login.failed(event.challenge)
                    if (event.challenge == null) recovery.failed(event.reason, event.retryable && !login.usingGuestAccess)
                }
            }
        }
    }

    DisposableEffect(session, clipboardSharing, observing, status, view) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener { shareLocalText() }
        val listen = clipboardSharing && !observing && status == SessionStatus.Connected
        if (listen) manager?.addPrimaryClipChangedListener(listener)
        onDispose { if (listen) manager?.removePrimaryClipChangedListener(listener) }
    }

    fun sendClipboardText(text: String?) {
        val content = text ?: clipboard.localText()
        scope.launch {
            if (content.isNullOrEmpty()) {
                snackbarHostState.showSnackbar(context.getString(R.string.session_clipboard_empty))
                return@launch
            }
            try {
                session.sendClipboard(content)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val message = if (error is ClipboardEncodingException) R.string.session_clipboard_latin1_only
                    else R.string.session_clipboard_send_failed
                snackbarHostState.showSnackbar(context.getString(message))
            }
        }
    }

    fun setClipboardSharing(enabled: Boolean) {
        clipboard.shared = enabled
        clipboardSharing = enabled
        scope.launch {
            try {
                session.setClipboardSharing(enabled)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                snackbarHostState.showSnackbar(context.getString(R.string.session_clipboard_settings_failed))
            }
        }
    }

    // A popup owns its own window. Wait for the session to regain focus before
    // requesting the clipboard so an immediate reply can be copied.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(session, clipboardSendRequested, windowFocused) {
        if (!clipboardSendRequested || !windowFocused || !view.hasWindowFocus()) return@LaunchedEffect
        clipboardSendRequested = false
        if (status == SessionStatus.Connected && !observing) sendClipboardText(null)
    }
    LaunchedEffect(session, clipboardFetchRequested, windowFocused, showClipboardDialog) {
        if (!clipboardFetchRequested || showClipboardDialog || !windowFocused || !view.hasWindowFocus()) {
            return@LaunchedEffect
        }
        clipboardFetchRequested = false
        if (status != SessionStatus.Connected) return@LaunchedEffect
        clipboardFetch = scope.launch {
            try {
                if (session.requestClipboard()) {
                    delay(5_000)
                    snackbarHostState.showSnackbar(context.getString(R.string.session_clipboard_no_reply))
                } else if (view.hasWindowFocus() && !clipboard.copyLatest()) {
                    snackbarHostState.showSnackbar(context.getString(R.string.session_clipboard_copy_first))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                snackbarHostState.showSnackbar(context.getString(R.string.session_clipboard_fetch_failed))
            }
        }
    }

    LaunchedEffect(session) {
        session.connect()
    }

    // A backgrounded app may come back with a suspended socket: the poller is still
    // blocked in a read that never returns, so no event ever flips the UI out of
    // "Connected". Rebuild the transport once the app has been away long enough.
    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    clipboardFetchRequested = false
                    clipboardSendRequested = false
                    clipboardFetch?.cancel()
                }
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    backgroundedAt = SystemClock.elapsedRealtime()
                    scope.launch { session.checkpointPreview() }
                }
                Lifecycle.Event.ON_START -> {
                    foreground = true
                    val leftAt = backgroundedAt
                    backgroundedAt = 0L
                    if (leftAt == 0L || isExiting || login.challenge != null || needsMacSessionChoice || login.usingGuestAccess) return@LifecycleEventObserver
                    val away = SystemClock.elapsedRealtime() - leftAt
                    if (away >= RESUME_RECONNECT_MS && status == SessionStatus.Connected) {
                        status = SessionStatus.Connecting
                        scope.launch { session.reconnect() }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(session) {
        onDispose { session.close() }
    }

    fun retry(manual: Boolean) {
        if (!manual && login.usingGuestAccess) return
        if (isExiting || !foreground || needsMacSessionChoice || login.challenge != null || !recovery.takeRetry(manual)) return
        status = SessionStatus.Connecting
        scope.launch { session.reconnect() }
    }

    // Leaving the foreground, cancelling, or entering authentication cancels the timer.
    LaunchedEffect(session, recovery.failure, foreground, isExiting, needsMacSessionChoice, login.challenge) {
        if (!foreground || isExiting || needsMacSessionChoice || login.challenge != null) return@LaunchedEffect
        recovery.nextDelayMillis?.let {
            delay(it)
            retry(manual = false)
        }
    }
    LaunchedEffect(session, status) {
        if (status == SessionStatus.Connected) {
            delay(10_000)
            recovery.stable()
        }
    }

    if (!isExiting && !needsMacSessionChoice && login.challenge == null) recovery.failure?.let { failure ->
        AlertDialog(
            onDismissRequest = exitSession,
            title = { Text(stringResource(R.string.session_status_disconnected)) },
            text = {
                Column {
                    Text(failure.message)
                    recovery.nextDelayMillis?.let {
                        Text(stringResource(R.string.session_retry_in, it / 1000))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { retry(manual = true) }) { Text(stringResource(R.string.session_reconnect)) }
            },
            dismissButton = {
                TextButton(onClick = exitSession) { Text(stringResource(R.string.library_cancel)) }
            },
        )
    }

    login.challenge?.let { required ->
        CredentialDialog(
            challenge = required,
            endpoint = connection.address,
            initialUsername = login.attempt.username,
            initiallyRemember = login.rememberLogin,
            onCancel = exitSession,
            onRequestPermission = {
                if (login.requestGuestAccess()) {
                    status = SessionStatus.Connecting
                    scope.launch { session.connect(MacSessionChoice.Guest) }
                }
            },
            onSubmit = { username, password, remember ->
                login.retry(username, password, remember)
                status = SessionStatus.Connecting
            },
        )
    }

    LaunchedEffect(remoteWidth, remoteHeight) {
        zoom = 1f
        if (!trackpadMode) pan = Offset.Zero
    }

    val waitingForScreen = status == SessionStatus.Connecting ||
        (status == SessionStatus.Connected && frame == null)
    val showControls = !waitingForScreen && chromeVisible

    // Sticky modifiers are pressed for the duration of one key chord, then released so
    // they cannot get stuck on the remote if the connection drops mid-chord.
    val sendKeysym: (Int) -> Unit = { keysym ->
        val active = MODIFIER_KEYS.filter { modifiers and it.flag != 0 }
        sendChord(keysym, active.map { it.keysym }, session::sendKey)
        modifiers = 0
    }

    val selectQuality: (QualityPreset) -> Unit = { preset ->
        if (preset != quality && status == SessionStatus.Connected && !changingQuality) {
            changingQuality = true
            scope.launch {
                try {
                    session.setQuality(preset)
                    onQualityChange(preset)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    changingQuality = false
                    snackbarHostState.showSnackbar(context.getString(R.string.session_quality_failed))
                } finally {
                    changingQuality = false
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SessionBackdrop)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val aspect = if (remoteWidth > 0 && remoteHeight > 0) {
            remoteWidth.toFloat() / remoteHeight.toFloat()
        } else {
            // Unknown until Connected; keep layout valid (NaN aspectRatio crashes).
            16f / 9f
        }
        val density = LocalDensity.current
        val verticalToolbar = maxWidth > maxHeight && maxHeight < 480.dp
        val sideInset = if (showControls && !keyboardActive && verticalToolbar) {
            with(density) { toolbarWidth.toDp() }.coerceAtMost((maxWidth - 1.dp).coerceAtLeast(0.dp))
        } else {
            0.dp
        }
        val bottomInset = if (keyboardActive || (showControls && !verticalToolbar)) {
            with(density) { bottomBarHeight.toDp() }.coerceAtMost((maxHeight - 1.dp).coerceAtLeast(0.dp))
        } else {
            0.dp
        }
        val canvasHeight = maxHeight - bottomInset
        val availableWidth = maxWidth - sideInset
        val fitWidth = min(availableWidth.value, canvasHeight.value * aspect)
        val canvasWidth = when (scaling) {
            SessionScaling.Fit -> fitWidth
            SessionScaling.Fill -> max(availableWidth.value, canvasHeight.value * aspect)
        }.dp
        val (minimumZoom, actualSizeZoom, maximumZoom) = sessionZoomBounds(
            canvasWidth.value, fitWidth, remoteWidth, connection.remoteScalePercent,
        )
        val safeDrawing = WindowInsets.safeDrawing
        val layoutDirection = LocalLayoutDirection.current

        // Keep a local crop around the visible region, and ask for a
        // sampling density that matches the current zoom. Debounced so a pinch
        // or pan settles before the region is re-requested.
        val canvasPixelWidth = with(density) { canvasWidth.toPx() }
        val canvasPixelHeight = canvasPixelWidth / aspect
        val screenPixelWidth = with(density) { availableWidth.toPx() }
        val screenPixelHeight = with(density) { canvasHeight.toPx() }
        val screenSizePx = IntSize(
            screenPixelWidth.roundToInt(),
            screenPixelHeight.roundToInt(),
        )
        val bandSizePx = IntSize(
            canvasPixelWidth.roundToInt(),
            canvasPixelHeight.roundToInt(),
        )
        // Express the full rendering area in the safe gesture space.
        val renderBounds = Rect(
            left = -safeDrawing.getLeft(density, layoutDirection).toFloat(),
            top = -safeDrawing.getTop(density).toFloat(),
            right = screenSizePx.width + with(density) { sideInset.toPx() } +
                safeDrawing.getRight(density, layoutDirection),
            bottom = screenSizePx.height + with(density) { bottomInset.toPx() } +
                safeDrawing.getBottom(density),
        )
        val centeredTrackpad = trackpadMode && !observing

        LaunchedEffect(bandSizePx, screenSizePx, centeredTrackpad, minimumZoom, maximumZoom, remoteWidth, remoteHeight) {
            viewport.stopRecentering()
            zoom = zoom.coerceIn(minimumZoom, maximumZoom)
            if (centeredTrackpad) {
                viewport.startRecentering(bandSizePx, screenSizePx)
            } else {
                pan = if (zoom <= minimumZoom * ZOOM_ACTIVE_EPSILON) Offset.Zero
                    else clampPan(pan, zoom, bandSizePx, screenSizePx)
            }
        }
        val selectPointerMode: (SessionPointerMode) -> Unit = { next ->
            viewport.stopRecentering()
            pointerMode = next
            onPointerModeChanged(next)
            if (next == SessionPointerMode.Trackpad && remoteWidth > 0 && remoteHeight > 0) {
                val (x, y) = pointerMemory.resolve(remoteWidth, remoteHeight)
                trackpadPosition = Offset(x.toFloat() / remoteWidth, y.toFloat() / remoteHeight)
            }
        }
        LaunchedEffect(
            session,
            status,
            zoom,
            pan,
            remoteWidth,
            remoteHeight,
            canvasPixelWidth,
            canvasPixelHeight,
            screenSizePx,
            renderBounds,
        ) {
            if (status != SessionStatus.Connected ||
                remoteWidth <= 1 || remoteHeight <= 1 || canvasPixelWidth < 1f
            ) {
                return@LaunchedEffect
            }
            delay(VIEWPORT_SETTLE_MS)
            val rect = visibleServerRect(
                zoom = zoom,
                pan = pan,
                bandWidth = canvasPixelWidth,
                bandHeight = canvasPixelHeight,
                screen = screenSizePx,
                renderBounds = renderBounds,
                serverWidth = remoteWidth,
                serverHeight = remoteHeight,
            )
            session.setViewport(
                rect[0],
                rect[1],
                rect[2],
                rect[3],
                renderBounds.width.roundToInt(),
            )
        }

        if (!observing) {
            HiddenKeyboardField(
                enabled = keyboardActive,
                focusRequester = focusRequester,
                onCodePoint = { code -> sendKeysym(x11Keysym(code)) },
                onBackspace = { sendKeysym(KEY_BACKSPACE) },
                onFocusChange = { focused -> if (!focused) keyboardActive = false },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        val zoomed = zoom > minimumZoom * ZOOM_ACTIVE_EPSILON
        val zoomEnabled = connection.zoomEnabled
        Box(
            modifier = Modifier
                .fillMaxSize()
                .absolutePadding(right = sideInset, bottom = bottomInset)
                // Gesture coordinates use the safe layout. The desktop draw
                // can overflow it into the remaining window area.
                .remoteTouchInput(
                    session, viewport, remoteWidth, remoteHeight, pointerMode, zoomEnabled,
                    observing, screenSizePx, bandSizePx, minimumZoom, maximumZoom,
                    scope, pointerMemory, taps, twoFingerTaps, trackPointer,
                    onWheelPosition = { cursorPosition = it },
                    onPressIndicator = { pressIndicator = it },
                    onToggleChrome = { chromeVisible = !chromeVisible },
                ),
        ) {
            RemoteSessionCanvas(
                viewport = viewport,
                frame = { frame },
                bandSizePx = bandSizePx,
                remoteWidth = remoteWidth,
                remoteHeight = remoteHeight,
                zoomed = zoomed,
                observing = observing,
                centeredTrackpad = centeredTrackpad,
                cursorPosition = { cursorPosition },
                cursorShape = { cursorShape },
            )
        }

        if (waitingForScreen && login.challenge == null && !isExiting) {
            SessionConnectingCard(
                connected = status == SessionStatus.Connected,
                endpoint = connection.address,
                onCancel = exitSession,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        pressIndicator?.let { indicator ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val position = if (centeredTrackpad) viewport.trackpadPointOnCanvas(bandSizePx, screenSizePx) else indicator.position
                        IntOffset(
                            (position.x - 11.dp.roundToPx()).roundToInt(),
                            (position.y - 11.dp.roundToPx()).roundToInt(),
                        )
                    }
                    .size(22.dp)
                    .shadow(8.dp, CircleShape)
                    .background(indicator.color.copy(alpha = 0.9f), CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            )
        }

        val transferLabels = listOfNotNull(uploadLabel, downloadLabel)
        if (transferLabels.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = transferLabels.joinToString("\n"),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (keyboardActive) {
            SessionKeyboardBar(
                modifiers = modifiers,
                onToggleModifier = { flag -> modifiers = modifiers xor flag },
                onKey = { keysym, _ -> sendKeysym(keysym) },
                onHideKeyboard = { keyboardActive = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomBarHeight = it.height }
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else if (showControls) {
            SessionToolbar(
                toolbarItems = toolbarItems,
                platform = if (isAppleRemote) RemotePlatform.MacOS else connection.platform,
                onCommand = { command ->
                    if (!observing && status == SessionStatus.Connected) {
                        sendChord(command.keysym, command.modifiers, session::sendKey)
                    }
                },
                observing = observing,
                onObserveModeChange = toggleObserveMode,
                quality = quality,
                changingQuality = changingQuality,
                onQualitySelected = selectQuality,
                isFullscreen = isFullscreen,
                pointerMode = pointerMode,
                onPointerModeSelected = selectPointerMode,
                vertical = verticalToolbar,
                onResetZoom = {
                    viewport.stopRecentering()
                    zoom = minimumZoom
                    if (centeredTrackpad) viewport.startRecentering(bandSizePx, screenSizePx) else pan = Offset.Zero
                },
                onActualSize = if (remoteWidth > 0) {{
                    viewport.stopRecentering()
                    zoom = actualSizeZoom.coerceIn(minimumZoom, maximumZoom)
                    if (centeredTrackpad) viewport.startRecentering(bandSizePx, screenSizePx) else pan = Offset.Zero
                }} else null,
                displays = desktopScreens,
                supportsCurtainMode = status == SessionStatus.Connected && supportsCurtainMode,
                onCurtainRequest = { enabled ->
                    scope.launch {
                        runCatching { session.requestCurtainMode(enabled) }.onFailure { error ->
                            snackbarHostState.showSnackbar(error.message ?: context.getString(R.string.session_curtain_error))
                        }
                    }
                },
                selectedDisplayId = selectedDisplayId,
                onSelectDisplay = { id ->
                    scope.launch {
                        displaySwitch.withLock {
                            if (status == SessionStatus.Connected &&
                                (id == null || desktopScreens.any { it.id == id })
                            ) {
                                pendingDisplayRestore = null
                                selectDisplay(id, userInitiated = true)
                            }
                        }
                    }
                },
                onKeyboard = { keyboardActive = true },
                connected = status == SessionStatus.Connected,
                clipboardSharing = clipboardSharing,
                onClipboardSharingChange = ::setClipboardSharing,
                onSendClipboard = { clipboardSendRequested = true },
                onCopyClipboard = {
                    clipboardFetch?.cancel()
                    clipboardFetchRequested = true
                },
                onComposeClipboardText = { showClipboardDialog = true },
                scaling = scaling,
                onScalingSelected = {
                    viewport.stopRecentering()
                    scaling = it
                    zoom = 1f
                    if (centeredTrackpad) viewport.startRecentering(bandSizePx, screenSizePx) else pan = Offset.Zero
                },
                macTarget = isAppleRemote || connection.platform == RemotePlatform.MacOS,
                onHotCorner = { x, y ->
                    if (!observing && status == SessionStatus.Connected && remoteWidth > 0 && remoteHeight > 0) {
                        viewport.stopRecentering()
                        val px = (x * remoteWidth).roundToInt().coerceIn(0, remoteWidth - 1)
                        val py = (y * remoteHeight).roundToInt().coerceIn(0, remoteHeight - 1)
                        trackPointer(px, py)
                        if (centeredTrackpad) {
                            trackpadPosition = Offset(x, y)
                            viewport.startRecentering(bandSizePx, screenSizePx)
                        }
                        session.sendPointer(px, py, 0)
                    }
                },
                supportsFileUpload = supportsFileUpload,
                supportsFileDownload = supportsFileDownload,
                onUpload = { sendFilePicker.launch(arrayOf("*/*")) },
                onDownload = { showDownloadDialog = true },
                canUpload = status == SessionStatus.Connected && pendingUpload == null,
                canDownload = status == SessionStatus.Connected && pendingDownload == null,
                onShowFileTransfers = { showFileTransfers = true },
                onScreenshot = {
                    captureScreenshot(context, frame?.bitmap, scope, snackbarHostState)
                },
                onToggleFullscreen = { isFullscreen = !isFullscreen },
                onHideChrome = { chromeVisible = false },
                onDisconnect = requestExit,
                modifier = Modifier
                    .align(if (verticalToolbar) AbsoluteAlignment.CenterRight else Alignment.BottomCenter)
                    .onSizeChanged {
                        if (verticalToolbar) toolbarWidth = it.width else bottomBarHeight = it.height
                    }
                    .absolutePadding(right = if (verticalToolbar) 8.dp else 0.dp, bottom = if (verticalToolbar) 0.dp else 8.dp)
                    .touchBarrier(),
            )
        } else if (!waitingForScreen) {
            ChromeRestoreButton(
                onClick = { chromeVisible = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
            )
        }


        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 16.dp, end = 16.dp, bottom = if (chromeVisible && !keyboardActive) 88.dp else 76.dp),
        )
    }

    disconnectRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { disconnectRequest = null },
            title = {
                Text(stringResource(if (request.interruptsTransfers) R.string.session_transfer_in_progress else R.string.session_disconnect))
            },
            text = {
                Column {
                    if (request.action != DisconnectAction.None) {
                        Text(stringResource(R.string.disconnect_action_confirmation, stringResource(request.action.title)))
                    }
                    if (request.interruptsTransfers) Text(stringResource(R.string.session_transfer_disconnect_warning))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (disconnectRequest === request && !isExiting) {
                        disconnectRequest = null
                        taps.cancel()
                        twoFingerTaps.cancel()
                        request.sendConfirmedAction(
                            currentConnection, status == SessionStatus.Connected && !observing, remoteWidth, remoteHeight,
                            session::releaseHeldInput, session::sendPointer, session::sendKey,
                        )
                        exitSession()
                    }
                }) {
                    Text(stringResource(R.string.session_disconnect))
                }
            },
            dismissButton = {
                TextButton(onClick = { disconnectRequest = null }) {
                    Text(stringResource(R.string.library_cancel))
                }
            },
        )
    }

    if (showFileTransfers) {
        SessionFileTransfers(
            receivedFiles = receivedFiles,
            sentFiles = sentFiles,
            onDismiss = { showFileTransfers = false },
        )
    }

    if (showDownloadDialog) {
        DownloadDialog(
            onDismiss = { showDownloadDialog = false },
            onDownload = { path ->
                showDownloadDialog = false
                if (supportsFileDownload && pendingDownload == null && status == SessionStatus.Connected) {
                    val folder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: File(context.filesDir, "downloads")
                    val root = File(folder, "transfers/${UUID.randomUUID()}")
                    val handle = session.nextTransferHandle()
                    pendingDownload = PendingDownload(handle, root)
                    downloadLabel = context.getString(R.string.session_download_preparing)
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                check(root.mkdirs()) {
                                    context.getString(R.string.session_transfer_create_folder_failed)
                                }
                            }
                            if (pendingDownload?.handle != handle) {
                                discardDownload(PendingDownload(handle, root))
                                return@launch
                            }
                            session.startDownload(handle, path, root.absolutePath)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            if (pendingDownload?.handle == handle) {
                                pendingDownload = null
                                downloadLabel = null
                                discardDownload(PendingDownload(handle, root))
                                snackbarHostState.showSnackbar(
                                    context.getString(
                                        R.string.session_download_failed,
                                        error.message ?: error.javaClass.simpleName,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        )
    }

    if (needsMacSessionChoice) {
        fun select(choice: MacSessionChoice) {
            needsMacSessionChoice = false
            scope.launch { session.connect(choice) }
        }
        AlertDialog(
            onDismissRequest = exitSession,
            title = { Text(stringResource(R.string.session_mac_choice_title)) },
            text = { Text(stringResource(R.string.session_mac_choice_hint)) },
            confirmButton = {
                Column {
                    TextButton(onClick = { select(MacSessionChoice.Personal) }) {
                        Text(stringResource(R.string.session_mac_personal))
                    }
                    TextButton(onClick = { select(MacSessionChoice.Share) }) {
                        Text(stringResource(R.string.session_mac_share))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = exitSession) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (!observing && showClipboardDialog && status == SessionStatus.Connected) {
        ClipboardComposeDialog(
            onDismiss = { showClipboardDialog = false },
            onSend = { text ->
                showClipboardDialog = false
                sendClipboardText(text)
            },
        )
    }
}

/** True when a stored crop still lies inside a framebuffer of [width] × [height]. */
private fun viewportFits(viewport: SessionEvent.Viewport?, width: Int, height: Int): Boolean {
    if (viewport == null) return false
    if (viewport.width <= 0 || viewport.height <= 0) return false
    return viewport.x + viewport.width <= width && viewport.y + viewport.height <= height
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(enabled = enabled, onClick = onClick) {
        Icon(imageVector = icon, contentDescription = label)
    }
}

/**
 * Invisible input field that feeds hardware and IME keystrokes to the remote. The
 * leading zero-width space keeps the field non-empty, so Backspace is observable even
 * when the user has not typed anything yet. Composing regions are not forwarded; only
 * committed text is, which keeps CJK IMEs from streaming pinyin letters to the host.
 */
@Composable
private fun HiddenKeyboardField(
    enabled: Boolean,
    focusRequester: FocusRequester,
    onCodePoint: (Int) -> Unit,
    onBackspace: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val buffer = remember(enabled) { RemoteKeyboardBuffer() }
    val send by rememberUpdatedState(onCodePoint)
    LaunchedEffect(buffer) { if (enabled) buffer.forward { send(it) } }
    BasicTextField(
        // Dialog dismissal must not move IME focus into the remote session.
        enabled = enabled,
        state = buffer.field,
        inputTransformation = buffer.handleBackspace(onBackspace),
        modifier = modifier
            .size(1.dp)
            .alpha(0f)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Transparent),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.None,
            autoCorrectEnabled = false,
            showKeyboardOnFocus = true,
        ),
        cursorBrush = SolidColor(Color.Transparent),
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 1),
    )
}

@Composable
private fun SessionKeyboardBar(
    modifiers: Int,
    onToggleModifier: (Int) -> Unit,
    onKey: (Int, String) -> Unit,
    onHideKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val esc = stringResource(R.string.session_key_esc)
    val tab = stringResource(R.string.session_key_tab)
    val enter = stringResource(R.string.session_key_enter)
    val backspace = stringResource(R.string.session_key_backspace)
    val arrowLeft = stringResource(R.string.session_key_arrow_left)
    val arrowUp = stringResource(R.string.session_key_arrow_up)
    val arrowDown = stringResource(R.string.session_key_arrow_down)
    val arrowRight = stringResource(R.string.session_key_arrow_right)

    Surface(
        modifier = modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .touchBarrier(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MODIFIER_KEYS.forEach { key ->
                    FilterChip(
                        selected = modifiers and key.flag != 0,
                        label = { Text(stringResource(key.labelRes)) },
                        onClick = { onToggleModifier(key.flag) },
                    )
                }
                KeyboardBarDivider()
                AssistChip(onClick = { onKey(KEY_ESCAPE, esc) }, label = { Text(esc) })
                AssistChip(onClick = { onKey(KEY_TAB, tab) }, label = { Text(tab) })
                ToolbarButton(
                    icon = Icons.AutoMirrored.Outlined.KeyboardReturn,
                    label = enter,
                    onClick = { onKey(KEY_RETURN, enter) },
                )
                ToolbarButton(
                    icon = Icons.AutoMirrored.Outlined.Backspace,
                    label = backspace,
                    onClick = { onKey(KEY_BACKSPACE, backspace) },
                )
                KeyboardBarDivider()
                ToolbarButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    label = arrowLeft,
                    onClick = { onKey(KEY_LEFT, arrowLeft) },
                )
                ToolbarButton(
                    icon = Icons.Filled.KeyboardArrowUp,
                    label = arrowUp,
                    onClick = { onKey(KEY_UP, arrowUp) },
                )
                ToolbarButton(
                    icon = Icons.Filled.KeyboardArrowDown,
                    label = arrowDown,
                    onClick = { onKey(KEY_DOWN, arrowDown) },
                )
                ToolbarButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    label = arrowRight,
                    onClick = { onKey(KEY_RIGHT, arrowRight) },
                )
            }
            KeyboardBarDivider()
            ToolbarButton(
                icon = Icons.Outlined.KeyboardHide,
                label = stringResource(R.string.session_keyboard_hide),
                onClick = onHideKeyboard,
            )
        }
    }
}

@Composable
private fun KeyboardBarDivider() {
    VerticalDivider(Modifier.height(26.dp))
}

@Composable
private fun ChromeRestoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(R.string.session_show_controls),
        )
    }
}

@Composable
private fun DownloadDialog(
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
) {
    var path by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_download)) },
        text = {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.session_download_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onDownload(path.trim()) },
                enabled = path.isNotBlank(),
            ) {
                Text(stringResource(R.string.session_download_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_cancel))
            }
        },
    )
}

@Composable
private fun ClipboardComposeDialog(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_clipboard_action)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.session_clipboard_hint)) },
                minLines = 3,
                maxLines = 5,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSend(text) }, enabled = text.isNotEmpty()) {
                Text(stringResource(R.string.session_clipboard_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }
        },
    )
}

private const val IME_OPEN_GRACE_MS = 1200L
private const val RESUME_RECONNECT_MS = 5_000L
private const val VIEWPORT_SETTLE_MS = 120L

// Wheel scrolling: one RFB button press/release pair per step of finger travel.

// X11 keysyms for the keys the accessory bar can send.
internal const val KEY_SHIFT = 0xFFE1
internal const val KEY_CONTROL = 0xFFE3
internal const val KEY_ALT = 0xFFE9
internal const val KEY_SUPER = 0xFFEB
internal const val KEY_ESCAPE = 0xFF1B
internal const val KEY_TAB = 0xFF09
internal const val KEY_RETURN = 0xFF0D
private const val KEY_BACKSPACE = 0xFF08
internal const val KEY_LEFT = 0xFF51
internal const val KEY_UP = 0xFF52
internal const val KEY_RIGHT = 0xFF53
internal const val KEY_DOWN = 0xFF54

private const val MODIFIER_CONTROL = 1
private const val MODIFIER_ALT = 1 shl 1
private const val MODIFIER_SHIFT = 1 shl 2
private const val MODIFIER_META = 1 shl 3

private data class PendingDownload(val handle: Int, val root: File)

private suspend fun discardDownload(download: PendingDownload?) {
    if (download != null) {
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) { download.root.deleteRecursively() }
    }
}

private data class PendingUpload(val handle: Int, val uris: List<Uri>)

private data class ModifierKey(val flag: Int, val keysym: Int, val labelRes: Int)

private val MODIFIER_KEYS = listOf(
    ModifierKey(MODIFIER_CONTROL, KEY_CONTROL, R.string.session_key_ctrl),
    ModifierKey(MODIFIER_ALT, KEY_ALT, R.string.session_key_alt),
    ModifierKey(MODIFIER_SHIFT, KEY_SHIFT, R.string.session_key_shift),
    ModifierKey(MODIFIER_META, KEY_SUPER, R.string.session_key_meta),
)

/**
 * Keeps a bar in the pointer hit path so taps on its padding cannot fall through to the
 * canvas underneath, without consuming anything from its child buttons or scroller.
 */
private fun Modifier.touchBarrier(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent()
        }
    }
}

/** Saves the current remote frame to the photo library, reporting through the snackbar. */
private fun captureScreenshot(
    context: Context,
    frame: Bitmap?,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    val snapshot = frame?.copy(Bitmap.Config.ARGB_8888, false)
    if (snapshot == null) {
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.session_screenshot_empty))
        }
        return
    }
    scope.launch {
        val saved = withContext(Dispatchers.IO) {
            runCatching { writeScreenshot(context, snapshot) }.isSuccess
        }
        snackbarHostState.showSnackbar(
            context.getString(
                if (saved) {
                    R.string.session_screenshot_saved
                } else {
                    R.string.session_screenshot_failed
                },
            ),
        )
    }
}

private fun writeScreenshot(context: Context, bitmap: Bitmap): Uri {
    require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "Saving screenshots requires Android 10"
    }
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Scrctl-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Scrctl")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        values,
    ) ?: error("Unable to create a photo entry")
    try {
        resolver.openOutputStream(uri)?.use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                error("Unable to encode the screenshot")
            }
        } ?: error("Unable to open the photo entry")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    }
    return uri
}
