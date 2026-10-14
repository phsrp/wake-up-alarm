package com.wakeup.alarm.ui

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A rolling number wheel like the ones in other clock apps: flick it, and it settles with one
 * number in the highlighted middle row. [value] and [onValueChange] let a text box drive it too.
 */
@Composable
fun WheelPicker(
    value: Int,
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit,
) {
    val itemHeight = 48.dp
    val visibleRows = 5
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val latestOnChange by rememberUpdatedState(onValueChange)

    val state = rememberLazyListState(initialFirstVisibleItemIndex = value.coerceIn(0, count - 1))
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    // The value changed from outside (for example typed in a box): roll the wheel to it.
    LaunchedEffect(value) {
        val target = value.coerceIn(0, count - 1)
        if (!state.isScrollInProgress && state.firstVisibleItemIndex != target) {
            state.animateScrollToItem(target)
        }
    }

    // The wheel stopped moving: report which number is in the middle.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) latestOnChange(state.firstVisibleItemIndex.coerceIn(0, count - 1))
        }
    }

    // The row closest to the middle while scrolling, for the bold highlight.
    val centred by remember {
        derivedStateOf {
            val extra = if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
            (state.firstVisibleItemIndex + extra).coerceIn(0, count - 1)
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.height(itemHeight * visibleRows)) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(itemHeight),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {}
            LazyColumn(
                state = state,
                flingBehavior = fling,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = itemHeight * (visibleRows / 2)),
            ) {
                items(count) { index ->
                    val selected = index == centred
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "%02d".format(index),
                            fontSize = if (selected) 32.sp else 22.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}
