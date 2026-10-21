@file:OptIn(ExperimentalMaterial3Api::class)

package com.wakeup.alarm.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Switch
import androidx.core.content.IntentCompat
import com.wakeup.alarm.AppPrefs
import com.wakeup.alarm.AppTheme
import com.wakeup.alarm.Difficulty
import com.wakeup.alarm.scheduling.AlarmScheduler

@Composable
fun SettingsScreen(prefs: AppPrefs, onOpenSetup: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var difficulty by remember { mutableStateOf(prefs.difficulty) }
    var count by remember { mutableIntStateOf(prefs.problemCount) }
    var mathRequired by remember { mutableStateOf(prefs.mathRequired) }
    var defaultSound by remember { mutableStateOf(prefs.defaultSoundUri) }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            val systemDefault = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            // Choosing the phone's own default simply clears the setting.
            defaultSound = if (picked == null || picked == systemDefault) null else picked.toString()
            prefs.defaultSoundUri = defaultSound
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Black) },
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val currentTheme = ThemeController.current(context)
                AppTheme.entries.forEach { option ->
                    val selected = option == currentTheme
                    Surface(
                        onClick = { ThemeController.set(context, option) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                                option.swatch().forEach { color ->
                                    Box(
                                        Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                    )
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(option.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(option.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (selected) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Selected")
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Default alarm sound", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Used by every alarm and timer that has no sound of its own.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Default alarm sound")
                            defaultSound?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
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
                            Text(
                                if (defaultSound == null) "Phone's default alarm sound" else soundTitle(context, defaultSound),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text("Tap to choose from your ringtones and audio", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Solve math to stop an alarm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (mathRequired) "On: you must solve the problems below." else "Off: you stop an alarm by holding the Stop button for 2 seconds.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = mathRequired,
                        onCheckedChange = {
                            mathRequired = it
                            prefs.mathRequired = it
                        },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Math difficulty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    when (difficulty) {
                        Difficulty.EASY -> "Single digits and small numbers, like 7 + 5 or 15 − 6."
                        Difficulty.MEDIUM -> "Two-digit adding and taking away, like 47 + 28."
                        Difficulty.HARD -> "Times tables and three-digit sums, like 17 × 8 or 342 + 257."
                    } + " If you get nine wrong in one ringing, a calculator unlocks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Difficulty.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            enabled = mathRequired,
                            selected = difficulty == option,
                            onClick = {
                                difficulty = option
                                prefs.difficulty = option
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, Difficulty.entries.size),
                        ) {
                            Text(option.label)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Correct answers in a row", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "A wrong answer starts the count again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    FilledTonalIconButton(
                        onClick = {
                            count = (count - 1).coerceAtLeast(1)
                            prefs.problemCount = count
                        },
                        modifier = Modifier.size(56.dp),
                    ) { Text("−", style = MaterialTheme.typography.headlineMedium) }
                    Text(count.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    FilledTonalIconButton(
                        onClick = {
                            count = (count + 1).coerceAtMost(10)
                            prefs.problemCount = count
                        },
                        modifier = Modifier.size(56.dp),
                    ) { Text("+", style = MaterialTheme.typography.headlineMedium) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Test", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        AlarmScheduler.scheduleTest(context)
                        Toast.makeText(context, "Test alarm in 10 seconds. Lock your phone now.", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Test alarm in 10 seconds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reliability", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                FilledTonalButton(
                    onClick = onOpenSetup,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Permissions and phone setup")
                }
                Text(
                    "Never use Force stop on this app in Android settings: it cancels every alarm until you open the app again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
