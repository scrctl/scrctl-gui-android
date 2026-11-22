package com.openscreens.app.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openscreens.app.R
import com.openscreens.app.data.NearbyDevice
import com.openscreens.app.ui.theme.ThemedSystemBarIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.scrctl.android.MirrorView
import java.util.concurrent.atomic.AtomicReference

@Composable
internal fun rememberIphoneIntegration(discover: Boolean): IphoneIntegration? {
    val context = LocalContext.current.applicationContext
    val controller = remember(context) { IphoneController(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(false) }
    var discoveryJob by remember { mutableStateOf<Job?>(null) }
    fun start(action: suspend () -> Unit) {
        if (!active || !discover || discoveryJob?.isCompleted == false) return
        discoveryJob = scope.launch { action() }
    }

    LifecycleResumeEffect(controller) {
        active = true
        onPauseOrDispose {
            active = false
            discoveryJob?.cancel()
            controller.cancel()
        }
    }
    LaunchedEffect(discover, active) {
        if (discover && active) {
            discoveryJob?.join()
            start(controller::discover)
        }
    }

    return IphoneIntegration(
        discovery = IphoneDiscoveryState(state.devices, state.busy, state.canCancel, state.message, state.pin),
        refresh = { start(controller::discover) },
        pair = { start(controller::pair) },
        prepare = { device -> start { controller.prepare(device) } },
        cancel = controller::cancel,
        session = { device, onExit -> IphoneSession(controller, device, active, onExit) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IphoneSession(
    controller: IphoneController,
    device: NearbyDevice.Iphone,
    active: Boolean,
    onExit: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val mirror = remember { AtomicReference<MirrorView?>() }
    ThemedSystemBarIcons()
    LaunchedEffect(device, active) {
        if (active) {
            controller.mirror(device) { frame ->
                val view = mirror.get()
                if (view == null) frame.bitmap.recycle() else view.submit(frame)
                view != null
            }
        }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(device.name) },
            navigationIcon = {
                IconButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.iphone_back))
                }
            },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (state.mirroring) {
                AndroidView(
                    factory = { context ->
                        MirrorView(context).apply {
                            acceptsFrame = { true }
                            onTouch = { id, point, down, move ->
                                if (id == 0) controller.queueTouch(point, down, move)
                            }
                            mirror.set(this)
                        }
                    },
                    onRelease = { view ->
                        mirror.compareAndSet(view, null)
                        view.clear()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (state.message != null && !state.mirroring) {
                Text(state.message.orEmpty())
            } else if (!state.hasFrame) {
                CircularProgressIndicator()
            }
        }
    }
}
