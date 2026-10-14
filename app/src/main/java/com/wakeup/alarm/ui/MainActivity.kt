package com.wakeup.alarm.ui

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.lifecycleScope
import com.wakeup.alarm.AppPrefs
import com.wakeup.alarm.scheduling.AlarmScheduler
import com.wakeup.alarm.scheduling.Timers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class Screen { HOME, TIMERS, STOPWATCH, EDITOR, SETTINGS, SETUP }

private const val NEW_ALARM = 0L

/** Not an official Android action, but cheap to accept in case an assistant sends it. */
private const val ACTION_SHOW_STOPWATCH = "android.intent.action.SHOW_STOPWATCH"

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** Which tab an incoming request (for example from Google) wants to open. */
    private val requestedTab = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readRequest(intent)
        setContent {
            WakeUpTheme {
                WakeUpRoot(viewModel, requestedTab)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readRequest(intent)
    }

    private fun readRequest(intent: Intent?) {
        requestedTab.value = when (intent?.action) {
            AlarmClock.ACTION_SHOW_ALARMS -> Screen.HOME.name
            AlarmClock.ACTION_SHOW_TIMERS -> Screen.TIMERS.name
            ACTION_SHOW_STOPWATCH -> Screen.STOPWATCH.name
            else -> null
        }
    }

    override fun onResume() {
        super.onResume()
        // Cheap safety net: every time the app opens, make sure the system has all alarms and timers.
        lifecycleScope.launch(Dispatchers.IO) {
            AlarmScheduler.rescheduleAll(applicationContext)
            Timers.rescheduleAll(applicationContext)
        }
    }
}

@Composable
private fun MainNavBar(current: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = current == Screen.HOME,
            onClick = { onSelect(Screen.HOME) },
            icon = { Icon(Icons.Filled.Alarm, contentDescription = null) },
            label = { Text("Alarms", fontWeight = FontWeight.Bold) },
        )
        NavigationBarItem(
            selected = current == Screen.TIMERS,
            onClick = { onSelect(Screen.TIMERS) },
            icon = { Icon(Icons.Filled.HourglassBottom, contentDescription = null) },
            label = { Text("Timer", fontWeight = FontWeight.Bold) },
        )
        NavigationBarItem(
            selected = current == Screen.STOPWATCH,
            onClick = { onSelect(Screen.STOPWATCH) },
            icon = { Icon(Icons.Filled.Timer, contentDescription = null) },
            label = { Text("Stopwatch", fontWeight = FontWeight.Bold) },
        )
    }
}

@Composable
private fun WakeUpRoot(viewModel: MainViewModel, requestedTab: MutableState<String?>) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val alarms by viewModel.alarms.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val timers by viewModel.timers.collectAsState()

    var screen by rememberSaveable { mutableStateOf(if (prefs.onboardingDone) Screen.HOME else Screen.SETUP) }
    var editingId by rememberSaveable { mutableLongStateOf(NEW_ALARM) }

    // A request from outside the app (Google asking to show timers, for example) switches the tab.
    LaunchedEffect(requestedTab.value) {
        val wanted = requestedTab.value ?: return@LaunchedEffect
        requestedTab.value = null
        if (prefs.onboardingDone) screen = Screen.valueOf(wanted)
    }

    BackHandler(enabled = screen != Screen.HOME && !(screen == Screen.SETUP && !prefs.onboardingDone)) {
        screen = if (screen == Screen.SETUP) Screen.SETTINGS else Screen.HOME
    }

    val navBar: @Composable () -> Unit = { MainNavBar(screen) { screen = it } }

    AnimatedContent(
        targetState = screen,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        transitionSpec = {
            val spec = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
            (fadeIn(spec) + scaleIn(spec, initialScale = 0.92f)) togetherWith
                (fadeOut(spring()) + scaleOut(spring(), targetScale = 1.04f))
        },
        label = "screen",
    ) { current ->
        when (current) {
            Screen.HOME -> HomeScreen(
                alarms = alarms,
                folders = folders,
                bottomBar = navBar,
                onToggleAlarm = viewModel::setAlarmEnabled,
                onOpenAlarm = {
                    editingId = it
                    screen = Screen.EDITOR
                },
                onNewAlarm = {
                    editingId = NEW_ALARM
                    screen = Screen.EDITOR
                },
                onDeleteAlarms = viewModel::deleteAlarms,
                onReorderAlarms = viewModel::reorderAlarms,
                onToggleFolder = viewModel::setFolderEnabled,
                onCreateFolder = viewModel::createFolder,
                onRenameFolder = viewModel::renameFolder,
                onDeleteFolder = viewModel::deleteFolder,
                onMoveFolder = viewModel::moveFolder,
                onOpenSettings = { screen = Screen.SETTINGS },
            )

            Screen.TIMERS -> TimersScreen(
                timers = timers,
                bottomBar = navBar,
                onAdd = viewModel::addTimer,
                onStart = viewModel::startTimer,
                onPause = viewModel::pauseTimer,
                onReset = viewModel::resetTimer,
                onDelete = viewModel::deleteTimers,
                onReorder = viewModel::reorderTimers,
            )

            Screen.STOPWATCH -> StopwatchScreen(bottomBar = navBar)

            Screen.EDITOR -> EditorScreen(
                alarm = alarms.firstOrNull { it.id == editingId },
                folders = folders,
                onSave = {
                    viewModel.saveAlarm(it)
                    screen = Screen.HOME
                },
                onDelete = {
                    viewModel.deleteAlarm(it)
                    screen = Screen.HOME
                },
                onBack = { screen = Screen.HOME },
            )

            Screen.SETTINGS -> SettingsScreen(
                prefs = prefs,
                onOpenSetup = { screen = Screen.SETUP },
                onBack = { screen = Screen.HOME },
            )

            Screen.SETUP -> SetupScreen(
                onDone = {
                    prefs.onboardingDone = true
                    screen = Screen.HOME
                },
            )
        }
    }
}
