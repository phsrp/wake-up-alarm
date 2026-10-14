@file:OptIn(ExperimentalMaterial3Api::class)

package com.wakeup.alarm.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.wakeup.alarm.data.AlarmEntity
import com.wakeup.alarm.data.FolderEntity

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
private val DURATIONS = listOf(0 to "Until dismissed", 1 to "1 min", 5 to "5 min", 10 to "10 min", 30 to "30 min")

private fun soundTitle(context: Context, uriString: String?): String {
    if (uriString == null) return "Default alarm sound"
    return runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(uriString))?.getTitle(context)
    }.getOrNull() ?: "Custom sound"
}

@Composable
fun EditorScreen(
    alarm: AlarmEntity?,
    folders: List<FolderEntity>,
    onSave: (AlarmEntity) -> Unit,
    onDelete: (AlarmEntity) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val timeState = rememberTimePickerState(
        initialHour = alarm?.hour ?: 7,
        initialMinute = alarm?.minute ?: 0,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    var repeatDays by remember { mutableIntStateOf(alarm?.repeatDays ?: 0) }
    var label by remember { mutableStateOf(alarm?.label ?: "") }
    var soundUri by remember { mutableStateOf(alarm?.soundUri) }
    var duration by remember { mutableIntStateOf(alarm?.durationMinutes ?: 0) }
    var folderId by remember { mutableStateOf(alarm?.folderId) }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            soundUri = if (picked == null || picked == default) null else picked.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (alarm == null) "New alarm" else "Edit alarm", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimePicker(state = timeState)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Repeat", if (repeatDays == 0) "Once" else describeDays(repeatDays))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_LETTERS.forEachIndexed { index, letter ->
                        val selected = repeatDays and (1 shl index) != 0
                        Surface(
                            onClick = { repeatDays = repeatDays xor (1 shl index) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(letter, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Label") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Sound", null)
                Surface(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                            soundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                        }
                        soundPicker.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(soundTitle(context, soundUri), style = MaterialTheme.typography.titleMedium)
                            Text("Tap to choose from your ringtones and audio", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Ring for", null)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DURATIONS.forEach { (minutes, text) ->
                        FilterChip(
                            selected = duration == minutes,
                            onClick = { duration = minutes },
                            label = { Text(text) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Folder", null)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = folderId == null,
                        onClick = { folderId = null },
                        label = { Text("None") },
                    )
                    folders.forEach { folder ->
                        FilterChip(
                            selected = folderId == folder.id,
                            onClick = { folderId = folder.id },
                            label = { Text(folder.name) },
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onSave(
                        AlarmEntity(
                            id = alarm?.id ?: 0,
                            hour = timeState.hour,
                            minute = timeState.minute,
                            repeatDays = repeatDays,
                            label = label.trim(),
                            enabled = true,
                            soundUri = soundUri,
                            durationMinutes = duration,
                            folderId = folderId,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("Save alarm", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            if (alarm != null) {
                OutlinedButton(
                    onClick = { onDelete(alarm) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Delete alarm")
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(title: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (value != null) Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
