package com.wakeup.alarm.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wakeup.alarm.NO_ALARM
import com.wakeup.alarm.TEST_ALARM_ID
import com.wakeup.alarm.data.AlarmEntity
import com.wakeup.alarm.data.AppDatabase
import com.wakeup.alarm.ui.MainActivity
import java.time.LocalTime
import java.time.ZonedDateTime

object AlarmTime {
    /** The next moment this alarm should ring, strictly after [now]. */
    fun nextTrigger(alarm: AlarmEntity, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val time = LocalTime.of(alarm.hour, alarm.minute)
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (alarm.repeatDays != 0) {
                val bit = 1 shl (date.dayOfWeek.value - 1) // Monday = 1
                if (alarm.repeatDays and bit == 0) continue
            }
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        return ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.zone)
    }
}

object AlarmScheduler {
    const val ACTION_RING = "com.wakeup.alarm.RING"
    const val ACTION_RESTART_RING = "com.wakeup.alarm.RESTART_RING"
    const val EXTRA_ALARM_ID = "alarm_id"

    private const val TAG = "AlarmScheduler"
    private const val RESTART_REQUEST_OFFSET = 1_000_000

    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun ringPending(context: Context, id: Long, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_ALARM_ID, id)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setClock(context: Context, triggerAtMillis: Long, operation: PendingIntent) {
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // setAlarmClock is the exact, Doze-proof call Android reserves for real alarm clocks.
        alarmManager(context).setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, show), operation)
    }

    fun schedule(context: Context, alarm: AlarmEntity) {
        val at = AlarmTime.nextTrigger(alarm).toInstant().toEpochMilli()
        setClock(context, at, ringPending(context, alarm.id, ACTION_RING, alarm.id.toInt()))
        Log.d(TAG, "Scheduled alarm ${alarm.id} for $at")
    }

    fun cancel(context: Context, alarmId: Long) {
        val pending = ringPending(context, alarmId, ACTION_RING, alarmId.toInt())
        alarmManager(context).cancel(pending)
    }

    fun scheduleTest(context: Context, delayMillis: Long = 10_000) {
        val at = System.currentTimeMillis() + delayMillis
        setClock(context, at, ringPending(context, TEST_ALARM_ID, ACTION_RING, TEST_ALARM_ID.toInt()))
    }

    /** A safety net used when the ringing service is swiped away: brings the ringing back within a second. */
    fun scheduleRingRestart(context: Context, alarmId: Long, delayMillis: Long = 1_000) {
        if (alarmId == NO_ALARM) return
        val at = System.currentTimeMillis() + delayMillis
        val request = RESTART_REQUEST_OFFSET + alarmId.toInt()
        setClock(context, at, ringPending(context, alarmId, ACTION_RESTART_RING, request))
    }

    /** Makes the system's alarm list match the database: enabled alarms in enabled folders only. */
    suspend fun rescheduleAll(context: Context) {
        val dao = AppDatabase.get(context).dao()
        val folders = dao.getFolders().associateBy { it.id }
        for (alarm in dao.getAlarms()) {
            val folderOn = alarm.folderId?.let { folders[it]?.enabled } ?: true
            if (alarm.enabled && folderOn) schedule(context, alarm) else cancel(context, alarm.id)
        }
    }

    /** Called right after an alarm fires: queue the next repeat, or switch a one-time alarm off. */
    suspend fun afterFired(context: Context, alarmId: Long) {
        if (alarmId == TEST_ALARM_ID) return
        val dao = AppDatabase.get(context).dao()
        val alarm = dao.getAlarm(alarmId) ?: return
        if (alarm.repeatDays == 0) {
            dao.setAlarmEnabled(alarm.id, false)
            cancel(context, alarm.id)
        } else {
            schedule(context, alarm)
        }
    }
}
