package com.openscreens.app.ios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openscreens.app.R
import com.openscreens.app.ui.theme.OpenScreensTheme
import com.openscreens.app.ui.theme.ThemedSystemBarIcons
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.scrctl.android.MirrorView

class IphoneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = IphoneController(applicationContext)
        setContent {
            OpenScreensTheme {
                IphoneScreen(controller, ::finish)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IphoneScreen(controller: IphoneController, onExit: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var task by remember { mutableStateOf<Job?>(null) }
    val mirror = remember { AtomicReference<MirrorView?>() }
    ThemedSystemBarIcons()
    fun start(action: suspend () -> Unit) {
        if (task?.isCompleted == false) return
        task = scope.launch { action() }
    }
    fun disconnect() {
        mirror.get()?.releaseTouch()
        task?.cancel()
    }

    LifecycleResumeEffect(controller) {
        val previous = task
        task = scope.launch {
            previous?.join()
            controller.discover()
        }
        onPauseOrDispose {
            disconnect()
            controller.cancel()
        }
    }

    val goBack = { if (state.mirroring) disconnect() else onExit() }
    BackHandler(state.mirroring, onBack = goBack)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.library_iphone)) },
            navigationIcon = {
                IconButton(onClick = goBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.iphone_back))
                }
            },
        )
    }) { padding ->
        if (state.mirroring) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
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
                if (!state.hasFrame) CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { start(controller::discover) }, enabled = !state.busy) {
                            Text(stringResource(R.string.iphone_refresh))
                        }
                        Button(onClick = { start(controller::pair) }, enabled = !state.busy) {
                            Text(stringResource(R.string.iphone_pair))
                        }
                        if (state.canCancel) {
                            TextButton(onClick = controller::cancel) {
                                Text(stringResource(R.string.iphone_cancel))
                            }
                        }
                    }
                }
                if (state.busy) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                state.pin?.let { pin ->
                    item { Text(stringResource(R.string.iphone_pin, pin)) }
                }
                state.message?.let { message ->
                    item { Text(message) }
                }
                if (state.devices.isEmpty() && !state.busy) {
                    item { Text(stringResource(R.string.iphone_no_devices)) }
                }
                items(state.devices, key = Iphone::udid) { device ->
                    Column {
                        Text(device.name.ifBlank { device.udid }, style = MaterialTheme.typography.titleMedium)
                        if (device.pairing.isNotBlank()) Text(device.pairing)
                        Row {
                            TextButton(
                                onClick = {
                                    start {
                                        controller.mirror(device) { frame ->
                                            val view = mirror.get()
                                            if (view == null) frame.bitmap.recycle() else view.submit(frame)
                                            view != null
                                        }
                                    }
                                },
                                enabled = !state.busy,
                            ) { Text(stringResource(R.string.iphone_mirror)) }
                            TextButton(
                                onClick = { start { controller.prepare(device) } },
                                enabled = !state.busy,
                            ) { Text(stringResource(R.string.iphone_prepare)) }
                        }
                    }
                }
            }
        }
    }
}
