package com.wakeup.alarm.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeup.alarm.AppPrefs
import com.wakeup.alarm.data.AppDatabase
import com.wakeup.alarm.data.TimerEntity
import com.wakeup.alarm.live.LiveUpdates
import com.wakeup.alarm.scheduling.Timers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The popup shown when you tap a running timer or stopwatch notification: a big countdown ring
 * (or stopwatch) with the controls under it, floating over whatever you were doing.
 */
class LivePopupActivity : ComponentActivity() {

    private val kind = mutableStateOf(KIND_TIMER)
    private val timerId = mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        read(intent)
        setContent {
            WakeUpTheme {
                if (kind.value == KIND_STOPWATCH) {
                    StopwatchPopup(onClose = { finish() })
                } else {
                    TimerPopup(timerId = timerId.value, onClose = { finish() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent?) {
        kind.value = intent?.getStringExtra(EXTRA_KIND) ?: KIND_TIMER
        timerId.value = intent?.getLongExtra(EXTRA_TIMER_ID, 0L) ?: 0L
    }

    companion object {
        const val KIND_TIMER = "timer"
        const val KIND_STOPWATCH = "stopwatch"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_TIMER_ID = "timer_id"

        fun intent(context: Context, kind: String, timerId: Long): Intent =
            Intent(context, LivePopupActivity::class.java)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_TIMER_ID, timerId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
private fun PopupShell(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // Taps on the sheet itself must not fall through and close it.
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

@Composable
private fun PopupHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
        }
    }
}

@Composable
private fun TimerPopup(timerId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(context).dao() }

    val timers by produceState<List<TimerEntity>?>(initialValue = null) {
        dao.observeTimers().collect { value = it }
    }
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(100)
        }
    }

    val loaded = timers
    val timer = loaded?.firstOrNull { it.id == timerId }

    // The timer was deleted or never existed: nothing to show.
    LaunchedEffect(loaded, timer) {
        if (loaded != null && timer == null) onClose()
    }
    if (timer == null) return

    val running = timer.state == TimerEntity.STATE_RUNNING
    val total = timer.durationSeconds * 1000L
    val left = when (timer.state) {
        TimerEntity.STATE_RUNNING -> (timer.endAtMillis - now).coerceAtLeast(0)
        TimerEntity.STATE_PAUSED -> timer.remainingMillis
        TimerEntity.STATE_FINISHED -> 0L
        else -> total
    }
    val fraction = if (total > 0) (left.toFloat() / total).coerceIn(0f, 1f) else 0f

    PopupShell(onClose) {
        PopupHeader(timer.label.ifBlank { "Timer" }, onClose)

        Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { fraction },
                modifier = Modifier.size(248.dp),
                strokeWidth = 18.dp,
                strokeCap = StrokeCap.Round,
                color = if (timer.state == TimerEntity.STATE_FINISHED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    LiveUpdates.clockText(left, roundUp = true),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp, fontWeight = FontWeight.Black),
                )
                Text(
                    when (timer.state) {
                        TimerEntity.STATE_RUNNING -> "Running"
                        TimerEntity.STATE_PAUSED -> "Paused"
                        TimerEntity.STATE_FINISHED -> "Time's up"
                        else -> "Ready"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { scope.launch(Dispatchers.IO) { Timers.reset(context, timer.id) } },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.height(56.dp),
            ) { Text("Reset") }

            FilledIconButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        if (running) Timers.pause(context, timer.id) else Timers.start(context, timer.id)
                    }
                },
                modifier = Modifier.size(80.dp),
            ) {
                Icon(
                    if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (running) "Pause" else "Start",
                    modifier = Modifier.size(40.dp),
                )
            }

            FilledTonalButton(
                onClick = { scope.launch(Dispatchers.IO) { Timers.addMinute(context, timer.id) } },
                enabled = timer.state == TimerEntity.STATE_RUNNING || timer.state == TimerEntity.STATE_PAUSED,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.height(56.dp),
            ) { Text("+1:00") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StopwatchPopup(onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }

    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(30)
        }
    }

    val running = prefs.stopwatchRunning
    val elapsed = prefs.stopwatchElapsed(tick)
    val laps = prefs.stopwatchLaps

    PopupShell(onClose) {
        PopupHeader("Stopwatch", onClose)

        Text(
            formatStopwatch(elapsed),
            modifier = Modifier.padding(vertical = 28.dp),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            ),
            textAlign = TextAlign.Center,
        )

        if (laps.isNotEmpty()) {
            val last = laps.last()
            val previous = if (laps.size >= 2) laps[laps.size - 2] else 0L
            Text(
                "Lap ${laps.size}   ${formatStopwatch(last - previous)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    LiveUpdates.resetStopwatch(context)
                    tick = System.currentTimeMillis()
                },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.height(56.dp),
            ) { Text("Reset") }

            FilledIconButton(
                onClick = {
                    LiveUpdates.toggleStopwatch(context)
                    tick = System.currentTimeMillis()
                },
                modifier = Modifier.size(80.dp),
            ) {
                Icon(
                    if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (running) "Pause" else "Start",
                    modifier = Modifier.size(40.dp),
                )
            }

            FilledTonalButton(
                onClick = {
                    LiveUpdates.lapStopwatch(context)
                    tick = System.currentTimeMillis()
                },
                enabled = running,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.height(56.dp),
            ) { Text("Lap") }
        }
        Spacer(Modifier.height(8.dp))
    }
}
