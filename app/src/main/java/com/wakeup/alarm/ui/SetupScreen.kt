@file:OptIn(ExperimentalMaterial3Api::class)

package com.wakeup.alarm.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private data class SetupStep(
    val title: String,
    val why: String,
    val done: Boolean,
    val applicable: Boolean,
    val buttonLabel: String,
    val action: () -> Unit,
)

private fun Context.openFirstThatWorks(vararg intents: Intent) {
    for (intent in intents) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // try the next one
        } catch (_: SecurityException) {
            // try the next one
        }
    }
}

private fun Context.appDetailsIntent() =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val audioPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    // Reading `refresh` makes the list recompute every time the user comes back from a settings screen.
    val steps = remember(refresh) {
        val pkg = context.packageName
        val packageUri = Uri.parse("package:$pkg")
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        listOf(
            SetupStep(
                title = "Notifications",
                why = "The ringing alarm is shown as a notification. Without this it can be hidden.",
                done = Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                applicable = Build.VERSION.SDK_INT >= 33,
                buttonLabel = "Allow notifications",
                action = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            ),
            SetupStep(
                title = "Full-screen alarm",
                why = "Lets the alarm open over the lock screen and turn the screen on. Turn it on for Wake Up.",
                done = Build.VERSION.SDK_INT < 34 || notificationManager.canUseFullScreenIntent(),
                applicable = Build.VERSION.SDK_INT >= 34,
                buttonLabel = "Open full-screen settings",
                action = {
                    context.openFirstThatWorks(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri),
                        context.appDetailsIntent(),
                    )
                },
            ),
            SetupStep(
                title = "Exact alarms",
                why = "Lets the alarm fire at the exact minute.",
                done = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms(),
                applicable = Build.VERSION.SDK_INT >= 31,
                buttonLabel = "Open alarm settings",
                action = {
                    context.openFirstThatWorks(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri),
                        context.appDetailsIntent(),
                    )
                },
            ),
            SetupStep(
                title = "Display over other apps",
                why = "Lets the alarm screen come back if you leave it while it is ringing.",
                done = Settings.canDrawOverlays(context),
                applicable = true,
                buttonLabel = "Open display settings",
                action = {
                    context.openFirstThatWorks(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri),
                        context.appDetailsIntent(),
                    )
                },
            ),
            SetupStep(
                title = "Battery: unrestricted",
                why = "OxygenOS stops background apps to save power. This exempts Wake Up so alarms are not killed.",
                done = powerManager.isIgnoringBatteryOptimizations(pkg),
                applicable = true,
                buttonLabel = "Remove battery limits",
                action = {
                    context.openFirstThatWorks(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                        context.appDetailsIntent(),
                    )
                },
            ),
            SetupStep(
                title = "Audio files (optional)",
                why = "Only needed if you pick your own music files as an alarm sound.",
                done = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED,
                applicable = true,
                buttonLabel = "Allow audio access",
                action = { audioLauncher.launch(audioPermission) },
            ),
        ).filter { it.applicable }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val required = steps.dropLast(1) // the last step (audio files) is optional
    val allRequiredDone = required.all { it.done }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Set up your phone", fontWeight = FontWeight.Black) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Work through these from top to bottom. When a step is done it turns green. " +
                    "This is what makes the alarm ring reliably on your OnePlus.",
                style = MaterialTheme.typography.bodyLarge,
            )

            steps.forEachIndexed { index, step ->
                StepCard(number = index + 1, step = step)
            }

            OnePlusCard(context)

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    if (allRequiredDone) "All set, continue" else "Continue anyway",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!allRequiredDone) {
                Text(
                    "Some steps are not done. The alarm may not ring reliably until they are.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StepCard(number: Int, step: SetupStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (step.done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = if (step.done) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = if (step.done) "Done" else "Not done",
                    tint = if (step.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text("$number. ${step.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(step.why, style = MaterialTheme.typography.bodyMedium)
            if (!step.done) {
                FilledTonalButton(onClick = step.action, shape = MaterialTheme.shapes.large) {
                    Text(step.buttonLabel)
                }
            }
        }
    }
}

@Composable
private fun OnePlusCard(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OnePlus (OxygenOS) extras", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "OxygenOS closes background apps more aggressively than stock Android. Please also do these by hand " +
                    "(menu names vary a little between versions):",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "1. Open Wake Up's app settings, then Battery (or Battery usage). Choose Unrestricted / Allow background activity.\n" +
                    "2. In the same area, turn on Allow auto launch (sometimes called Auto-start).\n" +
                    "3. Open the Recents screen, then lock Wake Up (tap the app's menu or long-press its card and choose Lock).\n" +
                    "4. Never use Force stop on Wake Up. That cancels every alarm until you open the app again.\n" +
                    "5. To see timers and the stopwatch in the Live Alerts capsule, open Live Updates settings and turn on " +
                    "Show Live Updates on Live Alerts for Wake Up.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { context.openFirstThatWorks(context.appDetailsIntent()) },
                    shape = MaterialTheme.shapes.large,
                ) { Text("App settings") }
                FilledTonalButton(
                    onClick = {
                        context.openFirstThatWorks(
                            Intent().setComponent(
                                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                            ),
                            Intent().setComponent(
                                ComponentName("com.oplus.battery", "com.oplus.startupapp.view.StartupAppListActivity"),
                            ),
                            Intent().setComponent(
                                ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                            ),
                            context.appDetailsIntent(),
                        )
                    },
                    shape = MaterialTheme.shapes.large,
                ) { Text("Auto launch") }
            }
            FilledTonalButton(
                onClick = {
                    val packageUri = Uri.parse("package:${context.packageName}")
                    context.openFirstThatWorks(
                        // Android 16's page for promoting an app's notifications; skipped if this phone lacks it.
                        Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS", packageUri),
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        context.appDetailsIntent(),
                    )
                },
                shape = MaterialTheme.shapes.large,
            ) { Text("Live Updates settings") }
        }
    }
}
