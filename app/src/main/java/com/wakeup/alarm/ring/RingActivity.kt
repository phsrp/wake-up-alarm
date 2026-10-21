package com.wakeup.alarm.ring

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wakeup.alarm.AppPrefs
import com.wakeup.alarm.Difficulty
import com.wakeup.alarm.NO_ALARM
import com.wakeup.alarm.ui.WakeUpTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Wrong answers in one ringing that unlock the calculator. */
private const val FAILURES_FOR_CALCULATOR = 9

/** How long the Stop button must be held when math is switched off. */
private const val HOLD_TO_STOP_MS = 2000

/**
 * Full-screen ringing screen: shown over the lock screen, wakes the display, cannot be
 * left with Back, and only closes once the math problems have been solved in a row
 * (or, if math is switched off in Settings, the Stop button has been held).
 */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        // Back does nothing while the alarm rings.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        val prefs = AppPrefs(this)
        if (prefs.ringingAlarmId == NO_ALARM) {
            finish()
            return
        }

        setContent {
            WakeUpTheme {
                RingScreen(
                    prefs = prefs,
                    onSolved = {
                        AlarmService.stop(this)
                        finish()
                    },
                    onNotRinging = { finish() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isVisible = true
    }

    override fun onStop() {
        isVisible = false
        super.onStop()
    }

    // The volume keys must not be able to turn the alarm down.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = when (event.keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        -> true

        else -> super.dispatchKeyEvent(event)
    }

    companion object {
        @Volatile
        var isVisible = false
            private set
    }
}

@Composable
private fun RingScreen(prefs: AppPrefs, onSolved: () -> Unit, onNotRinging: () -> Unit) {
    val difficulty: Difficulty = remember { prefs.difficulty }
    val required = remember { prefs.problemCount }
    val mathRequired = remember { prefs.mathRequired }

    var now by remember { mutableStateOf(LocalTime.now()) }
    var label by remember { mutableStateOf(prefs.ringingLabel) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            label = prefs.ringingLabel
            if (prefs.ringingAlarmId == NO_ALARM) onNotRinging()
            delay(500)
        }
    }

    var problem by remember { mutableStateOf(MathGenerator.next(difficulty)) }
    var input by remember { mutableStateOf("") }
    var streak by remember { mutableIntStateOf(0) }
    var wasWrong by remember { mutableStateOf(false) }

    // Counted for the whole ringing, not just this screen, so leaving and coming back does not reset it.
    var failures by remember { mutableIntStateOf(prefs.ringFailures) }
    var showCalculator by remember { mutableStateOf(false) }
    val calculatorUnlocked = failures >= FAILURES_FOR_CALCULATOR

    fun submit() {
        val answer = input.toIntOrNull()
        input = ""
        if (answer != null && answer == problem.answer) {
            wasWrong = false
            streak += 1
            if (streak >= required) {
                onSolved()
                return
            }
        } else {
            wasWrong = true
            streak = 0
            failures += 1
            prefs.ringFailures = failures
        }
        problem = MathGenerator.next(difficulty)
    }

    val background by animateColorAsState(
        targetValue = if (wasWrong) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ringBackground",
    )
    val contentColor = if (wasWrong) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp, fontWeight = FontWeight.Black),
                color = contentColor,
            )
            Text(
                text = label.ifBlank { "Time to wake up" },
                style = MaterialTheme.typography.titleLarge,
                color = contentColor,
                textAlign = TextAlign.Center,
            )

            if (!mathRequired) {
                Spacer(Modifier.weight(1f))
                HoldToStop(onDone = onSolved)
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.height(12.dp))
                StreakDots(streak = streak, required = required, color = contentColor)
                Text(
                    text = if (wasWrong) "Wrong, start again: $required in a row" else "Solve $required in a row to stop the alarm",
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )

                AnimatedVisibility(visible = calculatorUnlocked) {
                    FilledTonalButton(
                        onClick = { showCalculator = true },
                        modifier = Modifier.padding(top = 8.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text("Open calculator", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = problem.text,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 68.sp, fontWeight = FontWeight.ExtraBold),
                    color = contentColor,
                    textAlign = TextAlign.Center,
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .height(84.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = input.ifEmpty { "?" },
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (input.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Keypad(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onDigit = { if (input.length < 6) input += it },
                    onBackspace = { input = input.dropLast(1) },
                    onSubmit = { submit() },
                )
            }
        }

        if (showCalculator) {
            CalculatorOverlay(onClose = { showCalculator = false })
        }
    }
}

/** A big round button that has to be held down until its ring is full. */
@Composable
private fun HoldToStop(onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(280.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val fill = scope.launch {
                        progress.animateTo(1f, tween(HOLD_TO_STOP_MS, easing = LinearEasing))
                        onDone()
                    }
                    waitForUpOrCancellation()
                    fill.cancel()
                    scope.launch { progress.animateTo(0f, tween(250)) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(250.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
        )
        CircularProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 18.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Hold to stop",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "for 2 seconds",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StreakDots(streak: Int, required: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(required) { index ->
            val filled = index < streak
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (filled) color else color.copy(alpha = 0.25f)),
            )
        }
    }
}

@Composable
private fun Keypad(
    modifier: Modifier,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { digit ->
                    KeyButton(text = digit, modifier = Modifier.weight(1f)) { onDigit(digit) }
                }
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KeyButton(text = "⌫", modifier = Modifier.weight(1f), onClick = onBackspace)
            KeyButton(text = "0", modifier = Modifier.weight(1f)) { onDigit("0") }
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text("OK", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun KeyButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}
