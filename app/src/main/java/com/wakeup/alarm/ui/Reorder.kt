package com.wakeup.alarm.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Press and hold a list item, then drag it up or down to put it somewhere else, or onto a
 * drop target outside the list. If you hold without dragging, [reorderable]'s onHold fires instead.
 */
@Stable
class ReorderState(
    private val listState: LazyListState,
    private val canSwap: (draggedKey: Any, otherKey: Any) -> Boolean,
    private val onMove: (draggedKey: Any, otherKey: Any) -> Unit,
    private val onFinished: (draggedKey: Any?, fingerInRoot: Offset?) -> Unit,
) {
    var draggedKey by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    /** Where the finger is on the whole screen while dragging; used to find drop targets. */
    var fingerInRoot by mutableStateOf<Offset?>(null)
        private set
    private var movement = 0f

    fun start(key: Any) {
        draggedKey = key
        offset = 0f
        movement = 0f
        fingerInRoot = null
    }

    fun drag(delta: Float, finger: Offset) {
        val key = draggedKey ?: return
        offset += delta
        movement += abs(delta)
        fingerInRoot = finger

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
        val key = draggedKey
        val finger = fingerInRoot
        val moved = movement > MOVE_THRESHOLD
        if (moved) onFinished(key, finger)
        draggedKey = null
        offset = 0f
        fingerInRoot = null
        return moved
    }

    fun cancel() {
        draggedKey = null
        offset = 0f
        fingerInRoot = null
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
    onFinished: (draggedKey: Any?, fingerInRoot: Offset?) -> Unit,
): ReorderState {
    val canSwapNow by rememberUpdatedState(canSwap)
    val onMoveNow by rememberUpdatedState(onMove)
    val onFinishedNow by rememberUpdatedState(onFinished)
    return remember(listState) {
        ReorderState(
            listState = listState,
            canSwap = { a, b -> canSwapNow(a, b) },
            onMove = { a, b -> onMoveNow(a, b) },
            onFinished = { key, finger -> onFinishedNow(key, finger) },
        )
    }
}

/**
 * Tap, press-and-hold, and press-hold-and-drag on one item. It dips slightly while pressed, and while
 * being dragged it grows a little, floats above the others and follows the finger.
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

    var origin by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = when {
            dragging -> 1.04f
            pressed -> 0.97f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (dragging) 10.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "dragElevation",
    )

    // The pointer detectors must come BEFORE (outside) the translating layer. If they sat inside it,
    // the item would move with the finger and the finger would appear not to move at all.
    return this
        .zIndex(if (dragging) 1f else 0f)
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(key) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { tap() },
            )
        }
        .pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.start(key)
                },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(amount.y, origin + change.position)
                },
                onDragEnd = {
                    if (!state.finish()) hold()
                },
                onDragCancel = { state.cancel() },
            )
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = if (dragging) state.offset else 0f
        }
        .shadow(elevation, RoundedCornerShape(44.dp))
}

/** Slides and fades an item in the first time it appears; [index] staggers a group of items. */
@Composable
fun Modifier.enterAnimation(index: Int = 0): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceIn(0, 8) * 45L)
        progress.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        )
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 56f
        val s = 0.92f + 0.08f * p
        scaleX = s
        scaleY = s
    }
}
