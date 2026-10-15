@file:OptIn(ExperimentalMaterial3Api::class)

package com.wakeup.alarm.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeup.alarm.data.AlarmEntity
import com.wakeup.alarm.data.FolderEntity
import com.wakeup.alarm.scheduling.AlarmTime
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** The drop tile that appears while dragging an alarm that is inside a folder, to take it back out. */
private const val NO_FOLDER_TILE = -1L

fun formatTime(hour: Int, minute: Int, is24Hour: Boolean): Pair<String, String> =
    if (is24Hour) {
        "%02d:%02d".format(hour, minute) to ""
    } else {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        "%d:%02d".format(h12, minute) to if (hour < 12) "AM" else "PM"
    }

fun describeDays(repeatDays: Int): String = when (repeatDays) {
    0 -> "Once"
    127 -> "Every day"
    31 -> "Weekdays"
    96 -> "Weekends"
    else -> DAY_NAMES.filterIndexed { index, _ -> repeatDays and (1 shl index) != 0 }.joinToString(", ")
}

fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

private sealed interface FolderDialog {
    data object Create : FolderDialog
    data class Rename(val folder: FolderEntity) : FolderDialog
    data class Delete(val folder: FolderEntity) : FolderDialog
}

private data class FolderTileData(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val count: Int,
)

/** "7 h 12 min" for a number of minutes. */
private fun describeWait(minutes: Long): String {
    if (minutes <= 0) return "less than a minute"
    val days = minutes / 1440
    val hours = (minutes % 1440) / 60
    val mins = minutes % 60
    return buildList {
        if (days > 0) add("$days d")
        if (hours > 0) add("$hours h")
        if (mins > 0) add("$mins min")
    }.joinToString(" ")
}

