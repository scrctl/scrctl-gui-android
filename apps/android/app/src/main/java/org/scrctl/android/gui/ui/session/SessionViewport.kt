package org.scrctl.android.gui.ui.session

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class SessionViewport(private val scope: CoroutineScope) {
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    var trackpadPosition by mutableStateOf(Offset(0.5f, 0.5f))
    private var recenterJob: Job? = null
    private var recenterStart: Offset? = null
    private var recenterProgress = 1f

    fun trackpadPointOnCanvas(bandSizePx: IntSize, screenSizePx: IntSize): Offset = Offset(
        screenSizePx.width / 2f + (pan.x + (trackpadPosition.x - 0.5f) * bandSizePx.width) * zoom,
        screenSizePx.height / 2f + (pan.y + (trackpadPosition.y - 0.5f) * bandSizePx.height) * zoom,
    )

    fun followTrackpad(bandSizePx: IntSize, screenSizePx: IntSize) {
        val target = trackpadPan(trackpadPosition, zoom, bandSizePx, screenSizePx)
        val start = recenterStart
        pan = if (start == null) target else start + (target - start) * recenterProgress
    }

    fun stopRecentering() {
        recenterJob?.cancel()
        recenterJob = null
        recenterStart = null
    }

    fun startRecentering(bandSizePx: IntSize, screenSizePx: IntSize) {
        if (recenterJob?.isActive == true) return
        val target = trackpadPan(trackpadPosition, zoom, bandSizePx, screenSizePx)
        if ((target - pan).getDistance() * zoom < 0.5f) return
        recenterStart = pan
        recenterProgress = 0f
        recenterJob = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(500, easing = Easing { t -> t * t * (3f - 2f * t) }),
            ) { value, _ ->
                recenterProgress = value
                followTrackpad(bandSizePx, screenSizePx)
            }
            recenterStart = null
        }
    }
}

private const val MAX_LOGICAL_ZOOM = 4f

internal data class SessionZoomBounds(val minimum: Float, val actual: Float, val maximum: Float)

internal fun sessionZoomBounds(
    baseWidthDp: Float,
    fitWidthDp: Float,
    remoteWidth: Int,
    remoteScalePercent: Int,
): SessionZoomBounds {
    if (baseWidthDp <= 0f || fitWidthDp <= 0f || remoteWidth <= 0) {
        return SessionZoomBounds(1f, 1f, MAX_LOGICAL_ZOOM)
    }
    val minimum = fitWidthDp / baseWidthDp
    // The canvas width is already expressed in local, density-independent units.
    val remoteScale = remoteScalePercent.coerceAtLeast(1) / 100f
    val actual = (remoteWidth / remoteScale) / baseWidthDp
    return SessionZoomBounds(minimum, actual, max(minimum, actual * MAX_LOGICAL_ZOOM))
}

internal const val ZOOM_ACTIVE_EPSILON = 1.001f
/**
 * Keeps the pan inside the range where the scaled band still covers the local
 * screen on that axis; content smaller than the screen stays centred.
 */
internal fun trackpadPan(pointer: Offset, zoom: Float, band: IntSize, screen: IntSize): Offset {
    fun axis(position: Float, imageSize: Int, screenSize: Int): Float {
        val limit = (imageSize - screenSize / zoom) / 2f
        if (limit <= 0f) return 0f
        return ((0.5f - position) * imageSize).coerceIn(-limit, limit)
    }
    return Offset(axis(pointer.x, band.width, screen.width), axis(pointer.y, band.height, screen.height))
}

internal fun clampPan(value: Offset, zoom: Float, band: IntSize, screen: IntSize): Offset {
    val safeZoom = zoom
    // When the band covers the screen, keep its edges outside; when it is
    // smaller, let it move inside so a pinch stays anchored between the fingers
    // instead of snapping back to the centre.
    val maxX = abs(band.width * safeZoom - screen.width) / (2f * safeZoom)
    val maxY = abs(band.height * safeZoom - screen.height) / (2f * safeZoom)
    return Offset(
        x = value.x.coerceIn(-maxX, maxX),
        y = value.y.coerceIn(-maxY, maxY),
    )
}

/**
 * Maps a screen-space touch to the framebuffer. The band is drawn at the zoom-1
 * fit rect and scaled about its centre with a pan offset, so the inverse
 * transform lands in band coordinates, which map linearly to server pixels.
 */
internal fun remotePoint(
    position: Offset,
    screen: IntSize,
    band: IntSize,
    remoteWidth: Int,
    remoteHeight: Int,
    zoom: Float,
    pan: Offset,
): Pair<Int, Int> {
    if (band.width == 0 || band.height == 0) return 0 to 0
    val safeZoom = zoom.coerceAtLeast(0.01f)
    val bandX = band.width / 2f + (position.x - screen.width / 2f) / safeZoom - pan.x
    val bandY = band.height / 2f + (position.y - screen.height / 2f) / safeZoom - pan.y
    val x = (bandX / band.width * remoteWidth).roundToInt().coerceIn(0, remoteWidth - 1)
    val y = (bandY / band.height * remoteHeight).roundToInt().coerceIn(0, remoteHeight - 1)
    return x to y
}

/** Framebuffer region visible through the screen-sized window under zoom/pan. */
internal fun visibleServerRect(
    zoom: Float,
    pan: Offset,
    bandWidth: Float,
    bandHeight: Float,
    screen: IntSize,
    renderBounds: Rect,
    serverWidth: Int,
    serverHeight: Int,
): IntArray {
    val safeZoom = zoom.coerceAtLeast(0.01f)
    // The band is the 1x fit rect, scaled about its centre and panned in band
    // units; inverting the screen rect gives the visible band region.
    val centerX = screen.width / 2f
    val centerY = screen.height / 2f
    val left = (bandWidth / 2f + (renderBounds.left - centerX) / safeZoom - pan.x).coerceIn(0f, bandWidth)
    val right = (bandWidth / 2f + (renderBounds.right - centerX) / safeZoom - pan.x).coerceIn(0f, bandWidth)
    val top = (bandHeight / 2f + (renderBounds.top - centerY) / safeZoom - pan.y).coerceIn(0f, bandHeight)
    val bottom = (bandHeight / 2f + (renderBounds.bottom - centerY) / safeZoom - pan.y).coerceIn(0f, bandHeight)
    val scaleX = serverWidth / bandWidth
    val scaleY = serverHeight / bandHeight
    // Round outward so the delivered crop always covers the visible edges.
    val outLeft = floor(left * scaleX).toInt().coerceIn(0, serverWidth - 1)
    val outTop = floor(top * scaleY).toInt().coerceIn(0, serverHeight - 1)
    val outRight = ceil(right * scaleX).toInt().coerceIn(outLeft + 1, serverWidth)
    val outBottom = ceil(bottom * scaleY).toInt().coerceIn(outTop + 1, serverHeight)
    return intArrayOf(outLeft, outTop, outRight - outLeft, outBottom - outTop)
}
