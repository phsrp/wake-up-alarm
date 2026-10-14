package com.wakeup.alarm.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wakeup.alarm.AppPrefs
import com.wakeup.alarm.R
import com.wakeup.alarm.data.AppDatabase
import com.wakeup.alarm.data.TimerEntity
import com.wakeup.alarm.scheduling.Timers
import com.wakeup.alarm.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shows running timers and the stopwatch as ongoing notifications that ask Android 16 to promote
 * them to a Live Update. OxygenOS shows those in its Live Alerts capsule when the user has switched
 * "Show Live Updates on Live Alerts" on for the app. On a phone without that, they are plain
 * ongoing notifications with a live clock, which is still useful.
 */
object LiveUpdates {
    private const val CHANNEL_ID = "live_updates"
    private const val STOPWATCH_NOTIFICATION_ID = 4999
    private const val TIMER_NOTIFICATION_BASE = 5000

    const val ACTION_PAUSE_TIMER = "com.wakeup.alarm.LIVE_PAUSE_TIMER"
    const val ACTION_TOGGLE_STOPWATCH = "com.wakeup.alarm.LIVE_TOGGLE_STOPWATCH"
    const val EXTRA_TIMER_ID = "timer_id"

    /** Not an official Android action; matches what the main screen accepts. */
    private const val ACTION_SHOW_STOPWATCH = "android.intent.action.SHOW_STOPWATCH"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Running timers and stopwatch", NotificationManager.IMPORTANCE_LOW).apply {
            description = "A live clock for timers and the stopwatch while they run"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun openApp(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun actionPending(context: Context, action: String, timerId: Long, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, LiveActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_TIMER_ID, timerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // ----- timers -----

    suspend fun syncTimers(context: Context) {
        val manager = manager(context)
        for (timer in AppDatabase.get(context).dao().getTimers()) {
            val id = TIMER_NOTIFICATION_BASE + timer.id.toInt()
            if (timer.state == TimerEntity.STATE_RUNNING) {
                manager.notify(id, timerNotification(context, timer))
            } else {
                manager.cancel(id)
            }
        }
    }

    fun cancelTimer(context: Context, timerId: Long) {
        manager(context).cancel(TIMER_NOTIFICATION_BASE + timerId.toInt())
    }

    private fun timerNotification(context: Context, timer: TimerEntity): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(timer.label.ifBlank { "Timer" })
            .setContentText("Time left")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // The system draws the countdown itself, so nothing needs to re-post every second.
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setWhen(timer.endAtMillis)
            .setRequestPromotedOngoing(true)
            .setContentIntent(openApp(context, AlarmClock.ACTION_SHOW_TIMERS, 20))
            .addAction(
                0,
                "Pause",
                actionPending(context, ACTION_PAUSE_TIMER, timer.id, 30 + timer.id.toInt()),
            )
            .build()

    // ----- stopwatch -----

    fun syncStopwatch(context: Context) {
        val prefs = AppPrefs(context)
        if (!prefs.stopwatchRunning) {
            manager(context).cancel(STOPWATCH_NOTIFICATION_ID)
            return
        }
        val now = System.currentTimeMillis()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Stopwatch")
            .setContentText("Running")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .setShowWhen(true)
            .setWhen(now - prefs.stopwatchElapsed(now))
            .setRequestPromotedOngoing(true)
            .setContentIntent(openApp(context, ACTION_SHOW_STOPWATCH, 21))
            .addAction(0, "Pause", actionPending(context, ACTION_TOGGLE_STOPWATCH, 0, 22))
            .build()
        manager(context).notify(STOPWATCH_NOTIFICATION_ID, notification)
    }

    /** Pauses a running stopwatch or resumes a paused one. */
    fun toggleStopwatch(context: Context) {
        val prefs = AppPrefs(context)
        val now = System.currentTimeMillis()
        if (prefs.stopwatchRunning) {
            prefs.stopwatchAccumulated = prefs.stopwatchElapsed(now)
            prefs.stopwatchRunning = false
        } else {
            prefs.stopwatchStartedAt = now
            prefs.stopwatchRunning = true
        }
        syncStopwatch(context)
    }
}

/** Handles the Pause button on the live notifications. */
class LiveActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LiveUpdates.ACTION_PAUSE_TIMER -> {
                val id = intent.getLongExtra(LiveUpdates.EXTRA_TIMER_ID, -1)
                if (id < 0) return
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Timers.pause(context, id)
                    } catch (e: Exception) {
                        Log.e("LiveActionReceiver", "Could not pause the timer", e)
                    } finally {
                        pending.finish()
                    }
                }
            }

            LiveUpdates.ACTION_TOGGLE_STOPWATCH -> LiveUpdates.toggleStopwatch(context)
        }
    }
}
