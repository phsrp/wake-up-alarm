package com.wakeup.alarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wakeup.alarm.AppPrefs
import com.wakeup.alarm.NO_ALARM
import com.wakeup.alarm.ring.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives the exact-time broadcast from AlarmManager and starts the ringing service. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, NO_ALARM)
        if (alarmId == NO_ALARM) return

        AlarmService.start(context, alarmId)

        if (intent.action != AlarmScheduler.ACTION_RING) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.afterFired(context, alarmId)
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to queue the next alarm", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Puts every alarm back after a reboot, an app update, or a clock/timezone change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            // A ring that was in progress when the phone shut down is not resumed.
            AppPrefs(context).ringingAlarmId = NO_ALARM
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.rescheduleAll(context)
            } catch (e: Exception) {
                Log.e("RescheduleReceiver", "Failed to reschedule alarms", e)
            } finally {
                pending.finish()
            }
        }
    }
}