/** Returns (how long from now, when) for the soonest alarm that is really switched on, or null. */
private fun nextAlarm(
    alarms: List<AlarmEntity>,
    folders: List<FolderEntity>,
    nowMillis: Long,
    is24Hour: Boolean,
): Pair<String, String>? {
    val folderOn = folders.associate { it.id to it.enabled }
    val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
    val next = alarms
        .filter { it.enabled && (it.folderId?.let { id -> folderOn[id] } ?: true) }
        .map { it to AlarmTime.nextTrigger(it, now) }
        .minByOrNull { it.second }
        ?: return null

    val millis = next.second.toInstant().toEpochMilli() - nowMillis
    val minutes = (millis + 59_999) / 60_000
    val today = now.toLocalDate()
    val day = when (next.second.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> next.second.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
    val (time, suffix) = formatTime(next.first.hour, next.first.minute, is24Hour)
    return describeWait(minutes) to "$day, $time $suffix".trim()
}

@Composable
fun HomeScreen(
    alarms: List<AlarmEntity>,
    folders: List<FolderEntity>,
    bottomBar: @Composable () -> Unit,
    onToggleAlarm: (Long, Boolean) -> Unit,
    onOpenAlarm: (Long) -> Unit,
    onNewAlarm: () -> Unit,
    onDeleteAlarms: (List<Long>) -> Unit,
    onReorderAlarms: (List<Long>) -> Unit,
    onMoveAlarm: (Long, Long?) -> Unit,
    onToggleFolder: (Long, Boolean) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onMoveFolder: (Long, Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var dialog by remember { mutableStateOf<FolderDialog?>(null) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    val selecting = selected.isNotEmpty()

    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)

    // Re-evaluated once a minute so "next alarm in ..." stays right.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(60_000 - nowMillis % 60_000 + 50)
        }
    }

    // While an alarm is being dragged, the new order is held here until the database catches up.
    var localOrder by remember { mutableStateOf<List<Long>?>(null) }
    LaunchedEffect(alarms) { localOrder = null }
    val shown = remember(alarms, localOrder) {
        val order = localOrder
        if (order == null) {
            alarms
        } else {
            val index = order.withIndex().associate { it.value to it.index }
            alarms.sortedBy { index[it.id] ?: Int.MAX_VALUE }
        }
    }
    val byFolder = shown.groupBy { it.folderId }
    val ungrouped = byFolder[null].orEmpty()
    val folderOf = remember(shown) { shown.associate { it.id to it.folderId } }

    // Where each folder tile is on screen, so a dragged alarm can be dropped onto one.
    val tileBounds = remember { mutableMapOf<Long, Rect>() }

    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        listState = listState,
        // Alarms are only reordered inside their own folder (or among the ungrouped ones).
        canSwap = { dragged, other -> dragged is Long && other is Long && folderOf[dragged] == folderOf[other] },
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
        onFinished = { key, finger ->
            val target = finger?.let { point -> tileBounds.entries.firstOrNull { point in it.value }?.key }
            if (target != null && key is Long) {
                localOrder = null
                onMoveAlarm(key, if (target == NO_FOLDER_TILE) null else target)
                if (target != NO_FOLDER_TILE) openFolderId = target
            } else {
                localOrder?.let(onReorderAlarms)
            }
        },
    )

    val draggedAlarmId = reorder.draggedKey as? Long
    val dragging = draggedAlarmId != null
    val hoverTile = reorder.fingerInRoot?.let { point -> tileBounds.entries.firstOrNull { point in it.value }?.key }

    BackHandler(enabled = selecting) { selected = emptySet() }

    val tiles = buildList {
        // Only offered while dragging an alarm that currently sits in a folder.
        if (draggedAlarmId != null && folderOf[draggedAlarmId] != null) {
            add(FolderTileData(NO_FOLDER_TILE, "No folder", true, ungrouped.size))
        }
        folders.forEach { add(FolderTileData(it.id, it.name, it.enabled, byFolder[it.id].orEmpty().size)) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val nextText = remember(shown, folders, nowMillis, is24Hour) { nextAlarm(shown, folders, nowMillis, is24Hour) }
    val openFolder = folders.firstOrNull { it.id == openFolderId }

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
                    title = { Text("Alarms", fontWeight = FontWeight.Black) },
                    actions = {
                        IconButton(onClick = { dialog = FolderDialog.Create }) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            AnimatedVisibility(
                visible = !selecting && !dragging,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                ExtendedFloatingActionButton(onClick = onNewAlarm) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New alarm", fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { padding ->
        if (alarms.isEmpty() && folders.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Text("No alarms yet", style = MaterialTheme.typography.headlineMedium)
                    Text("Tap New alarm to add one.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            // The folder row sits outside the list so it stays in view as a drop target while dragging.
            AnimatedVisibility(visible = tiles.isNotEmpty()) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val tileWidth: Dp = (maxWidth - 16.dp * 2 - 10.dp * 2) / 3
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(tiles, key = { it.id }) { tile ->
                            val index = folders.indexOfFirst { it.id == tile.id }
                            FolderTile(
                                modifier = Modifier.animateItem(),
                                tile = tile,
                                width = tileWidth,
                                open = tile.id == openFolderId,
                                hovered = tile.id == hoverTile,
                                isDropOnly = tile.id == NO_FOLDER_TILE,
                                canMoveLeft = index > 0,
                                canMoveRight = index in 0 until folders.lastIndex,
                                onBounds = { tileBounds[tile.id] = it },
                                onToggle = { onToggleFolder(tile.id, it) },
                                onOpen = {
                                    openFolderId = if (openFolderId == tile.id) null else tile.id
                                },
                                onRename = { folders.firstOrNull { it.id == tile.id }?.let { dialog = FolderDialog.Rename(it) } },
                                onDelete = { folders.firstOrNull { it.id == tile.id }?.let { dialog = FolderDialog.Delete(it) } },
                                onMove = { onMoveFolder(tile.id, it) },
                            )
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = padding.calculateBottomPadding() + 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "next-alarm") {
                    NextAlarmBanner(nextText, Modifier.animateItem())
                }

                if (openFolder != null) {
                    val inFolder = byFolder[openFolder.id].orEmpty()
                    item(key = "open-folder-header") {
                        Text(
                            "${openFolder.name} · " + if (inFolder.size == 1) "1 alarm" else "${inFolder.size} alarms",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .animateItem()
                                .padding(start = 8.dp, top = 4.dp),
                        )
                    }
                    if (inFolder.isEmpty()) {
                        item(key = "open-folder-empty") {
                            Text(
                                "Nothing in this folder yet. Drag an alarm onto its tile, or choose the folder when editing an alarm.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = 8.dp),
                            )
                        }
                    }
                    items(inFolder, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            index = inFolder.indexOf(alarm),
                            folderOn = openFolder.enabled,
                            is24Hour = is24Hour,
                            selected = alarm.id in selected,
                            reorder = reorder,
                            onToggle = onToggleAlarm,
                            onTap = {
                                if (selecting) selected = selected.toggled(alarm.id) else onOpenAlarm(alarm.id)
                            },
                            onHold = { selected = selected + alarm.id },
                        )
                    }
                }

                if (ungrouped.isNotEmpty()) {
                    if (folders.isNotEmpty()) {
                        item(key = "ungrouped-header") {
                            Text(
                                "Ungrouped",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(start = 8.dp, top = 8.dp),
                            )
                        }
                    }
                    items(ungrouped, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            index = ungrouped.indexOf(alarm),
                            folderOn = true,
                            is24Hour = is24Hour,
                            selected = alarm.id in selected,
                            reorder = reorder,
                            onToggle = onToggleAlarm,
                            onTap = {
                                if (selecting) selected = selected.toggled(alarm.id) else onOpenAlarm(alarm.id)
                            },
                            onHold = { selected = selected + alarm.id },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (selected.size == 1) "Delete this alarm?" else "Delete ${selected.size} alarms?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAlarms(selected.toList())
                    selected = emptySet()
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    when (val current = dialog) {
        null -> Unit
        FolderDialog.Create -> NameDialog(
            title = "New folder",
            initial = "",
            confirmLabel = "Create",
            onConfirm = {
                onCreateFolder(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is FolderDialog.Rename -> NameDialog(
            title = "Rename folder",
            initial = current.folder.name,
            confirmLabel = "Save",
            onConfirm = {
                onRenameFolder(current.folder.id, it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is FolderDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete \"${current.folder.name}\"?") },
            text = { Text("The alarms inside are kept and move to Ungrouped.") },
            confirmButton = {
                TextButton(onClick = {
                    if (openFolderId == current.folder.id) openFolderId = null
                    onDeleteFolder(current.folder.id)
                    dialog = null
                }) { Text("Delete folder") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NextAlarmBanner(next: Pair<String, String>?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Alarm, contentDescription = null)
            Spacer(Modifier.width(14.dp))
            AnimatedContent(
                targetState = next,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "nextAlarm",
            ) { value ->
                if (value == null) {
                    Text("No upcoming alarms", style = MaterialTheme.typography.titleMedium)
                } else {
                    Column {
                        Text(
                            "Next alarm in ${value.first}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(value.second, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/**
 * One folder as a tile. The folder icon is a button that switches the whole folder on or off;
 * tapping the rest of the tile opens or closes the folder's alarms. Hold for rename, move and delete.
 */
@Composable
private fun FolderTile(
    modifier: Modifier,
    tile: FolderTileData,
    width: Dp,
    open: Boolean,
    hovered: Boolean,
    isDropOnly: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onBounds: (Rect) -> Unit,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    val container by animateColorAsState(
        targetValue = when {
            hovered -> MaterialTheme.colorScheme.tertiaryContainer
            open -> MaterialTheme.colorScheme.primaryContainer
            tile.enabled -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tileColor",
    )
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tileScale",
    )
    val textAlpha by animateFloatAsState(if (tile.enabled) 1f else 0.55f, label = "tileText")

    Box(
        modifier
            .width(width)
            .enterAnimation()
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(isDropOnly) {
                    detectTapGestures(
                        onTap = { if (!isDropOnly) onOpen() },
                        onLongPress = { if (!isDropOnly) menuOpen = true },
                    )
                },
            shape = MaterialTheme.shapes.extraLarge,
            color = container,
            border = if (open || hovered) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isDropOnly) {
                    Icon(
                        Icons.Filled.Alarm,
                        contentDescription = null,
                        modifier = Modifier.padding(14.dp),
                    )
                } else {
                    FilledIconToggleButton(
                        checked = tile.enabled,
                        onCheckedChange = onToggle,
                    ) {
                        Icon(
                            if (tile.enabled) Icons.Filled.Folder else Icons.Outlined.Folder,
                            contentDescription = if (tile.enabled) "Switch folder off" else "Switch folder on",
                        )
                    }
                }
                Text(
                    tile.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .graphicsLayer { alpha = textAlpha },
                )
                Text(
                    if (tile.count == 1) "1 alarm" else "${tile.count} alarms",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.graphicsLayer { alpha = textAlpha },
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = {
                menuOpen = false
                onRename()
            })
            if (canMoveLeft) {
                DropdownMenuItem(text = { Text("Move left") }, onClick = {
                    menuOpen = false
                    onMove(-1)
                })
            }
            if (canMoveRight) {
                DropdownMenuItem(text = { Text("Move right") }, onClick = {
                    menuOpen = false
                    onMove(1)
                })
            }
            DropdownMenuItem(text = { Text("Delete") }, onClick = {
                menuOpen = false
                onDelete()
            })
        }
    }
}

@Composable
private fun LazyItemScope.AlarmCard(
    alarm: AlarmEntity,
    index: Int,
    folderOn: Boolean,
    is24Hour: Boolean,
    selected: Boolean,
    reorder: ReorderState,
    onToggle: (Long, Boolean) -> Unit,
    onTap: () -> Unit,
    onHold: () -> Unit,
) {
    val active = alarm.enabled && folderOn
    val (time, suffix) = formatTime(alarm.hour, alarm.minute, is24Hour)
    val dragging = reorder.draggedKey == alarm.id

    val container by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.tertiaryContainer
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "alarmColor",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (dragging) Modifier else Modifier.animateItem())
            .enterAnimation(index)
            .reorderable(reorder, alarm.id, onTap = onTap, onHold = onHold),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(
                        targetState = time,
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                        },
                        label = "alarmTime",
                    ) { shownTime ->
                        Text(
                            shownTime,
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp),
                            fontWeight = FontWeight.Black,
                        )
                    }
                    if (suffix.isNotEmpty()) {
                        Text(
                            suffix,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                        )
                    }
                }
                val details = buildString {
                    append(describeDays(alarm.repeatDays))
                    if (alarm.label.isNotBlank()) append(" · ").append(alarm.label)
                    if (!folderOn) append(" · folder is off")
                }
                Text(details, style = MaterialTheme.typography.bodyLarge)
            }
            Switch(checked = alarm.enabled, onCheckedChange = { onToggle(alarm.id, it) })
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    fieldLabel: String = "Folder name",
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(fieldLabel) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
