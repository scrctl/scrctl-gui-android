package org.scrctl.android.gui.ui.session

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.scrctl.android.gui.data.SessionPointerMode
import org.scrctl.android.gui.session.RemoteSession
import org.scrctl.android.gui.session.sendChord

internal fun Modifier.remoteTouchInput(
    session: RemoteSession,
    viewport: SessionViewport,
    remoteWidth: Int,
    remoteHeight: Int,
    pointerMode: SessionPointerMode,
    zoomEnabled: Boolean,
    observing: Boolean,
    screenSizePx: IntSize,
    bandSizePx: IntSize,
    minimumZoom: Float,
    maximumZoom: Float,
    scope: CoroutineScope,
    pointerMemory: PointerMemory,
    taps: DeferredTapController,
    twoFingerTaps: DeferredTapController,
    trackPointer: (Int, Int) -> Unit,
    onWheelPosition: (IntOffset) -> Unit,
    onPressIndicator: (PressIndicator?) -> Unit,
    onToggleChrome: () -> Unit,
): Modifier = pointerInput(
    session, viewport, remoteWidth, remoteHeight, pointerMode, zoomEnabled, observing,
    screenSizePx, bandSizePx, minimumZoom, maximumZoom,
) {
    val trackpadMode = pointerMode == SessionPointerMode.Trackpad
    val hybridMode = pointerMode == SessionPointerMode.Hybrid
    val centeredTrackpad = trackpadMode && !observing
    val longPressSlop = 12.dp.toPx()
    val swipeThreshold = 48.dp.toPx()

    if (observing) {
        if (zoomEnabled && remoteWidth > 1 && remoteHeight > 1) {
            val center = Offset(size.width / 2f, size.height / 2f)
            detectTransformGestures { centroid, delta, scale, _ ->
                val newZoom = (viewport.zoom * scale).coerceIn(minimumZoom, maximumZoom)
                viewport.pan = if (newZoom <= minimumZoom * ZOOM_ACTIVE_EPSILON) {
                    Offset.Zero
                } else {
                    clampPan(
                        viewport.pan + (centroid + delta - center) / newZoom -
                            (centroid - center) / viewport.zoom,
                        newZoom,
                        bandSizePx,
                        screenSizePx,
                    )
                }
                viewport.zoom = newZoom
            }
        }
        return@pointerInput
    }

    // No framebuffer yet: pointer math would divide by zero and
    // round NaN coordinates.
    if (remoteWidth <= 1 || remoteHeight <= 1) return@pointerInput
    val touchSlop = viewConfiguration.touchSlop
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis.toLong()
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
    val doubleTapSlop = max(40.dp.toPx(), touchSlop * 2.5f)
    val wheelStep = WHEEL_STEP_PX
    val screenSize = size

    fun trackpadTarget(): Pair<Int, Int> =
        (viewport.trackpadPosition.x * remoteWidth).roundToInt().coerceIn(0, remoteWidth - 1) to
            (viewport.trackpadPosition.y * remoteHeight).roundToInt().coerceIn(0, remoteHeight - 1)

    fun startHybridRecentering() {
        viewport.stopRecentering()
        val (x, y) = pointerMemory.resolve(remoteWidth, remoteHeight)
        viewport.trackpadPosition = Offset(x.toFloat() / remoteWidth, y.toFloat() / remoteHeight)
        viewport.startRecentering(bandSizePx, screenSizePx)
    }

    fun moveTrackpad(delta: Offset, followsCursor: Boolean = true): Pair<Int, Int> {
        if (followsCursor && centeredTrackpad) viewport.startRecentering(bandSizePx, screenSizePx)
        viewport.trackpadPosition = Offset(
            (viewport.trackpadPosition.x + delta.x / (bandSizePx.width * viewport.zoom).coerceAtLeast(1f)).coerceIn(0f, 1f),
            (viewport.trackpadPosition.y + delta.y / (bandSizePx.height * viewport.zoom).coerceAtLeast(1f)).coerceIn(0f, 1f),
        )
        if (followsCursor) viewport.followTrackpad(bandSizePx, screenSizePx)
        return trackpadTarget()
    }

    fun isInsideContent(position: Offset): Boolean {
        val halfWidth = bandSizePx.width * viewport.zoom / 2f
        val halfHeight = bandSizePx.height * viewport.zoom / 2f
        val centerX = screenSize.width / 2f + viewport.pan.x * viewport.zoom
        val centerY = screenSize.height / 2f + viewport.pan.y * viewport.zoom
        return abs(position.x - centerX) <= halfWidth &&
            abs(position.y - centerY) <= halfHeight
    }

    fun sendPointerClick(x: Int, y: Int) {
        session.sendPointer(x, y, 0)
        session.sendPointer(x, y, 1)
        session.sendPointer(x, y, 0)
        trackPointer(x, y)
    }

    fun flushPendingTap() {
        val pending = taps.pending ?: return
        taps.cancel()
        sendPointerClick(pending.x, pending.y)
    }

    fun scheduleTap(x: Int, y: Int, position: Offset, time: Long) {
        val pending = PendingTap(x, y, position, time)
        taps.pending = pending
        taps.job = scope.launch {
            // A small margin keeps the deferred click from winning a race
            // against a second tap that arrives exactly at the timeout.
            delay(doubleTapTimeout + DOUBLE_TAP_CLICK_MARGIN_MS)
            if (taps.pending === pending) {
                taps.pending = null
                taps.job = null
                sendPointerClick(x, y)
            }
        }
    }

    fun sendWheel(mask: Int) {
        val (x, y) = if (trackpadMode) trackpadTarget() else pointerMemory.resolve(remoteWidth, remoteHeight)
        onWheelPosition(IntOffset(x, y))
        session.sendPointer(x, y, mask)
        session.sendPointer(x, y, 0)
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startedOnImage = trackpadMode || isInsideContent(down.position)
        var movedInLetterbox = false
        val previousTap = taps.pending
        val dragTap = previousTap?.takeIf {
            (startedOnImage || hybridMode) &&
                down.uptimeMillis - it.time in 0L..doubleTapTimeout &&
                (down.position - it.position).getDistance() <= doubleTapSlop
        }
        if (previousTap != null) flushPendingTap()

        var mode = GestureMode.Pending
        var start = remotePoint(
            down.position,
            screenSize,
            bandSizePx,
            remoteWidth,
            remoteHeight,
            viewport.zoom,
            viewport.pan,
        )
        var last = start
        var lastTouch = down.position
        val downTime = down.uptimeMillis
        var tapEndedAt = downTime

        // Two-finger state: a pinch is anchored at the touch
        // centroid. The centered trackpad scrolls at every zoom level.
        var multiTouchStartZoom = viewport.zoom
        var multiTouchStartPan = viewport.pan
        var multiTouchStartCentroid = Offset.Zero
        var multiTouchStartSpan = Offset.Zero
        var multiTouchZoom = 1f
        var scrollAccum = Offset.Zero
        var threeFinger = false
        var multiTouchMoved = false
        var multiTouchTapCandidate = false
        val screenCenter = Offset(
            screenSize.width / 2f,
            screenSize.height / 2f,
        )

        // Long press holds a button down for dragging: one finger
        // holds the left button, two the right, three the middle.
        // Movement or a change in finger count cancels a pending hold.
        var holdTouches = if (startedOnImage || dragTap != null) 1 else 0
        var holdStartedAt = down.uptimeMillis
        var holdAnchor = down.position
        var currentTouches = 1
        var currentCentroid = down.position
        var pressIndicator: PressIndicator? = null
        var pressActive = false
        var pressTouches = 0
        var pressButton = 0
        var pressTarget = start
        var holdFinger: PointerId? = null
        var heldTransformStartDistance: Float? = null
        var threeFingerSwipe: SwipeDirection? = null
        var threeFingerStart = Offset.Zero

        fun movePress(position: Offset) {
            currentCentroid = position
            if (trackpadMode) {
                pressTarget = moveTrackpad(position - lastTouch)
                lastTouch = position
            } else {
                pressTarget = remotePoint(
                    position,
                    screenSize,
                    bandSizePx,
                    remoteWidth,
                    remoteHeight,
                    viewport.zoom,
                    viewport.pan,
                )
            }
            pressIndicator = pressIndicator?.copy(
                position = if (trackpadMode) viewport.trackpadPointOnCanvas(bandSizePx, screenSizePx) else position,
            )
            onPressIndicator(pressIndicator)
            session.sendPointer(pressTarget.first, pressTarget.second, pressButton)
            trackPointer(pressTarget.first, pressTarget.second)
        }

        fun beginPress(now: Long, tapTarget: PendingTap? = null) {
            if (pressActive || holdTouches == 0 ||
                currentTouches != holdTouches
            ) {
                return
            }
            if (tapTarget == null && now - holdStartedAt < longPressTimeout) return
            if (tapTarget == null && !trackpadMode && !isInsideContent(currentCentroid)) {
                holdTouches = 0
                return
            }
            val button = when (currentTouches) {
                1 -> 1
                2 -> 1 shl 2
                else -> 1 shl 1
            }
            pressTarget = if (tapTarget != null) {
                tapTarget.x to tapTarget.y
            } else if (trackpadMode) {
                trackpadTarget()
            } else {
                remotePoint(
                    currentCentroid,
                    screenSize,
                    bandSizePx,
                    remoteWidth,
                    remoteHeight,
                    viewport.zoom,
                    viewport.pan,
                )
            }
            if (button == 1) {
                holdFinger = down.id
                if (!trackpadMode) {
                    viewport.trackpadPosition = Offset(
                        pressTarget.first.toFloat() / remoteWidth,
                        pressTarget.second.toFloat() / remoteHeight,
                    )
                }
            }
            pressActive = true
            pressTouches = currentTouches
            pressButton = button
            pressIndicator = PressIndicator(
                pressIndicatorColor(button),
                if (trackpadMode) viewport.trackpadPointOnCanvas(bandSizePx, screenSizePx) else currentCentroid,
            )
            onPressIndicator(pressIndicator)
            lastTouch = currentCentroid
            session.sendPointer(pressTarget.first, pressTarget.second, 0)
            session.sendPointer(pressTarget.first, pressTarget.second, button)
            trackPointer(pressTarget.first, pressTarget.second)
            multiTouchTapCandidate = false
            multiTouchMoved = true
            threeFingerSwipe = null
            holdTouches = 0
        }

        fun endPress() {
            if (!pressActive) return
            session.sendPointer(pressTarget.first, pressTarget.second, 0)
            trackPointer(pressTarget.first, pressTarget.second)
            pressActive = false
            pressIndicator = null
            onPressIndicator(null)
            if (centeredTrackpad && (heldTransformStartDistance != null || mode == GestureMode.Transform)) {
                viewport.startRecentering(bandSizePx, screenSizePx)
            }
        }

        fun sendClick(x: Int, y: Int, mask: Int) {
            session.sendPointer(x, y, 0)
            session.sendPointer(x, y, mask)
            session.sendPointer(x, y, 0)
            trackPointer(x, y)
        }

        fun applyPinch(centroid: Offset) {
            viewport.stopRecentering()
            val newZoom = (multiTouchStartZoom * multiTouchZoom)
                .coerceIn(minimumZoom, maximumZoom)
            val newPan = clampPan(
                multiTouchStartPan +
                    (centroid - screenCenter) / newZoom -
                    (multiTouchStartCentroid - screenCenter) /
                    multiTouchStartZoom,
                newZoom,
                bandSizePx,
                screenSizePx,
            )
            viewport.zoom = newZoom
            viewport.pan = if (viewport.zoom <= minimumZoom * ZOOM_ACTIVE_EPSILON) {
                Offset.Zero
            } else {
                newPan
            }
        }

        fun scheduleTwoFingerTap(position: Offset) {
            val target = if (trackpadMode) {
                trackpadTarget()
            } else {
                remotePoint(
                    position,
                    screenSize,
                    bandSizePx,
                    remoteWidth,
                    remoteHeight,
                    viewport.zoom,
                    viewport.pan,
                )
            }
            val pending = PendingTap(
                target.first,
                target.second,
                position,
                SystemClock.uptimeMillis(),
            )
            twoFingerTaps.job?.cancel()
            twoFingerTaps.pending = pending
            twoFingerTaps.job = scope.launch {
                delay(doubleTapTimeout + DOUBLE_TAP_CLICK_MARGIN_MS)
                if (twoFingerTaps.pending === pending) {
                    twoFingerTaps.pending = null
                    twoFingerTaps.job = null
                    sendClick(target.first, target.second, 1 shl 2)
                }
            }
        }

        fun handleTwoFingerTap(position: Offset) {
            val now = SystemClock.uptimeMillis()
            val previous = twoFingerTaps.pending
            if (previous != null) {
                twoFingerTaps.job?.cancel()
                twoFingerTaps.pending = null
                twoFingerTaps.job = null
                if (now - previous.time <= doubleTapTimeout &&
                    (position - previous.position).getDistance() <= doubleTapSlop
                ) {
                    onToggleChrome()
                    return
                }
                sendClick(previous.x, previous.y, 1 shl 2)
            }
            scheduleTwoFingerTap(position)
        }

        if (dragTap != null) beginPress(down.uptimeMillis, dragTap)

        while (true) {
            val event = try {
                if (holdTouches > 0 && holdTouches == currentTouches) {
                    val remaining = holdStartedAt + longPressTimeout - SystemClock.uptimeMillis()
                    withTimeoutOrNull(remaining.coerceAtLeast(1L)) { awaitPointerEvent() }
                } else {
                    awaitPointerEvent()
                }
            } catch (cancelled: CancellationException) {
                endPress()
                throw cancelled
            }
            if (event == null) {
                beginPress(SystemClock.uptimeMillis())
                continue
            }
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                tapEndedAt = event.changes.firstOrNull { it.id == down.id }?.uptimeMillis
                    ?: event.changes.first().uptimeMillis
                break
            }
            val touchSetChanged = event.changes.any { it.pressed != it.previousPressed }
            // A second finger may land after the hold deadline but before the
            // timeout coroutine resumes. Resolve that hold at the first finger.
            val originalStillPressed = pressed.any { it.id == down.id && it.previousPressed }
            if (pressed.size == currentTouches || (holdTouches == 1 && originalStillPressed)) {
                beginPress(event.changes.first().uptimeMillis)
            }
            if (pressActive && pressButton == 1) {
                val holdChanged = pressed.none { it.id == holdFinger }
                if (holdChanged) holdFinger = pressed.first().id

                // Preserve single-finger dragging. With multiple fingers,
                // the first holds the button while the others move.
                val controls = if (pressed.size == 1) pressed
                    else pressed.filter { it.id != holdFinger }
                if (controls.size == 2) {
                    val (first, second) = controls
                    val centroid = (first.position + second.position) / 2f
                    val distance = (first.position - second.position).getDistance().coerceAtLeast(1f)
                    val startDistance = heldTransformStartDistance
                    if (startDistance == null || holdChanged || controls.any { !it.previousPressed }) {
                        multiTouchStartZoom = viewport.zoom
                        multiTouchStartPan = viewport.pan
                        multiTouchStartCentroid = centroid
                        heldTransformStartDistance = distance
                    } else if (zoomEnabled && controls.any { it.position != it.previousPosition }) {
                        multiTouchZoom = distance / startDistance
                        applyPinch(centroid)
                    }
                } else {
                    val wasTransforming = heldTransformStartDistance != null
                    heldTransformStartDistance = null
                    if (wasTransforming && centeredTrackpad) viewport.startRecentering(bandSizePx, screenSizePx)
                    val finger = controls.singleOrNull()
                    if (!wasTransforming && !touchSetChanged && !holdChanged && finger != null && finger.previousPressed) {
                        val delta = finger.position - finger.previousPosition
                        if (delta != Offset.Zero) {
                            pressTarget = moveTrackpad(delta, followsCursor = trackpadMode)
                            session.sendPointer(pressTarget.first, pressTarget.second, pressButton)
                            trackPointer(pressTarget.first, pressTarget.second)
                        }
                    }
                }
                pressIndicator = pressIndicator?.copy(position = viewport.trackpadPointOnCanvas(bandSizePx, screenSizePx))
                onPressIndicator(pressIndicator)
                event.changes.forEach { it.consume() }
                continue
            }
            if (pressActive && pressed.size < pressTouches) break
            if (mode == GestureMode.Transform && pressed.size < 2) break
            if (mode == GestureMode.TwoFinger && pressed.size < 2) break
            if (mode == GestureMode.Scroll && pressed.size < 2) break

            if (pressed.size >= 2) {
                currentTouches = pressed.size
                // Include the newly pressed finger when establishing the anchor.
                // calculateCentroid filters out pointers that were not down previously.
                val centroidNow = pressed.fold(Offset.Zero) { sum, pointer ->
                    sum + pointer.position
                } / pressed.size.toFloat()
                currentCentroid = centroidNow
                if (pressActive) {
                    movePress(centroidNow)
                    event.changes.forEach { it.consume() }
                    continue
                }
                if (holdTouches > 0 && (pressed.size != holdTouches ||
                        (centroidNow - holdAnchor).getDistance() > longPressSlop)
                ) {
                    holdTouches = 0
                }
                if (mode == GestureMode.Pending || mode == GestureMode.Drag ||
                    mode == GestureMode.Move
                ) {
                    if (mode == GestureMode.Drag) {
                        session.sendPointer(last.first, last.second, 0)
                        trackPointer(last.first, last.second)
                    }
                    multiTouchTapCandidate = startedOnImage && mode == GestureMode.Pending
                    mode = if (!centeredTrackpad && zoomEnabled && viewport.zoom > minimumZoom * ZOOM_ACTIVE_EPSILON) {
                        GestureMode.Transform
                    } else {
                        GestureMode.TwoFinger
                    }
                    multiTouchStartZoom = viewport.zoom
                    multiTouchStartPan = viewport.pan
                    multiTouchStartCentroid = centroidNow
                    multiTouchStartSpan = pressed[1].position - pressed[0].position
                    multiTouchZoom = 1f
                    scrollAccum = Offset.Zero
                    threeFinger = pressed.size >= 3
                    threeFingerStart = centroidNow
                    threeFingerSwipe = null
                    holdTouches = if (startedOnImage) pressed.size.coerceAtMost(3) else 0
                    holdStartedAt = SystemClock.uptimeMillis()
                    holdAnchor = centroidNow
                    multiTouchMoved = false
                    // The pointer count changed on this event, so its pan and
                    // zoom deltas are not meaningful. Start clean next event.
                    event.changes.forEach { it.consume() }
                    continue
                }

                if (threeFinger || pressed.size >= 3) {
                    if (!threeFinger) {
                        threeFinger = true
                        threeFingerStart = centroidNow
                        threeFingerSwipe = null
                    }
                    if (threeFingerSwipe == null) {
                        val delta = centroidNow - threeFingerStart
                        if (max(abs(delta.x), abs(delta.y)) > swipeThreshold) {
                            threeFingerSwipe = if (abs(delta.x) > abs(delta.y)) {
                                if (delta.x > 0f) {
                                    SwipeDirection.Right
                                } else {
                                    SwipeDirection.Left
                                }
                            } else if (delta.y > 0f) {
                                SwipeDirection.Down
                            } else {
                                SwipeDirection.Up
                            }
                            multiTouchMoved = true
                            multiTouchTapCandidate = false
                            holdTouches = 0
                        }
                    }
                    event.changes.forEach { it.consume() }
                    continue
                }

                when (mode) {
                    GestureMode.TwoFinger -> {
                        if (zoomEnabled) {
                            multiTouchZoom *= event.calculateZoom()
                        }
                        val translation = centroidNow - multiTouchStartCentroid
                        val canScroll = startedOnImage && translation.getDistance() > touchSlop * 2f
                        val changedScale = zoomEnabled && abs(multiTouchZoom - 1f) > PINCH_ZOOM_EPSILON
                        val canPinch = changedScale && (!centeredTrackpad || trackpadPinchDominates(
                            translation,
                            multiTouchStartSpan,
                            pressed[1].position - pressed[0].position,
                            touchSlop,
                        ))
                        if (canPinch) {
                            multiTouchMoved = true
                            multiTouchTapCandidate = false
                            holdTouches = 0
                            mode = GestureMode.Transform
                            applyPinch(centroidNow)
                        } else if (canScroll) {
                            multiTouchTapCandidate = false
                            holdTouches = 0
                            mode = GestureMode.Scroll
                            scrollAccum = Offset.Zero
                        }
                        event.changes.forEach { it.consume() }
                        continue
                    }

                    GestureMode.Scroll -> {
                        val panChange = event.calculatePan()
                        if (!centeredTrackpad && zoomEnabled) {
                            multiTouchZoom *= event.calculateZoom()
                            if (abs(multiTouchZoom - 1f) > PINCH_ZOOM_EPSILON) {
                                multiTouchMoved = true
                                multiTouchTapCandidate = false
                                mode = GestureMode.Transform
                                applyPinch(centroidNow)
                                event.changes.forEach { it.consume() }
                                continue
                            }
                        }

                        scrollAccum += panChange
                        var steps = 0
                        while (steps < MAX_WHEEL_STEPS_PER_EVENT) {
                            val ax = abs(scrollAccum.x)
                            val ay = abs(scrollAccum.y)
                            if (max(ax, ay) < wheelStep) break
                            if (ay >= ax) {
                                val fingersUp = scrollAccum.y < 0f
                                // Natural touch scrolling: content follows fingers.
                                sendWheel(
                                    if (fingersUp) WHEEL_DOWN else WHEEL_UP,
                                )
                                scrollAccum = Offset(
                                    scrollAccum.x,
                                    scrollAccum.y -
                                        (if (fingersUp) -1f else 1f) * wheelStep,
                                )
                            } else {
                                val fingersLeft = scrollAccum.x < 0f
                                sendWheel(
                                    if (fingersLeft) WHEEL_RIGHT else WHEEL_LEFT,
                                )
                                scrollAccum = Offset(
                                    scrollAccum.x -
                                        (if (fingersLeft) -1f else 1f) * wheelStep,
                                    scrollAccum.y,
                                )
                            }
                            steps++
                        }
                        event.changes.forEach { it.consume() }
                        continue
                    }

                    GestureMode.Transform -> {
                        if (zoomEnabled) {
                            multiTouchZoom *= event.calculateZoom()
                            applyPinch(centroidNow)
                        }
                        event.changes.forEach { it.consume() }
                        continue
                    }

                    else -> Unit
                }
            }

            if (mode == GestureMode.Transform || mode == GestureMode.TwoFinger ||
                mode == GestureMode.Scroll
            ) {
                continue
            }

            val change = pressed.first()
            if (!startedOnImage && !hybridMode) {
                movedInLetterbox = movedInLetterbox ||
                    (change.position - down.position).getDistance() > touchSlop
                continue
            }
            currentTouches = 1
            currentCentroid = change.position
            if (pressActive) {
                movePress(change.position)
                change.consume()
                continue
            }
            if (holdTouches == 1 &&
                (change.position - holdAnchor).getDistance() > longPressSlop
            ) {
                holdTouches = 0
            }
            if (mode == GestureMode.Pending &&
                (change.position - down.position).getDistance() > touchSlop
            ) {
                if (trackpadMode || hybridMode) {
                    mode = GestureMode.Move
                    if (hybridMode) startHybridRecentering()
                } else {
                    mode = GestureMode.Drag
                    session.sendPointer(start.first, start.second, 1)
                    trackPointer(start.first, start.second)
                }
            }
            if (mode == GestureMode.Drag) {
                last = remotePoint(
                    change.position,
                    screenSize,
                    bandSizePx,
                    remoteWidth,
                    remoteHeight,
                    viewport.zoom,
                    viewport.pan,
                )
                session.sendPointer(last.first, last.second, 1)
                trackPointer(last.first, last.second)
                change.consume()
            } else if (mode == GestureMode.Move) {
                val delta = change.position - lastTouch
                lastTouch = change.position
                val (x, y) = moveTrackpad(delta)
                trackPointer(x, y)
                session.sendPointer(x, y, 0)
                change.consume()
            }
        }

        if (!pressActive && centeredTrackpad && mode == GestureMode.Transform) viewport.startRecentering(bandSizePx, screenSizePx)
        if (pressActive) {
            endPress()
        } else when (mode) {
            GestureMode.Drag -> {
                session.sendPointer(last.first, last.second, 0)
                trackPointer(last.first, last.second)
            }
            GestureMode.Pending -> {
                if (hybridMode) viewport.stopRecentering()
                if (!startedOnImage) {
                    if (!movedInLetterbox) onToggleChrome()
                } else {
                    val tapTarget = if (trackpadMode) {
                        trackpadTarget()
                    } else {
                        start
                    }
                    scheduleTap(
                        tapTarget.first,
                        tapTarget.second,
                        down.position,
                        tapEndedAt,
                    )
                }
            }
            else -> {
                val swipe = threeFingerSwipe
                if (swipe != null) {
                    val keysym = when (swipe) {
                        SwipeDirection.Up -> KEY_UP
                        SwipeDirection.Down -> KEY_DOWN
                        SwipeDirection.Left -> KEY_LEFT
                        SwipeDirection.Right -> KEY_RIGHT
                    }
                    sendChord(keysym, listOf(KEY_CONTROL), session::sendKey)
                } else if (multiTouchTapCandidate && !multiTouchMoved &&
                    SystemClock.uptimeMillis() - downTime <= TAP_TIMEOUT_MS
                ) {
                    if (threeFinger) {
                        val target = if (trackpadMode) {
                            trackpadTarget()
                        } else {
                            remotePoint(
                                multiTouchStartCentroid,
                                screenSize,
                                bandSizePx,
                                remoteWidth,
                                remoteHeight,
                                viewport.zoom,
                                viewport.pan,
                            )
                        }
                        sendClick(target.first, target.second, 1 shl 1)
                    } else {
                        handleTwoFingerTap(multiTouchStartCentroid)
                    }
                }
            }
        }
    }
}

