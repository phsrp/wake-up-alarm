@file:OptIn(ExperimentalMaterial3Api::class)

package com.wakeup.alarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wakeup.alarm.AppPrefs
import com.wakeup.alarm.live.LiveUpdates
import kotlinx.coroutines.delay

/** 01:23.45, or 1:01:23.45 once it passes an hour. */
fun formatStopwatch(millis: Long): String {
    val total = millis.coerceAtLeast(0)
    val hours = total / 3_600_000
    val minutes = (total / 60_000) % 60
    val seconds = (total / 1000) % 60
    val hundredths = (total / 10) % 100
    return if (hours > 0) {
        "%d:%02d:%02d.%02d".format(hours, minutes, seconds, hundredths)
    } else {
        "%02d:%02d.%02d".format(minutes, seconds, hundredths)
    }
}

@Composable
fun StopwatchScreen(bottomBar: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }

    var running by remember { mutableStateOf(prefs.stopwatchRunning) }
    var laps by remember { mutableStateOf(prefs.stopwatchLaps) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // The Pause button on the live notification changes the stopwatch while this screen is behind it.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                running = prefs.stopwatchRunning
                laps = prefs.stopwatchLaps
                now = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            delay(30)
        }
        now = System.currentTimeMillis()
    }

    val elapsed = prefs.stopwatchElapsed(now)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Stopwatch", fontWeight = FontWeight.Black) },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatStopwatch(elapsed),
                modifier = Modifier.padding(vertical = 28.dp),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        prefs.stopwatchRunning = false
                        prefs.stopwatchAccumulated = 0
                        prefs.stopwatchStartedAt = 0
                        prefs.stopwatchLaps = emptyList()
                        running = false
                        laps = emptyList()
                        LiveUpdates.syncStopwatch(context)
                    },
                    enabled = elapsed > 0 || running,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                ) { Text("Reset", style = MaterialTheme.typography.titleMedium) }

                Button(
                    onClick = {
                        val moment = System.currentTimeMillis()
                        if (running) {
                            prefs.stopwatchAccumulated = prefs.stopwatchElapsed(moment)
                            prefs.stopwatchRunning = false
                            running = false
                        } else {
                            prefs.stopwatchStartedAt = moment
                            prefs.stopwatchRunning = true
                            running = true
                        }
                        LiveUpdates.syncStopwatch(context)
                    },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(64.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(
                        if (running) "Pause" else if (elapsed > 0) "Resume" else "Start",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                FilledTonalButton(
                    onClick = {
                        laps = laps + prefs.stopwatchElapsed()
                        prefs.stopwatchLaps = laps
                    },
                    enabled = running,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                ) { Text("Lap", style = MaterialTheme.typography.titleMedium) }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Newest lap first.
                itemsIndexed(laps.asReversed()) { reversedIndex, total ->
                    val number = laps.size - reversedIndex
                    val previous = if (number >= 2) laps[number - 2] else 0L
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Lap $number", style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatStopwatch(total - previous),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            formatStopwatch(total),
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
