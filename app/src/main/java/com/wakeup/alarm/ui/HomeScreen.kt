@file:OptIn(ExperimentalMaterial3Api::class)

package com.wakeup.alarm.ui

import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeup.alarm.data.AlarmEntity
import com.wakeup.alarm.data.FolderEntity

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

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

private sealed interface FolderDialog {
    data object Create : FolderDialog
    data class Rename(val folder: FolderEntity) : FolderDialog
    data class Delete(val folder: FolderEntity) : FolderDialog
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
    val selecting = selected.isNotEmpty()

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

    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        listState = listState,
        // Alarms can only be reordered inside their own folder (or among the ungrouped ones).
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
        onFinished = { localOrder?.let(onReorderAlarms) },
    )

    BackHandler(enabled = selecting) { selected = emptySet() }

    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
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
            if (!selecting) {
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
            for ((folderIndex, folder) in folders.withIndex()) {
                item(key = "folder-${folder.id}") {
                    FolderHeader(
                        folder = folder,
                        count = byFolder[folder.id].orEmpty().size,
                        canMoveUp = folderIndex > 0,
                        canMoveDown = folderIndex < folders.lastIndex,
                        onToggle = { onToggleFolder(folder.id, it) },
                        onRename = { dialog = FolderDialog.Rename(folder) },
                        onDelete = { dialog = FolderDialog.Delete(folder) },
                        onMove = { onMoveFolder(folder.id, it) },
                    )
                }
                val inFolder = byFolder[folder.id].orEmpty()
                if (inFolder.isEmpty()) {
                    item(key = "folder-empty-${folder.id}") {
                        Text(
                            "No alarms in this folder. Open an alarm to move it here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
                items(inFolder, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        folderOn = folder.enabled,
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
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                        )
                    }
                }
                items(ungrouped, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
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
                    onDeleteFolder(current.folder.id)
                    dialog = null
                }) { Text("Delete folder") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

@Composable
private fun FolderHeader(
    folder: FolderEntity,
    count: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (count == 1) "1 alarm" else "$count alarms",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = folder.enabled, onCheckedChange = onToggle)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Folder options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        menuOpen = false
                        onRename()
                    })
                    if (canMoveUp) {
                        DropdownMenuItem(text = { Text("Move up") }, onClick = {
                            menuOpen = false
                            onMove(-1)
                        })
                    }
                    if (canMoveDown) {
                        DropdownMenuItem(text = { Text("Move down") }, onClick = {
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
    }
}

@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.AlarmCard(
    alarm: AlarmEntity,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (dragging) Modifier else Modifier.animateItem())
            .reorderable(reorder, alarm.id, onTap = onTap, onHold = onHold)
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.tertiaryContainer
                active -> MaterialTheme.colorScheme.primaryContainer
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
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        time,
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp),
                        fontWeight = FontWeight.Black,
                    )
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
