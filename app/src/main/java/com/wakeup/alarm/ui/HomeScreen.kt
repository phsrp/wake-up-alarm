@file:OptIn(ExperimentalMaterial3Api::class)

package com.wakeup.alarm.ui

import android.text.format.DateFormat
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CreateNewFolder
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
    onToggleAlarm: (Long, Boolean) -> Unit,
    onOpenAlarm: (Long) -> Unit,
    onNewAlarm: () -> Unit,
    onToggleFolder: (Long, Boolean) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var dialog by remember { mutableStateOf<FolderDialog?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val byFolder = alarms.groupBy { it.folderId }
    val ungrouped = byFolder[null].orEmpty()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewAlarm) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New alarm", fontWeight = FontWeight.Bold)
            }
        },
    ) { padding ->
        if (alarms.isEmpty() && folders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (folder in folders) {
                item(key = "folder-${folder.id}") {
                    FolderHeader(
                        folder = folder,
                        count = byFolder[folder.id].orEmpty().size,
                        onToggle = { onToggleFolder(folder.id, it) },
                        onRename = { dialog = FolderDialog.Rename(folder) },
                        onDelete = { dialog = FolderDialog.Delete(folder) },
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
                    AlarmCard(alarm, folderOn = folder.enabled, is24Hour, onToggleAlarm, onOpenAlarm)
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
                    AlarmCard(alarm, folderOn = true, is24Hour, onToggleAlarm, onOpenAlarm)
                }
            }
        }
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

@Composable
private fun FolderHeader(
    folder: FolderEntity,
    count: Int,
    onToggle: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
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
private fun AlarmCard(
    alarm: AlarmEntity,
    folderOn: Boolean,
    is24Hour: Boolean,
    onToggle: (Long, Boolean) -> Unit,
    onOpen: (Long) -> Unit,
) {
    val active = alarm.enabled && folderOn
    val (time, suffix) = formatTime(alarm.hour, alarm.minute, is24Hour)
    Card(
        onClick = { onOpen(alarm.id) },
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
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
                label = { Text("Folder name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
