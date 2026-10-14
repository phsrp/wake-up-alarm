package com.wakeup.alarm.ui

import android.os.Bundle
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.wakeup.alarm.AppPrefs
import com.wakeup.alarm.scheduling.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class Screen { HOME, EDITOR, SETTINGS, SETUP }

private const val NEW_ALARM = 0L

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WakeUpTheme {
                WakeUpRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Cheap safety net: every time the app opens, make sure the system has all the alarms.
        lifecycleScope.launch(Dispatchers.IO) { AlarmScheduler.rescheduleAll(applicationContext) }
    }
}

@Composable
private fun WakeUpRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val alarms by viewModel.alarms.collectAsState()
    val folders by viewModel.folders.collectAsState()

    var screen by rememberSaveable { mutableStateOf(if (prefs.onboardingDone) Screen.HOME else Screen.SETUP) }
    var editingId by rememberSaveable { mutableLongStateOf(NEW_ALARM) }

    BackHandler(enabled = screen != Screen.HOME && !(screen == Screen.SETUP && !prefs.onboardingDone)) {
        screen = if (screen == Screen.SETUP) Screen.SETTINGS else Screen.HOME
    }

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
                onToggleAlarm = viewModel::setAlarmEnabled,
                onOpenAlarm = {
                    editingId = it
                    screen = Screen.EDITOR
                },
                onNewAlarm = {
                    editingId = NEW_ALARM
                    screen = Screen.EDITOR
                },
                onToggleFolder = viewModel::setFolderEnabled,
                onCreateFolder = viewModel::createFolder,
                onRenameFolder = viewModel::renameFolder,
                onDeleteFolder = viewModel::deleteFolder,
                onOpenSettings = { screen = Screen.SETTINGS },
            )

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
