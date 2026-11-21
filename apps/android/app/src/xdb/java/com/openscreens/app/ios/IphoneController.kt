package com.openscreens.app.ios

import android.content.Context
import android.graphics.Bitmap
import com.openscreens.app.data.NearbyDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.scrctl.android.Geometry
import org.scrctl.android.NativeBridge
import org.scrctl.android.Point
import org.scrctl.android.VideoFrame
import uniffi.rfb_ffi.IosFrame
import uniffi.rfb_ffi.IosSession
import uniffi.rfb_ffi.IosTransport
import uniffi.rfb_ffi.iosListDevices
import java.io.File

internal data class IphoneState(
    val devices: List<NearbyDevice.Iphone> = emptyList(),
    val busy: Boolean = false,
    val canCancel: Boolean = false,
    val mirroring: Boolean = false,
    val hasFrame: Boolean = false,
    val message: String? = null,
    val pin: String? = null,
)

internal class IphoneController(private val context: Context) {
    private val mutableState = MutableStateFlow(IphoneState())
    val state = mutableState.asStateFlow()
    private val operations = Mutex()
    private val requestLock = Any()
    private var request: Pair<Long, Long>? = null
    private val input = ArrayDeque<Touch>()

    private data class Touch(val point: Point, val down: Boolean, val move: Boolean)

    fun queueTouch(point: Point, down: Boolean, move: Boolean) {
        synchronized(input) {
            if (move && input.lastOrNull()?.move == true) input.removeLast()
            input.addLast(Touch(point, down, move))
        }
    }

    fun cancel() {
        synchronized(requestLock) {
            request?.let { (runtime, client) -> NativeBridge.interrupt(runtime, client) }
        }
    }

    suspend fun discover() = operation { _, port -> refresh(port) }

    suspend fun pair() = operation { runtime, port ->
        request(runtime, "pair", JSONObject()
            .put("transport", "wifi")
            .put("display_brand", "scrctl")
            .put("timeout_ms", 120_000))
        refresh(port)
    }

    suspend fun prepare(device: NearbyDevice.Iphone) = operation { runtime, _ ->
        val cache = File(context.cacheDir, "ddi").apply { mkdirs() }
        request(runtime, "ddi.prepare", JSONObject()
            .put("udid", device.udid)
            .put("wifi", "auto")
            .put("cache_directory", cache.absolutePath))
        mutableState.value = mutableState.value.copy(message = "DDI prepared")
    }

    suspend fun mirror(device: NearbyDevice.Iphone, onFrame: (VideoFrame) -> Boolean) = operation { _, port ->
        synchronized(input) { input.clear() }
        IosSession.open(port, device.udid, IosTransport.WIFI, false).use { session ->
            var failure: Throwable? = null
            try {
                mutableState.value = mutableState.value.copy(mirroring = true, busy = false)
                var serial = 0uL
                while (true) {
                    currentCoroutineContext().ensureActive()
                    drainTouch(session)
                    val frame = session.nextFrame(serial, 40u) ?: continue
                    serial = frame.serial
                    if (onFrame(frame.toVideoFrame()) && !mutableState.value.hasFrame) {
                        mutableState.value = mutableState.value.copy(hasFrame = true)
                    }
                }
            } catch (error: Throwable) {
                failure = error
                throw error
            } finally {
                synchronized(input) { input.clear() }
                try {
                    session.shutdown()
                } catch (shutdownError: Exception) {
                    if (failure == null) throw shutdownError
                    failure.addSuppressed(shutdownError)
                }
            }
        }
    }

    private fun drainTouch(session: IosSession) {
        val pending = synchronized(input) { input.toList().also { input.clear() } }
        for (touch in pending) {
            session.touch(touch.point.x.toDouble(), touch.point.y.toDouble(), touch.down)
        }
    }

    private suspend fun operation(block: suspend (Long, UShort) -> Unit) = operations.withLock {
        withContext(Dispatchers.IO) {
            mutableState.value = mutableState.value.copy(busy = true, message = null)
            try {
                val directory = File(context.filesDir, "xdb").apply { mkdirs() }
                val runtime = NativeBridge.start(context, directory.absolutePath)
                AutoCloseable { NativeBridge.stop(runtime) }.use {
                    val port = NativeBridge.port(runtime)
                    check(port in 1..65535) { "XDB server did not report a valid port" }
                    currentCoroutineContext().ensureActive()
                    block(runtime, port.toUShort())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(message = error.message ?: error.javaClass.simpleName)
            } catch (error: LinkageError) {
                mutableState.value = mutableState.value.copy(message = error.message ?: "Could not load XDB")
            } finally {
                mutableState.value = mutableState.value.copy(busy = false, canCancel = false, mirroring = false, hasFrame = false, pin = null)
            }
        }
    }

    private fun refresh(port: UShort) {
        val response = JSONObject(iosListDevices(port, IosTransport.WIFI, 3_000u))
        val array = response.getJSONArray("devices")
        val devices = (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            val udid = item.optString("udid")
            if (udid.isBlank()) null else NearbyDevice.Iphone(
                udid, item.optString("name").replace(udid, "").trim().ifBlank { "iPhone" },
                item.optString("pairing"),
            )
        }.distinctBy { it.udid }
        val warnings = response.optJSONArray("warnings")
        val message = warnings?.let { list ->
            (0 until list.length()).joinToString("\n") { list.getString(it) }.ifBlank { null }
        }
        mutableState.value = mutableState.value.copy(devices = devices, message = message, pin = null)
    }

    private suspend fun request(runtime: Long, method: String, params: JSONObject) {
        val client = NativeBridge.connect(runtime) { raw ->
            runCatching {
                val event = JSONObject(raw)
                if (event.optString("event") == "pin") {
                    val pin = event.getJSONObject("data").getString("pin")
                    mutableState.value = mutableState.value.copy(pin = pin)
                }
            }
        }
        try {
            synchronized(requestLock) { request = runtime to client }
            currentCoroutineContext().ensureActive()
            mutableState.value = mutableState.value.copy(canCancel = true)
            val result = JSONObject(NativeBridge.request(runtime, client, method, params.toString()).json)
            if (!result.optBoolean("ok")) {
                val error = result.getJSONObject("error")
                error("${error.optString("code")}: ${error.optString("message")}")
            }
        } finally {
            synchronized(requestLock) { request = null }
            NativeBridge.close(runtime, client)
            mutableState.value = mutableState.value.copy(canCancel = false, pin = null)
        }
    }
}

private fun IosFrame.toVideoFrame(): VideoFrame {
    val frameWidth = width.toInt()
    val frameHeight = height.toInt()
    val stride = rowPitch.toInt()
    val argb = IntArray(frameWidth * frameHeight)
    for (row in 0 until frameHeight) {
        var source = row * stride
        for (column in 0 until frameWidth) {
            val blue = pixels[source++].toInt() and 255
            val green = pixels[source++].toInt() and 255
            val red = pixels[source++].toInt() and 255
            val alpha = pixels[source++].toInt() and 255
            argb[row * frameWidth + column] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }
    val bitmap = Bitmap.createBitmap(argb, frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
    val panelWidth = displayWidth?.toInt()?.takeIf { it > 0 } ?: frameWidth
    val panelHeight = displayHeight?.toInt()?.takeIf { it > 0 } ?: frameHeight
    val rotation = orientation?.let { Geometry.rotation(it) }
        ?.takeIf { displayWidth != null && displayHeight != null }
    val geometry = Geometry(frameWidth, frameHeight, panelWidth, panelHeight, rotation)
    return VideoFrame(bitmap, geometry, 0)
}
