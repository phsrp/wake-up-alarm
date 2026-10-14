@file:OptIn(ExperimentalMaterial3Api::class)

package com.wakeup.alarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeup.alarm.data.TimerEntity
import kotlinx.coroutines.delay

/** 1:05:09 or 05:09, rounding up so a timer shows 00:01 until it is truly done. */
fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0) + 999) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private fun TimerEntity.millisLeft(now: Long): Long = when (state) {
    TimerEntity.STATE_RUNNING -> (endAtMillis - now).coerceAtLeast(0)
    TimerEntity.STATE_PAUSED -> remainingMillis
    TimerEntity.STATE_FINISHED -> 0
    else -> durationSeconds * 1000L
}

@Composable
fun TimersScreen(
    timers: List<TimerEntity>,
    bottomBar: @Composable () -> Unit,
    onAdd: (durationSeconds: Int, label: String) -> Unit,
    onStart: (Long) -> Unit,
    onPause: (Long) -> Unit,
    onReset: (Long) -> Unit,
    onDelete: (List<Long>) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    var showNew by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    var localOrder by remember { mutableStateOf<List<Long>?>(null) }
    LaunchedEffect(timers) { localOrder = null }
    val shown = remember(timers, localOrder) {
        val order = localOrder
        if (order == null) {
            timers
        } else {
            val index = order.withIndex().associate { it.value to it.index }
            timers.sortedBy { index[it.id] ?: Int.MAX_VALUE }
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val anyRunning = timers.any { it.state == TimerEntity.STATE_RUNNING }
    LaunchedEffect(anyRunning) {
        while (anyRunning) {
            now = System.currentTimeMillis()
            delay(200)
        }
        now = System.currentTimeMillis()
    }

    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        listState = listState,
        canSwap = { dragged, other -> dragged is Long && other is Long },
        onMove = { dragged, other ->
            val order = (localOrder ?: shown.map { it.id }).toMutableList()
            val from = order.indexOf(dragged as Long)
            val to = order.indexOf(other as Long)
            if (from >= 0 && to >= 0) {
                order[from] = other
                order[to] = dragged
                localOrder = order
            }
        },
        onFinished = { localOrder?.let(onReorder) },
    )

    BackHandler(enabled = selecting) { selected = emptySet() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size} selected", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    },
                )
            } else {
                LargeTopAppBar(
                    title = { Text("Timers", fontWeight = FontWeight.Black) },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(onClick = { showNew = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New timer", fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { padding ->
        if (timers.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.HourglassBottom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Text("No timers yet", style = MaterialTheme.typography.headlineMedium)
                    Text("Tap New timer, or ask Google to set one.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shown, key = { it.id }) { timer ->
                TimerCard(
                    timer = timer,
                    now = now,
                    selected = timer.id in selected,
                    reorder = reorder,
                    onStart = onStart,
                    onPause = onPause,
                    onReset = onReset,
                    onTap = { if (selecting) selected = selected.toggled(timer.id) },
                    onHold = { selected = selected + timer.id },
                )
            }
        }
    }

    if (showNew) {
        NewTimerDialog(
            onCreate = { seconds, label ->
                onAdd(seconds, label)
                showNew = false
            },
            onDismiss = { showNew = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (selected.size == 1) "Delete this timer?" else "Delete ${selected.size} timers?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(selected.toList())
                    selected = emptySet()
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LazyItemScope.TimerCard(
    timer: TimerEntity,
    now: Long,
    selected: Boolean,
    reorder: ReorderState,
    onStart: (Long) -> Unit,
    onPause: (Long) -> Unit,
    onReset: (Long) -> Unit,
    onTap: () -> Unit,
    onHold: () -> Unit,
) {
    val running = timer.state == TimerEntity.STATE_RUNNING
    val finished = timer.state == TimerEntity.STATE_FINISHED
    val dragging = reorder.draggedKey == timer.id
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (dragging) Modifier else Modifier.animateItem())
            .reorderable(reorder, timer.id, onTap = onTap, onHold = onHold)
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.tertiaryContainer
                finished -> MaterialTheme.colorScheme.errorContainer
                running -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    formatCountdown(timer.millisLeft(now)),
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp),
                    fontWeight = FontWeight.Black,
                )
                val caption = when {
                    finished -> "Time's up" + if (timer.label.isNotBlank()) " · ${timer.label}" else ""
                    timer.label.isNotBlank() -> timer.label
                    else -> "Timer"
                }
                Text(caption, style = MaterialTheme.typography.bodyLarge)
            }
            if (timer.state != TimerEntity.STATE_IDLE) {
                IconButton(onClick = { onReset(timer.id) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset")
                }
            }
            FilledIconButton(
                onClick = { if (running) onPause(timer.id) else onStart(timer.id) },
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (running) "Pause" else "Start",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun NewTimerDialog(onCreate: (Int, String) -> Unit, onDismiss: () -> Unit) {
    // The wheels and the boxes edit the same three numbers, so either one can be used.
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }
    var label by remember { mutableStateOf("") }

    val total = hours * 3600 + minutes * 60 + seconds

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WheelPicker(hours, 24, "hours", Modifier.weight(1f)) { hours = it }
                    WheelPicker(minutes, 60, "min", Modifier.weight(1f)) { minutes = it }
                    WheelPicker(seconds, 60, "sec", Modifier.weight(1f)) { seconds = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField("h", hours, 23, Modifier.weight(1f)) { hours = it }
                    TimeField("min", minutes, 59, Modifier.weight(1f)) { minutes = it }
                    TimeField("sec", seconds, 59, Modifier.weight(1f)) { seconds = it }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    label = { Text("Label (optional)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(total, label) }, enabled = total > 0) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TimeField(label: String, value: Int, max: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { text ->
            val number = text.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
            onChange(number.coerceAtMost(max))
        },
        modifier = modifier,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
