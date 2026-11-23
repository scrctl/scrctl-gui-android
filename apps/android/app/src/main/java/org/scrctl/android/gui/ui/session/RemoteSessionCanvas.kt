package org.scrctl.android.gui.ui.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.scrctl.android.gui.session.SessionEvent

private val FramebufferBackground = Color(0xFF000000)

@Composable
internal fun BoxScope.RemoteSessionCanvas(
    viewport: SessionViewport,
    frame: () -> SessionEvent.Frame?,
    bandSizePx: IntSize,
    remoteWidth: Int,
    remoteHeight: Int,
    zoomed: Boolean,
    observing: Boolean,
    centeredTrackpad: Boolean,
    cursorPosition: () -> IntOffset?,
    cursorShape: () -> SessionEvent.Cursor?,
) {
    val live = frame() ?: return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val desktop = Size(bandSizePx.width * viewport.zoom, bandSizePx.height * viewport.zoom)
        val origin = center + viewport.pan * viewport.zoom - Offset(desktop.width / 2, desktop.height / 2)
        val crop = live.viewport
        val scaleX = desktop.width / remoteWidth
        val scaleY = desktop.height / remoteHeight
        val left = (origin.x + crop.x * scaleX).roundToInt()
        val top = (origin.y + crop.y * scaleY).roundToInt()
        val right = (origin.x + (crop.x + crop.width) * scaleX).roundToInt()
        val bottom = (origin.y + (crop.y + crop.height) * scaleY).roundToInt()
        fun DrawScope.drawDesktop() {
            drawImage(
                image = live.bitmap.asImageBitmap(),
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(live.bitmap.width, live.bitmap.height),
                dstOffset = IntOffset(left, top),
                dstSize = IntSize((right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1)),
                filterQuality = if (viewport.zoom > 1.2f) FilterQuality.Medium else FilterQuality.Low,
            )
        }
        if (zoomed) {
            drawDesktop()
        } else {
            val radius = CornerRadius(16.dp.toPx())
            val outline = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = origin.x, top = origin.y,
                        right = origin.x + desktop.width, bottom = origin.y + desktop.height,
                        cornerRadius = radius,
                    ),
                )
            }
            clipPath(outline) {
                drawRect(FramebufferBackground, topLeft = origin, size = desktop)
                drawDesktop()
                drawPath(outline, Color.White.copy(alpha = 0.08f), style = Stroke(1.dp.toPx()))
            }
        }
        // Without a separate cursor shape, the framebuffer is authoritative.
        // Some servers already draw their pointer there.
        if (!observing) {
            val shape = cursorShape() ?: return@Canvas
            val bitmap = shape.bitmap ?: return@Canvas
            val position = cursorPosition()
            val x = position?.x?.coerceIn(0, remoteWidth - 1)?.toFloat() ?: (remoteWidth * 0.5f)
            val y = position?.y?.coerceIn(0, remoteHeight - 1)?.toFloat() ?: (remoteHeight * 0.55f)
            val pointer = if (centeredTrackpad) {
                Offset(origin.x + viewport.trackpadPosition.x * desktop.width, origin.y + viewport.trackpadPosition.y * desktop.height)
            } else {
                Offset(origin.x + x * scaleX, origin.y + y * scaleY)
            }
            clipRect {
                val scale = 24.dp.toPx() / maxOf(bitmap.width, bitmap.height)
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(
                        (pointer.x - shape.hotspotX * scale).roundToInt(),
                        (pointer.y - shape.hotspotY * scale).roundToInt(),
                    ),
                    dstSize = IntSize(
                        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                        (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.None,
                )
            }
        }
    }
}
