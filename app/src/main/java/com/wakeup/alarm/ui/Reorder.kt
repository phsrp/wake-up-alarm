package com.wakeup.alarm.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs

/**
 * Press and hold a list item, then drag it up or down to put it somewhere else.
 * If you hold without dragging, [reorderable]'s onHold fires instead (used to start selecting).
 */
@Stable
class ReorderState(
    private val listState: LazyListState,
    private val canSwap: (draggedKey: Any, otherKey: Any) -> Boolean,
    private val onMove: (draggedKey: Any, otherKey: Any) -> Unit,
    private val onFinished: () -> Unit,
) {
    var draggedKey by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    private var movement = 0f

    fun start(key: Any) {
        draggedKey = key
        offset = 0f
        movement = 0f
    }

    fun drag(delta: Float) {
        val key = draggedKey ?: return
        offset += delta
        movement += abs(delta)

        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == key } ?: return
        val center = dragged.offset + dragged.size / 2f + offset
        val target = items.firstOrNull {
            it.key != key && center >= it.offset && center <= it.offset + it.size && canSwap(key, it.key)
        } ?: return

        // After the swap the dragged item sits at its new slot; shift `offset` so it does not jump on screen.
        val newPosition = if (target.offset > dragged.offset) target.offset + target.size - dragged.size else target.offset
        offset -= (newPosition - dragged.offset)
        onMove(key, target.key)
    }

    /** Returns true if the item was really dragged (rather than just held). */
    fun finish(): Boolean {
        val moved = movement > MOVE_THRESHOLD
        draggedKey = null
        offset = 0f
        if (moved) onFinished()
        return moved
    }

    fun cancel() {
        draggedKey = null
        offset = 0f
    }

    private companion object {
        const val MOVE_THRESHOLD = 12f
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    canSwap: (draggedKey: Any, otherKey: Any) -> Boolean,
    onMove: (draggedKey: Any, otherKey: Any) -> Unit,
    onFinished: () -> Unit,
): ReorderState {
    val canSwapNow by rememberUpdatedState(canSwap)
    val onMoveNow by rememberUpdatedState(onMove)
    val onFinishedNow by rememberUpdatedState(onFinished)
    return remember(listState) {
        ReorderState(
            listState = listState,
            canSwap = { a, b -> canSwapNow(a, b) },
            onMove = { a, b -> onMoveNow(a, b) },
            onFinished = { onFinishedNow() },
        )
    }
}

/**
 * Tap, press-and-hold, and press-hold-and-drag on one item. While it is being dragged the item
 * floats above the others and follows the finger.
 */
@Composable
fun Modifier.reorderable(
    state: ReorderState,
    key: Any,
    onTap: () -> Unit,
    onHold: () -> Unit,
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    val haptics = LocalHapticFeedback.current
    val dragging = state.draggedKey == key

    // The pointer detectors must come BEFORE (outside) the translating layer. If they sat inside it,
    // the item would move with the finger and the finger would appear not to move at all.
    return this
        .zIndex(if (dragging) 1f else 0f)
        .pointerInput(key) { detectTapGestures(onTap = { tap() }) }
        .pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.start(key)
                },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(amount.y)
                },
                onDragEnd = {
                    if (!state.finish()) hold()
                },
                onDragCancel = { state.cancel() },
            )
        }
        .then(
            if (dragging) {
                Modifier
                    .graphicsLayer { translationY = state.offset }
                    .shadow(8.dp, shape = RoundedCornerShape(44.dp))
            } else {
                Modifier
            },
        )
}
