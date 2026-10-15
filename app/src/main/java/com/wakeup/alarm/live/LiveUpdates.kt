package com.wakeup.alarm.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.wakeup.alarm.AppPrefs
import com.wakeup.alarm.R
import com.wakeup.alarm.data.AppDatabase
import com.wakeup.alarm.data.TimerEntity
import com.wakeup.alarm.scheduling.Timers
import com.wakeup.alarm.ui.LivePopupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Running timers and the stopwatch are shown as ongoing notifications that ask Android 16 to promote
 * them to a Live Update (OnePlus shows these in Live Alerts). A small foreground service re-posts them
 * once a second so the time itself, not just the name, is shown in the capsule.
 */
object LiveUpdates {
    const val CHANNEL_ID = "live_updates"
    const val FOREGROUND_NOTIFICATION_ID = 4998
    const val STOPWATCH_NOTIFICATION_ID = 4999
    const val TIMER_NOTIFICATION_BASE = 5000

    const val ACTION_PAUSE_TIMER = "com.wakeup.alarm.LIVE_PAUSE_TIMER"
    const val ACTION_ADD_MINUTE = "com.wakeup.alarm.LIVE_ADD_MINUTE"
    const val ACTION_TOGGLE_STOPWATCH = "com.wakeup.alarm.LIVE_TOGGLE_STOPWATCH"
    const val ACTION_LAP_STOPWATCH = "com.wakeup.alarm.LIVE_LAP_STOPWATCH"
    const val EXTRA_TIMER_ID = "timer_id"

    private const val TAG = "LiveUpdates"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Running timers and stopwatch", NotificationManager.IMPORTANCE_LOW).apply {
            description = "A live clock for timers and the stopwatch while they run"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Starts the once-a-second updater if anything is running, or stops it and clears the notifications. */
    suspend fun sync(context: Context) {
        val running = AppPrefs(context).stopwatchRunning ||
            AppDatabase.get(context).dao().getTimers().any { it.state == TimerEntity.STATE_RUNNING }
        val ticker = Intent(context, LiveTickerService::class.java)
        if (running) {
            try {
                ContextCompat.startForegroundService(context, ticker)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start the live updater", e)
            }
        } else {
            context.stopService(ticker)
        }
    }

    /** For callers that are not already in a coroutine. */
    fun syncAsync(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                sync(app)
            } catch (e: Exception) {
                Log.w(TAG, "Live update sync failed", e)
            }
        }
    }

    fun cancelTimer(context: Context, timerId: Long) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(TIMER_NOTIFICATION_BASE + timerId.toInt())
    }

    // ----- notification content -----

    /** 1:05:09 or 05:09. [roundUp] is for countdowns, so 0.4 seconds left still reads 00:01. */
    fun clockText(millis: Long, roundUp: Boolean): String {
        val safe = millis.coerceAtLeast(0)
        val totalSeconds = if (roundUp) (safe + 999) / 1000 else safe / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
    }

    private fun popup(context: Context, kind: String, timerId: Long, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            LivePopupActivity.intent(context, kind, timerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(context: Context, action: String, timerId: Long, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, LiveActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_TIMER_ID, timerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun timerNotification(context: Context, timer: TimerEntity, now: Long): Notification {
        val left = (timer.endAtMillis - now).coerceAtLeast(0)
        val total = timer.durationSeconds * 1000
        val time = clockText(left, roundUp = true)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            // The time is the title because that is the part the capsule shows.
            .setContentTitle(time)
            .setContentText(timer.label.ifBlank { "Timer" })
            .setShortCriticalText(time)
            // Android 16's progress template: this is what the system draws as the designed Live Update card.
            .setStyle(
                NotificationCompat.ProgressStyle()
                    .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(total.coerceAtLeast(1))))
                    .setProgress((total - left.toInt()).coerceIn(0, total.coerceAtLeast(1)))
                    .setStyledByProgress(true)
                    .setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_alarm)),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true)
            .setContentIntent(popup(context, LivePopupActivity.KIND_TIMER, timer.id, 20 + timer.id.toInt()))
            .addAction(0, "Pause", action(context, ACTION_PAUSE_TIMER, timer.id, 30 + timer.id.toInt()))
            .addAction(0, "+1 min", action(context, ACTION_ADD_MINUTE, timer.id, 40 + timer.id.toInt()))
            .build()
    }

    fun stopwatchNotification(context: Context, prefs: AppPrefs, now: Long): Notification {
        val time = clockText(prefs.stopwatchElapsed(now), roundUp = false)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(time)
            .setContentText("Stopwatch")
            .setShortCriticalText(time)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true)
            .setContentIntent(popup(context, LivePopupActivity.KIND_STOPWATCH, 0, 21))
            .addAction(0, "Pause", action(context, ACTION_TOGGLE_STOPWATCH, 0, 22))
            .addAction(0, "Lap", action(context, ACTION_LAP_STOPWATCH, 0, 23))
            .build()
    }

    // ----- stopwatch controls (shared by the screen, the popup and the notification) -----

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
        syncAsync(context)
    }

    fun lapStopwatch(context: Context) {
        val prefs = AppPrefs(context)
        if (!prefs.stopwatchRunning) return
        prefs.stopwatchLaps = prefs.stopwatchLaps + prefs.stopwatchElapsed()
    }

    fun resetStopwatch(context: Context) {
        val prefs = AppPrefs(context)
        prefs.stopwatchRunning = false
        prefs.stopwatchAccumulated = 0
        prefs.stopwatchStartedAt = 0
        prefs.stopwatchLaps = emptyList()
        syncAsync(context)
    }
}

/** Handles the buttons on the live notifications. */
class LiveActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getLongExtra(LiveUpdates.EXTRA_TIMER_ID, -1)
        when (intent.action) {
            LiveUpdates.ACTION_PAUSE_TIMER -> onTimer(context, timerId) { Timers.pause(context, timerId) }
            LiveUpdates.ACTION_ADD_MINUTE -> onTimer(context, timerId) { Timers.addMinute(context, timerId) }
            LiveUpdates.ACTION_TOGGLE_STOPWATCH -> LiveUpdates.toggleStopwatch(context)
            LiveUpdates.ACTION_LAP_STOPWATCH -> LiveUpdates.lapStopwatch(context)
        }
    }

    private fun onTimer(context: Context, timerId: Long, work: suspend () -> Unit) {
        if (timerId < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                work()
            } catch (e: Exception) {
                Log.e("LiveActionReceiver", "Timer button failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
