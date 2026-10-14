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
import com.wakeup.alarm.AppPrefs
import com.wakeup.alarm.Difficulty
import com.wakeup.alarm.scheduling.AlarmScheduler

@Composable
fun SettingsScreen(prefs: AppPrefs, onOpenSetup: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var difficulty by remember { mutableStateOf(prefs.difficulty) }
    var count by remember { mutableIntStateOf(prefs.problemCount) }

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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Math difficulty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Difficulty.entries.forEachIndexed { index, option ->
                        SegmentedButton(
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