internal fun trackpadPinchDominates(
    translation: Offset,
    startSpan: Offset,
    currentSpan: Offset,
    touchSlop: Float,
): Boolean {
    val spanChange = abs(currentSpan.getDistance() - startSpan.getDistance())
    // Same-direction finger motion moves the centroid farther than either
    // finger moves relative to it. A stationary pinch anchor allows equality.
    return spanChange > touchSlop &&
        translation.getDistanceSquared() * 4f <= (currentSpan - startSpan).getDistanceSquared()
}

private const val PINCH_ZOOM_EPSILON = 0.08f
private const val TAP_TIMEOUT_MS = 500L
private const val DOUBLE_TAP_CLICK_MARGIN_MS = 32L
private const val WHEEL_STEP_PX = 40f
private const val MAX_WHEEL_STEPS_PER_EVENT = 4
private const val WHEEL_UP = 1 shl 3
private const val WHEEL_DOWN = 1 shl 4
private const val WHEEL_LEFT = 1 shl 5
private const val WHEEL_RIGHT = 1 shl 6

internal data class PendingTap(
    val x: Int,
    val y: Int,
    val position: Offset,
    val time: Long,
)

internal data class PressIndicator(val color: Color, val position: Offset)

private enum class SwipeDirection { Up, Down, Left, Right }

/// Screens colours the held button the same way on every platform: blue for the
/// left button, red for the right one and green for the middle.
private fun pressIndicatorColor(button: Int): Color = when (button) {
    1 -> Color(0.29f, 0.56f, 1f)
    1 shl 2 -> Color(1f, 0.35f, 0.32f)
    else -> Color(0.20f, 0.78f, 0.35f)
}

internal class DeferredTapController {
    var pending: PendingTap? = null
    var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
        pending = null
    }
}

/** Remembers the last remote pointer position so wheel events land where the user left it. */
internal class PointerMemory {
    private var x = -1
    private var y = -1

    fun remember(remoteX: Int, remoteY: Int) {
        x = remoteX
        y = remoteY
    }

    fun resolve(remoteWidth: Int, remoteHeight: Int): Pair<Int, Int> {
        val maxX = (remoteWidth - 1).coerceAtLeast(0)
        val maxY = (remoteHeight - 1).coerceAtLeast(0)
        return if (x < 0 || y < 0) {
            maxX / 2 to maxY / 2
        } else {
            x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
        }
    }
}

private enum class GestureMode { Pending, Drag, Move, TwoFinger, Scroll, Transform }
