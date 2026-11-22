package org.scrctl.android.gui.ui.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.scrctl.android.gui.R
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
    statusText: String,
) {
    val live = frame()
    if (live != null) {
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
            if (!observing) {
                val position = cursorPosition()
                val x = position?.x?.coerceIn(0, remoteWidth - 1)?.toFloat() ?: (remoteWidth * 0.5f)
                val y = position?.y?.coerceIn(0, remoteHeight - 1)?.toFloat() ?: (remoteHeight * 0.55f)
                val pointer = if (centeredTrackpad) {
                    Offset(origin.x + viewport.trackpadPosition.x * desktop.width, origin.y + viewport.trackpadPosition.y * desktop.height)
                } else {
                    Offset(origin.x + x * scaleX, origin.y + y * scaleY)
                }
                clipRect {
                    val shape = cursorShape()
                    val bitmap = shape?.bitmap
                    if (shape == null) {
                        drawLocalCursor(pointer)
                    } else if (bitmap != null) {
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
    } else {
        RemoteCanvasPlaceholder(
            statusText = statusText,
            remoteWidth = remoteWidth,
            remoteHeight = remoteHeight,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Draws a fixed-size arrow whose tip is at the local input position. */
private fun DrawScope.drawLocalCursor(tip: Offset) {
    val unit = 1.dp.toPx()
    val arrow = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x, tip.y + 18 * unit)
        lineTo(tip.x + 4.5f * unit, tip.y + 14 * unit)
        lineTo(tip.x + 8 * unit, tip.y + 20 * unit)
        lineTo(tip.x + 11 * unit, tip.y + 18.5f * unit)
        lineTo(tip.x + 7.5f * unit, tip.y + 12.5f * unit)
        lineTo(tip.x + 14 * unit, tip.y + 12.5f * unit)
        close()
    }
    drawPath(arrow, Color.White)
    drawPath(arrow, Color.Black, style = Stroke(unit))
}

@Composable
private fun RemoteCanvasPlaceholder(
    statusText: String,
    remoteWidth: Int,
    remoteHeight: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Monitor,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.9f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.session_waiting_body),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        if (remoteWidth > 0 && remoteHeight > 0) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White.copy(alpha = 0.8f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = stringResource(R.string.session_resolution, remoteWidth, remoteHeight),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }
}
