package com.wakeup.alarm.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.wakeup.alarm.data.AppDatabase
import com.wakeup.alarm.data.TimerEntity
import com.wakeup.alarm.live.LiveUpdates
import com.wakeup.alarm.ring.TimerService

/** All timer behaviour in one place, so the screen, Google's set-timer request and the boot receiver agree. */
object Timers {
    const val ACTION_FIRE = "com.wakeup.alarm.TIMER_FIRE"
    const val EXTRA_TIMER_ID = "timer_id"
    private const val REQUEST_BASE = 2_000_000

    private fun dao(context: Context) = AppDatabase.get(context).dao()

    private fun pending(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_TIMER_ID, id)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleAt(context: Context, id: Long, endAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val delay = (endAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val triggerAt = SystemClock.elapsedRealtime() + delay
        val operation = pending(context, id)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
        }
    }

    private fun cancelSchedule(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, id))
    }

    suspend fun create(context: Context, durationSeconds: Int, label: String, startNow: Boolean): Long {
        val id = dao(context).insertTimer(
            TimerEntity(
                label = label.trim(),
                durationSeconds = durationSeconds,
                remainingMillis = durationSeconds * 1000L,
            ),
        )
        if (startNow) start(context, id)
        return id
    }

    /** Starts from the full time, or carries on from where a paused timer stopped. */
    suspend fun start(context: Context, id: Long) {
        val timer = dao(context).getTimer(id) ?: return
        val left = when (timer.state) {
            TimerEntity.STATE_PAUSED -> timer.remainingMillis
            else -> timer.durationSeconds * 1000L
        }
        val endAt = System.currentTimeMillis() + left
        dao(context).updateTimer(timer.copy(state = TimerEntity.STATE_RUNNING, endAtMillis = endAt, remainingMillis = left))
        scheduleAt(context, id, endAt)
        LiveUpdates.syncTimers(context)
    }

    suspend fun pause(context: Context, id: Long) {
        val timer = dao(context).getTimer(id) ?: return
        if (timer.state != TimerEntity.STATE_RUNNING) return
        val left = (timer.endAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        cancelSchedule(context, id)
        dao(context).updateTimer(timer.copy(state = TimerEntity.STATE_PAUSED, remainingMillis = left))
        LiveUpdates.syncTimers(context)
    }

    suspend fun reset(context: Context, id: Long) {
        val timer = dao(context).getTimer(id) ?: return
        cancelSchedule(context, id)
        dao(context).updateTimer(
            timer.copy(state = TimerEntity.STATE_IDLE, endAtMillis = 0, remainingMillis = timer.durationSeconds * 1000L),
        )
        LiveUpdates.syncTimers(context)
    }

    suspend fun delete(context: Context, ids: List<Long>) {
        ids.forEach {
            cancelSchedule(context, it)
            LiveUpdates.cancelTimer(context, it)
        }
        dao(context).deleteTimers(ids)
    }

    /** The time ran out: remember that, then ring. */
    suspend fun onFired(context: Context, id: Long) {
        val timer = dao(context).getTimer(id) ?: return
        dao(context).updateTimer(timer.copy(state = TimerEntity.STATE_FINISHED, endAtMillis = 0, remainingMillis = 0))
        LiveUpdates.syncTimers(context)
    }

    /** After a reboot or update: put running timers back, and ring any that ran out while the phone was off. */
    suspend fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        for (timer in dao(context).getTimers()) {
            if (timer.state != TimerEntity.STATE_RUNNING) continue
            if (timer.endAtMillis <= now) {
                onFired(context, timer.id)
                TimerService.start(context, timer.id)
            } else {
                scheduleAt(context, timer.id, timer.endAtMillis)
            }
        }
        LiveUpdates.syncTimers(context)
        LiveUpdates.syncStopwatch(context)
    }
}
